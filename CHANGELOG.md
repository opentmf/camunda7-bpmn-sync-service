# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- Upgraded the parent to Spring Boot 4.1.0 (from 4.0.4).
- Bumped opentmf-commons 2.1.0 → 2.2.0, opentmf-db-lock-service 2.0.0 → 2.2.0,
  opentmf-http-clients 2.1.0 → 2.1.3, opentmf-mockserver 2.1.2 → 2.1.4,
  ArchUnit 1.4.1 → 1.4.2 and JaCoCo 0.8.14 → 0.8.15.
- Declared an explicit `maven-compiler-plugin` `annotationProcessorPaths` (Lombok +
  spring-boot-configuration-processor). Spring Boot 4.1 manages maven-compiler-plugin 3.15.0,
  which no longer auto-discovers annotation processors from the compile classpath; without this
  the Lombok-generated members no longer compile.

## [2.0.1] - 2026-03-25

### Changed
- Upgraded to opentmf-http-clients 2.1.0.
- Renamed `RestTemplateUtil` usage to `SyncClientUtil` following the upstream rename in opentmf-http-clients.

## [2.0.0] - 2026-03-25

### Changed
- Upgraded to Spring Boot 4.0.4.
- Started using the new `opentmf-http-clients:2.0.0` module.
- Added synchronous implementation using Spring's `RestClient`, provided by the opentmf-http-clients.
- Upgraded to opentmf-db-lock-service 2.0.0, opentmf-commons 2.1.0.

## [1.1.3]

### Fixed
- Fixes the resource name finding logic when the BPMN resides within a jar.

## [1.1.1]

### Changed
- Initial open source release, replacing pia with camunda7.

## [1.1.0]

### Added
- Web Client Starters to the autoconfiguration afterName.
- objectMapper bean as a dependency to the autoconfiguration.

## [1.0.9]

### Changed
- Updated to pia-web-clients 1.0.8, for fewer dependencies for the reactive WebClient.

## [1.0.8]

### Fixed
- Autoconfiguration conditionals.

## [1.0.7]

### Fixed
- Autoconfiguration conditionals.

### Changed
- Updated pia-db-lock-service version to 1.0.7.

## [1.0.6]

### Changed
- Updated pia-db-lock-service version to 1.0.6.
- Updated Spring Boot version to 3.4.0.

## [1.0.5]

### Changed
- Updated dependency version pia-db-lock-service to the backward incompatible 1.0.5.

### Fixed
- Skips migration if deployed BPMN is initial.

## [1.0.4]

### Changed
- Updated dependency versions of pia-web-clients and pia-db-lock-service to their latest.

## [1.0.3]

### Changed
- Updated dependency versions of pia-web-clients and pia-db-lock-service to their latest.

## [1.0.2]

### Changed
- Updated dependency versions of pia-web-clients and pia-db-lock-service to their latest.

## [1.0.1]

### Changed
- Simplifies configuration properties.

## [1.0.0]

### Added
- Initial release.

[Unreleased]: https://github.com/opentmf/camunda7-bpmn-sync-service/compare/v2.0.1...HEAD
[2.0.1]: https://github.com/opentmf/camunda7-bpmn-sync-service/compare/v2.0.0...v2.0.1
[2.0.0]: https://github.com/opentmf/camunda7-bpmn-sync-service/compare/v1.1.3...v2.0.0
[1.1.3]: https://github.com/opentmf/camunda7-bpmn-sync-service/compare/v1.1.1...v1.1.3
[1.1.1]: https://github.com/opentmf/camunda7-bpmn-sync-service/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/opentmf/camunda7-bpmn-sync-service/compare/v1.0.9...v1.1.0
[1.0.9]: https://github.com/opentmf/camunda7-bpmn-sync-service/compare/v1.0.8...v1.0.9
[1.0.8]: https://github.com/opentmf/camunda7-bpmn-sync-service/compare/v1.0.7...v1.0.8
[1.0.7]: https://github.com/opentmf/camunda7-bpmn-sync-service/compare/v1.0.6...v1.0.7
[1.0.6]: https://github.com/opentmf/camunda7-bpmn-sync-service/compare/v1.0.5...v1.0.6
[1.0.5]: https://github.com/opentmf/camunda7-bpmn-sync-service/compare/v1.0.4...v1.0.5
[1.0.4]: https://github.com/opentmf/camunda7-bpmn-sync-service/compare/v1.0.3...v1.0.4
[1.0.3]: https://github.com/opentmf/camunda7-bpmn-sync-service/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/opentmf/camunda7-bpmn-sync-service/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/opentmf/camunda7-bpmn-sync-service/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/opentmf/camunda7-bpmn-sync-service/releases/tag/v1.0.0

