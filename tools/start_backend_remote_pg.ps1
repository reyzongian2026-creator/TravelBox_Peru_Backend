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

$backendRoot = Split-Path -Parent $PSScriptRoot
$workspaceRoot = Split-Path -Parent $backendRoot
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

if (-not $env:APP_FIREBASE_ENABLED) {
    $env:APP_FIREBASE_ENABLED = "true"
}
if (-not $env:APP_FIREBASE_PROJECT_ID) {
    $env:APP_FIREBASE_PROJECT_ID = "travelboxperu-f96ee"
}
if (-not $env:APP_FIREBASE_SERVICE_ACCOUNT_FILE -and -not $env:APP_FIREBASE_SERVICE_ACCOUNT_JSON) {
    $env:APP_FIREBASE_SERVICE_ACCOUNT_FILE = ""
}

Set-Location $backendRoot
.\mvnw.cmd spring-boot:run 1> deployment-current.log 2> deployment-current.err
