package com.wanqu.copilot.record;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectRecord extends AbstractRecord {
  String name;
  String path;
}
