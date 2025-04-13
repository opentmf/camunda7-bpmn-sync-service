package org.opentmf.bpmn.sync.client.impl;

import static org.opentmf.client.common.util.WebClientUtil.retry;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import org.opentmf.bpmn.sync.client.api.CamundaClient;
import org.opentmf.bpmn.sync.config.CamundaProperties;
import org.opentmf.bpmn.sync.exception.CamundaResponseException;
import org.opentmf.bpmn.sync.model.CamundaDeploymentResponse;
import org.opentmf.bpmn.sync.model.CamundaErrorResponse;
import org.opentmf.bpmn.sync.model.ExecuteMigrationPlanAsyncResponse;
import org.opentmf.bpmn.sync.model.ExecuteMigrationPlanRequest;
import org.opentmf.bpmn.sync.model.GenerateMigrationPlanRequest;
import org.opentmf.bpmn.sync.model.MigrationPlan;
import org.opentmf.bpmn.sync.model.ObjectCount;
import org.opentmf.bpmn.sync.model.ProcessDefinition;
import org.opentmf.bpmn.sync.util.ResourceUtil;
import org.opentmf.client.common.model.BaseClientProperties;
import org.opentmf.client.common.service.api.TokenService;
import java.net.URI;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.lang.NonNull;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Gokhan Demir
 */
@RequiredArgsConstructor
@Slf4j
public class CamundaClientImpl implements CamundaClient {

  private final WebClient webClient;
  private final TokenService tokenService;
  private final BaseClientProperties clientProperties;
  private final CamundaProperties camundaProperties;

  @Override
  public Mono<ProcessDefinition> getProcessDefinition(String key, int version) {
    return tokenService.getToken()
        .flatMap(token -> getFluxResponse(
            processDefinitionsUri(key, version), token, ProcessDefinition.class)
            .next());
  }

  @Override
  public Mono<ObjectCount> getProcessInstanceCount(String processDefinitionId) {
    return tokenService.getToken()
        .flatMap(token -> getMonoResponse(
            processInstanceCountUri(processDefinitionId), token, ObjectCount.class));
  }

  @Override
  public Mono<MigrationPlan> generateMigrationPlan(GenerateMigrationPlanRequest request) {
    return tokenService.getToken()
        .flatMap(token -> post(generateMigrationPlanURI(), request, token, MigrationPlan.class));
  }

  @Override
  public Mono<ExecuteMigrationPlanAsyncResponse> executeMigrationPlanAsync(
      ExecuteMigrationPlanRequest request) {
    return tokenService.getToken()
        .flatMap(token -> post(executeMigrationPlanAsyncUri(), request, token,
            ExecuteMigrationPlanAsyncResponse.class));
  }

  @Override
  public Mono<CamundaDeploymentResponse> syncBpmnFiles(String deploymentName,
      Resource[] bpmnFiles) {
    return tokenService.getToken()
        .flatMap(token -> webClient.post().uri(deploymentURI())
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .headers(headers -> headers.set(AUTHORIZATION, getAuth(token)))
            .body(BodyInserters.fromMultipartData(getMultipartRequest(deploymentName, bpmnFiles)))
            .retrieve().onStatus(HttpStatusCode::isError, CamundaClientImpl::handleError)
            .bodyToMono(CamundaDeploymentResponse.class)
            .retryWhen(retry(clientProperties.getNumRetries(),
                Duration.ofMillis(clientProperties.getRetryWaitMillis()), 0)));
  }

  private String getAuth(String token) {
    return tokenService.getTokenType() + " " + token;
  }

  private URI deploymentURI() {
    return URI.create(camundaProperties.getBaseUrl() + "/deployment/create");
  }

  private URI processDefinitionsUri(String key, int version) {
    return UriComponentsBuilder
        .fromUriString(camundaProperties.getBaseUrl() + "/process-definition")
        .queryParam("key", key)
        .queryParam("version", version)
        .build().toUri();
  }

  private URI processInstanceCountUri(String processDefinitionId) {
    return UriComponentsBuilder
        .fromUriString(camundaProperties.getBaseUrl() + "/process-instance/count")
        .queryParam("processDefinitionId", processDefinitionId)
        .build().toUri();
  }

  private URI generateMigrationPlanURI() {
    return URI.create(camundaProperties.getBaseUrl() + "/migration/generate");
  }

  private URI executeMigrationPlanAsyncUri() {
    return URI.create(camundaProperties.getBaseUrl() + "/migration/executeAsync");
  }

  @NonNull
  private MultiValueMap<String, HttpEntity<?>> getMultipartRequest(String deploymentName,
      Resource[] bpmnFiles) {
    MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder.part("deployment-name", deploymentName);
    builder.part("deployment-source", "BPMN Sync Service");
    builder.part("deploy-changed-only", "true");
    for (Resource bpmn : bpmnFiles) {
      builder.part(ResourceUtil.getName(bpmn), bpmn);
    }
    return builder.build();
  }

  private <T> Flux<T> getFluxResponse(URI uri, String token, Class<T> t) {
    return webClient
        .get()
        .uri(uri)
        .headers(headers -> headers.set(AUTHORIZATION, getAuth(token)))
        .retrieve()
        .onStatus(HttpStatusCode::isError, CamundaClientImpl::handleError)
        .bodyToFlux(t)
        .retryWhen(retry(clientProperties.getNumRetries(),
            Duration.ofMillis(clientProperties.getRetryWaitMillis())));
  }

  private <T> Mono<T> getMonoResponse(URI uri, String token, Class<T> t) {
    return webClient
        .get()
        .uri(uri)
        .headers(headers -> headers.set(AUTHORIZATION, getAuth(token)))
        .retrieve()
        .onStatus(HttpStatusCode::isError, CamundaClientImpl::handleError)
        .bodyToMono(t)
        .retryWhen(retry(clientProperties.getNumRetries(),
            Duration.ofMillis(clientProperties.getRetryWaitMillis())));
  }

  private <T> Mono<T> post(URI uri, Object body, String accessToken, Class<T> t) {
    return post(uri, MediaType.APPLICATION_JSON, body, accessToken, t);
  }

  private <T> Mono<T> post(URI uri, MediaType contentType, Object body, String token, Class<T> t) {
    return webClient
        .post()
        .uri(uri)
        .contentType(contentType)
        .headers(headers -> headers.set(AUTHORIZATION, getAuth(token)))
        .bodyValue(body)
        .retrieve()
        .onStatus(HttpStatusCode::isError, CamundaClientImpl::handleError)
        .bodyToMono(t)
        .retryWhen(retry(clientProperties.getNumRetries(),
            Duration.ofMillis(clientProperties.getRetryWaitMillis())));
  }

  public static Mono<Throwable> handleError(ClientResponse clientResponse) {
    var request = clientResponse.request();
    var httpStatus = clientResponse.statusCode();
    log.debug("Handling {} for {} {}", httpStatus, request.getMethod(), request.getURI());
    return clientResponse
        .bodyToMono(CamundaErrorResponse.class)
        .doOnNext(error -> log.error("Camunda Error Details: {}", error))
        .switchIfEmpty(Mono.defer(() -> Mono.error(new CamundaResponseException(httpStatus, null))))
        .map(error -> new CamundaResponseException(httpStatus, error));
  }
}
