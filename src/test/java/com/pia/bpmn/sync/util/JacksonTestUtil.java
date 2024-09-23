package com.pia.bpmn.sync.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.Generated;

/**
 * @author Gökhan Demir
 */
public final class JacksonTestUtil {

  private static final JavaTimeModule JAVA_TIME_MODULE = new JavaTimeModule();
  private static final ObjectMapper OBJECT_MAPPER;

  static {
    JAVA_TIME_MODULE.addDeserializer(OffsetDateTime.class, new DelegatingDateTimeDeserializer());

    OBJECT_MAPPER =
        new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(JAVA_TIME_MODULE);
  }

  @Generated
  private JacksonTestUtil() {}

  public static <T> T jsonToObject(String json, Class<T> clazz) {
    try {
      return OBJECT_MAPPER.readValue(json, clazz);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static <T> String objectToJson(T object) {
    try {
      return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Retrieves the contents of the text file from the classpath, and then deserialize the read value
   * to the requested class.
   *
   * @param jsonFileNameInClassPath the name of the text file in the class-path with valid json
   *     content.
   * @param clazz Requested class type to deserialize the json into.
   * @param <T> The object to be returned.
   * @return The deserialized class instance.
   */
  public static <T> T fileToObject(String jsonFileNameInClassPath, Class<T> clazz) {
    try {
      return OBJECT_MAPPER.readValue(inputStream(jsonFileNameInClassPath), clazz);
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Locate the text file in the classpath, read and return its contents as a UTF_8 string.
   *
   * @param textFileNameInClassPath the name of the text file in the class path.
   * @return the contents of the requested file.
   */
  public static String contents(String textFileNameInClassPath) {
    return contents(inputStream(textFileNameInClassPath));
  }

  /**
   * Assuming the input stream points to a text file, read and return its contents as a UTF_8
   * string.
   *
   * @param inputStream Input stream of a text file.
   * @return the contents of the requested input stream.
   */
  public static String contents(InputStream inputStream) {
    try {
      ByteArrayOutputStream result = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      for (int length; (length = inputStream.read(buffer)) != -1; ) {
        result.write(buffer, 0, length);
      }
      return result.toString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static InputStream inputStream(String textFileNameInClassPath) {
    return ClassLoader.getSystemResourceAsStream(textFileNameInClassPath);
  }

  static class DelegatingDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {

    @Override
    public OffsetDateTime deserialize(JsonParser p, DeserializationContext context)
        throws IOException {
      try {
        return InstantDeserializer.OFFSET_DATE_TIME.deserialize(p, context);
      } catch (IOException e) {
        return LocalDateTimeDeserializer.INSTANCE.deserialize(p, context).atOffset(ZoneOffset.UTC);
      }
    }
  }
}
