package org.opentmf.bpmn.sync.config;

import org.opentmf.bpmn.sync.client.impl.ReactiveCamundaClientImpl;
import org.opentmf.bpmn.sync.client.impl.RestCamundaClientImpl;
import org.opentmf.bpmn.sync.service.api.BpmnSyncService;
import org.opentmf.bpmn.sync.service.impl.ReactiveBpmnMigrationServiceImpl;
import org.opentmf.bpmn.sync.service.impl.ReactiveBpmnSyncServiceImpl;
import org.opentmf.bpmn.sync.service.impl.RestBpmnMigrationServiceImpl;
import org.opentmf.bpmn.sync.service.impl.RestBpmnSyncServiceImpl;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.reactive.service.api.TokenService;
import org.opentmf.client.rest.service.api.SyncTokenService;
import org.opentmf.db.lock.config.DbLockAutoConfiguration;
import org.opentmf.db.lock.service.api.DbLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Gokhan Demir
 */
@AutoConfiguration(
    after = DbLockAutoConfiguration.class,
    afterName = "org.opentmf.client.starter.OpentmfHttpClientsAutoConfiguration")
@ConditionalOnBean(name = "dbLockService")
@ConditionalOnProperty(prefix = "opentmf.bpmn-sync", name = "deployment-name")
@EnableConfigurationProperties({CamundaProperties.class, BpmnSyncProperties.class})
@Slf4j
public class BpmnSyncAutoConfiguration implements SmartInitializingSingleton {

  private final BpmnSyncProperties bpmnSyncProperties;
  private final BpmnSyncService bpmnSyncService;

  public BpmnSyncAutoConfiguration(ApplicationContext ctx,
      BpmnSyncProperties bpmnSyncProperties,
      CamundaProperties camundaProperties,
      DbLockService dbLockService) {
    this.bpmnSyncProperties = bpmnSyncProperties;

    var clientRef = bpmnSyncProperties.getClientRef();
    var clientProperties = (ClientProperties) ctx.getBean(clientRef + "ClientProperties");

    if (ctx.containsBean(clientRef + "WebClient")) {
      log.info("Using reactive client with client-ref '{}'", clientRef);
      var webClient = (WebClient) ctx.getBean(clientRef + "WebClient");
      var tokenService = (TokenService) ctx.getBean(clientRef + "TokenService");
      var client = new ReactiveCamundaClientImpl(webClient, tokenService, clientProperties,
          camundaProperties, bpmnSyncProperties.getTenantId());
      var migration = new ReactiveBpmnMigrationServiceImpl(bpmnSyncProperties, client);
      this.bpmnSyncService = new ReactiveBpmnSyncServiceImpl(
          bpmnSyncProperties, dbLockService, client, migration);
    } else {
      log.info("Using REST client with client-ref '{}'", clientRef);
      var restClient = (RestClient) ctx.getBean(clientRef + "RestClient");
      var tokenService = (SyncTokenService) ctx.getBean(clientRef + "TokenService");
      var client = new RestCamundaClientImpl(restClient, tokenService, clientProperties,
          camundaProperties, bpmnSyncProperties.getTenantId());
      var migration = new RestBpmnMigrationServiceImpl(bpmnSyncProperties, client);
      this.bpmnSyncService = new RestBpmnSyncServiceImpl(
          bpmnSyncProperties, dbLockService, client, migration);
    }
  }

  @Bean
  public BpmnSyncService bpmnSyncService() {
    return bpmnSyncService;
  }

  @Override
  public void afterSingletonsInstantiated() {
    if (bpmnSyncProperties.isEnabled()) {
      log.info("Initializing BPMN Sync.");
      try {
        bpmnSyncService.ensureBpmnConsistency();
      } finally {
        log.info("Completed initialization of BPMN Sync.");
      }
    }
  }
}
