# PaymentGatewayDegraded

## Alert Description

The payment gateway health check is failing more than 5% of the time.

**Severity:** Warning
**Team:** Payments

## Impact

- Intermittent payment failures
- Increased latency for customers
- Potential escalation to critical if not addressed

## Investigation Steps

### 1. Check health endpoint status

```bash
for i in {1..10}; do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/health/payment-gateway
  sleep 1
done
```

### 2. Check health check failure rate

```promql
sum(rate(http_server_requests_seconds_count{uri="/health/payment-gateway", status!="200"}[5m])) by (application)
/
sum(rate(http_server_requests_seconds_count{uri="/health/payment-gateway"}[5m])) by (application)
```

### 3. Check latency metrics

```promql
histogram_quantile(0.95,
  sum(rate(http_server_requests_seconds_bucket{uri="/health/payment-gateway"}[5m])) by (le)
)
```

### 4. Review recent logs

```bash
# Look for payment gateway related warnings
{compose_service=~"app-.*"} |= "payment" |= "degraded"
```

## Common Causes

| Cause | Symptoms | Resolution |
|-------|----------|------------|
| Network latency | Slow responses, timeouts | Check network connectivity |
| Upstream degradation | Intermittent 503s | Monitor provider status |
| Resource contention | High CPU/memory | Scale or optimize |

## Resolution Steps

1. **Monitor closely** - This is a warning, not critical yet
2. Check if the degradation is trending up or stabilizing
3. If trending up, prepare for failover
4. Contact payment provider if external issue suspected

## Prevention

- Implement circuit breaker pattern
- Add fallback payment providers
- Set up synthetic monitoring for early detection

## Escalation

- **If rate exceeds 20%:** Escalate to critical
- **If duration exceeds 30 minutes:** Page payments engineer
