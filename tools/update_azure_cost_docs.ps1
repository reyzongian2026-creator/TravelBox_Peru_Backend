param(
    [string]$SubscriptionId,
    [string]$DocsDirectory = (Join-Path $PSScriptRoot "..\docs"),
    [string]$SnapshotPath = (Join-Path $PSScriptRoot "..\docs\AZURE_COST_SNAPSHOT.json"),
    [string]$RuntimeSnapshotPath = (Join-Path $PSScriptRoot "..\src\main\resources\admin\azure-cost-snapshot.json"),
    [switch]$SkipExchangeRateLookup
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

function Invoke-AzJson {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $output = & az @Arguments --only-show-errors -o json
    if ($LASTEXITCODE -ne 0) {
        throw "Azure CLI command failed: az $($Arguments -join ' ')"
    }
    if ([string]::IsNullOrWhiteSpace($output)) {
        return $null
    }
    return $output | ConvertFrom-Json
}

function Convert-ToFlatArray {
    param(
        [Parameter(ValueFromPipeline = $true)]
        [object]$Value
    )

    return @($Value | ForEach-Object { $_ })
}

function Invoke-CostQuery {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ScopeUrl,
        [Parameter(Mandatory = $true)]
        [hashtable]$Body
    )

    $tempFile = [System.IO.Path]::Combine(
        [System.IO.Path]::GetTempPath(),
        "cost-query-$([System.Guid]::NewGuid().ToString('N')).json"
    )

    try {
        ($Body | ConvertTo-Json -Depth 20) | Set-Content -Path $tempFile -Encoding ascii
        $result = & az rest `
            --method post `
            --url $ScopeUrl `
            --headers "Content-Type=application/json" `
            --body "@$tempFile" `
            --only-show-errors `
            -o json

        if ($LASTEXITCODE -ne 0) {
            throw "Cost query failed for scope $ScopeUrl"
        }

        return $result | ConvertFrom-Json
    }
    finally {
        if (Test-Path $tempFile) {
            Remove-Item -LiteralPath $tempFile -Force
        }
    }
}

function Get-RetailItems {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Filter
    )

    $encodedFilter = [System.Uri]::EscapeDataString($Filter)
    $url = "https://prices.azure.com/api/retail/prices?`$filter=$encodedFilter"
    $response = Invoke-RestMethod -Uri $url
    return @($response.Items)
}

function Get-UsdToPenRate {
    param(
        [switch]$SkipLookup
    )

    if ($SkipLookup) {
        return [pscustomobject]@{
            Rate = $null
            Source = "Lookup skipped"
            Timestamp = $null
        }
    }

    try {
        $response = Invoke-RestMethod -Uri "https://open.er-api.com/v6/latest/USD"
        if ($response.result -eq "success" -and $response.rates.PEN) {
            return [pscustomobject]@{
                Rate = [double]$response.rates.PEN
                Source = "open.er-api.com"
                Timestamp = $response.time_last_update_utc
            }
        }
    }
    catch {
    }

    return [pscustomobject]@{
        Rate = $null
        Source = "Unavailable"
        Timestamp = $null
    }
}

function Convert-ToPen {
    param(
        [double]$UsdValue,
        [double]$Rate
    )

    if ($null -eq $Rate) {
        return $null
    }

    return [math]::Round($UsdValue * $Rate, 2)
}

function Find-PriceItem {
    param(
        [Parameter(Mandatory = $true)]
        [object[]]$Items,
        [Parameter(Mandatory = $true)]
        [scriptblock]$Predicate
    )

    foreach ($item in $Items) {
        if (& $Predicate $item) {
            return $item
        }
    }

    return $null
}

function Convert-CostRowsToObjects {
    param(
        [Parameter(Mandatory = $true)]
        [object]$QueryResult
    )

    $columns = @($QueryResult.properties.columns)
    $rows = @($QueryResult.properties.rows)
    $results = @()

    foreach ($row in $rows) {
        $item = [ordered]@{}
        for ($i = 0; $i -lt $columns.Count; $i++) {
            $item[$columns[$i].name] = $row[$i]
        }
        $results += [pscustomobject]$item
    }

    return $results
}

function New-MarkdownTable {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Headers,
        [Parameter(Mandatory = $true)]
        [object[][]]$Rows
    )

    $lines = @()
    $lines += "| " + ($Headers -join " | ") + " |"
    $lines += "|" + (($Headers | ForEach-Object { "---" }) -join "|") + "|"

    foreach ($row in $Rows) {
        $values = foreach ($value in $row) {
            if ($null -eq $value) { "" } else { [string]$value }
        }
        $lines += "| " + ($values -join " | ") + " |"
    }

    return ($lines -join "`n")
}

function Format-Usd {
    param([double]$Value)
    return ("{0:N6}" -f $Value)
}

function Format-UsdShort {
    param([double]$Value)
    return ("{0:N2}" -f $Value)
}

function Format-Pen {
    param([Nullable[double]]$Value)
    if ($null -eq $Value) {
        return "N/A"
    }
    return ("S/ {0:N2}" -f $Value)
}

function AsCode {
    param([string]$Text)
    return "``$Text``"
}

function Get-ResourceMetric {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ResourceId,
        [Parameter(Mandatory = $true)]
        [string]$MetricNames
    )

    return Invoke-AzJson -Arguments @(
        "monitor", "metrics", "list",
        "--resource", $ResourceId,
        "--metric", $MetricNames,
        "--interval", "PT1H",
        "--aggregation", "Average", "Maximum"
    )
}

