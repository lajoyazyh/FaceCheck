Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-FaceCheckLocalDevContext {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ScriptRoot
    )

    $backendDir = Split-Path -Parent $ScriptRoot
    $repoRoot = Split-Path -Parent $backendDir

    [pscustomobject]@{
        ScriptRoot = $ScriptRoot
        BackendDir = $backendDir
        RepoRoot = $repoRoot
        ComposeFile = Join-Path $repoRoot "docker-compose.yml"
        WrapperCmd = Join-Path $backendDir "mvnw.cmd"
        ExpectedPostgresDb = "facecheck"
        ExpectedPostgresUser = "facecheck"
        ExpectedPostgresPassword = "facecheck"
    }
}

function Write-Section {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Title
    )

    Write-Host ""
    Write-Host ("== " + $Title + " ==")
}

function Write-Ok {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    Write-Host ("[OK] " + $Message)
}

function Write-Fail {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    Write-Host ("[FAIL] " + $Message)
}

function Write-Info {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    Write-Host ("[INFO] " + $Message)
}

function Normalize-ComposeValue {
    param(
        [string]$Value
    )

    if ($null -eq $Value) {
        return $null
    }

    $normalized = $Value.Trim()
    $normalized = $normalized.Trim("'".ToCharArray())
    $normalized = $normalized.Trim('"'.ToCharArray())
    return $normalized
}

function Get-ComposeInfo {
    param(
        [Parameter(Mandatory = $true)]
        $Context
    )

    $info = [ordered]@{
        ContainerName = "facecheck-postgres"
        PostgresDb = $null
        PostgresUser = $null
        PostgresPassword = $null
    }

    if (-not (Test-Path -Path $Context.ComposeFile)) {
        return [pscustomobject]$info
    }

    $lines = Get-Content -Path $Context.ComposeFile
    $inPostgres = $false

    foreach ($line in $lines) {
        if ($line -match '^\s{2}postgres:\s*$') {
            $inPostgres = $true
            continue
        }

        if ($inPostgres -and $line -match '^\s{2}[A-Za-z0-9_-]+:\s*$') {
            break
        }

        if (-not $inPostgres) {
            continue
        }

        if ($line -match '^\s{4}container_name:\s*(?<value>.+?)\s*$') {
            $info.ContainerName = Normalize-ComposeValue -Value $Matches["value"]
            continue
        }

        if ($line -match '^\s{6}(?<key>POSTGRES_(?:DB|USER|PASSWORD)):\s*(?<value>.+?)\s*$') {
            $key = $Matches["key"]
            $value = Normalize-ComposeValue -Value $Matches["value"]

            switch ($key) {
                "POSTGRES_DB" { $info.PostgresDb = $value }
                "POSTGRES_USER" { $info.PostgresUser = $value }
                "POSTGRES_PASSWORD" { $info.PostgresPassword = $value }
            }

            continue
        }

        if ($line -match '^\s{6}-\s*(?<key>POSTGRES_(?:DB|USER|PASSWORD))=(?<value>.+?)\s*$') {
            $key = $Matches["key"]
            $value = Normalize-ComposeValue -Value $Matches["value"]

            switch ($key) {
                "POSTGRES_DB" { $info.PostgresDb = $value }
                "POSTGRES_USER" { $info.PostgresUser = $value }
                "POSTGRES_PASSWORD" { $info.PostgresPassword = $value }
            }
        }
    }

    return [pscustomobject]$info
}

function Test-ComposeMatchesLocalDefaults {
    param(
        [Parameter(Mandatory = $true)]
        $Context,
        [Parameter(Mandatory = $true)]
        $ComposeInfo
    )

    return (
        $ComposeInfo.PostgresDb -eq $Context.ExpectedPostgresDb -and
        $ComposeInfo.PostgresUser -eq $Context.ExpectedPostgresUser -and
        $ComposeInfo.PostgresPassword -eq $Context.ExpectedPostgresPassword
    )
}

function Get-JavaVersionStatus {
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if (-not $javaCommand) {
        return [pscustomobject]@{
            Available = $false
            Major = $null
            Output = "java is not on PATH."
        }
    }

    $output = (& cmd /c "java -version 2>&1" | Out-String).TrimEnd()
    $major = $null

    if ($output -match 'version "(?<major>\d+)') {
        $major = [int]$Matches["major"]
    }

    return [pscustomobject]@{
        Available = $true
        Major = $major
        Output = $output
    }
}

