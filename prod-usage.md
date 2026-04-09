# Production Usage Guide - Consumer Driven Contract Testing

This guide explains how to use `consumer-driven-contract-testing` as an enterprise-ready Consumer-Driven Contract Testing framework in production Spring Boot applications, with GitHub Actions as the CI/CD engine.

Parent framework location in this workspace:

```text
C:\Users\pravi\spring-services\consumer-driven-contract-testing
```

## 1. What This Framework Is

`consumer-driven-contract-testing` is a parent Gradle project containing reusable modules for automated consumer-driven Pact generation.

The framework lets consumer applications declare expectations using annotations instead of manually writing Pact DSL for each interaction.

Current parent-contained modules:

| Module | Production Role |
|---|---|
| `cdc-pact-core` | Core annotations, scanner, model, generator, sample payload factory, and Pact JSON writer. |
| `cdc-pact-spring-boot-starter` | Single developer-facing CDC dependency. Registers core CDC automation beans and exposes consumer/provider test support transitively. |
| `cdc-pact-test-support` | Internal reusable consumer-side test helper module. Developers do not add this directly; it is exposed through the starter. |
| `cdc-pact-provider-test-support` | Internal reusable provider-side verification helper module. Developers do not add this directly; it is exposed through the starter. |
| `cdc-pact-broker` | Broker integration module placeholder/config module. The current production recommendation is to publish generated Pact JSON from GitHub Actions using Pact CLI or a broker HTTP step until this module is fully implemented. |
| `cdc-pact-sample-app` | Sample app only. Do not deploy this as a production service. Use it as a reference implementation. |

## 2. Production Deployment Model

This framework should not be deployed as a long-running production application.

Instead, publish its reusable libraries to your internal artifact repository, then use those libraries from real consumer services.

Recommended artifact flow:

```text
consumer-driven-contract-testing repo
  -> build reusable jars
  -> publish jars to internal Maven/Artifactory/GitHub Packages
  -> consumer services depend on starter + test-support
  -> consumer service CI generates Pact files
  -> consumer service CI publishes Pact files to Pact Broker
  -> provider service CI verifies against Pact Broker
```

## 3. Artifacts To Publish

Publish these modules as reusable production artifacts. Consumer teams should directly depend only on cdc-pact-spring-boot-starter:

```text
com.fedex.cdc:cdc-pact-core:1.0.0
com.fedex.cdc:cdc-pact-spring-boot-starter:1.0.0
com.fedex.cdc:cdc-pact-test-support:1.0.0
com.fedex.cdc:cdc-pact-broker:1.0.0
```

Consumer services normally need one CDC framework dependency:

```gradle
implementation 'com.fedex.cdc:cdc-pact-spring-boot-starter:1.0.0'
```

Provider services also use the same single CDC framework dependency. Pact JVM provider verification support is exposed transitively by the starter, so provider teams should not add Pact JVM boilerplate dependencies directly unless they need advanced customization.

## 4. Recommended Production Repository Strategy

Use one of these patterns.

### Option A - Dedicated Framework Repository

Recommended for enterprise use.

```text
consumer-driven-contract-testing
  -> framework source
  -> publish artifacts to internal Maven repository

consumer-service-a
  -> depends on published framework artifact
  -> declares expectations
  -> generates and publishes Pact files

provider-service-b
  -> verifies Pacts from broker
```

### Option B - Composite Build During Development

Good only for local testing or early adoption.

```gradle
includeBuild('../consumer-driven-contract-testing')
```

Do not rely on composite builds for production CI unless your enterprise build strategy intentionally supports mono-repo or multi-repo checkouts.

## 5. How A Consumer Service Uses The Framework

### Step 1 - Add Dependencies

In the consumer service `build.gradle`:

```gradle
dependencies {
    implementation 'com.fedex.cdc:cdc-pact-spring-boot-starter:1.0.0'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

### Step 2 - Create Consumer-Owned DTOs

Create DTOs that represent what the consumer expects from the provider.

Example:

```java
package com.myteam.orders.contracts.dto;

