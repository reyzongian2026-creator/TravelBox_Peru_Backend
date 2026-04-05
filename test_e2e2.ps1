$ErrorActionPreference = "Continue"
$base = "https://api.inkavoy.pe"

function ApiCall($method, $path, $body, $token) {
    $args = @("-s", "-X", $method, "$base$path", "-H", "Content-Type: application/json", "--max-time", "30")
    if ($token) { $args += @("-H", "Authorization: Bearer $token") }
    if ($body) { $args += @("-d", $body) }
    $resp = & curl.exe @args
    if ($resp -is [array]) { $resp = $resp -join "" }
    return $resp | ConvertFrom-Json
}

# Step 1: Login
Write-Host "=== STEP 1: LOGIN ==="
$login = ApiCall "POST" "/api/v1/auth/login" '{"email":"reyzongian@outlook.es","password":"G14nfr4nc0030525@"}'
$tkn = $login.accessToken
if (-not $tkn) { Write-Host "LOGIN FAILED: $($login | ConvertTo-Json -Compress)"; exit 1 }
Write-Host "Token: $($tkn.Substring(0,30))..."

# Step 2: Create reservation
Write-Host "`n=== STEP 2: CREATE RESERVATION ==="
$sd = (Get-Date).AddDays(10).ToString("yyyy-MM-dd")
$ed = (Get-Date).AddDays(12).ToString("yyyy-MM-dd")
$res = ApiCall "POST" "/api/v1/reservations" "{`"warehouseId`":1,`"spaceIds`":[1],`"vehiclePlate`":`"ABC-123`",`"startDate`":`"$sd`",`"endDate`":`"$ed`",`"customerNotes`":`"E2E test`"}" $tkn
Write-Host "Reservation ID: $($res.id) | Status: $($res.status) | Total: $($res.totalPrice)"
$resId = $res.id
if (-not $resId) { Write-Host "RESERVATION FAILED"; exit 1 }

# Step 3: Create payment intent  
Write-Host "`n=== STEP 3: PAYMENT INTENT ==="
$intent = ApiCall "POST" "/api/v1/payments/intent" "{`"reservationId`":$resId,`"paymentMethod`":`"IZIPAY`",`"currency`":`"PEN`"}" $tkn
Write-Host "Intent ID: $($intent.id) | Status: $($intent.status) | Amount: $($intent.amount)"
$intId = $intent.id
if (-not $intId) { Write-Host "INTENT FAILED: $($intent | ConvertTo-Json -Compress)"; exit 1 }

# Step 4: Confirm payment
Write-Host "`n=== STEP 4: CONFIRM PAYMENT ==="
$conf = ApiCall "POST" "/api/v1/payments/confirm" "{`"paymentIntentId`":$intId}" $tkn
Write-Host "Status: $($conf.status)"
$na = $conf.nextAction
if ($na) {
    Write-Host "`n=== PAYMENT POPUP DATA ==="
    Write-Host "scriptUrl:     $($na.scriptUrl)"
    Write-Host "authorization: $($na.authorization.Substring(0, [Math]::Min(60, $na.authorization.Length)))..."
    Write-Host "publicKey:     $($na.publicKey)"
    Write-Host "keyRSA:        $($na.keyRSA)"
    
    $ok = $true
    if ($na.scriptUrl -notlike "*micuentaweb*") { Write-Host "FAIL: scriptUrl not Krypton"; $ok = $false }
    if (-not $na.authorization) { Write-Host "FAIL: no formToken"; $ok = $false }
    if (-not $na.publicKey) { Write-Host "FAIL: no publicKey"; $ok = $false }
    if ($ok) { Write-Host "`n*** ALL CHECKS PASSED - Krypton popin ready ***" }
} else {
    Write-Host "FAIL: No nextAction. Full: $($conf | ConvertTo-Json -Compress -Depth 5)"
}
