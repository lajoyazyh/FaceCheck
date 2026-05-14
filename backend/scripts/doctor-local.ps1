[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "local-dev-common.ps1")

$context = Get-FaceCheckLocalDevContext -ScriptRoot $PSScriptRoot
$composeInfo = Get-ComposeInfo -Context $context
$blockingIssueFound = $false

Write-Section "Java"
$javaStatus = Get-JavaVersionStatus
if (-not $javaStatus.Available) {
    Write-Fail "java is not on PATH. Backend local startup expects Java 21."
    $blockingIssueFound = $true
}
else {
    Write-Info $javaStatus.Output
    if ($javaStatus.Major -eq 21) {
        Write-Ok "Detected Java 21."
    }
    else {
        Write-Fail "Detected Java $($javaStatus.Major). The backend baseline is Java 21. Do not change the global JDK just for this repo; open a Java 21 terminal for backend work."
        $blockingIssueFound = $true
    }
}

Write-Section "Maven Wrapper"
$wrapperStatus = Invoke-MavenWrapperVersion -Context $context
if (Test-MavenWrapperHealthy -Result $wrapperStatus) {
    Write-Ok "mvnw.cmd -v returned a valid Maven version."
}
else {
    Write-Fail "mvnw.cmd -v did not return a valid Maven version."
    if ($wrapperStatus.Output) {
        Write-Host $wrapperStatus.Output
    }
    Show-MavenWrapperFixGuidance -WrapperOutput $wrapperStatus.Output
    $blockingIssueFound = $true
}

Write-Section "Docker"
$dockerStatus = Get-DockerVersionStatus
if ($dockerStatus.ExitCode -ne 0) {
    Write-Fail "docker is not available."
    if ($dockerStatus.Output) {
        Write-Host $dockerStatus.Output
    }
    $blockingIssueFound = $true
}
else {
    Write-Ok "docker version is available."
}

$composePsStatus = Invoke-DockerComposePs -Context $context
if ($composePsStatus.ExitCode -ne 0) {
    Write-Fail "docker compose ps failed."
    if ($composePsStatus.Output) {
        Write-Host $composePsStatus.Output
    }
    $blockingIssueFound = $true
}
else {
    Write-Info "docker compose ps:"
    Write-Host $composePsStatus.Output

    $runningServices = Get-DockerComposeRunningServices -Context $context
    $requiredServices = @("postgres", "redis", "rabbitmq")
    $missingServices = @($requiredServices | Where-Object { $runningServices -notcontains $_ })

    if ($missingServices.Count -eq 0) {
        Write-Ok "docker compose ps can see postgres, redis, and rabbitmq."
    }
    else {
        Write-Fail ("Missing compose services: " + ($missingServices -join ", "))
        Write-Host "Start them with:"
        Write-Host "  docker compose up -d postgres redis rabbitmq"
        $blockingIssueFound = $true
    }
}

Write-Section "PostgreSQL"
$postgresContainerName = if ($composeInfo.ContainerName) { $composeInfo.ContainerName } else { "facecheck-postgres" }
Write-Info ("PostgreSQL container name: " + $postgresContainerName)

if (-not (Test-DockerContainerExists -ContainerName $postgresContainerName)) {
    Write-Fail ("Container not found: " + $postgresContainerName)
    Write-Host "Bring up the local infra first:"
    Write-Host "  docker compose up -d postgres redis rabbitmq"
    $blockingIssueFound = $true
}
else {
    $postgresPortStatus = Get-DockerPublishedPort -ContainerName $postgresContainerName -ContainerPort "5432/tcp"
    if ($postgresPortStatus.ExitCode -eq 0 -and $postgresPortStatus.Output -match ":5432") {
        Write-Ok ("Host port mapping detected for PostgreSQL: " + $postgresPortStatus.Output)
    }
    else {
        Write-Fail "PostgreSQL container is not published to host port 5432."
        if ($postgresPortStatus.Output) {
            Write-Host $postgresPortStatus.Output
        }
        Write-Host "If docker compose ps shows only 5432/tcp instead of 0.0.0.0:5432->5432/tcp, Spring Boot on Windows will not hit this container through localhost:5432."
        $host5432Status = Get-LocalPortStatus -Port 5432
        if ($host5432Status.InUse) {
            Write-Host "Host port 5432 is already occupied by:"
            foreach ($detail in $host5432Status.Details) {
                if ($detail -is [string]) {
                    Write-Host ("  " + $detail)
                }
                else {
                    Write-Host ("  PID " + $detail.ProcessId + " (" + $detail.ProcessName + ")")
                }
            }
        }
        $blockingIssueFound = $true
    }

    $socketLoginStatus = Invoke-PostgresSocketLogin -Context $context -ContainerName $postgresContainerName
    if ($socketLoginStatus.ExitCode -eq 0) {
        Write-Ok ("docker exec socket login succeeded: docker exec -i {0} psql -U facecheck -d facecheck -c `"select 1;`"" -f $postgresContainerName)
    }
    else {
        Write-Fail "docker exec socket login failed."
        Write-Host $socketLoginStatus.Output
        $blockingIssueFound = $true
    }

    $tcpPasswordStatus = Invoke-PostgresTcpPasswordLogin -Context $context -ContainerName $postgresContainerName
    if ($tcpPasswordStatus.ExitCode -eq 0) {
        Write-Ok "Host-path TCP password authentication to localhost:5432 succeeded."
    }
    else {
        Write-Fail "Host-path TCP password authentication to localhost:5432 failed."
        Write-Host $tcpPasswordStatus.Output
        Show-PostgresRepairGuidance -Context $context -ComposeInfo $composeInfo -ContainerName $postgresContainerName
        $blockingIssueFound = $true
    }
}

Write-Section "Port 8080"
$port8080Status = Get-Port8080Status
if ($port8080Status.InUse) {
    Write-Fail "Port 8080 is already in use."
    foreach ($detail in $port8080Status.Details) {
        if ($detail -is [string]) {
            Write-Host ("  " + $detail)
        }
        else {
            Write-Host ("  PID " + $detail.ProcessId + " (" + $detail.ProcessName + ")")
        }
    }
    $blockingIssueFound = $true
}
else {
    Write-Ok "Port 8080 is available."
}

Write-Section "Maven Settings"
$settingsPath = Join-Path $env:USERPROFILE ".m2\settings.xml"
if (Test-TopLevelMavenRepositories -SettingsPath $settingsPath) {
    Write-Fail "Detected a top-level <repositories> element in your user Maven settings."
    Show-MavenSettingsGuidance -SettingsPath $settingsPath -Context $context
}
else {
    Write-Ok "No top-level <repositories> issue detected in user Maven settings."
}

Write-Section "Next Steps"
Write-Host "Recommended order:"
Write-Host "  docker compose up -d postgres redis rabbitmq"
Write-Host "  cd backend"
Write-Host "  .\mvnw.cmd -v"
Write-Host "  .\mvnw.cmd spring-boot:run"
Write-Host "  curl.exe http://localhost:8080/api/health"

if ($blockingIssueFound) {
    exit 1
}

exit 0
