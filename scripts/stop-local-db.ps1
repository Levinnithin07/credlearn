$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$configPath=Join-Path $projectRoot '.local/database.json'
if(!(Test-Path $configPath)){throw 'No isolated database configuration exists.'}
$dbConfig=Get-Content $configPath -Raw | ConvertFrom-Json
$clientConfig=Join-Path $projectRoot '.local/admin-client.cnf'
@"
[client]
user=root
password=$($dbConfig.rootPassword)
host=127.0.0.1
port=$($dbConfig.port)
"@ | Set-Content $clientConfig -Encoding utf8
try {
 & 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqladmin.exe' "--defaults-extra-file=$clientConfig" shutdown
 if($LASTEXITCODE -ne 0){throw 'Could not stop the isolated database.'}
} finally {Remove-Item -LiteralPath $clientConfig -ErrorAction SilentlyContinue}
