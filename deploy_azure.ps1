<#
.SYNOPSIS
    TravelBox Peru Backend - Despliegue completo a Azure App Service
.DESCRIPTION
    Script consolidado 100% Azure para compilar, configurar y desplegar
    el backend Java/Spring Boot a Azure App Service.
    Incluye: Key Vault, App Settings, Build, Deploy, Health Check.
.PARAMETER Action
    Accion a ejecutar: deploy (default), config-only, build-only, status, logs, restart
.PARAMETER Environment
    Entorno destino: prod (default), staging
.PARAMETER SkipBuild
    Omitir compilacion Maven y usar JAR existente
.PARAMETER SkipConfig
    Omitir configuracion de App Settings en Azure
.PARAMETER Verbose
    Mostrar output detallado
.EXAMPLE
    .\deploy_azure.ps1
    .\deploy_azure.ps1 -Action deploy
    .\deploy_azure.ps1 -Action config-only
    .\deploy_azure.ps1 -Action status
    .\deploy_azure.ps1 -Action logs
    .\deploy_azure.ps1 -Action restart
    .\deploy_azure.ps1 -SkipBuild
#>

param(
    [ValidateSet("deploy", "config-only", "build-only", "status", "logs", "restart")]
    [string]$Action = "deploy",

    [ValidateSet("prod", "staging")]
    [string]$Environment = "prod",

    [switch]$SkipBuild,
    [switch]$SkipConfig,
    [switch]$VerboseOutput
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

# ============================================================================
# CONFIGURACION AZURE
# ============================================================================
$AZURE = @{
    SubscriptionId = "33815caa-4cfb-4a9e-b60a-8fee5caa2b08"
    ResourceGroup  = "travelbox-peru-bs-rg"
    WebAppName     = "travelbox-backend-bs"
    KeyVaultName   = "kvtravelboxpebs"
    KeyVaultUrl    = "https://kvtravelboxpebs.vault.azure.net/"
    Region         = "brazilsouth"
}

$BUILD = @{
    JavaVersion     = "21"
    SpringProfile   = $Environment
    MavenCommand    = ".\mvnw.cmd"
    ArtifactPattern = "target\storage-*.jar"
    Port            = 8080
}

$URLS = @{
    BackendPublic  = "https://api.inkavoy.pe"
    FrontendPublic = "https://www.inkavoy.pe"
    CorsOrigins    = "https://inkavoy.pe,https://www.inkavoy.pe,https://api.inkavoy.pe"
    HealthEndpoint = "https://travelbox-backend-bs.azurewebsites.net/actuator/health"
}

# ============================================================================
# KEY VAULT SECRETS MAP  (KV secret name -> App Setting name)
# ============================================================================
$KV_SECRETS = @{
    # --- Base de datos ---
    "tbx-new-db-host"                                  = "DB_HOST"
    "tbx-new-db-name"                                  = "DB_NAME"
    "tbx-new-db-username"                              = "DB_USERNAME"
    "tbx-new-db-password"                              = "DB_PASSWORD"

    # --- JWT & Seguridad ---
    "tbx-back-jwt-secret"                              = "APP_JWT_SECRET"
    "tbx-back-encryption-key"                          = "APP_SECURITY_ENCRYPTION_KEY"
    "tbx-back-qr-signing-key"                          = "APP_SECURITY_QR_SIGNING_KEY"

    # --- Izipay Pagos ---
    "tbx-back-payments-izipay-merchant-code"           = "APP_IZIPAY_MERCHANT_CODE"
    "tbx-back-payments-izipay-public-key"              = "APP_IZIPAY_PROD_PUBLIC_KEY"
    "tbx-back-payments-izipay-hash-key"                = "APP_IZIPAY_PROD_HASH_KEY"
    "tbx-back-payments-izipay-api-user"                = "APP_IZIPAY_API_USER"
    "tbx-back-payments-izipay-api-password"            = "APP_IZIPAY_PROD_API_PASSWORD"

    # --- SMTP Email ---
    "tbx-back-smtp-host"                               = "APP_SMTP_HOST"
    "tbx-back-smtp-port"                               = "APP_SMTP_PORT"
    "tbx-back-smtp-username"                           = "APP_SMTP_USERNAME"
    "tbx-back-smtp-password"                           = "APP_SMTP_PASSWORD"

    # --- Azure Email (Graph API) ---
    "tbx-back-email-graph-tenant-id"                   = "APP_EMAIL_GRAPH_TENANT_ID"
    "tbx-back-email-graph-client-id"                   = "APP_EMAIL_GRAPH_CLIENT_ID"
    "tbx-back-email-graph-client-secret"               = "APP_EMAIL_GRAPH_CLIENT_SECRET"

    # --- Azure Storage (Imagenes) ---
    "tbx-back-azure-storage-images-connection-string"  = "AZURE_STORAGE_IMAGES_CONNECTION_STRING"
    "tbx-back-azure-storage-images-endpoint"           = "AZURE_STORAGE_IMAGES_ENDPOINT"

    # --- Azure Storage (Reportes) ---
    "tbx-back-azure-storage-reports-connection-string" = "AZURE_STORAGE_REPORTS_CONNECTION_STRING"
    "tbx-back-azure-storage-reports-endpoint"          = "AZURE_STORAGE_REPORTS_ENDPOINT"

    # --- Azure Translation ---
    "tbx-back-translation-azure-api-key"               = "AZURE_TRANSLATION_API_KEY"
    "tbx-back-translation-azure-region"                = "AZURE_TRANSLATION_REGION"

    # --- Azure Maps ---
    "tbx-back-routing-azure-maps-key"                  = "AZURE_MAPS_API_KEY"

    # --- CORS ---
    "tbx-back-cors-allowed-origins"                    = "APP_CORS_ALLOWED_ORIGINS"

    # --- Email Provider ---
    "tbx-back-email-provider"                          = "APP_EMAIL_PROVIDER"
    "tbx-back-email-from-address"                      = "APP_EMAIL_FROM_ADDRESS"
    "tbx-back-auth-email-provider"                     = "APP_AUTH_EMAIL_PROVIDER"
}

# ============================================================================
# FUNCIONES AUXILIARES
# ============================================================================

function Write-Step {
    param([string]$Step, [string]$Message)
    Write-Host "`n[$Step] " -ForegroundColor Cyan -NoNewline
    Write-Host $Message -ForegroundColor White
}

function Write-Ok {
    param([string]$Message)
    Write-Host "  [OK] $Message" -ForegroundColor Green
}

function Write-Warn {
    param([string]$Message)
    Write-Host "  [WARN] $Message" -ForegroundColor Yellow
}

function Write-Fail {
    param([string]$Message)
    Write-Host "  [ERROR] $Message" -ForegroundColor Red
}

function Test-AzureCli {
    $azVersion = az version 2>$null | ConvertFrom-Json
    if (-not $azVersion) {
        Write-Fail "Azure CLI no esta instalado. Instalar desde https://aka.ms/installazurecli"
        exit 1
    }
    Write-Ok "Azure CLI v$($azVersion.'azure-cli')"
}

function Test-AzureLogin {
    $account = az account show 2>$null | ConvertFrom-Json
    if (-not $account) {
        Write-Step "AUTH" "Iniciando login de Azure..."
        az login
        $account = az account show | ConvertFrom-Json
    }
    az account set --subscription $AZURE.SubscriptionId
    Write-Ok "Suscripcion: $($account.name) ($($AZURE.SubscriptionId))"
}

function Test-JavaVersion {
    try {
        $oldPref = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $javaOutput = (java -version 2>&1 | Out-String)
        $ErrorActionPreference = $oldPref
        if ($javaOutput -match "version.*?`"(\d+)") {
            $major = $Matches[1]
            if ([int]$major -ge [int]$BUILD.JavaVersion) {
                Write-Ok "Java $major detectado"
                return
            }
        }
    }
    catch {}
    Write-Fail "Java $($BUILD.JavaVersion)+ no detectado. Instalar JDK $($BUILD.JavaVersion)"
    exit 1
}

function Get-KeyVaultSecrets {
    Write-Step "KEY VAULT" "Obteniendo secretos de $($AZURE.KeyVaultName)..."
    $appSettings = @{}
    $retrieved = 0
    $skipped = 0

    foreach ($kv in $KV_SECRETS.GetEnumerator()) {
        $oldPref = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $value = az keyvault secret show `
            --vault-name $AZURE.KeyVaultName `
            --name $kv.Key `
            --query value -o tsv 2>$null
        $ErrorActionPreference = $oldPref

        if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($value)) {
            $appSettings[$kv.Value] = $value
            $retrieved++
            if ($VerboseOutput) { Write-Ok "$($kv.Key) -> $($kv.Value)" }
        }
        else {
            $skipped++
            if ($VerboseOutput) { Write-Warn "$($kv.Key) no encontrado (opcional)" }
        }
    }

    Write-Ok "$retrieved secretos obtenidos, $skipped omitidos"
    return $appSettings
}

function Build-Maven {
    Write-Step "BUILD" "Compilando con Maven (perfil: $($BUILD.SpringProfile))..."

    $projectRoot = $PSScriptRoot
    Push-Location $projectRoot

    try {
        if (-not (Test-Path $BUILD.MavenCommand)) {
            Write-Fail "Maven wrapper no encontrado: $($BUILD.MavenCommand)"
            exit 1
        }

        & $BUILD.MavenCommand clean package -DskipTests -B `
            "-Dspring.profiles.active=$($BUILD.SpringProfile)" | Out-Host

        if ($LASTEXITCODE -ne 0) {
            Write-Fail "Compilacion Maven fallo (exit code: $LASTEXITCODE)"
            exit 1
        }

        $jarFile = Get-ChildItem -Path "target" -Filter "*.jar" |
        Where-Object { $_.Name -notlike "*.jar.original" } |
        Select-Object -First 1

        if (-not $jarFile) {
            Write-Fail "No se encontro JAR en target/"
            exit 1
        }

        Write-Ok "JAR generado: $($jarFile.Name) ($([math]::Round($jarFile.Length / 1MB, 1)) MB)"
        return $jarFile.FullName
    }
    finally {
        Pop-Location
    }
}

function Set-AzureAppSettings {
    param([hashtable]$Secrets)

    Write-Step "CONFIG" "Configurando App Settings en $($AZURE.WebAppName)..."

    # App Settings fijos (no vienen de KV)
    $fixedSettings = @{
        "SPRING_PROFILES_ACTIVE"               = $BUILD.SpringProfile
        "PORT"                                 = "$($BUILD.Port)"
        "WEBSITES_PORT"                        = "$($BUILD.Port)"
        "AZURE_KEYVAULT_ENABLED"               = "true"
        "AZURE_KEYVAULT_ENDPOINT"              = $AZURE.KeyVaultUrl
        "APP_TRANSLATION_PROVIDER"             = "azure"
        "APP_ROUTING_PROVIDER"                 = "azure"
        "APP_PAYMENT_PROVIDER"                 = "izipay"
        "APP_PAYMENTS_FORCE_CASH_ONLY"         = "false"
        "APP_PAYMENTS_ALLOW_MOCK_CONFIRMATION" = "false"
        "APP_FIREBASE_ENABLED"                 = "false"
        "APP_PUBLIC_BASE_URL"                  = $URLS.BackendPublic
        "APP_FRONTEND_BASE_URL"                = $URLS.FrontendPublic
        "SERVER_HTTP2_ENABLED"                 = "true"
        "APP_EMAIL_FROM_NAME"                  = "InkaVoy Peru"
        "APP_EMAIL_QUEUE_ENABLED"              = "true"
    }

    # Construir la URL de la BD a partir de host + name si tenemos ambos
    if ($Secrets.ContainsKey("DB_HOST") -and $Secrets.ContainsKey("DB_NAME")) {
        $dbHost = $Secrets["DB_HOST"]
        $dbName = $Secrets["DB_NAME"]
        $fixedSettings["DB_URL"] = "jdbc:postgresql://${dbHost}:5432/${dbName}?sslmode=require"
    }

    # Merge secrets + fixed settings
    $allSettings = @{}
    foreach ($kv in $Secrets.GetEnumerator()) { $allSettings[$kv.Key] = $kv.Value }
    foreach ($kv in $fixedSettings.GetEnumerator()) { $allSettings[$kv.Key] = $kv.Value }

    # Construir array de "KEY=VALUE" para az webapp config
    $settingsArray = @()
    foreach ($kv in $allSettings.GetEnumerator()) {
        $settingsArray += "$($kv.Key)=$($kv.Value)"
    }

    # Azure CLI acepta multiples settings de una vez
    $batchSize = 20
    for ($i = 0; $i -lt $settingsArray.Count; $i += $batchSize) {
        $batch = $settingsArray[$i..([Math]::Min($i + $batchSize - 1, $settingsArray.Count - 1))]
        az webapp config appsettings set `
            --resource-group $AZURE.ResourceGroup `
            --name $AZURE.WebAppName `
            --settings @batch | Out-Null

        if ($LASTEXITCODE -ne 0) {
            Write-Fail "Error configurando App Settings (batch $([Math]::Floor($i/$batchSize) + 1))"
            exit 1
        }
    }

    Write-Ok "$($allSettings.Count) App Settings configurados"
}

function Deploy-ToAzure {
    param([string]$JarPath)

    Write-Step "DEPLOY" "Desplegando JAR a Azure App Service..."

    if (-not (Test-Path $JarPath)) {
        Write-Fail "JAR no encontrado: $JarPath"
        exit 1
    }

    $jarSize = [math]::Round((Get-Item $JarPath).Length / 1MB, 1)
    Write-Host "  Subiendo $([System.IO.Path]::GetFileName($JarPath)) ($jarSize MB)..."

    az webapp deploy `
        --resource-group $AZURE.ResourceGroup `
        --name $AZURE.WebAppName `
        --type jar `
        --src-path $JarPath

    if ($LASTEXITCODE -ne 0) {
        Write-Fail "Despliegue fallo"
        exit 1
    }

    Write-Ok "JAR desplegado exitosamente"
}

function Restart-WebApp {
    Write-Step "RESTART" "Reiniciando $($AZURE.WebAppName)..."
    az webapp restart `
        --resource-group $AZURE.ResourceGroup `
        --name $AZURE.WebAppName

    if ($LASTEXITCODE -ne 0) {
        Write-Fail "Reinicio fallo"
        exit 1
    }
    Write-Ok "App Service reiniciado"
}

function Test-HealthCheck {
    Write-Step "HEALTH" "Verificando estado del backend..."
    $maxAttempts = 12
    $delaySeconds = 15

    for ($i = 1; $i -le $maxAttempts; $i++) {
        try {
            $response = Invoke-RestMethod -Uri $URLS.HealthEndpoint -TimeoutSec 10 -ErrorAction Stop
            $status = $response.status
            if ($status -eq "UP") {
                Write-Ok "Backend activo y saludable (status: UP)"
                return $true
            }
            Write-Warn "Intento $i/$maxAttempts - Status: $status"
        }
        catch {
            Write-Warn "Intento $i/$maxAttempts - No responde aun..."
        }
        if ($i -lt $maxAttempts) {
            Start-Sleep -Seconds $delaySeconds
        }
    }

    Write-Warn "Health check no confirmo status UP despues de $maxAttempts intentos"
    Write-Host "  Verificar manualmente: $($URLS.HealthEndpoint)"
    return $false
}

function Show-Status {
    Write-Step "STATUS" "Estado actual del App Service..."

    $state = az webapp show `
        --resource-group $AZURE.ResourceGroup `
        --name $AZURE.WebAppName `
        --query "{state:state, defaultHostName:defaultHostName, enabled:enabled, httpsOnly:httpsOnly}" `
        -o json 2>$null | ConvertFrom-Json

    if ($state) {
        Write-Host "  Estado:   $($state.state)" -ForegroundColor $(if ($state.state -eq "Running") { "Green" } else { "Red" })
        Write-Host "  URL:      https://$($state.defaultHostName)"
        Write-Host "  HTTPS:    $($state.httpsOnly)"
        Write-Host "  Habilitado: $($state.enabled)"
    }
    else {
        Write-Fail "No se pudo obtener estado del App Service"
    }

    Write-Host ""
    try {
        $health = Invoke-RestMethod -Uri $URLS.HealthEndpoint -TimeoutSec 10 -ErrorAction Stop
        Write-Host "  Health:   $($health.status)" -ForegroundColor $(if ($health.status -eq "UP") { "Green" } else { "Yellow" })
        if ($health.components) {
            foreach ($comp in $health.components.PSObject.Properties) {
                $cStatus = $comp.Value.status
                $color = if ($cStatus -eq "UP") { "Green" } else { "Red" }
                Write-Host "    - $($comp.Name): $cStatus" -ForegroundColor $color
            }
        }
    }
    catch {
        Write-Warn "Health endpoint no responde"
    }
}

function Show-Logs {
    Write-Step "LOGS" "Mostrando logs recientes del App Service..."
    az webapp log tail `
        --resource-group $AZURE.ResourceGroup `
        --name $AZURE.WebAppName
}

# ============================================================================
# MAIN
# ============================================================================

$startTime = Get-Date

Write-Host ""
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  TravelBox Peru Backend - Azure Deploy" -ForegroundColor Cyan
Write-Host "  Accion: $Action | Entorno: $Environment" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

switch ($Action) {

    "status" {
        Test-AzureCli
        Test-AzureLogin
        Show-Status
    }

    "logs" {
        Test-AzureCli
        Test-AzureLogin
        Show-Logs
    }

    "restart" {
        Test-AzureCli
        Test-AzureLogin
        Restart-WebApp
        Test-HealthCheck
    }

    "config-only" {
        Test-AzureCli
        Test-AzureLogin
        $secrets = Get-KeyVaultSecrets
        Set-AzureAppSettings -Secrets $secrets
        Restart-WebApp
    }

    "build-only" {
        Test-JavaVersion
        $jarPath = Build-Maven
        Write-Ok "JAR listo en: $jarPath"
    }

    "deploy" {
        # Paso 1: Pre-requisitos
        Write-Step "PRE" "Verificando pre-requisitos..."
        Test-AzureCli
        Test-AzureLogin

        if (-not $SkipBuild) {
            Test-JavaVersion
        }
        Write-Ok "Pre-requisitos OK"

        # Paso 2: Key Vault
        if (-not $SkipConfig) {
            $secrets = Get-KeyVaultSecrets
        }

        # Paso 3: Build
        if (-not $SkipBuild) {
            $jarPath = Build-Maven
        }
        else {
            $jarPath = Get-ChildItem -Path "$PSScriptRoot\target" -Filter "*.jar" |
            Where-Object { $_.Name -notlike "*.jar.original" } |
            Select-Object -First 1 -ExpandProperty FullName

            if (-not $jarPath) {
                Write-Fail "No se encontro JAR existente en target/. Ejecutar sin -SkipBuild"
                exit 1
            }
            Write-Ok "Usando JAR existente: $([System.IO.Path]::GetFileName($jarPath))"
        }

        # Paso 4: Config App Settings
        if (-not $SkipConfig) {
            Set-AzureAppSettings -Secrets $secrets
        }

        # Paso 5: Deploy
        Deploy-ToAzure -JarPath $jarPath

        # Paso 6: Restart
        Restart-WebApp

        # Paso 7: Health Check
        Test-HealthCheck

        $elapsed = (Get-Date) - $startTime
        Write-Host ""
        Write-Host "========================================================" -ForegroundColor Green
        Write-Host "  DESPLIEGUE COMPLETADO" -ForegroundColor Green
        Write-Host "  Tiempo: $([math]::Round($elapsed.TotalMinutes, 1)) minutos" -ForegroundColor Green
        Write-Host "  URL: $($URLS.BackendPublic)" -ForegroundColor Green
        Write-Host "  Health: $($URLS.HealthEndpoint)" -ForegroundColor Green
        Write-Host "========================================================" -ForegroundColor Green
    }
}