import com.fedex.cdc.automation.annotations.PactExample;
import com.fedex.cdc.automation.annotations.PactSecret;

import java.util.UUID;

public record CustomerResponseExpectation(
        @PactExample("22222222-2222-2222-2222-222222222222") UUID id,
        @PactExample("Priya Customer") String name,
        @PactSecret String email,
        @PactExample("GOLD") String tier) {
}
```

Important rule:

```text
Expectation DTOs are consumer-owned requirements, not provider entity mirrors.
```

### Step 3 - Create REST Expectations

```java
package com.myteam.orders.contracts;

import com.fedex.cdc.automation.annotations.ConsumerPactClient;
import com.fedex.cdc.automation.annotations.ConsumerPactInteraction;
import com.myteam.orders.contracts.dto.CustomerResponseExpectation;
import org.springframework.stereotype.Component;

@Component
@ConsumerPactClient(consumer = "orders-service", provider = "customer-service")
public class CustomerServiceExpectations {
    @ConsumerPactInteraction(
            description = "orders service expects to read customer by id",
            providerState = "customer exists",
            method = "GET",
            path = "/api/customers/22222222-2222-2222-2222-222222222222",
            query = {"includeLinks=true"},
            responseBody = CustomerResponseExpectation.class)
    public void readCustomerById() {
    }
}
```

### Step 4 - Create JMS/Message Expectations

```java
package com.myteam.orders.contracts;

import com.fedex.cdc.automation.annotations.ConsumerPactClient;
import com.fedex.cdc.automation.annotations.ConsumerPactMessageInteraction;
import com.myteam.orders.contracts.dto.CustomerEventExpectation;
import org.springframework.stereotype.Component;

@Component
@ConsumerPactClient(consumer = "orders-service", provider = "customer-events-service")
public class CustomerEventExpectations {
    @ConsumerPactMessageInteraction(
            description = "orders service expects customer created event",
            providerState = "customer created event exists",
            destination = "customer.events.v1",
            metadata = {"contentType=application/json", "eventType=CUSTOMER_CREATED"},
            messageBody = CustomerEventExpectation.class)
    public void customerCreatedEvent() {
    }
}
```

### Step 5 - Use The Framework-Provided Generation Test

Developers should not write custom generator logic for each service.

Use the base test provided transitively by `cdc-pact-spring-boot-starter`:

```java
package com.myteam.orders.contracts;

import com.fedex.cdc.testsupport.AbstractConsumerContractGenerationTest;

class ConsumerContractGenerationTest extends AbstractConsumerContractGenerationTest {
}
```

That is intentionally minimal. The inherited framework test will:

- Start the Spring test context.
- Scan all `@ConsumerPactClient` expectation classes.
- Generate Pact files under `build/pacts`.
- Fail if no Pact files were generated.

Only override defaults when you need targeted generation, for example a specific provider or type:

```java
package com.myteam.orders.contracts;

import com.fedex.cdc.automation.generator.PactGenerationFilter;
import com.fedex.cdc.testsupport.AbstractConsumerContractGenerationTest;

import java.util.Set;

