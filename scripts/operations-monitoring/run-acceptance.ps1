param([int]$Seconds = 1800)
$ErrorActionPreference = 'Stop'
$opsRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$opsReport = Join-Path $opsRoot 'backend/target/surefire-reports/TEST-com.antshorttv.operations.OperationsQueryServiceTest.xml'
if (!(Test-Path -LiteralPath $opsReport)) { throw 'Run the operations backend tests first to compile the isolated fixture and resolve its classpath.' }
$opsXml = [xml](Get-Content -LiteralPath $opsReport -Raw)
$opsClasspath = ($opsXml.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
$opsOutput = Join-Path $opsRoot 'outputs/operations-acceptance'
New-Item -ItemType Directory -Path $opsOutput -Force | Out-Null
& javac -encoding UTF-8 -cp $opsClasspath -d $opsOutput (Join-Path $PSScriptRoot 'OperationsAcceptance.java')
if ($LASTEXITCODE -ne 0) { throw 'Acceptance fixture compilation failed.' }
& java -Xms128m -Xmx512m -cp "$opsOutput;$opsClasspath" com.antshorttv.operations.telemetry.OperationsAcceptance $Seconds (Join-Path $opsOutput 'result.json')
if ($LASTEXITCODE -ne 0) { throw 'Acceptance fixture failed.' }
