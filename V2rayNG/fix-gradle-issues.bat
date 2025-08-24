@echo off
echo Fixing Gradle issues for V2rayNG project...
echo.

echo 1. Stopping all Gradle daemons...
gradlew --stop
echo.

echo 2. Cleaning Gradle cache...
gradlew clean
echo.

echo 3. Cleaning .gradle directory...
if exist .gradle rmdir /s /q .gradle
echo.

echo 4. Cleaning build directories...
if exist app\build rmdir /s /q app\build
if exist build rmdir /s /q build
echo.

echo 5. Refreshing Gradle wrapper...
gradlew wrapper --gradle-version 7.5
echo.

echo 6. Building project...
gradlew build
echo.

echo Gradle fix completed! Please try opening the project in Android Studio again.
pause
