#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.yml"

echo "[1/7] Starting Pact Broker stack"
docker compose -f "${COMPOSE_FILE}" down --remove-orphans --volumes >/dev/null 2>&1 || true
docker compose -f "${COMPOSE_FILE}" up -d pact-broker-db pact-broker
docker compose -f "${COMPOSE_FILE}" build --no-cache consumer-contract-tests provider-pact-verification
sleep 8
sleep 7

echo "[2/7] Generating consumer contracts from scanned client usage"
docker compose -f "${COMPOSE_FILE}" --profile pipeline run --rm --no-deps consumer-contract-tests

echo "[3/7] Publishing two consumer versions (main + release branch)"
docker compose -f "${COMPOSE_FILE}" --profile pipeline run --rm --no-deps publish-consumer-pacts-v1
docker compose -f "${COMPOSE_FILE}" --profile pipeline run --rm --no-deps publish-consumer-pacts-v2

echo "[4/7] Running can-i-deploy before provider verification (expected to fail)"
set +e
docker compose -f "${COMPOSE_FILE}" --profile pipeline run --rm --no-deps can-i-deploy-check
PRECHECK_EXIT=$?
set -e
if [ "${PRECHECK_EXIT}" -eq 0 ]; then
  echo "Expected can-i-deploy to fail before provider verification, but it passed."
  exit 1
fi
echo "Pre-verification can-i-deploy correctly blocked deployment."

echo "[5/7] Running provider verification and publishing results"
docker compose -f "${COMPOSE_FILE}" --profile pipeline run --rm --no-deps provider-pact-verification
docker compose -f "${COMPOSE_FILE}" --profile pipeline run --rm --no-deps record-provider-production-deployment

echo "[6/7] Running can-i-deploy after provider verification (must pass)"
docker compose -f "${COMPOSE_FILE}" --profile pipeline run --rm --no-deps can-i-deploy-check

echo "[7/7] CDC demo complete. Cleaning stopped containers."
docker compose -f "${COMPOSE_FILE}" down --remove-orphans
