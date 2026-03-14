package com.wanqu.copilot.browser;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Delegates handling of various namespaced actions. Extracted from CefMessageHandler to
 * reduce cyclomatic complexity and method length.
 */
@Component
@Slf4j
public class RequestHandlerDelegator {
  @Autowired private ObjectMapper objectMapper;

  @Autowired private ApplicationEventPublisher publisher;

  @Autowired private Map<String, String> name2Vendor;

  @Autowired private WanquProxySelector wanquProxySelector;

  @Autowired private CopilotConfig copilotConfig;

  @Autowired private ProjectMapper projectMapper;

  @Autowired private ConversationMapper conversationMapper;

  @Autowired private ChatService chatService;

  @Autowired private ChatMessageMapper chatMessageMapper;

  @Autowired private BuildProperties build;

  /**
   * Handle filesystem related actions: fs:select, fs:ls
   */
  public boolean handleFsActions(
      String action,
      String id,
      JsonNode payload,
      CefQueryCallback callback,
      CefBrowser browser,
      CefFrame frame,
      CefMessage message)
      throws JsonProcessingException {
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
                  message.setSuccess(false);
                  message.setError("Invalid selection mode: " + selectMode);
                  sendSync(callback, message);
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
                sendSync(callback, message);
              } else {
                message.setSuccess(true);
                message.setPayload(Map.of("cancel", true));
                sendSync(callback, message);
              }
            });
      }
      case "fs:ls" -> {
        String path = payload.path("path").asText();
        if (path == null || path.isEmpty()) {
          message.setSuccess(false);
          message.setError("Path is required");
          sendSync(callback, message);
          return true;
        }

        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
          message.setSuccess(false);
          message.setError("Invalid directory path");
          sendSync(callback, message);
          return true;
        }

        File[] files = dir.listFiles();
        List<Map<String, Object>> fileList = new java.util.ArrayList<>();
        if (files != null) {
          for (File f : files) {
            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("name", f.getName());
            fileInfo.put("path", f.getAbsolutePath());
            fileInfo.put("isDirectory", f.isDirectory());
            fileInfo.put("size", f.length());
            fileInfo.put("lastModified", f.lastModified());
            fileList.add(fileInfo);
          }
        }
        message.setPayload(fileList);
        message.setSuccess(true);
        sendSync(callback, message);
      }
      default -> log.warn("Unhandled fs action: {}", action);
    }
    return true;
  }

  private void sendSync(CefQueryCallback callback, CefMessage message) {
    try {
      String r = objectMapper.writeValueAsString(message);
      callback.success(r);
    } catch (JsonProcessingException e) {
      log.error("Failed to send sync response", e);
      callback.failure(-1, e.getMessage());
    }
  }

  /**
   * Project related handlers
   */
  public boolean handleProjectActions(
      String action,
      String id,
      JsonNode payload,
      CefQueryCallback callback,
      CefBrowser browser,
      CefFrame frame,
      CefMessage message)
      throws JsonProcessingException {
    switch (action) {
      case "project:create" -> {
        String projectName = payload.path("name").asText();
        if (projectName == null || projectName.isEmpty()) {
          message.setSuccess(false);
          message.setError("Project name is required");
          sendSync(callback, message);
          return true;
        }

        String workbench = copilotConfig.getWorkbench();
        if (workbench == null || workbench.isEmpty()) {
          message.setSuccess(false);
          message.setError("Workbench path is not configured");
          sendSync(callback, message);
          return true;
        }

        File projectDir = new File(workbench, projectName);
        if (!projectDir.exists()) {
          if (!projectDir.mkdirs()) {
            message.setSuccess(false);
            message.setError("Failed to create project directory: " + projectDir.getAbsolutePath());
            sendSync(callback, message);
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
        sendSync(callback, message);
      }
      case "project:ls" -> {
        List<ProjectRecord> projects = projectMapper.selectList(null);
        message.setPayload(projects);
        message.setSuccess(true);
        sendSync(callback, message);
      }
      case "project:rename" -> {
        String projectUid = payload.path("project_uid").asText();
        String name = payload.path("name").asText();
        if (StringUtils.isBlank(projectUid) || StringUtils.isBlank(name)) {
          message.setSuccess(false);
          message.setError("project_uid and name are required");
          sendSync(callback, message);
          return true;
        }

        ProjectRecord updateRecord = new ProjectRecord();
        updateRecord.setName(name);
        projectMapper.update(updateRecord, new LambdaQueryWrapper<ProjectRecord>()
                .eq(ProjectRecord::getUid, projectUid));

        message.setSuccess(true);
        sendSync(callback, message);
      }
      case "project:delete" -> {
        String projectUid = payload.path("project_uid").asText();
        if (StringUtils.isBlank(projectUid)) {
          message.setSuccess(false);
          message.setError("project_uid is required");
          sendSync(callback, message);
          return true;
        }

        List<ConversationRecord> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<ConversationRecord>().eq(ConversationRecord::getProjectUid, projectUid)
        );

        for (ConversationRecord conv : conversations) {
          chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessageRecord>()
                  .eq(ChatMessageRecord::getConversationUid, conv.getUid()));
          conversationMapper.deleteById(conv.getId());
        }

        projectMapper.delete(new LambdaQueryWrapper<ProjectRecord>().eq(ProjectRecord::getUid, projectUid));

        message.setSuccess(true);
        sendSync(callback, message);
      }
      default -> log.warn("Unhandled project action: {}", action);
    }
    return true;
  }

  /**
   * Conversation related handlers
   */
  public boolean handleConversationActions(
      String action,
      String id,
      JsonNode payload,
      CefQueryCallback callback,
      CefBrowser browser,
      CefFrame frame,
      CefMessage message)
      throws JsonProcessingException {
    switch (action) {
      case "conversation:create" -> {
        String projectUid = payload.path("project_uid").asText();
        if (projectUid == null || projectUid.isEmpty()) {
          message.setSuccess(false);
          message.setError("project_uid is required");
          sendSync(callback, message);
          return true;
        }

        ConversationRecord conversationRecord = new ConversationRecord();
        conversationRecord.setUid(UUID.randomUUID().toString());
        conversationRecord.setProjectUid(projectUid);
        conversationRecord.setTitle("New Conversation");
        conversationMapper.insert(conversationRecord);

        message.setPayload(conversationRecord);
        message.setSuccess(true);
        sendSync(callback, message);
      }
      case "conversation:ls" -> {
        String projectUid = payload.path("project_uid").asText();
        if (projectUid == null || projectUid.isEmpty()) {
          message.setSuccess(false);
          message.setError("project_uid is required");
          sendSync(callback, message);
          return true;
        }

        List<ConversationRecord> conversations = conversationMapper.selectList(
            new LambdaQueryWrapper<ConversationRecord>()
                .eq(ConversationRecord::getProjectUid, projectUid)
                .orderByDesc(ConversationRecord::getCreateTime)
        );
        message.setPayload(conversations);
        message.setSuccess(true);
        sendSync(callback, message);
      }
      case "conversation:rename" -> {
        String conversationUid = payload.path("conversation_uid").asText();
        String name = payload.path("name").asText();
        if (StringUtils.isBlank(conversationUid) || StringUtils.isBlank(name)) {
          message.setSuccess(false);
          message.setError("conversation_uid and name are required");
          sendSync(callback, message);
          return true;
        }

        ConversationRecord updateRecord = new ConversationRecord();
        updateRecord.setTitle(name);
        conversationMapper.update(
            updateRecord,
            new LambdaQueryWrapper<ConversationRecord>().eq(ConversationRecord::getUid, conversationUid));

        message.setSuccess(true);
        sendSync(callback, message);
      }
      case "conversation:delete" -> {
        String conversationUid = payload.path("conversation_uid").asText();
        if (StringUtils.isBlank(conversationUid)) {
          message.setSuccess(false);
          message.setError("conversation_uid is required");
          sendSync(callback, message);
          return true;
        }

        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessageRecord>()
                .eq(ChatMessageRecord::getConversationUid, conversationUid));
        conversationMapper.delete(new LambdaQueryWrapper<ConversationRecord>()
                .eq(ConversationRecord::getUid, conversationUid));

        message.setSuccess(true);
        sendSync(callback, message);
      }
      default -> log.warn("Unhandled conversation action: {}", action);
    }
    return true;
  }

  /**
   * Proxy related handlers
   */
  public boolean handleProxyActions(
      String action,
      String id,
      JsonNode payload,
      CefQueryCallback callback,
      CefBrowser browser,
      CefFrame frame,
      CefMessage message)
      throws JsonProcessingException {
    switch (action) {
      case "proxy:save" -> {
        ProxyConfig proxyConfig = objectMapper.convertValue(payload, ProxyConfig.class);
        publisher.publishEvent(proxyConfig);
        message.setSuccess(true);
        sendSync(callback, message);
      }
      case "proxy:get" -> {
        // Return current proxy config if available
        ProxyConfig cfg = wanquProxySelector.getProxyConfig();
        message.setPayload(cfg);
        message.setSuccess(true);
        sendSync(callback, message);
      }
      default -> log.warn("Unhandled proxy action: {}", action);
    }
    return true;
  }

  /**
   * Copilot configuration handlers
   */
  public boolean handleCopilotConfigActions(
      String action,
      String id,
      JsonNode payload,
      CefQueryCallback callback,
      CefBrowser browser,
      CefFrame frame,
      CefMessage message)
      throws JsonProcessingException {
    switch (action) {
      case "copilotConfig:get" -> {
        message.setPayload(copilotConfig);
        message.setSuccess(true);
        sendSync(callback, message);
      }
      case "copilotConfig:update" -> {
        String workbench = payload.path("workbench").asText();
        if (StringUtils.isNotBlank(workbench)) {
          copilotConfig.setWorkbench(workbench);
        }
        // Only update known fields here; unknown fields are ignored to avoid calling non-existent setters
        message.setSuccess(true);
        sendSync(callback, message);
      }
      default -> log.warn("Unhandled copilot config action: {}", action);
    }
    return true;
  }

  /**
   * Skill related handlers
   */
  public boolean handleSkillActions(
      String action,
      String id,
      JsonNode payload,
      CefQueryCallback callback,
      CefBrowser browser,
      CefFrame frame,
      CefMessage message)
      throws JsonProcessingException {
    switch (action) {
      case "skill:install" -> {
        String skillName = payload.path("name").asText();
        if (StringUtils.isBlank(skillName)) {
          message.setSuccess(false);
          message.setError("Skill name is required");
          sendSync(callback, message);
          return true;
        }

        // Implement skill installation logic here

        message.setSuccess(true);
        sendSync(callback, message);
      }
      case "skill:uninstall" -> {
        String skillName = payload.path("name").asText();
        if (StringUtils.isBlank(skillName)) {
          message.setSuccess(false);
          message.setError("Skill name is required");
          sendSync(callback, message);
          return true;
        }

        // Implement skill uninstallation logic here

        message.setSuccess(true);
        sendSync(callback, message);
      }
      case "skill:list" -> {
        // Implement logic to list installed skills

        message.setSuccess(true);
        sendSync(callback, message);
      }
      default -> log.warn("Unhandled skill action: {}", action);
    }
    return true;
  }
}
