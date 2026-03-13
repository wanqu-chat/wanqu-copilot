package com.wanqu.copilot.entity;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WanquProxySelector extends ProxySelector {
  @Getter ProxyConfig proxyConfig;

  private final List<Proxy> noProxyList = List.of(Proxy.NO_PROXY);
  private List<Proxy> proxies;

  @EventListener
  public void updateProxy(ProxyConfig pc) {
    this.proxyConfig = pc;
    if (pc.enabled) {
      proxies =
          List.of(
              new Proxy(
                  Proxy.Type.HTTP, new InetSocketAddress(pc.host, Integer.parseInt(pc.port))));
      log.info("Proxy enabled\t {}://{}:{}", pc.protocol, pc.host, pc.port);
    }
  }

  @Override
  public List<Proxy> select(URI uri) {
    if (proxyConfig == null || !proxyConfig.isEnabled()) {
      return noProxyList;
    }
    return proxies;
  }

  public Proxy getProxy() {
    if (Objects.nonNull(proxies) && !proxies.isEmpty()) {
      return proxies.get(0);
    } else {
      return Proxy.NO_PROXY;
    }
  }

  @Override
  public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {}
}
