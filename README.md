# Camunda7 BPMN Synchronization Service
This service synchronizes the BPMN files under classpath:bpmn/ folder with the configured Camunda server.

Depending on the value of auto-migrate, migrates the deployed BPMN's previous version's process instances to the newly deployed version.

## Auto Migration
BPMN Sync Service supports automatically migrating the process instances of the deployed BPMNs' previous version to the new version. To enable auto migration, set auto-migrate to true.

The potential failure of auto-migration, will not affect application startup.

The result of the auto-migration can be checked using Camunda Cockpit.

## How the Service works
- Obtains a db lock for lock type BPMN.
- Reads the latest synchronized BPMN version from db_lock_latest table.
- If the new BPMN version is the same as the latest synchonized version, then the service just releases the db lock and silently returns.
- If either a downgrade or an upgrade is necessary:
    - Deploys the BPMN files under src/main/resources/bpmn to Camunda in a single API call, using Camunda's /deployment/create endpoint.
    - Obtains the deployment result. If no BPMN files are deployed, logs a warning to the developer to not increase the BPMN version when no changes to the BPMNs are present, releases the db lock and returns.
    - If the auto-migrate is set to true, for each deployed BPMN file:
        - Retrieves the process definition for the previous version.
        - If no process definition exists with the previous version, skips that BPMN and continues the loop.
        - Retrieves the process instance count for the previous process definition.
        - If the instance count is zero, skips that BPMN and continues the loop.
        - Generates a migration plan for migrating the process instances from previous version to the newly deployed version.
        - Triggers the execution of the generated migration plan in an async manner, and continues the loop.

## Using the Service
To use this service from a microservice, the following six small steps are necessary:

### 1. pom.xml Additions
Import opentmf-versions for managing the opentmf library dependencies
```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.opentmf</groupId>
      <artifactId>opentmf-versions</artifactId>
      <version>LATEST</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```
