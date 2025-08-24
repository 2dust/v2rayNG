@echo off
echo Cleaning Gradle cache and rebuilding project...

REM Clean Gradle cache
gradlew.bat clean

REM Clean build cache
gradlew.bat cleanBuildCache

REM Remove .gradle directory in project
if exist .gradle rmdir /s /q .gradle

REM Remove build directories
if exist app\build rmdir /s /q app\build
if exist build rmdir /s /q build

REM Sync project
gradlew.bat --refresh-dependencies

echo Clean and rebuild completed. Try building your project now.
pause
