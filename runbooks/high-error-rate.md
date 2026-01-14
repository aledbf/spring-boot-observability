# HighErrorRate

## Alert Description

The overall HTTP error rate (5xx responses) exceeds 5% across all endpoints.

**Severity:** Warning
**Team:** Platform

## Impact

- Degraded user experience
- Potential data inconsistencies
- May escalate to critical

## Investigation Steps

### 1. Identify which endpoints are failing

```promql
topk(10,
  sum by (uri, status) (
    rate(http_server_requests_seconds_count{status=~"5.."}[5m])
  )
)
```

### 2. Check error distribution by status code

```promql
sum by (status) (rate(http_server_requests_seconds_count{status=~"[45].."}[5m]))
```

### 3. Check application logs for exceptions

```bash
# In Grafana Loki
{compose_service=~"app-.*"} |= "ERROR" | json | line_format "{{.message}}"
```

### 4. Check for recent deployments

```bash
git log --oneline -10
docker images | grep spring-boot-observability
```

### 5. Check dependency health

```bash
# Database
docker exec spring-boot-observability-postgres-1 pg_isready

# Redis
docker exec spring-boot-observability-redis-1 redis-cli ping

# Other services
curl http://localhost:8080/actuator/health
```

## Common Causes

| Cause | Symptoms | Resolution |
|-------|----------|------------|
| Bad deployment | Errors after deploy | Rollback |
| Dependency failure | Connection errors in logs | Fix dependency |
| Resource exhaustion | Timeouts, OOM | Scale or optimize |
| Traffic spike | High request rate | Scale out |

## Resolution Steps

### If caused by deployment:

```bash
# Rollback to previous image
docker-compose pull  # Get previous tagged image
docker-compose up -d
```

### If dependency issue:

1. Identify failing dependency from logs
2. Check dependency health
3. Restart dependency or failover

### If resource issue:

1. Check container resource usage
2. Increase limits or scale horizontally
3. Investigate for resource leaks

## Metrics Dashboard

Key metrics to monitor during investigation:

- Error rate by endpoint
- Request latency percentiles
- Active database connections
- JVM heap usage
- Container CPU/memory

## Prevention

- Implement proper error handling
- Add circuit breakers for dependencies
- Set up canary deployments
- Maintain adequate capacity headroom
