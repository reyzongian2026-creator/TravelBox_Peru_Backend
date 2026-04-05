$ErrorActionPreference = "Continue"
$base = "https://api.inkavoy.pe"
$tmpDir = "C:\Users\MiniOS\Documents\TravelBox_Peru_Backend"

function ApiCall($method, $path, $bodyJson, $token) {
    $args = @("-s", "-X", $method, "$base$path", "-H", "Content-Type: application/json", "--max-time", "30")
    if ($token) { $args += @("-H", "Authorization: Bearer $token") }
    if ($bodyJson) {
        $tmpFile = "$tmpDir\api_body_tmp.json"
        [System.IO.File]::WriteAllText($tmpFile, $bodyJson)
        $args += @("-d", "@$tmpFile")
    }
    $resp = & curl.exe @args
    if ($resp -is [array]) { $resp = $resp -join "" }
    return $resp | ConvertFrom-Json
}

# Step 1: Login
Write-Host "=== STEP 1: LOGIN ==="
$login = ApiCall "POST" "/api/v1/auth/login" '{"email":"reyzongian@outlook.es","password":"G14nfr4nc0030525@"}'
$tkn = $login.accessToken
if (-not $tkn) { Write-Host "LOGIN FAILED: $($login | ConvertTo-Json -Compress)"; exit 1 }
Write-Host "OK - Token: $($tkn.Substring(0,30))..."

# Step 2: Create reservation
Write-Host "`n=== STEP 2: CREATE RESERVATION ==="
$sd = (Get-Date).ToUniversalTime().AddDays(10).ToString("yyyy-MM-ddTHH:mm:ssZ")
$ed = (Get-Date).ToUniversalTime().AddDays(12).ToString("yyyy-MM-ddTHH:mm:ssZ")
$resBody = '{"warehouseId":1,"startAt":"' + $sd + '","endAt":"' + $ed + '","estimatedItems":2,"bagSize":"MEDIUM"}'
$res = ApiCall "POST" "/api/v1/reservations" $resBody $tkn
if (-not $res) { Write-Host "Empty response - retrying..."; $res = ApiCall "POST" "/api/v1/reservations" $resBody $tkn }
Write-Host "Reservation ID: $($res.id) | Status: $($res.status) | Total: $($res.totalPrice)"
$resId = $res.id
if (-not $resId) { Write-Host "RESERVATION FAILED: $($res | ConvertTo-Json -Compress -Depth 3)"; exit 1 }

# Step 3: Create payment intent
Write-Host "`n=== STEP 3: PAYMENT INTENT ==="
$intBody = '{"reservationId":' + $resId + ',"paymentMethod":"IZIPAY","currency":"PEN"}'
$intent = ApiCall "POST" "/api/v1/payments/intent" $intBody $tkn
Write-Host "Intent ID: $($intent.id) | Status: $($intent.status) | Amount: $($intent.amount)"
$intId = $intent.id
if (-not $intId) { Write-Host "INTENT FAILED: $($intent | ConvertTo-Json -Compress)"; exit 1 }

# Step 4: Confirm payment (get formToken + popup data)
Write-Host "`n=== STEP 4: CONFIRM PAYMENT ==="
$confBody = '{"paymentIntentId":' + $intId + '}'
$conf = ApiCall "POST" "/api/v1/payments/confirm" $confBody $tkn
Write-Host "Status: $($conf.status)"

$na = $conf.nextAction
if ($na) {
    Write-Host "`n=== PAYMENT POPUP DATA ==="
    Write-Host "scriptUrl:     $($na.scriptUrl)"
    $authLen = if ($na.authorization) { [Math]::Min(60, $na.authorization.Length) } else { 0 }
    if ($authLen -gt 0) { Write-Host "authorization: $($na.authorization.Substring(0, $authLen))..." }
    else { Write-Host "authorization: (empty)" }
    Write-Host "publicKey:     $($na.publicKey)"
    Write-Host "keyRSA:        $($na.keyRSA)"
    
    $ok = $true
    if ($na.scriptUrl -notlike "*micuentaweb*") { Write-Host "[FAIL] scriptUrl not Krypton"; $ok = $false }
    if (-not $na.authorization) { Write-Host "[FAIL] no formToken"; $ok = $false }
    if (-not $na.publicKey) { Write-Host "[FAIL] no publicKey"; $ok = $false }
    if ($ok) { Write-Host "`n*** ALL CHECKS PASSED - Lyra V4 Krypton popin ready ***" }
} else {
    Write-Host "[FAIL] No nextAction. Full response:"
    Write-Host ($conf | ConvertTo-Json -Compress -Depth 5)
}
