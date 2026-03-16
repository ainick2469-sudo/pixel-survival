param(
  [ValidateSet(48, 96, 192)][int]$RenderDistance = 48,
  [int]$WaitSeconds = 12,
  [switch]$Launch,
  [switch]$Restart,
  [switch]$QuitAfterCapture
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$launcherPath = Join-Path $PSScriptRoot "launch_desktop.vbs"
$toolsDir = Join-Path $repoRoot "..\\pixel-survival-tools"
$javaPath = Join-Path $toolsDir "jdk-21.0.10+7\\bin\\java.exe"
$jarPath = Join-Path $repoRoot "build\\libs\\pixel-survival-desktop.jar"
$screenshotScript = "C:\Users\nickb\.codex\skills\screenshot\scripts\take_screenshot.ps1"
$windowTitle = "Pixel Survival"
$renderDistanceProperty = "pixelSurvival.renderDistanceChunks"
$smokeModeProperty = "pixelSurvival.smokeMode"

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

  $clientRect = New-Object PixelSurvivalSmokeNative+RECT
  if (-not [PixelSurvivalSmokeNative]::GetClientRect($Process.MainWindowHandle, [ref]$clientRect)) {
    throw "Failed to get client rect for Pixel Survival"
  }

  $origin = New-Object PixelSurvivalSmokeNative+POINT
  if (-not [PixelSurvivalSmokeNative]::ClientToScreen($Process.MainWindowHandle, [ref]$origin)) {
    throw "Failed to resolve client origin for Pixel Survival"
  }

  return @{
    X = $origin.X
    Y = $origin.Y
    Width = $clientRect.Right - $clientRect.Left
    Height = $clientRect.Bottom - $clientRect.Top
  }
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

function Launch-PixelSurvival {
  param([int]$TargetRenderDistance)

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
    "-jar",
    $jarPath
  )
  return Start-Process -FilePath $javaPath -WorkingDirectory $repoRoot -ArgumentList $arguments -WindowStyle Hidden -PassThru
}

function Capture-Window {
  param([System.Diagnostics.Process]$Process)

  & "C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe" `
    -ExecutionPolicy Bypass `
    -File $screenshotScript `
    -Mode temp `
    -WindowHandle $Process.MainWindowHandle
}

if ($Restart) {
  Stop-PixelSurvivalIfRunning
  $Launch = $true
}

$launchedProcessId = 0
$existingProcess = Get-PixelSurvivalProcess
if ($Launch -or $null -eq $existingProcess) {
  $launchedProcess = Launch-PixelSurvival -TargetRenderDistance $RenderDistance
  $launchedProcessId = $launchedProcess.Id
}

$process = Wait-ForWindow -ProcessId $launchedProcessId -Title $windowTitle
Focus-Window -Process $process
Start-Sleep -Seconds $WaitSeconds
$process = Wait-ForWindow -ProcessId $launchedProcessId -Title $windowTitle -TimeoutSeconds 10
Focus-Window -Process $process
Resume-GameIfPauseMenuIsOpen -Process $process
$screenshotPath = Capture-Window -Process $process

if ($QuitAfterCapture) {
  $process = Get-PixelSurvivalProcess -ProcessId $launchedProcessId
  Stop-Process -Id $process.Id -Force
}

Write-Output $screenshotPath
