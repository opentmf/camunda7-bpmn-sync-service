package org.opentmf.bpmn.sync.client.impl;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opentmf.bpmn.sync.client.api.CamundaRestClient;
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
import org.opentmf.client.rest.util.RestTemplateUtil;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Synchronous {@link CamundaRestClient} implementation backed by {@link RestTemplate}.
 *
 * @author Gokhan Demir
 */
@RequiredArgsConstructor
@Slf4j
public class RestCamundaClientImpl implements CamundaRestClient {

  private final RestTemplate restTemplate;
  private final SyncTokenService tokenService;
  private final ClientProperties clientProperties;
  private final CamundaProperties camundaProperties;

  @Override
  public ProcessDefinition getProcessDefinition(String key, int version) {
    URI uri = UriComponentsBuilder
        .fromUriString(camundaProperties.getBaseUrl() + "/process-definition")
        .queryParam("key", key)
        .queryParam("version", version)
        .build().toUri();
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
  public CamundaDeploymentResponse syncBpmnFiles(String deploymentName, Resource[] bpmnFiles) {
    try {
      String token = tokenService.getToken();
      return RestTemplateUtil.executeWithRetry(
          () -> doMultipartPost(deploymentName, bpmnFiles, token),
          clientProperties.getNumRetries(),
          Duration.ofMillis(clientProperties.getRetryWaitMillis()));
    } catch (OpenTmfClientResponseException e) {
      throw new CamundaResponseException(e);
    }
  }

  private <T> T doGet(URI uri, Class<T> responseType) {
    try {
      String token = tokenService.getToken();
      return RestTemplateUtil.executeWithRetry(() -> {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<T> response = restTemplate.exchange(
            uri, HttpMethod.GET, new HttpEntity<>(headers), responseType);
        return response.getBody();
      }, clientProperties.getNumRetries(),
          Duration.ofMillis(clientProperties.getRetryWaitMillis()));
    } catch (OpenTmfClientResponseException e) {
      throw new CamundaResponseException(e);
    }
  }

  private <T> T doGet(URI uri, ParameterizedTypeReference<T> responseType) {
    try {
      String token = tokenService.getToken();
      return RestTemplateUtil.executeWithRetry(() -> {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<T> response = restTemplate.exchange(
            uri, HttpMethod.GET, new HttpEntity<>(headers), responseType);
        return response.getBody();
      }, clientProperties.getNumRetries(),
          Duration.ofMillis(clientProperties.getRetryWaitMillis()));
    } catch (OpenTmfClientResponseException e) {
      throw new CamundaResponseException(e);
    }
  }

  private <T> T doPost(URI uri, Object body, Class<T> responseType) {
    try {
      String token = tokenService.getToken();
      return RestTemplateUtil.executeWithRetry(() -> {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<T> response = restTemplate.exchange(
            uri, HttpMethod.POST, new HttpEntity<>(body, headers), responseType);
        return response.getBody();
      }, clientProperties.getNumRetries(),
          Duration.ofMillis(clientProperties.getRetryWaitMillis()));
    } catch (OpenTmfClientResponseException e) {
      throw new CamundaResponseException(e);
    }
  }

  private CamundaDeploymentResponse doMultipartPost(String deploymentName,
      Resource[] bpmnFiles, String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("deployment-name", deploymentName);
    body.add("deployment-source", "BPMN Sync Service");
    body.add("deploy-changed-only", "true");
    for (Resource bpmn : bpmnFiles) {
      body.add(ResourceUtil.getResourceNameWithFolder(bpmn), bpmn);
    }

    ResponseEntity<CamundaDeploymentResponse> response = restTemplate.exchange(
        URI.create(camundaProperties.getBaseUrl() + "/deployment/create"),
        HttpMethod.POST, new HttpEntity<>(body, headers), CamundaDeploymentResponse.class);
    return response.getBody();
  }
}
