#!/bin/bash
# migrate-to-platform.sh
# Run this in a service repo to add the shared observability platform

set -e

PLATFORM_REPO="${PLATFORM_REPO:-git@github.com:your-org/observability-platform.git}"
SERVICE_NAME="${1:-$(basename $(pwd))}"

echo "🚀 Migrating $SERVICE_NAME to shared observability platform"

# 1. Add platform as submodule
if [ ! -d "platform" ]; then
    echo "📦 Adding observability-platform as submodule..."
    git submodule add "$PLATFORM_REPO" platform
    git submodule update --init --recursive
else
    echo "✓ Platform submodule already exists"
fi

# 2. Create observability directory structure
echo "📁 Creating observability directory structure..."
mkdir -p observability/{dashboards,runbooks}

# 3. Create service manifest if it doesn't exist
if [ ! -f "service.yaml" ]; then
    echo "📝 Creating service.yaml..."
    cat > service.yaml << EOF
name: $SERVICE_NAME
team: platform  # TODO: Update with your team name
tier: standard

description: |
  TODO: Add service description

owners:
  - email: team@example.com
    slack: "#team-oncall"

slos:
  availability:
    target: 99.9
    window: 30d
  latency:
    p95_target_ms: 500

ports:
  http: 8080
EOF
fi

# 4. Create Taskfile if it doesn't exist
if [ ! -f "Taskfile.yml" ]; then
    echo "📝 Creating Taskfile.yml..."
    cat > Taskfile.yml << EOF
version: '3'

includes:
  platform: ./platform/Taskfile.platform.yml

vars:
  SERVICE_NAME: $SERVICE_NAME
  SERVICE_PORT: 8080
  PLATFORM_DIR: ./platform

tasks:
  default:
    cmds: [task --list]

  build:
    desc: Build the service
    cmds:
      - docker compose -f \${PLATFORM_DIR}/docker-compose.base.yaml -f docker-compose.override.yaml build \${SERVICE_NAME}
EOF
fi

# 5. Create docker-compose.override.yaml if it doesn't exist
if [ ! -f "docker-compose.override.yaml" ]; then
    echo "📝 Creating docker-compose.override.yaml..."
    cat > docker-compose.override.yaml << EOF
services:
  $SERVICE_NAME:
    build:
      context: .
      dockerfile: Dockerfile
    environment:
      - OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4317
      - OTEL_EXPORTER_OTLP_PROTOCOL=grpc
      - OTEL_SERVICE_NAME=$SERVICE_NAME
      - OTEL_METRICS_EXPORTER=none
      - OTEL_LOGS_EXPORTER=none
      - PYROSCOPE_APPLICATION_NAME=$SERVICE_NAME
      - PYROSCOPE_SERVER_ADDRESS=http://pyroscope:4040
      - DATABASE_URL=jdbc:postgresql://postgres:5432/\${DATABASE_NAME:-postgres}
      - REDIS_HOST=redis
    ports:
      - "8080:8080"
    logging:
      driver: loki
      options:
        loki-url: 'http://localhost:3100/api/prom/push'
    depends_on:
      loki:
        condition: service_started
      postgres:
        condition: service_healthy
EOF
fi

# 6. Create placeholder alerts file
if [ ! -f "observability/alerts.yml" ]; then
    echo "📝 Creating observability/alerts.yml placeholder..."
    cat > observability/alerts.yml << EOF
# $SERVICE_NAME - Custom Alert Rules
# Add business-specific alerts here
# Platform already provides: ServiceDown, HighErrorRate, HighLatency

groups:
  - name: $SERVICE_NAME
    rules: []
      # Example:
      # - alert: CustomBusinessAlert
      #   expr: some_metric > threshold
      #   labels:
      #     severity: warning
      #     team: your-team
EOF
fi

# 7. Update .gitignore
if ! grep -q "platform/" .gitignore 2>/dev/null; then
    echo "" >> .gitignore
    echo "# Platform submodule is tracked separately" >> .gitignore
fi

echo ""
echo "✅ Migration complete!"
echo ""
echo "Next steps:"
echo "  1. Review and update service.yaml with your team info"
echo "  2. Add business-specific alerts to observability/alerts.yml"
echo "  3. Add service-specific dashboards to observability/dashboards/"
echo "  4. Commit the changes: git add -A && git commit -m 'Add observability platform'"
echo ""
echo "Commands:"
echo "  task platform:up     - Start the observability stack"
echo "  task platform:urls   - Show all service URLs"
echo "  task platform:logs   - View logs"
