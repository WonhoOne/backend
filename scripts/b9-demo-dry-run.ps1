# Offline Backend-only rehearsal. Requires a fresh b9-demo DB; NEVER enables SOLAPI.
param()
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
Push-Location $repo
$settings = @{
    DB_URL = 'jdbc:mysql://127.0.0.1:3308/misterworld_demo?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&connectTimeout=5000&socketTimeout=20000'
    DB_USERNAME = 'misterworld_demo'; DB_PASSWORD = 'b9-demo-local-only-password'
    JWT_SECRET = 'b9-dry-run-test-only-signing-key-at-least-32-bytes'
    DEMO_EXPECTED_DATABASE = 'misterworld_demo'; SMS_DELIVERY_ENABLED = 'false'
    EMPLOYEE_BOOTSTRAP_ENABLED = 'true'; EMPLOYEE_LOGIN_ID = 'b9-test-only-employee'
    EMPLOYEE_PASSWORD = 'b9-test-only-employee-password'; EMPLOYEE_NAME = 'B9 Test Employee'
    EMPLOYEE_ADDRESS = 'Test-only address'; EMPLOYEE_CONTACT = '010-0000-0000'
    DEMO_PROVISIONING_ENABLED = 'true'; BUSINESS_TIME_ZONE = 'Asia/Seoul'
}
$previous = @{}
foreach ($name in $settings.Keys) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$previous['DEMO_SCENARIO_FILE'] = $env:DEMO_SCENARIO_FILE
$server = $null
function Require([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
function StartOfflineBackend([string]$Label) {
    $java = (Get-Command java).Source
    $jar = Join-Path $repo 'target/misterworld-0.0.1-SNAPSHOT.jar'
    Require (Test-Path -LiteralPath $jar) 'Build the demo JAR first'
    $script:server = Start-Process -FilePath $java -ArgumentList @('-jar', ('"{0}"' -f $jar),
        '--server.port=18080', '--sms.delivery.enabled=false') -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $repo "target/b9-demo-$Label.stdout.log") `
        -RedirectStandardError (Join-Path $repo "target/b9-demo-$Label.stderr.log")
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($script:server.HasExited) { throw 'Offline backend startup failed; inspect local target logs' }
        try {
            $result = Invoke-RestMethod 'http://127.0.0.1:18080/api/v1/tours' -TimeoutSec 2
            if (@($result).Count -eq 1) { return }
        } catch { }
        Start-Sleep -Milliseconds 300
    }
    throw 'Offline backend readiness timeout'
}
function StopOfflineBackend {
    if ($null -ne $script:server -and -not $script:server.HasExited) {
        Stop-Process -Id $script:server.Id
        $script:server.WaitForExit(10000) | Out-Null
    }
    $script:server = $null
}
function PostJson([string]$Path, $Body, $Headers = @{}) {
    Invoke-RestMethod -Uri "http://127.0.0.1:18080$Path" -Method Post -ContentType 'application/json' `
        -Body ($Body | ConvertTo-Json -Depth 8 -Compress) -Headers $Headers -TimeoutSec 10
}
try {
    $portProbe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 18080)
    try { $portProbe.Start() } finally { $portProbe.Stop() }
    foreach ($name in $settings.Keys) { [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process') }
    $today = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow, 'Korea Standard Time').Date
    $manifest = @{
        schemaVersion = 1
        products = @(@{ key = 'dry-test'; theme = 'GOLF_CHALLENGE'; name = 'B9 Test Product'
            description = 'Clearly test-only offline rehearsal'; stylePrices = @(
                @{ style = 'CLASSIC'; amount = 101; currency = 'KRW' },
                @{ style = 'GRAND'; amount = 201; currency = 'KRW' },
                @{ style = 'PREMIUM'; amount = 301; currency = 'KRW' }) })
        schedules = @(@{ productKey = 'dry-test'; startDate = $today.AddDays(30).ToString('yyyy-MM-dd')
            endDate = $today.AddDays(34).ToString('yyyy-MM-dd') })
    }
    $file = Join-Path $repo 'target/b9-test-only-external-manifest.json'
    [IO.File]::WriteAllText($file, ($manifest | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
    $env:DEMO_SCENARIO_FILE = $file
    StartOfflineBackend 'first-start'
    $tours = Invoke-RestMethod 'http://127.0.0.1:18080/api/v1/tours'
    Require ($tours.Count -eq 1 -and $tours[0].stylePrices.Count -eq 3) 'Provisioned product/price count mismatch'
    $tourId = $tours[0].id
    StopOfflineBackend
    $env:DEMO_PROVISIONING_ENABLED = 'false'; $env:EMPLOYEE_BOOTSTRAP_ENABLED = 'false'
    StartOfflineBackend 'restart'
    $tours = Invoke-RestMethod 'http://127.0.0.1:18080/api/v1/tours'
    Require ($tours.Count -eq 1 -and $tours[0].id -eq $tourId) 'Restart did not preserve product identity'
    $schedules = Invoke-RestMethod "http://127.0.0.1:18080/api/v1/tour-schedules?tourId=$tourId"
    Require ($schedules.Count -eq 1 -and $schedules[0].reservable -and -not $schedules[0].recruitment.confirmed) 'Schedule is not future/unconfirmed'
    PostJson '/api/v1/auth/signup' @{ loginId = 'b9-test-only-customer'; password = 'b9-test-only-customer-password'
        name = 'B9 Test Customer'; address = 'Test-only address'; contact = '010-0000-0000' } | Out-Null
    $login = PostJson '/api/v1/auth/login' @{ loginId = 'b9-test-only-customer'; password = 'b9-test-only-customer-password' }
    $customerAuth = @{ Authorization = "Bearer $($login.accessToken)" }
    $reservation = PostJson '/api/v1/reservations' @{ scheduleId = $schedules[0].id; participantCount = 3
        configuration = @{ style = 'GRAND'; hotelOption = 'HOTEL_4_STAR'; transportOption = 'PREMIUM_VAN_10'
            mealOption = 'LOCAL_RESTAURANT'; extraOptions = @() } } $customerAuth
    Require ($reservation.schedule.recruitment.confirmed) 'Reservation did not cross confirmation threshold'
    Invoke-RestMethod "http://127.0.0.1:18080/api/v1/reservations/$($reservation.id)" -Headers $customerAuth | Out-Null
    $employee = PostJson '/api/v1/auth/login' @{ loginId = 'b9-test-only-employee'; password = 'b9-test-only-employee-password' }
    $employeeAuth = @{ Authorization = "Bearer $($employee.accessToken)" }
    $inventory = Invoke-RestMethod 'http://127.0.0.1:18080/api/v1/employee/inventory' -Headers $employeeAuth
    Require ($inventory.Count -eq 4 -and @($inventory | Where-Object quantity -ne 0).Count -eq 0) 'V2 catalog changed'
    Invoke-RestMethod 'http://127.0.0.1:18080/api/v1/employee/tours' -Headers $employeeAuth | Out-Null
    Invoke-RestMethod "http://127.0.0.1:18080/api/v1/employee/tours/$tourId" -Method Put -Headers $employeeAuth `
        -ContentType 'application/json' -Body (@{ theme = 'GOLF_CHALLENGE'; name = 'B9 Test Product Updated'
            description = 'Clearly test-only same-theme edit'; stylePrices = $manifest.products[0].stylePrices } | ConvertTo-Json -Depth 8) | Out-Null
    $sql = 'SELECT VERSION(); SELECT COUNT(*) FROM tour_reservation; SELECT COUNT(*) FROM sms_confirmation_event; SELECT COUNT(*) FROM sms_confirmation_recipient; SELECT status,attempt_count FROM sms_confirmation_recipient;'
    $facts = & docker compose -p misterworld-b9-demo -f compose.b9-demo.yml exec -T `
        -e MYSQL_PWD=b9-demo-local-only-password mysql mysql -umisterworld_demo -Dmisterworld_demo -N -e $sql
    Require ($LASTEXITCODE -eq 0) 'Offline DB evidence query failed'
    Require (@($facts).Count -eq 5 -and $facts[1] -eq '1' -and $facts[2] -eq '1' -and $facts[3] -eq '1' `
        -and $facts[4] -match '^PENDING\s+0$') 'Offline outbox state mismatch'
    $report = "OFFLINE_DEMO_DRY_RUN=PASS`nJava executable=$((Get-Command java).Source)`nMySQL=$($facts[0])`nbootstrap/restart/public GET/signup/login/reservation/employee GET+PUT=PASS`nInventory rows=4 quantity=0`nReservations=1 events=1 recipients=1 status=PENDING attempts=0`nSMS_DELIVERY_ENABLED=false`nProvider calls=0`n"
    [IO.File]::WriteAllText((Join-Path $repo 'target/b9-demo-dry-run.txt'), $report)
    Write-Output $report
} finally {
    StopOfflineBackend
    foreach ($name in $previous.Keys) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    Pop-Location
}
