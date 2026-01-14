# PaymentHighErrorRate

## Alert Description

The payment endpoint is experiencing an error rate above 10% over a 5-minute window.

**Severity:** Critical
**Team:** Payments

## Impact

- Customers unable to complete purchases
- Revenue loss
- Potential SLA breach

## Investigation Steps

### 1. Check current error rate

```promql
sum(rate(http_server_requests_seconds_count{uri="/payment", status=~"5.."}[5m])) by (application)
/
sum(rate(http_server_requests_seconds_count{uri="/payment"}[5m])) by (application)
```

### 2. Identify error types

```promql
sum by (status, application) (
  rate(http_server_requests_seconds_count{uri="/payment", status=~"[45].."}[5m])
)
```

### 3. Check application logs

```bash
# Via Grafana Loki
{compose_service=~"app-.*"} |= "payment" |= "error"
```

### 4. Check payment gateway health

```bash
curl http://localhost:8080/health/payment-gateway
```

### 5. Check database connectivity

```bash
docker exec -it spring-boot-observability-postgres-1 pg_isready
```

### 6. Check Redis connectivity

```bash
docker exec -it spring-boot-observability-redis-1 redis-cli ping
```

## Common Causes

| Cause | Symptoms | Resolution |
|-------|----------|------------|
| Payment gateway down | 503 errors, high latency | Check upstream provider status |
| Database connection exhaustion | 500 errors, connection timeouts | Increase pool size or investigate leaks |
| Invalid payment data | 400 errors | Check client-side validation |
| Rate limiting | 429 errors | Back off or increase limits |

## Resolution Steps

### If payment gateway is down:
1. Check upstream provider status page
2. Enable fallback payment processor if available
3. Communicate with customers about delays

### If database issues:
1. Check connection pool metrics: `hikaricp_connections_active`
2. Restart application if pool is exhausted
3. Increase `DATABASE_POOL_SIZE` environment variable

### If high load:
1. Scale up application instances
2. Enable rate limiting
3. Check for attack patterns

## Escalation

- **After 5 minutes:** Page on-call payments engineer
- **After 15 minutes:** Escalate to payments team lead
- **After 30 minutes:** Incident commander involvement

## Related Alerts

- PaymentGatewayDegraded
- PaymentHighLatency
- DatabaseConnectionPoolExhausted
