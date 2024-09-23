package com.pia.bpmn.sync;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import com.pia.bpmn.sync.util.JacksonTestUtil;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.MatchType;
import org.mockserver.matchers.Times;
import org.mockserver.model.JsonBody;
import org.springframework.http.HttpStatus;

/**
 * @author Gokhan Demir
 */
@Slf4j
public class MockServer {

  private final ClientAndServer clientAndServer = new ClientAndServer();
  private final String baseUrl = "http://localhost:" + clientAndServer.getPort();

  private static final JsonBody ACCESS_TOKEN = new JsonBody(JacksonTestUtil.contents("json/sh_access_token.json"));

  public MockServer() {
    log.debug("New instance created.");
    expectOpenidToken();
  }

  public String getBaseUrl() {
    System.out.println("Getting baseUrl as " + baseUrl);
    return baseUrl;
  }

  private void expectOpenidToken() {
    clientAndServer
        .when(request().withMethod("POST").withPath("/oauth2/token"), Times.exactly(1))
        .respond(response().withBody(ACCESS_TOKEN).withStatusCode(HttpStatus.OK.value()));
  }

  public void expectPost(String path, String requestBody, HttpStatus status, String responseBody) {
    clientAndServer
        .when(request()
            .withMethod("POST").withPath(path)
            .withBody(new JsonBody(requestBody, MatchType.ONLY_MATCHING_FIELDS)), Times.once())
        .respond(response().withBody(new JsonBody(responseBody)).withStatusCode(status.value()));
  }

  public void expectGet(String path, HttpStatus status) {
    clientAndServer
        .when(request().withMethod("GET").withPath(path), Times.once())
        .respond(response().withStatusCode(status.value()));
  }

  public void expectGet(String path, HttpStatus status, String responseBody) {
    clientAndServer
        .when(request().withMethod("GET").withPath(path), Times.once())
        .respond(response().withBody(new JsonBody(responseBody)).withStatusCode(status.value()));
  }

  public void expectGet(String path, int count, HttpStatus status, String responseBody) {
    clientAndServer
        .when(request().withMethod("GET").withPath(path), Times.exactly(count))
        .respond(response().withBody(new JsonBody(responseBody)).withStatusCode(status.value()));
  }
}
