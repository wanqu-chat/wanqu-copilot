package com.wanqu.copilot.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class ProxyConfig {
  boolean enabled;
  String protocol;
  String host;
  String port;
  String user;
  String pass;
}
