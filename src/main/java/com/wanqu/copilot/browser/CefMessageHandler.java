package com.wanqu.copilot.browser;

import cn.hutool.core.util.ZipUtil;
import com.alibaba.cloud.ai.graph.skills.SkillMetadata;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanqu.copilot.entity.ChatRequest;
import com.wanqu.copilot.entity.CopilotConfig;
import com.wanqu.copilot.entity.ProxyConfig;
import com.wanqu.copilot.entity.WanquProxySelector;
import com.wanqu.copilot.mapper.ChatMessageMapper;
import com.wanqu.copilot.mapper.ConversationMapper;
import com.wanqu.copilot.mapper.ProjectMapper;
import com.wanqu.copilot.record.ChatMessageRecord;
import com.wanqu.copilot.record.ConversationRecord;
import com.wanqu.copilot.record.ProjectRecord;
import com.wanqu.copilot.service.ChatService;
import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/***
 * ```json
 * {
 *   "type": "request | response | event",
 *   "action": "namespace:actionName",
 *   "id": "uuid",
 *   "payload": { ... }
 * }
 * ```
 */
@Component
@Slf4j
public class CefMessageHandler extends CefMessageRouterHandlerAdapter {
  @Autowired private ObjectMapper objectMapper;
  private final ExecutorService executorService = Executors.newFixedThreadPool(4);

  @Autowired private ApplicationEventPublisher publisher;

  @Autowired private Map<String, String> name2Vendor;

  @Autowired private WanquProxySelector wanquProxySelector;

  @Autowired private CopilotConfig copilotConfig;

  @Autowired private ProjectMapper projectMapper;

  @Autowired private ConversationMapper conversationMapper;

  @Autowired private ChatService chatService;

  @Autowired private ChatMessageMapper chatMessageMapper;

  @Autowired private BuildProperties build;

  @Override
  public boolean onQuery(
      CefBrowser browser,
      CefFrame frame,
      long queryId,
      String request,
      boolean persistent,
      CefQueryCallback callback) {
    // 标记为异步处理，防止在当前方法返回时 callback 被回收或 finalize
    executorService.submit(
        () -> {
          try {
            JsonNode node = objectMapper.readTree(request);
            String type = node.path("type").asText("unknown");
            String action = node.path("action").asText("unknown");
            String id = node.path("id").asText("unknown");
            JsonNode payload = node.path("payload");
            onQuery(browser, frame, type, action, id, payload, callback);
          } catch (Exception e) {
            log.error("Failed to process query request: {}", request, e);
            callback.failure(-1, e.getMessage());
          }
        });
    return true;
  }

  boolean onQuery(
      CefBrowser browser,
      CefFrame frame,
      String type,
      String action,
      String id,
      JsonNode payload,
      CefQueryCallback callback)
      throws JsonProcessingException {
    switch (type) {
      case "request":
        return doRequest(action, id, payload, callback, browser, frame);
      case "response":
        return doResponse(action, id, payload, callback);
      case "event":
        return doEvent(action, id, payload, callback);
      default:
        log.warn("Unknown message type: {}", type);
        return false;
    }
  }

  private void sendResponse(CefQueryCallback callback, CefMessage message) {
    try {
      String response = objectMapper.writeValueAsString(message);
      log.info("Sending response: {}", response);
      callback.success(response);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize response", e);
      callback.failure(-1, "Serialization error");
    }
  }

  private void sendError(CefQueryCallback callback, CefMessage message, String error) {
    message.setSuccess(false);
    message.setError(error);
    sendResponse(callback, message);
  }

