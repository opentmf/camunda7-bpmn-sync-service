package org.opentmf.bpmn.sync;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import org.opentmf.bpmn.sync.client.api.ReactiveCamundaClient;
import org.opentmf.bpmn.sync.client.impl.ReactiveCamundaClientImpl;
import org.opentmf.bpmn.sync.config.BpmnSyncProperties;
import org.opentmf.bpmn.sync.config.CamundaProperties;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.reactive.service.api.TokenService;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@EnableConfigurationProperties({
    BpmnSyncProperties.class,
    CamundaProperties.class
})
@ExtendWith(MockitoExtension.class)
@TestInstance(PER_CLASS)
public abstract class BaseIT {

  @Autowired protected BpmnSyncProperties bpmnSyncProperties;
  @Autowired protected CamundaProperties camundaProperties;
  @Autowired protected ApplicationContext ctx;

  protected static final MockServer mockServer = new MockServer();

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry r) {
    r.add("camunda.bpm.client.base-url", mockServer::getBaseUrl);
    r.add("opentmf.http-clients.openid.bearer-auth.token-url",
        () -> mockServer.getBaseUrl() + "/oauth2/token");
  }

  protected ReactiveCamundaClient getCamundaClient() {
    String ref = bpmnSyncProperties.getClientRef();
    return new ReactiveCamundaClientImpl(
        (WebClient) ctx.getBean(ref + "WebClient"),
        (TokenService) ctx.getBean(ref + "TokenService"),
        (ClientProperties) ctx.getBean(ref + "ClientProperties"),
        camundaProperties);
  }
}
