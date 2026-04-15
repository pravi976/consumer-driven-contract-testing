$ErrorActionPreference = "Stop"

$composeFile = Join-Path $PSScriptRoot "docker-compose.yml"

function Invoke-Compose {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Args
    )
    docker compose -f $composeFile @Args
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose command failed: $($Args -join ' ')"
    }
}

Write-Host "[1/7] Starting Pact Broker stack"
docker compose -f $composeFile down --remove-orphans --volumes | Out-Null
Invoke-Compose -Args @("up", "-d", "pact-broker-db", "pact-broker")
Invoke-Compose -Args @("build", "--no-cache", "consumer-contract-tests", "provider-pact-verification")
Start-Sleep -Seconds 8
Start-Sleep -Seconds 7

Write-Host "[2/7] Generating consumer contracts from scanned client usage"
Invoke-Compose -Args @("--profile", "pipeline", "run", "--rm", "--no-deps", "consumer-contract-tests")

Write-Host "[3/7] Publishing two consumer versions (main + release branch)"
Invoke-Compose -Args @("--profile", "pipeline", "run", "--rm", "--no-deps", "publish-consumer-pacts-v1")
Invoke-Compose -Args @("--profile", "pipeline", "run", "--rm", "--no-deps", "publish-consumer-pacts-v2")

Write-Host "[4/7] Running can-i-deploy before provider verification (expected to fail)"
docker compose -f $composeFile --profile pipeline run --rm --no-deps can-i-deploy-check
$preCheckExitCode = $LASTEXITCODE

if ($preCheckExitCode -eq 0) {
    throw "Expected can-i-deploy to fail before provider verification, but it passed."
}

Write-Host "Pre-verification can-i-deploy correctly blocked deployment."

Write-Host "[5/7] Running provider verification and publishing results"
Invoke-Compose -Args @("--profile", "pipeline", "run", "--rm", "--no-deps", "provider-pact-verification")

Invoke-Compose -Args @("--profile", "pipeline", "run", "--rm", "--no-deps", "record-provider-production-deployment")

Write-Host "[6/7] Running can-i-deploy after provider verification (must pass)"
Invoke-Compose -Args @("--profile", "pipeline", "run", "--rm", "--no-deps", "can-i-deploy-check")

Write-Host "[7/7] CDC demo complete. Cleaning stopped containers."
Invoke-Compose -Args @("down", "--remove-orphans")
