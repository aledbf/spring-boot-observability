#!/bin/bash
# migrate-to-standard-build.sh
#
# Migrates a service repository to use the organization's standard build infrastructure:
# - Parent POM for dependency management
# - Docker-based builds for consistency
# - Standard CI/CD workflows
# - Nexus integration for faster builds
#
# Usage:
#   curl -sL https://raw.githubusercontent.com/yourorg/build-infrastructure/main/migrate.sh | bash
#   # or
#   ./migrate-to-standard-build.sh [service-name]

set -e

# Configuration
INFRA_REPO="${INFRA_REPO:-https://github.com/yourorg/build-infrastructure.git}"
PARENT_GROUP_ID="${PARENT_GROUP_ID:-com.yourorg}"
PARENT_ARTIFACT_ID="${PARENT_ARTIFACT_ID:-spring-boot-parent}"
PARENT_VERSION="${PARENT_VERSION:-1.0.0}"

# Detect service name from directory or argument
SERVICE_NAME="${1:-$(basename "$(pwd)")}"

echo "🚀 Migrating $SERVICE_NAME to standard build infrastructure"
echo ""

# Check prerequisites
command -v git >/dev/null 2>&1 || { echo "❌ git is required"; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "❌ docker is required"; exit 1; }

# 1. Create directory structure
echo "📁 Creating directory structure..."
mkdir -p .github/{workflows,linters}
mkdir -p app/src/test/{java,resources}

# 2. Download standard files
echo "📥 Downloading standard configuration files..."

# Checkstyle config
curl -sL "https://raw.githubusercontent.com/yourorg/build-infrastructure/main/.github/linters/checkstyle.xml" \
    -o .github/linters/checkstyle.xml

# CI workflow
curl -sL "https://raw.githubusercontent.com/yourorg/build-infrastructure/main/templates/ci.yml" \
    -o .github/workflows/ci.yml

# Security workflow
curl -sL "https://raw.githubusercontent.com/yourorg/build-infrastructure/main/templates/security.yml" \
    -o .github/workflows/security.yml 2>/dev/null || true

# Dependabot
if [ ! -f .github/dependabot.yml ]; then
    cat > .github/dependabot.yml << 'EOF'
version: 2
updates:
  - package-ecosystem: "maven"
    directory: "/app"
    schedule:
      interval: "weekly"
    labels:
      - "dependencies"
    groups:
      spring-boot:
        patterns:
          - "org.springframework*"
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
  - package-ecosystem: "docker"
    directory: "/app"
    schedule:
      interval: "weekly"
EOF
fi

# 3. Update pom.xml to use parent POM
echo "📝 Checking pom.xml..."
POM_FILE="app/pom.xml"
if [ -f "$POM_FILE" ]; then
    # Check if already using parent POM
    if grep -q "$PARENT_ARTIFACT_ID" "$POM_FILE"; then
        echo "   ✓ Already using parent POM"
    else
        echo "   ⚠️  Manual update required for $POM_FILE"
        echo ""
        echo "   Add this parent block to your pom.xml:"
        echo ""
        echo "   <parent>"
        echo "       <groupId>$PARENT_GROUP_ID</groupId>"
        echo "       <artifactId>$PARENT_ARTIFACT_ID</artifactId>"
        echo "       <version>$PARENT_VERSION</version>"
        echo "   </parent>"
        echo ""
        echo "   Then remove these sections (now inherited from parent):"
        echo "   - <properties> for java.version, plugin versions"
        echo "   - <dependencyManagement> for testcontainers, opentelemetry"
        echo "   - <build><plugins> for checkstyle, spotbugs, pmd, jacoco"
        echo ""
    fi
else
    echo "   ⚠️  No pom.xml found at $POM_FILE"
fi

# 4. Create/update Taskfile
echo "📝 Creating Taskfile.yml..."
if [ ! -f Taskfile.yml ]; then
    curl -sL "https://raw.githubusercontent.com/yourorg/build-infrastructure/main/templates/Taskfile.yml" \
        -o Taskfile.yml
    # Replace placeholder with service name
    sed -i.bak "s/SERVICE_NAME: 'app'/SERVICE_NAME: '$SERVICE_NAME'/g" Taskfile.yml && rm -f Taskfile.yml.bak
else
    echo "   ✓ Taskfile.yml already exists"
fi

# 5. Create test profile if not exists
echo "📝 Checking test configuration..."
TEST_CONFIG="app/src/test/resources/application-test.yml"
if [ ! -f "$TEST_CONFIG" ]; then
    mkdir -p "$(dirname "$TEST_CONFIG")"
    cat > "$TEST_CONFIG" << 'EOF'
spring:
  cache:
    type: simple
  datasource:
    url: jdbc:tc:postgresql:17-alpine:///testdb
    driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver
  jpa:
    hibernate:
      ddl-auto: create-drop

management:
  tracing:
    enabled: false

logging:
  level:
    com.example: DEBUG
    org.testcontainers: INFO
EOF
    echo "   ✓ Created $TEST_CONFIG"
else
    echo "   ✓ Test config already exists"
fi

# 6. Create .dockerignore if not exists
if [ ! -f app/.dockerignore ]; then
    cat > app/.dockerignore << 'EOF'
target/
*.md
.git
.github
.idea
*.iml
EOF
    echo "📝 Created app/.dockerignore"
fi

# 7. Summary
echo ""
echo "✅ Migration complete!"
echo ""
echo "📋 Next steps:"
echo ""
echo "1. Update app/pom.xml to use parent POM (if not done automatically)"
echo ""
echo "2. Verify your Nexus settings (for faster builds):"
echo "   cp build-infrastructure/nexus/nexus-settings.xml ~/.m2/settings.xml"
echo ""
echo "3. Test the build locally:"
echo "   task build        # Docker-based (consistent with CI)"
echo "   task test         # Run all tests"
echo "   task check        # Run code quality checks"
echo "   task ci           # Simulate full CI pipeline"
echo ""
echo "4. Commit and push:"
echo "   git add -A"
echo "   git commit -m 'Migrate to standard build infrastructure'"
echo "   git push"
echo ""
echo "📚 Documentation: https://github.com/yourorg/build-infrastructure"
