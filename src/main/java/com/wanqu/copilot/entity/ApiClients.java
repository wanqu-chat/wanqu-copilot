package com.wanqu.copilot.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class ApiClients {
  OpenAiApi openAiApi;
  DeepSeekApi deepSeekApi;
}
