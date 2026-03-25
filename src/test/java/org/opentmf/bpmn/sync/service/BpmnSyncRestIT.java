package org.opentmf.bpmn.sync.service;

import org.opentmf.bpmn.sync.CamundaTestContainers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integration test for BPMN sync using the REST (RestClient) client.
 *
 * @author Gokhan Demir
 */
@ActiveProfiles("it")
class BpmnSyncRestIT extends BpmnSyncBaseIT {

  static final CamundaTestContainers CONTAINERS = new CamundaTestContainers();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    CONTAINERS.registerProperties(r, "rest");
  }
}
