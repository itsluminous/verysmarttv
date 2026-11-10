# VerySmartTV setup for Windows (PowerShell 5.1 or newer): finds Python 3.9+, makes a venv under
# %USERPROFILE%\.verysmarttv\venv with the one package the tool needs (setup\requirements.txt), and runs the
# Python package with your arguments.
#
#   powershell -ExecutionPolicy Bypass -File setup\setup.ps1                        # the guided flow
#   powershell -ExecutionPolicy Bypass -File setup\setup.ps1 --host 192.168.1.50
#   powershell -ExecutionPolicy Bypass -File setup\setup.ps1 install --apk-dir dist
#
# No Python? Install it from https://www.python.org/downloads/ (tick "Add python.exe to PATH") and run this again.
$ErrorActionPreference = "Stop"
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$Home_ = if ($env:VERYSMARTTV_HOME) { $env:VERYSMARTTV_HOME } else { Join-Path $env:USERPROFILE ".verysmarttv" }
$Venv = Join-Path $Home_ "venv"

function Find-Python {
    # Each candidate is "exe" or "exe|arg": the Windows launcher `py -3`, then python3, then python.
    foreach ($candidate in @("py|-3", "python3", "python")) {
        $parts = $candidate.Split("|")
        $exe = $parts[0]
        $extra = @($parts | Select-Object -Skip 1)
        if (-not (Get-Command $exe -ErrorAction SilentlyContinue)) { continue }
        try {
            & $exe @extra -c "import sys; sys.exit(0 if sys.version_info >= (3, 9) else 1)" 2>$null
            if ($LASTEXITCODE -eq 0) { return $candidate }
        } catch { }
    }
    return $null
}

function Fail($message) {
    [Console]::Error.WriteLine($message)
    exit 2
}

$Python = Find-Python
if (-not $Python) {
    Fail "VerySmartTV setup needs Python 3.9 or newer and found none. Install it from https://www.python.org/downloads/ (tick 'Add python.exe to PATH'), open a new PowerShell window and run this again."
}
$PyParts = $Python.Split("|")
$PyExe = $PyParts[0]
$PyArgs = @($PyParts | Select-Object -Skip 1)

$VenvPython = Join-Path $Venv "Scripts\python.exe"
if (-not (Test-Path $VenvPython)) {
    Write-Host "creating $Venv ..."
    New-Item -ItemType Directory -Force -Path $Home_ | Out-Null
    & $PyExe @PyArgs -m venv $Venv
    if ($LASTEXITCODE -ne 0) { Fail "could not create a venv under $Venv" }
}

$Requirements = Join-Path $Here "requirements.txt"
$Stamp = Join-Path $Venv ".requirements-stamp"
$Hash = (Get-FileHash -Algorithm SHA256 $Requirements).Hash
if (-not (Test-Path $Stamp) -or (Get-Content $Stamp -Raw).Trim() -ne $Hash) {
    & $VenvPython -m pip install --quiet --disable-pip-version-check -r $Requirements
    if ($LASTEXITCODE -ne 0) { Fail "pip install of setup\requirements.txt failed" }
    Set-Content -Path $Stamp -Value $Hash -NoNewline
}

$Commands = @("setup", "discover", "probe", "install", "theme", "debloat", "restore", "check", "screenshot", "adb-closed", "-h", "--help", "--version")
$Argv = @($args)
if ($Argv.Count -eq 0 -or $Commands -notcontains $Argv[0]) { $Argv = @("setup") + $Argv }
Push-Location $Here
try {
    & $VenvPython -m verysmarttv_setup @Argv
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
