Write-Host "Testing Kotlin compatibility fix..." -ForegroundColor Green
Write-Host ""

Write-Host "1. Cleaning project..." -ForegroundColor Yellow
.\gradlew clean

Write-Host ""
Write-Host "2. Testing compilation..." -ForegroundColor Yellow
.\gradlew compileDebugKotlin

Write-Host ""
Write-Host "3. If successful, try full build..." -ForegroundColor Yellow
Write-Host "Run: .\gradlew build" -ForegroundColor Cyan
Write-Host ""
Write-Host "Kotlin version has been updated to 1.9.10" -ForegroundColor Green
Write-Host "Coroutines version has been updated to 1.7.3" -ForegroundColor Green
Write-Host ""
Write-Host "Try opening the project in Android Studio now!" -ForegroundColor Green
