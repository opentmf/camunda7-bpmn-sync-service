package org.opentmf.bpmn.sync.client.impl;

import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opentmf.bpmn.sync.client.api.RestCamundaClient;
import org.opentmf.bpmn.sync.config.CamundaProperties;
import org.opentmf.bpmn.sync.exception.CamundaResponseException;
import org.opentmf.bpmn.sync.model.CamundaDeploymentResponse;
import org.opentmf.bpmn.sync.model.ExecuteMigrationPlanAsyncResponse;
import org.opentmf.bpmn.sync.model.ExecuteMigrationPlanRequest;
import org.opentmf.bpmn.sync.model.GenerateMigrationPlanRequest;
import org.opentmf.bpmn.sync.model.MigrationPlan;
import org.opentmf.bpmn.sync.model.ObjectCount;
import org.opentmf.bpmn.sync.model.ProcessDefinition;
import org.opentmf.bpmn.sync.util.ResourceUtil;
import org.opentmf.client.common.exception.OpenTmfClientResponseException;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.rest.service.api.SyncTokenService;
import org.opentmf.client.rest.util.SyncClientUtil;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Synchronous {@link RestCamundaClient} implementation backed by {@link RestClient}.
 *
 * @author Gokhan Demir
 */
@RequiredArgsConstructor
@Slf4j
public class RestCamundaClientImpl implements RestCamundaClient {

  private final RestClient restClient;
  private final SyncTokenService tokenService;
  private final ClientProperties clientProperties;
  private final CamundaProperties camundaProperties;
  private final String tenantId;

  @Override
  public ProcessDefinition getProcessDefinition(String key, int version) {
    var builder = UriComponentsBuilder
        .fromUriString(camundaProperties.getBaseUrl() + "/process-definition")
        .queryParam("key", key)
        .queryParam("version", version);
    if (StringUtils.hasText(tenantId)) {
      builder.queryParam("tenantIdIn", tenantId);
    }
    URI uri = builder.build().toUri();
    List<ProcessDefinition> list = doGet(uri, new ParameterizedTypeReference<>() {});
    return list != null && !list.isEmpty() ? list.get(0) : null;
  }

  @Override
  public ObjectCount getProcessInstanceCount(String processDefinitionId) {
    URI uri = UriComponentsBuilder
        .fromUriString(camundaProperties.getBaseUrl() + "/process-instance/count")
        .queryParam("processDefinitionId", processDefinitionId)
        .build().toUri();
    return doGet(uri, ObjectCount.class);
  }

  @Override
  public MigrationPlan generateMigrationPlan(GenerateMigrationPlanRequest request) {
    return doPost(URI.create(camundaProperties.getBaseUrl() + "/migration/generate"),
        request, MigrationPlan.class);
  }

  @Override
  public ExecuteMigrationPlanAsyncResponse executeMigrationPlanAsync(
      ExecuteMigrationPlanRequest request) {
    return doPost(URI.create(camundaProperties.getBaseUrl() + "/migration/executeAsync"),
        request, ExecuteMigrationPlanAsyncResponse.class);
  }

  @Override
  public CamundaDeploymentResponse syncResources(String deploymentName, Resource[] resources) {
    try {
      String token = tokenService.getToken();
      return SyncClientUtil.executeWithRetry(
          () -> doMultipartPost(deploymentName, resources, token),
          clientProperties.getNumRetries(),
          clientProperties.getRetryWaitDuration());
    } catch (OpenTmfClientResponseException e) {
      throw new CamundaResponseException(e);
    }
  }

  private <T> T doGet(URI uri, Class<T> responseType) {
    try {
      String token = tokenService.getToken();
      return SyncClientUtil.executeWithRetry(() ->
          restClient.get()
              .uri(uri)
              .headers(h -> h.setBearerAuth(token))
              .retrieve()
              .body(responseType),
          clientProperties.getNumRetries(),
          clientProperties.getRetryWaitDuration());
    } catch (OpenTmfClientResponseException e) {
      throw new CamundaResponseException(e);
    }
  }

  private <T> T doGet(URI uri, ParameterizedTypeReference<T> responseType) {
    try {
      String token = tokenService.getToken();
      return SyncClientUtil.executeWithRetry(() ->
          restClient.get()
              .uri(uri)
              .headers(h -> h.setBearerAuth(token))
              .retrieve()
              .body(responseType),
          clientProperties.getNumRetries(),
          clientProperties.getRetryWaitDuration());
    } catch (OpenTmfClientResponseException e) {
      throw new CamundaResponseException(e);
    }
  }

  private <T> T doPost(URI uri, Object body, Class<T> responseType) {
    try {
      String token = tokenService.getToken();
      return SyncClientUtil.executeWithRetry(() ->
          restClient.post()
              .uri(uri)
              .headers(h -> h.setBearerAuth(token))
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(responseType),
          clientProperties.getNumRetries(),
          clientProperties.getRetryWaitDuration());
    } catch (OpenTmfClientResponseException e) {
      throw new CamundaResponseException(e);
    }
  }

  private CamundaDeploymentResponse doMultipartPost(String deploymentName,
      Resource[] resources, String token) {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("deployment-name", deploymentName);
    body.add("deployment-source", "BPMN Sync Service");
    body.add("deploy-changed-only", "true");
    if (StringUtils.hasText(tenantId)) {
      body.add("tenant-id", tenantId);
    }
    for (Resource bpmn : resources) {
      body.add(ResourceUtil.getResourceNameWithFolder(bpmn), bpmn);
    }

    return restClient.post()
        .uri(URI.create(camundaProperties.getBaseUrl() + "/deployment/create"))
        .headers(h -> h.setBearerAuth(token))
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(body)
        .retrieve()
        .body(CamundaDeploymentResponse.class);
  }
}
