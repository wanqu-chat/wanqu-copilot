package com.wanqu.copilot.browser;

import lombok.extern.slf4j.Slf4j;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefSchemeHandlerFactory;
import org.cef.handler.CefResourceHandler;
import org.cef.network.CefRequest;

@Slf4j
public class WanquCopilotSchemaHandlerFactory implements CefSchemeHandlerFactory {
  @Override
  public CefResourceHandler create(
      CefBrowser browser, CefFrame frame, String schemeName, CefRequest request) {
    log.info(
        "WanquCopilotSchemaHandlerFactory.create: scheme={}, url={}", schemeName, request.getURL());
    return new WanquCopilotResourceHandler();
  }
}
