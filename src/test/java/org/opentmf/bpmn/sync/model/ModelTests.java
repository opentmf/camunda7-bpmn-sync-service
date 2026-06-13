package org.opentmf.bpmn.sync.model;

import static org.opentmf.commons.util.JacksonUtil.fileToObject;
import static org.opentmf.commons.util.JacksonUtil.jsonToObject;
import static org.opentmf.commons.util.JacksonUtil.objectToPrettyJson;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * @author Gokhan Demir
 */
class ModelTests {

  static Stream<Arguments> supportedCamundaModels() {
    return Stream.of(
        arguments("json/camunda_deployment_response.json", CamundaDeploymentResponse.class),
        arguments("json/camunda_error.json", CamundaErrorResponse.class),
        arguments("json/generate_migration_plan_request.json", GenerateMigrationPlanRequest.class),
        arguments("json/migration_execute_async_response.json",
            ExecuteMigrationPlanAsyncResponse.class),
        arguments("json/migration_plan.json", MigrationPlan.class),
        arguments("json/process_definition_list.json", ProcessDefinition[].class),
        arguments("json/process_instance_count.json", ObjectCount.class),
        arguments("json/process_instance_query.json", ProcessInstanceQuery.class)
    );
  }

  @ParameterizedTest
  @MethodSource("supportedCamundaModels")
  void testJson_deserializesSuccessfully_andProducesSameObject_whenSerializedAgain(
      String originalJson, Class<?> clazz) {
    Object object = fileToObject(originalJson, clazz);
    Assertions.assertNotNull(object);
    String newJson = objectToPrettyJson(object);
    org.assertj.core.api.Assertions
        .assertThat(object).usingRecursiveComparison()
        .isEqualTo(jsonToObject(newJson, clazz));
  }

  @Test
  void testTotalDeployedCount_isNullSafeSumOfAllArtifactMaps() {
    var empty = new CamundaDeploymentResponse();
    Assertions.assertEquals(0, empty.totalDeployedCount());

    var response = new CamundaDeploymentResponse();
    response.setDeployedProcessDefinitions(Map.of("a", new ProcessDefinition()));
    response.setDeployedDecisionDefinitions(
        Map.of("b", new DecisionDefinition(), "c", new DecisionDefinition()));
    response.setDeployedDecisionRequirementsDefinitions(
        Map.of("d", new DecisionRequirementsDefinition()));

    Assertions.assertEquals(1, response.getDeployedProcessDefinitionCount());
    Assertions.assertEquals(2, response.getDeployedDecisionDefinitionCount());
    Assertions.assertEquals(1, response.getDeployedDecisionRequirementsDefinitionCount());
    Assertions.assertEquals(4, response.totalDeployedCount());
  }
}
