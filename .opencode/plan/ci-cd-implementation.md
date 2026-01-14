# CI/CD Implementation Plan

## Overview

This plan adds comprehensive CI/CD pipelines with:
- **Unit Tests** - MockMvc for controllers, Mockito for services
- **Integration Tests** - Testcontainers for real Postgres/Redis
- **Code Quality** - Checkstyle (Google style), SpotBugs, PMD
- **Coverage** - JaCoCo with 70% minimum enforcement
- **Security** - CodeQL SAST + Dependabot for dependencies

## Files to Create/Modify

| # | File | Action |
|---|------|--------|
| 1 | `app/pom.xml` | Modify - Add test deps + quality plugins |
| 2 | `.github/linters/checkstyle.xml` | Create - Google Java Style config |
| 3 | `.github/workflows/ci.yml` | Create - Main CI pipeline |
| 4 | `.github/workflows/security.yml` | Create - CodeQL analysis |
| 5 | `.github/dependabot.yml` | Create - Dependency updates |
| 6 | `app/src/test/resources/application-test.yml` | Create - Testcontainers config |
| 7 | `app/src/test/java/.../AppControllerTest.java` | Create - Controller unit tests |
| 8 | `app/src/test/java/.../PeanutsServiceTest.java` | Create - Service unit tests |
| 9 | `app/src/test/java/.../integration/PeanutsIntegrationTest.java` | Create - Integration tests |
| 10 | `.github/workflows/build.yaml` | Modify - Trigger after CI passes |

## Workflow Diagram

```
                    PR or Push to main
                           │
           ┌───────────────┼───────────────┐
           │               │               │
           ▼               ▼               ▼
    ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
    │ build-test  │ │code-quality │ │  security   │
    │             │ │             │ │  (CodeQL)   │
    │ - compile   │ │ - checkstyle│ │             │
    │ - unit test │ │ - spotbugs  │ │             │
    │ - coverage  │ │ - pmd       │ │             │
    └──────┬──────┘ └──────┬──────┘ └─────────────┘
           │               │
           └───────┬───────┘
                   │ Must pass
                   ▼
         ┌─────────────────┐
         │integration-test │
         │ - testcontainers│
         │ - postgres      │
         │ - redis         │
         └────────┬────────┘
                  │ (main only)
                  ▼
         ┌─────────────────┐
         │  build & push   │
         │  Docker image   │
         └─────────────────┘
```

## Configuration Details

### Coverage Enforcement
- Minimum 70% line coverage
- Build fails if below threshold

### PR Checks
- All checks required to merge
- Integration tests must pass

### Dependabot
- Weekly updates for Maven, Docker, GitHub Actions

## Ready to Execute

When you disable Plan Mode, I will implement all 10 items in the todo list.
