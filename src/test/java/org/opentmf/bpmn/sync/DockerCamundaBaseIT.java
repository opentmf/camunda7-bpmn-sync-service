package org.opentmf.bpmn.sync;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.net.URI;
import org.opentmf.bpmn.sync.config.BpmnSyncProperties;
import org.opentmf.bpmn.sync.config.CamundaProperties;
import org.opentmf.bpmn.sync.service.api.BpmnSyncService;
import org.opentmf.client.reactive.service.api.TokenService;
import org.opentmf.client.rest.service.api.SyncTokenService;
import org.opentmf.db.lock.service.api.DbLockService;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import reactor.core.scheduler.Schedulers;

/**
 * Base class for integration tests that need a real Camunda 7 engine.
 * <p>
 * Each subclass must create its own {@link CamundaTestContainers} instance
 * (in a static field) and wire it via {@code @DynamicPropertySource}.
 * This ensures complete container isolation between test classes.
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@EnableConfigurationProperties({BpmnSyncProperties.class, CamundaProperties.class})
@ExtendWith(MockitoExtension.class)
@TestInstance(PER_CLASS)
public abstract class DockerCamundaBaseIT {

  @Autowired protected BpmnSyncService bpmnSyncService;
  @Autowired protected BpmnSyncProperties bpmnSyncProperties;
  @Autowired protected CamundaProperties camundaProperties;
  @Autowired protected DbLockService dbLockService;
  @Autowired protected ApplicationContext ctx;

  protected void startProcessInstanceById(String processDefinitionId) {
    String ref = bpmnSyncProperties.getClientRef();
    Object tokenSvc = ctx.getBean(ref + "TokenService");
    String token;
    if (tokenSvc instanceof TokenService reactive) {
      token = reactive.getToken().subscribeOn(Schedulers.boundedElastic()).block();
    } else {
      token = ((SyncTokenService) tokenSvc).getToken();
    }
    var headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    new RestTemplate().exchange(
        URI.create(camundaProperties.getBaseUrl()
            + "/process-definition/" + processDefinitionId + "/start"),
        HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);
  }
}
