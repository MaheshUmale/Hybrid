
Write-Host "Starting Backtest Strategy Verification (SAFE MODE - V5 CLEAN)..."

# Define Paths
$LOG_JAVA = "backtest_java_v5.log"
$LOG_SERVER = "server_log_v5.txt"
$ERR_SERVER = "server_error_v5.txt"
$ERR_JAVA = "java_error_v5.txt"

# 1. Cleanup previous logs AND DB
if (Test-Path $LOG_JAVA) { Remove-Item $LOG_JAVA }
if (Test-Path $LOG_SERVER) { Remove-Item $LOG_SERVER }
if (Test-Path $ERR_SERVER) { Remove-Item $ERR_SERVER }
if (Test-Path $ERR_JAVA) { Remove-Item $ERR_JAVA }

# CLEANUP DATABASE to prevent Zombie trades (Gate=null)
if (Test-Path "trading_system.db") { Remove-Item "trading_system.db" }
if (Test-Path "positions.json") { Remove-Item "positions.json" }

# Define correct Python path
$PYTHON_EXE = "D:\py_code_workspace\.venv\Scripts\python.exe"

# 2. Start Replay Engine (Background) on Port 8767
Write-Host "Launching Replay Engine (Port 8767, Speed 300)..."
$replayProcess = Start-Process -FilePath $PYTHON_EXE -ArgumentList "-u backtest_replay_parallel.py --date 2026-01-05 --speed 300" -PassThru -RedirectStandardOutput $LOG_SERVER -RedirectStandardError $ERR_SERVER -WindowStyle Hidden

Start-Sleep -Seconds 5

# 3. Start Java Dashboard (using V2 JAR) with SYSTEM PROPERTIES
Write-Host "Launching Java Application (V2 JAR)..."
if (Test-Path "ats-dashboard/target/ats-dashboard-v2-jar-with-dependencies.jar") {
    $javaArgs = "-Dws.url=ws://127.0.0.1:8767 -Ddashboard.port=7071 -jar ats-dashboard/target/ats-dashboard-v2-jar-with-dependencies.jar"
    $javaProcess = Start-Process -FilePath "java" -ArgumentList $javaArgs -PassThru -RedirectStandardOutput $LOG_JAVA -RedirectStandardError $ERR_JAVA -WindowStyle Hidden
} else {
    Write-Host "Critical: V2 JAR not found!"
    exit
}

Write-Host "Backtest running... Waiting for completion (~45 seconds)..."

# 4. Monitor Loop
$timeout = 180
$elapsed = 0
$completed = $false

while ($elapsed -lt $timeout) {
    if (Test-Path $LOG_SERVER) {
        try {
            $logContent = Get-Content $LOG_SERVER -Tail 10 -ErrorAction Stop
            if ($logContent -match "Completed") {
                $completed = $true
                break
            }
        } catch {
            # Ignore
        }
    }
    
    if ($replayProcess.HasExited) { break }
    if ($javaProcess.HasExited) { break }

    Start-Sleep -Seconds 2
    $elapsed += 2
    Write-Host "." -NoNewline
}

Write-Host ""
Stop-Process -Id $replayProcess.Id -ErrorAction SilentlyContinue
Stop-Process -Id $javaProcess.Id -ErrorAction SilentlyContinue

if ($completed) {
    Write-Host "Backtest Completed Successfully."
    Write-Host "Running Analysis..."
    Copy-Item $LOG_JAVA "backtest_java.log" -Force
    & $PYTHON_EXE analyze_backtest.py
} else {
    Write-Host "Timeout or Failure."
    Write-Host "--- SERVER LOG ---"
    if (Test-Path $LOG_SERVER) { Get-Content $LOG_SERVER -Tail 10 }
}