Depend on the camunda7-bpmn-sync-service: 
```xml
  <dependency>
    <groupId>org.opentmf.camunda</groupId>
    <artifactId>camunda7-bpmn-sync-service</artifactId>
  </dependency>
```
### 2. Reorganize the BPMN files
1. In your microservice, the BPMN files must be under **src/main/resources/bpmn** folder. All *.bpmn files within this folder and its sub-folders will be used in the synchronization process.
    * Note: If your microservice uses embedded Camunda for its IT tests, you can benefit from this camunda configuration property: [camunda.deployment-resource-pattern](https://docs.camunda.org/manual/7.19/user-guide/spring-boot-integration/configuration/)
2. Ensure one BPMN is deployed by one microservice. Do not try to deploy the same BPMN in a different microservice.

### 3. Specify the BPMN Sync Properties
In your application.yaml, specify the BPMN Sync Properties:
```yaml
opentmf:
  bpmn-sync:
    enabled: true
    deployment-name: UC-SOA
    bpmn-version: 1.0
    auto-migrate: true
    client: default
```

**client:** The client id to use. This id is the prefix to the following exposed beans:

1. `${client}WebClient`
2. `${client}TokenService`
3. `${client}ClientProperties`

The BPMN Sync Service remembers the latest deployed BPMN versions. If the specified bpmnVersion is already the latest deployed version, then no synchronization will take place. Therefore, it is the developers' responsibility to increase the bpmn-version when any of the BPMN files changes, to enforce the BPMN synchronization.

### 4. Disable JDBC Repositories
JDBC template is used only to obtain the DB connections by the db lock service and the rest is performed by pure JDBC calls by the DB Lock service. However, Spring Boot does not know this beforehand and checks if JDBC repositories can also be used as the repository implementations. In order to let Spring Boot know that we don't want to use JDBC repositories, the following should be added to application.yml file:

```yaml
spring:
  data:
    jdbc:
      repositories:
        enabled: false
```

### 5. Skip BPMN Sync in IT Tests
In order to skip the BPMN Sync in the IT tests, first disable the BPMN Sync Service in your application-it.yml file:
```yaml
opentmf:
  db-lock:
    create-tables: false
  bpmn-sync:
    enabled: false
```

### 6. Expose `objectMapper` Bean
The BPMN Sync Service requires the `objectMapper` bean to serialize and deserialize the Camunda API exchanges. Therefore it is required to expose a bean of type `com.fasterxml.jackson.databind.ObjectMapper` with the bean name `objectMapper`.

## Sample Logs
Here are some sample log statements from a microservice's startup logs:

### Initial Deployment, Migration not necessary
```text
23:42.893 INFO  [main] o.o.b.s.s.i.BpmnSyncServiceImpl -- Starting BPMN Deployment for TestDeployment, version: v1
23:42.897 DEBUG [main] o.o.d.l.s.i.DbLockServiceImpl -- Attempting to acquire lock for lockType = BPMN, and lockVersion = v1
23:42.905 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK(lock_type, lock_version, hostname) values (('B'), ('v1'), ('gentoo.lan'))
RETURNING *
23:42.909 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: select lock_version, lock_acquired_on from DB_LOCK_LATEST where lock_type = ('B')
23:42.914 DEBUG [main] o.o.d.l.s.i.DbLockServiceImpl -- Acquired lock id = 1 for lockType = BPMN, and lockVersion = v1.
23:42.915 INFO  [main] o.o.b.s.s.i.BpmnSyncServiceImpl -- Will synchronize 1 BPMN files, for TestDeployment, bpmnVersion: v1
23:42.936 DEBUG [main] o.o.c.o.s.i.OpenidTokenClientImpl -- Will retrieve a new openid token from url: http://localhost:34637/oauth2/token, scope: [openid], username: [user]
23:45.146 INFO  [reactor-http-epoll-2] o.o.b.s.s.i.BpmnSyncServiceImpl -- BPMN deployment for TestDeployment, version v1 has been completed. Deployed BPMN count: 1
23:45.147 DEBUG [reactor-http-epoll-2] o.o.b.s.s.i.BpmnSyncServiceImpl -- Deployed BPMN Files and Their Versions follows:
23:45.147 DEBUG [reactor-http-epoll-2] o.o.b.s.s.i.BpmnSyncServiceImpl -- Version: 1, BPMN: SampleBpmn.bpmn
23:45.147 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: select count(*) from DB_LOCK_LATEST where lock_type = ('B')
23:45.149 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK_LATEST (lock_type, lock_version, hostname, lock_acquired_on) select lock_type, lock_version, hostname, created_on from DB_LOCK where id = ('1'::int4)
23:45.150 TRACE [main] o.o.d.l.s.i.DbLockServiceImpl -- Initialized LatestLock using lockId = 1
23:45.151 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK_HISTORY (lock_id, lock_type, lock_version, hostname, lock_acquired_on) select id, lock_type, lock_version, hostname, created_on from DB_LOCK where DB_LOCK.id = ('1'::int4)
23:45.152 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: delete from DB_LOCK where id = ('1'::int4) and lock_type = ('B')
23:45.153 INFO  [main] o.o.d.l.s.i.DbLockServiceImpl -- Released lock after 2 seconds. lockId = 1, and lockType = BPMN
23:45.154 TRACE [main] o.o.d.l.s.i.DbLockServiceImpl -- Cancelling auto lock release timer for lock id = 1, type = BPMN
23:45.154 INFO  [main] o.o.b.s.s.i.BpmnMigrationServiceImpl -- Skipping BPMN migration because auto-migrate is set to false.
```

### When all files are up-to-date
```text
23:45.163 INFO  [main] o.o.b.s.s.i.BpmnSyncServiceImpl -- Starting BPMN Deployment for TestDeployment, version: v1
23:45.164 DEBUG [main] o.o.d.l.s.i.DbLockServiceImpl -- Attempting to acquire lock for lockType = BPMN, and lockVersion = v1
23:45.164 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK(lock_type, lock_version, hostname) values (('B'), ('v1'), ('gentoo.lan'))
RETURNING *
23:45.165 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: select lock_version, lock_acquired_on from DB_LOCK_LATEST where lock_type = ('B')
23:45.167 DEBUG [main] o.o.d.l.s.i.DbLockServiceImpl -- Acquired lock id = 2 for lockType = BPMN, and lockVersion = v1.
23:45.168 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK_HISTORY (lock_id, lock_type, lock_version, hostname, lock_acquired_on) select id, lock_type, lock_version, hostname, created_on from DB_LOCK where DB_LOCK.id = ('2'::int4)
23:45.169 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: delete from DB_LOCK where id = ('2'::int4) and lock_type = ('B')
23:45.170 INFO  [main] o.o.d.l.s.i.DbLockServiceImpl -- Released lock after 0 seconds. lockId = 2, and lockType = BPMN
23:45.171 TRACE [main] o.o.d.l.s.i.DbLockServiceImpl -- Cancelling auto lock release timer for lock id = 2, type = BPMN
23:45.171 INFO  [main] o.o.b.s.s.i.BpmnSyncServiceImpl -- TestDeployment BPMN files are already up-to-date for version v1.
23:45.171 INFO  [main] o.o.b.s.s.i.BpmnMigrationServiceImpl -- Skipping BPMN migration because auto-migrate is set to false.
```

### When all files are up-to-date but version is increased
```text
23:45.173 INFO  [main] o.o.b.s.s.i.BpmnSyncServiceImpl -- Starting BPMN Deployment for TestDeployment, version: v1.1
23:45.174 DEBUG [main] o.o.d.l.s.i.DbLockServiceImpl -- Attempting to acquire lock for lockType = BPMN, and lockVersion = v1.1
23:45.174 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK(lock_type, lock_version, hostname) values (('B'), ('v1.1'), ('gentoo.lan'))
RETURNING *
23:45.175 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: select lock_version, lock_acquired_on from DB_LOCK_LATEST where lock_type = ('B')
23:45.177 DEBUG [main] o.o.d.l.s.i.DbLockServiceImpl -- Acquired lock id = 3 for lockType = BPMN, and lockVersion = v1.1.
23:45.177 INFO  [main] o.o.b.s.s.i.BpmnSyncServiceImpl -- Will synchronize 1 BPMN files, for TestDeployment, bpmnVersion: v1.1
23:45.181 TRACE [main] o.o.c.o.s.i.OpenidTokenServiceImpl -- Returning cached openid token for baseUrl: http://localhost:34637/oauth2/token, scope: openid, username: user
23:45.208 WARN  [reactor-http-epoll-2] o.o.b.s.s.i.BpmnSyncServiceImpl -- TestDeployment BPMN synchronization completed without deploying any BPMN. The specified bpmnVersion was: v1.1. Hint: Do not change the bpmnVersion when there are no BPMN changes.
23:45.208 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK_HISTORY (lock_id, lock_type, lock_version, hostname, lock_acquired_on) select id, lock_type, lock_version, hostname, created_on from DB_LOCK where DB_LOCK.id = ('3'::int4)
23:45.210 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: delete from DB_LOCK where id = ('3'::int4) and lock_type = ('B')
23:45.211 INFO  [main] o.o.d.l.s.i.DbLockServiceImpl -- Released lock after 0 seconds. lockId = 3, and lockType = BPMN
23:45.211 TRACE [main] o.o.d.l.s.i.DbLockServiceImpl -- Cancelling auto lock release timer for lock id = 3, type = BPMN
23:45.211 INFO  [main] o.o.b.s.s.i.BpmnMigrationServiceImpl -- Auto-migration not necessary because no new BPMN has been deployed.
```

### When a single BPMN File Is Changed, auto-migrate: true, no previous version process instances exist
```text
23:45.213 INFO  [main] o.o.b.s.s.i.BpmnSyncServiceImpl -- Starting BPMN Deployment for TestDeployment, version: v2
23:45.213 DEBUG [main] o.o.d.l.s.i.DbLockServiceImpl -- Attempting to acquire lock for lockType = BPMN, and lockVersion = v2
23:45.214 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK(lock_type, lock_version, hostname) values (('B'), ('v2'), ('gentoo.lan'))
RETURNING *
23:45.215 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: select lock_version, lock_acquired_on from DB_LOCK_LATEST where lock_type = ('B')
23:45.216 DEBUG [main] o.o.d.l.s.i.DbLockServiceImpl -- Acquired lock id = 4 for lockType = BPMN, and lockVersion = v2.
23:45.216 INFO  [main] o.o.b.s.s.i.BpmnSyncServiceImpl -- Will synchronize 1 BPMN files, for TestDeployment, bpmnVersion: v2
23:45.217 TRACE [main] o.o.c.o.s.i.OpenidTokenServiceImpl -- Returning cached openid token for baseUrl: http://localhost:34637/oauth2/token, scope: openid, username: user
23:45.273 INFO  [reactor-http-epoll-2] o.o.b.s.s.i.BpmnSyncServiceImpl -- BPMN deployment for TestDeployment, version v2 has been completed. Deployed BPMN count: 1
23:45.273 DEBUG [reactor-http-epoll-2] o.o.b.s.s.i.BpmnSyncServiceImpl -- Deployed BPMN Files and Their Versions follows:
23:45.273 DEBUG [reactor-http-epoll-2] o.o.b.s.s.i.BpmnSyncServiceImpl -- Version: 2, BPMN: SampleBpmn.bpmn
23:45.274 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: select count(*) from DB_LOCK_LATEST where lock_type = ('B')
23:45.275 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: update DB_LOCK_LATEST T set lock_version = L.lock_version, hostname = L.hostname, lock_acquired_on = L.created_on from DB_LOCK L where L.id = ('4'::int4) and T.lock_type = L.lock_type
23:45.276 TRACE [main] o.o.d.l.s.i.DbLockServiceImpl -- Updated LatestLock using lockId = 4
23:45.277 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK_HISTORY (lock_id, lock_type, lock_version, hostname, lock_acquired_on) select id, lock_type, lock_version, hostname, created_on from DB_LOCK where DB_LOCK.id = ('4'::int4)
23:45.277 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: delete from DB_LOCK where id = ('4'::int4) and lock_type = ('B')
23:45.278 INFO  [main] o.o.d.l.s.i.DbLockServiceImpl -- Released lock after 0 seconds. lockId = 4, and lockType = BPMN
23:45.278 TRACE [main] o.o.d.l.s.i.DbLockServiceImpl -- Cancelling auto lock release timer for lock id = 4, type = BPMN
23:45.279 INFO  [main] o.o.b.s.s.i.BpmnMigrationServiceImpl -- Starting BPMN migration for 1 deployed BPMNs.
23:45.286 TRACE [main] o.o.c.o.s.i.OpenidTokenServiceImpl -- Returning cached openid token for baseUrl: http://localhost:34637/oauth2/token, scope: openid, username: user
23:45.619 DEBUG [reactor-http-epoll-2] o.o.b.s.s.i.BpmnMigrationServiceImpl -- Previous version's process definition id: Sample_BPMN:1:b4d4d6e0-1816-11f0-a4d9-1a5d066173a7
23:45.619 TRACE [reactor-http-epoll-2] o.o.b.s.s.i.BpmnMigrationServiceImpl -- Getting process instance count for process definition id: Sample_BPMN:1:b4d4d6e0-1816-11f0-a4d9-1a5d066173a7
23:45.621 TRACE [reactor-http-epoll-2] o.o.c.o.s.i.OpenidTokenServiceImpl -- Returning cached openid token for baseUrl: http://localhost:34637/oauth2/token, scope: openid, username: user
23:45.670 DEBUG [reactor-http-epoll-2] o.o.b.s.s.i.BpmnMigrationServiceImpl -- Migration not necessary for Sample_BPMN version 1 to 2 because no process instances exist.
23:45.671 INFO  [main] o.o.b.s.s.i.BpmnMigrationServiceImpl -- BPMN Migration completed without creating any async migration jobs.
```

### When a single BPMN File Is Changed, auto-migrate: true, migration jobs are created
```text
23:45.713 DEBUG [main] o.o.b.t.SampleTask -- SampleTask started and stopped.
23:45.847 TRACE [awaitility-thread] o.o.c.o.s.i.OpenidTokenServiceImpl -- Returning cached openid token for baseUrl: http://localhost:34637/oauth2/token, scope: openid, username: user
23:45.861 INFO  [main] o.o.b.s.s.i.BpmnSyncServiceImpl -- Starting BPMN Deployment for TestDeployment, version: v3
23:45.861 DEBUG [main] o.o.d.l.s.i.DbLockServiceImpl -- Attempting to acquire lock for lockType = BPMN, and lockVersion = v3
23:45.862 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK(lock_type, lock_version, hostname) values (('B'), ('v3'), ('gentoo.lan'))
RETURNING *
23:45.863 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: select lock_version, lock_acquired_on from DB_LOCK_LATEST where lock_type = ('B')
23:45.864 DEBUG [main] o.o.d.l.s.i.DbLockServiceImpl -- Acquired lock id = 5 for lockType = BPMN, and lockVersion = v3.
23:45.864 INFO  [main] o.o.b.s.s.i.BpmnSyncServiceImpl -- Will synchronize 2 BPMN files, for TestDeployment, bpmnVersion: v3
23:45.865 TRACE [main] o.o.c.o.s.i.OpenidTokenServiceImpl -- Returning cached openid token for baseUrl: http://localhost:34637/oauth2/token, scope: openid, username: user
23:45.935 INFO  [reactor-http-epoll-2] o.o.b.s.s.i.BpmnSyncServiceImpl -- BPMN deployment for TestDeployment, version v3 has been completed. Deployed BPMN count: 2
23:45.936 DEBUG [reactor-http-epoll-2] o.o.b.s.s.i.BpmnSyncServiceImpl -- Deployed BPMN Files and Their Versions follows:
23:45.936 DEBUG [reactor-http-epoll-2] o.o.b.s.s.i.BpmnSyncServiceImpl -- Version: 1, BPMN: DummyBpmn.bpmn
23:45.936 DEBUG [reactor-http-epoll-2] o.o.b.s.s.i.BpmnSyncServiceImpl -- Version: 3, BPMN: SampleBpmn.bpmn
23:45.936 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: select count(*) from DB_LOCK_LATEST where lock_type = ('B')
23:45.937 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: update DB_LOCK_LATEST T set lock_version = L.lock_version, hostname = L.hostname, lock_acquired_on = L.created_on from DB_LOCK L where L.id = ('5'::int4) and T.lock_type = L.lock_type
23:45.938 TRACE [main] o.o.d.l.s.i.DbLockServiceImpl -- Updated LatestLock using lockId = 5
23:45.939 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: insert into DB_LOCK_HISTORY (lock_id, lock_type, lock_version, hostname, lock_acquired_on) select id, lock_type, lock_version, hostname, created_on from DB_LOCK where DB_LOCK.id = ('5'::int4)
23:45.940 TRACE [main] o.o.d.l.u.JdbcHelper -- Executing SQL: delete from DB_LOCK where id = ('5'::int4) and lock_type = ('B')
23:45.941 INFO  [main] o.o.d.l.s.i.DbLockServiceImpl -- Released lock after 0 seconds. lockId = 5, and lockType = BPMN
23:45.942 TRACE [main] o.o.d.l.s.i.DbLockServiceImpl -- Cancelling auto lock release timer for lock id = 5, type = BPMN
23:45.942 INFO  [main] o.o.b.s.s.i.BpmnMigrationServiceImpl -- Starting BPMN migration for 2 deployed BPMNs.
23:45.942 DEBUG [main] o.o.b.s.s.i.BpmnMigrationServiceImpl -- Skipping migration of Dummy_BPMN from version 0 to 1, because no previous process definition exists.
23:45.943 TRACE [main] o.o.c.o.s.i.OpenidTokenServiceImpl -- Returning cached openid token for baseUrl: http://localhost:34637/oauth2/token, scope: openid, username: user
23:45.975 DEBUG [reactor-http-epoll-2] o.o.b.s.s.i.BpmnMigrationServiceImpl -- Previous version's process definition id: Sample_BPMN:2:b4f1fbd3-1816-11f0-a4d9-1a5d066173a7
23:45.975 TRACE [reactor-http-epoll-2] o.o.b.s.s.i.BpmnMigrationServiceImpl -- Getting process instance count for process definition id: Sample_BPMN:2:b4f1fbd3-1816-11f0-a4d9-1a5d066173a7
23:45.977 TRACE [reactor-http-epoll-2] o.o.c.o.s.i.OpenidTokenServiceImpl -- Returning cached openid token for baseUrl: http://localhost:34637/oauth2/token, scope: openid, username: user
23:45.992 TRACE [reactor-http-epoll-2] o.o.b.s.s.i.BpmnMigrationServiceImpl -- There are 1 process instances for Sample_BPMN version 2
23:45.992 TRACE [reactor-http-epoll-2] o.o.b.s.s.i.BpmnMigrationServiceImpl -- Generating migration plan for migrating Sample_BPMN process instances from version 2 to 3
23:45.993 TRACE [reactor-http-epoll-2] o.o.c.o.s.i.OpenidTokenServiceImpl -- Returning cached openid token for baseUrl: http://localhost:34637/oauth2/token, scope: openid, username: user
23:46.058 DEBUG [reactor-http-epoll-2] o.o.b.s.s.i.BpmnMigrationServiceImpl -- Executing migration async for migrating Sample_BPMN process instances from version 2 to 3
23:46.060 TRACE [reactor-http-epoll-2] o.o.c.o.s.i.OpenidTokenServiceImpl -- Returning cached openid token for baseUrl: http://localhost:34637/oauth2/token, scope: openid, username: user
23:46.184 DEBUG [reactor-http-epoll-2] o.o.b.s.s.i.BpmnMigrationServiceImpl -- Migration Async Execution initiated for Sample_BPMN version 2 to 3. Details: ExecuteMigrationPlanAsyncResponse(id=b57b0201-1816-11f0-a4d9-1a5d066173a7, type=instance-migration, totalJobs=1, jobsCreated=0, batchJobsPerSeed=100, invocationsPerBatchJob=1, seedJobDefinitionId=b57b9e42-1816-11f0-a4d9-1a5d066173a7, monitorJobDefinitionId=b57b9e43-1816-11f0-a4d9-1a5d066173a7, batchJobDefinitionId=b57b9e44-1816-11f0-a4d9-1a5d066173a7, suspended=false, tenantId=null, createUserId=null, startTime=2025-04-13T06:23:46.116+0300, executionStartTime=null)
23:46.190 INFO  [main] o.o.b.s.s.i.BpmnMigrationServiceImpl -- BPMN Migration completed, total async jobs created: 1. Use Camunda Cockpit for checking job completions.
23:46.293 TRACE [awaitility-thread] o.o.c.o.s.i.OpenidTokenServiceImpl -- Returning cached openid token for baseUrl: http://localhost:34637/oauth2/token, scope: openid, username: user
```

## Version History
### 1.0.0
- Initial Release
### 1.0.1
- Simplifies configuration properties
### 1.0.2
- Updates dependency versions of pia-web-clients and pia-db-lock-service to their latest.
### 1.0.3
- Updates dependency versions of pia-web-clients and pia-db-lock-service to their latest.
### 1.0.4
- Updates dependency versions of pia-web-clients and pia-db-lock-service to their latest.
### 1.0.5
- Updates dependency version pia-db-lock-service to the backward incompatible 1.0.5
- Fix: Skips migration if deployed BPMN is initial.
### 1.0.6
- Updates pia-db-lock-service version to 1.0.6
- Updates Spring Boot version to 3.4.0
### 1.0.7
- Updates pia-db-lock-service version to 1.0.7
- Fixed autoconfiguration conditionals.
### 1.0.8
- Fixed autoconfiguration conditionals.
### 1.0.9
- Updates to pia-web-clients 1.0.8, for fewer dependencies for the reactive WebClient.
### 1.1.0
- Added Web Client Starters to the autoconfiguration afterName.
- Added objectMapper bean as a dependency to the autoconfiguration
### 1.1.1
- Initial open source release, replacing pia with camunda7