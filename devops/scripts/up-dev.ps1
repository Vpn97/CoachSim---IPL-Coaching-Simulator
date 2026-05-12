$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Test-Path '.env.dev')) {
  Write-Host "[up-dev] devops/.env.dev not found — copying from .env.dev.example"
  Copy-Item '.env.dev.example' '.env.dev'
}

docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file .env.dev up --build @args
