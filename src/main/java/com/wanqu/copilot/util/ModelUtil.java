package com.wanqu.copilot.util;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.openai.OpenAiChatModel;

public class ModelUtil {

  public static String chatModelVendor(ChatModel chatModel) {
    if (chatModel instanceof DeepSeekChatModel) {
      return "deepseek";
    } else if (chatModel instanceof OpenAiChatModel) {
      return "openai";
    } else {
      return "unknown";
    }
  }
}
