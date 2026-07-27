package org.opentmf.bpmn.sync.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.opentmf.bpmn.sync.DockerCamundaBaseIT;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Shared test logic for BPMN sync integration tests.
 * Subclasses only configure containers and client-ref.
 */
abstract class BpmnSyncBaseIT extends DockerCamundaBaseIT {

  @Test
  void test_ensureBpmnConsistency_deploysBpmnAndDmnFilesSuccessfully() {
    bpmnSyncProperties.setEnabled(true);
    // A single, version-guarded deployment covers both BPMN and DMN. (The sync is idempotent per
    // version, so it must be invoked only once per test class - a second call would return null.)
    var result = Assertions.assertDoesNotThrow(() -> bpmnSyncService.ensureBpmnConsistency());
    assertNotNull(result);

    // 17 BPMN files under classpath:bpmn/ become process definitions.
    assertNotNull(result.getDeployedProcessDefinitions());
    assertEquals(17, result.getDeployedProcessDefinitions().size());

    // 2 DMN files under the resource location (classpath:bpmn/dmn/) land in their own response
    // map, separate from the process definitions, and are therefore never touched by
    // auto-migration.
    assertNotNull(result.getDeployedDecisionDefinitions());
    assertEquals(2, result.getDeployedDecisionDefinitions().size());
  }
}
