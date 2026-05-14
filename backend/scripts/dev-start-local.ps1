[CmdletBinding()]
param(
    [switch]$UseSystemMavenFallback
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "local-dev-common.ps1")

$context = Get-FaceCheckLocalDevContext -ScriptRoot $PSScriptRoot
$composeInfo = Get-ComposeInfo -Context $context
$postgresContainerName = if ($composeInfo.ContainerName) { $composeInfo.ContainerName } else { "facecheck-postgres" }

Write-Section "Preflight"

$port8080Status = Get-Port8080Status
if ($port8080Status.InUse) {
    Write-Fail "Port 8080 is already in use. Stop the existing process before starting Spring Boot."
    foreach ($detail in $port8080Status.Details) {
        if ($detail -is [string]) {
            Write-Host ("  " + $detail)
        }
        else {
            Write-Host ("  PID " + $detail.ProcessId + " (" + $detail.ProcessName + ")")
        }
    }
    exit 1
}

$wrapperStatus = Invoke-MavenWrapperVersion -Context $context
$useWrapper = Test-MavenWrapperHealthy -Result $wrapperStatus

if ($useWrapper) {
    Write-Ok "Maven Wrapper is healthy."
}
else {
    Write-Fail "Maven Wrapper is not healthy."
    if ($wrapperStatus.Output) {
        Write-Host $wrapperStatus.Output
    }
    Show-MavenWrapperFixGuidance -WrapperOutput $wrapperStatus.Output

    if (-not $UseSystemMavenFallback) {
        Write-Host "Pass -UseSystemMavenFallback if you want this script to run system Maven instead."
        exit 1
    }

    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        Write-Fail "System Maven is not on PATH, so fallback is not available."
        exit 1
    }

    Write-Info "Using system Maven fallback because -UseSystemMavenFallback was provided."
}

$dockerStatus = Get-DockerVersionStatus
if ($dockerStatus.ExitCode -ne 0) {
    Write-Fail "docker is not available."
    if ($dockerStatus.Output) {
        Write-Host $dockerStatus.Output
    }
    exit 1
}

$composeMatchesLocalDefaults = Test-ComposeMatchesLocalDefaults -Context $context -ComposeInfo $composeInfo
if (-not $composeMatchesLocalDefaults) {
    Write-Fail "docker-compose.yml does not match the local backend defaults."
    Write-Host "Expected:"
    Write-Host "  POSTGRES_USER=facecheck"
    Write-Host "  POSTGRES_PASSWORD=facecheck"
    Write-Host "  POSTGRES_DB=facecheck"
    Write-Host "Fix docker-compose.yml or adjust your local run strategy before starting Spring Boot."
    exit 1
}

if (-not (Test-DockerContainerExists -ContainerName $postgresContainerName)) {
    Write-Fail ("Container not found: " + $postgresContainerName)
    Write-Host "Start the local infra first:"
    Write-Host "  docker compose up -d postgres redis rabbitmq"
    exit 1
}

$postgresPortStatus = Get-DockerPublishedPort -ContainerName $postgresContainerName -ContainerPort "5432/tcp"
if (-not ($postgresPortStatus.ExitCode -eq 0 -and $postgresPortStatus.Output -match ":5432")) {
    Write-Fail "PostgreSQL is not published to host port 5432, so jdbc:postgresql://localhost:5432/facecheck will not reach the expected container."
    if ($postgresPortStatus.Output) {
        Write-Host $postgresPortStatus.Output
    }
    $host5432Status = Get-LocalPortStatus -Port 5432
    if ($host5432Status.InUse) {
        Write-Host "Host port 5432 is currently occupied by:"
        foreach ($detail in $host5432Status.Details) {
            if ($detail -is [string]) {
                Write-Host ("  " + $detail)
            }
            else {
                Write-Host ("  PID " + $detail.ProcessId + " (" + $detail.ProcessName + ")")
            }
        }
    }
    Write-Host "Recreate the local infra so the current docker-compose.yml port mapping takes effect:"
    Write-Host "  docker compose down"
    Write-Host "  docker compose up -d postgres redis rabbitmq"
    exit 1
}

$tcpPasswordStatus = Invoke-PostgresTcpPasswordLogin -Context $context -ContainerName $postgresContainerName
if ($tcpPasswordStatus.ExitCode -ne 0) {
    Write-Fail "PostgreSQL password authentication failed for facecheck/facecheck. Spring Boot will not be started."
    Write-Host $tcpPasswordStatus.Output
    Show-PostgresRepairGuidance -Context $context -ComposeInfo $composeInfo -ContainerName $postgresContainerName
    exit 1
}

Write-Ok "PostgreSQL password authentication is healthy."

Write-Section "Environment"
$env:SPRING_PROFILES_ACTIVE = "local"
$env:DB_URL = "jdbc:postgresql://localhost:5432/facecheck"
$env:DB_USERNAME = "facecheck"
$env:DB_PASSWORD = "facecheck"
$env:REDIS_HOST = "localhost"
$env:REDIS_PORT = "6379"
$env:RABBITMQ_HOST = "localhost"
$env:RABBITMQ_PORT = "5672"
$env:RABBITMQ_USERNAME = "facecheck"
$env:RABBITMQ_PASSWORD = "facecheck"
$env:JWT_SECRET = "change-this-to-a-long-random-local-secret"
$env:HUAWEI_CLOUD_ENABLED = "false"
$env:SPRING_RABBITMQ_LISTENER_SIMPLE_AUTO_STARTUP = "true"
Write-Ok "Local backend environment variables are set for this PowerShell session only."

Write-Section "Startup"
Push-Location $context.BackendDir
try {
    if ($useWrapper) {
        Write-Info "Running .\mvnw.cmd spring-boot:run"
        & cmd /c .\mvnw.cmd spring-boot:run
        $exitCode = $LASTEXITCODE
    }
    else {
        Write-Info "Running mvn spring-boot:run"
        & mvn spring-boot:run
        $exitCode = $LASTEXITCODE
    }
}
finally {
    Pop-Location
}

exit $exitCode
