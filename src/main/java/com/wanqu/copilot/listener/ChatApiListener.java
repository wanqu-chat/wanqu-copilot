package com.wanqu.copilot.listener;

import com.wanqu.copilot.entity.ApiClients;
import com.wanqu.copilot.entity.ApiCredential;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Slf4j
public class ChatApiListener {

  @Autowired ApplicationEventPublisher publisher;

  @Autowired Map<String, String> name2Vendor;

  @Autowired ApiClients apiClients;

  @Autowired RestClient.Builder restClientBuilder;

  @Autowired WebClient.Builder webClientBuilder;

  @Autowired Map<String, ChatModel> chatModelMap;

  @EventListener
  public void updateApiProvider(ApiCredential credential) {
    if (!credential.isEnabled()) {
      log.info("API credential is disabled, removing... {}", credential);
      String provider = credential.getProvider().toString();

      // 1. 移除 chatModelMap 中相同 provider 的 chatModel
      chatModelMap.remove(provider);

      // 2. 移除 name2Vendor 中所有 value 与 provider 相同的项目
      name2Vendor.values().removeIf(v -> v.equalsIgnoreCase(provider));

      return;
    }
    switch (credential.getProvider()) {
      case openai -> {
        publisher.publishEvent(
            OpenAiApi.builder()
                .apiKey(credential.getProperties().get("apiKey").toString())
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .build());
      }
      case deepseek -> {
        publisher.publishEvent(
            DeepSeekApi.builder()
                .apiKey(credential.getProperties().get("apiKey").toString())
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .build());
      }
      default -> {
        log.warn("unsupported API provider: {}", credential.getProvider());
      }
    }
  }

  @EventListener
  public void addOpenAiApi(OpenAiApi api) {
    apiClients.setOpenAiApi(api);
    log.info("Received OpenAiApi event, publishing OpenAiChatModel...");
    publisher.publishEvent(OpenAiChatModel.builder().openAiApi(api).build());
    for (OpenAiApi.ChatModel model : OpenAiApi.ChatModel.values()) {
      name2Vendor.put(model.getName(), "openai");
    }
  }

  @EventListener
  public void addDeepSeekApi(DeepSeekApi api) {
    apiClients.setDeepSeekApi(api);
    log.info("Received DeepSeekApi event, publishing DeepSeekChatModel...");
    publisher.publishEvent(DeepSeekChatModel.builder().deepSeekApi(api).build());
    for (DeepSeekApi.ChatModel model : DeepSeekApi.ChatModel.values()) {
      name2Vendor.put(model.getName(), "deepseek");
    }
  }
}
