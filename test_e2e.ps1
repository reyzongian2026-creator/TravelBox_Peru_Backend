$ErrorActionPreference = "Continue"

# Step 1: Login
Write-Host "=== STEP 1: LOGIN ==="
$loginBody = "{""email"":""reyzongian@outlook.es"",""password"":""G14nfr4nc0030525@""}"
$loginResp = curl.exe -s -X POST "https://api.inkavoy.pe/api/v1/auth/login" -H "Content-Type: application/json" -d $loginBody --max-time 30
Write-Host "Login raw response type: $($loginResp.GetType().Name) length: $($loginResp.Length)"
Write-Host "First 200 chars: $($loginResp[0..199] -join '')"
if (-not $loginResp) { Write-Host "Empty response from login"; exit 1 }
$joinedResp = if ($loginResp -is [array]) { $loginResp -join "" } else { $loginResp }
$login = $joinedResp | ConvertFrom-Json
$tkn = $login.accessToken
if (-not $tkn) { Write-Host "LOGIN FAILED"; exit 1 }
Write-Host "Token: $($tkn.Substring(0,30))..."

# Step 2: Create reservation
Write-Host "`n=== STEP 2: CREATE RESERVATION ==="
$startDate = (Get-Date).AddDays(10).ToString("yyyy-MM-dd")
$endDate = (Get-Date).AddDays(12).ToString("yyyy-MM-dd")
$resBody = "{""warehouseId"":1,""spaceIds"":[1],""vehiclePlate"":""ABC-123"",""startDate"":""$startDate"",""endDate"":""$endDate"",""customerNotes"":""E2E popup test""}"
$resResp = curl.exe -s -X POST "https://api.inkavoy.pe/api/v1/reservations" -H "Content-Type: application/json" -H "Authorization: Bearer $tkn" -d $resBody --max-time 30
$res = $resResp | ConvertFrom-Json
Write-Host "Reservation ID: $($res.id)"
Write-Host "Status: $($res.status)"
Write-Host "Total: $($res.totalPrice)"
$resId = $res.id
if (-not $resId) { Write-Host "RESERVATION FAILED: $resResp"; exit 1 }

# Step 3: Create payment intent
Write-Host "`n=== STEP 3: CREATE PAYMENT INTENT ==="
$intBody = "{""reservationId"":$resId,""paymentMethod"":""IZIPAY"",""currency"":""PEN""}"
$intResp = curl.exe -s -X POST "https://api.inkavoy.pe/api/v1/payments/intent" -H "Content-Type: application/json" -H "Authorization: Bearer $tkn" -d $intBody --max-time 30
$intent = $intResp | ConvertFrom-Json
Write-Host "Intent ID: $($intent.id)"
Write-Host "Status: $($intent.status)"
Write-Host "Amount: $($intent.amount)"

# Step 4: Confirm payment (get session/formToken)
Write-Host "`n=== STEP 4: CONFIRM ==="
$intId = $intent.id
if (-not $intId) { Write-Host "INTENT FAILED: $intResp"; exit 1 }
$confBody = "{""paymentIntentId"":$intId}"
$confResp = curl.exe -s -X POST "https://api.inkavoy.pe/api/v1/payments/confirm" -H "Content-Type: application/json" -H "Authorization: Bearer $tkn" -d $confBody --max-time 30
$conf = $confResp | ConvertFrom-Json
Write-Host "Confirm status: $($conf.status)"

$na = $conf.nextAction
if ($na) {
    Write-Host "`n=== NEXT ACTION (popup data) ==="
    Write-Host "scriptUrl: $($na.scriptUrl)"
    Write-Host "authorization (formToken): $($na.authorization.Substring(0, [Math]::Min(50, $na.authorization.Length)))..."
    Write-Host "publicKey: $($na.publicKey)"
    Write-Host "keyRSA: $($na.keyRSA)"
    Write-Host "checkoutConfig.render: $($na.checkoutConfig.render | ConvertTo-Json -Compress)"
    
    # Validate critical fields
    $ok = $true
    if ($na.scriptUrl -notlike "*micuentaweb*") { Write-Host "ERROR: scriptUrl should be micuentaweb Krypton"; $ok = $false }
    if (-not $na.authorization) { Write-Host "ERROR: no formToken"; $ok = $false }
    if (-not $na.publicKey) { Write-Host "ERROR: no publicKey"; $ok = $false }
    if ($ok) { Write-Host "`n*** ALL PAYMENT DATA CORRECT - Krypton popin ready ***" }
} else {
    Write-Host "ERROR: No nextAction in confirm response"
    Write-Host "Full response: $confResp"
}
