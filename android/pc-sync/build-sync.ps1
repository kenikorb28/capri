# Builds CapriSync.exe with the C# compiler that ships with Windows (.NET Framework 4.x), the same way Capri is built.
# Usage, in PowerShell from this folder:   .\build-sync.ps1        then   .\CapriSync.exe --selftest
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$csc = Join-Path $env:WINDIR "Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if (-not (Test-Path $csc)) { $csc = Join-Path $env:WINDIR "Microsoft.NET\Framework\v4.0.30319\csc.exe" }
if (-not (Test-Path $csc)) { throw "csc.exe not found: install or enable .NET Framework 4.8 (it is part of Windows 10 and 11)." }

$refs = @("System.dll", "System.Core.dll", "System.Drawing.dll", "System.Windows.Forms.dll", "System.Security.dll", "System.Web.Extensions.dll") | ForEach-Object { "/r:$_" }
& $csc /nologo /target:winexe /optimize+ /langversion:5 /codepage:65001 "/out:$here\CapriSync.exe" $refs "$here\CapriSync.cs"
if ($LASTEXITCODE -ne 0) { throw "Build failed (exit $LASTEXITCODE)." }

# The self-test needs a console, so it is run through a console copy of the same code.
& $csc /nologo /target:exe /optimize+ /langversion:5 /codepage:65001 "/out:$here\CapriSync-selftest.exe" $refs "$here\CapriSync.cs"
if ($LASTEXITCODE -ne 0) { throw "Self-test build failed (exit $LASTEXITCODE)." }
& "$here\CapriSync-selftest.exe" --selftest
if ($LASTEXITCODE -ne 0) { throw "Self-test failed: the PC's lock doesn't match the phone's." }
Write-Host "CapriSync.exe is ready. Start it, then pair from Capri on your phone (Settings > PC SYNC)."
