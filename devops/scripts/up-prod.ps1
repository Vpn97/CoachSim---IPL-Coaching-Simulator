$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Test-Path '.env.prod')) {
  Write-Error "[up-prod] devops/.env.prod missing. Copy .env.prod.example and fill in real values first."
  exit 1
}

docker compose --profile proxy -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod up -d @args
