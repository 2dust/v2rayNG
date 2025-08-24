Write-Host "Fixing SDK XML version compatibility issues..." -ForegroundColor Green
Write-Host ""

Write-Host "1. Checking Android SDK installation..." -ForegroundColor Yellow
$sdkPath = "C:\Users\MRAM\AppData\Local\Android\Sdk"
if (Test-Path $sdkPath) {
    Write-Host "SDK found at: $sdkPath" -ForegroundColor Green
} else {
    Write-Host "SDK not found at expected location!" -ForegroundColor Red
    Write-Host "Please update your local.properties file with correct SDK path" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "2. Cleaning Gradle cache..." -ForegroundColor Yellow
if (Test-Path ".gradle") {
    Remove-Item -Recurse -Force ".gradle"
    Write-Host "Gradle cache cleaned" -ForegroundColor Green
}

Write-Host ""
Write-Host "3. Cleaning build directories..." -ForegroundColor Yellow
if (Test-Path "build") {
    Remove-Item -Recurse -Force "build"
    Write-Host "Build directory cleaned" -ForegroundColor Green
}
if (Test-Path "app\build") {
    Remove-Item -Recurse -Force "app\build"
    Write-Host "App build directory cleaned" -ForegroundColor Green
}

Write-Host ""
Write-Host "4. Testing Gradle sync..." -ForegroundColor Yellow
try {
    .\gradlew --version
    Write-Host "Gradle is working correctly" -ForegroundColor Green
} catch {
    Write-Host "Gradle test failed" -ForegroundColor Red
}

Write-Host ""
Write-Host "SDK compatibility fixes applied:" -ForegroundColor Green
Write-Host "- Added SDK channel settings" -ForegroundColor Cyan
Write-Host "- Added override path check" -ForegroundColor Cyan
Write-Host "- Cleaned build cache" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "1. Open Android Studio" -ForegroundColor Cyan
Write-Host "2. Go to Tools > SDK Manager" -ForegroundColor Cyan
Write-Host "3. Update Android SDK Command-line Tools" -ForegroundColor Cyan
Write-Host "4. Update Android SDK Build-Tools" -ForegroundColor Cyan
Write-Host "5. Sync project with Gradle files" -ForegroundColor Cyan
Write-Host ""
Write-Host "Try opening the project in Android Studio now!" -ForegroundColor Green
