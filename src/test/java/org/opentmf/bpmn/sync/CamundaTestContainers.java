package org.opentmf.bpmn.sync;

import java.time.Duration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Creates and starts a fresh set of Docker containers (PostgreSQL, MockServer, Camunda 7)
 * on an isolated network. Each instance is fully independent.
 */
public class CamundaTestContainers {

  final Network network;
  final PostgreSQLContainer postgres;
  final GenericContainer<?> mockServer;
  final GenericContainer<?> camunda;

  @SuppressWarnings("resource")
  public CamundaTestContainers() {
    network = Network.newNetwork();

    postgres = new PostgreSQLContainer("postgres:18.3-alpine")
        .withNetwork(network)
        .withNetworkAliases("postgresql")
        .withDatabaseName("db")
        .withUsername("test")
        .withPassword("test")
        .withInitScript("init-camunda-schema.sql");

    mockServer = new GenericContainer<>("ghcr.io/opentmf/opentmf-mockserver:2.1.2")
        .withNetwork(network)
        .withNetworkAliases("mockserver")
        .withExposedPorts(1080)
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("keycloak-docker-config.json"),
            "/config/keycloak-mock.json")
        .waitingFor(Wait.forHttp("/mockserver/status")
            .withMethod("PUT")
            .forStatusCode(200)
            .withStartupTimeout(Duration.ofSeconds(30)));

    camunda = new GenericContainer<>("ghcr.io/opentmf/opentmf-camunda7:24.0.3")
        .withNetwork(network)
        .withExposedPorts(8080)
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("camunda-docker-mockauth.yml"),
            "/application/application-mockauth.yml")
        .withEnv("SPRING_PROFILES_ACTIVE", "mockauth")
        .withEnv("SPRING_DATASOURCE_URL",
            "jdbc:postgresql://postgresql:5432/db?currentSchema=camunda7")
        .withEnv("SPRING_DATASOURCE_USERNAME", "camunda7")
        .withEnv("SPRING_DATASOURCE_PASSWORD", "camunda7_pwd")
        .withEnv("SPRING_DATASOURCE_HIKARI_SCHEMA", "camunda7")
        .withEnv("CAMUNDA_BPM_AUTO_DEPLOYMENT_ENABLED", "false")
        .withEnv("OPENTMF_SECURITY_USER_CLAIM", "email")
        .withEnv("OPENTMF_SECURITY_AUTHORITIES_CLAIM", "realm_access.roles")
        .withEnv("PLUGIN_IDENTITY_KEYCLOAK_KEYCLOAK_ISSUER_URL",
            "http://mockserver:1080/realms/opentmf")
        .withEnv("PLUGIN_IDENTITY_KEYCLOAK_KEYCLOAK_ADMIN_URL",
            "http://mockserver:1080/admin/realms/opentmf")
        .withEnv("PLUGIN_IDENTITY_KEYCLOAK_CLIENT_ID", "client1")
        .withEnv("PLUGIN_IDENTITY_KEYCLOAK_CLIENT_SECRET", "client1Secret")
        .dependsOn(postgres, mockServer)
        .waitingFor(Wait.forLogMessage(".*Started.*", 1)
            .withStartupTimeout(Duration.ofSeconds(120)));

    postgres.start();
    mockServer.start();
    camunda.start();
  }

  public String camundaRestUrl() {
    return "http://localhost:" + camunda.getMappedPort(8080) + "/camunda/v7/engine-rest";
  }

  public String mockServerTokenUrl() {
    return "http://localhost:" + mockServer.getMappedPort(1080)
        + "/realms/opentmf/protocol/openid-connect/token";
  }

  public void registerProperties(DynamicPropertyRegistry r, String clientRef) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("camunda.bpm.client.base-url", this::camundaRestUrl);
    r.add("opentmf.bpmn-sync.client-ref", () -> clientRef);
    r.add("opentmf.http-clients.reactive.bearer-auth.token-url", this::mockServerTokenUrl);
    r.add("opentmf.http-clients.rest.bearer-auth.token-url", this::mockServerTokenUrl);
  }
}
