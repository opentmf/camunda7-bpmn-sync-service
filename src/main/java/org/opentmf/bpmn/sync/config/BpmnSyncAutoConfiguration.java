package org.opentmf.bpmn.sync.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.opentmf.bpmn.sync.client.impl.CamundaClientImpl;
import org.opentmf.bpmn.sync.service.impl.BpmnMigrationServiceImpl;
import org.opentmf.bpmn.sync.service.impl.BpmnSyncServiceImpl;
import org.opentmf.client.common.model.BaseClientProperties;
import org.opentmf.client.common.service.api.TokenService;
import org.opentmf.db.lock.config.DbLockAutoConfiguration;
import org.opentmf.db.lock.service.api.DbLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.DependsOn;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Gokhan Demir
 */
@AutoConfiguration(
    after = DbLockAutoConfiguration.class,
    afterName = {
      "org.opentmf.client.openid.config.OpenidWebClientProviderAutoConfiguration",
      "org.opentmf.client.openid.config.OpenidWebClientsStarterAutoConfiguration",
      "org.opentmf.client.basic.config.BasicWebClientProviderAutoConfiguration",
      "org.opentmf.client.basic.config.BasicWebClientsStarterAutoConfiguration"
    })
@ConditionalOnBean(name = "dbLockService")
@EnableConfigurationProperties({CamundaProperties.class, BpmnSyncProperties.class})
@ConditionalOnExpression(
    "${opentmf.bpmn-sync.enabled:true} && T(java.net.URI).create('${camunda.bpm.client.base-url}').toString().length() > 0")
@Slf4j
@DependsOn("objectMapper")
public class BpmnSyncAutoConfiguration {

  public BpmnSyncAutoConfiguration(ApplicationContext ctx, BpmnSyncProperties bpmnSyncProperties,
      CamundaProperties camundaProperties, DbLockService dbLockService, ObjectMapper objectMapper) {

    try {
      log.info("Initializing BPMN Sync.");
      log.trace("objectMapper registered module ids: {}", objectMapper.getRegisteredModuleIds());
      var client = bpmnSyncProperties.getClient();
      var webClient = (WebClient) ctx.getBean(client + "WebClient");
      var tokenService = (TokenService) ctx.getBean(client + "TokenService");
      var clientProperties = (BaseClientProperties) ctx.getBean(client + "ClientProperties");
      var camundaClient = new CamundaClientImpl(webClient, tokenService, clientProperties, camundaProperties);
      var migrationService = new BpmnMigrationServiceImpl(bpmnSyncProperties, camundaClient);
      var bpmnSyncService = new BpmnSyncServiceImpl(
          bpmnSyncProperties, dbLockService, camundaClient, migrationService);

      // do the real job, deploy if necessary and then migrate processes if necessary
      bpmnSyncService.ensureBpmnConsistency();

    } finally {
      log.info("Completed initialization of BPMN Sync.");
    }
  }
}
