package com.pia.bpmn.sync;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import com.pia.bpmn.sync.client.api.CamundaClient;
import com.pia.bpmn.sync.client.impl.CamundaClientImpl;
import com.pia.bpmn.sync.config.BpmnSyncProperties;
import com.pia.bpmn.sync.config.CamundaProperties;
import com.pia.bpmn.sync.service.api.BpmnSyncService;
import com.pia.bpmn.sync.service.impl.BpmnMigrationServiceImpl;
import com.pia.bpmn.sync.service.impl.BpmnSyncServiceImpl;
import com.pia.client.common.service.api.TokenService;
import com.pia.client.openid.model.OpenidClientProperties;
import com.pia.db.lock.service.api.DbLockService;
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

  private CamundaClient camundaClient = null;

  protected final CamundaClient getCamundaClient() {
    if (camundaClient == null) {
      camundaClient =
          new CamundaClientImpl(
              openidWebClient, openidTokenService, openidClientProperties, camundaProperties);
    }
    return camundaClient;
  }

  protected final BpmnSyncService getBpmnSyncService() {
    var client = getCamundaClient();
    var migrationService = new BpmnMigrationServiceImpl(bpmnSyncProperties, client);
    return new BpmnSyncServiceImpl(
        bpmnSyncProperties, dbLockService, client, migrationService);
  }
}