class CustomerContractGenerationTest extends AbstractConsumerContractGenerationTest {
    @Override
    protected PactGenerationFilter generationFilter() {
        return new PactGenerationFilter(Set.of("customer-service"), Set.of("REST"));
    }
}
```

Run locally:

```powershell
.\gradlew.bat test
```

Generated Pact files should be under:

```text
build/pacts
```

## 6. How To Generate Contract Tests

In this framework, contract tests are generated through the reusable `AbstractConsumerContractGenerationTest` and annotated consumer expectations.

You do not manually write Pact DSL per endpoint.

Process:

```text
1. Add expectation DTOs.
2. Add @ConsumerPactClient class.
3. Add @ConsumerPactInteraction or @ConsumerPactMessageInteraction methods.
4. Run the generic generation test.
5. Validate generated Pact files.
6. Publish generated Pact files to Pact Broker.
```

### Auto Generate Expectations (No Manual Expectation Classes)

For teams that want fully automated expectation discovery:

```text
cdc.expectations.auto=true
```

With this enabled, framework scans the consumer app and auto-creates:

- REST expectations from Feign-style client interfaces.
- JMS expectations from `@JmsListener` methods.

Optional properties:

```text
cdc.consumer.name=orders-service
cdc.jms.default.provider=shipment-events-provider
```

Environment alternatives:

```text
CDC_EXPECTATIONS_AUTO=true
CDC_CONSUMER_NAME=orders-service
CDC_JMS_DEFAULT_PROVIDER=shipment-events-provider
```

Recommended quality gates:

- Each interaction description must be unique per consumer/provider/type.
- Each generated Pact must include `consumer`, `provider`, `metadata`, and `matchingRules`.
- REST contracts must include `interactions`.
- JMS/message contracts must include `messages`.
- Sensitive fields must be masked with `********`.
- Contract generation must run in pull requests before merge.

### OpenAPI/Swagger Generated Models And Custom Generated Paths

If your consumer uses OpenAPI/Swagger Gradle generation (for stubs/entities/models), the framework can scan generated model sources and include them in sample payload generation.

Default scanned path:

```text
build/generated
```

To override with one or more custom generated folders, set:

```text
cdc.openapi.generated.path
```

Examples:

```powershell
.\gradlew.bat test -Dcdc.openapi.generated.path="build/generated,build/generated-sources/openapi"
```

```text
CDC_OPENAPI_GENERATED_PATH=build/generated;custom-output/openapi
```

Use this in GitHub Actions when generated models are not under the default path:

```yaml
- name: Run consumer contract tests
  run: ./gradlew test -Dcdc.openapi.generated.path="build/generated,custom-output/openapi"
```

## 7. Publishing Generated Contracts To Pact Broker

The current `cdc-pact-broker` module is prepared as a broker integration module but does not yet include a full publish client.

Recommended production approach for now:

```text
Use GitHub Actions to publish generated build/pacts/*.json files to Pact Broker using Pact CLI, Pact Broker Docker image, or an enterprise-approved HTTP publishing step.
```

Typical broker inputs:

| Input | Meaning |
|---|---|
| `PACT_BROKER_BASE_URL` | Base URL of the Pact Broker. |
| `PACT_BROKER_TOKEN` | Broker token stored as a GitHub Actions secret. |
| `GITHUB_SHA` | Provider/consumer version. Usually the commit SHA. |
| `GITHUB_REF_NAME` | Branch name for broker tagging/branch metadata. |

Recommended GitHub secrets:

```text
PACT_BROKER_BASE_URL
PACT_BROKER_TOKEN
```

Recommended GitHub variables:

```text
PACT_CONSUMER_NAME
PACT_PROVIDER_NAME
```

## 8. Consumer GitHub Actions Workflow

Create this in a consumer service repository:

```text
.github/workflows/consumer-contracts.yml
```

Example:

```yaml
name: Consumer Contract Tests

on:
  pull_request:
  push:
    branches:
      - main

jobs:
  generate-and-publish-pacts:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: gradle

      - name: Run consumer contract tests
        run: ./gradlew test

      - name: Upload generated Pact files as build artifact
        uses: actions/upload-artifact@v4
        with:
          name: generated-pacts
          path: build/pacts/**/*.json
          if-no-files-found: error

      - name: Publish Pacts to Pact Broker
        if: github.event_name == 'push'
        env:
          PACT_BROKER_BASE_URL: ${{ secrets.PACT_BROKER_BASE_URL }}
          PACT_BROKER_TOKEN: ${{ secrets.PACT_BROKER_TOKEN }}
          CONSUMER_VERSION: ${{ github.sha }}
          CONSUMER_BRANCH: ${{ github.ref_name }}
        run: |
          docker run --rm \
            -v "$PWD/build/pacts:/pacts" \
            pactfoundation/pact-cli:latest \
            publish /pacts \
            --broker-base-url "$PACT_BROKER_BASE_URL" \
            --broker-token "$PACT_BROKER_TOKEN" \
            --consumer-app-version "$CONSUMER_VERSION" \
            --branch "$CONSUMER_BRANCH"
