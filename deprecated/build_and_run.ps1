# Build the project
Write-Host "Building project..." -ForegroundColor Cyan
mvn clean install -DskipTests

# Check if build was successful
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}

# Run the application
Write-Host "Starting application..." -ForegroundColor Green
$jarPath = "ats-dashboard/target/ats-dashboard-1.0-SNAPSHOT-jar-with-dependencies.jar"
if (Test-Path $jarPath) {
    java -jar $jarPath
} else {
    Write-Host "Jar file not found: $jarPath" -ForegroundColor Red
}
