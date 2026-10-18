# Finishes a Windows update after Dari has quit: runs the staged MSI silently, then relaunches Dari.
# The MSI rolls itself back on failure, so the installed version stays usable either way.
#   dari-finish-update.ps1 -AppPid <old pid> -Target <install folder> -Workspace <workspace>
param(
    [Parameter(Mandatory = $true)][int]$AppPid,
    [Parameter(Mandatory = $true)][string]$Target,
    [Parameter(Mandatory = $true)][string]$Workspace
)
$ErrorActionPreference = 'Stop'

# Tests replace msiexec and the launcher so they don't install or start anything.
$msiexec = if ($env:DARI_UPDATE_MSIEXEC) { $env:DARI_UPDATE_MSIEXEC } else { Join-Path $env:SystemRoot 'System32\msiexec.exe' }
$launcher = if ($env:DARI_UPDATE_OPEN) { $env:DARI_UPDATE_OPEN } else { Join-Path $Target 'Dari.exe' }
$msi = Join-Path $Workspace 'Dari.msi'

function Start-Dari {
    try {
        Start-Process -FilePath $launcher | Out-Null
        return $true
    } catch {
        Write-Output "Dari could not be started: $_"
        return $false
    }
}

if (-not (Split-Path -Leaf $Workspace).StartsWith('.dari-update-')) {
    Write-Output "Invalid update workspace: $Workspace"
    exit 2
}
if ((Split-Path -Parent $Workspace) -ne (Split-Path -Parent $Target) -or -not (Test-Path -LiteralPath $msi -PathType Leaf)) {
    Write-Output 'Invalid update arguments.'
    exit 2
}

# Wait up to 30 seconds for Dari to exit: the MSI stops on dari.lock while it is still running.
$process = Get-Process -Id $AppPid -ErrorAction SilentlyContinue
if ($process -and -not $process.WaitForExit(30000)) {
    Write-Output 'Dari did not exit within 30 seconds. Nothing was installed.'
    exit 1
}

$log = Join-Path $Workspace 'msiexec.log'
$install = Start-Process -FilePath $msiexec -ArgumentList @('/i', "`"$msi`"", '/qn', '/norestart', '/l*v', "`"$log`"") -Wait -PassThru
# 3010 means success with a reboot requested, which a per-user Dari never needs.
if ($install.ExitCode -ne 0 -and $install.ExitCode -ne 3010) {
    Start-Dari | Out-Null
    Write-Output "The update could not be installed (msiexec exit code $($install.ExitCode)). See $log."
    exit 1
}
if (-not (Start-Dari)) {
    exit 1
}
Remove-Item -LiteralPath $Workspace -Recurse -Force
