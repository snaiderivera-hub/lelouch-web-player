$ws = New-Object -ComObject WScript.Shell
$sc = $ws.CreateShortcut([System.IO.Path]::Combine([Environment]::GetFolderPath('Desktop'), 'IPTV Data Architect.lnk'))
$sc.TargetPath = 'c:\Users\Lelouch\Downloads\LIVE_VOD_ALVARADO2023_20260924\IPTV-Data-Architect.exe'
$sc.WorkingDirectory = 'c:\Users\Lelouch\Downloads\LIVE_VOD_ALVARADO2023_20260924'
$sc.Description = 'IPTV Data Architect Desktop App'
$sc.Save()
Write-Host 'Acceso directo creado con éxito en el Escritorio.' -ForegroundColor Green