function Get-LastMetricValue {
    param(
        [Parameter(Mandatory = $true)]
        [object]$MetricResponse,
        [Parameter(Mandatory = $true)]
        [string]$MetricName
    )

    $metric = @($MetricResponse.value) | Where-Object { $_.name.value -eq $MetricName } | Select-Object -First 1
    if (-not $metric) {
        return $null
    }

    $series = @($metric.timeseries) | Select-Object -First 1
    if (-not $series) {
        return $null
    }

    $data = @($series.data) | Where-Object { $null -ne $_.average -or $null -ne $_.maximum }
    if (-not $data) {
        return $null
    }

    $last = $data[-1]
    return [pscustomobject]@{
        Average = $last.average
        Maximum = $last.maximum
        Unit = $metric.unit
    }
}

$account = Invoke-AzJson -Arguments @("account", "show")
if (-not $SubscriptionId) {
    $SubscriptionId = $account.id
}

$now = Get-Date
$nowLabel = $now.ToString("MMMM d, yyyy HH:mm K", [System.Globalization.CultureInfo]::InvariantCulture)
$dayOfMonth = [math]::Max($now.Day, 1)

$exchange = Get-UsdToPenRate -SkipLookup:$SkipExchangeRateLookup
$usdToPen = $exchange.Rate

$resources = Convert-ToFlatArray (Invoke-AzJson -Arguments @("resource", "list", "--subscription", $SubscriptionId))
$budgets = Convert-ToFlatArray (Invoke-AzJson -Arguments @("consumption", "budget", "list", "--subscription", $SubscriptionId))

$costByRgQuery = Invoke-CostQuery `
    -ScopeUrl "https://management.azure.com/subscriptions/$SubscriptionId/providers/Microsoft.CostManagement/query?api-version=2025-03-01" `
    -Body @{
        type = "Usage"
        timeframe = "MonthToDate"
        dataset = @{
            aggregation = @{
                totalCost = @{
                    name = "PreTaxCost"
                    function = "Sum"
                }
            }
            granularity = "None"
            grouping = @(
                @{
                    type = "Dimension"
                    name = "ResourceGroup"
                }
            )
        }
    }

$costRows = @(Convert-CostRowsToObjects -QueryResult $costByRgQuery) | Sort-Object ResourceGroup
$mtdTotalUsd = ($costRows | Measure-Object -Property PreTaxCost -Sum).Sum
if ($null -eq $mtdTotalUsd) {
    $mtdTotalUsd = 0.0
}

$backendPlan = Invoke-AzJson -Arguments @("appservice", "plan", "show", "--resource-group", "travelbox-peru-bs-rg", "--name", "travelbox-peru-plan-linux-bs")
$backendApp = Invoke-AzJson -Arguments @("webapp", "show", "--resource-group", "travelbox-peru-bs-rg", "--name", "travelbox-backend-bs")
$postgres = Invoke-AzJson -Arguments @("postgres", "flexible-server", "show", "--resource-group", "travelbox-peru-bs-rg", "--name", "travelbox-peru-db-bs")
$frontend = Invoke-AzJson -Arguments @("staticwebapp", "show", "--resource-group", "travelbox-peru-rg", "--name", "travelbox-frontend")
$appInsights = Invoke-AzJson -Arguments @("monitor", "app-insights", "component", "show", "--app", "travelbox-appinsights-bs", "--resource-group", "travelbox-peru-bs-rg")
$workspace = Invoke-AzJson -Arguments @("monitor", "log-analytics", "workspace", "show", "--resource-group", "ai_travelbox-appinsights-bs_a3afbd61-8f6d-4797-a17e-586d95dfacab_managed", "--workspace-name", "managed-travelbox-appinsights-bs-ws")
$keyVault = Invoke-AzJson -Arguments @("keyvault", "show", "--resource-group", "travelbox_kv_bs", "--name", "kvtravelboxpebs")
$storageAccounts = Convert-ToFlatArray (Invoke-AzJson -Arguments @("storage", "account", "list", "--query", "[?resourceGroup=='travelbox_st_bs']"))
$translator = Invoke-AzJson -Arguments @("cognitiveservices", "account", "show", "--resource-group", "travelbox-rg", "--name", "travelbox-translator")
$maps = (Convert-ToFlatArray (Invoke-AzJson -Arguments @("maps", "account", "list"))) | Where-Object { $_.name -eq "travelbox-maps" } | Select-Object -First 1
$communicationService = (Convert-ToFlatArray (Invoke-AzJson -Arguments @("communication", "list"))) | Where-Object { $_.name -eq "travelbox-communications" } | Select-Object -First 1
$emailServices = Convert-ToFlatArray (Invoke-AzJson -Arguments @("communication", "email", "list"))
$emailService = $emailServices | Where-Object { $_.name -eq "inkavoy-email" } | Select-Object -First 1
$emailDomains = Convert-ToFlatArray (Invoke-AzJson -Arguments @("communication", "email", "domain", "list", "--resource-group", "inkavoy-rg", "--email-service-name", "inkavoy-email"))
$emailDomain = $emailDomains | Select-Object -First 1
$openAi = Invoke-AzJson -Arguments @("cognitiveservices", "account", "show", "--resource-group", "rg-getting-started-service-260320072430", "--name", "getting-started-service-260320072430")
$openAiDeployments = Convert-ToFlatArray (Invoke-AzJson -Arguments @("cognitiveservices", "account", "deployment", "list", "--resource-group", "rg-getting-started-service-260320072430", "--name", "getting-started-service-260320072430"))

