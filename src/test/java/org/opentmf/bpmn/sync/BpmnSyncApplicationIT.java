package org.opentmf.bpmn.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.opentmf.bpmn.sync.config.BpmnSyncAutoConfiguration;
import org.opentmf.bpmn.sync.config.BpmnSyncProperties;
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
        Exception.class,
        () ->
            new BpmnSyncAutoConfiguration(
                Mockito.mock(ApplicationContext.class),
                Mockito.mock(BpmnSyncProperties.class),
                null,
                null,
                new ObjectMapper()));
  }
}
