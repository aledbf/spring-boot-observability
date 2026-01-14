# Payment Gateway Degraded

## Alert

```
PaymentGatewayDegraded
Severity: critical
Team: payments
```

## Impact

- **Customer Impact**: Payment transactions may fail or timeout
- **Business Impact**: Direct revenue loss, cart abandonment
- **Affected Services**: payment-service, checkout-service, order-service

## Quick Diagnosis

```promql
# Error rate by gateway endpoint
sum by (uri, status) (
  rate(http_client_requests_seconds_count{job="payment-service", uri=~".*gateway.*"}[5m])
)

# Latency to payment gateway
histogram_quantile(0.95,
  sum by (le) (
    rate(http_client_requests_seconds_bucket{job="payment-service", uri=~".*gateway.*"}[5m])
  )
)
```

## Investigation Steps

### 1. Verify Gateway Status

Check the payment gateway's status page:
- Stripe: https://status.stripe.com
- PayPal: https://www.paypal-status.com
- Adyen: https://status.adyen.com

### 2. Check Network Connectivity

```bash
# From the payment-service container
task platform:logs -- payment-service | grep -i "gateway\|timeout\|connection"

# Check DNS resolution
docker exec payment-service nslookup api.stripe.com
```

### 3. Review Recent Changes

```bash
# Recent deployments
git log --oneline -10

# Config changes
git diff HEAD~5 -- src/main/resources/application*.yml
```

## Common Causes

| Cause | Indicators | Resolution |
|-------|------------|------------|
| Gateway outage | Status page shows incident | Wait for recovery, enable retry with backoff |
| Network issues | Connection timeouts in logs | Check VPC/firewall rules |
| Rate limiting | 429 responses | Reduce request rate, contact gateway |
| Certificate expiry | SSL handshake errors | Update certificates |
| Config change | Errors after deploy | Rollback deployment |

## Resolution

### Immediate Mitigation

1. **Enable circuit breaker** (if not already):
   ```bash
   curl -X POST http://localhost:8080/admin/circuit-breaker/gateway/open
   ```

2. **Switch to backup gateway** (if available):
   ```bash
   curl -X POST http://localhost:8080/admin/gateway/failover
   ```

### Long-term

1. Implement multi-gateway failover
2. Add synthetic monitoring for gateway health
3. Review timeout and retry configurations

## Escalation

If unresolved after 15 minutes:
1. Page payments-oncall via PagerDuty
2. Notify #payments-incidents in Slack
3. Create incident in status page

## Related Runbooks

- [High Error Rate](./high-error-rate.md)
- [Payment Processing Backlog](./payment-backlog.md)
