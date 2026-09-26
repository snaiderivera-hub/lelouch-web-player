$ws = New-Object -ComObject WScript.Shell
$sc = $ws.CreateShortcut([System.IO.Path]::Combine([Environment]::GetFolderPath('Desktop'), 'IPTV Data Architect.lnk'))
$sc.TargetPath = 'c:\Users\Lelouch\Downloads\Lelouch Web Player\IPTV-Data-Architect.exe'
$sc.WorkingDirectory = 'c:\Users\Lelouch\Downloads\Lelouch Web Player'
$sc.Description = 'Lelouch IPTV Web Player Desktop App'
$sc.Save()
Write-Host 'Acceso directo creado con éxito en el Escritorio.' -ForegroundColor Green
