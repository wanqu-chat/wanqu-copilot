package com.wanqu.copilot.record;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class AbstractRecord {
  private Long id;
  private String uid;
  private boolean deleted;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
