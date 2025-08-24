Write-Host "Fixing Gradle issues for V2rayNG project..." -ForegroundColor Green
Write-Host ""

Write-Host "1. Stopping all Gradle daemons..." -ForegroundColor Yellow
try {
    .\gradlew --stop
    Write-Host "Gradle daemons stopped successfully" -ForegroundColor Green
} catch {
    Write-Host "Warning: Could not stop Gradle daemons" -ForegroundColor Yellow
}
Write-Host ""

Write-Host "2. Cleaning Gradle cache..." -ForegroundColor Yellow
try {
    .\gradlew clean
    Write-Host "Gradle clean completed" -ForegroundColor Green
} catch {
    Write-Host "Warning: Gradle clean failed, continuing..." -ForegroundColor Yellow
}
Write-Host ""

Write-Host "3. Cleaning .gradle directory..." -ForegroundColor Yellow
if (Test-Path ".gradle") {
    Remove-Item -Recurse -Force ".gradle"
    Write-Host ".gradle directory cleaned" -ForegroundColor Green
}
Write-Host ""

Write-Host "4. Cleaning build directories..." -ForegroundColor Yellow
if (Test-Path "app\build") {
    Remove-Item -Recurse -Force "app\build"
    Write-Host "app\build directory cleaned" -ForegroundColor Green
}
if (Test-Path "build") {
    Remove-Item -Recurse -Force "build"
    Write-Host "build directory cleaned" -ForegroundColor Green
}
Write-Host ""

Write-Host "5. Refreshing Gradle wrapper..." -ForegroundColor Yellow
try {
    .\gradlew wrapper --gradle-version 7.5
    Write-Host "Gradle wrapper refreshed" -ForegroundColor Green
} catch {
    Write-Host "Warning: Could not refresh Gradle wrapper" -ForegroundColor Yellow
}
Write-Host ""

Write-Host "6. Building project..." -ForegroundColor Yellow
try {
    .\gradlew build
    Write-Host "Project built successfully!" -ForegroundColor Green
} catch {
    Write-Host "Build failed, but cache has been cleaned. Try opening in Android Studio now." -ForegroundColor Yellow
}
Write-Host ""

Write-Host "Gradle fix completed! Please try opening the project in Android Studio again." -ForegroundColor Green
Read-Host "Press Enter to continue"
