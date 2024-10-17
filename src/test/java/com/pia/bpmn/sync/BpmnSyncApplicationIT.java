package com.pia.bpmn.sync;

import com.pia.bpmn.sync.config.BpmnSyncAutoConfiguration;
import com.pia.bpmn.sync.config.BpmnSyncProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * @author Gokhan Demir
 */
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
@ActiveProfiles("it")
@DirtiesContext
class BpmnSyncApplicationIT {

  static {
    System.setProperty("server.port", "8884");
  }

  @Test
  void testBpmnSyncAutoConfiguration_withInvalidData_throwsException() {
    // for getting rid of unused class.
    Assertions.assertThrows(
        Exception.class, () -> new BpmnSyncAutoConfiguration(
            Mockito.mock(ApplicationContext.class),
            Mockito.mock(BpmnSyncProperties.class), null, null));
  }
}