$appPlanMetrics = Get-ResourceMetric -ResourceId $backendPlan.id -MetricNames "CpuPercentage,MemoryPercentage"
$postgresMetrics = Get-ResourceMetric -ResourceId $postgres.id -MetricNames "storage_used,cpu_percent,memory_percent"

$cpuPlanMetric = Get-LastMetricValue -MetricResponse $appPlanMetrics -MetricName "CpuPercentage"
$memoryPlanMetric = Get-LastMetricValue -MetricResponse $appPlanMetrics -MetricName "MemoryPercentage"
$cpuDbMetric = Get-LastMetricValue -MetricResponse $postgresMetrics -MetricName "cpu_percent"
$memoryDbMetric = Get-LastMetricValue -MetricResponse $postgresMetrics -MetricName "memory_percent"
$storageDbMetric = Get-LastMetricValue -MetricResponse $postgresMetrics -MetricName "storage_used"

$storageUsage = @()
foreach ($storageAccount in $storageAccounts) {
    $storageResourceId = [string]$storageAccount.id
    $storageResourceName = [string]$storageAccount.name
    $metricResponse = Get-ResourceMetric -ResourceId $storageResourceId -MetricNames "UsedCapacity"
    $usedMetric = Get-LastMetricValue -MetricResponse $metricResponse -MetricName "UsedCapacity"
    $usedBytes = 0.0
    if ($usedMetric) {
        $usedBytes = [double]$usedMetric.Average
    }
    $storageUsage += [pscustomobject]@{
        Name = $storageResourceName
        UsedBytes = $usedBytes
    }
}

$retailAppService = Get-RetailItems -Filter "serviceName eq 'Azure App Service' and armRegionName eq 'brazilsouth' and meterName eq 'B2 App' and type eq 'Consumption'"
$retailPostgres = Get-RetailItems -Filter "armRegionName eq 'brazilsouth' and serviceName eq 'Azure Database for PostgreSQL'"
$retailStorage = Get-RetailItems -Filter "serviceName eq 'Storage' and armRegionName eq 'brazilsouth' and productName eq 'Blob Storage' and skuName eq 'Hot RA-GRS' and meterName eq 'Hot RA-GRS Data Stored'"
$retailLogAnalytics = Get-RetailItems -Filter "serviceName eq 'Log Analytics' and armRegionName eq 'brazilsouth'"
$retailMaps = Get-RetailItems -Filter "serviceName eq 'Azure Maps' and armRegionName eq 'eastus'"
$retailTranslator = Get-RetailItems -Filter "contains(productName,'Translator')"
$retailEmail = Get-RetailItems -Filter "contains(productName,'Email') or contains(serviceName,'Communication')"

$appPrice = Find-PriceItem -Items $retailAppService -Predicate { param($item) $item.meterName -eq "B2 App" -and $item.type -eq "Consumption" }
$pgComputePrice = Find-PriceItem -Items $retailPostgres -Predicate { param($item) $item.productName -eq "Azure Database for PostgreSQL Flexible Server Burstable BS Series Compute" -and $item.skuName -eq "B1MS" -and $item.type -eq "Consumption" }
$pgStoragePrice = Find-PriceItem -Items $retailPostgres -Predicate { param($item) $item.productName -eq "Az DB for PostgreSQL Flexible Server Storage" -and $item.skuName -eq "Storage" -and $item.type -eq "Consumption" }
$blobStoragePrice = Find-PriceItem -Items $retailStorage -Predicate { param($item) $item.meterName -eq "Hot RA-GRS Data Stored" -and $item.type -eq "Consumption" }
$logAnalyticsIngestionPrice = Find-PriceItem -Items $retailLogAnalytics -Predicate { param($item) $item.meterName -eq "Analytics Logs Data Ingestion" -and [double]$item.unitPrice -gt 0 }
$logAnalyticsRetentionPrice = Find-PriceItem -Items $retailLogAnalytics -Predicate { param($item) $item.meterName -eq "Analytics Logs Data Retention" }
$translatorPrice = Find-PriceItem -Items $retailTranslator -Predicate { param($item) $item.productName -eq "Azure Translator" -and $item.meterName -eq "S1 Standard Characters" }
$mapsGeoPrice = Find-PriceItem -Items $retailMaps -Predicate { param($item) $item.meterName -eq "Standard Geolocation Transactions" -and [double]$item.unitPrice -gt 0 }
$mapsTilePrice = Find-PriceItem -Items $retailMaps -Predicate { param($item) $item.meterName -eq "Standard Tile Transactions" -and [double]$item.unitPrice -gt 0 }
$mapsRoutePrice = Find-PriceItem -Items $retailMaps -Predicate { param($item) $item.meterName -eq "Standard S1 Routing Transactions" -and [double]$item.unitPrice -gt 0 }
$emailSentPrice = Find-PriceItem -Items $retailEmail -Predicate { param($item) $item.meterName -eq "Basic Sent Email" }
$emailDataPrice = Find-PriceItem -Items $retailEmail -Predicate { param($item) $item.meterName -eq "Basic Data Transferred" }

