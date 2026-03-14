package com.wanqu.copilot.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class AgentConfig {
  @Bean
  Map<String, ReactAgent> reactAgentMap() {
    return new HashMap<>();
  }
}
