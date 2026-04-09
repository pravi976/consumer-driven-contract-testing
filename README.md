# Consumer Driven Contract Testing

This project demonstrates an enterprise-style **consumer-driven contract testing** framework for Spring Boot. It avoids hand-written Pact DSL by letting the consumer declare expectations with annotations, then automatically scanning those expectations and generating Pact-compatible REST and JMS/message JSON files.

Everything is kept inside one parent folder:

```text
C:\Users\pravi\spring-services\consumer-driven-contract-testing
```

## Current Structure

```text
consumer-driven-contract-testing/
|- cdc-pact-core
|- cdc-pact-spring-boot-starter
|- cdc-pact-test-support
|- cdc-pact-broker
|- cdc-pact-sample-app
|- build.gradle
|- settings.gradle
|- details.md
|- README.md
```

## Modules

| Module | Purpose |
|---|---|
| `cdc-pact-core` | Core annotations, scanner, generator, sample factory, model objects, and Pact writer. |
| `cdc-pact-spring-boot-starter` | Single developer-facing CDC dependency. Registers core CDC automation beans and exposes test support transitively. |
| `cdc-pact-test-support` | Internal consumer-side test helper module. Developers do not add this directly; it is exposed through the starter. |
| `cdc-pact-provider-test-support` | Internal provider-side verification helper module. Developers do not add this directly; it is exposed through the starter. |
| `cdc-pact-broker` | Placeholder broker integration module for future Pact Broker publishing. |
| `cdc-pact-sample-app` | Sample consumer app with REST/JMS/legacy expectations and validation tests. |

## Consumer-Driven Flow

```text
Consumer expectation classes
  -> @ConsumerPactClient
  -> @ConsumerPactInteraction / @ConsumerPactMessageInteraction
  -> ConsumerPactScanner
  -> ConsumerDrivenPactGenerator
  -> ConsumerDrivenPactWriter
  -> Pact JSON under cdc-pact-sample-app/build/pacts
  -> Provider verifies generated contract
```

## Important Annotations

Use `@ConsumerPactClient` on a consumer-owned expectation class:

```java
@Component
@ConsumerPactClient(consumer = "consumer-driven-contract-testing", provider = "pact-rest-h2-sample")
public class RestH2ProviderExpectations {
}
```

Use `@ConsumerPactInteraction` for REST expectations:

```java
@ConsumerPactInteraction(
        description = "consumer expects to read customer from REST H2 provider",
        providerState = "customer exists in H2",
        method = "GET",
        path = "/api/customers/22222222-2222-2222-2222-222222222222",
        query = {"includeLinks=true"},
        responseBody = CustomerResponseExpectation.class)
public void readCustomerById() {
}
```

Use `@ConsumerPactMessageInteraction` for JMS/message expectations:

```java
@ConsumerPactMessageInteraction(
        description = "consumer expects customer created event from JMS H2 provider",
        providerState = "customer event exists in H2 audit store",
        destination = "customer.events.v1",
        metadata = {"contentType=application/json", "eventType=CUSTOMER_CREATED"},
        messageBody = CustomerEventExpectation.class)
public void consumeCustomerCreatedEvent() {
}
```

Use `@PactExample` and `@PactSecret` on DTO fields/record components:

```java
public record CustomerResponseExpectation(
        @PactExample("22222222-2222-2222-2222-222222222222") UUID id,
        @PactExample("Priya Customer") String name,
        @PactSecret String email) {
}
```

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

Generated contracts appear under:

```text
cdc-pact-sample-app/build/pacts/all
cdc-pact-sample-app/build/pacts/h2-targeted
```

## Generated Contracts

The sample test currently generates:

```text
cdc-pact-sample-app/build/pacts/all/consumer-driven-contract-testing-pact-rest-h2-sample-consumer-driven-rest.json
cdc-pact-sample-app/build/pacts/all/consumer-driven-contract-testing-pact-jms-h2-sample-consumer-driven-jms.json
cdc-pact-sample-app/build/pacts/all/consumer-driven-contract-testing-sample-producer-consumer-driven-rest.json
```

The targeted H2 run generates only:

```text
cdc-pact-sample-app/build/pacts/h2-targeted/consumer-driven-contract-testing-pact-rest-h2-sample-consumer-driven-rest.json
cdc-pact-sample-app/build/pacts/h2-targeted/consumer-driven-contract-testing-pact-jms-h2-sample-consumer-driven-jms.json
```

## OpenAPI Generated Models Support

The framework now supports OpenAPI/Swagger generated model workflows.

During generation it:

1. Scans `build.gradle` / `build.gradle.kts` for OpenAPI/Swagger generation signals.
2. Scans generated model classes under `build/generated` by default.
3. Uses generated model types when creating Pact payload samples.

If your generated sources are in a custom location, configure:

```text
cdc.openapi.generated.path
```

You can provide one or more paths (comma or semicolon separated):

```powershell
.\gradlew.bat test -Dcdc.openapi.generated.path="build/generated,custom-output/openapi"
```

Environment variable alternative:

```text
CDC_OPENAPI_GENERATED_PATH=build/generated;custom-output/openapi
```

## How To Add A New Expectation

1. Add a DTO under `cdc-pact-sample-app/src/main/java/com/fedex/cdc/sample/dto`.
2. Add or update an expectation class under `cdc-pact-sample-app/src/main/java/com/fedex/cdc/sample/contracts`.
3. Annotate the class with `@ConsumerPactClient`.
4. Annotate methods with `@ConsumerPactInteraction` or `@ConsumerPactMessageInteraction`.
5. Run `./gradlew.bat test` from the parent folder.
6. Verify the generated Pact file under `cdc-pact-sample-app/build/pacts`.

## Enterprise Usage

For real service adoption, publish these modules to your internal Maven/Artifactory repository. Consumer teams should directly depend only on the starter:

```text
com.fedex.cdc:cdc-pact-core
com.fedex.cdc:cdc-pact-spring-boot-starter
com.fedex.cdc:cdc-pact-test-support
com.fedex.cdc:cdc-pact-broker
```

A consumer service should add one CDC framework dependency:

```gradle
implementation 'com.fedex.cdc:cdc-pact-spring-boot-starter:1.0.0-SNAPSHOT'
```

The starter brings in the core annotations, generator, Spring Boot auto-configuration, and reusable test support. Keep expectation declarations consumer-owned. Use filters for targeted runs instead of deleting expectation classes.

See `details.md` for every file name and responsibility.