  boolean doRequest(
      String action,
      String id,
      JsonNode payload,
      CefQueryCallback callback,
      CefBrowser browser,
      CefFrame frame)
      throws JsonProcessingException {
    CefMessage message = new CefMessage();
    message.setId(id);
    message.setAction(action);
    message.setType("response");

    // Compute namespace (prefix before ':')
    String namespace = action.contains(":" ) ? action.substring(0, action.indexOf(':')) : action;
    switch (namespace) {
      case "fs" -> {
        return handleFsActions(action, id, payload, callback, browser, frame, message);
      }
      case "project" -> {
        return handleProjectActions(action, id, payload, callback, browser, frame, message);
      }
      case "conversation" -> {
        return handleConversationActions(action, id, payload, callback, browser, frame, message);
      }
      case "proxy" -> {
        return handleProxyActions(action, id, payload, callback, browser, frame, message);
      }
      case "copilotConfig" -> {
        return handleCopilotConfigActions(action, id, payload, callback, browser, frame, message);
      }
      case "skills", "skill" -> {
        return handleSkillActions(action, id, payload, callback, browser, frame, message);
      }
      case "chat" -> {
        return handleChatActions(action, id, payload, callback, browser, frame, message);
      }
      default -> {
        if ("model:ls".equals(action)) {
          message.setPayload(name2Vendor.keySet());
          message.setSuccess(true);
          sendResponse(callback, message);
        } else if ("build:info".equals(action)) {
            Map<String, String> properties = new HashMap<>();
            build.forEach(entry -> properties.put(entry.getKey(), entry.getValue()));
            message.setPayload(properties);
            message.setSuccess(true);
            sendResponse(callback, message);
        } else {
          log.warn("Unhandled action: {}", action);
        }
      }
    }
    return true;
  }

