[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$NdkPath,
    [Parameter(Mandatory=$true)][string]$AppPath
)
$ErrorActionPreference = 'Stop'
$lock = Get-Content (Join-Path $PSScriptRoot 'aether-core.lock.json') -Raw | ConvertFrom-Json
$downloadPath = Join-Path $PSScriptRoot '.aether-native-downloads'
New-Item -ItemType Directory -Path $downloadPath -Force | Out-Null
foreach ($asset in $lock.assets) {
    $archive = Join-Path $downloadPath $asset.archive
    if (!(Test-Path -LiteralPath $archive) -or (Get-FileHash -LiteralPath $archive).Hash.ToLowerInvariant() -ne $asset.sha256) {
        & curl.exe --fail --location --connect-timeout 10 "https://github.com/$($lock.repository)/releases/download/$($lock.version)/$($asset.archive)" --output $archive
        if ($LASTEXITCODE -ne 0) { throw "Aether download failed: $($asset.abi)" }
    }
    if ((Get-FileHash -LiteralPath $archive).Hash.ToLowerInvariant() -ne $asset.sha256) { throw "Aether checksum mismatch: $($asset.abi)" }
    $extract = Join-Path $downloadPath $asset.abi
    $output = Join-Path $AppPath "libs/$($asset.abi)"
    New-Item -ItemType Directory -Path $extract,$output -Force | Out-Null
    & tar.exe -xzf $archive -C $extract aether
    if ($LASTEXITCODE -ne 0) { throw 'Aether extraction failed' }
    Copy-Item -LiteralPath (Join-Path $extract 'aether') -Destination (Join-Path $output 'libaether-core.so') -Force
    $clang = Join-Path $NdkPath "toolchains/llvm/prebuilt/windows-x86_64/bin/$($asset.target)$($lock.minSdk)-clang.cmd"
    & $clang -O2 -fPIE -pie '-Wl,-z,max-page-size=16384' '-Wl,-z,common-page-size=16384' (Join-Path $PSScriptRoot 'native/aether-launcher.c') -o (Join-Path $output 'libaether-launcher.so')
    if ($LASTEXITCODE -ne 0) { throw "Aether launcher build failed: $($asset.abi)" }
}
$licenses = Join-Path $AppPath 'src/main/assets/third_party'
New-Item -ItemType Directory -Path $licenses -Force | Out-Null
& curl.exe --fail --location "https://raw.githubusercontent.com/$($lock.repository)/$($lock.sourceRevision)/LICENSE" --output (Join-Path $licenses 'AETHER_LICENSE.txt')
if ($LASTEXITCODE -ne 0) { throw 'Aether license download failed' }
Write-Output 'Pinned Aether core and launcher prepared for ARM64, ARMv7, and x86_64.'
