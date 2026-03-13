package com.wanqu.copilot.service;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanqu.copilot.entity.ChatRequest;
import com.wanqu.copilot.mapper.ChatMessageMapper;
import com.wanqu.copilot.record.ChatMessageRecord;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@Slf4j
public class ChatService {

  @Autowired ObjectMapper objectMapper;

  @Autowired ModelService modelService;

  @Autowired ChatMessageMapper chatMessageMapper;

  public List<ChatMessageRecord> getHistory(String conversationUid) {
    return chatMessageMapper.selectList(
        new LambdaQueryWrapper<ChatMessageRecord>()
            .eq(ChatMessageRecord::getConversationUid, conversationUid)
            .orderByAsc(ChatMessageRecord::getId));
  }

  public Flux<Message> chat(ChatRequest request) {

    ReactAgent agent = modelService.getAgent(request);
    RunnableConfig runnableConfig =
        RunnableConfig.builder().threadId(request.getConversationUid()).build();
    try {
      return agent.streamMessages(request.getContent(), runnableConfig);
    } catch (GraphRunnerException e) {
      log.info("Error running agent: {}", e.getMessage());
      return Flux.empty();
    }
  }

  public ChatMessageRecord convert(Message message) {
    ChatMessageRecord record = new ChatMessageRecord();
    record.setUid(UUID.randomUUID().toString());
    record.setMessageType(message.getMessageType());
    record.setText(message.getText());
    if (message instanceof AssistantMessage) {
      convert(record, (AssistantMessage) message);
    } else if (message instanceof ToolResponseMessage) {
      convert(record, (ToolResponseMessage) message);
    } else {
      log.error("unknown message type: {}", message.getClass().getCanonicalName());
    }
    return record;
  }

  protected void convert(ChatMessageRecord record, AssistantMessage message) {
    if (message.hasToolCalls()) {
      try {
        record.setToolCalls(objectMapper.writeValueAsString(message.getToolCalls()));
      } catch (JsonProcessingException e) {
        log.error("Failed to serialize tool calls", e);
      }
    }
  }

  protected void convert(ChatMessageRecord record, ToolResponseMessage message) {
    List<ToolResponseMessage.ToolResponse> responses = message.getResponses();
    try {
      record.setResponses(objectMapper.writeValueAsString(responses));
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize tool responses", e);
    }
  }
}
