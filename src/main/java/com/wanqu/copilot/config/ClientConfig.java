package com.wanqu.copilot.config;

import com.wanqu.copilot.entity.ProxyConfig;
import com.wanqu.copilot.entity.WanquProxyAuthenticator;
import com.wanqu.copilot.entity.WanquProxySelector;
import java.net.http.HttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.transport.ProxyProvider;

@Configuration
@Slf4j
public class ClientConfig {

  @Bean
  HttpClient httpClient(
      WanquProxySelector wanquProxySelector, WanquProxyAuthenticator wanquProxyAuthenticator) {
    return HttpClient.newBuilder()
        .proxy(wanquProxySelector)
        .authenticator(wanquProxyAuthenticator)
        .build();
  }

  @Bean
  ClientHttpRequestFactory clientHttpRequestFactory(HttpClient httpClient) {
    return new JdkClientHttpRequestFactory(httpClient);
  }

  @Bean
  RestClient.Builder restClientBuilder(ClientHttpRequestFactory requestFactory) {
    return RestClient.builder().requestFactory(requestFactory);
  }

  @Bean
  reactor.netty.http.client.HttpClient reactorHttpClient(
      WanquProxySelector wanquProxySelector, WanquProxyAuthenticator wanquProxyAuthenticator) {
    return reactor.netty.http.client.HttpClient.create()
        .proxyWhen(
            ((httpClientConfig, typeSpec) -> {
              ProxyConfig proxyConfig = wanquProxySelector.getProxyConfig();
              if (proxyConfig != null && proxyConfig.isEnabled()) {
                log.info(
                    "Configuring Reactor HTTP Client with proxy: {}://{}:{}",
                    proxyConfig.getProtocol(),
                    proxyConfig.getHost(),
                    proxyConfig.getPort());
                ProxyProvider.Builder builder =
                    typeSpec
                        .type(ProxyProvider.Proxy.HTTP)
                        .host(proxyConfig.getHost())
                        .port(Integer.parseInt(proxyConfig.getPort()));
                if (proxyConfig.getUser() != null && !proxyConfig.getUser().isEmpty()) {
                  builder.username(proxyConfig.getUser()).password(s -> proxyConfig.getPass());
                }
                return Mono.just(builder);
              } else {
                return Mono.empty();
              }
            }));
  }

  //    @Bean
  //    ClientHttpConnector reactClientHttpConnector(HttpClient httpClient) {
  //        return new JdkClientHttpConnector(httpClient);
  //    }

  @Bean
  ClientHttpConnector reactClientHttpConnector(
      @Qualifier("reactorHttpClient") reactor.netty.http.client.HttpClient reactorHttpClient) {
    return new ReactorClientHttpConnector(reactorHttpClient);
  }

  @Bean
  WebClient.Builder webClientBuilder(
      @Qualifier("reactClientHttpConnector") ClientHttpConnector reactClientHttpConnector) {
    return WebClient.builder().clientConnector(reactClientHttpConnector);
  }
}
