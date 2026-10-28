# Met la fenetre de l'emulateur au premier plan, sans toucher au reste.
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win {
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
}
"@
$proc = Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
if (-not $proc) { Write-Output "fenetre introuvable"; exit 1 }
[void][Win]::ShowWindow($proc.MainWindowHandle, 9)   # SW_RESTORE
[void][Win]::SetForegroundWindow($proc.MainWindowHandle)
Write-Output ("fenetre mise au premier plan : " + $proc.MainWindowTitle)
