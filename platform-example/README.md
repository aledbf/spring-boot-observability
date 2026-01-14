# Shared Observability Platform Architecture

This document describes how to structure reusable observability infrastructure
across 54+ repositories using a **Shared Git Repo + Override Files** approach.

## Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        observability-platform (shared repo)                  │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐                │
│  │ docker-compose  │ │   Taskfile.yml  │ │  etc/           │                │
│  │ (base stack)    │ │  (shared tasks) │ │  (base configs) │                │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘                │
└───────────────────────────────────────────┬─────────────────────────────────┘
                                            │ git submodule
                                            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        payment-service (app repo)                            │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐                │
│  │ src/            │ │ observability/  │ │ Taskfile.yml    │                │
│  │ (business code) │ │ (overrides)     │ │ (includes base) │                │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘                │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Quick Start

### Migrate an existing service

```bash
# In your service repo
curl -sL https://raw.githubusercontent.com/your-org/observability-platform/main/migrate-to-platform.sh | bash -s my-service
```

Or manually:

```bash
# 1. Add platform as submodule
git submodule add git@github.com:your-org/observability-platform.git platform

# 2. Create minimal Taskfile.yml
cat > Taskfile.yml << 'EOF'
version: '3'
includes:
  platform: ./platform/Taskfile.platform.yml
vars:
  SERVICE_NAME: my-service
  PLATFORM_DIR: ./platform
tasks:
  default:
    cmds: [task --list]
EOF

# 3. Create docker-compose.override.yaml (see service-example/)

# 4. Start the stack
task platform:up
```

## What Each Repo Contains

### Platform Repo (shared, version-controlled)

| Component | Purpose |
|-----------|---------|
| `docker-compose.base.yaml` | Loki, Prometheus, Tempo, Grafana, Pyroscope, PostgreSQL, Redis |
| `Taskfile.platform.yml` | Common tasks: up, down, logs, alerts, test, clean |
| `etc/prometheus/rules/platform.yml` | Generic alerts: ServiceDown, HighErrorRate, HighLatency, etc. |
| `etc/dashboards/*.json` | Generic dashboards: Spring Boot metrics, Fleet overview |
| `runbooks/*.md` | Generic runbooks for platform alerts |

### Service Repo (business-specific)

| Component | Purpose |
|-----------|---------|
| `src/` | Business logic only |
| `docker-compose.override.yaml` | Service definition + env vars |
| `observability/alerts.yml` | Business-specific alerts |
| `observability/dashboards/*.json` | Service dashboards |
| `observability/runbooks/*.md` | Service runbooks |
| `service.yaml` | Metadata: team, SLOs, owners |

## How It Works

### 1. Docker Compose Merging

```bash
# Platform provides infrastructure
docker compose -f platform/docker-compose.base.yaml \
               -f docker-compose.override.yaml up -d
```

The override file only defines your app - infrastructure comes from the platform.

### 2. Alert Rule Merging

Prometheus loads rules from two paths:
- `/etc/prometheus/rules/*.yml` - Platform rules (from submodule)
- `/etc/prometheus/service/*.yml` - Service rules (from your repo)

Your service alerts are **additive** - they don't replace platform alerts.

### 3. Dashboard Provisioning

Grafana loads dashboards from:
- `/etc/grafana/dashboards/platform/` - Generic dashboards
- `/etc/grafana/dashboards/service/` - Your dashboards

### 4. Taskfile Includes

```yaml
# Your Taskfile.yml
includes:
  platform: ./platform/Taskfile.platform.yml

# Now you can run:
# task platform:up
# task platform:logs
# task platform:alerts
```

## Example Files

```
platform-example/
├── README.md                           # This file
├── docker-compose.base.yaml            # Platform infrastructure
├── Taskfile.platform.yml               # Shared tasks
├── migrate-to-platform.sh              # Migration script
│
├── etc/
│   └── prometheus/
│       ├── prometheus.yml              # Prometheus config
│       └── rules/
│           └── platform.yml            # Generic alert rules
│
├── service-example/                    # Example service repo
│   ├── Taskfile.yml                    # Includes platform tasks
│   ├── docker-compose.override.yaml    # Service definition
│   ├── service.yaml                    # Metadata
│   └── observability/
│       ├── alerts.yml                  # Business alerts
│       ├── dashboards/                 # Service dashboards
│       └── runbooks/
│           └── payment-gateway-degraded.md
│
└── .github/workflows/
    ├── platform-update.yml             # Auto-update submodule
    └── validate-observability.yml      # Validate configs on PR
```

## Updating the Platform

### Automated (recommended)

Add `.github/workflows/platform-update.yml` to your service repo.
It creates a PR weekly when the platform has updates.

### Manual

```bash
task platform:update
# or
git submodule update --remote platform
```

## Available Tasks

After including the platform Taskfile:

| Task | Description |
|------|-------------|
| `task platform:up` | Start observability stack + your app |
| `task platform:down` | Stop all services |
| `task platform:logs` | Show logs (e.g., `task platform:logs -- app prometheus`) |
| `task platform:alerts` | Show firing alerts |
| `task platform:urls` | Show URLs for all services |
| `task platform:test` | Run k6 load tests |
| `task platform:db` | Open PostgreSQL shell |
| `task platform:redis` | Open Redis CLI |
| `task platform:clean` | Stop services, remove volumes |
| `task platform:update` | Update platform submodule |
| `task platform:validate:alerts` | Validate your alert rules |

## Adding Business-Specific Alerts

Create `observability/alerts.yml`:

```yaml
groups:
  - name: payment-service
    rules:
      - alert: PaymentGatewayDegraded
        expr: |
          sum(rate(http_client_requests_seconds_count{
            job="payment-service",
            uri=~".*gateway.*",
            status=~"5.."
          }[5m])) > 0.1
        labels:
          severity: critical
          team: payments
          runbook: payment-gateway-degraded
        annotations:
          summary: "Payment gateway error rate > 10%"
```

## Adding Service Dashboards

Add JSON files to `observability/dashboards/`. They'll be automatically
provisioned into Grafana under a "Service" folder.

## FAQ

### Q: What if I need to customize the Prometheus config?

Use environment variables in `docker-compose.override.yaml`:
```yaml
prometheus:
  environment:
    - SCRAPE_INTERVAL=10s
```

### Q: Can I use a different database?

Override the postgres service in your `docker-compose.override.yaml`:
```yaml
postgres:
  image: postgres:15
  environment:
    - POSTGRES_DB=mydb
```

### Q: How do I add a new infrastructure component?

Add it to your override file. If it's useful for all services,
submit a PR to the platform repo.

### Q: Can I opt out of certain platform alerts?

Add inhibition rules in your service's alertmanager config,
or use label matchers to exclude your service from specific alerts.
