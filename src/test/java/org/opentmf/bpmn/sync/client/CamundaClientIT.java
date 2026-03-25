package org.opentmf.bpmn.sync.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpStatus.GATEWAY_TIMEOUT;
import static org.springframework.http.HttpStatus.OK;

import org.opentmf.bpmn.sync.BaseIT;
import org.opentmf.bpmn.sync.exception.CamundaResponseException;
import org.opentmf.bpmn.sync.model.CamundaErrorResponse;
import org.opentmf.bpmn.sync.model.ExecuteMigrationPlanAsyncResponse;
import org.opentmf.bpmn.sync.model.ExecuteMigrationPlanRequest;
import org.opentmf.bpmn.sync.model.GenerateMigrationPlanRequest;
import org.opentmf.bpmn.sync.model.MigrationPlan;
import org.opentmf.bpmn.sync.model.ProcessInstanceQuery;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.commons.util.JacksonUtil;
import java.time.Duration;
import java.util.Collections;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import reactor.test.StepVerifier;

/**
 * @author Gokhan Demir
 */
@DirtiesContext
class CamundaClientIT extends BaseIT {

  private ClientProperties getClientProperties() {
    String ref = bpmnSyncProperties.getClientRef();
    return (ClientProperties) ctx.getBean(ref + "ClientProperties");
  }

  @Test
  void test_getProcessDefinitions_returnsValidResult() {
    var responseBody = JacksonUtil.contents("json/process_definition_list.json");
    mockServer.expectGet("/process-definition", OK, responseBody);

    StepVerifier.create(getCamundaClient().getProcessDefinition("UCMainNumber_Add", 2))
        .expectNextMatches(processDefinition -> {
          assertEquals(2, processDefinition.getVersion());
          return true;
        })
        .expectComplete()
        .verify();
  }

  @Test
  void test_getProcessDefinitions_returnsGatewayTimeout() {
    getClientProperties().setNumRetries(0);
    var camundaErrorResponse = JacksonUtil.contents("json/camunda_error.json");
    mockServer.expectGet("/process-definition", GATEWAY_TIMEOUT, camundaErrorResponse);

    StepVerifier.create(getCamundaClient().getProcessDefinition("UCMainNumber_Add", 2))
        .expectErrorMatches(error -> {
          assertInstanceOf(CamundaResponseException.class, error);
          assertNotNull(error.getMessage());
          assertTrue(error.getMessage().contains("504"));
          assertTrue(error.getMessage().contains("Exceeded timeout value"));
          return true;
        })
        .verify();
  }

  @Test
  void test_getProcessDefinitions_returnsGatewayTimeoutAndEmptyBody() {
    getClientProperties().setNumRetries(0);
    mockServer.expectGet("/process-definition", GATEWAY_TIMEOUT);

    StepVerifier.create(getCamundaClient().getProcessDefinition("UCMainNumber_Add", 2))
        .expectErrorMatches(error -> {
          assertInstanceOf(CamundaResponseException.class, error);
          assertNotNull(error.getMessage());
          assertTrue(error.getMessage().contains("504"));
          return true;
        })
        .verify();
  }

  @Test
  void test_getProcessDefinitions_returnsGatewayTimeoutAfterTwoRetries() {
    var clientProps = getClientProperties();
    clientProps.setNumRetries(2);
    clientProps.setRetryWaitDuration(Duration.ofMillis(100L));
    var camundaErrorResponse = JacksonUtil.fileToObject("json/camunda_error.json", CamundaErrorResponse.class);
    camundaErrorResponse.setDetails(Collections.emptyList());
    mockServer.expectGet("/process-definition", 3, GATEWAY_TIMEOUT,
        JacksonUtil.objectToPrettyJson(camundaErrorResponse));

    StepVerifier.create(getCamundaClient().getProcessDefinition("UCMainNumber_Add", 2))
        .expectErrorMatches(error -> {
          assertInstanceOf(CamundaResponseException.class, error);
          assertNotNull(error.getMessage());
          assertTrue(error.getMessage().contains("504"));
          assertTrue(error.getMessage().contains("Exceeded timeout value"));
          return true;
        })
        .verify();
  }

  @Test
  void test_getProcessInstanceCount_returnsValidResult() {
    var processDefinitionId = "UCMainNumber_Add:2:f113c4a4-904d-11ee-aaa3-0242ac1a000d";
    var responseBody = JacksonUtil.contents("json/process_instance_count.json");
    mockServer.expectGet("/process-instance/count", OK, responseBody);

    StepVerifier.create(getCamundaClient().getProcessInstanceCount(processDefinitionId))
        .expectNextMatches(count -> {
          assertEquals(1, count.getCount());
          return true;
        })
        .expectComplete()
        .verify();
  }

  @Test
  void test_generateMigrationPlan_returnsValidResult() {
    var requestBody = JacksonUtil.contents("json/generate_migration_plan_request.json");
    var responseBody = JacksonUtil.contents("json/migration_plan.json");
    mockServer.expectPost("/migration/generate", requestBody, OK, responseBody);

    var request = JacksonUtil.jsonToObject(requestBody, GenerateMigrationPlanRequest.class);
    StepVerifier.create(getCamundaClient().generateMigrationPlan(request))
        .expectNextMatches(migrationPlan -> {
          assertEquals(10, migrationPlan.getInstructions().size());
          return true;
        })
        .expectComplete()
        .verify();
  }

  @Test
  void test_executeMigrationPlanRequest_returnsValidResult() {
    var executeMigrationPlanRequest = executeMigrationPlanRequest();
    String requestBody = JacksonUtil.objectToPrettyJson(executeMigrationPlanRequest);
    String responseBody = JacksonUtil.contents("json/migration_execute_async_response.json");
    mockServer.expectPost("/migration/executeAsync", requestBody, OK, responseBody);

    StepVerifier.create(getCamundaClient().executeMigrationPlanAsync(executeMigrationPlanRequest))
        .expectNextMatches(response -> {
          assertNotNull(response);
          Assertions.assertThat(
                  JacksonUtil.jsonToObject(responseBody, ExecuteMigrationPlanAsyncResponse.class))
              .usingRecursiveComparison()
              .isEqualTo(response);
          return true;
        })
        .expectComplete()
        .verify();
  }

  private ExecuteMigrationPlanRequest executeMigrationPlanRequest() {
    var executeMigrationPlanRequest = new ExecuteMigrationPlanRequest();
    executeMigrationPlanRequest.setMigrationPlan(
        JacksonUtil.fileToObject("json/migration_plan.json", MigrationPlan.class));
    executeMigrationPlanRequest.setProcessInstanceQuery(
        JacksonUtil.fileToObject("json/process_instance_query.json", ProcessInstanceQuery.class));
    return executeMigrationPlanRequest;
  }
}