  private boolean handleFsActions(String action, String id, JsonNode payload, CefQueryCallback callback, CefBrowser browser, CefFrame frame, CefMessage message) throws JsonProcessingException {
    switch (action) {
      case "fs:select" -> {
        SwingUtilities.invokeLater(
            () -> {
              String selectMode = payload.path("mode").asText("file");
              JFileChooser chooser = new JFileChooser();
              switch (selectMode) {
                case "file" -> chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                case "folder" -> chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                case "all" -> chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                default -> {
                  sendError(callback, message, "Invalid selection mode: " + selectMode);
                  return;
                }
              }
              if (payload.has("defaultPath")) {
                String defaultPath = payload.path("defaultPath").asText();
                if (StringUtils.isNotBlank(defaultPath)) {
                  File file = new File(defaultPath);
                  if (file.exists() && file.isDirectory()) {
                    chooser.setCurrentDirectory(file);
                  }
                }
              }
              if (payload.has("multiple") && payload.path("multiple").asBoolean()) {
                chooser.setMultiSelectionEnabled(true);
              }
              int returnVal = chooser.showOpenDialog(null);
              if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                message.setPayload(Map.of("path", file.getAbsolutePath()));
                message.setSuccess(true);
                sendResponse(callback, message);
              } else {
                message.setSuccess(true);
                message.setPayload(Map.of("cancel", true));
                sendResponse(callback, message);
              }
            });
      }
      case "fs:ls" -> {
        String path = payload.path("path").asText();
        if (path == null || path.isEmpty()) {
          sendError(callback, message, "Path is required");
          return true;
        }

        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
          sendError(callback, message, "Invalid directory path");
          return true;
        }

        File[] files = dir.listFiles();
        java.util.List<Map<String, Object>> fileList = new java.util.ArrayList<>();
        if (files != null) {
          for (File file : files) {
            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("name", file.getName());
            fileInfo.put("path", file.getAbsolutePath());
            fileInfo.put("isDirectory", file.isDirectory());
            fileInfo.put("size", file.length());
            fileInfo.put("lastModified", file.lastModified());
            fileList.add(fileInfo);
          }
        }
        message.setPayload(fileList);
        message.setSuccess(true);
        sendResponse(callback, message);
      }
      default -> log.warn("Unhandled fs action: {}", action);
    }
    return true;
  }

  private boolean handleProjectActions(String action, String id, JsonNode payload, CefQueryCallback callback, CefBrowser browser, CefFrame frame, CefMessage message) throws JsonProcessingException {
    switch (action) {
      case "project:create" -> {
        String projectName = payload.path("name").asText();
        if (projectName == null || projectName.isEmpty()) {
          sendError(callback, message, "Project name is required");
          return true;
        }

        String workbench = copilotConfig.getWorkbench();
        if (workbench == null || workbench.isEmpty()) {
          sendError(callback, message, "Workbench path is not configured");
          return true;
        }

        File projectDir = new File(workbench, projectName);
        if (!projectDir.exists()) {
          if (!projectDir.mkdirs()) {
            sendError(callback, message, "Failed to create project directory: " + projectDir.getAbsolutePath());
            return true;
          }
        }

        ProjectRecord projectRecord = new ProjectRecord();
        projectRecord.setUid(UUID.randomUUID().toString());
        projectRecord.setName(projectName);
        projectRecord.setPath(projectDir.getAbsolutePath());
        projectMapper.insert(projectRecord);

        message.setPayload(projectRecord);
        message.setSuccess(true);
        sendResponse(callback, message);
      }
      case "project:ls" -> {
        java.util.List<ProjectRecord> projects = projectMapper.selectList(null);
        message.setPayload(projects);
        message.setSuccess(true);
        sendResponse(callback, message);
      }
      case "project:rename" -> {
        String projectUid = payload.path("project_uid").asText();
        String name = payload.path("name").asText();
        if (StringUtils.isBlank(projectUid) || StringUtils.isBlank(name)) {
          sendError(callback, message, "project_uid and name are required");
          return true;
        }

        ProjectRecord updateRecord = new ProjectRecord();
        updateRecord.setName(name);
        projectMapper.update(updateRecord, new LambdaQueryWrapper<ProjectRecord>()
                .eq(ProjectRecord::getUid, projectUid));

        message.setSuccess(true);
        sendResponse(callback, message);
      }
      case "project:delete" -> {
        String projectUid = payload.path("project_uid").asText();
        if (StringUtils.isBlank(projectUid)) {
          sendError(callback, message, "project_uid is required");
          return true;
        }

        // 1. 查询该项目下所有的会话
        List<ConversationRecord> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<ConversationRecord>().eq(ConversationRecord::getProjectUid, projectUid)
        );

        for (ConversationRecord conv : conversations) {
          // 2. 删除每个会话下的所有消息
          chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessageRecord>()
                  .eq(ChatMessageRecord::getConversationUid, conv.getUid()));
          // 3. 删除会话本身
          conversationMapper.deleteById(conv.getId());
        }

        // 4. 最后删除项目记录
        projectMapper.delete(new LambdaQueryWrapper<ProjectRecord>().eq(ProjectRecord::getUid, projectUid));

        message.setSuccess(true);
        sendResponse(callback, message);
      }
      default -> log.warn("Unhandled project action: {}", action);
    }
    return true;
  }

  private boolean handleConversationActions(String action, String id, JsonNode payload, CefQueryCallback callback, CefBrowser browser, CefFrame frame, CefMessage message) throws JsonProcessingException {
    switch (action) {
      case "conversation:create" -> {
        String projectUid = payload.path("project_uid").asText();
        if (projectUid == null || projectUid.isEmpty()) {
          sendError(callback, message, "project_uid is required");
          return true;
        }

        ConversationRecord conversationRecord = new ConversationRecord();
        conversationRecord.setUid(UUID.randomUUID().toString());
        conversationRecord.setProjectUid(projectUid);
        conversationRecord.setTitle("New Conversation");
        conversationMapper.insert(conversationRecord);

        message.setPayload(conversationRecord);
        message.setSuccess(true);
        sendResponse(callback, message);
      }
      case "conversation:ls" -> {
        String projectUid = payload.path("project_uid").asText();
        if (projectUid == null || projectUid.isEmpty()) {
          sendError(callback, message, "project_uid is required");
          return true;
        }

        java.util.List<ConversationRecord> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<ConversationRecord>()
                        .eq(ConversationRecord::getProjectUid, projectUid)
                        .orderByDesc(ConversationRecord::getCreateTime)
        );
        message.setPayload(conversations);
        message.setSuccess(true);
        sendResponse(callback, message);
      }
      case "conversation:rename" -> {
        String conversationUid = payload.path("conversation_uid").asText();
        String name = payload.path("name").asText();
        if (StringUtils.isBlank(conversationUid) || StringUtils.isBlank(name)) {
          sendError(callback, message, "conversation_uid and name are required");
          return true;
        }

        ConversationRecord updateRecord = new ConversationRecord();
        updateRecord.setTitle(name);
        conversationMapper.update(
            updateRecord,
            new LambdaQueryWrapper<ConversationRecord>()
                .eq(ConversationRecord::getUid, conversationUid));

        message.setSuccess(true);
        sendResponse(callback, message);
      }
      case "conversation:delete" -> {
        String conversationUid = payload.path("conversation_uid").asText();
        if (StringUtils.isBlank(conversationUid)) {
          sendError(callback, message, "conversation_uid is required");
          return true;
        }

        // 删除消息记录
        chatMessageMapper.delete(
            new LambdaQueryWrapper<ChatMessageRecord>()
                .eq(ChatMessageRecord::getConversationUid, conversationUid));

        // 删除会话记录
        conversationMapper.delete(
            new LambdaQueryWrapper<ConversationRecord>()
                .eq(ConversationRecord::getUid, conversationUid));

        message.setSuccess(true);
        sendResponse(callback, message);
      }
      default -> log.warn("Unhandled conversation action: {}", action);
    }
    return true;
  }

  private boolean handleProxyActions(String action, String id, JsonNode payload, CefQueryCallback callback, CefBrowser browser, CefFrame frame, CefMessage message) throws JsonProcessingException {
    switch (action) {
      case "proxy:save" -> {
        ProxyConfig proxyConfig = objectMapper.convertValue(payload, ProxyConfig.class);
        publisher.publishEvent(proxyConfig);
        message.setSuccess(true);
        sendResponse(callback, message);
      }
      case "proxy:get" -> {
        ProxyConfig proxyConfig = wanquProxySelector.getProxyConfig();
        message.setPayload(proxyConfig);
        message.setSuccess(true);
        sendResponse(callback, message);
      }
      default -> log.warn("Unhandled proxy action: {}", action);
    }
    return true;
  }

  private boolean handleCopilotConfigActions(String action, String id, JsonNode payload, CefQueryCallback callback, CefBrowser browser, CefFrame frame, CefMessage message) throws JsonProcessingException {
    switch (action) {
      case "copilotConfig:get" -> {
        message.setPayload(copilotConfig);
        message.setSuccess(true);
        sendResponse(callback, message);
      }
      case "copilotConfig:save" -> {
        log.info("Received copilotConfig: {}", payload);
        CopilotConfig newConfig = objectMapper.convertValue(payload, CopilotConfig.class);
        if (newConfig == null) {
          sendError(callback, message, "Invalid config payload");
          return true;
        }
        publisher.publishEvent(newConfig);
        message.setSuccess(true);
        sendResponse(callback, message);
      }
      default -> log.warn("Unhandled copilotConfig action: {}", action);
    }
    return true;
  }

  private boolean handleSkillActions(String action, String id, JsonNode payload, CefQueryCallback callback, CefBrowser browser, CefFrame frame, CefMessage message) throws JsonProcessingException {
    switch (action) {
      case "skills:ls" -> {
        FileSystemSkillRegistry fileSystemSkillRegistry =
            FileSystemSkillRegistry.builder()
                .userSkillsDirectory(
                    Paths.get(copilotConfig.getWorkdir(), "skills", "user")
                        .toFile()
                        .getAbsolutePath())
                .autoLoad(true)
                .build();

        List<SkillMetadata> skills = fileSystemSkillRegistry.listAll();
        message.setPayload(skills);
        message.setSuccess(true);
        sendResponse(callback, message);
      }
      case "skill:install" -> {
        String skillPath = payload.path("path").asText();
        log.info("Received skill install request, path: {}", skillPath);
        File skillFile = new File(skillPath);
        if (!skillFile.exists()) {
          sendError(callback, message, "Skill file does not exist: " + skillPath);
          return true;
        }
        try {
          if (skillFile.isFile() && skillPath.endsWith(".zip")) {
            ZipUtil.unzip(
                skillFile, Paths.get(copilotConfig.getWorkdir(), "skills", "user").toFile());
            message.setSuccess(true);
            sendResponse(callback, message);
          } else if (skillFile.isDirectory()) {
            File destDir = Paths.get(copilotConfig.getWorkdir(), "skills", "user").toFile();
            cn.hutool.core.io.FileUtil.copy(skillFile, destDir, true);
            message.setSuccess(true);
            log.info(
                "copy skill directory from {} to {}",
                skillFile.getAbsolutePath(),
                destDir.getAbsolutePath());
            sendResponse(callback, message);
          } else {
            sendError(callback, message, "Invalid skill file: " + skillPath);
            return true;
          }
        } catch (Exception e) {
          log.error("Failed to install skill", e);
          sendError(callback, message, "Failed to install skill: " + e.getMessage());
          return true;
        }
      }

      case "skill:uninstall" -> {
        String skillPath = payload.path("skillPath").asText();
        if (StringUtils.isBlank(skillPath)) {
          sendError(callback, message, "skillPath is required");
          return true;
        }
        File skillFile = new File(skillPath);
        if (!skillFile.exists()) {
          sendError(callback, message, "Skill path does not exist: " + skillPath);
          return true;
        }
        try {
          boolean result = cn.hutool.core.io.FileUtil.del(skillFile);
          log.info("Uninstall skill result: {}, path: {}", result, skillPath);
          message.setSuccess(result);
          sendResponse(callback, message);
        } catch (Exception e) {
          log.error("Failed to uninstall skill", e);
          sendError(callback, message, "Failed to uninstall skill: " + e.getMessage());
        }
      }
      default -> log.warn("Unhandled skill action: {}", action);
    }
    return true;
  }

  private boolean handleChatActions(String action, String id, JsonNode payload, CefQueryCallback callback, CefBrowser browser, CefFrame frame, CefMessage message) throws JsonProcessingException {
    switch (action) {
      case "chat:ls" -> {
        String conversationUid = payload.path("conversation_uid").asText();
        if (conversationUid == null || conversationUid.isEmpty()) {
          sendError(callback, message, "conversation_uid is required");
          return true;
        }

        List<ChatMessageRecord> messages = chatService.getHistory(conversationUid);
        message.setPayload(messages);
        message.setSuccess(true);
        sendResponse(callback, message);
      }
      case "chat:append" -> {
        ChatRequest chatRequest = objectMapper.convertValue(payload, ChatRequest.class);
        log.info("Received chat append request: {}", chatRequest);

        ChatMessageRecord chatMessageRecord = new ChatMessageRecord();
        chatMessageRecord.setUid(UUID.randomUUID().toString());
        chatMessageRecord.setConversationUid(chatRequest.getConversationUid());
        chatMessageRecord.setMessageType(MessageType.USER);
        chatMessageRecord.setText(chatRequest.getContent());
        chatMessageMapper.insert(chatMessageRecord);

        ChatMessageRecord assistantMessageRecord = new ChatMessageRecord();
        assistantMessageRecord.setUid(UUID.randomUUID().toString());
        assistantMessageRecord.setConversationUid(chatRequest.getConversationUid());
        assistantMessageRecord.setMessageType(MessageType.ASSISTANT);

        // Delegate the streaming/flux processing to a helper to keep this method short
        processChatFlux(chatRequest, assistantMessageRecord, id, browser, frame);
        return true;
      }
      default -> {
        log.warn("Unhandled chat action: {}", action);
      }
    }
    return true;
  }

  // extracted from handleChatActions to reduce length
  private void processChatFlux(ChatRequest chatRequest, ChatMessageRecord assistantMessageRecord, String id, CefBrowser browser, CefFrame frame) {
    StringBuilder assistantContentBuilder = new StringBuilder();

    Flux<Message> flux = chatService.chat(chatRequest);

    flux.doFirst(
            () -> {
              CefMessage event = new CefMessage();
              event.setType("event");
              event.setAction("chat:message:streaming");
              event.setId(id);
              event.setPayload(Map.of("conversation_uid", chatRequest.getConversationUid(), "msg_uid", assistantMessageRecord.getUid()));
              event.setSuccess(true);
              try {
                String json = objectMapper.writeValueAsString(event);
                String script = String.format("window.__wanquBridge_receive(%s)", json);
                browser.executeJavaScript(script, frame.getURL(), 0);
              } catch (JsonProcessingException e) {
                log.error("Failed to serialize chat message streaming event", e);
              }
            })
        .doOnError(
            error -> {
              log.error("chat:message:streaming error", error);
              ChatMessageRecord errorRecord = new ChatMessageRecord();
              errorRecord.setUid(UUID.randomUUID().toString());
              errorRecord.setConversationUid(chatRequest.getConversationUid());
              errorRecord.setMessageType(MessageType.ASSISTANT);
              errorRecord.setMetadata(Map.of("conversation_uid", chatRequest.getConversationUid(), "msg_uid", assistantMessageRecord.getUid()));
              errorRecord.setText(error.getMessage());

              CefMessage event = new CefMessage();
              event.setType("event");
              event.setAction("chat:message:streamingError");
              event.setId(id);
              event.setPayload(errorRecord);
              event.setSuccess(true);
              try {
                String json = objectMapper.writeValueAsString(event);
                String script = String.format("window.__wanquBridge_receive(%s)", json);
                browser.executeJavaScript(script, frame.getURL(), 0);
              } catch (JsonProcessingException e) {
                log.error("Failed to serialize chat message streaming event", e);
              }
              chatMessageMapper.insert(errorRecord);
            })
        .doOnComplete(
            () -> {
              assistantMessageRecord.setText(assistantContentBuilder.toString());
              chatMessageMapper.insert(assistantMessageRecord);

              CefMessage event = new CefMessage();
              event.setType("event");
              event.setAction("chat:message:streamingComplete");
              event.setId(id);
              event.setPayload(Map.of("conversation_uid", chatRequest.getConversationUid(), "msg_uid", assistantMessageRecord.getUid()));
              event.setSuccess(true);

              try {
                String json = objectMapper.writeValueAsString(event);
                String script = String.format("window.__wanquBridge_receive(%s)", json);
                browser.executeJavaScript(script, frame.getURL(), 0);
              } catch (JsonProcessingException e) {
                log.error("Failed to serialize chat message streaming event", e);
              }
            })
        .subscribe(
            chatMessage -> {
              chatMessage.getMetadata().put("conversation", chatRequest.getConversationUid());
              chatMessage.getMetadata().put("msg_uid", assistantMessageRecord.getUid());

              CefMessage event = new CefMessage();
              event.setType("event");
              event.setAction("chat:message");
              event.setId(id);
              event.setPayload(chatMessage);
              event.setSuccess(true);

              if (StringUtils.isNotBlank(chatMessage.getText())) {
                assistantContentBuilder.append(chatMessage.getText());
              }

              try {
                String json = objectMapper.writeValueAsString(event);
                String script = String.format("window.__wanquBridge_receive(%s)", json);
                browser.executeJavaScript(script, frame.getURL(), 0);
              } catch (JsonProcessingException e) {
                log.error("Failed to serialize chat message event", e);
              }

              ChatMessageRecord item = chatService.convert(chatMessage);
              item.setConversationUid(chatRequest.getConversationUid());
              if (StringUtils.isNotBlank(item.getToolCalls()) || StringUtils.isNotBlank(item.getResponses())) {
                chatMessageMapper.insert(item);
              }
            });
  }

  boolean doResponse(String action, String id, JsonNode payload, CefQueryCallback callback) {
    return true;
  }

  boolean doEvent(String action, String id, JsonNode payload, CefQueryCallback callback) {
    return true;
  }
}
