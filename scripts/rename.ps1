<#
.SYNOPSIS
    Renames this template to a new app: package/namespace, applicationId, Gradle project
    name, app label and (optionally) the AdMob App ID.

.DESCRIPTION
    Run this FIRST, on a fresh copy of the template, before writing any feature code and
    before the app exists anywhere else. applicationId is permanent on Google Play once
    the first build is uploaded, and Firebase/AdMob/Play products are all keyed to it -
    renaming after any of those exist means recreating them.

    What it touches:
      * app/src/{main,test,androidTest,debug}/java/<old path>  -> <new path>
      * every `package` / `import` line and any other occurrence of the old package
        in .kt, .kts, .pro, .xml, .json, .md files (this is what keeps the Konsist
        architecture test's hard-coded package strings working)
      * namespace + applicationId in app/build.gradle.kts
      * rootProject.name in settings.gradle.kts
      * the exported Room schema directory name
      * app_name in res/values/strings.xml   (-AppName)
      * the AdMob APPLICATION_ID in AndroidManifest.xml  (-AdmobAppId)

    Subscription product IDs are derived from applicationId at runtime
    (BillingConfig), so they need no edit here - just create products with the
    matching IDs in Play Console.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File ./scripts/rename.ps1 -Package io.aetherstudios.invoice.maker -AppName "Invoice Maker"

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File ./scripts/rename.ps1 -Package io.aetherstudios.invoice.maker `
        -AppName "Invoice Maker" -AdmobAppId "ca-app-pub-1234567890123456~1234567890"
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$')]
    [string] $Package,

    [string] $AppName,

    [string] $AdmobAppId,

    # Skip the confirmation prompt (for scripted use).
    [switch] $Force
)

$ErrorActionPreference = 'Stop'

# Set-Content -Encoding utf8 writes a BOM on Windows PowerShell 5.1; source files must
# not gain one, so every write goes through this.
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
function Write-TextFile([string] $Path, [string] $Text) {
    [System.IO.File]::WriteAllText($Path, $Text, $utf8NoBom)
}
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

# ---- current package, read from the build file (never hard-coded) -------------------
$buildFile = Join-Path $repoRoot 'app/build.gradle.kts'
if (-not (Test-Path $buildFile)) { throw "app/build.gradle.kts not found - run this from the repo." }
$buildText = Get-Content $buildFile -Raw -Encoding UTF8
$match = [regex]::Match($buildText, 'namespace\s*=\s*"([^"]+)"')
if (-not $match.Success) { throw 'Could not read the namespace from app/build.gradle.kts.' }
$oldPackage = $match.Groups[1].Value

if ($oldPackage -eq $Package) { throw "Package is already $Package - nothing to do." }

Write-Host "Rename: $oldPackage  ->  $Package"
if ($AppName)    { Write-Host "App name:  $AppName" }
if ($AdmobAppId) { Write-Host "AdMob App ID: $AdmobAppId" }
if (-not $Force) {
    $answer = Read-Host "Proceed? (y/N)"
    if ($answer -ne 'y') { Write-Host 'Aborted.'; return }
}

$oldPath = $oldPackage -replace '\.', '/'
$newPath = $Package    -replace '\.', '/'

# ---- 1. move the source trees --------------------------------------------------------
foreach ($sourceSet in @('main', 'test', 'androidTest', 'debug')) {
    $from = Join-Path $repoRoot "app/src/$sourceSet/java/$oldPath"
    if (-not (Test-Path $from)) { continue }
    $to = Join-Path $repoRoot "app/src/$sourceSet/java/$newPath"
    New-Item -ItemType Directory -Force (Split-Path -Parent $to) | Out-Null
    Move-Item -Path $from -Destination $to
    Write-Host "  moved app/src/$sourceSet/java/$oldPath"

    # Drop the now-empty old package directories (e.g. app/letostudio/iptv).
    $cursor = Join-Path $repoRoot "app/src/$sourceSet/java"
    $segments = $oldPath -split '/'
    $dirs = @()
    foreach ($segment in $segments) { $cursor = Join-Path $cursor $segment; $dirs += $cursor }
    [array]::Reverse($dirs)
    foreach ($dir in $dirs) {
        if ((Test-Path $dir) -and -not (Get-ChildItem $dir -Force)) { Remove-Item $dir }
    }
}

# ---- 2. rewrite the package string everywhere ---------------------------------------
$extensions = @('*.kt', '*.kts', '*.pro', '*.xml', '*.json', '*.md', '*.ps1')
$skip = @('\\build\\', '\\.gradle\\', '\\.git\\', '\\.idea\\', '\\.kotlin\\')
$changed = 0
foreach ($ext in $extensions) {
    Get-ChildItem -Path $repoRoot -Filter $ext -Recurse -File | ForEach-Object {
        $full = $_.FullName
        foreach ($pattern in $skip) { if ($full -match $pattern) { return } }
        # -Encoding UTF8 is REQUIRED: Windows PowerShell 5.1 reads BOM-less files as ANSI,
        # which turns every non-ASCII character in the repo into mojibake on rewrite.
        $text = Get-Content $full -Raw -Encoding UTF8
        if ($text -and $text.Contains($oldPackage)) {
            $text = $text.Replace($oldPackage, $Package)
            Write-TextFile $full $text
            $changed++
        }
    }
}
Write-Host "  rewrote the package string in $changed files"

# ---- 3. exported Room schema directory ----------------------------------------------
$schemas = Join-Path $repoRoot 'app/schemas'
if (Test-Path $schemas) {
    Get-ChildItem $schemas -Directory | Where-Object { $_.Name.StartsWith($oldPackage) } | ForEach-Object {
        $newName = $_.Name.Replace($oldPackage, $Package)
        Rename-Item $_.FullName $newName
        Write-Host "  schema dir -> $newName"
    }
}

# ---- 4. Gradle project name ----------------------------------------------------------
$settingsFile = Join-Path $repoRoot 'settings.gradle.kts'
$projectName = ($Package -split '\.')[-1]
$settingsText = Get-Content $settingsFile -Raw -Encoding UTF8
$settingsText = [regex]::Replace($settingsText, 'rootProject\.name\s*=\s*"[^"]*"', "rootProject.name = `"$projectName`"")
Write-TextFile $settingsFile $settingsText

# ---- 5. app label --------------------------------------------------------------------
if ($AppName) {
    $stringsFile = Join-Path $repoRoot 'app/src/main/res/values/strings.xml'
    $stringsText = Get-Content $stringsFile -Raw -Encoding UTF8
    $escaped = [System.Security.SecurityElement]::Escape($AppName)
    $stringsText = [regex]::Replace(
        $stringsText,
        '<string name="app_name">[^<]*</string>',
        "<string name=`"app_name`">$escaped</string>")
    Write-TextFile $stringsFile $stringsText
}

# ---- 6. AdMob App ID ------------------------------------------------------------------
if ($AdmobAppId) {
    if ($AdmobAppId -notmatch '^ca-app-pub-\d+~\d+$') {
        throw "AdmobAppId must look like ca-app-pub-0000000000000000~0000000000"
    }
    $manifestFile = Join-Path $repoRoot 'app/src/main/AndroidManifest.xml'
    $manifestText = Get-Content $manifestFile -Raw -Encoding UTF8
    $manifestText = [regex]::Replace(
        $manifestText,
        '(?<=com\.google\.android\.gms\.ads\.APPLICATION_ID"\s*\r?\n\s*android:value=")[^"]+',
        $AdmobAppId)
    Write-TextFile $manifestFile $manifestText
}

Write-Host ''
Write-Host 'Done. Still to do by hand (see TEMPLATE.md):'
Write-Host '  1. app/google-services.json      - download for THIS applicationId'
Write-Host '  2. ads/AdsConfig.kt PROD_* units - create in the AdMob console'
Write-Host '  3. BillingConfig legal URLs      - real Privacy Policy + Terms'
Write-Host '  4. Play Console products         - IDs derive from applicationId'
Write-Host '  5. Launcher icon + onboarding/paywall art (res/drawable placeholders)'
Write-Host ''
Write-Host 'Then: ./gradlew :app:compileDebugKotlin  then  ./gradlew test'
