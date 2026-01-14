# Build Infrastructure

This directory contains shared infrastructure for building Java applications consistently
across all 54 repositories in your organization.

## Components

```
build-infrastructure/
├── docker/
│   ├── Dockerfile.builder      # Build container with exact JDK version
│   └── Dockerfile.builder-ci   # Lighter version for CI (no local cache mounts)
│
├── nexus/
│   ├── docker-compose.yml      # Local Nexus Repository Manager
│   └── nexus-settings.xml      # Maven settings for Nexus proxy
│
├── parent-pom/
│   └── pom.xml                 # Parent POM with all dependency versions
│
└── templates/
    ├── service-pom.xml         # Template POM for services (inherits parent)
    ├── Taskfile.yml            # Template Taskfile for services
    └── ci.yml                  # Template CI workflow
```

## Quick Start

### 1. Start Nexus (Local Maven Proxy)

```bash
cd build-infrastructure/nexus
docker compose up -d
# Access at http://localhost:8081 (admin/admin123)
```

### 2. Build Locally with Docker

```bash
task build:docker    # Uses same JDK as CI
task test:docker     # Run tests in Docker
```

### 3. Publish Parent POM to Nexus

```bash
cd build-infrastructure/parent-pom
task publish
```

### 4. Migrate a Service

```bash
# In the service repo
curl -sL https://raw.githubusercontent.com/your-org/build-infrastructure/main/migrate.sh | bash
```

## Benefits

| Aspect | Before | After |
|--------|--------|-------|
| JDK Version | Varies by developer machine | Same everywhere (Docker) |
| Dependency Versions | Copy-paste across repos | Single parent POM |
| Build Speed | Download from Maven Central | Cached in local Nexus |
| CI Setup | Manual per repo | Template + migrate script |
