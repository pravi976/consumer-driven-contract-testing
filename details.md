# Consumer Driven Contract Testing - File Responsibilities

This project is now a **single parent Gradle project** with multiple modules kept inside the same parent folder. It is not split into separate sibling applications.

Parent location:

```text
C:\Users\pravi\spring-services\consumer-driven-contract-testing
```

## Module Layout

| Module | Responsibility |
|---|---|
| `cdc-pact-core` | Reusable CDC automation engine: annotations, scanner, models, sample generation, Pact JSON generation, and Pact writer. |
| `cdc-pact-spring-boot-starter` | Spring Boot auto-configuration module that makes the core CDC automation beans available to consumer applications. |
| `cdc-pact-test-support` | Shared JUnit/test helper module for validating generated consumer Pact files. |
| `cdc-pact-provider-test-support` | Provider-side Pact verification support module with `@CdcPactProviderVerification` and `AbstractCdcProviderVerificationTest`. |
| `cdc-pact-broker` | Broker integration placeholder module for publishing generated contracts to a Pact Broker in a future hardening step. |
| `cdc-pact-sample-app` | Sample Spring Boot consumer application that declares expectations and tests generated REST/JMS contracts. |

## Root Files

| File | Responsibility |
|---|---|
| `settings.gradle` | Declares the parent project name and includes all child modules under the same parent folder. |
| `build.gradle` | Defines shared Gradle configuration for all modules: group, version, Java 17 toolchain, Spring dependency management, UTF-8 compilation, and JUnit settings. |
| `gradle.properties` | Controls Gradle runtime behavior such as daemon and worker settings. |
| `gradlew` | Gradle wrapper launcher for Unix/Linux/macOS. |
| `gradlew.bat` | Gradle wrapper launcher for Windows. Use `./gradlew.bat test` from the parent folder. |
| `gradle/wrapper/gradle-wrapper.jar` | Gradle wrapper runtime jar. |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle wrapper version/distribution configuration. |
| `README.md` | Main usage guide for the parent project and CDC workflow. |
| `details.md` | This file. Explains every source/config file and its responsibility. |

## `cdc-pact-core`

| File | Responsibility |
|---|---|
| `cdc-pact-core/build.gradle` | Builds the reusable core library. Exposes Spring context and Jackson APIs needed by the scanner/generator. |
| `cdc-pact-core/src/main/java/com/fedex/cdc/automation/annotations/ConsumerPactClient.java` | Class-level annotation that declares the consumer/provider pair for a consumer-owned expectation class. |
| `cdc-pact-core/src/main/java/com/fedex/cdc/automation/annotations/ConsumerPactInteraction.java` | Method-level annotation for REST consumer expectations, including method, path, query, headers, request body, response body, provider state, and status. |
| `cdc-pact-core/src/main/java/com/fedex/cdc/automation/annotations/ConsumerPactMessageInteraction.java` | Method-level annotation for JMS/message expectations, including destination, metadata, provider state, and message payload type. |
| `cdc-pact-core/src/main/java/com/fedex/cdc/automation/annotations/PactExample.java` | DTO field/record-component annotation used to provide stable sample values for generated contract payloads. |
| `cdc-pact-core/src/main/java/com/fedex/cdc/automation/annotations/PactSecret.java` | DTO field/record-component annotation used to mask sensitive values in generated Pact files. |
| `cdc-pact-core/src/main/java/com/fedex/cdc/automation/model/ConsumerInteractionDefinition.java` | Normalized model for a scanned REST expectation. The writer uses this instead of reading annotations directly. |
| `cdc-pact-core/src/main/java/com/fedex/cdc/automation/model/ConsumerMessageDefinition.java` | Normalized model for a scanned JMS/message expectation. |
| `cdc-pact-core/src/main/java/com/fedex/cdc/automation/generator/PactGenerationFilter.java` | Optional provider/type filter used for targeted generation without deleting expectation classes. |
| `cdc-pact-core/src/main/java/com/fedex/cdc/automation/generator/ConsumerDrivenPactGenerator.java` | Orchestrates the scan-filter-write flow and returns generated Pact file paths. |
| `cdc-pact-core/src/main/java/com/fedex/cdc/automation/scanner/ConsumerPactScanner.java` | Scans Spring beans annotated with `@ConsumerPactClient` and finds REST/JMS expectation methods. |
| `cdc-pact-core/src/main/java/com/fedex/cdc/automation/support/PactSampleFactory.java` | Creates deterministic sample payloads from DTOs, records, nested objects, lists, maps, UUIDs, dates, numbers, `@PactExample`, and `@PactSecret`. |
| `cdc-pact-core/src/main/java/com/fedex/cdc/automation/writer/ConsumerDrivenPactWriter.java` | Writes Pact-compatible REST and JMS/message JSON files with metadata, interactions/messages, provider states, bodies, and matching rules. |

