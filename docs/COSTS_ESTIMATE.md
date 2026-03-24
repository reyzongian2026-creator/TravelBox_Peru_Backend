# Cost Estimation - TravelBox Peru

**Last Updated:** March 24, 2026  
**Currency:** USD

---

## Monthly Cost Estimate

### Compute

| Resource | SKU | Quantity | Est. Monthly Cost |
|----------|-----|----------|-------------------|
| App Service Backend | P1V2 (Linux) | 1 | $55.00 - $75.00 |
| App Service Frontend | P1V2 (Linux) | 1 | $55.00 - $75.00 |
| App Service Plan | P1V2 | 1 | Shared with above |

### Data

| Resource | SKU | Storage/Usage | Est. Monthly Cost |
|----------|-----|---------------|------------------|
| PostgreSQL Flexible | Standard_D2s_v3 | 32 GB | $115.00 - $150.00 |
| PostgreSQL Backup Storage | - | ~32 GB | Included |

### AI Services

| Resource | SKU | Est. Monthly Cost |
|----------|-----|------------------|
| Azure Translator | S0 | $10.00 - $50.00* |

*Based on character count. First 500K chars/month free.

### Maps

| Resource | SKU | Est. Monthly Cost |
|----------|-----|------------------|
| Azure Maps | Gen2 | $0.00 - $25.00* |

*First 25K transactions/month free.

### Security

| Resource | SKU | Est. Monthly Cost |
|----------|-----|------------------|
| Key Vault | Standard | $0.03/10K ops + storage |

### Storage

| Resource | Est. Monthly Cost |
|----------|------------------|
| Blob Storage (all containers) | $5.00 - $20.00* |

*First 5 GB/month free. $0.0018/GB after.

---

## Total Estimated Monthly Cost

| Tier | Estimate |
|------|----------|
| **Minimum** | **$180.00 - $270.00** |
| **Typical** | **$250.00 - $400.00** |
| **Maximum** | **$500.00+** |

---

## Cost Optimization Tips

1. **App Service:** Consider scaling down to B1 during low traffic periods
2. **PostgreSQL:** Use burstable tier (B1ms) for dev/qa environments
3. **Translator:** Enable caching to reduce API calls
4. **Storage:** Use lifecycle policies to auto-delete old files

---

## How to Check Actual Costs

```bash
# Check costs via CLI
az cost management query \
  --type ActualCost \
  --time-period "2026-03-01/2026-03-31" \
  --dataset aggregation "{\"aggregation\":{\"totalCost\":{\"func\":\"Sum\"}}}" \
  --dimensions "ResourceGroup"

# Or via Azure Portal
# https://portal.azure.com/#blade/Microsoft_Azure_CostManagement/Menu/overview
```

---

## Budget Alerts

Recommended to set up budget alerts at:
- 50% of expected spend
- 75% of expected spend
- 90% of expected spend

```bash
# Create budget alert (example)
az consumption budget create \
  --name "travelbox-monthly-budget" \
  --subscription "33815caa-4cfb-4a9e-b60a-8fee5caa2b08" \
  --amount 500 \
  --time-grain Monthly \
  --start-date "2026-04-01"
```
