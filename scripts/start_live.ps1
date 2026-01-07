# start_live.ps1
# 1. Build the project
Write-Host "Building project..." -ForegroundColor Cyan
mvn clean install -DskipTests

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed! Exiting." -ForegroundColor Red
    exit
}
D:\py_code_workspace\.venv\Scripts\Activate.ps1
# 2. Start the TV Data Bridge in the background
Write-Host "Starting TV Data Bridge..." -ForegroundColor Cyan
$bridgeProc = Start-Process python -ArgumentList "tv_data_bridge.py" -PassThru -NoNewWindow

# 3. Start the Java Engine in live mode
Write-Host "Starting Java Hybrid Engine (Live Mode)..." -ForegroundColor Cyan
java -cp "ats-dashboard/target/ats-dashboard-v2-jar-with-dependencies.jar" com.trading.hf.Main

# Cleanup on exit
Write-Host "Shutting down..." -ForegroundColor Yellow
Stop-Process -Id $bridgeProc.Id -Force
