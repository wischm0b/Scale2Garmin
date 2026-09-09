param([string]$JavaHome = 'C:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME = $JavaHome
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-home'
Push-Location $projectRoot
try {
    & '.\gradlew.bat' --no-daemon --max-workers=1 testDebugUnitTest assembleRelease lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build oder Prüfung fehlgeschlagen.' }
    New-Item -ItemType Directory -Force 'releases' | Out-Null
    $version = [regex]::Match((Get-Content -LiteralPath 'app\build.gradle' -Raw), 'versionName\s+[''"]([^''"]+)[''"]').Groups[1].Value
    if (-not $version) { throw 'App-Version konnte nicht gelesen werden.' }
    $apkName = "Scale2Garmin-$version.apk"
    Copy-Item -LiteralPath 'app\build\outputs\apk\release\app-release.apk' -Destination (Join-Path 'releases' $apkName)
    Get-ChildItem -LiteralPath 'releases' -Filter '*.apk' | Sort-Object Name | ForEach-Object {
        $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLower()
        "$hash  $($_.Name)"
    } | Set-Content -LiteralPath 'releases\SHA256SUMS.txt'
} finally { Pop-Location }