$hoursPerMonth = 730
$appMonthlyUsd = 0.0
if ($appPrice) { $appMonthlyUsd = [double]$appPrice.unitPrice * $hoursPerMonth }
$pgComputeMonthlyUsd = 0.0
if ($pgComputePrice) { $pgComputeMonthlyUsd = [double]$pgComputePrice.unitPrice * $hoursPerMonth }
$pgStorageMonthlyUsd = 0.0
if ($pgStoragePrice) { $pgStorageMonthlyUsd = [double]$pgStoragePrice.unitPrice * [double]$postgres.storage.storageSizeGb }
$storageUsedGb = (($storageUsage | Measure-Object -Property UsedBytes -Sum).Sum) / 1GB
$blobStorageMonthlyUsd = 0.0
if ($blobStoragePrice) { $blobStorageMonthlyUsd = [double]$blobStoragePrice.unitPrice * $storageUsedGb }

$keyVaultCostRow = $costRows | Where-Object { $_.ResourceGroup -eq "travelbox_kv_bs" } | Select-Object -First 1
$keyVaultMtdUsd = 0.0
if ($keyVaultCostRow) { $keyVaultMtdUsd = [double]$keyVaultCostRow.PreTaxCost }
$keyVaultMonthlyUsd = 0.0
if ($keyVaultMtdUsd -gt 0) { $keyVaultMonthlyUsd = ($keyVaultMtdUsd / $dayOfMonth) * 30 }

$baseMonthlyUsd = $appMonthlyUsd + $pgComputeMonthlyUsd + $pgStorageMonthlyUsd + $blobStorageMonthlyUsd + $keyVaultMonthlyUsd
$postgresMonthlyUsd = $pgComputeMonthlyUsd + $pgStorageMonthlyUsd
$translatorMonthlyUsd = 0.0
$mapsMonthlyUsd = 0.0
$frontendMonthlyUsd = 0.0

$snapshot = [ordered]@{
    generatedAt = $now.ToString("o")
    subscription = [ordered]@{
        id = $account.id
        name = $account.name
        tenantId = $account.tenantId
        tenantDisplayName = $account.tenantDisplayName
    }
    exchangeRate = [ordered]@{
        usdToPen = $usdToPen
        source = $exchange.Source
        timestamp = $exchange.Timestamp
    }
    totals = [ordered]@{
        monthToDateUsd = [math]::Round($mtdTotalUsd, 6)
        baseMonthlyUsd = [math]::Round($baseMonthlyUsd, 4)
    }
    dashboard = [ordered]@{
        currency = "USD"
        totalMonthlyUsd = [math]::Round($baseMonthlyUsd, 4)
        items = @(
            [ordered]@{
                service = "App Service Backend"
                sku = "$($backendPlan.sku.name) Linux"
                amount = [math]::Round($appMonthlyUsd, 2)
                period = "monthly"
            },
            [ordered]@{
                service = "Static Web App Frontend"
                sku = "$($frontend.sku.name)"
                amount = [math]::Round($frontendMonthlyUsd, 2)
                period = "monthly"
            },
            [ordered]@{
                service = "PostgreSQL Flexible"
                sku = "$($postgres.sku.name) + $($postgres.storage.storageSizeGb)GB"
                amount = [math]::Round($postgresMonthlyUsd, 2)
                period = "monthly"
            },
            [ordered]@{
                service = "Azure AI Translator"
                sku = "$($translator.sku.name)"
                amount = [math]::Round($translatorMonthlyUsd, 2)
                period = "usage-based"
            },
            [ordered]@{
                service = "Azure Maps"
                sku = "$($maps.sku.name)"
                amount = [math]::Round($mapsMonthlyUsd, 2)
                period = "usage-based"
            },
            [ordered]@{
                service = "Key Vault"
                sku = "$($keyVault.properties.sku.name)"
                amount = [math]::Round($keyVaultMonthlyUsd, 2)
                period = "monthly"
            },
            [ordered]@{
                service = "Blob Storage"
                sku = "$([math]::Round($storageUsedGb, 6))GB current footprint"
                amount = [math]::Round($blobStorageMonthlyUsd, 2)
                period = "monthly"
            }
        )
    }
    activeResourceIds = @($resources | ForEach-Object { $_.id } | Sort-Object)
}

$previousSnapshot = $null
if (Test-Path $SnapshotPath) {
    try {
        $previousSnapshot = Get-Content -Path $SnapshotPath -Raw | ConvertFrom-Json
    }
    catch {
        $previousSnapshot = $null
    }
}

$changeLines = @()
if ($previousSnapshot) {
    $previousIds = @($previousSnapshot.activeResourceIds)
    $currentIds = @($snapshot.activeResourceIds)
    $newIds = $currentIds | Where-Object { $_ -notin $previousIds }
    $removedIds = $previousIds | Where-Object { $_ -notin $currentIds }

    if ($newIds.Count -gt 0) {
        $changeLines += "- Nuevos recursos detectados: $(($newIds | Measure-Object).Count)"
    }
    if ($removedIds.Count -gt 0) {
        $changeLines += "- Recursos eliminados desde el ultimo snapshot: $(($removedIds | Measure-Object).Count)"
    }

    $previousBase = [double]$previousSnapshot.totals.baseMonthlyUsd
    $deltaBase = [math]::Round($baseMonthlyUsd - $previousBase, 4)
    if ($deltaBase -ne 0) {
        $changeLines += "- Cambio en proyeccion base mensual: USD $deltaBase"
    }
}
else {
    $changeLines += "- No habia snapshot previo. Este es el primer baseline generado."
}

