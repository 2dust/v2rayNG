<# :
@echo off
REM Set console to UTF-8 for proper logging in Gradle
chcp 65001 >nul

REM Execute the embedded PowerShell script silently
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Command -ScriptBlock ([ScriptBlock]::Create((Get-Content '%~f0' -Raw)))"

REM Exit with success code for Gradle/CMake
exit /b 0
#>

# --- PowerShell Script Starts Here ---
$files = Get-ChildItem -Path . -Filter *.h -Recurse
$modifiedFiles = 0

foreach ($f in $files) {
    # Read file line by line into an array
    $lines = @(Get-Content $f.FullName)
    $isModified = $false

    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]

        # Match lines that only contain a path or an unquoted include
        if ($line -match '^([ \t]*)(?:#include[ \t]+)?([a-zA-Z0-9_.\-]+[/\\][a-zA-Z0-9_.\-/\\]+\.h)[ \t]*$') {
            $indent = $matches[1]
            $path = $matches[2]

            # Replace the line with the properly quoted include
            $lines[$i] = "$indent#include `"$path`""
            $isModified = $true
        }
    }

    if ($isModified) {
        # Write back to file with UTF8 No BOM encoding (crucial for GCC/Clang/NDK)
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        [IO.File]::WriteAllLines($f.FullName, $lines, $utf8NoBom)

        # Print a simple log for Android Studio build console
        Write-Host "Auto-fixed header path in: $($f.Name)"
        $modifiedFiles++
    }
}

if ($modifiedFiles -gt 0) {
    Write-Host "Fixed $modifiedFiles header files successfully."
}