function Invoke-MavenWrapperVersion {
    param(
        [Parameter(Mandatory = $true)]
        $Context
    )

    if (-not (Test-Path -Path $Context.WrapperCmd)) {
        return [pscustomobject]@{
            ExitCode = 1
            Output = "Missing $($Context.WrapperCmd)"
        }
    }

    Push-Location $Context.BackendDir
    try {
        $output = (& cmd /c .\mvnw.cmd -v 2>&1 | Out-String).TrimEnd()
        $exitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }

    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = $output
    }
}

function Test-MavenWrapperHealthy {
    param(
        [Parameter(Mandatory = $true)]
        $Result
    )

    return (
        $Result.ExitCode -eq 0 -and
        $Result.Output -match "Apache Maven" -and
        $Result.Output -match "Java version"
    )
}

function Test-MavenWrapperDownloadChainAbnormal {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Output
    )

    return (
        $Output -match "StatusCode" -or
        $Output -match "RawContentLength" -or
        $Output -match "Content\s*[:{]"
    )
}

function Get-DockerVersionStatus {
    $dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $dockerCommand) {
        return [pscustomobject]@{
            ExitCode = 1
            Output = "docker is not on PATH."
        }
    }

    $output = (& cmd /c "docker version 2>&1" | Out-String).TrimEnd()
    $exitCode = $LASTEXITCODE

    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = $output
    }
}

function Invoke-DockerComposePs {
    param(
        [Parameter(Mandatory = $true)]
        $Context
    )

    Push-Location $Context.RepoRoot
    try {
        $output = (& cmd /c "docker compose ps 2>&1" | Out-String).TrimEnd()
        $exitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }

    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = $output
    }
}

function Get-DockerComposeRunningServices {
    param(
        [Parameter(Mandatory = $true)]
        $Context
    )

    Push-Location $Context.RepoRoot
    try {
        $services = & docker compose ps --services 2>$null
        $exitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }

    if ($exitCode -ne 0) {
        return @()
    }

    return @($services | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

function Test-DockerContainerExists {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ContainerName
    )

    $containerNames = & docker ps -a --format "{{.Names}}" 2>$null
    if ($LASTEXITCODE -ne 0) {
        return $false
    }

    return @($containerNames) -contains $ContainerName
}

function Get-DockerPublishedPort {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ContainerName,
        [Parameter(Mandatory = $true)]
        [string]$ContainerPort
    )

    $output = (& cmd /c ('docker port {0} {1} 2>&1' -f $ContainerName, $ContainerPort) | Out-String).TrimEnd()
    $exitCode = $LASTEXITCODE

    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = $output
    }
}

function Invoke-PostgresSocketLogin {
    param(
        [Parameter(Mandatory = $true)]
        $Context,
        [Parameter(Mandatory = $true)]
        [string]$ContainerName
    )

    $command = 'docker exec -i {0} psql -U {1} -d {2} -c "select 1;" 2>&1' -f $ContainerName, $Context.ExpectedPostgresUser, $Context.ExpectedPostgresDb
    $output = (& cmd /c $command | Out-String).TrimEnd()
    $exitCode = $LASTEXITCODE

    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = $output
    }
}

function Invoke-PostgresTcpPasswordLogin {
    param(
        [Parameter(Mandatory = $true)]
        $Context,
        [Parameter(Mandatory = $true)]
        [string]$ContainerName
    )

    $command = 'docker exec -e PGPASSWORD={0} -i {1} psql -h host.docker.internal -p 5432 -U {2} -d {3} -c "select 1;" 2>&1' -f $Context.ExpectedPostgresPassword, $ContainerName, $Context.ExpectedPostgresUser, $Context.ExpectedPostgresDb
    $output = (& cmd /c $command | Out-String).TrimEnd()
    $exitCode = $LASTEXITCODE

    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = $output
    }
}

