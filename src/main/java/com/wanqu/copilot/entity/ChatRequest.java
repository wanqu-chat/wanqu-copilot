package com.wanqu.copilot.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ChatRequest {
  String projectUid;
  String conversationUid;
  String model;
  String content;
}
