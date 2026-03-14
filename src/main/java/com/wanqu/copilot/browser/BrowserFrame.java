package com.wanqu.copilot.browser;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.CefInitializationException;
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter;
import me.friwi.jcefmaven.UnsupportedPlatformException;
import org.apache.commons.lang3.StringUtils;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;
import org.cef.callback.CefSchemeRegistrar;
import org.cef.handler.CefContextMenuHandlerAdapter;
import org.cef.handler.CefDisplayHandlerAdapter;

@Slf4j
public class BrowserFrame extends JFrame {
  private CefApp cefApp;
  private CefClient cefClient;
  private CefBrowser browser;

  private CefMessageHandler cefMessageHandler;

  /**
   * JCEF init/download can take a while and must not run on the Swing EDT, otherwise all Swing UI
   * (including our progress dialog) won't paint.
   */
  private static final ExecutorService JCEF_INIT_EXECUTOR =
      Executors.newSingleThreadExecutor(
          new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
              Thread t = new Thread(r, "jcef-init");
              t.setDaemon(true);
              return t;
            }
          });

  private JDialog jcefProgressDialog;
  private JLabel jcefProgressLabel;
  private JProgressBar jcefProgressBar;

  public BrowserFrame(CefMessageHandler cefMessageHandler) {
    this.cefMessageHandler = cefMessageHandler;
  }

  private void showJcefProgressDialog() {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(this::showJcefProgressDialog);
      return;
    }
    if (jcefProgressDialog != null && jcefProgressDialog.isShowing()) {
      return;
    }

    JDialog dialog = new JDialog((Window) null);
    dialog.setTitle("Initializing Browser Engine");
    dialog.setModal(false);
    dialog.setAlwaysOnTop(true);
    dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

    JPanel content = new JPanel(new BorderLayout(12, 12));
    content.setBorder(new EmptyBorder(16, 16, 16, 16));
    JLabel label = new JLabel("Downloading browser engine...");
    JProgressBar bar = new JProgressBar(0, 100);
    bar.setStringPainted(true);
    bar.setIndeterminate(true);

    content.add(label, BorderLayout.NORTH);
    content.add(bar, BorderLayout.CENTER);
    dialog.setContentPane(content);
    dialog.pack();
    dialog.setLocationRelativeTo(null);
    dialog.setVisible(true);
    dialog.toFront();

    this.jcefProgressDialog = dialog;
    this.jcefProgressLabel = label;
    this.jcefProgressBar = bar;
  }

  private void updateJcefProgressDialog(String text, double progress01) {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(() -> updateJcefProgressDialog(text, progress01));
      return;
    }
    if (jcefProgressLabel != null) {
      jcefProgressLabel.setText(text);
    }
    if (jcefProgressBar != null) {
      if (progress01 <= 0) {
        jcefProgressBar.setIndeterminate(true);
        jcefProgressBar.setString("0%");
      } else {
        jcefProgressBar.setIndeterminate(false);
        jcefProgressBar.setValue((int)progress01);
        jcefProgressBar.setString(progress01 + "%");
      }
    }
    if (jcefProgressDialog != null) {
      jcefProgressDialog.validate();
      jcefProgressDialog.repaint();
    }
  }

  private void closeJcefProgressDialog() {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(this::closeJcefProgressDialog);
      return;
    }
    if (jcefProgressDialog != null) {
      jcefProgressDialog.setVisible(false);
      jcefProgressDialog.dispose();
    }
    jcefProgressDialog = null;
    jcefProgressLabel = null;
    jcefProgressBar = null;
  }

  public void prepare()
      throws UnsupportedPlatformException,
          CefInitializationException,
          IOException,
          InterruptedException {
    log.info(
        "BrowserFrame.prepare enter. thread={}, isEDT={}, cwd={}",
        Thread.currentThread().getName(),
        SwingUtilities.isEventDispatchThread(),
        System.getProperty("user.dir"));

    // Basic frame properties must be done on EDT
    if (!SwingUtilities.isEventDispatchThread()) {
      try {
        SwingUtilities.invokeAndWait(
            () -> {
              setTitle("Wanqu Copilot Browser");
              setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
              setLocationRelativeTo(null);
            });
      } catch (Exception e) {
        log.warn("Failed to initialize Swing frame on EDT", e);
      }
    } else {
      setTitle("Wanqu Copilot Browser");
      setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      setLocationRelativeTo(null);
    }

    // 使用 jcefmaven 方式初始化，并注册自定义协议
    CefAppBuilder builder = new CefAppBuilder();

    String mirrors = System.getProperty("jcef.maven.repo");
    if (StringUtils.isNotBlank(mirrors)) {
      String[] repos = StringUtils.split(mirrors, ",");
      log.info("Using custom Maven repository from jcef.maven.repo: {}", mirrors);
      builder.setMirrors(List.of(repos));
    } else {
      log.info("No custom Maven repository specified, using defaults");
    }

    // 重要：先设置路径和安装选项
    // Resolve JCEF install directory depending on runtime environment:
    // 1) If running inside a macOS .app bundle, prefer Contents/MacOS/jcef
    // 2) Fallback to ./jcef or jcef-bundle in working dir
    if (System.getProperties().contains("jcef.dir")) {
      File installDir = Paths.get(System.getProperty("jcef.dir")).toFile();
      log.info("Running with jcef.dir specified: {}", installDir.getAbsolutePath());
      builder.setInstallDir(installDir);
      if (installDir.exists() && installDir.isDirectory()) {
        builder.setSkipInstallation(Boolean.TRUE);
      } else {
        builder.setSkipInstallation(Boolean.FALSE);
      }
    } else {
      File installDir =
          Paths.get(System.getProperty("user.home"), ".wanqu", "copilot", "jcef").toFile();
      log.info(
          "Running without jcef.dir specified, using default install location in user home = {}",
          installDir.getAbsolutePath());

      // IMPORTANT: must set installDir, otherwise jcefmaven may choose another default path and
      // skipInstallation/progress behavior becomes unpredictable.
      builder.setInstallDir(installDir);

      if (installDir.exists() && installDir.isDirectory()) {
        builder.setSkipInstallation(Boolean.TRUE);
      } else {
        builder.setSkipInstallation(Boolean.FALSE);
      }
    }

    if (builder.getSkipInstallation()) {
      log.info("JCEF installation skipped (already installed), no progress dialog will be shown");
    } else {
      log.info("JCEF installation will proceed, showing progress dialog");
      showJcefProgressDialog();
      updateJcefProgressDialog("Downloading browser engine...", 0);
    }

    // LOCATING, DOWNLOADING, EXTRACTING, BUILDING, FINISHED
    builder.setProgressHandler(
        (enumProgress, v) -> {
          log.info("Installation progress: {} - {}", enumProgress, v);
          if (!builder.getSkipInstallation()) {
            String stage = enumProgress == null ? "" : enumProgress.toString();
            updateJcefProgressDialog("Kernel engine: " + stage, v);
          }
        });

    // 然后设置 App Handler 来注册自定义 Scheme（必须在 build 之前）
    builder.setAppHandler(
        new MavenCefAppHandlerAdapter() {
          @Override
          public void onRegisterCustomSchemes(CefSchemeRegistrar registrar) {
            // 注册 wanqu-copilot 自定义协议
            log.info("==> Registering custom scheme: wanqu-copilot");
            boolean schemaResult =
                registrar.addCustomScheme(
                    "wanqu-copilot",
                    true, // is_standard - 必须为 true 以支持相对路径和跨域
                    false, // is_local
                    false, // is_display_isolated
                    true, // is_secure
                    true, // is_cors_enabled
                    true, // is_csp_bypassing
                    false // is_fetch_enabled
                    );
            log.info("==> Custom scheme wanqu-copilot registered = {}", schemaResult);
          }

          @Override
          public void onContextInitialized() {
            log.info("Registering scheme handler factory...");
            WanquCopilotSchemaHandlerFactory factory = new WanquCopilotSchemaHandlerFactory();
            CefApp.getInstance().registerSchemeHandlerFactory("wanqu-copilot", "app", factory);

            // 兼容没有 host 的情况
            CefApp.getInstance().registerSchemeHandlerFactory("wanqu-copilot", "", factory);

            log.info("==> Custom scheme handler factory registered");
          }
        });

    // IMPORTANT: JCEF build/download must run off the EDT, otherwise Swing can't paint the dialog.
    JCEF_INIT_EXECUTOR.submit(
        () -> {
          try {
            buildAndInitCefApp(builder);

            // Now that CefApp exists, create client/router/handlers.
            createClientAndRegisterHandlers();

            SwingUtilities.invokeLater(
                () -> {
                  closeJcefProgressDialog();
                  // 创建浏览器 - 使用带 host 的 URL 以便正确解析相对路径
//                  String initialUrl = "http://localhost:5173/";
                    String initialUrl = "wanqu-copilot://index.html";
                  createBrowserAndShow(initialUrl);
                });
          } catch (Exception e) {
            log.error("Failed to build/init JCEF", e);
            SwingUtilities.invokeLater(
                () -> {
                  closeJcefProgressDialog();
                  // keep the app alive but visible
                  javax.swing.JOptionPane.showMessageDialog(
                      null,
                      "Failed to initialize browser engine:\n" + e.getMessage(),
                      "Wanqu Copilot",
                      javax.swing.JOptionPane.ERROR_MESSAGE);
                });
          }
        });

    log.info("BrowserFrame.prepare scheduled JCEF initialization in background");
  }

  // Extracted helper methods to reduce prepare() length
  private void buildAndInitCefApp(CefAppBuilder builder) throws CefInitializationException, UnsupportedPlatformException, IOException, InterruptedException {
    log.info("Building CefApp...");
    cefApp = builder.build();
    log.info("CefApp built successfully");
  }

  private void createClientAndRegisterHandlers() {
    cefClient = cefApp.createClient();

    // 为 JCEF 设置右键菜单 (重新加载, 开发者工具)
    cefClient.addContextMenuHandler(
        new CefContextMenuHandlerAdapter() {
          private static final int MENU_ID_RELOAD = 1;
          private static final int MENU_ID_DEVTOOLS = 2;

          @Override
          public void onBeforeContextMenu(
              CefBrowser browser, CefFrame frame, CefContextMenuParams params, CefMenuModel model) {
            model.clear();
            model.addItem(MENU_ID_RELOAD, "重新加载");
            model.addItem(MENU_ID_DEVTOOLS, "开发者工具");
          }

          @Override
          public boolean onContextMenuCommand(
              CefBrowser browser,
              CefFrame frame,
              CefContextMenuParams params,
              int commandId,
              int eventFlags) {
            if (commandId == MENU_ID_RELOAD) {
              browser.reload();
              return true;
            } else if (commandId == MENU_ID_DEVTOOLS) {
              openDevtools();
              return true;
            }
            return false;
          }
        });

    // 核心修复：添加 MessageRouter 支持 JSBridge (方案 3.1)
    CefMessageRouter.CefMessageRouterConfig config =
        new CefMessageRouter.CefMessageRouterConfig("cefQuery", "cefQueryCancel");
    CefMessageRouter router = CefMessageRouter.create(config);
    router.addHandler(cefMessageHandler, true);
    cefClient.addMessageRouter(router);

    // 添加显示处理器
    cefClient.addDisplayHandler(
        new CefDisplayHandlerAdapter() {
          @Override
          public void onTitleChange(CefBrowser browser, String title) {
            SwingUtilities.invokeLater(() -> setTitle(title));
          }
        });

    log.info("CefClient created and handlers registered");
  }

  private void createBrowserAndShow(String initialUrl) {
    log.info("Creating browser with URL: {}", initialUrl);
    browser = cefClient.createBrowser(initialUrl, false, false);
    SwingUtilities.invokeLater(
        () -> {
          getContentPane().add(browser.getUIComponent(), BorderLayout.CENTER);
          browser
              .getUIComponent()
              .addKeyListener(
                  new KeyAdapter() {
                    @Override
                    public void keyPressed(KeyEvent e) {
                      if (e.isControlDown() || e.isMetaDown()) {
                        switch (e.getKeyCode()) {
                          case KeyEvent.VK_C:
                            browser.getFocusedFrame().copy();
                            break;
                          case KeyEvent.VK_V:
                            browser.getFocusedFrame().paste();
                            break;
                          case KeyEvent.VK_X:
                            browser.getFocusedFrame().cut();
                            break;
                          case KeyEvent.VK_A:
                            browser.getFocusedFrame().selectAll();
                            break;

                          case KeyEvent.VK_Z:
                            browser.getFocusedFrame().undo();
                            break;
                        }
                      }
                    }
                  });
          this.setMinimumSize(new Dimension(1000, 800));
          this.pack();
          this.setVisible(true);
        });
  }

  public void openDevtools() {
    var devTools = browser.getDevTools();
    JFrame devToolsFrame = new JFrame("DevTools");
    devToolsFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    devToolsFrame.getContentPane().add(devTools.getUIComponent(), BorderLayout.CENTER);
    devToolsFrame.setSize(800, 600);
    devToolsFrame.setLocationRelativeTo(null);
    devToolsFrame.setVisible(true);
  }
}
