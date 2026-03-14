package com.wanqu.copilot.browser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import lombok.extern.slf4j.Slf4j;
import org.cef.callback.CefCallback;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

@Slf4j
public class WanquCopilotResourceHandler extends CefResourceHandlerAdapter {
  private static final String SCHEME_1 = "wanqu-copilot://";

  private InputStream inputStream;
  private String mimeType;
  private int remaining;
  private boolean resourceFound;

  @Override
  public boolean processRequest(CefRequest request, CefCallback callback) {
    String url = request.getURL();
    String resourcePath = toResourcePath(url);

    log.info("Processing request for URL: {}, mapped to resource path: {}", url, resourcePath);

    if (resourcePath == null) {
      callback.cancel();
      return false;
    }

    try {
      URL resourceUrl = getClass().getResource(resourcePath);
      if (resourceUrl != null) {
        var connection = resourceUrl.openConnection();
        inputStream = connection.getInputStream();
        mimeType = getMimeType(resourcePath);
        long contentLength = connection.getContentLengthLong();
        remaining =
            (contentLength >= 0 && contentLength <= Integer.MAX_VALUE)
                ? (int) contentLength
                : -1; // unknown length
        resourceFound = true;
      } else {
        log.warn("Resource NOT found: {}", resourcePath);
        String message = "Resource not found: " + resourcePath;
        inputStream = new ByteArrayInputStream(message.getBytes());
        mimeType = "text/plain";
        remaining = message.length();
        resourceFound = false;
      }

      callback.Continue();
      return true;
    } catch (IOException e) {
      log.error("Resource error", e);
      callback.cancel();
      return false;
    }
  }

  @Override
  public void getResponseHeaders(
      CefResponse response, IntRef responseLength, StringRef redirectUrl) {
    response.setMimeType(mimeType != null ? mimeType : "application/octet-stream");
    response.setStatus(resourceFound ? 200 : 404);
    response.setStatusText(resourceFound ? "OK" : "Not Found");
    // 如果是 404，设置 length 为文本内容的长度
    responseLength.set(remaining >= 0 ? remaining : -1);
    log.info(
        "Response headers set: MIME type={}, status={}, length={}",
        response.getMimeType(),
        response.getStatus(),
        responseLength.get());
  }

  @Override
  public boolean readResponse(
      byte[] dataOut, int bytesToRead, IntRef bytesRead, CefCallback callback) {
    if (inputStream == null) {
      bytesRead.set(0);
      return false;
    }
    if (remaining == 0) {
      bytesRead.set(0);
      closeStream();
      return false;
    }

    try {
      int maxRead = bytesToRead;
      if (remaining > 0 && remaining < bytesToRead) {
        maxRead = remaining;
      }
      int read = inputStream.read(dataOut, 0, maxRead);
      if (read == -1) {
        bytesRead.set(0);
        closeStream();
        return false;
      }

      bytesRead.set(read);
      if (remaining > 0) {
        remaining -= read;
      }
      return true;
    } catch (IOException e) {
      bytesRead.set(0);
      closeStream();
      return false;
    }
  }

  @Override
  public void cancel() {
    closeStream();
  }

  private void closeStream() {
    if (inputStream != null) {
      try {
        inputStream.close();
      } catch (IOException ignored) {
        // Intentionally ignored to avoid throwing from cleanup.
      } finally {
        inputStream = null;
      }
    }
  }

  private String toResourcePath(String url) {
    if (url == null) {
      return null;
    }

    String path;
    if (url.startsWith(SCHEME_1)) {
      path = url.substring(SCHEME_1.length());
    } else {
      return null;
    }

    // 标准 URL 格式: scheme://host/path
    int firstSlash = path.indexOf('/');
    if (firstSlash != -1) {
      String host = path.substring(0, firstSlash);
      String subPath = path.substring(firstSlash + 1);

      if (host.equals("app")) {
        // 如果是固定主机名 "app"，取后面的路径
        path = subPath;
      } else if (!subPath.isEmpty()) {
        // 如果 Host 不是 app，且 Path 不为空，说明 Host 被当作了域名使用（如 usage.html）
        // 此时相对路径加载会导致 url 变成 wanqu-copilot://usage.html/js/index.js
        // 我们应该舍弃作为域名标识的 Host，直接使用 Path
        path = subPath;
      } else {
        // 如果 Path 为空（如 wanqu-copilot://static.html/），则 Host 就是资源文件名
        path = host;
      }
    }

    int queryIndex = path.indexOf('?');
    if (queryIndex >= 0) {
      path = path.substring(0, queryIndex);
    }
    int fragmentIndex = path.indexOf('#');
    if (fragmentIndex >= 0) {
      path = path.substring(0, fragmentIndex);
    }

    // 去掉开头的 /
    while (path.startsWith("/")) {
      path = path.substring(1);
    }

    // 去掉末尾的 /
    while (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }

    if (path.isEmpty()) {
      path = "static.html";
    }
    return "/html/" + path;
  }

  /** 根据文件扩展名返回 MIME 类型 */
  private String getMimeType(String path) {
    if (path.endsWith(".html")) {
      return "text/html";
    }
    if (path.endsWith(".css")) {
      return "text/css";
    }
    if (path.endsWith(".js")) {
      return "application/javascript";
    }
    if (path.endsWith(".json")) {
      return "application/json";
    }
    if (path.endsWith(".png")) {
      return "image/png";
    }
    if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
      return "image/jpeg";
    }
    if (path.endsWith(".gif")) {
      return "image/gif";
    }
    if (path.endsWith(".svg")) {
      return "image/svg+xml";
    }
    if (path.endsWith(".ico")) {
      return "image/x-icon";
    }
    if (path.endsWith(".woff")) {
      return "font/woff";
    }
    if (path.endsWith(".woff2")) {
      return "font/woff2";
    }
    if (path.endsWith(".ttf")) {
      return "font/ttf";
    }
    if (path.endsWith(".eot")) {
      return "application/vnd.ms-fontobject";
    }
    return "application/octet-stream";
  }
}
