package com.wanqu.copilot.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.SqliteChatMemoryRepositoryDialect;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatModelConfig {

  @Bean
  List<ChatModel> chatModelList() {
    return new ArrayList<>();
  }

  @Bean
  Map<String, ChatModel> chatModelMap() {
    return new HashMap<>();
  }

  @Bean
  Map<String, String> name2Vendor() {
    return new HashMap<>();
  }

  @Bean
  JdbcChatMemoryRepository jdbcChatMemoryRepository(DataSource dataSource) {
    return JdbcChatMemoryRepository.builder()
        .dataSource(dataSource)
        .dialect(new SqliteChatMemoryRepositoryDialect())
        .build();
  }

  @Bean
  ChatMemory chatMemory(JdbcChatMemoryRepository jdbcChatMemoryRepository) {
    return MessageWindowChatMemory.builder()
        .chatMemoryRepository(jdbcChatMemoryRepository)
        .maxMessages(10)
        .build();
  }
}
