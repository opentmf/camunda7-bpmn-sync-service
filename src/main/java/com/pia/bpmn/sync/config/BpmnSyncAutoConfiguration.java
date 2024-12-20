package com.pia.bpmn.sync.config;

import com.pia.bpmn.sync.client.impl.CamundaClientImpl;
import com.pia.bpmn.sync.service.impl.BpmnMigrationServiceImpl;
import com.pia.bpmn.sync.service.impl.BpmnSyncServiceImpl;
import com.pia.client.common.model.BaseClientProperties;
import com.pia.client.common.service.api.TokenService;
import com.pia.db.lock.config.DbLockAutoConfiguration;
import com.pia.db.lock.service.api.DbLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Gokhan Demir
 */
@AutoConfiguration(
    after = DbLockAutoConfiguration.class,
    afterName = {
      "com.pia.client.openid.config.OpenidWebClientProviderAutoConfiguration",
      "com.pia.client.basic.config.BasicWebClientProviderAutoConfiguration"
    })
@ConditionalOnBean(name = "dbLockService")
@EnableConfigurationProperties({CamundaProperties.class, BpmnSyncProperties.class})
@ConditionalOnExpression(
    "${pia.bpmn-sync.enabled} && T(java.net.URI).create('${camunda.bpm.client.base-url}').toString().length() > 0")
@Slf4j
public class BpmnSyncAutoConfiguration {

  public BpmnSyncAutoConfiguration(ApplicationContext ctx, BpmnSyncProperties bpmnSyncProperties,
      CamundaProperties camundaProperties, DbLockService dbLockService) {

    try {
      log.info("Initializing BPMN Sync.");
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
