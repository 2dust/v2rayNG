@echo off
setlocal EnableDelayedExpansion

REM Get current directory of the script
set "__dir=%~dp0"
REM Remove trailing backslash for path consistency
set "__dir=%__dir:~0,-1%"

REM Check if NDK_HOME is set and exists
if not exist "%NDK_HOME%" (
    echo Android NDK: NDK_HOME not found. Please set env NDK_HOME
    exit /b 1
)

REM Create a temporary directory using Windows temp folder
set "TMPDIR=%TEMP%\hev_build_%RANDOM%"
mkdir "%TMPDIR%"

REM Build hev-socks5-tunnel
mkdir "%TMPDIR%\jni"
pushd "%TMPDIR%"

REM Create Android.mk
echo include $(call all-subdir-makefiles^) > jni\Android.mk

REM Use Directory Junction (mklink /J) instead of symbolic link (ln -s)
REM This avoids the need for Administrator privileges on Windows
mklink /J "jni\hev-socks5-tunnel" "%__dir%\hev-socks5-tunnel" >nul
if %ERRORLEVEL% neq 0 goto :error

REM Execute ndk-build.cmd for Windows
call "%NDK_HOME%\ndk-build.cmd" ^
    NDK_PROJECT_PATH=. ^
    APP_BUILD_SCRIPT=jni/Android.mk ^
    "APP_ABI=armeabi-v7a arm64-v8a x86 x86_64" ^
    APP_PLATFORM=android-24 ^
    NDK_LIBS_OUT="%TMPDIR%\libs" ^
    NDK_OUT="%TMPDIR%\obj" ^
    "APP_CFLAGS=-O3 -DPKGNAME=com/v2ray/ang/service" ^
    "APP_LDFLAGS=-Wl,--build-id=none -Wl,--hash-style=gnu"
if %ERRORLEVEL% neq 0 goto :error

REM Copy compiled libraries back to original directory
if not exist "%__dir%\libs" mkdir "%__dir%\libs"
xcopy /E /Y /I "%TMPDIR%\libs\*" "%__dir%\libs\" >nul

popd

REM Clean up temporary directory
rmdir /S /Q "%TMPDIR%"
echo Build successful.
exit /b 0

:error
REM Error handling routine to clean up and exit
echo Aborted, error %ERRORLEVEL%
popd
if exist "%TMPDIR%" rmdir /S /Q "%TMPDIR%"
exit /b 1