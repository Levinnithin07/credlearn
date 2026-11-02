param([switch]$Test,[switch]$Jar)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$configPath=Join-Path $projectRoot '.local/database.json'
if(Test-Path $configPath){
 $dbConfig=Get-Content $configPath -Raw | ConvertFrom-Json
 $env:DB_PASSWORD=$dbConfig.password
 $env:DB_USERNAME='skillswap'
 $schema=if($Test){'skillswap_test'}else{'skillswap'}
 $env:DB_URL="jdbc:mysql://127.0.0.1:$($dbConfig.port)/$($schema)?connectionTimeZone=UTC"
}
Push-Location $projectRoot
try {
 if($Test){ & .\mvnw.cmd -B -ntp test } elseif($Jar) { & java -jar target/credlearn-0.1.0-SNAPSHOT.jar } else { & .\mvnw.cmd -B -ntp spring-boot:run }
 if($LASTEXITCODE -ne 0){throw 'Maven failed. See output above.'}
} finally {Pop-Location}
