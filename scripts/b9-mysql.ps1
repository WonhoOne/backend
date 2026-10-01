param([ValidateSet('up', 'verify', 'down', 'fresh')][string]$Action = 'verify')
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
Push-Location $repo
$names = @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET', 'SMS_DELIVERY_ENABLED', 'EMPLOYEE_BOOTSTRAP_ENABLED', 'B9_MYSQL_TEST_DATABASE')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
function Compose([string[]]$Arguments) {
    & docker compose -f compose.b9-mysql.yml @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'B9 local Docker Compose failed' }
}
try {
    switch ($Action) {
        'up' { Compose @('up', '-d', '--wait', '--wait-timeout', '180') }
        'down' { Compose @('down', '-v') }
        'fresh' {
            # Fixed Compose project only. Never issues DROP DATABASE against DB_URL.
            Compose @('down', '-v')
            Compose @('up', '-d', '--wait', '--wait-timeout', '180')
        }
        'verify' {
            $env:DB_URL = 'jdbc:mysql://127.0.0.1:3307/misterworld?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&connectTimeout=5000&socketTimeout=20000'
            $env:DB_USERNAME = 'misterworld'
            $env:DB_PASSWORD = 'b9-test-only-password'
            $env:JWT_SECRET = 'b9-test-only-jwt-signing-key-at-least-32-bytes'
            $env:SMS_DELIVERY_ENABLED = 'false'
            $env:EMPLOYEE_BOOTSTRAP_ENABLED = 'false'
            $env:B9_MYSQL_TEST_DATABASE = 'misterworld'
            & .\mvnw.cmd --batch-mode --no-transfer-progress -Pmysql-integration verify
            if ($LASTEXITCODE -ne 0) { throw 'B9 MySQL validation failed; inspect target/failsafe-reports' }
        }
    }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    Pop-Location
}
