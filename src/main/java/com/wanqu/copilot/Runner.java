package com.wanqu.copilot;

import com.wanqu.copilot.browser.BrowserFrame;
import com.wanqu.copilot.browser.CefMessageHandler;
import java.io.IOException;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import me.friwi.jcefmaven.CefInitializationException;
import me.friwi.jcefmaven.UnsupportedPlatformException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class Runner implements ApplicationRunner {
  BrowserFrame browserFrame;

  @Autowired CefMessageHandler cefMessageHandler;

  @Override
  public void run(ApplicationArguments args) {
    log.info("ApplicationRunner executed - Copilot Application is up and running!");
    log.info(
        "Runner.run thread={}, isEDT={}, cwd={}",
        Thread.currentThread().getName(),
        SwingUtilities.isEventDispatchThread(),
        System.getProperty("user.dir"));

    // Make sure exceptions on EDT are visible in packaged apps.
    Thread.setDefaultUncaughtExceptionHandler(
        (t, e) -> log.error("Uncaught exception in thread: {}", t.getName(), e));

    log.info("Scheduling BrowserFrame initialization on EDT...");
    SwingUtilities.invokeLater(
        () -> {
          log.info(
              "EDT task started. thread={}, isEDT={}, cwd={}",
              Thread.currentThread().getName(),
              SwingUtilities.isEventDispatchThread(),
              System.getProperty("user.dir"));
          try {
            log.info("Initializing BrowserFrame...");
            browserFrame = new BrowserFrame(cefMessageHandler);
            browserFrame.prepare();
            log.info("BrowserFrame.prepare() returned successfully");
          } catch (UnsupportedPlatformException
              | CefInitializationException
              | IOException
              | InterruptedException e) {
            log.error("Failed to initialize BrowserFrame", e);
            try {
              JOptionPane.showMessageDialog(
                  null,
                  "Failed to initialize UI:\n" + e.getMessage(),
                  "Wanqu Copilot",
                  JOptionPane.ERROR_MESSAGE);
            } catch (Exception dialogError) {
              log.error("Failed to show error dialog", dialogError);
            }
          }
        });

    log.info("BrowserFrame initialization task scheduled.");
  }
}