if ($changeLines.Count -eq 0) {
    $changeLines += "- No se detectaron cambios en recursos ni en la proyeccion base mensual."
}

$budgetStatus = $null
if (@($budgets).Count -gt 0) {
    $budgetStatus = AsCode "Yes"
}
else {
    $budgetStatus = AsCode "No"
}
$backendAppNotes = (AsCode $backendApp.siteConfig.linuxFxVersion) + ", " + (AsCode "httpsOnly=$($backendApp.httpsOnly.ToString().ToLowerInvariant())") + ", domain " + (AsCode "api.inkavoy.pe")
$certificateNotes = "TLS for " + (AsCode "api.inkavoy.pe")
$frontendNotes = "Custom domain " + (AsCode "www.inkavoy.pe")
$emailDomainNotes = "Domain " + (AsCode "inkavoy.pe")
$openAiNotes = $null
if ($openAiDeployments.Count -gt 0) {
    $openAiNotes = "$($openAiDeployments.Count) model deployment(s)"
}
else {
    $openAiNotes = "No model deployments found"
}

$subscriptionInfoTable = New-MarkdownTable -Headers @("Field", "Value") -Rows @(
    @("Subscription ID", (AsCode $account.id)),
    @("Subscription Name", (AsCode $account.name)),
    @("Tenant ID", (AsCode $account.tenantId)),
    @("Tenant", (AsCode $account.tenantDisplayName)),
    @("Primary Billing Currency", (AsCode "USD")),
    @("Azure Budgets Configured", $budgetStatus)
)

$currentProductionRows = @(
    @((AsCode "travelbox-peru-bs-rg"), "App Service Plan", (AsCode $backendPlan.name), "Brazil South", (AsCode "$($backendPlan.sku.name) Linux"), "Running", "1 worker, max $($backendPlan.properties.maximumNumberOfWorkers) workers"),
    @((AsCode "travelbox-peru-bs-rg"), "App Service", (AsCode $backendApp.name), "Brazil South", "Attached to $(AsCode $backendPlan.sku.name) plan", "$($backendApp.state)", $backendAppNotes),
    @((AsCode "travelbox-peru-bs-rg"), "PostgreSQL Flexible Server", (AsCode $postgres.name), "Brazil South", (AsCode $postgres.sku.name), "$($postgres.state)", "PostgreSQL $($postgres.version), $($postgres.storage.storageSizeGb) GB allocated storage, $($postgres.backup.backupRetentionDays)-day backups"),
    @((AsCode "travelbox-peru-bs-rg"), "Application Insights", (AsCode $appInsights.name), "Brazil South", "Pay-as-you-go", "$($appInsights.provisioningState)", "Retention $($appInsights.retentionInDays) days, workspace-based"),
    @((AsCode $workspace.resourceGroup), "Log Analytics Workspace", (AsCode $workspace.name), "Brazil South", (AsCode $workspace.sku.name), "$($workspace.provisioningState)", "Retention $($workspace.retentionInDays) days"),
    @((AsCode "travelbox_kv_bs"), "Key Vault", (AsCode $keyVault.name), "Brazil South", (AsCode $keyVault.properties.sku.name), "$($keyVault.properties.provisioningState)", "RBAC enabled, purge protection enabled"),
    @((AsCode "travelbox_st_bs"), "Storage Account", (AsCode "travelboximgbs01"), "Brazil South", (AsCode "StorageV2 / Standard_RAGRS"), "Succeeded", "Images and media blobs"),
    @((AsCode "travelbox_st_bs"), "Storage Account", (AsCode "travelboxrepbs01"), "Brazil South", (AsCode "StorageV2 / Standard_RAGRS"), "Succeeded", "Reports and generated files"),
    @((AsCode "travelbox-peru-bs-rg"), "Managed Certificate", (AsCode "api-inkavoy-pe-bs"), "Brazil South", "Managed", "Succeeded", $certificateNotes)
)

$frontendRows = @()
$frontendRows += ,@((AsCode "travelbox-peru-rg"), "Static Web App", (AsCode $frontend.name), "West US 2", (AsCode $frontend.sku.name), "Succeeded", $frontendNotes)

$sharedRows = @(
    @((AsCode "travelbox-rg"), "Translator", (AsCode $translator.name), "Global", (AsCode $translator.sku.name), "$($translator.properties.provisioningState)", "Usage-based"),
    @((AsCode "travelbox-rg"), "Azure Maps", (AsCode $maps.name), "East US", (AsCode $maps.sku.name), "$($maps.provisioningState)", "Usage-based"),
    @((AsCode "travelbox-rg"), "Communication Service", (AsCode $communicationService.name), "Global", "Pay-as-you-go", "$($communicationService.provisioningState)", "Base communication resource"),
    @((AsCode "inkavoy-rg"), "Email Service", (AsCode $emailService.name), "Global", "Basic / usage-based", "$($emailDomain.provisioningState)", "Customer-managed domain"),
    @((AsCode "inkavoy-rg"), "Email Domain", (AsCode $emailDomain.name), "Global", "Included", "$($emailDomain.provisioningState)", $emailDomainNotes),
    @((AsCode "rg-getting-started-service-260320072430"), "Azure OpenAI Resource", (AsCode $openAi.name), "East US", (AsCode $openAi.sku.name), "$($openAi.properties.provisioningState)", $openAiNotes)
)

