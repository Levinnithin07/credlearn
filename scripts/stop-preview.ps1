$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$pidFile=Join-Path $projectRoot '.local/app.pid'
if(!(Test-Path $pidFile)){Write-Output 'No recorded preview process.';exit 0}
$appPid=[int](Get-Content $pidFile)
$process=Get-CimInstance Win32_Process -Filter "ProcessId = $appPid"
$expected=Join-Path $projectRoot 'target/credlearn-0.1.0-SNAPSHOT.jar'
if($process -and $process.CommandLine.Contains($expected)) {Stop-Process -Id $appPid;Write-Output 'Local CredLearn preview stopped.'}
elseif($process){throw 'The PID belongs to a different process; it was not stopped.'}
else {Write-Output 'The preview has already stopped.'}
