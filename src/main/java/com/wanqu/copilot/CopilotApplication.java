package com.wanqu.copilot;

import java.awt.GraphicsEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class CopilotApplication {

  public static void main(String[] args) {
    log.info(
        "Checking for headless environment...{}",
        GraphicsEnvironment.isHeadless()
            ? "Headless mode detected"
            : "Graphical environment detected");
    if (GraphicsEnvironment.isHeadless()) {
      log.warn("Running in headless environment - attempting to disable headless mode for JCEF");
    }

    log.info("Starting Copilot Application...");
    SpringApplication.run(CopilotApplication.class, args);
  }
}
