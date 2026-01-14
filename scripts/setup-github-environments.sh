#!/usr/bin/env bash
#
# GitHub Environments Setup Script
#
# This script creates and configures GitHub Environments for the deployment pipeline.
# It uses the GitHub CLI (gh) to set up environments, protection rules, and variables.
#
# Prerequisites:
# - GitHub CLI (gh) installed and authenticated
# - Repository admin access
#
# Usage:
#   ./scripts/setup-github-environments.sh
#
set -euo pipefail

# Configuration
REPO="${GITHUB_REPOSITORY:-bna/spring-boot-observability}"
DOMAIN="bna.internal"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."
    
    if ! command -v gh &> /dev/null; then
        log_error "GitHub CLI (gh) is not installed. Install it from https://cli.github.com/"
        exit 1
    fi
    
    if ! gh auth status &> /dev/null; then
        log_error "GitHub CLI is not authenticated. Run 'gh auth login' first."
        exit 1
    fi
    
    log_info "Prerequisites OK"
}

# Create environment with optional protection rules
create_environment() {
    local env_name=$1
    local require_reviewers=${2:-false}
    local wait_timer=${3:-0}
    
    log_info "Creating environment: $env_name"
    
    # Check if environment exists
    if gh api "repos/$REPO/environments/$env_name" &> /dev/null; then
        log_warn "Environment '$env_name' already exists, updating..."
    fi
    
    # Build the API payload
    local payload="{}"
    
    if [[ "$require_reviewers" == "true" ]]; then
        # For production, we need to add reviewers
        # First, get the current user/team IDs
        log_info "  - Adding required reviewers protection rule"
        
        # Note: You'll need to customize this with actual team/user IDs
        # This creates a placeholder that needs manual configuration
        payload=$(cat <<EOF
{
  "wait_timer": $wait_timer,
  "prevent_self_review": true,
  "reviewers": [],
  "deployment_branch_policy": {
    "protected_branches": true,
    "custom_branch_policies": false
  }
}
EOF
)
    else
        payload=$(cat <<EOF
{
  "wait_timer": $wait_timer,
  "deployment_branch_policy": null
}
EOF
)
    fi
    
    # Create/update the environment
    echo "$payload" | gh api --method PUT "repos/$REPO/environments/$env_name" --input - > /dev/null
    
    log_info "  - Environment '$env_name' created/updated"
}

# Set environment variable
set_env_variable() {
    local env_name=$1
    local var_name=$2
    local var_value=$3
    
    log_info "  - Setting variable $var_name"
    
    # Check if variable exists
    if gh api "repos/$REPO/environments/$env_name/variables/$var_name" &> /dev/null 2>&1; then
        # Update existing variable
        gh api --method PATCH "repos/$REPO/environments/$env_name/variables/$var_name" \
            -f name="$var_name" \
            -f value="$var_value" > /dev/null
    else
        # Create new variable
        gh api --method POST "repos/$REPO/environments/$env_name/variables" \
            -f name="$var_name" \
            -f value="$var_value" > /dev/null
    fi
}

# Main setup function
setup_dev_environment() {
    log_info "Setting up DEV environment..."
    
    create_environment "dev" "false" 0
    
    # Set environment variables
    set_env_variable "dev" "CLUSTER_URL" "https://dev-cluster.${DOMAIN}:6443"
    set_env_variable "dev" "ARGOCD_SERVER" "argocd-dev.${DOMAIN}"
    set_env_variable "dev" "APP_URL" "https://peanuts-dev.${DOMAIN}"
    set_env_variable "dev" "NAMESPACE" "peanuts-dev"
    
    log_info "DEV environment configured"
}

setup_qa_environment() {
    log_info "Setting up QA environment..."
    
    create_environment "qa" "false" 0
    
    # Set environment variables
    set_env_variable "qa" "CLUSTER_URL" "https://qa-cluster.${DOMAIN}:6443"
    set_env_variable "qa" "ARGOCD_SERVER" "argocd-qa.${DOMAIN}"
    set_env_variable "qa" "APP_URL" "https://peanuts-qa.${DOMAIN}"
    set_env_variable "qa" "NAMESPACE" "peanuts-qa"
    
    log_info "QA environment configured"
}

setup_production_environment() {
    log_info "Setting up PRODUCTION environment..."
    
    # Production requires reviewers and has a wait timer
    create_environment "production" "true" 0
    
    # Set environment variables
    set_env_variable "production" "CLUSTER_URL" "https://production-cluster.${DOMAIN}:6443"
    set_env_variable "production" "ARGOCD_SERVER" "argocd.${DOMAIN}"
    set_env_variable "production" "APP_URL" "https://peanuts.${DOMAIN}"
    set_env_variable "production" "NAMESPACE" "peanuts-production"
    
    log_info "PRODUCTION environment configured"
    log_warn "IMPORTANT: Add required reviewers manually in GitHub UI:"
    log_warn "  Settings > Environments > production > Required reviewers"
}

print_summary() {
    echo ""
    echo "=============================================="
    echo "  GitHub Environments Setup Complete"
    echo "=============================================="
    echo ""
    echo "Environments created:"
    echo "  - dev:        Auto-deploys, no approval required"
    echo "  - qa:         Auto-deploys, no approval required"  
    echo "  - production: Requires approval from team leads"
    echo ""
    echo "Next steps:"
    echo "  1. Add required reviewers for production environment:"
    echo "     https://github.com/$REPO/settings/environments"
    echo ""
    echo "  2. Add environment secrets (for each environment):"
    echo "     - ARGOCD_AUTH_TOKEN: ArgoCD authentication token"
    echo "     - KUBECONFIG_DATA: Base64-encoded kubeconfig (optional)"
    echo ""
    echo "  3. Verify the setup:"
    echo "     gh api repos/$REPO/environments --jq '.environments[].name'"
    echo ""
}

print_secrets_instructions() {
    echo ""
    echo "=============================================="
    echo "  Required Secrets Configuration"
    echo "=============================================="
    echo ""
    echo "For each environment, you need to configure these secrets:"
    echo ""
    echo "1. ARGOCD_AUTH_TOKEN"
    echo "   Generate with: argocd account generate-token --account github-actions"
    echo ""
    echo "   Set for each environment:"
    echo "   gh secret set ARGOCD_AUTH_TOKEN --env dev"
    echo "   gh secret set ARGOCD_AUTH_TOKEN --env qa"
    echo "   gh secret set ARGOCD_AUTH_TOKEN --env production"
    echo ""
    echo "2. KUBECONFIG_DATA (optional, for direct kubectl access)"
    echo "   base64 -w 0 ~/.kube/config-dev | gh secret set KUBECONFIG_DATA --env dev"
    echo ""
}

# Main
main() {
    echo ""
    echo "=============================================="
    echo "  GitHub Environments Setup for $REPO"
    echo "=============================================="
    echo ""
    
    check_prerequisites
    
    setup_dev_environment
    echo ""
    
    setup_qa_environment
    echo ""
    
    setup_production_environment
    echo ""
    
    print_summary
    print_secrets_instructions
}

main "$@"
