package org.opentmf.bpmn.sync.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * @author Gokhan Demir
 */
class ResourceUtilTests {

  private static final String LOCATION = "classpath:bpmn/";

  @Test
  void test_getBpmnFiles_returnsValidResult() {
    Resource[] resources = ResourceUtil.getBpmnFiles(LOCATION);
    Assertions.assertNotNull(resources);
    Assertions.assertEquals(17, resources.length);
    var set = new HashSet<String>();
    for (Resource r : resources) {
      var name = ResourceUtil.getResourceNameWithFolder(r, LOCATION);
      Assertions.assertFalse(name.startsWith("/"));
      Assertions.assertFalse(name.startsWith("/bpmn"));
      set.add(name);
    }
    Assertions.assertEquals(17, set.size());
  }

  @Test
  void test_getDmnFiles_returnsValidResult() {
    Resource[] resources = ResourceUtil.getDmnFiles(LOCATION);
    Assertions.assertNotNull(resources);
    Assertions.assertEquals(2, resources.length);
    var set = new HashSet<String>();
    for (Resource r : resources) {
      var name = ResourceUtil.getResourceNameWithFolder(r, LOCATION);
      Assertions.assertFalse(name.startsWith("/"));
      set.add(name);
    }
    Assertions.assertEquals(2, set.size());
    // sub-folder structure under the resource location is preserved, the location is stripped
    Assertions.assertTrue(set.contains("dmn/routing.dmn"));
    Assertions.assertTrue(set.contains("dmn/sub/bounce.dmn"));
  }

  @Test
  void test_getDeployableResources_combinesBpmnAndDmn() {
    Resource[] resources = ResourceUtil.getDeployableResources(LOCATION);
    Assertions.assertNotNull(resources);
    Assertions.assertEquals(
        ResourceUtil.getBpmnFiles(LOCATION).length + ResourceUtil.getDmnFiles(LOCATION).length,
        resources.length);
    Assertions.assertEquals(19, resources.length);
  }

  @Test
  void test_getDeployableResources_withCustomLocation_stripsThatLocation() {
    Resource[] resources = ResourceUtil.getDeployableResources("classpath:bpmn/dmn/");
    Assertions.assertEquals(2, resources.length);
    var set = new HashSet<String>();
    for (Resource r : resources) {
      set.add(ResourceUtil.getResourceNameWithFolder(r, "classpath:bpmn/dmn/"));
    }
    Assertions.assertTrue(set.contains("routing.dmn"));
    Assertions.assertTrue(set.contains("sub/bounce.dmn"));
  }

  @Test
  void test_getDeployableResources_withoutTrailingSlash_isNormalized() {
    Assertions.assertEquals(19, ResourceUtil.getDeployableResources("classpath:bpmn").length);
  }

  @Test
  void test_getDeployableResources_withNonexistentLocation_throwsWithConfigHint() {
    var e = Assertions.assertThrows(IllegalStateException.class,
        () -> ResourceUtil.getDeployableResources("classpath:no-such-folder/"));
    Assertions.assertTrue(e.getMessage().contains("classpath:no-such-folder/"));
    Assertions.assertTrue(e.getMessage().contains("opentmf.bpmn-sync.resource-location"));
  }

  @Test
  void test_getDeployableResources_withEmptyLocation_throws() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> ResourceUtil.getDeployableResources(" "));
  }

  @Test
  void test_getName_withNonBpmnResource_returnsFileName() {
    var resource = new PathMatchingResourcePatternResolver().getResource(
        "classpath:json/migration_plan.json");
    assertEquals("migration_plan.json",
        ResourceUtil.getResourceNameWithFolder(resource, LOCATION));
  }

  @Test
  void test_getName_withNonFileResource_returnsResourceAsTheName() {
    var resource = new ByteArrayResource(new byte[]{1, 2, 3});
    assertEquals("Resource", ResourceUtil.getResourceNameWithFolder(resource, LOCATION));
  }
}
