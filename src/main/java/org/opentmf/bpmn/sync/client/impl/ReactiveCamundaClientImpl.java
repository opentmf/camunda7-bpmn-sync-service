package org.opentmf.bpmn.sync.client.impl;

import static org.opentmf.client.reactive.util.WebClientUtil.retry;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import org.opentmf.bpmn.sync.client.api.ReactiveCamundaClient;
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
import org.opentmf.client.reactive.service.api.TokenService;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.jspecify.annotations.NonNull;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive {@link ReactiveCamundaClient} implementation backed by {@link WebClient}.
 *
 * @author Gokhan Demir
 */
@RequiredArgsConstructor
@Slf4j
public class ReactiveCamundaClientImpl implements ReactiveCamundaClient {

  private final WebClient webClient;
  private final TokenService tokenService;
  private final ClientProperties clientProperties;
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
  public Mono<CamundaDeploymentResponse> syncResources(String deploymentName,
      Resource[] resources) {
    return tokenService
        .getToken()
        .flatMap(
            token ->
                webClient
                    .post()
                    .uri(deploymentURI())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .headers(headers -> headers.set(AUTHORIZATION, getAuth(token)))
                    .body(
                        BodyInserters.fromMultipartData(
                            getMultipartRequest(deploymentName, resources)))
                    .retrieve()
                    .bodyToMono(CamundaDeploymentResponse.class)
                    .onErrorMap(OpenTmfClientResponseException.class, CamundaResponseException::new)
                    .retryWhen(
                        retry(
                            clientProperties.getNumRetries(),
                            clientProperties.getRetryWaitDuration(),
                            0)));
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
      Resource[] resources) {
    var builder = new org.springframework.http.client.MultipartBodyBuilder();
    builder.part("deployment-name", deploymentName);
    builder.part("deployment-source", "BPMN Sync Service");
    builder.part("deploy-changed-only", "true");
    for (Resource bpmn : resources) {
      builder.part(ResourceUtil.getResourceNameWithFolder(bpmn), bpmn);
    }
    return builder.build();
  }

  private <T> Flux<T> getFluxResponse(URI uri, String token, Class<T> t) {
    return webClient
        .get()
        .uri(uri)
        .headers(headers -> headers.set(AUTHORIZATION, getAuth(token)))
        .retrieve()
        .bodyToFlux(t)
        .onErrorMap(OpenTmfClientResponseException.class, CamundaResponseException::new)
        .retryWhen(
            retry(clientProperties.getNumRetries(), clientProperties.getRetryWaitDuration()));
  }

  private <T> Mono<T> getMonoResponse(URI uri, String token, Class<T> t) {
    return webClient
        .get()
        .uri(uri)
        .headers(headers -> headers.set(AUTHORIZATION, getAuth(token)))
        .retrieve()
        .bodyToMono(t)
        .onErrorMap(OpenTmfClientResponseException.class, CamundaResponseException::new)
        .retryWhen(
            retry(clientProperties.getNumRetries(), clientProperties.getRetryWaitDuration()));
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
        .bodyToMono(t)
        .onErrorMap(OpenTmfClientResponseException.class, CamundaResponseException::new)
        .retryWhen(
            retry(clientProperties.getNumRetries(), clientProperties.getRetryWaitDuration()));
  }
}
