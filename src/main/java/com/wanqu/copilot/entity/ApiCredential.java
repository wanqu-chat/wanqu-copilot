package com.wanqu.copilot.entity;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiCredential {
  ApiProvider provider;
  boolean enabled;
  Map<String, Object> properties;
}
