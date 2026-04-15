# Docker Desktop CDC Demo (Standalone Apps)

This demo uses `consumer-driven-contract-testing` with two standalone Spring Boot apps:

- Consumer app: `C:\Users\pravi\spring-services\cdc-docker-consumer`
- Provider app: `C:\Users\pravi\spring-services\cdc-docker-provider`

The demo validates:

1. Auto consumer contract generation (no manual expectation classes)
2. Pact publication to Pact Broker
3. Provider verification via `@CdcPactProviderVerification`
4. `can-i-deploy` gating
5. A complex gating scenario:
   - `can-i-deploy` fails before provider verification
   - `can-i-deploy` passes after provider verification is published

## Prerequisites

- Docker Desktop running
- `docker compose` available

## Files

- Compose orchestration: `docker-compose.yml`
- Complex scenario script (PowerShell): `run-complex-can-i-deploy.ps1`
- Complex scenario script (Bash): `run-complex-can-i-deploy.sh`

## Run Runtime Apps (Docker Desktop)

From this directory:

```powershell
docker compose --profile runtime up --build
```

Runtime endpoints:

- Consumer app: `http://localhost:8081`
- Provider app: `http://localhost:8082`
- Pact Broker: `http://localhost:9292`

## Run CDC Pipeline + can-i-deploy (Complex Scenario)

PowerShell:

```powershell
.\run-complex-can-i-deploy.ps1
```

Bash:

```bash
chmod +x ./run-complex-can-i-deploy.sh
./run-complex-can-i-deploy.sh
```

## What The Complex Scenario Verifies

1. Consumer contracts are generated from scanned Feign client usage.
2. Contracts are published as two consumer versions (`2.3.0`, `2.4.0`).
3. `can-i-deploy` is executed before provider verification and is expected to block.
4. Provider verification publishes results with provider version `5.7.0`.
5. Provider `5.7.0` is recorded as deployed to `production`.
6. `can-i-deploy` is executed again for consumer `2.3.0` and must pass.
