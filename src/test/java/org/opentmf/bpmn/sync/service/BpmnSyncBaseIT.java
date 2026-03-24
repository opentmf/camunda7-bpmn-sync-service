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
  void test_ensureBpmnConsistency_deploysBpmnFilesSuccessfully() {
    bpmnSyncProperties.setEnabled(true);
    var result = Assertions.assertDoesNotThrow(() -> bpmnSyncService.ensureBpmnConsistency());
    assertNotNull(result);
    assertNotNull(result.getDeployedProcessDefinitions());
    assertEquals(17, result.getDeployedProcessDefinitions().size());
  }
}
