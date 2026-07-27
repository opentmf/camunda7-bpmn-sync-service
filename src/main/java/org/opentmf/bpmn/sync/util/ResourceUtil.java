package org.opentmf.bpmn.sync.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Generated;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;

/**
 * @author Gokhan Demir
 */
@Slf4j
public final class ResourceUtil {

  @Generated
  private ResourceUtil() {
  }

  public static Resource[] getBpmnFiles(String resourceLocation) {
    return getResources(resourceLocation, "**/*.bpmn", "BPMN");
  }

  public static Resource[] getDmnFiles(String resourceLocation) {
    return getResources(resourceLocation, "**/*.dmn", "DMN");
  }

  /**
   * Returns every deployable resource (BPMN first, then DMN) found under the given location in a
   * single array, ready to be sent to Camunda's {@code /deployment/create} endpoint in one
   * multipart call. Camunda distinguishes BPMN from DMN by file extension, so both kinds can travel
   * together.
   */
  public static Resource[] getDeployableResources(String resourceLocation) {
    Resource[] bpmnFiles = getBpmnFiles(resourceLocation);
    Resource[] dmnFiles = getDmnFiles(resourceLocation);
    List<Resource> all = new ArrayList<>(bpmnFiles.length + dmnFiles.length);
    all.addAll(List.of(bpmnFiles));
    all.addAll(List.of(dmnFiles));
    return all.toArray(new Resource[0]);
  }

  private static Resource[] getResources(String resourceLocation, String filePattern, String kind) {
    String locationPattern = normalize(resourceLocation) + filePattern;
    try {
      return new PathMatchingResourcePatternResolver().getResources(locationPattern);
    } catch (FileNotFoundException e) {
      throw new IllegalStateException(
          "Resource location " + resourceLocation + " does not exist. Point "
              + "opentmf.bpmn-sync.resource-location to the folder that holds the deployable "
              + "BPMN/DMN files.", e);
    } catch (IOException e) {
      throw new IllegalArgumentException(
          kind + " files could not be resolved for pattern " + locationPattern, e);
    }
  }

  /**
   * Returns the deployment (multipart part) name of a resource: its path relative to the given
   * resource location, so that sub-folder structure under the location is preserved while the
   * location itself is not. Falls back to the plain filename when the resource does not reside
   * under the location.
   */
  @NonNull
  public static String getResourceNameWithFolder(@NonNull Resource r, String resourceLocation) {
    try {
      String uri = r.getURI().toString().replace('\\', '/'); // class path form
      String root = rootPath(resourceLocation);
      int idx = uri.lastIndexOf(root);
      if (idx >= 0) {
        return uri.substring(idx + root.length());
      }
      return Objects.requireNonNull(r.getFilename());
    } catch (IOException e) {
      log.warn("Could not extract resource name with folder for {}", r, e);
      return Objects.requireNonNullElse(r.getFilename(), "Resource");
    }
  }

  /**
   * Ensures the location ends with {@code /} so it can serve as the root of a search pattern.
   */
  private static String normalize(String resourceLocation) {
    Assert.hasText(resourceLocation, "opentmf.bpmn-sync.resource-location must not be empty.");
    return resourceLocation.endsWith("/") ? resourceLocation : resourceLocation + "/";
  }

  /**
   * Strips a possible {@code classpath:} / {@code file:} prefix and ensures leading and trailing
   * slashes, e.g. {@code classpath:bpmn/} becomes {@code /bpmn/} — the form in which the location
   * appears inside a resolved resource URI.
   */
  private static String rootPath(String resourceLocation) {
    String path = normalize(resourceLocation);
    int colon = path.indexOf(':');
    if (colon >= 0) {
      path = path.substring(colon + 1);
    }
    return path.startsWith("/") ? path : "/" + path;
  }
}
