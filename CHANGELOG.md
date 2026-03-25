# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

