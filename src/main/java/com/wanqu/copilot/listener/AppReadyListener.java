package com.wanqu.copilot.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanqu.copilot.entity.CopilotConfig;
import com.wanqu.copilot.entity.ProxyConfig;
import com.wanqu.copilot.service.ConfigService;
import java.io.File;
import java.nio.file.Paths;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AppReadyListener implements ApplicationListener<ApplicationReadyEvent> {

  @Autowired ApplicationEventPublisher publisher;

  @Autowired ConfigService configService;

  @Autowired ObjectMapper objectMapper;

  void initCopilotConfig() {
    String copilotConfigStr = configService.getValue("app", "copilotConfig");
    CopilotConfig config = null;
    if (StringUtils.isNotBlank(copilotConfigStr)) {
      try {
        log.info("get copilot config from db: {}", copilotConfigStr);
        config = objectMapper.readValue(copilotConfigStr, CopilotConfig.class);
        publisher.publishEvent(config);
      } catch (JsonProcessingException e) {
        log.error("Failed to parse copilot config: {}", copilotConfigStr, e);
      }
    } else {
      config = new CopilotConfig();
      config.setCredentials(Collections.emptyList());
      log.info("use default copilot config: {}", config);
      publisher.publishEvent(config);
    }

    File workbenchDir = Paths.get(config.getWorkbench()).toFile();
    if (!workbenchDir.exists()) {
      boolean created = workbenchDir.mkdirs();
      if (created) {
        log.info("Created workbench directory: {}", config.getWorkbench());
      } else {
        log.warn("Failed to create workbench directory: {}", config.getWorkbench());
      }
    }
    File workDir = Paths.get(config.getWorkdir()).toFile();
    if (!workDir.exists()) {
      boolean created = workDir.mkdirs();
      if (created) {
        log.info("Created workdir directory: {}", config.getWorkdir());
      } else {
        log.warn("Failed to create workdir directory: {}", config.getWorkdir());
      }
    }
  }

  void initProxy() {
    String proxyConfigStr = configService.getValue("app", "proxy");

    if (StringUtils.isNotBlank(proxyConfigStr)) {
      try {
        ProxyConfig proxyConfig = objectMapper.readValue(proxyConfigStr, ProxyConfig.class);
        publisher.publishEvent(proxyConfig);
      } catch (JsonProcessingException e) {
        log.error("Failed to parse proxy config: {}", proxyConfigStr, e);
        ProxyConfig config = new ProxyConfig();
        config.setEnabled(Boolean.FALSE);
        publisher.publishEvent(config);
      }
    } else {
      ProxyConfig config = new ProxyConfig();
      config.setEnabled(Boolean.FALSE);
      publisher.publishEvent(config);
    }
  }

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    initCopilotConfig();
    initProxy();
  }
}
