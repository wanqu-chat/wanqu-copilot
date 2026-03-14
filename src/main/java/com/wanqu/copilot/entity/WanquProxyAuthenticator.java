package com.wanqu.copilot.entity;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WanquProxyAuthenticator extends Authenticator {

  @Autowired WanquProxySelector wanquProxySelector;

  @Override
  protected PasswordAuthentication getPasswordAuthentication() {
    ProxyConfig proxyConfig = wanquProxySelector.getProxyConfig();
    if (Objects.nonNull(proxyConfig)
        && proxyConfig.isEnabled()
        && StringUtils.isNotBlank(proxyConfig.getUser())) {
      return new PasswordAuthentication(proxyConfig.getUser(), proxyConfig.getPass().toCharArray());
    }
    return null;
  }
}