function Get-LocalPortStatus {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    $listeners = @()
    $getNetTcpConnection = Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue

    if ($getNetTcpConnection) {
        try {
            $listeners = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop)
        }
        catch {
            $listeners = @()
        }
    }

    if ($listeners.Count -gt 0) {
        $details = foreach ($listener in $listeners) {
            $processName = "unknown"
            try {
                $processName = (Get-Process -Id $listener.OwningProcess -ErrorAction Stop).ProcessName
            }
            catch {
            }

            [pscustomobject]@{
                ProcessId = $listener.OwningProcess
                ProcessName = $processName
            }
        }

        return [pscustomobject]@{
            InUse = $true
            Details = $details
        }
    }

    $netstatLines = @(netstat -ano -p tcp | Select-String (":{0}\s+.*LISTENING" -f $Port))
    if ($netstatLines.Count -gt 0) {
        return [pscustomobject]@{
            InUse = $true
            Details = @($netstatLines | ForEach-Object { $_.ToString().Trim() })
        }
    }

    return [pscustomobject]@{
        InUse = $false
        Details = @()
    }
}

function Get-Port8080Status {
    return Get-LocalPortStatus -Port 8080
}

function Test-TopLevelMavenRepositories {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SettingsPath
    )

    if (-not (Test-Path -Path $SettingsPath)) {
        return $false
    }

    try {
        [xml]$settingsXml = Get-Content -Path $SettingsPath -Raw
    }
    catch {
        return $false
    }

    foreach ($childNode in $settingsXml.DocumentElement.ChildNodes) {
        if ($childNode.NodeType -eq [System.Xml.XmlNodeType]::Element -and $childNode.LocalName -eq "repositories") {
            return $true
        }
    }

    return $false
}

function Show-MavenWrapperFixGuidance {
    param(
        [Parameter(Mandatory = $true)]
        [string]$WrapperOutput
    )

    Write-Host "Maven Wrapper currently cannot start Maven correctly."
    if (Test-MavenWrapperDownloadChainAbnormal -Output $WrapperOutput) {
        Write-Host "Detected StatusCode/RawContentLength/Content output, which means the wrapper download chain is broken."
    }
    Write-Host "Fix .\mvnw.cmd first, or temporarily use system Maven:"
    Write-Host "  mvn spring-boot:run"
    Write-Host "Do not troubleshoot http://localhost:8080/api/health before wrapper is healthy."
}

function Show-PostgresRepairGuidance {
    param(
        [Parameter(Mandatory = $true)]
        $Context,
        [Parameter(Mandatory = $true)]
        $ComposeInfo,
        [Parameter(Mandatory = $true)]
        [string]$ContainerName
    )

    $composeMatchesLocalDefaults = Test-ComposeMatchesLocalDefaults -Context $Context -ComposeInfo $ComposeInfo

    Write-Host "PostgreSQL local repair options:"
    Write-Host "1. If you can recreate the local containers:"
    Write-Host "   docker compose down"
    Write-Host "   docker compose up -d postgres redis rabbitmq"
    Write-Host "   Warning: whether old data is kept depends on the volume configuration in docker-compose.yml."
    Write-Host "2. If you only want to fix the current container password:"
    Write-Host "   docker exec -it $ContainerName sh"
    Write-Host "   psql -U facecheck -d postgres"
    Write-Host "   ALTER USER facecheck WITH PASSWORD 'facecheck';"
    Write-Host "   If the initial superuser is not facecheck in your container, try psql -U postgres -d postgres instead."
    Write-Host "   If Spring Boot still fails but docker exec inside the container succeeds, recheck whether localhost:5432 is actually mapped to this container."

    if ($composeMatchesLocalDefaults) {
        Write-Host "3. docker-compose.yml already uses POSTGRES_USER=facecheck, POSTGRES_PASSWORD=facecheck, POSTGRES_DB=facecheck."
        Write-Host "   Old volumes do not automatically update an existing database user's password when POSTGRES_PASSWORD changes."
    }
    else {
        Write-Host "3. docker-compose.yml and the local backend defaults are not aligned."
        Write-Host "   For local development, make them consistent with:"
        Write-Host "   POSTGRES_USER=facecheck"
        Write-Host "   POSTGRES_PASSWORD=facecheck"
        Write-Host "   POSTGRES_DB=facecheck"
    }
}

function Show-MavenSettingsGuidance {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SettingsPath,
        [Parameter(Mandatory = $true)]
        $Context
    )

    Write-Host "Maven settings warning: $SettingsPath contains a top-level <repositories> element."
    Write-Host "Move repositories into <profiles><profile>...</profile></profiles>, or use <mirrors> instead."
    Write-Host ("A valid project example is available at " + (Join-Path $Context.RepoRoot "docs\maven-settings-example.xml"))
}
