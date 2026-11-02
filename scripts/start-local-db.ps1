param([int]$Port=3307)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$localDir=Join-Path $projectRoot '.local'
$dataDir=Join-Path $localDir 'mysql-data'
$mysqlBin='C:\Program Files\MySQL\MySQL Server 8.0\bin'
if (!(Test-Path (Join-Path $mysqlBin 'mysqld.exe'))) { throw 'Install MySQL 8, or configure DB_URL, DB_USERNAME and DB_PASSWORD for your own development database.' }
New-Item -ItemType Directory -Force $localDir | Out-Null
if (!(Test-Path (Join-Path $localDir 'database.json'))) {
 $rootSecret=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
 $appSecret=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
 @{port=$Port;password=$appSecret;rootPassword=$rootSecret} | ConvertTo-Json | Set-Content (Join-Path $localDir 'database.json')
}
$dbConfig=Get-Content (Join-Path $localDir 'database.json') -Raw | ConvertFrom-Json
function Test-LocalPort([int]$number) {
 $client=[System.Net.Sockets.TcpClient]::new()
 try {$client.Connect('127.0.0.1',$number); return $true} catch {return $false} finally {$client.Dispose()}
}
if (Test-LocalPort $dbConfig.port) { Write-Output "Port $($dbConfig.port) already has a listener; leaving it unchanged."; exit 0 }
if (!(Test-Path (Join-Path $dataDir 'mysql'))) {
 & (Join-Path $mysqlBin 'mysqld.exe') --no-defaults --initialize-insecure "--datadir=$dataDir" "--basedir=$(Split-Path $mysqlBin -Parent)" --console
 if ($LASTEXITCODE -ne 0) { throw 'MySQL initialization failed.' }
}
$initPath=Join-Path $localDir 'bootstrap.sql'
$bootstrapSql=@"
ALTER USER 'root'@'localhost' IDENTIFIED BY '$($dbConfig.rootPassword)';
CREATE DATABASE IF NOT EXISTS skillswap CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS skillswap_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'skillswap'@'localhost' IDENTIFIED BY '$($dbConfig.password)';
GRANT ALL PRIVILEGES ON skillswap.* TO 'skillswap'@'localhost';
GRANT ALL PRIVILEGES ON skillswap_test.* TO 'skillswap'@'localhost';
"@
[IO.File]::WriteAllText($initPath,$bootstrapSql,[Text.UTF8Encoding]::new($false))
$configPath=Join-Path $localDir 'mysql.ini'
$mysqlSettings=@"
[mysqld]
basedir=$( (Split-Path $mysqlBin -Parent).Replace('\','/') )
datadir=$( $dataDir.Replace('\','/') )
port=$($dbConfig.port)
bind-address=127.0.0.1
mysqlx=0
init-file=$( $initPath.Replace('\','/') )
log-error=$( (Join-Path $localDir 'mysql-error.log').Replace('\','/') )
"@
[IO.File]::WriteAllText($configPath,$mysqlSettings,[Text.UTF8Encoding]::new($false))
$process=Start-Process -FilePath (Join-Path $mysqlBin 'mysqld.exe') -ArgumentList "--defaults-file=`"$configPath`"" -WindowStyle Hidden -PassThru
$process.Id | Set-Content (Join-Path $localDir 'mysql.pid')
for($attempt=0;$attempt -lt 30;$attempt++) {
 if (Test-LocalPort $dbConfig.port) { Write-Output "Isolated MySQL ready on 127.0.0.1:$($dbConfig.port). Local credentials are in ignored .local/database.json."; exit 0 }
 if($process.HasExited){throw 'MySQL exited; inspect .local/mysql-error.log.'}
 Start-Sleep -Seconds 1
}
throw 'MySQL did not become ready within 30 seconds.'
