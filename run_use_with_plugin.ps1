# run_use_with_plugin.ps1
# Đặt file này trong D:\workspaces\use

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
$useDTDL = Join-Path $projectRoot "use-dtdl"
$assembly = Join-Path $projectRoot "use-assembly"

$pluginJar = Join-Path $useDTDL "target\use-dtdl-7.1.1.jar"
$pluginDest = Join-Path $assembly "src\main\resources\plugins"
$zipFile = Join-Path $assembly "target\use-7.1.1.zip"
$extractFolder = Join-Path $assembly "target\use-7.1.1"

Write-Host ">>> Build use-dtdl..." -ForegroundColor Cyan
cd $projectRoot
mvn clean package -pl use-dtdl -am

Write-Host ">>> Copy use-dtdl-7.1.1.jar to plugins..." -ForegroundColor Cyan
Copy-Item -Path $pluginJar -Destination $pluginDest -Force

Write-Host ">>> Build use-assembly..." -ForegroundColor Cyan
mvn package -pl use-assembly

Write-Host ">>> Extract use-7.1.1.zip..." -ForegroundColor Cyan
if (Test-Path $extractFolder) {
    Remove-Item $extractFolder -Recurse -Force
}
Expand-Archive -Path $zipFile -DestinationPath $extractFolder -Force

Write-Host ">>> Looking for file use.bat..." -ForegroundColor Cyan
$useBat = Get-ChildItem -Path $extractFolder -Recurse -Filter "use.bat" | Select-Object -First 1

if (-not $useBat) {
    Write-Host "⚠️  use.bat not found in folder $extractFolder" -ForegroundColor Red
    exit 1
}

$useBatPath = $useBat.FullName
$workingDir = Split-Path $useBatPath

Write-Host ">>> Located file use.bat at: $useBatPath" -ForegroundColor Green
Write-Host ">>> Run use.bat..." -ForegroundColor Cyan
Start-Process -FilePath $useBatPath -WorkingDirectory $workingDir

Write-Host ">>> Finish!" -ForegroundColor Green
