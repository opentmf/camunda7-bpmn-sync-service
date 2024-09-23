package com.pia.bpmn.sync.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.http.HttpStatus.GATEWAY_TIMEOUT;
import static org.springframework.http.HttpStatus.OK;

import com.pia.bpmn.sync.BaseIT;
import com.pia.bpmn.sync.exception.CamundaResponseException;
import com.pia.bpmn.sync.model.CamundaErrorResponse;
import com.pia.bpmn.sync.model.ExecuteMigrationPlanAsyncResponse;
import com.pia.bpmn.sync.model.ExecuteMigrationPlanRequest;
import com.pia.bpmn.sync.model.GenerateMigrationPlanRequest;
import com.pia.bpmn.sync.model.MigrationPlan;
import com.pia.bpmn.sync.model.ProcessInstanceQuery;
import com.pia.bpmn.sync.util.JacksonTestUtil;
import java.util.Collections;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

/**
 * @author Gokhan Demir
 */
@ActiveProfiles("it")
@DirtiesContext
class CamundaClientIT extends BaseIT {

  static {
    System.setProperty("server.port", "8883");
  }

  @BeforeAll
  void beforeAll() {
    camundaProperties.setBaseUrl(mockServer.getBaseUrl());
  }

  @Test
  void test_getProcessDefinitions_returnsValidResult() {
    var responseBody = JacksonTestUtil.contents("json/process_definition_list.json");
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
    openidClientProperties.setNumRetries(0);
    var camundaErrorResponse = JacksonTestUtil.contents("json/camunda_error.json");
    mockServer.expectGet("/process-definition", GATEWAY_TIMEOUT, camundaErrorResponse);

    StepVerifier.create(getCamundaClient().getProcessDefinition("UCMainNumber_Add", 2))
        .expectErrorMatches(error -> {
          assertInstanceOf(CamundaResponseException.class, error);
          assertEquals("504 Gateway Timeout: Exceeded timeout value waiting for response "
                  + "from the remote Camunda server. Details:  1) Request was sent 30 seconds ago.",
              error.getMessage());
          return true;
        })
        .verify();
  }

  @Test
  void test_getProcessDefinitions_returnsGatewayTimeoutAndNullMessage() {
    openidClientProperties.setNumRetries(0);
    mockServer.expectGet("/process-definition", GATEWAY_TIMEOUT);

    StepVerifier.create(getCamundaClient().getProcessDefinition("UCMainNumber_Add", 2))
        .expectErrorMatches(error -> {
          assertInstanceOf(CamundaResponseException.class, error);
          assertNull(error.getMessage());
          return true;
        })
        .verify();
  }

  @Test
  void test_getProcessDefinitions_returnsGatewayTimeoutAfterTwoRetries() {
    openidClientProperties.setNumRetries(2);
    openidClientProperties.setRetryWaitMillis(100L);
    var camundaErrorResponse = JacksonTestUtil.fileToObject("json/camunda_error.json", CamundaErrorResponse.class);
    camundaErrorResponse.setDetails(Collections.emptyList());
    mockServer.expectGet("/process-definition", 3, GATEWAY_TIMEOUT,
        JacksonTestUtil.objectToJson(camundaErrorResponse));

    StepVerifier.create(getCamundaClient().getProcessDefinition("UCMainNumber_Add", 2))
        .expectErrorMatches(error -> {
          assertInstanceOf(CamundaResponseException.class, error);
          assertEquals("504 Gateway Timeout: Exceeded timeout value waiting for response "
                  + "from the remote Camunda server.",
              error.getMessage());
          return true;
        })
        .verify();
  }

  @Test
  void test_getProcessInstanceCount_returnsValidResult() {
    var processDefinitionId = "UCMainNumber_Add:2:f113c4a4-904d-11ee-aaa3-0242ac1a000d";
    var responseBody = JacksonTestUtil.contents("json/process_instance_count.json");
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
    var requestBody = JacksonTestUtil.contents("json/generate_migration_plan_request.json");
    var responseBody = JacksonTestUtil.contents("json/migration_plan.json");
    mockServer.expectPost("/migration/generate", requestBody, OK, responseBody);

    var request = JacksonTestUtil.jsonToObject(requestBody, GenerateMigrationPlanRequest.class);
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
    String requestBody = JacksonTestUtil.objectToJson(executeMigrationPlanRequest);
    String responseBody = JacksonTestUtil.contents("json/migration_execute_async_response.json");
    mockServer.expectPost("/migration/executeAsync", requestBody, OK, responseBody);

    StepVerifier.create(getCamundaClient().executeMigrationPlanAsync(executeMigrationPlanRequest))
        .expectNextMatches(response -> {
          assertNotNull(response);
          Assertions.assertThat(
                  JacksonTestUtil.jsonToObject(responseBody, ExecuteMigrationPlanAsyncResponse.class))
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
        JacksonTestUtil.fileToObject("json/migration_plan.json", MigrationPlan.class));
    executeMigrationPlanRequest.setProcessInstanceQuery(
        JacksonTestUtil.fileToObject("json/process_instance_query.json", ProcessInstanceQuery.class));
    return executeMigrationPlanRequest;
  }
}
