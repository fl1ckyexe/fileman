$ErrorActionPreference = "Stop"

$Release = $false
$AppImage = $false

if ($args -contains "-Release") { $Release = $true }
if ($args -contains "-AppImage") { $AppImage = $true }

if (-not (Test-Path ".\pom.xml")) {
  throw "Run this script from the project root (where pom.xml is)."
}

$name = "FileMan"
$mainClass = "org.example.ftp.fileman.Launcher"
$icon = "src\main\resources\ftp.ico"
$inputDir = "target"
$destDir = "dist-jpackage"

$mvn = Get-Command mvn -ErrorAction SilentlyContinue

if ($mvn) {
  Write-Host "==> Building jar with Maven (skip tests)"
  mvn -DskipTests clean package
  if ($LASTEXITCODE -ne 0) {
    throw "Maven build failed with exit code $LASTEXITCODE"
  }
} else {
  Write-Host "==> Maven not found in PATH - using existing jar (if present)."
}

$jar = Get-ChildItem -Path "$inputDir\fileman-*.jar" |
  Where-Object { $_.Name -notlike "original-*" } |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1

if (-not $jar) {
  throw "No jar found in '$inputDir'. Install Maven (or add mvn to PATH) and re-run, or build the jar from your IDE."
}

Write-Host "==> Using jar: $($jar.Name)"

$appVersion = "1.0.0"
if ($mvn) {
  try {
    $pomVersion = (mvn -q -DforceStdout "help:evaluate -Dexpression=project.version" 2>$null | Select-Object -Last 1).Trim()
    $candidate = ($pomVersion -replace "-SNAPSHOT$", "")
    if ($candidate -match "^\d+(\.\d+){0,3}$") { 
      $appVersion = $candidate 
    }
  } catch {
  }
}

if (-not ($appVersion -match "^\d+(\.\d+){0,3}$")) {
  if ($jar.BaseName -match "^fileman-(.+)$") {
    $candidate = ($Matches[1] -replace "-SNAPSHOT$", "")
    if ($candidate -match "^\d+(\.\d+){0,3}$") { 
      $appVersion = $candidate 
    }
  }
}

if (Test-Path $destDir) {
  try {
    Remove-Item -Path "$destDir\*" -Recurse -Force -ErrorAction SilentlyContinue
  } catch {
  }
}
if (-not (Test-Path $destDir)) {
  New-Item -ItemType Directory -Force -Path $destDir | Out-Null
}

$buildType = if ($AppImage) { "app-image" } elseif ($Release) { "exe" } else { "app-image" }
$withConsole = -not $Release

Write-Host "==> Building $buildType with jpackage..."

$jpackageArgs = @(
  "--type", $buildType,
  "--dest", $destDir,
  "--name", $name,
  "--input", $inputDir,
  "--main-jar", $jar.Name,
  "--main-class", $mainClass,
  "--app-version", $appVersion,
  "--vendor", "org.example.ftp"
)

if (Test-Path $icon) {
  $jpackageArgs += @("--icon", $icon)
}

if ($buildType -eq "exe") {
  $jpackageArgs += @(
    "--win-menu",
    "--win-shortcut",
    "--win-dir-chooser"
  )
}

if ($withConsole) {
  $jpackageArgs += @("--win-console")
}

$jpackageArgs += @("--java-options", "-Dfile.encoding=UTF-8")

Write-Host "==> Running: jpackage $($jpackageArgs -join ' ')"

jpackage @jpackageArgs

if ($LASTEXITCODE -ne 0) {
  throw "jpackage failed with exit code $LASTEXITCODE"
}

Write-Host ""
if ($buildType -eq "app-image") {
  Write-Host "Done: $destDir\$name\$name.exe"
  Write-Host "Run it from: $destDir\$name\$name.exe"
} else {
  Write-Host "Done: $destDir\${name}-${appVersion}.exe"
  Write-Host "Installer: $destDir\${name}-${appVersion}.exe"
}
