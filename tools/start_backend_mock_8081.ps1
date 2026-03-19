param(
    [switch]$SkipAzureKeyVault,
    [string]$AzureKeyVaultName = "kvtravelboxpe",
    [switch]$EnableKeyVaultRuntime
)

$ErrorActionPreference = "Stop"
try {
    Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force -ErrorAction Stop
} catch {
}

$root = "C:\Users\GianLH\Desktop\PROYECTI\TravelBox_Peru_Backend"
$workspaceRoot = Split-Path -Parent $root
$azureKvLoaderPath = Join-Path $workspaceRoot "tools\load_azure_kv_env.ps1"

if ((-not $SkipAzureKeyVault) -and (Test-Path $azureKvLoaderPath)) {
    try {
        $kvResult = . $azureKvLoaderPath -VaultName $AzureKeyVaultName -Quiet
        Write-Host ("Azure Key Vault '{0}': {1} variables cargadas." -f $AzureKeyVaultName, $kvResult.loadedCount)
    } catch {
        Write-Warning ("No se pudieron cargar variables desde Azure Key Vault '{0}': {1}" -f $AzureKeyVaultName, $_.Exception.Message)
    }
}

$hasAzureSpCreds = -not [string]::IsNullOrWhiteSpace($env:AZURE_CLIENT_ID) `
    -and -not [string]::IsNullOrWhiteSpace($env:AZURE_CLIENT_SECRET) `
    -and -not [string]::IsNullOrWhiteSpace($env:AZURE_TENANT_ID)

if ($EnableKeyVaultRuntime) {
    if ($hasAzureSpCreds) {
        $env:AZURE_KEYVAULT_ENABLED = "true"
        Write-Host "Azure Key Vault runtime: ENABLED"
    } else {
        $env:AZURE_KEYVAULT_ENABLED = "false"
        Write-Warning "EnableKeyVaultRuntime solicitado, pero faltan AZURE_CLIENT_ID/AZURE_CLIENT_SECRET/AZURE_TENANT_ID. Se usa fallback local."
    }
} elseif ([string]::IsNullOrWhiteSpace($env:AZURE_KEYVAULT_ENABLED)) {
    $env:AZURE_KEYVAULT_ENABLED = "false"
    Write-Host "Azure Key Vault runtime: DISABLED (fallback local)"
}

Set-Location $root

$env:SERVER_PORT = "8081"
$env:APP_PAYMENT_PROVIDER = "mock"
$env:APP_INVENTORY_OFFICE_PICKUP_LATE_GRACE_MINUTES = "0"
$env:APP_FIREBASE_ENABLED = if ($env:APP_FIREBASE_ENABLED) { $env:APP_FIREBASE_ENABLED } else { "true" }
$env:APP_FIREBASE_PROJECT_ID = if ($env:APP_FIREBASE_PROJECT_ID) { $env:APP_FIREBASE_PROJECT_ID } else { "travelboxperu-f96ee" }

$serviceAccountFile = "C:/Users/GianLH/Downloads/travelboxperu-f96ee-firebase-adminsdk-fbsvc.json"
if (-not $env:APP_FIREBASE_SERVICE_ACCOUNT_JSON -and (Test-Path $serviceAccountFile)) {
    $env:APP_FIREBASE_SERVICE_ACCOUNT_FILE = $serviceAccountFile
} elseif (-not $env:APP_FIREBASE_SERVICE_ACCOUNT_FILE -and -not $env:APP_FIREBASE_SERVICE_ACCOUNT_JSON) {
    $env:APP_FIREBASE_SERVICE_ACCOUNT_FILE = ""
    Write-Warning "No se encontro $serviceAccountFile. Se usara ADC si esta disponible."
}

.\mvnw.cmd spring-boot:run
