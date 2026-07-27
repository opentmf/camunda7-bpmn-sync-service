package org.opentmf.bpmn.sync.client.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@SuppressWarnings({"rawtypes", "unchecked"})
@ExtendWith(MockitoExtension.class)
class RestCamundaClientImplTest {

  @Mock private RestClient restClient;
  @Mock private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;
  @Mock private RestClient.RequestBodyUriSpec requestBodyUriSpec;
  @Mock private RestClient.ResponseSpec responseSpec;
  @Mock private SyncTokenService tokenService;

  private RestCamundaClientImpl client;
  private ClientProperties props;
  private CamundaProperties camundaProperties;

  @BeforeEach
  void setUp() {
    lenient().when(restClient.get()).thenReturn(requestHeadersUriSpec);
    lenient().when(requestHeadersUriSpec.uri(any(URI.class))).thenReturn(requestHeadersUriSpec);
    lenient().when(requestHeadersUriSpec.headers(any())).thenReturn(requestHeadersUriSpec);
    lenient().when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);

    lenient().when(restClient.post()).thenReturn(requestBodyUriSpec);
    lenient().when(requestBodyUriSpec.uri(any(URI.class))).thenReturn(requestBodyUriSpec);
    lenient().when(requestBodyUriSpec.headers(any())).thenReturn(requestBodyUriSpec);
    lenient().when(requestBodyUriSpec.contentType(any(MediaType.class)))
        .thenReturn(requestBodyUriSpec);
    lenient().when(requestBodyUriSpec.body(any(Object.class))).thenReturn(requestBodyUriSpec);
    lenient().when(requestBodyUriSpec.retrieve()).thenReturn(responseSpec);

    props = new ClientProperties();
    props.setNumRetries(0);
    props.setRetryWaitDuration(Duration.ofMillis(100L));
    camundaProperties = new CamundaProperties();
    camundaProperties.setBaseUrl("http://localhost:8080/engine-rest");
    client = new RestCamundaClientImpl(restClient, tokenService, props, camundaProperties, null,
        "classpath:bpmn/");
  }

  private RestCamundaClientImpl tenantClient(String tenantId) {
    return new RestCamundaClientImpl(restClient, tokenService, props, camundaProperties, tenantId,
        "classpath:bpmn/");
  }

  @Test
  void getProcessDefinition_returnsFirstElement() {
    when(tokenService.getToken()).thenReturn("tok");
    var pd = new ProcessDefinition();
    pd.setVersion(2);
    when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(List.of(pd));

    var result = client.getProcessDefinition("key", 2);
    assertNotNull(result);
    assertEquals(2, result.getVersion());
  }

  @Test
  void getProcessInstanceCount_returnsCount() {
    when(tokenService.getToken()).thenReturn("tok");
    var count = new ObjectCount();
    count.setCount(5);
    when(responseSpec.body(eq(ObjectCount.class))).thenReturn(count);

    var result = client.getProcessInstanceCount("def-id");
    assertNotNull(result);
    assertEquals(5, result.getCount());
  }

  @Test
  void generateMigrationPlan_returnsResult() {
    when(tokenService.getToken()).thenReturn("tok");
    var plan = new MigrationPlan();
    when(responseSpec.body(eq(MigrationPlan.class))).thenReturn(plan);

    var result = client.generateMigrationPlan(new GenerateMigrationPlanRequest());
    assertNotNull(result);
  }

  @Test
  void executeMigrationPlanAsync_returnsResult() {
    when(tokenService.getToken()).thenReturn("tok");
    var response = new ExecuteMigrationPlanAsyncResponse();
    when(responseSpec.body(eq(ExecuteMigrationPlanAsyncResponse.class))).thenReturn(response);

    var result = client.executeMigrationPlanAsync(new ExecuteMigrationPlanRequest());
    assertNotNull(result);
  }

  @Test
  void syncResources_returnsResult() {
    when(tokenService.getToken()).thenReturn("tok");
    var deployment = new CamundaDeploymentResponse();
    when(responseSpec.body(eq(CamundaDeploymentResponse.class))).thenReturn(deployment);

    var result = client.syncResources("deploy", new Resource[0]);
    assertNotNull(result);
  }

  @Test
  void getProcessDefinition_withTenant_addsTenantIdInQueryParam() {
    when(tokenService.getToken()).thenReturn("tok");
    when(responseSpec.body(any(ParameterizedTypeReference.class)))
        .thenReturn(List.of(new ProcessDefinition()));

    tenantClient("tenant-a").getProcessDefinition("key", 2);

    var uriCaptor = ArgumentCaptor.forClass(URI.class);
    verify(requestHeadersUriSpec).uri(uriCaptor.capture());
    assertTrue(uriCaptor.getValue().getQuery().contains("tenantIdIn=tenant-a"));
  }

  @Test
  void getProcessDefinition_withoutTenant_omitsTenantIdInQueryParam() {
    when(tokenService.getToken()).thenReturn("tok");
    when(responseSpec.body(any(ParameterizedTypeReference.class)))
        .thenReturn(List.of(new ProcessDefinition()));

    client.getProcessDefinition("key", 2);

    var uriCaptor = ArgumentCaptor.forClass(URI.class);
    verify(requestHeadersUriSpec).uri(uriCaptor.capture());
    assertFalse(uriCaptor.getValue().getQuery().contains("tenantIdIn"));
  }

  @Test
  void syncResources_withTenant_addsTenantIdFormField() {
    when(tokenService.getToken()).thenReturn("tok");
    when(responseSpec.body(eq(CamundaDeploymentResponse.class)))
        .thenReturn(new CamundaDeploymentResponse());

    tenantClient("tenant-a").syncResources("deploy", new Resource[0]);

    var bodyCaptor = ArgumentCaptor.forClass(Object.class);
    verify(requestBodyUriSpec).body(bodyCaptor.capture());
    var body = (MultiValueMap<String, Object>) bodyCaptor.getValue();
    assertEquals("tenant-a", body.getFirst("tenant-id"));
  }

  @Test
  void syncResources_withoutTenant_omitsTenantIdFormField() {
    when(tokenService.getToken()).thenReturn("tok");
    when(responseSpec.body(eq(CamundaDeploymentResponse.class)))
        .thenReturn(new CamundaDeploymentResponse());

    client.syncResources("deploy", new Resource[0]);

    var bodyCaptor = ArgumentCaptor.forClass(Object.class);
    verify(requestBodyUriSpec).body(bodyCaptor.capture());
    var body = (MultiValueMap<String, Object>) bodyCaptor.getValue();
    assertFalse(body.containsKey("tenant-id"));
  }

  @Test
  void getProcessDefinition_onError_throwsCamundaResponseException() {
    when(tokenService.getToken()).thenReturn("tok");
    when(responseSpec.body(any(ParameterizedTypeReference.class)))
        .thenThrow(new OpenTmfClientResponseException(HttpStatus.GATEWAY_TIMEOUT, "timeout"));

    var ex = assertThrows(CamundaResponseException.class,
        () -> client.getProcessDefinition("key", 2));
    assertEquals("timeout", ex.getMessage());
  }
}
