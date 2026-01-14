# ServiceDown

## Alert Description

Prometheus cannot scrape metrics from a service. The service may be down or unreachable.

**Severity:** Critical
**Team:** Platform

## Impact

- Service unavailable to users
- Dependent services may fail
- Data loss if database-related

## Investigation Steps

### 1. Check container status

```bash
docker ps -a | grep spring-boot-observability
```

### 2. Check container logs

```bash
# Replace app-a with the affected service
docker logs spring-boot-observability-app-a-1 --tail 100
```

### 3. Check if service is responding

```bash
curl -v http://localhost:8080/actuator/health
```

### 4. Check resource usage

```bash
docker stats --no-stream
```

### 5. Check for OOM kills

```bash
docker inspect spring-boot-observability-app-a-1 | grep -A5 "State"
```

## Common Causes

| Cause | Symptoms | Resolution |
|-------|----------|------------|
| OOM killed | Exit code 137 | Increase memory limit |
| Application crash | Non-zero exit code | Check logs for exception |
| Network issue | Container running but unreachable | Check Docker network |
| Resource exhaustion | Slow startup | Check disk/CPU/memory |

## Resolution Steps

### If container is stopped:

```bash
# Restart the service
docker-compose up -d app-a

# Or restart all services
docker-compose restart
```

### If OOM killed:

1. Increase memory limit in docker-compose.yaml
2. Check for memory leaks in application
3. Consider horizontal scaling

### If application crash:

1. Check logs for root cause
2. Fix the underlying issue
3. Restart service

### If network issue:

```bash
# Check Docker networks
docker network ls
docker network inspect spring-boot-observability_default
```

## Verification

After resolution, verify:

```bash
# Check service is responding
curl http://localhost:8080/actuator/health

# Check Prometheus targets
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | {job: .labels.job, health: .health}'
```

## Escalation

- **Immediate:** Page on-call platform engineer
- **If revenue-impacting:** Notify incident commander
