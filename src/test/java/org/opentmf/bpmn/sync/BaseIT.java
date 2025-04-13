package org.opentmf.bpmn.sync;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import org.opentmf.bpmn.sync.client.api.CamundaClient;
import org.opentmf.bpmn.sync.client.impl.CamundaClientImpl;
import org.opentmf.bpmn.sync.config.BpmnSyncProperties;
import org.opentmf.bpmn.sync.config.CamundaProperties;
import org.opentmf.bpmn.sync.service.api.BpmnSyncService;
import org.opentmf.bpmn.sync.service.impl.BpmnMigrationServiceImpl;
import org.opentmf.bpmn.sync.service.impl.BpmnSyncServiceImpl;
import org.opentmf.client.common.service.api.TokenService;
import org.opentmf.client.openid.model.OpenidClientProperties;
import org.opentmf.db.lock.service.api.DbLockService;
import java.net.URI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
@EnableConfigurationProperties({
    BpmnSyncProperties.class,
    CamundaProperties.class
})
@ExtendWith(MockitoExtension.class)
@TestInstance(PER_CLASS)
public abstract class BaseIT {

  @Autowired protected BpmnSyncProperties bpmnSyncProperties;
  @Autowired protected CamundaProperties camundaProperties;
  @Autowired protected OpenidClientProperties openidClientProperties;

  @Autowired private DbLockService dbLockService;
  @Autowired private WebClient openidWebClient;
  @Autowired private TokenService openidTokenService;

  protected final MockServer mockServer = new MockServer();

  @BeforeAll
  void initTokenProperties() {
    var tokenConfig = openidClientProperties.getTokenConfig();
    tokenConfig.setTokenUrl(URI.create(mockServer.getBaseUrl() + "/oauth2/token"));
  }

  protected final CamundaClient getCamundaClient() {
   return new CamundaClientImpl(
          openidWebClient, openidTokenService, openidClientProperties, camundaProperties);
  }

  protected final BpmnSyncService getBpmnSyncService() {
    var camundaClient = getCamundaClient();
    return getBpmnSyncService(camundaClient);
  }

  protected final BpmnSyncService getBpmnSyncService(CamundaClient camundaClient) {
    var migrationService = new BpmnMigrationServiceImpl(bpmnSyncProperties, camundaClient);
    return new BpmnSyncServiceImpl(
        bpmnSyncProperties, dbLockService, camundaClient, migrationService);
  }
}
