package com.wanqu.copilot.util;

import java.io.File;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PathUtil {
  public static void ensureFolder(Path path) {
    File file = path.toFile();
    if (file.exists()) {
      if (file.isDirectory()) {
        log.info("folder already exists: {}", file.getAbsolutePath());
      } else {
        file.mkdirs();
        log.info(
            "file with same name already exists, delete it and create folder: {}",
            file.getAbsolutePath());
      }
    } else {
      file.mkdirs();
      log.info("create folder: {}", file.getAbsolutePath());
    }
  }
}
