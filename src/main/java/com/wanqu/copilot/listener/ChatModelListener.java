package com.wanqu.copilot.listener;

import com.wanqu.copilot.util.ModelUtil;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ChatModelListener {
  @Autowired List<ChatModel> chatModelList;

  @Autowired Map<String, ChatModel> chatModelMap;

  @EventListener
  public void addChatModel(ChatModel chatModel) {
    if (chatModelList.contains(chatModel)) {
      log.info("exist chat model, ignore");
      return;
    }
    chatModelList.add(chatModel);
    String vendor = ModelUtil.chatModelVendor(chatModel);
    chatModelMap.put(vendor, chatModel);
    log.info("add chat model, vendor: {}", vendor);
  }
}
