package com.wanqu.copilot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanqu.copilot.entity.ApiCredential;
import com.wanqu.copilot.entity.CopilotConfig;
import com.wanqu.copilot.entity.ProxyConfig;
import com.wanqu.copilot.mapper.ConfigMapper;
import com.wanqu.copilot.record.ConfigRecord;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ConfigService {

  @Autowired ConfigMapper configMapper;

  @Autowired CopilotConfig copilotConfig;

  @Autowired ProxyConfig proxyConfig;

  @Autowired ObjectMapper objectMapper;

  @Autowired ApplicationEventPublisher publisher;

  /**
   * 获取配置项的值
   *
   * @param namespace 命名空间
   * @param key 键
   * @return 配置项的值，如果不存在则返回 null
   */
  public String getValue(String namespace, String key) {
    ConfigRecord record =
        configMapper.selectOne(
            new LambdaQueryWrapper<ConfigRecord>()
                .eq(ConfigRecord::getNamespace, namespace)
                .eq(ConfigRecord::getKey, key));
    return record != null ? record.getValue() : null;
  }

  /**
   * 设置配置项的值（插入或更新）
   *
   * @param namespace 命名空间
   * @param key 键
   * @param value 值
   */
  public void setValue(String namespace, String key, String value) {
    ConfigRecord record =
        configMapper.selectOne(
            new LambdaQueryWrapper<ConfigRecord>()
                .eq(ConfigRecord::getNamespace, namespace)
                .eq(ConfigRecord::getKey, key));

    if (record != null) {
      record.setValue(value);
      configMapper.updateById(record);
      log.info("Updated config: namespace={}, key={}, value={}", namespace, key, value);
    } else {
      record = new ConfigRecord();
      record.setUid(UUID.randomUUID().toString());
      record.setNamespace(namespace);
      record.setKey(key);
      record.setValue(value);
      configMapper.insert(record);
      log.info("Inserted config: namespace={}, key={}, value={}", namespace, key, value);
    }
  }

  @EventListener
  public void updateCopilotConfig(CopilotConfig config) throws JsonProcessingException {
    BeanUtils.copyProperties(config, copilotConfig);
    setValue("app", "copilotConfig", objectMapper.writeValueAsString(config));
    List<ApiCredential> crendentials = config.getCredentials();
    if (CollectionUtils.isNotEmpty(crendentials)) {
      crendentials.stream().forEach(publisher::publishEvent);
    }
  }

  @EventListener
  public void updateProxyConfig(ProxyConfig config) throws JsonProcessingException {
    BeanUtils.copyProperties(config, proxyConfig);
    setValue("app", "proxy", objectMapper.writeValueAsString(config));
  }
}
