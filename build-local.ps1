[CmdletBinding()]
param(
    [string]$JavaPath = 'C:\Program Files\Java\jdk-21',
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repoPath = $PSScriptRoot
$buildPath = Join-Path $repoPath '.local-apk-build'
$projectPath = Join-Path $buildPath 'project'
$sdkPath = Join-Path $buildPath 'android-sdk'
$signingPath = Join-Path $repoPath 'tmp/behradNG-release-signing'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$abis = @('armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64')

function Run-Native {
    param([string]$Tool, [string[]]$Arguments)
    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Build tool failed: $Tool (exit $LASTEXITCODE)" }
}

function Download-File {
    param([string]$Url, [string]$Destination)
    Run-Native 'curl.exe' @('--fail', '--location', '--connect-timeout', '10', '--max-time', '900', $Url, '--output', $Destination)
}

function Write-Text {
    param([string]$Path, [string]$Text)
    [IO.File]::WriteAllText($Path, $Text, $utf8)
}

function Get-PinnedSource {
    param([string]$Url, [string]$Revision, [string]$Path)
    if (!(Test-Path -LiteralPath (Join-Path $Path '.git'))) {
        Run-Native 'git.exe' @('clone', '--no-checkout', $Url, $Path)
    }
    $actual = (& git.exe -C $Path rev-parse HEAD 2>$null)
    if ($LASTEXITCODE -ne 0 -or $actual -ne $Revision) {
        Run-Native 'git.exe' @('-C', $Path, 'fetch', 'origin', $Revision)
        Run-Native 'git.exe' @('-C', $Path, 'checkout', '--detach', $Revision)
    }
    Run-Native 'git.exe' @('-C', $Path, 'submodule', 'update', '--init', '--recursive')
    $actual = (& git.exe -C $Path rev-parse HEAD).Trim()
    if ($actual -ne $Revision) { throw 'Native source revision does not match the project pin.' }
}

foreach ($tool in @('git.exe', 'curl.exe', 'go.exe')) { $null = Get-Command $tool }
$javaExe = Join-Path $JavaPath 'bin/java.exe'
$keytoolExe = Join-Path $JavaPath 'bin/keytool.exe'
if (!(Test-Path -LiteralPath $javaExe)) { throw "Java not found at $JavaPath; supply -JavaPath." }
foreach ($name in @('behradNG-release.jks', 'APP_KEYSTORE_PASSWORD.txt', 'APP_KEYSTORE_ALIAS.txt', 'APP_KEY_PASSWORD.txt')) {
    if (!(Test-Path -LiteralPath (Join-Path $signingPath $name))) { throw "Missing local signing file: $name" }
}

$wrapperProperties = Get-Content (Join-Path $repoPath 'V2rayNG/gradle/wrapper/gradle-wrapper.properties') -Raw
$gradleMatch = [regex]::Match($wrapperProperties, 'gradle-([0-9.]+)-bin.zip')
if (!$gradleMatch.Success) { throw 'Cannot read pinned Gradle version.' }
$gradleVersion = $gradleMatch.Groups[1].Value
$workflow = Get-Content (Join-Path $repoPath '.github/workflows/build.yml') -Raw
$packageMatch = [regex]::Match($workflow, "packages: '([^']+)'")
if (!$packageMatch.Success) { throw 'Cannot read SDK packages from the workflow.' }
$sdkPackages = $packageMatch.Groups[1].Value.Split(' ', [StringSplitOptions]::RemoveEmptyEntries)
$ndkPackages = @($sdkPackages | Where-Object { $_.StartsWith('ndk;') })
if ($ndkPackages.Count -ne 2) { throw 'Expected the workflow to declare both native build NDKs.' }
$buildToolsVersion = ($sdkPackages | Where-Object { $_.StartsWith('build-tools;') }) -replace '^build-tools;', ''
$sdkManager = Join-Path $sdkPath 'cmdline-tools/16.0/bin/sdkmanager.bat'
$wrapperJar = Join-Path $projectPath 'gradle/wrapper/gradle-wrapper.jar'

if ($CheckOnly) {
    Write-Output 'Java, Git, curl, Go, and the local signing files are available.'
    Write-Output "Pinned Gradle: $gradleVersion"
    Write-Output "Required SDK packages: $($sdkPackages -join ', ')"
    Write-Output "Workspace SDK ready: $(Test-Path -LiteralPath $sdkManager)"
    Write-Output "Workspace Gradle wrapper ready: $(Test-Path -LiteralPath $wrapperJar)"
    Write-Output 'First build needs Internet access and will prompt for Android SDK licenses.'
    exit 0
}

New-Item -ItemType Directory -Path $buildPath, $projectPath, $sdkPath -Force | Out-Null
$savedEnv = @{}
$envNames = @('JAVA_HOME', 'PATH', 'ANDROID_HOME', 'ANDROID_SDK_ROOT', 'ANDROID_USER_HOME', 'ANDROID_NDK_HOME', 'GRADLE_USER_HOME', 'GOPATH', 'GOMODCACHE', 'GOCACHE', 'GOTOOLCHAIN', 'BNG_SIGNING_KEYSTORE', 'APP_KEYSTORE_PASSWORD', 'APP_KEYSTORE_ALIAS', 'APP_KEY_PASSWORD')
foreach ($name in $envNames) { $savedEnv[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    $env:JAVA_HOME = $JavaPath
    $env:ANDROID_HOME = $sdkPath
    $env:ANDROID_SDK_ROOT = $sdkPath
    $env:ANDROID_USER_HOME = Join-Path $buildPath 'android-user'
    $env:GRADLE_USER_HOME = Join-Path $buildPath 'gradle-home'
    $env:GOPATH = Join-Path $buildPath 'go'
    $env:GOMODCACHE = Join-Path $buildPath 'go-modules'
    $env:GOCACHE = Join-Path $buildPath 'go-cache'
    $env:GOTOOLCHAIN = 'auto'
    $env:PATH = "$JavaPath\bin;$env:GOPATH\bin;$env:PATH"
    New-Item -ItemType Directory -Path $env:ANDROID_USER_HOME -Force | Out-Null

    if (!(Test-Path -LiteralPath $sdkManager)) {
        Write-Output 'Downloading Android command-line tools into the workspace...'
        $sdkZip = Join-Path $buildPath 'android-commandline-tools.zip'
        Download-File 'https://dl.google.com/android/repository/commandlinetools-win-12266719_latest.zip' $sdkZip
        $unpackPath = Join-Path $buildPath 'sdk-tools-unpacked'
        Expand-Archive -LiteralPath $sdkZip -DestinationPath $unpackPath -Force
        $toolsPath = Join-Path $sdkPath 'cmdline-tools/16.0'
        New-Item -ItemType Directory -Path $toolsPath -Force | Out-Null
        Copy-Item -Path "$unpackPath/cmdline-tools/*" -Destination $toolsPath -Recurse -Force
    }
    Write-Output 'Review and accept the SDK licenses in this terminal to continue.'
    Run-Native $sdkManager @('--licenses')
    Run-Native $sdkManager (@('--install') + $sdkPackages)
    # Match the API 37 platform alias used by the project workflow, inside the workspace SDK.
    $integerPlatform = Join-Path $sdkPath 'platforms/android-37'
    $minorPlatform = Join-Path $sdkPath 'platforms/android-37.0'
    if (!(Test-Path -LiteralPath $integerPlatform) -and (Test-Path -LiteralPath $minorPlatform)) {
        New-Item -ItemType Junction -Path $integerPlatform -Target $minorPlatform | Out-Null
    }
    if (!(Test-Path -LiteralPath (Join-Path $integerPlatform 'android.jar'))) { throw 'Android 37 platform is missing.' }

    Write-Output 'Preparing the current local behradNG source...'
    Copy-Item -Path "$repoPath/V2rayNG/*" -Destination $projectPath -Recurse -Force
    Copy-Item -Path "$repoPath/app/*" -Destination (Join-Path $projectPath 'app') -Recurse -Force
    Write-Text (Join-Path $projectPath 'local.properties') ('sdk.dir=' + $sdkPath.Replace('\', '/'))
    if (!(Test-Path -LiteralPath $wrapperJar)) {
        Download-File "https://raw.githubusercontent.com/gradle/gradle/v$gradleVersion/gradle/wrapper/gradle-wrapper.jar" $wrapperJar
    }
    $wrapperBat = Join-Path $projectPath 'gradlew.bat'
    if (!(Test-Path -LiteralPath $wrapperBat)) {
        Download-File "https://raw.githubusercontent.com/gradle/gradle/v$gradleVersion/gradlew.bat" $wrapperBat
    }

    # These are the gitlinks in the fork; native sources are not upgraded for the local test.
    $xrayPath = Join-Path $buildPath 'AndroidLibXrayLite'
    $hevPath = Join-Path $buildPath 'hev-socks5-tunnel'
    Get-PinnedSource 'https://github.com/2dust/AndroidLibXrayLite.git' '1f3e2c5df1c248ed42630cee2bf747643d303ecc' $xrayPath
    Get-PinnedSource 'https://github.com/heiher/hev-socks5-tunnel.git' 'd9dca26c7ad0e494492244f0309e80ee583e739e' $hevPath
    $libsPath = Join-Path $projectPath 'app/libs'
    New-Item -ItemType Directory -Path $libsPath -Force | Out-Null
    $env:ANDROID_NDK_HOME = Join-Path $sdkPath ($ndkPackages[0].Replace(';', '/'))
    Push-Location $xrayPath
    try {
        Run-Native 'go.exe' @('mod', 'download')
        $mobileVersion = (& go.exe list -m -f '{{.Version}}' golang.org/x/mobile).Trim()
        if ($LASTEXITCODE -ne 0 -or !$mobileVersion) { throw 'Cannot read pinned Go mobile version.' }
        Run-Native 'go.exe' @('install', "golang.org/x/mobile/cmd/gomobile@$mobileVersion", "golang.org/x/mobile/cmd/gobind@$mobileVersion")
        $assetsPath = Join-Path $xrayPath 'assets'
        New-Item -ItemType Directory -Path $assetsPath -Force | Out-Null
        # Equivalent to the pinned gen_assets.sh download step, without requiring jq/Bash on Windows.
        Download-File 'https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat' (Join-Path $assetsPath 'geoip.dat')
        Download-File 'https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat' (Join-Path $assetsPath 'geosite.dat')
        Download-File 'https://raw.githubusercontent.com/Loyalsoldier/geoip/release/geoip-only-cn-private.dat' (Join-Path $assetsPath 'geoip-only-cn-private.dat')
        Run-Native (Join-Path $env:GOPATH 'bin/gomobile.exe') @('init')
        Run-Native (Join-Path $env:GOPATH 'bin/gomobile.exe') @('bind', '-v', '-target=android', '-androidapi', '24', '-trimpath', '-ldflags=-s -w -buildid= -checklinkname=0', '-o', (Join-Path $libsPath 'libv2ray.aar'), './')
    } finally { Pop-Location }

    Write-Output 'Building HEV libraries for all four ABIs...'
    $ndkBuild = Join-Path $sdkPath ($ndkPackages[1].Replace(';', '/') + '/ndk-build.cmd')
    $hevBuildPath = Join-Path $buildPath 'hev-build'
    New-Item -ItemType Directory -Path $hevBuildPath -Force | Out-Null
    $nativeLibsPath = Join-Path $hevBuildPath 'libs'
    $nativeObjectsPath = Join-Path $hevBuildPath 'obj'
    # The pinned Android.mk builds both the JNI library and standalone executable.
    Run-Native $ndkBuild @("NDK_PROJECT_PATH=$($hevBuildPath.Replace('\', '/'))", "APP_BUILD_SCRIPT=$($hevPath.Replace('\', '/'))/Android.mk", "APP_ABI=$($abis -join ' ')", 'APP_PLATFORM=android-24', "NDK_LIBS_OUT=$($nativeLibsPath.Replace('\', '/'))", "NDK_OUT=$($nativeObjectsPath.Replace('\', '/'))", 'APP_CFLAGS=-O3 -DPKGNAME=com/v2ray/ang/service', 'APP_LDFLAGS=-Wl,--build-id=none -Wl,--hash-style=gnu')
    foreach ($abi in $abis) {
        $abiPath = Join-Path $libsPath $abi
        New-Item -ItemType Directory -Path $abiPath -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path $nativeLibsPath "$abi/libhev-socks5-tunnel.so") -Destination $abiPath -Force
        Copy-Item -LiteralPath (Join-Path $nativeLibsPath "$abi/hev-socks5-tunnel-bin") -Destination (Join-Path $abiPath 'libhevsockstun.so') -Force
    }

    $env:BNG_SIGNING_KEYSTORE = Join-Path $signingPath 'behradNG-release.jks'
    $env:APP_KEYSTORE_PASSWORD = [IO.File]::ReadAllText((Join-Path $signingPath 'APP_KEYSTORE_PASSWORD.txt')).Trim()
    $env:APP_KEYSTORE_ALIAS = [IO.File]::ReadAllText((Join-Path $signingPath 'APP_KEYSTORE_ALIAS.txt')).Trim()
    $env:APP_KEY_PASSWORD = [IO.File]::ReadAllText((Join-Path $signingPath 'APP_KEY_PASSWORD.txt')).Trim()
    $debugKeystore = Join-Path $projectPath 'debug.keystore'
    if (!(Test-Path -LiteralPath $debugKeystore)) {
        Run-Native $keytoolExe @('-genkeypair', '-keystore', $debugKeystore, '-alias', 'androiddebugkey', '-storepass', 'android', '-keypass', 'android', '-dname', 'CN=Android Debug,O=Android,C=US', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000')
    }
    Push-Location $projectPath
    try {
        Run-Native $wrapperBat @(':app:testDebugUnitTest', ':app:compileDebugKotlin', ':app:assembleRelease', '--stacktrace', '--no-daemon')
    } finally { Pop-Location }

    $apkPath = Join-Path $projectPath 'app/build/outputs/apk/release/app-release.apk'
    if (!(Test-Path -LiteralPath $apkPath)) { throw 'Expected release APK was not produced.' }
    Run-Native (Join-Path $sdkPath "build-tools/$buildToolsVersion/apksigner.bat") @('verify', '--verbose', '--min-sdk-version', '24', $apkPath)
    $badging = & (Join-Path $sdkPath "build-tools/$buildToolsVersion/aapt.exe") dump badging $apkPath
    if ($LASTEXITCODE -ne 0 -or !($badging -match "package: name='com.v2ray.ang.behradng'.*versionCode='753'.*versionName='1.5.0'")) { throw 'APK package or version does not match behradNG 1.5.0.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($apkPath)
    try {
        foreach ($abi in $abis) {
            foreach ($lib in @('libgojni.so', 'libhev-socks5-tunnel.so', 'libhevsockstun.so')) {
                $entry = $zip.GetEntry("lib/$abi/$lib")
                if ($null -eq $entry -or $entry.Length -eq 0) { throw "APK missing native library: $abi/$lib" }
            }
        }
    } finally { $zip.Dispose() }
    $outputPath = Join-Path $repoPath 'local-apks'
    New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
    $outputApk = Join-Path $outputPath 'behradNG-1.5.0-local.apk'
    Copy-Item -LiteralPath $apkPath -Destination $outputApk -Force
    Write-Output "Local APK built and checked: $outputApk"
    Get-FileHash -LiteralPath $outputApk -Algorithm SHA256 | Select-Object Hash
} finally {
    foreach ($name in $envNames) { [Environment]::SetEnvironmentVariable($name, $savedEnv[$name], 'Process') }
}
