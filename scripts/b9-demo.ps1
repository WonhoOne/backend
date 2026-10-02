param([ValidateSet('db-up', 'db-fresh', 'db-down', 'build', 'start')][string]$Action = 'db-up')
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
Push-Location $repo
$names = @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'DEMO_EXPECTED_DATABASE', 'SMS_DELIVERY_ENABLED')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
function Compose([string[]]$Arguments) {
    & docker compose -p misterworld-b9-demo -f compose.b9-demo.yml @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'B9 local demo Compose failed' }
}
try {
    switch ($Action) {
        'db-up' { Compose @('up', '-d', '--wait', '--wait-timeout', '180') }
        'db-down' { Compose @('down') } # Keep the presentation data volume.
        'db-fresh' {
            # Destructive ONLY to fixed misterworld-b9-demo project volume; never uses arbitrary DB_URL.
            Compose @('down', '-v')
            Compose @('up', '-d', '--wait', '--wait-timeout', '180')
        }
        'build' {
            & .\mvnw.cmd --batch-mode --no-transfer-progress package
            if ($LASTEXITCODE -ne 0) { throw 'B9 demo build failed' }
        }
        'start' {
            if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) { throw 'Set JWT_SECRET through local environment' }
            if ($env:EMPLOYEE_BOOTSTRAP_ENABLED -eq 'true') {
                foreach ($name in @('EMPLOYEE_LOGIN_ID', 'EMPLOYEE_PASSWORD', 'EMPLOYEE_NAME', 'EMPLOYEE_ADDRESS', 'EMPLOYEE_CONTACT')) {
                    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
                        throw "Set required local environment: $name"
                    }
                }
            }
            if ($env:DEMO_PROVISIONING_ENABLED -eq 'true' -and [string]::IsNullOrWhiteSpace($env:DEMO_SCENARIO_FILE)) {
                throw 'Set external DEMO_SCENARIO_FILE before provisioning'
            }
            $env:DB_URL = 'jdbc:mysql://127.0.0.1:3308/misterworld_demo?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&connectTimeout=5000&socketTimeout=20000'
            $env:DB_USERNAME = 'misterworld_demo'
            $env:DB_PASSWORD = 'b9-demo-local-only-password'
            $env:DEMO_EXPECTED_DATABASE = 'misterworld_demo'
            $env:SMS_DELIVERY_ENABLED = 'false' # Rehearsal is always offline; A2.2 has a separate manual harness.
            & java -jar target/misterworld-0.0.1-SNAPSHOT.jar
            if ($LASTEXITCODE -ne 0) { throw 'B9 demo backend failed' }
        }
    }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    Pop-Location
}