## `cdc-pact-spring-boot-starter`

| File | Responsibility |
|---|---|
| `cdc-pact-spring-boot-starter/build.gradle` | Builds the Spring Boot starter and exposes `cdc-pact-core` to applications that depend on the starter. |
| `cdc-pact-spring-boot-starter/src/main/java/com/fedex/cdc/boot/CdcPactAutoConfiguration.java` | Auto-configuration class that component-scans `com.fedex.cdc.automation` so core scanner/generator/writer beans are registered automatically. |
| `cdc-pact-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | Spring Boot 3 auto-configuration registration file for `CdcPactAutoConfiguration`. |

## `cdc-pact-test-support`

| File | Responsibility |
|---|---|
| `cdc-pact-test-support/build.gradle` | Builds shared test helper utilities and depends on JUnit and Jackson. |
| `cdc-pact-test-support/src/main/java/com/fedex/cdc/testsupport/AbstractConsumerContractGenerationTest.java` | Reusable base JUnit/Spring Boot test that consumer apps can extend to generate Pact files without writing custom generator logic. Defaults to `build/pacts` and scans all expectations; teams can override output directory or filter when needed. |
| `cdc-pact-test-support/src/main/java/com/fedex/cdc/testsupport/GeneratedPactAssertions.java` | Reusable assertions for generated Pact files: checks file existence, consumer/provider names, Pact spec version, interaction/message count, matching rules, masking, and duplicate descriptions. |


## `cdc-pact-provider-test-support`

| File | Responsibility |
|---|---|
| `cdc-pact-provider-test-support/build.gradle` | Builds provider-side Pact JVM JUnit 5 verification support. Uses the lighter Pact `junit5` provider dependency to avoid forcing Spring WebFlux/WebSocket onto consumer apps. |
| `cdc-pact-provider-test-support/src/main/java/com/fedex/cdc/provider/CdcPactProviderVerification.java` | Provider-side annotation used by provider teams to declare the provider name and broker-related environment property names. |
| `cdc-pact-provider-test-support/src/main/java/com/fedex/cdc/provider/CdcPactProviderVerificationExtension.java` | JUnit extension that reads `@CdcPactProviderVerification` and sets Pact JVM system properties such as provider name, broker URL, token, provider version, branch, and publish-results flag. |
| `cdc-pact-provider-test-support/src/main/java/com/fedex/cdc/provider/AbstractCdcProviderVerificationTest.java` | Abstract Spring Boot provider verification test for Pact Broker based verification. Starts the provider on a random port and delegates verification to Pact JVM's JUnit 5 provider extension. |
| `cdc-pact-provider-test-support/src/main/java/com/fedex/cdc/provider/AbstractCdcLocalPactProviderVerificationTest.java` | Abstract Spring Boot provider verification test for local Pact folder verification, useful for local samples and GitHub Actions jobs that pass generated Pact files as artifacts. |
## `cdc-pact-broker`

| File | Responsibility |
|---|---|
| `cdc-pact-broker/build.gradle` | Builds the broker integration module. Currently depends on core and Spring Web for future publish-client implementation. |
| `cdc-pact-broker/src/main/java/com/fedex/cdc/broker/PactBrokerPublishProperties.java` | Configuration record for future Pact Broker publishing inputs such as broker URL, token, provider version, and pact directory. |

## `cdc-pact-sample-app`

| File | Responsibility |
|---|---|
| `cdc-pact-sample-app/build.gradle` | Builds the sample Spring Boot consumer app. Depends on the starter and test-support modules. |
| `cdc-pact-sample-app/src/main/resources/application.properties` | Runtime/test configuration for the sample app. |
| `cdc-pact-sample-app/src/main/java/com/fedex/cdc/sample/ConsumerDrivenAutoPactApplication.java` | Sample Spring Boot application entrypoint. Uses `scanBasePackages = "com.fedex.cdc"` so both sample expectations and starter-provided automation can be discovered. |
| `cdc-pact-sample-app/src/main/java/com/fedex/cdc/sample/contracts/rest/RestH2ProviderExpectations.java` | Consumer-owned REST expectations for provider `pact-rest-h2-sample`. |
| `cdc-pact-sample-app/src/main/java/com/fedex/cdc/sample/contracts/jms/JmsH2ProviderExpectations.java` | Consumer-owned JMS/message expectations for provider `pact-jms-h2-sample`. |
| `cdc-pact-sample-app/src/main/java/com/fedex/cdc/sample/contracts/legacy/LegacySampleProducerExpectations.java` | Additive legacy expectations for provider `sample-producer`, proving old valid expectations can coexist with new provider contracts. |
| `cdc-pact-sample-app/src/main/java/com/fedex/cdc/sample/dto/CreateCustomerRequestExpectation.java` | Consumer-owned REST request body shape for customer creation. |
| `cdc-pact-sample-app/src/main/java/com/fedex/cdc/sample/dto/CustomerResponseExpectation.java` | Consumer-owned REST response body shape expected from the REST H2 provider. |
| `cdc-pact-sample-app/src/main/java/com/fedex/cdc/sample/dto/CustomerEventExpectation.java` | Consumer-owned JMS/message body shape expected from the JMS H2 provider. |
| `cdc-pact-sample-app/src/main/java/com/fedex/cdc/sample/dto/LegacyProductResponseExpectation.java` | Consumer-owned legacy REST response body shape expected from `sample-producer`. |
| `cdc-pact-sample-app/src/test/java/com/fedex/cdc/sample/ConsumerDrivenAgainstH2SamplesTest.java` | Minimal sample test that extends `AbstractConsumerContractGenerationTest` and overrides the output directory/filter for H2-targeted REST/JMS generation. It does not contain custom generator logic. |

## Generated Output

Generated files are under the sample app module and should not be edited manually.

| Path | Responsibility |
|---|---|
| `cdc-pact-sample-app/build/pacts/all/` | Scan-all generated Pact files for every valid consumer expectation set. |
| `cdc-pact-sample-app/build/pacts/h2-targeted/` | Targeted Pact output for only `pact-rest-h2-sample` and `pact-jms-h2-sample`. |
| `cdc-pact-sample-app/build/pacts/all/consumer-driven-contract-testing-pact-rest-h2-sample-consumer-driven-rest.json` | Generated REST Pact for the H2 REST provider. |
| `cdc-pact-sample-app/build/pacts/all/consumer-driven-contract-testing-pact-jms-h2-sample-consumer-driven-jms.json` | Generated JMS/message Pact for the H2 JMS provider. |
| `cdc-pact-sample-app/build/pacts/all/consumer-driven-contract-testing-sample-producer-consumer-driven-rest.json` | Generated REST Pact for the legacy sample provider. |

## How To Run

From the parent folder:

```powershell
cd C:\Users\pravi\spring-services\consumer-driven-contract-testing
.\gradlew.bat test
```

Expected result:

```text
BUILD SUCCESSFUL
```

## Enterprise Direction

The project is already split into parent-contained modules. The next hardening steps are:

- Implement real Pact Broker publishing in `cdc-pact-broker`.
- Add Maven publishing metadata for each reusable module.
- Add provider verification helpers to `cdc-pact-test-support`.
- Add configuration properties to the starter for output directory, provider filters, and broker behavior.
- Keep `cdc-pact-sample-app` as a sample app only; business services should depend on the reusable modules.