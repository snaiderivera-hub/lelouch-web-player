$adb = "C:\Users\Lelouch\Downloads\Compressed\scrcpy-win64-v4.1\scrcpy-win64-v4.1\adb.exe"
$ips = @("192.168.18.6", "192.168.18.9", "192.168.18.10", "192.168.18.11", "192.168.18.13", "192.168.18.14", "192.168.18.16", "192.168.18.132", "192.168.18.145")

Write-Host "Scanning local IPs for port 5555 (Wireless ADB)..."
foreach ($ip in $ips) {
    $tcp = Test-NetConnection -ComputerName $ip -Port 5555 -WarningAction SilentlyContinue -InformationLevel Quiet
    if ($tcp) {
        Write-Host ">>> OPEN ADB FOUND at $ip:5555! Attempting adb connect..."
        & $adb connect "$($ip):5555"
    }
}

Write-Host "Current adb devices:"
& $adb devices -l
