# Moves the dev Minecraft client window (java.exe with KnotClient in its command line) onto the non-primary
# monitor without activating it, then hands focus back to whatever window had it. Start it just before runClient.
# Usage: powershell -NoProfile -File tools\move-mc-window.ps1 [-TimeoutSec 300]
param([int]$TimeoutSec = 300)

Add-Type @"
using System; using System.Runtime.InteropServices;
public static class W {
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int w, int h2, uint f);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
}
"@
Add-Type -AssemblyName System.Windows.Forms

$target = [System.Windows.Forms.Screen]::AllScreens | Where-Object { -not $_.Primary } | Select-Object -First 1
if (-not $target) { Write-Output "no secondary monitor; leaving the window alone"; exit 0 }
$b = $target.WorkingArea
$prev = [W]::GetForegroundWindow()
$end = (Get-Date).AddSeconds($TimeoutSec)
while ((Get-Date) -lt $end) {
    $ids = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
        Where-Object { $_.CommandLine -match 'KnotClient' } | ForEach-Object { $_.ProcessId }
    foreach ($id in $ids) {
        $p = Get-Process -Id $id -ErrorAction SilentlyContinue
        if ($p -and $p.MainWindowHandle -ne [IntPtr]::Zero) {
            $w = [Math]::Min(1280, $b.Width - 40); $h = [Math]::Min(720, $b.Height - 40)
            # SWP_NOZORDER 0x4 | SWP_NOACTIVATE 0x10
            [void][W]::SetWindowPos($p.MainWindowHandle, [IntPtr]::Zero, $b.X + 20, $b.Y + 20, $w, $h, 0x14)
            Start-Sleep -Milliseconds 300
            if ($prev -ne [IntPtr]::Zero) { [void][W]::SetForegroundWindow($prev) }
            Write-Output "moved pid $id to $($target.DeviceName)"
            exit 0
        }
    }
    Start-Sleep -Milliseconds 100
}
Write-Output "no client window appeared within $TimeoutSec s"
