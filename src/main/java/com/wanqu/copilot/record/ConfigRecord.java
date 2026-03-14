package com.wanqu.copilot.record;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfigRecord extends AbstractRecord {
  String namespace;
  String key;
  String value;
}
