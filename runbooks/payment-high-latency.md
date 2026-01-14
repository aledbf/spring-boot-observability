# PaymentHighLatency

## Alert Description

The 95th percentile latency for the payment endpoint exceeds 1 second.

**Severity:** Warning
**Team:** Payments

## Impact

- Poor customer experience
- Increased cart abandonment
- Potential timeout-related failures

## Investigation Steps

### 1. Check current latency percentiles

```promql
histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket{uri="/payment"}[5m])) by (le, application))
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{uri="/payment"}[5m])) by (le, application))
histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{uri="/payment"}[5m])) by (le, application))
```

### 2. Check for slow database queries

```promql
# Check database connection metrics
hikaricp_connections_acquire_seconds{quantile="0.95"}
```

### 3. Check Redis latency

```bash
docker exec -it spring-boot-observability-redis-1 redis-cli --latency
```

### 4. Check JVM GC pauses

```promql
rate(jvm_gc_pause_seconds_sum[5m]) / rate(jvm_gc_pause_seconds_count[5m])
```

### 5. Trace slow requests

Use Grafana Tempo to find slow traces:
- Filter by `service.name = app-*`
- Filter by `http.url = /payment`
- Sort by duration descending

## Common Causes

| Cause | Symptoms | Resolution |
|-------|----------|------------|
| Database slow queries | High DB latency in traces | Optimize queries, add indexes |
| GC pressure | GC pause spikes | Increase heap, tune GC |
| Connection pool wait | Acquire time high | Increase pool size |
| Upstream latency | External spans slow | Contact provider |

## Resolution Steps

### If database is slow:
1. Check for missing indexes
2. Analyze slow query logs
3. Consider connection pool tuning

### If GC is the issue:
1. Increase heap size: `-Xmx1g`
2. Consider G1GC tuning
3. Profile for memory leaks

### If external service is slow:
1. Implement timeout and retry
2. Add circuit breaker
3. Cache where possible

## Metrics to Monitor

```promql
# Request rate
sum(rate(http_server_requests_seconds_count{uri="/payment"}[5m]))

# Error rate
sum(rate(http_server_requests_seconds_count{uri="/payment", status=~"5.."}[5m]))

# Apdex score (target: 500ms, tolerating: 2s)
(
  sum(rate(http_server_requests_seconds_bucket{uri="/payment", le="0.5"}[5m]))
  + sum(rate(http_server_requests_seconds_bucket{uri="/payment", le="2"}[5m]))
) / 2 / sum(rate(http_server_requests_seconds_count{uri="/payment"}[5m]))
```
