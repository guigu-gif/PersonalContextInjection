$ErrorActionPreference = "Stop"

$repoRoot = Split-Path $PSScriptRoot -Parent
$runtimeDir = Join-Path $repoRoot "runtime"
$redisDataDir = Join-Path $runtimeDir "redis-data"
$emptyInput = Join-Path $runtimeDir "empty-input.txt"
$redisConf = Join-Path $runtimeDir "redis-runtime.conf"

New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
New-Item -ItemType Directory -Force -Path $redisDataDir | Out-Null
if (!(Test-Path $emptyInput)) { New-Item -ItemType File -Path $emptyInput | Out-Null }

function Test-ProcessRunning {
    param([string]$Name)
    return $null -ne (Get-Process -Name $Name -ErrorAction SilentlyContinue | Select-Object -First 1)
}

function Test-PortListening {
    param([int]$Port)
    return $null -ne (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1)
}

function Wait-Until {
    param([scriptblock]$Check, [int]$Seconds = 10)
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        if (& $Check) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return (& $Check)
}

function Start-HiddenProcess {
    param([string]$FilePath, [string[]]$ArgumentList = @(), [string]$WorkingDirectory, [string]$Name)
    $stdout = Join-Path $runtimeDir ($Name + ".out.log")
    $stderr = Join-Path $runtimeDir ($Name + ".err.log")
    if (!(Test-Path $stdout)) { New-Item -ItemType File -Path $stdout | Out-Null }
    if (!(Test-Path $stderr)) { New-Item -ItemType File -Path $stderr | Out-Null }
    $p = @{
        FilePath = $FilePath
        WorkingDirectory = $WorkingDirectory
        WindowStyle = "Hidden"
        RedirectStandardInput = $emptyInput
        RedirectStandardOutput = $stdout
        RedirectStandardError = $stderr
    }
    if ($ArgumentList -and $ArgumentList.Count -gt 0) { $p.ArgumentList = $ArgumentList }
    Start-Process @p | Out-Null
}

function Ensure-FileExists {
    param([string]$Path, [string]$Label)
    if (!(Test-Path $Path)) { Write-Host "[error] $Label not found: $Path"; exit 1 }
}

# Redis config
$redisLog = (Join-Path $runtimeDir "redis.log").Replace("\", "/")
$redisData = $redisDataDir.Replace("\", "/")
$redisRuntimeText = @(
    "port 6379", "bind 127.0.0.1",
    "dir $redisData", "dbfilename dump.rdb",
    "logfile $redisLog", "appendonly no"
) -join "`r`n"
Set-Content -Path $redisConf -Value $redisRuntimeText -Encoding ASCII

Write-Host "=== PCI Start Services ==="

Write-Host "[1/3] Check MySQL..."
if (Test-PortListening 3306) {
    Write-Host "[ok] MySQL is running"
} else {
    Write-Host "[warn] MySQL not on 3306 — start it manually if backend fails"
}

Write-Host "[2/3] Check Redis..."
if ((Test-PortListening 6379) -or (Test-ProcessRunning "redis-server")) {
    Write-Host "[ok] Redis is running"
} else {
    Ensure-FileExists "D:\redis\redis-server.exe" "Redis executable"
    Start-HiddenProcess -FilePath "D:\redis\redis-server.exe" -ArgumentList @($redisConf) -WorkingDirectory "D:\redis" -Name "redis"
    if (Wait-Until -Seconds 10 -Check { Test-PortListening 6379 }) {
        Write-Host "[ok] Redis started"
    } else {
        Write-Host "[error] Redis failed to start"; exit 1
    }
}

Write-Host "[3/3] Check Qdrant..."
if (Test-ProcessRunning "qdrant") {
    Write-Host "[ok] Qdrant is running"
} else {
    Ensure-FileExists "D:\qdrant\qdrant.exe" "Qdrant executable"
    Start-HiddenProcess -FilePath "D:\qdrant\qdrant.exe" -WorkingDirectory "D:\qdrant" -Name "qdrant"
    if (Wait-Until -Seconds 10 -Check { Test-ProcessRunning "qdrant" }) {
        Write-Host "[ok] Qdrant started"
    } else {
        Write-Host "[error] Qdrant failed to start"; exit 1
    }
}

Write-Host ""
Write-Host "=== Services ready ==="
Write-Host "Next steps:"
Write-Host "  1. Run Spring Boot in IDEA  (backend port 8082)"
Write-Host "  2. cd frontend && npm run dev  (port 5173)"
Write-Host "  3. Open http://localhost:5173"
exit 0
