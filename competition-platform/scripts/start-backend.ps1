param([int]$Port = 8080)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$configFile = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $configFile) {
    foreach ($line in Get-Content -LiteralPath $configFile) {
        if ($line -match '^([A-Za-z][A-Za-z0-9_]*)=(.*)$') {
            $configName = $Matches[1]
            $configValue = $Matches[2].Trim().Trim('"').Trim("'")
            if ($configName -eq 'apikey') { $configName = 'LLM_API_KEY' }
            if ($configName -match '^(LLM_|ADVICE_|DB_|REDIS_|JWT_)') {
                [Environment]::SetEnvironmentVariable($configName, $configValue, 'Process')
            }
        }
    }
}
Set-Location (Join-Path $projectRoot 'backend')
mvn -o spring-boot:run "-Dspring-boot.run.arguments=--server.port=$Port"