$alertRows = @()
foreach ($resource in $resources | Where-Object { $_.resourceGroup -eq "azureapp-auto-alerts-22d313-reyzongian_outlook_es" } | Sort-Object name) {
    $service = ($resource.type -split "/")[-1]
    $alertRows += ,@((AsCode $resource.resourceGroup), $service, (AsCode $resource.name), "Global", "Auto-created alerting resource")
}

$azureResourcesContent = @()
$azureResourcesContent += "# Azure Resources - TravelBox Peru"
$azureResourcesContent += ""
$azureResourcesContent += "**Last Updated:** $nowLabel"
$azureResourcesContent += ""
$azureResourcesContent += '**Generated By:** `tools/update_azure_cost_docs.ps1`'
$azureResourcesContent += ""
$azureResourcesContent += "---"
$azureResourcesContent += ""
$azureResourcesContent += "## Subscription Information"
$azureResourcesContent += ""
$azureResourcesContent += $subscriptionInfoTable
$azureResourcesContent += ""
$azureResourcesContent += "---"
$azureResourcesContent += ""
$azureResourcesContent += "## Current Production Stack"
$azureResourcesContent += ""
$azureResourcesContent += 'The current production backend was migrated on April 8, 2026 to `Brazil South`.'
$azureResourcesContent += "The previous West US 3 resources were removed and only appear in Cost Management as early-April legacy charges."
$azureResourcesContent += ""
$azureResourcesContent += "### Core Backend"
$azureResourcesContent += ""
$azureResourcesContent += (New-MarkdownTable -Headers @("Resource Group", "Service", "Name", "Location", "SKU / Plan", "Current State", "Notes") -Rows $currentProductionRows)
$azureResourcesContent += ""
$azureResourcesContent += "### Frontend"
$azureResourcesContent += ""
$azureResourcesContent += (New-MarkdownTable -Headers @("Resource Group", "Service", "Name", "Location", "SKU / Plan", "Current State", "Notes") -Rows $frontendRows)
$azureResourcesContent += ""
$azureResourcesContent += "### Shared / Auxiliary Azure Services"
$azureResourcesContent += ""
$azureResourcesContent += (New-MarkdownTable -Headers @("Resource Group", "Service", "Name", "Location", "SKU / Plan", "Current State", "Notes") -Rows $sharedRows)
$azureResourcesContent += ""
$azureResourcesContent += "### Operational / Alerting Resources"
$azureResourcesContent += ""
$azureResourcesContent += (New-MarkdownTable -Headers @("Resource Group", "Service", "Name", "Location", "Notes") -Rows @($alertRows))
$azureResourcesContent += ""
$azureResourcesContent += "---"
$azureResourcesContent += ""
$azureResourcesContent += "## Current Utilization Snapshot"
$azureResourcesContent += ""
$azureResourcesContent += "These values are the live snapshot collected on $($now.ToString('MMMM d, yyyy', [System.Globalization.CultureInfo]::InvariantCulture))."
$azureResourcesContent += ""
$azureResourcesContent += '### App Service Plan `travelbox-peru-plan-linux-bs`'
$azureResourcesContent += ""
$azureResourcesContent += (New-MarkdownTable -Headers @("Metric", "Last Avg", "Last Max") -Rows @(
    @("CPU Percentage", (AsCode "$([math]::Round([double]$cpuPlanMetric.Average, 2))%"), (AsCode "$([math]::Round([double]$cpuPlanMetric.Maximum, 2))%")),
    @("Memory Percentage", (AsCode "$([math]::Round([double]$memoryPlanMetric.Average, 2))%"), (AsCode "$([math]::Round([double]$memoryPlanMetric.Maximum, 2))%"))
))
$azureResourcesContent += ""
$azureResourcesContent += '### PostgreSQL `travelbox-peru-db-bs`'
$azureResourcesContent += ""
$azureResourcesContent += (New-MarkdownTable -Headers @("Metric", "Last Avg", "Last Max") -Rows @(
    @("CPU Percentage", (AsCode "$([math]::Round([double]$cpuDbMetric.Average, 2))%"), (AsCode "$([math]::Round([double]$cpuDbMetric.Maximum, 2))%")),
    @("Memory Percentage", (AsCode "$([math]::Round([double]$memoryDbMetric.Average, 2))%"), (AsCode "$([math]::Round([double]$memoryDbMetric.Maximum, 2))%")),
    @("Storage Used", (AsCode "$([math]::Round(([double]$storageDbMetric.Average / 1GB), 2)) GiB"), (AsCode "$([math]::Round(([double]$storageDbMetric.Maximum / 1GB), 2)) GiB"))
))
$azureResourcesContent += ""
$azureResourcesContent += "### Blob Storage Used Capacity"
$azureResourcesContent += ""
$blobRows = @()
foreach ($item in $storageUsage | Sort-Object Name) {
    $label = $null
    if ($item.Name -eq "travelboximgbs01") {
        $label = AsCode "$([math]::Round($item.UsedBytes / 1MB, 2)) MiB"
    }
    else {
        $label = AsCode "$([math]::Round($item.UsedBytes / 1KB, 2)) KiB"
    }
    $blobRows += ,@((AsCode $item.Name), $label)
}
$azureResourcesContent += (New-MarkdownTable -Headers @("Storage Account", "Current Used Capacity") -Rows @($blobRows))
$azureResourcesContent += ""
$azureResourcesContent += "---"
$azureResourcesContent += ""
$azureResourcesContent += "## Detected Changes Since Previous Snapshot"
$azureResourcesContent += ""
$azureResourcesContent += ($changeLines -join "`n")
$azureResourcesContent += ""
$azureResourcesContent += "---"
$azureResourcesContent += ""
$azureResourcesContent += "## How To Refresh"
$azureResourcesContent += ""
$azureResourcesContent += '```powershell'
$azureResourcesContent += "powershell -ExecutionPolicy Bypass -File .\tools\update_azure_cost_docs.ps1"
$azureResourcesContent += '```'

