param(
  [ValidateSet(48, 96, 192)][int]$RenderDistance = 48,
  [int]$WaitSeconds = 12,
  [int]$MoveForwardSeconds = 0,
  [string]$ReportPath,
  [switch]$Launch,
  [switch]$Restart,
  [switch]$QuitAfterCapture,
  [switch]$CaptureSeries,
  [switch]$KeepOpen
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$launcherPath = Join-Path $PSScriptRoot "launch_desktop.vbs"
$toolsDir = Join-Path $repoRoot "..\\pixel-survival-tools"
$javaPath = Join-Path $toolsDir "jdk-21.0.10+7\\bin\\java.exe"
$jarPath = Join-Path $repoRoot "build\\libs\\pixel-survival-desktop.jar"
$screenshotDir = Join-Path $repoRoot "screenshots"
$windowTitle = "Pixel Survival"
$renderDistanceProperty = "pixelSurvival.renderDistanceChunks"
$smokeModeProperty = "pixelSurvival.smokeMode"
$smokeMoveForwardSecondsProperty = "pixelSurvival.smokeMoveForwardSeconds"
$smokeReportPathProperty = "pixelSurvival.smokeReportPath"
$smokeScreenshotScheduleProperty = "pixelSurvival.smokeScreenshotScheduleSeconds"
$smokeQuitAfterScreenshotsProperty = "pixelSurvival.smokeQuitAfterScreenshots"

Add-Type -AssemblyName Microsoft.VisualBasic
Add-Type -AssemblyName System.Windows.Forms

Add-Type @"
using System;
using System.Runtime.InteropServices;

public static class PixelSurvivalSmokeNative {
  [StructLayout(LayoutKind.Sequential)]
  public struct POINT {
    public int X;
    public int Y;
  }

  [StructLayout(LayoutKind.Sequential)]
  public struct RECT {
    public int Left;
    public int Top;
    public int Right;
    public int Bottom;
  }

  [DllImport("user32.dll")]
  public static extern bool SetForegroundWindow(IntPtr hWnd);

  [DllImport("user32.dll")]
  public static extern bool ShowWindowAsync(IntPtr hWnd, int nCmdShow);

  [DllImport("user32.dll")]
  public static extern bool BringWindowToTop(IntPtr hWnd);

  [DllImport("user32.dll")]
  public static extern bool GetClientRect(IntPtr hWnd, out RECT rect);

  [DllImport("user32.dll")]
  public static extern bool ClientToScreen(IntPtr hWnd, ref POINT point);

  [DllImport("user32.dll")]
  public static extern void mouse_event(uint dwFlags, uint dx, uint dy, uint dwData, UIntPtr dwExtraInfo);
}
"@

$MOUSEEVENTF_LEFTDOWN = 0x0002
$MOUSEEVENTF_LEFTUP = 0x0004

function Wait-ForWindow {
  param(
    [int]$ProcessId = 0,
    [string]$Title,
    [int]$TimeoutSeconds = 45
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    if ($ProcessId -gt 0) {
      $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
      if ($null -ne $process -and $process.MainWindowHandle -ne 0) {
        return $process
      }
    }
    $process = Get-Process | Where-Object { $_.MainWindowTitle -eq $Title } | Select-Object -First 1
    if ($null -ne $process -and $process.MainWindowHandle -ne 0) {
      return $process
    }
    Start-Sleep -Milliseconds 250
  }

  throw "Timed out waiting for window '$Title'"
}

function Get-PixelSurvivalProcess {
  param([int]$ProcessId = 0)

  if ($ProcessId -gt 0) {
    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($null -ne $process -and $process.MainWindowHandle -ne 0) {
      return $process
    }
  }

  Get-Process | Where-Object { $_.MainWindowTitle -eq $windowTitle -and $_.MainWindowHandle -ne 0 } | Select-Object -First 1
}

function Stop-PixelSurvivalIfRunning {
  $runningProcesses = @(
    Get-CimInstance Win32_Process | Where-Object {
      $_.CommandLine -like "*pixel-survival-desktop.jar*"
    }
  )
  foreach ($runningProcess in $runningProcesses) {
    Stop-Process -Id $runningProcess.ProcessId -Force -ErrorAction SilentlyContinue
  }
  if ($runningProcesses.Count -gt 0) {
    Start-Sleep -Milliseconds 500
  }
}

function Focus-Window {
  param([System.Diagnostics.Process]$Process)

  [PixelSurvivalSmokeNative]::ShowWindowAsync($Process.MainWindowHandle, 5) | Out-Null
  [PixelSurvivalSmokeNative]::BringWindowToTop($Process.MainWindowHandle) | Out-Null
  [PixelSurvivalSmokeNative]::SetForegroundWindow($Process.MainWindowHandle) | Out-Null
  try {
    [Microsoft.VisualBasic.Interaction]::AppActivate($Process.Id) | Out-Null
  } catch {
    Start-Sleep -Milliseconds 150
  }
  Start-Sleep -Milliseconds 200
}

function Get-ClientOrigin {
  param([System.Diagnostics.Process]$Process)

  if ($null -eq $Process) {
    throw "Pixel Survival process is not available"
  }

  $processId = $Process.Id
  $deadline = (Get-Date).AddSeconds(3)
  while ((Get-Date) -lt $deadline) {
    $process = Get-PixelSurvivalProcess -ProcessId $processId
    if ($null -eq $process) {
      Start-Sleep -Milliseconds 150
      continue
    }

    $clientRect = New-Object PixelSurvivalSmokeNative+RECT
    if (-not [PixelSurvivalSmokeNative]::GetClientRect($process.MainWindowHandle, [ref]$clientRect)) {
      Start-Sleep -Milliseconds 150
      continue
    }
    $width = $clientRect.Right - $clientRect.Left
    $height = $clientRect.Bottom - $clientRect.Top
    if ($width -le 0 -or $height -le 0) {
      Start-Sleep -Milliseconds 150
      continue
    }

    $origin = New-Object PixelSurvivalSmokeNative+POINT
    if (-not [PixelSurvivalSmokeNative]::ClientToScreen($process.MainWindowHandle, [ref]$origin)) {
      Start-Sleep -Milliseconds 150
      continue
    }

    return @{
      X = $origin.X
      Y = $origin.Y
      Width = $width
      Height = $height
    }
  }

  throw "Failed to get client rect for Pixel Survival"
}

function Click-ClientPoint {
  param(
    [System.Diagnostics.Process]$Process,
    [double]$ClientX,
    [double]$ClientYFromBottom
  )

  $client = Get-ClientOrigin -Process $Process
  $screenX = [int]([Math]::Round($client.X + $ClientX))
  $screenY = [int]([Math]::Round($client.Y + ($client.Height - $ClientYFromBottom)))
  [System.Windows.Forms.Cursor]::Position = New-Object System.Drawing.Point($screenX, $screenY)
  Start-Sleep -Milliseconds 125
  [PixelSurvivalSmokeNative]::mouse_event($MOUSEEVENTF_LEFTDOWN, 0, 0, 0, [UIntPtr]::Zero)
  Start-Sleep -Milliseconds 50
  [PixelSurvivalSmokeNative]::mouse_event($MOUSEEVENTF_LEFTUP, 0, 0, 0, [UIntPtr]::Zero)
  Start-Sleep -Milliseconds 250
}

function Resume-GameIfPauseMenuIsOpen {
  param([System.Diagnostics.Process]$Process)

  $client = Get-ClientOrigin -Process $Process
  $centerX = $client.Width * 0.5
  $topY = $client.Height * 0.68
  Click-ClientPoint -Process $Process -ClientX $centerX -ClientYFromBottom ($topY + 18)
}

function Build-PixelSurvival {
  if (-not (Test-Path $launcherPath)) {
    throw "Launcher not found at $launcherPath"
  }
  & "C:\Windows\System32\wscript.exe" $launcherPath /buildonly
}

function Get-ScreenshotPaths {
  if (-not (Test-Path $screenshotDir)) {
    return @()
  }
  return @(
    Get-ChildItem $screenshotDir -Filter "pixel-survival-*.png" |
      Sort-Object LastWriteTime |
      Select-Object -ExpandProperty FullName
  )
}

function Launch-PixelSurvival {
  param(
    [int]$TargetRenderDistance,
    [int]$MoveSeconds,
    [string]$BenchmarkReportPath,
    [string]$ScreenshotSchedule,
    [bool]$QuitAfterScheduledScreenshots
  )

  if (-not (Test-Path $javaPath)) {
    throw "java.exe not found at $javaPath"
  }
  if (-not (Test-Path $jarPath)) {
    throw "Desktop jar not found at $jarPath"
  }

  Build-PixelSurvival
  $arguments = @(
    "-Dfile.encoding=UTF-8",
    "-D$smokeModeProperty=true",
    "-D$renderDistanceProperty=$TargetRenderDistance",
    "-D$smokeScreenshotScheduleProperty=$ScreenshotSchedule",
    "-jar",
    $jarPath
  )
  if ($MoveSeconds -gt 0) {
    $arguments = @("-D$smokeMoveForwardSecondsProperty=$MoveSeconds") + $arguments
  }
  if (-not [string]::IsNullOrWhiteSpace($BenchmarkReportPath)) {
    $arguments = @("-D$smokeReportPathProperty=$BenchmarkReportPath") + $arguments
  }
  if ($QuitAfterScheduledScreenshots) {
    $arguments = @("-D$smokeQuitAfterScreenshotsProperty=true") + $arguments
  }
  return Start-Process -FilePath $javaPath -WorkingDirectory $repoRoot -ArgumentList $arguments -WindowStyle Hidden -PassThru
}

if ($Restart) {
  Stop-PixelSurvivalIfRunning
  $Launch = $true
}

$baselineScreenshots = Get-ScreenshotPaths
$finalCaptureSeconds = [Math]::Max(1, $WaitSeconds)
$screenshotSchedule = if ($CaptureSeries -and $finalCaptureSeconds -gt 1) {
  "1,$finalCaptureSeconds"
} else {
  "$finalCaptureSeconds"
}
$quitAfterScheduledScreenshots = $QuitAfterCapture -and (-not $KeepOpen)

$launchedProcessId = 0
$existingProcess = Get-PixelSurvivalProcess
if ($Launch -or $null -eq $existingProcess) {
  $launchedProcess = Launch-PixelSurvival `
    -TargetRenderDistance $RenderDistance `
    -MoveSeconds $MoveForwardSeconds `
    -BenchmarkReportPath $ReportPath `
    -ScreenshotSchedule $screenshotSchedule `
    -QuitAfterScheduledScreenshots $quitAfterScheduledScreenshots
  $launchedProcessId = $launchedProcess.Id
}

$process = Wait-ForWindow -ProcessId $launchedProcessId -Title $windowTitle
Focus-Window -Process $process

if ($quitAfterScheduledScreenshots -and $launchedProcessId -gt 0) {
  $launchedProcess = Get-Process -Id $launchedProcessId -ErrorAction SilentlyContinue
  if ($null -ne $launchedProcess) {
    Wait-Process -Id $launchedProcessId -Timeout ([Math]::Max(20, $finalCaptureSeconds + $MoveForwardSeconds + 20))
  }
} else {
  Start-Sleep -Seconds ($finalCaptureSeconds + 2)
}

if (-not $KeepOpen -and $QuitAfterCapture -and $launchedProcessId -eq 0) {
  $process = Get-PixelSurvivalProcess -ProcessId $launchedProcessId
  if ($null -ne $process) {
    Stop-Process -Id $process.Id -Force
  }
}

Start-Sleep -Milliseconds 500

if (-not [string]::IsNullOrWhiteSpace($ReportPath)) {
  Write-Output $ReportPath
}

$captures = Get-ScreenshotPaths | Where-Object { $_ -notin $baselineScreenshots }
foreach ($capture in $captures) {
  Write-Output $capture
}
