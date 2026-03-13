package com.wanqu.copilot.browser;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CefMessage {
  String type;
  String action;
  String id;
  Object payload;
  private boolean success;
  private String error;
}