```

Notes:

- Pull requests generate and validate contracts but do not publish by default.
- Pushes to `main` publish contracts to the Pact Broker.
- If your enterprise does not allow Docker in GitHub Actions, replace the Docker step with your approved Pact CLI installation or an internal reusable workflow.

## 9. Provider Verification In Production

Provider services should verify contracts from the Pact Broker before deployment.

Provider verification flow:

```text
Provider app starts in test mode
  -> provider verification test fetches contracts from Pact Broker
  -> provider states are configured
  -> Pact verifier calls provider endpoints or message producers
  -> build fails if provider breaks consumer expectations
```

For REST providers, use `@CdcPactProviderVerification` with `AbstractCdcProviderVerificationTest`.

For message providers, add message provider methods/provider states as required by Pact JVM. The base support removes broker/target boilerplate, but provider-owned state setup still belongs in the provider repo.


## 9A. Provider-Side Framework Usage

Provider teams should also add only the starter dependency:

```gradle
dependencies {
    implementation 'com.fedex.cdc:cdc-pact-spring-boot-starter:1.0.0'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

Then create one provider verification test:

```java
package com.myteam.customer.contracts;

import au.com.dius.pact.provider.junitsupport.State;
import com.fedex.cdc.provider.AbstractCdcProviderVerificationTest;
import com.fedex.cdc.provider.CdcPactProviderVerification;

@CdcPactProviderVerification(provider = "customer-service")
class ProviderContractTest extends AbstractCdcProviderVerificationTest {
    @State("customer exists")
    void customerExists() {
        // Insert H2/test data or configure mocks for this provider state.
    }
}
```

Provider teams do not write one Pact test per endpoint. They write one verification class and only add provider-state methods needed to prepare data for consumer expectations.

For local artifact-based validation, use `AbstractCdcLocalPactProviderVerificationTest` and set the `pact.folder` system property, for example in Gradle: `systemProperty 'pact.folder', file('../sample-orders/build/pacts').absolutePath`. For production Pact Broker validation, use `AbstractCdcProviderVerificationTest`.

The annotation maps GitHub Actions environment variables to Pact JVM system properties:

| Annotation Attribute | Default Environment Source | Pact JVM Property |
|---|---|---|
| `provider` | Annotation value | `pact.provider.name` |
| `brokerUrlProperty` | `PACT_BROKER_BASE_URL` | `pactbroker.url` |
| `brokerTokenProperty` | `PACT_BROKER_TOKEN` | `pactbroker.auth.token` |
| `providerVersionProperty` | `GITHUB_SHA` | `pact.provider.version` |
| `providerBranchProperty` | `GITHUB_REF_NAME` | `pact.provider.branch` |
| `publishResults` | Annotation value | `pact.verifier.publishResults` |
## 10. Provider GitHub Actions Workflow

Create this in the provider service repository:

```text
.github/workflows/provider-verification.yml
```

Example:

```yaml
name: Provider Contract Verification

on:
  pull_request:
  push:
    branches:
      - main
  workflow_dispatch:

jobs:
  verify-pacts:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: gradle

      - name: Run provider Pact verification
        env:
          PACT_BROKER_BASE_URL: ${{ secrets.PACT_BROKER_BASE_URL }}
          PACT_BROKER_TOKEN: ${{ secrets.PACT_BROKER_TOKEN }}
          PROVIDER_VERSION: ${{ github.sha }}
          PROVIDER_BRANCH: ${{ github.ref_name }}
        run: ./gradlew pactVerify

      - name: Can I Deploy provider
        if: github.event_name == 'push'
        env:
          PACT_BROKER_BASE_URL: ${{ secrets.PACT_BROKER_BASE_URL }}
          PACT_BROKER_TOKEN: ${{ secrets.PACT_BROKER_TOKEN }}
          PROVIDER_VERSION: ${{ github.sha }}
        run: |
          docker run --rm pactfoundation/pact-cli:latest \
            broker can-i-deploy \
            --broker-base-url "$PACT_BROKER_BASE_URL" \
            --broker-token "$PACT_BROKER_TOKEN" \
            --pacticipant "customer-service" \
            --version "$PROVIDER_VERSION"
```

The exact Gradle task name may differ depending on the provider verification plugin or wrapper used by the provider service.

Common task names:

```text
pactVerify
contractTest
providerContractTest
```

## 11. Example Provider Verification Test Shape

A Spring Boot REST provider should use the framework base class rather than Pact JVM boilerplate:

```java
import au.com.dius.pact.provider.junitsupport.State;
import com.fedex.cdc.provider.AbstractCdcProviderVerificationTest;
import com.fedex.cdc.provider.CdcPactProviderVerification;

@CdcPactProviderVerification(provider = "customer-service")
class CustomerProviderContractTest extends AbstractCdcProviderVerificationTest {
    @State("customer exists")
    void customerExists() {
        // Insert or configure H2/test data required by the consumer expectation.
    }
}
```

Use the provider states declared in the consumer expectation annotations, for example:

```java
@ConsumerPactInteraction(
    providerState = "customer exists",
    ...
)
```

## 12. Full GitHub Actions End-To-End Flow

Recommended production flow:

```text
Consumer PR
  -> run ./gradlew test
  -> generate Pact files
  -> upload generated Pacts as PR artifact
  -> do not publish unless approved

Consumer main push
  -> run ./gradlew test
  -> generate Pact files
  -> publish Pact files to Pact Broker with commit SHA

Provider PR
  -> run provider verification against Pact Broker
  -> fail PR if provider breaks published consumer contracts

Provider main push
  -> run provider verification
  -> run can-i-deploy
  -> deploy only if broker says safe
```

## 13. GitHub Actions Reusable Workflow Recommendation

For enterprise consistency, create central reusable workflows:

```text
.github/workflows/reusable-consumer-contracts.yml
.github/workflows/reusable-provider-verification.yml
```

Consumer repos call:

```yaml
jobs:
  contracts:
    uses: fedex-platform/contract-workflows/.github/workflows/reusable-consumer-contracts.yml@main
    secrets: inherit
```

Provider repos call:

```yaml
jobs:
  provider-verification:
    uses: fedex-platform/contract-workflows/.github/workflows/reusable-provider-verification.yml@main
    secrets: inherit
```

This keeps Pact Broker publishing, token handling, branch tagging, and can-i-deploy rules consistent across teams.

## 14. Recommended Branch And Version Rules

Use:

```text
consumer version = GitHub SHA
provider version = GitHub SHA
branch = GitHub ref name
```

Recommended broker metadata:

```text
main branch contracts are deployable candidates
feature branch contracts are for early compatibility checks
production deployments should run can-i-deploy before release
```

## 15. Production Readiness Checklist

Before rolling this out broadly:

- Publish framework artifacts to internal Maven/Artifactory/GitHub Packages.
- Add real broker publishing implementation to `cdc-pact-broker` or standardize Pact CLI usage in GitHub Actions.
- Add provider verification templates for REST and JMS/message providers.
- Add generated contract duplicate checks to every consumer contract test.
- Add security review for token handling and generated payload masking.
- Add versioning strategy for breaking contract changes.
- Add branch/tag strategy in Pact Broker.
- Add `can-i-deploy` checks before production deployments.
- Create reusable GitHub Actions workflows for consumer and provider teams.
- Keep `cdc-pact-sample-app` as reference only, not as a deployed production application.

## 16. Local Validation Commands

From the parent framework project:

```powershell
cd C:\Users\pravi\spring-services\consumer-driven-contract-testing
.\gradlew.bat test
```

From the REST H2 provider sample:

```powershell
cd C:\Users\pravi\spring-services\pact-rest-h2-sample
.\gradlew.bat test
```

From the JMS H2 provider sample:

```powershell
cd C:\Users\pravi\spring-services\pact-jms-h2-sample
.\gradlew.bat test
```

All three should pass before publishing framework changes.
