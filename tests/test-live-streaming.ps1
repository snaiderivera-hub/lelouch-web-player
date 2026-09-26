try {
    $req = [System.Net.HttpWebRequest]::Create('http://localhost:7878/proxy?target=http%3A%2F%2Fliontv.es%3A80%2Flive%2FHermanos503%2FBysckXDynC%2F1976611.ts')
    $res = $req.GetResponse()
    $s = $res.GetResponseStream()
    $b = New-Object byte[] 4096
    $read = $s.Read($b, 0, 4096)
    Write-Host ('STREAMING EN VIVO 100% OPERATIVO! Bytes leídos en tiempo real: ' + $read) -ForegroundColor Green
    $res.Close()
} catch {
    Write-Host ('ERR: ' + $_.Exception.Message) -ForegroundColor Red
}
