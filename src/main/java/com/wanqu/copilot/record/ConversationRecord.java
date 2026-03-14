package com.wanqu.copilot.record;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConversationRecord extends AbstractRecord {
  String projectUid;
  String title;
}
