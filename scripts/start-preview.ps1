$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$jarPath=Join-Path $projectRoot 'target/credlearn-0.1.0-SNAPSHOT.jar'
if(!(Test-Path $jarPath)){throw 'Build the application first: ./mvnw.cmd -DskipTests package'}
& (Join-Path $PSScriptRoot 'start-local-db.ps1')
$dbConfig=Get-Content (Join-Path $projectRoot '.local/database.json') -Raw | ConvertFrom-Json
$env:DB_PASSWORD=$dbConfig.password
$env:DB_USERNAME='skillswap'
$env:DB_URL="jdbc:mysql://127.0.0.1:$($dbConfig.port)/skillswap?connectionTimeZone=UTC"
$env:SERVER_ADDRESS='127.0.0.1'
$previewPort=if($env:PORT){[int]$env:PORT}else{8080}
$client=[System.Net.Sockets.TcpClient]::new()
try{$client.Connect('127.0.0.1',$previewPort); throw "Port $previewPort already has a listener. Stop the existing preview first."}catch [System.Net.Sockets.SocketException] {}finally{$client.Dispose()}
$java=(Get-Command java).Source
$app=Start-Process -FilePath $java -ArgumentList @('-jar',"`"$jarPath`"") -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $projectRoot '.local/preview.log') -RedirectStandardError (Join-Path $projectRoot '.local/preview-error.log') -PassThru
$app.Id | Set-Content (Join-Path $projectRoot '.local/app.pid')
for($attempt=0;$attempt -lt 45;$attempt++){
 if($app.HasExited){throw 'Preview exited. Inspect .local/preview.log and .local/preview-error.log.'}
 try{ $response=Invoke-WebRequest -Uri "http://127.0.0.1:$previewPort/" -TimeoutSec 2 -UseBasicParsing; if($response.StatusCode -eq 200){Write-Output "CredLearn is ready: http://127.0.0.1:$previewPort (PID $($app.Id))";exit 0} }catch{}
 Start-Sleep -Seconds 1
}
throw 'Preview did not become ready within 45 seconds. Inspect .local/preview.log.'
