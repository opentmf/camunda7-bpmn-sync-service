package org.opentmf.bpmn.sync.util;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import lombok.Generated;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.lang.NonNull;

/**
 * @author Gokhan Demir
 */
public final class ResourceUtil {

  @Generated
  private ResourceUtil() {
  }

  public static Resource[] getBpmnFiles() {
    try {
      return new PathMatchingResourcePatternResolver().getResources("classpath:bpmn/**/*.bpmn");
    } catch (IOException e) {
      throw new IllegalArgumentException("BPMN Files not found under bpmn/* on the classpath.", e);
    }
  }

  @NonNull
  public static String getName(Resource resource) {
    try {
      String path = resource.getFile().getPath();
      String bpmn = "bpmn" + File.separator;
      int i = path.lastIndexOf(bpmn);
      return i >= 0
          ? path.substring(i + bpmn.length())
          : Objects.requireNonNull(resource.getFilename());
    } catch (IOException ignored) {
      return "Resource";
    }
  }
}
