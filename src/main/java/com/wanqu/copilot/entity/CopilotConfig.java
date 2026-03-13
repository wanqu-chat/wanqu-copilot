package com.wanqu.copilot.entity;

import java.nio.file.Paths;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class CopilotConfig {
  // 用户工作台目录
  String workbench = Paths.get(System.getProperty("user.home"), "wanqu-copilot").toString();

  String workdir = Paths.get(System.getProperty("user.home"), ".wanqu", "copilot").toString();

  List<ApiCredential> credentials;
}
