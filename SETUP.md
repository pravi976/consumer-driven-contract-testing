# CDC Setup Guide (Brokerless)

This document explains end-to-end setup for:

- `consumer-driven-contract-testing`
- `cdc-docker-consumer`
- `cdc-docker-provider`

This setup uses a brokerless shared-contract model via GitHub branch `contracts`.

## 1. Where To Configure In GitHub

For each repository:

1. Open repo in GitHub.
2. Go to `Settings` -> `Secrets and variables` -> `Actions`.
3. Add required `Repository secrets`.
4. Add required `Repository variables`.

## 2. How To Generate Tokens / Secrets

## 2.1 GH_PACKAGES_TOKEN (GitHub PAT)

1. Open GitHub -> profile icon -> `Settings`.
2. Go to `Developer settings` -> `Personal access tokens`.
3. Choose `Tokens (classic)` -> `Generate new token (classic)`.
4. Give a name like `cdc-packages-token`.
5. Expiration: choose your policy (recommended 90 days or org standard).
6. Select scopes:
   - `read:packages`
   - `write:packages` (needed for framework publish)
   - `repo` (recommended, required for private repos)
7. Click `Generate token`.
8. Copy token immediately and store in password manager.
9. Add token in repo secrets as `GH_PACKAGES_TOKEN`.

## 2.2 GH_REPO_DISPATCH_TOKEN (GitHub PAT)

1. Open GitHub -> `Settings` -> `Developer settings` -> `Personal access tokens`.
2. Create `Tokens (classic)` -> `Generate new token (classic)`.
3. Name it `cdc-repo-dispatch-token`.
4. Scope:
   - `repo`
5. Generate token and copy it.
6. Add in both consumer and provider repos as `GH_REPO_DISPATCH_TOKEN`.

## 2.3 DOCKERHUB_TOKEN

1. Login to Docker Hub.
2. Open `Account settings` -> `Personal access tokens`.
3. Click `Generate new token`.
4. Name: `cdc-github-actions`.
5. Permission: `Read, Write, Delete` (or minimum needed by your policy).
6. Copy token.
7. Add secrets:
   - `DOCKERHUB_USERNAME`
   - `DOCKERHUB_TOKEN`

## 2.4 KUBE_CONFIG_DATA (Optional for K8s deploy)

From your machine with working `kubectl` context:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\.kube\config"))
```

Copy output and add as secret `KUBE_CONFIG_DATA`.

## 3. Repository Setup Matrix

## 3.1 consumer-driven-contract-testing

### Secrets

- `GH_PACKAGES_TOKEN` (required)
- `DOCKERHUB_USERNAME` (optional)
- `DOCKERHUB_TOKEN` (optional)
- `KUBE_CONFIG_DATA` (optional)

### Variables

No mandatory variables unless your workflow defines custom ones.

## 3.2 cdc-docker-consumer

### Secrets

- `GH_PACKAGES_TOKEN` (required)
- `GH_REPO_DISPATCH_TOKEN` (required)
- `DOCKERHUB_USERNAME` (optional)
- `DOCKERHUB_TOKEN` (optional)
- `KUBE_CONFIG_DATA` (optional)

### Variables

- `CDC_FRAMEWORK_VERSION` (required, example `1.0.0-SNAPSHOT`)
- `CDC_PACKAGES_OWNER` (required, example `pravi976`)
- `CDC_PACKAGES_REPO` (required, example `consumer-driven-contract-testing`)
- `PROVIDER_REPO` (optional, default `pravi976/cdc-docker-provider`)
- `SHARED_CONTRACT_REPO` (optional, default `pravi976/cdc-docker-consumer`)
- `SHARED_CONTRACT_BRANCH` (optional, default `contracts`)

## 3.3 cdc-docker-provider

### Secrets

- `GH_PACKAGES_TOKEN` (required)
- `GH_REPO_DISPATCH_TOKEN` (required)
- `DOCKERHUB_USERNAME` (optional)
- `DOCKERHUB_TOKEN` (optional)
- `KUBE_CONFIG_DATA` (optional)

### Variables

- `CDC_FRAMEWORK_VERSION` (required)
- `CDC_PACKAGES_OWNER` (required)
- `CDC_PACKAGES_REPO` (required)
- `SHARED_CONTRACT_REPO` (optional, default `pravi976/cdc-docker-consumer`)
- `SHARED_CONTRACT_BRANCH` (optional, default `contracts`)

## 4. Execution Order

1. Publish framework artifacts from `consumer-driven-contract-testing`.
2. Run `cdc-docker-consumer` workflow:
   - generates pact files
   - publishes into `contracts` branch
   - dispatches provider validation
3. Run `cdc-docker-provider` workflow:
   - reads pact files from `contracts` branch
   - verifies provider using `PACT_FOLDER`
   - writes verification status JSON
4. Consumer workflow waits for status JSON and enforces can-i-deploy equivalent.

## 5. Source-of-Truth Paths

- Pacts:
  - `contracts/pacts/supply-orders-consumer/<consumer-sha>/*.json`
- Latest pointer:
  - `contracts/latest/supply-orders-consumer/latest.sha`
- Verification status:
  - `contracts/verification/supply-orders-consumer/<consumer-sha>/supply-inventory-provider-test.json`

## 6. Where To Check Results

1. Consumer Actions run:
   - step `Can I Deploy Equivalent (Wait For Provider Verification Status)`
2. Contracts branch:
   - verification JSON in `contracts/verification/...`
3. Dashboard run:
   - `Contracts Verification Dashboard` workflow in `cdc-docker-consumer`
   - summary table + artifact.

## 7. Not Required In Brokerless Mode

- `PACT_BROKER_BASE_URL`
- `PACT_BROKER_USERNAME`
- `PACT_BROKER_PASSWORD`