$costRowsTable = @()
foreach ($row in $costRows) {
    $usdValue = [double]$row.PreTaxCost
    $status = switch ($row.ResourceGroup) {
        "travelbox-peru-bs-rg" { "New active backend RG, billing lag" }
        "ai_travelbox-appinsights-bs_a3afbd61-8f6d-4797-a17e-586d95dfacab_managed" { "New active workspace RG, billing lag" }
        "travelbox_kv_bs" { "Active" }
        "travelbox_st_bs" { "Active, billing lag" }
        "travelbox-peru-rg" { "Legacy April charges from deleted West US 3 resources plus free frontend RG" }
        "travelbox_kv" { "Legacy deleted RG" }
        "travelbox_st" { "Legacy deleted RG" }
        "travelbox-rg" { "Active shared services, no billed usage yet" }
        "inkavoy-rg" { "Active email resources, no billed usage yet" }
        "rg-getting-started-service-260320072430" { "OpenAI resource with no deployments" }
        default { "Tracked" }
    }
    $costRowsTable += ,@((AsCode $row.ResourceGroup), (AsCode (Format-Usd $usdValue)), (AsCode (Format-Pen (Convert-ToPen -UsdValue $usdValue -Rate $usdToPen))), $status)
}

$costContent = @()
$costContent += "# Cost Estimation - TravelBox Peru"
$costContent += ""
$costContent += "**Last Updated:** $nowLabel  "
$costContent += "**Primary Billing Currency:** USD  "
if ($usdToPen) {
    $costContent += "**Reference Exchange Rate Used:** " + (AsCode "1 USD = S/ $([math]::Round($usdToPen, 4))")
}
else {
    $costContent += "**Reference Exchange Rate Used:** " + (AsCode "Unavailable")
}
$costContent += ""
$costContent += '**Generated By:** `tools/update_azure_cost_docs.ps1`'
$costContent += ""
$costContent += "---"
$costContent += ""
$costContent += "## Billing Notes"
$costContent += ""
$costContent += "1. These values are generated from live Azure APIs and current retail prices at generation time."
$costContent += "2. There are currently **$($budgets.Count) Azure Budget resource(s)** configured in this subscription."
$costContent += "3. Azure Cost Management can show **billing lag**, especially for resources created the same day."
$costContent += "4. PostgreSQL is billed by **provisioned compute and allocated storage**, not just by actual DB bytes used."
$costContent += "5. Blob Storage is billed by **stored data + operations + replication/egress**. The storage line below uses current stored data only, so it stays conservative."
$costContent += ""
$costContent += "---"
$costContent += ""
$costContent += "## Actual Month-To-Date Cost"
$costContent += ""
$costContent += "Snapshot from Azure Cost Management on $($now.ToString('MMMM d, yyyy', [System.Globalization.CultureInfo]::InvariantCulture))."
$costContent += ""
$costContent += "### By Resource Group"
$costContent += ""
$costContent += (New-MarkdownTable -Headers @("Resource Group", "MTD Cost (USD)", "MTD Cost (PEN)", "Status") -Rows @($costRowsTable))
$costContent += ""
$costContent += "### Subscription Total"
$costContent += ""
$costContent += (New-MarkdownTable -Headers @("Metric", "Value") -Rows @(
    @("Total month-to-date billed cost", (AsCode "USD $(Format-Usd $mtdTotalUsd)")),
    @("Total month-to-date billed cost in soles", (AsCode (Format-Pen (Convert-ToPen -UsdValue $mtdTotalUsd -Rate $usdToPen))))
))
$costContent += ""
$costContent += "---"
$costContent += ""
$costContent += "## Projected Monthly Steady-State Cost"
$costContent += ""
$costContent += "These values reflect the **current live stack** if it stays provisioned for a full month."
$costContent += ""
$costContent += "### Base Stack"
$costContent += ""
$openAiReference = $null
if ($openAiDeployments.Count -gt 0) {
    $openAiReference = "Resource has deployments; review model usage separately"
}
else {
    $openAiReference = "No deployments found, so no current runtime cost"
}
$costContent += (New-MarkdownTable -Headers @("Resource", "Pricing Basis", "Estimated Monthly USD", "Estimated Monthly PEN") -Rows @(
    @('App Service Plan `travelbox-peru-plan-linux-bs`', (AsCode "B2 App at USD $([math]::Round([double]$appPrice.unitPrice, 3))/hour * 730 hours"), (AsCode (Format-UsdShort $appMonthlyUsd)), (AsCode (Format-Pen (Convert-ToPen -UsdValue $appMonthlyUsd -Rate $usdToPen)))),
    @('PostgreSQL compute `travelbox-peru-db-bs`', (AsCode "B1MS at USD $([math]::Round([double]$pgComputePrice.unitPrice, 3))/hour * 730 hours"), (AsCode (Format-UsdShort $pgComputeMonthlyUsd)), (AsCode (Format-Pen (Convert-ToPen -UsdValue $pgComputeMonthlyUsd -Rate $usdToPen)))),
    @("PostgreSQL allocated storage", (AsCode "$($postgres.storage.storageSizeGb) GB * USD $([math]::Round([double]$pgStoragePrice.unitPrice, 4))/GB-month"), (AsCode (Format-UsdShort $pgStorageMonthlyUsd)), (AsCode (Format-Pen (Convert-ToPen -UsdValue $pgStorageMonthlyUsd -Rate $usdToPen)))),
    @("Blob Storage current data footprint", (AsCode "$([math]::Round($storageUsedGb, 6)) GB * USD $([math]::Round([double]$blobStoragePrice.unitPrice, 4))/GB-month"), (AsCode (Format-UsdShort $blobStorageMonthlyUsd)), (AsCode (Format-Pen (Convert-ToPen -UsdValue $blobStorageMonthlyUsd -Rate $usdToPen)))),
    @("Key Vault projected from current usage trend", (AsCode "Current MTD extrapolated linearly"), (AsCode (Format-UsdShort $keyVaultMonthlyUsd)), (AsCode (Format-Pen (Convert-ToPen -UsdValue $keyVaultMonthlyUsd -Rate $usdToPen)))),
    @('Static Web App `travelbox-frontend`', (AsCode "Free tier"), (AsCode "0.00"), (AsCode "S/ 0.00")),
    @('Managed certificate `api-inkavoy-pe-bs`', "Managed App Service certificate", (AsCode "0.00"), (AsCode "S/ 0.00"))
))
$costContent += ""
$costContent += "### Current Base Projection"
$costContent += ""
$costContent += (New-MarkdownTable -Headers @("Metric", "Value") -Rows @(
    @("Base monthly cost (USD)", (AsCode (Format-UsdShort $baseMonthlyUsd))),
    @("Base monthly cost (PEN)", (AsCode (Format-Pen (Convert-ToPen -UsdValue $baseMonthlyUsd -Rate $usdToPen))))
))
$costContent += ""
$costContent += "---"
$costContent += ""
$costContent += "## Variable Usage-Based Services"
$costContent += ""
$costContent += (New-MarkdownTable -Headers @("Resource", "Current Billed Usage", "Reference Price") -Rows @(
    @("Application Insights / Log Analytics", (AsCode "USD 0.00 so far"), "$(AsCode ("USD {0:N2}/GB" -f [double]$logAnalyticsIngestionPrice.unitPrice)) ingestion, $(AsCode ("USD {0:N2}/GB-month" -f [double]$logAnalyticsRetentionPrice.unitPrice)) extra retention"),
    @('Azure Translator `travelbox-translator`', (AsCode "USD 0.00 so far"), "$(AsCode ("USD {0:N2}/1M characters" -f [double]$translatorPrice.unitPrice)) for $(AsCode "S1 Standard Characters")"),
    @('Azure Maps `travelbox-maps`', (AsCode "USD 0.00 so far"), "Example references: $(AsCode ("USD {0:N2}/1K geolocation" -f [double]$mapsGeoPrice.unitPrice)), $(AsCode ("USD {0:N2}/1K standard tiles" -f [double]$mapsTilePrice.unitPrice)), $(AsCode ("USD {0:N2}/1K Standard S1 routes" -f [double]$mapsRoutePrice.unitPrice))"),
    @('Azure Email `inkavoy-email`', (AsCode "USD 0.00 so far"), "$(AsCode ("USD {0:N5}" -f [double]$emailSentPrice.unitPrice)) per sent email and $(AsCode ("USD {0:N5}/MB" -f [double]$emailDataPrice.unitPrice)) transferred"),
    @("Azure OpenAI resource", (AsCode "USD 0.00 so far"), $openAiReference),
    @("Communication Service", (AsCode "USD 0.00 so far"), "Usage-based, depends on channel and traffic")
))
$costContent += ""
$costContent += "---"
$costContent += ""
$costContent += "## Change Detection"
$costContent += ""
$costContent += ($changeLines -join "`n")
$costContent += ""
$costContent += "---"
$costContent += ""
$costContent += "## How To Refresh"
$costContent += ""
$costContent += '```powershell'
$costContent += "powershell -ExecutionPolicy Bypass -File .\tools\update_azure_cost_docs.ps1"
$costContent += '```'

$docsDirectoryResolved = Resolve-Path -Path $DocsDirectory
$azureResourcesPath = Join-Path $docsDirectoryResolved "AZURE_RESOURCES.md"
$costEstimatePath = Join-Path $docsDirectoryResolved "COSTS_ESTIMATE.md"

Set-Content -Path $azureResourcesPath -Value ($azureResourcesContent -join "`r`n") -Encoding utf8
Set-Content -Path $costEstimatePath -Value ($costContent -join "`r`n") -Encoding utf8
($snapshot | ConvertTo-Json -Depth 20) | Set-Content -Path $SnapshotPath -Encoding utf8

$runtimeSnapshotDirectory = Split-Path -Path $RuntimeSnapshotPath -Parent
if (-not (Test-Path $runtimeSnapshotDirectory)) {
    New-Item -ItemType Directory -Path $runtimeSnapshotDirectory -Force | Out-Null
}
($snapshot | ConvertTo-Json -Depth 20) | Set-Content -Path $RuntimeSnapshotPath -Encoding utf8

Write-Output "Updated:"
Write-Output " - $azureResourcesPath"
Write-Output " - $costEstimatePath"
Write-Output " - $SnapshotPath"
Write-Output " - $RuntimeSnapshotPath"
