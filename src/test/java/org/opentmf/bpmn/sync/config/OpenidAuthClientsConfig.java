package org.opentmf.bpmn.sync.config;

import org.opentmf.client.openid.model.OpenidClientProperties;
import org.opentmf.client.openid.model.OpenidClients;
import org.opentmf.client.openid.service.api.OpenidTokenService;
import org.opentmf.client.openid.service.api.OpenidWebClientProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Gokhan Demir
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(OpenidClients.class)
public class OpenidAuthClientsConfig {

  private final OpenidWebClientProvider openidWebClientProvider;
  private final OpenidClients openidClients;

  @Bean
  public OpenidClientProperties openidClientProperties() {
    return openidClients.getOpenid().get("client1");
  }

  @Bean
  public WebClient openidWebClient(OpenidClientProperties properties) {
    return openidWebClientProvider.buildWebClient(properties);
  }

  @Bean
  public OpenidTokenService openidTokenService(OpenidClientProperties properties) {
    return openidWebClientProvider.buildTokenService(properties);
  }
}
