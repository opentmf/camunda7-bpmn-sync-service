package com.pia.bpmn.sync.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.pia.bpmn.sync.BaseIT;
import com.pia.bpmn.sync.service.api.BpmnSyncService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * @author Gokhan Demir
 */
@ActiveProfiles("embedded-camunda")
@DirtiesContext
class BpmnSyncServiceIT extends BaseIT {

  static {
    System.setProperty("desired.port", "8881");
  }

  @BeforeAll
  void beforeAll() {
    bpmnSyncService = getBpmnSyncService();
  }

  private BpmnSyncService bpmnSyncService;

  @Test
  void test_ensureBpmnConsistency_withDeploymentEnabled_deploysBpmnFilesSuccessfully() {
    bpmnSyncProperties.setEnabled(true);
    var result = Assertions.assertDoesNotThrow(bpmnSyncService::ensureBpmnConsistency);
    assertNotNull(result);
    assertNotNull(result.getDeployedProcessDefinitions());
    assertEquals(17, result.getDeployedProcessDefinitions().size());
  }
}
