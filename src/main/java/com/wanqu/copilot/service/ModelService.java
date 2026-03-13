package com.wanqu.copilot.service;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.shelltool.ShellToolAgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.todolist.TodoListInterceptor;
import com.alibaba.cloud.ai.graph.agent.tools.GlobSearchTool;
import com.alibaba.cloud.ai.graph.agent.tools.GrepSearchTool;
import com.alibaba.cloud.ai.graph.agent.tools.ShellTool2;
import com.alibaba.cloud.ai.graph.checkpoint.savers.file.FileSystemSaver;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import com.wanqu.copilot.entity.ChatRequest;
import com.wanqu.copilot.entity.CopilotConfig;
import com.wanqu.copilot.util.PathUtil;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ModelService {

  @Autowired Map<String, ChatModel> chatModelMap;

  @Autowired Map<String, String> name2Vendor;

  @Autowired Map<String, ReactAgent> reactAgentMap;

  @Autowired CopilotConfig copilotConfig;

  public ReactAgent getAgent(ChatRequest chatRequest) {
    String modelName = chatRequest.getModel();
    String vendor = name2Vendor.get(modelName);

    return reactAgentMap.computeIfAbsent(vendor, v -> createAgent(vendor, chatRequest));
  }

  ReactAgent createAgent(String vendor, ChatRequest chatRequest) {
    ChatModel chatModel = chatModelMap.get(vendor);
    if (chatModel == null) {
      log.error("No chat model found for vendor: {}", vendor);
      throw new RuntimeException("No chat model found for vendor: " + vendor);
    }
    Path checkpointPath =
        Paths.get(
            copilotConfig.getWorkdir(),
            "checkpoints",
            chatRequest.getProjectUid(),
            chatRequest.getConversationUid());

    PathUtil.ensureFolder(checkpointPath);

    Path projectRoot = Paths.get(copilotConfig.getWorkdir(), chatRequest.getProjectUid());
    PathUtil.ensureFolder(projectRoot);

    String projectRootPath = projectRoot.toFile().getAbsolutePath();

    FileSystemSaver fileSystemSaver =
        FileSystemSaver.builder().targetFolder(checkpointPath).build();

    Path userSkill = Paths.get(copilotConfig.getWorkdir(), "skills", "user");
    Path projectSkill =
        Paths.get(copilotConfig.getWorkdir(), "skills", chatRequest.getProjectUid());

    PathUtil.ensureFolder(userSkill);
    PathUtil.ensureFolder(projectSkill);

    SkillRegistry skillRegistry =
        FileSystemSkillRegistry.builder()
            .userSkillsDirectory(userSkill.toFile().getAbsolutePath())
            .projectSkillsDirectory(projectSkill.toFile().getAbsolutePath())
            .autoLoad(true)
            .build();

    SkillsAgentHook skillsAgentHook =
        SkillsAgentHook.builder().skillRegistry(skillRegistry).build();

    ShellTool2 shellTool2 = ShellTool2.builder(projectRoot.toFile().getAbsolutePath()).build();
    ShellToolAgentHook shellHook = ShellToolAgentHook.builder().shellTool2(shellTool2).build();

    // todo: 可以更换更便宜的model
    SummarizationHook summarizationHook =
        SummarizationHook.builder()
            .model(chatModel)
            .maxTokensBeforeSummary(2000)
            .messagesToKeep(10)
            .build();

    //        ModelCallLimitHook modelCallLimitHook =
    // ModelCallLimitHook.builder().runLimit(5).build();

    TodoListInterceptor todoListInterceptor = TodoListInterceptor.builder().build();
    return ReactAgent.builder()
        .name("Wanqu Copilot")
        .model(chatModel)
        .saver(fileSystemSaver)
        .tools(
            GrepSearchTool.builder(projectRootPath).build(),
            GlobSearchTool.builder(projectRootPath).build()
            //                        PythonTool.createPythonToolCallback(PythonTool.DESCRIPTION)
            )
        .hooks(skillsAgentHook, shellHook, summarizationHook)
        .interceptors(todoListInterceptor)
        .build();
  }
}
