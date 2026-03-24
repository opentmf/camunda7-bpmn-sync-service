package org.opentmf.bpmn.sync.client.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opentmf.bpmn.sync.config.CamundaProperties;
import org.opentmf.bpmn.sync.exception.CamundaResponseException;
import org.opentmf.bpmn.sync.model.CamundaDeploymentResponse;
import org.opentmf.bpmn.sync.model.ExecuteMigrationPlanAsyncResponse;
import org.opentmf.bpmn.sync.model.ExecuteMigrationPlanRequest;
import org.opentmf.bpmn.sync.model.GenerateMigrationPlanRequest;
import org.opentmf.bpmn.sync.model.MigrationPlan;
import org.opentmf.bpmn.sync.model.ObjectCount;
import org.opentmf.bpmn.sync.model.ProcessDefinition;
import org.opentmf.client.common.exception.OpenTmfClientResponseException;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.rest.service.api.SyncTokenService;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class RestCamundaClientImplTest {

  @Mock private RestTemplate restTemplate;
  @Mock private SyncTokenService tokenService;

  private RestCamundaClientImpl client;

  @BeforeEach
  void setUp() {
    var props = new ClientProperties();
    props.setNumRetries(0);
    props.setRetryWaitMillis(100);
    var camundaProperties = new CamundaProperties();
    camundaProperties.setBaseUrl("http://localhost:8080/engine-rest");
    client = new RestCamundaClientImpl(restTemplate, tokenService, props, camundaProperties);
  }

  @Test
  @SuppressWarnings("unchecked")
  void getProcessDefinition_returnsFirstElement() {
    when(tokenService.getToken()).thenReturn("tok");
    var pd = new ProcessDefinition();
    pd.setVersion(2);
    when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class),
        any(ParameterizedTypeReference.class)))
        .thenReturn(ResponseEntity.ok(List.of(pd)));

    var result = client.getProcessDefinition("key", 2);
    assertNotNull(result);
    assertEquals(2, result.getVersion());
  }

  @Test
  void getProcessInstanceCount_returnsCount() {
    when(tokenService.getToken()).thenReturn("tok");
    var count = new ObjectCount();
    count.setCount(5);
    when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class),
        eq(ObjectCount.class)))
        .thenReturn(ResponseEntity.ok(count));

    var result = client.getProcessInstanceCount("def-id");
    assertNotNull(result);
    assertEquals(5, result.getCount());
  }

  @Test
  void generateMigrationPlan_returnsResult() {
    when(tokenService.getToken()).thenReturn("tok");
    var plan = new MigrationPlan();
    when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class),
        eq(MigrationPlan.class)))
        .thenReturn(ResponseEntity.ok(plan));

    var result = client.generateMigrationPlan(new GenerateMigrationPlanRequest());
    assertNotNull(result);
  }

  @Test
  void executeMigrationPlanAsync_returnsResult() {
    when(tokenService.getToken()).thenReturn("tok");
    var response = new ExecuteMigrationPlanAsyncResponse();
    when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class),
        eq(ExecuteMigrationPlanAsyncResponse.class)))
        .thenReturn(ResponseEntity.ok(response));

    var result = client.executeMigrationPlanAsync(new ExecuteMigrationPlanRequest());
    assertNotNull(result);
  }

  @Test
  void syncBpmnFiles_returnsResult() {
    when(tokenService.getToken()).thenReturn("tok");
    var deployment = new CamundaDeploymentResponse();
    when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class),
        eq(CamundaDeploymentResponse.class)))
        .thenReturn(ResponseEntity.ok(deployment));

    var result = client.syncBpmnFiles("deploy", new org.springframework.core.io.Resource[0]);
    assertNotNull(result);
  }

  @Test
  @SuppressWarnings("unchecked")
  void getProcessDefinition_onError_throwsCamundaResponseException() {
    when(tokenService.getToken()).thenReturn("tok");
    when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class),
        any(ParameterizedTypeReference.class)))
        .thenThrow(new OpenTmfClientResponseException(HttpStatus.GATEWAY_TIMEOUT, "timeout"));

    var ex = assertThrows(CamundaResponseException.class,
        () -> client.getProcessDefinition("key", 2));
    assertEquals("timeout", ex.getMessage());
  }
}
