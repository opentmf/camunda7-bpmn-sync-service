package org.opentmf.bpmn.sync.model;

import static org.opentmf.bpmn.sync.util.JacksonTestUtil.fileToObject;
import static org.opentmf.bpmn.sync.util.JacksonTestUtil.jsonToObject;
import static org.opentmf.bpmn.sync.util.JacksonTestUtil.objectToJson;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
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
    String newJson = objectToJson(object);
    org.assertj.core.api.Assertions
        .assertThat(object).usingRecursiveComparison()
        .isEqualTo(jsonToObject(newJson, clazz));
  }
}
