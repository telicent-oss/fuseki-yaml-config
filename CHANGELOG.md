# Fuseki YAML Config Parser

## 2.0.1

- Build and Test Improvements:
    - Upgraded Apache Jena to 5.5.0
    - Upgraded Fuseki Kafka to 2.0.2
    - Upgraded Smart Caches Core to 0.29.2
    - Upgraded various build and test dependencies to latest available

## 2.0.0

- **BREAKING** Moved code to package `io.telicent.jena.fuseki.config.yaml`
- New `dlq-topic` property on `connectors` for configuring DLQ topic for a connector
- `topic` property on `connectors` may now be either a string, or a list, to allow configure multiple topics for a
  connector
- Build and Test Improvements:
    - Upgraded Apache Jena to 5.4.0
    - Upgraded Fuseki Kafka to 2.0.0
    - Upgraded Log4j to 2.24.3
    - Upgraded RDF ABAC to 1.0.2
    - Upgraded Smart Caches Core to 0.29.1
    - Upgraded Snake YAML to 2.4
    - Upgraded various build and test dependencies to latest available
    - Refactored Kafka test containers tests to use KafkaTestCluster from Smart Caches Core

## 1.0.7
- Don't generate groupId if it's empty

## 1.0.6
- Fix ctxName and ctxValue typo to cxt

## 1.0.5
- Add missing server context generation

## 1.0.4
- Change the unregistered operation error to a warning

## 1.0.3
- Update connector-service mismatch checks

## 1.0.2
- Add fk:configFile field to connectors

## 1.0.1
- Minor improvements to build

## 1.0.0
- First release