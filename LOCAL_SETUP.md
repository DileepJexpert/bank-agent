# Local Development Setup Guide

> IDFC First Bank - AI Agent Platform
> Works on **Windows 10/11** and **macOS** (Intel & Apple Silicon)

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Clone & Branch Setup](#2-clone--branch-setup)
3. [Infrastructure Setup (Docker)](#3-infrastructure-setup-docker)
4. [Build the Java Backend](#4-build-the-java-backend)
5. [Configure LLM Provider](#5-configure-llm-provider)
6. [Local Debug Mode (Recommended for Development)](#6-local-debug-mode-recommended-for-development)
7. [Run Services Individually (Default Profile)](#7-run-services-individually-default-profile)
8. [Run All Services via Docker Compose](#8-run-all-services-via-docker-compose)
9. [Flutter Admin Dashboard](#9-flutter-admin-dashboard)
10. [Verify Everything is Running](#10-verify-everything-is-running)
11. [Common Issues & Troubleshooting](#11-common-issues--troubleshooting)
12. [Port Reference](#12-port-reference)
13. [Environment Variables Reference](#13-environment-variables-reference)

---

## 1. Prerequisites

### Required Software

| Tool | Version | Windows Install | macOS Install |
|------|---------|----------------|---------------|
| **Java JDK** | 21+ | `winget install EclipseAdoptium.Temurin.21.JDK` | `brew install --cask temurin@21` |
| **Apache Maven** | 3.9+ | `winget install Apache.Maven` | `brew install maven` |
| **Docker Desktop** | 4.25+ | Download from [docker.com](https://www.docker.com/products/docker-desktop/) | `brew install --cask docker` |
| **Git** | 2.40+ | `winget install Git.Git` | `brew install git` |
| **Flutter SDK** | 3.16+ | Download from [flutter.dev](https://docs.flutter.dev/get-started/install/windows/web) | `brew install --cask flutter` |
| **Node.js** *(optional)* | 18+ | `winget install OpenJS.NodeJS.LTS` | `brew install node` |

### Verify installations

```bash
# Run all of these — every command should succeed
java -version          # Should show 21.x.x
mvn -version           # Should show 3.9+
docker --version       # Should show 24+
docker compose version # Should show v2.20+
git --version          # Should show 2.40+
flutter --version      # Should show 3.16+
```

### Docker Desktop Settings

Allocate sufficient resources in Docker Desktop → Settings → Resources:

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| CPUs | 4 | 6+ |
| Memory | 8 GB | 12 GB |
| Disk | 30 GB | 50 GB |

> **Windows users**: Ensure WSL 2 backend is enabled in Docker Desktop settings.

---

## 2. Clone & Branch Setup

```bash
git clone https://github.com/dileepjexpert/bank-agent.git
cd bank-agent
git checkout main
```

---

## 3. Infrastructure Setup (Docker)

Start only the infrastructure containers (PostgreSQL, Redis, Kafka, OPA):

```bash
cd deployment/docker

# Start infrastructure services
docker compose up -d postgres redis zookeeper kafka opa

# Wait for all to become healthy (~30-45 seconds)
docker compose ps
```

Verify each is running:

```bash
# PostgreSQL
docker exec platform-postgres pg_isready -U platform
# Expected: /var/run/postgresql:5432 - accepting connections

# Redis
docker exec platform-redis redis-cli ping
# Expected: PONG

# Kafka
docker exec platform-kafka kafka-topics --bootstrap-server localhost:9092 --list
# Expected: (empty list or default topics)

# OPA
curl http://localhost:8181/health
# Expected: {}
```

### Windows PowerShell note

Replace `curl` with `Invoke-RestMethod` or install curl via `winget install cURL.cURL`:
```powershell
Invoke-RestMethod http://localhost:8181/health
```

---

## 4. Build the Java Backend

From the project root directory:

```bash
cd /path/to/bank-agent

# Full build (skip tests for initial setup)
mvn clean install -DskipTests

# If you want to run tests
mvn clean verify
```

**Expected output**: `BUILD SUCCESS` for all 12 modules.

### Build a single module (faster iteration)

```bash
# Build only the orchestrator
mvn clean install -pl agent-orchestrator-service -am -DskipTests
```

The `-am` flag builds dependent modules (e.g., `common-spring-boot-starter`).

---

## 5. Configure LLM Provider

Create a `.env` file in the project root for your API keys. **This file is gitignored.**

```bash
# bank-agent/.env
# =============================================
# LLM Provider Configuration
# =============================================
# Uncomment ONE provider section below

# --- Option A: Anthropic Claude (default) ---
LLM_PROVIDER=anthropic
LLM_API_KEY=sk-ant-your-key-here
LLM_MODEL=claude-sonnet-4-20250514

# --- Option B: OpenAI ---
# LLM_PROVIDER=openai
# LLM_API_KEY=sk-your-openai-key
# LLM_MODEL=gpt-4o

# --- Option C: Azure OpenAI ---
# LLM_PROVIDER=azure-openai
# LLM_API_KEY=your-azure-key
# LLM_MODEL=gpt-4o
# LLM_BASE_URL=https://your-resource.openai.azure.com

# --- Option D: Ollama (local, free, no API key) ---
# LLM_PROVIDER=ollama
# LLM_API_KEY=not-needed
# LLM_MODEL=llama3.1
# LLM_BASE_URL=http://localhost:11434

# --- Option E: Mistral ---
# LLM_PROVIDER=mistral
# LLM_API_KEY=your-mistral-key
# LLM_MODEL=mistral-large-latest
# LLM_BASE_URL=https://api.mistral.ai/v1

# =============================================
# Infrastructure (defaults work with docker-compose)
# =============================================
REDIS_HOST=localhost
REDIS_PORT=6379
KAFKA_BOOTSTRAP_SERVERS=localhost:29092
SPRING_DATASOURCE_USERNAME=platform
SPRING_DATASOURCE_PASSWORD=platform_dev_password
```

### Using Ollama (free, no API key needed)

```bash
# macOS
brew install ollama
ollama serve &
ollama pull llama3.1

# Windows — download installer from https://ollama.com/download/windows
# Then in a terminal:
ollama serve
ollama pull llama3.1
```

---

## 6. Local Debug Mode (Recommended for Development)

Each service includes an `application-local.yml` profile pre-configured to connect to Docker Desktop infrastructure and **Ollama** for AI — no cloud API keys needed, no `.env` file required.

### Prerequisites

1. **Docker Desktop** running with infrastructure containers (see [Section 3](#3-infrastructure-setup-docker))
2. **Ollama** installed and running locally with `llama3.1` model pulled

#### Install & Start Ollama

```bash
# macOS
brew install ollama
ollama serve &
ollama pull llama3.1

# Windows — download from https://ollama.com/download/windows
# Then in a terminal:
ollama serve
ollama pull llama3.1

# Linux
curl -fsSL https://ollama.ai/install.sh | sh
ollama serve &
ollama pull llama3.1
```

Verify Ollama is running:
```bash
curl http://localhost:11434/api/tags
# Should list llama3.1 in the models
```

### Step 1: Start Infrastructure (Docker)

You only need the core infrastructure — no need to run all containers:

```bash
cd deployment/docker
docker compose up -d postgres redis zookeeper kafka opa
cd ../..
```

Wait ~30 seconds, then verify:
```bash
docker compose -f deployment/docker/docker-compose.yaml ps
```

All 5 services should show `running` or `healthy`.

### Step 2: Build the Project

```bash
mvn clean install -DskipTests
```

### Step 3: Run Any Service with Local Profile

The `local` profile auto-configures each service to use:
- **PostgreSQL** at `localhost:5432` (with correct database per service)
- **Redis** at `localhost:6379`
- **Kafka** at `localhost:29092`
- **Ollama** at `localhost:11434` (as the LLM provider)
- **OPA** at `localhost:8181`

Run any service from the project root with:

```bash
mvn spring-boot:run -pl <module-name> -Dspring-boot.run.profiles=local
```

**No `.env` file, no environment variables, no extra arguments needed.**

### Service Quick-Start Commands

| Service | Module Name | Command | Port |
|---------|-------------|---------|------|
| Config Server | `config-server` | `mvn spring-boot:run -pl config-server -Dspring-boot.run.profiles=local` | 8888 |
| API Gateway | `api-gateway` | `mvn spring-boot:run -pl api-gateway -Dspring-boot.run.profiles=local` | 8080 |
| **Orchestrator** | `agent-orchestrator-service` | `mvn spring-boot:run -pl agent-orchestrator-service -Dspring-boot.run.profiles=local` | 8084 |
| Account Agent | `agent-account-service` | `mvn spring-boot:run -pl agent-account-service -Dspring-boot.run.profiles=local` | 8085 |
| Card Agent | `agent-card-service` | `mvn spring-boot:run -pl agent-card-service -Dspring-boot.run.profiles=local` | 8088 |
| Loans Agent | `agent-loans-service` | `mvn spring-boot:run -pl agent-loans-service -Dspring-boot.run.profiles=local` | 8090 |
| Wealth Agent | `agent-wealth-service` | `mvn spring-boot:run -pl agent-wealth-service -Dspring-boot.run.profiles=local` | 8094 |
| Collections Agent | `agent-collections-service` | `mvn spring-boot:run -pl agent-collections-service -Dspring-boot.run.profiles=local` | 8095 |
| Fraud Agent | `agent-fraud-service` | `mvn spring-boot:run -pl agent-fraud-service -Dspring-boot.run.profiles=local` | 8096 |
| Internal Ops Agent | `agent-internal-ops-service` | `mvn spring-boot:run -pl agent-internal-ops-service -Dspring-boot.run.profiles=local` | 8097 |
| MCP Core Banking | `mcp-core-banking-server` | `mvn spring-boot:run -pl mcp-core-banking-server -Dspring-boot.run.profiles=local` | 8086 |
| MCP Card Management | `mcp-card-management-server` | `mvn spring-boot:run -pl mcp-card-management-server -Dspring-boot.run.profiles=local` | 8089 |
| Vault Identity | `vault-identity-service` | `mvn spring-boot:run -pl vault-identity-service -Dspring-boot.run.profiles=local` | 8081 |
| Vault Policy | `vault-policy-service` | `mvn spring-boot:run -pl vault-policy-service -Dspring-boot.run.profiles=local` | 8082 |
| Vault Audit | `vault-audit-service` | `mvn spring-boot:run -pl vault-audit-service -Dspring-boot.run.profiles=local` | 8083 |
| Vault Anomaly | `vault-anomaly-service` | `mvn spring-boot:run -pl vault-anomaly-service -Dspring-boot.run.profiles=local` | 8087 |

### What to Start for Common Scenarios

You don't need to start all 16 services. Here's what to run for each scenario:

#### Scenario A: Test the AI Chat Demo (most common)

```bash
# Terminal 1: Orchestrator (the main AI service)
mvn spring-boot:run -pl agent-orchestrator-service -Dspring-boot.run.profiles=local

# Open browser: http://localhost:8084/demo.html
```

The orchestrator has mock fallbacks, so it works standalone for AI demo testing.

#### Scenario B: Test Account / Balance Flows End-to-End

```bash
# Terminal 1: Vault Identity (authentication)
mvn spring-boot:run -pl vault-identity-service -Dspring-boot.run.profiles=local

# Terminal 2: Vault Policy (authorization)
mvn spring-boot:run -pl vault-policy-service -Dspring-boot.run.profiles=local

# Terminal 3: MCP Core Banking (banking tools)
mvn spring-boot:run -pl mcp-core-banking-server -Dspring-boot.run.profiles=local

# Terminal 4: Account Agent
mvn spring-boot:run -pl agent-account-service -Dspring-boot.run.profiles=local

# Terminal 5: Orchestrator
mvn spring-boot:run -pl agent-orchestrator-service -Dspring-boot.run.profiles=local
```

#### Scenario C: Test Card Flows

```bash
# Terminal 1: vault-identity-service (local profile)
# Terminal 2: vault-policy-service (local profile)
# Terminal 3: mcp-card-management-server (local profile)
# Terminal 4: agent-card-service (local profile)
# Terminal 5: agent-orchestrator-service (local profile)
```

#### Scenario D: Full Stack Local

Start in this order (each in its own terminal):
1. `config-server`
2. `vault-identity-service`, `vault-policy-service`, `vault-audit-service`, `vault-anomaly-service`
3. `mcp-core-banking-server`, `mcp-card-management-server`
4. `agent-account-service`, `agent-card-service`, `agent-loans-service`, `agent-wealth-service`
5. `agent-orchestrator-service`
6. `api-gateway`

### IntelliJ IDEA Debug Configuration (Local Profile)

1. Open `bank-agent/pom.xml` as a project
2. Navigate to any `*Application.java` main class (e.g., `OrchestratorApplication.java`)
3. Right-click → **Run** or **Debug**
4. Edit the Run Configuration:
   - **Active profiles**: `local`
   - (No environment variables needed!)
5. Click **Debug** to start with breakpoints

**Detailed steps:**
1. **Run → Edit Configurations → + → Spring Boot**
2. **Main class**: select the `*Application` class for your service
3. **Active profiles**: type `local`
4. **Use classpath of module**: select the corresponding module (e.g., `agent-orchestrator-service`)
5. Click **Apply** and **Debug**

> **Tip**: You can create a compound run configuration in IntelliJ to start multiple services at once.

### VS Code Debug Configuration (Local Profile)

Add this to `.vscode/launch.json`:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Orchestrator (Local)",
      "request": "launch",
      "mainClass": "com.idfcfirstbank.agent.orchestrator.OrchestratorApplication",
      "projectName": "agent-orchestrator-service",
      "vmArgs": "-Dspring.profiles.active=local"
    },
    {
      "type": "java",
      "name": "Account Agent (Local)",
      "request": "launch",
      "mainClass": "com.idfcfirstbank.agent.account.AccountAgentApplication",
      "projectName": "agent-account-service",
      "vmArgs": "-Dspring.profiles.active=local"
    },
    {
      "type": "java",
      "name": "Vault Identity (Local)",
      "request": "launch",
      "mainClass": "com.idfcfirstbank.agent.vault.identity.VaultIdentityApplication",
      "projectName": "vault-identity-service",
      "vmArgs": "-Dspring.profiles.active=local"
    }
  ]
}
```

### How the Local Profile Works

Each service has an `application-local.yml` file in `src/main/resources/` that overrides the default `application.yml`:

- **Ollama as LLM**: Services use Ollama's OpenAI-compatible endpoint (`http://localhost:11434/v1`) with `llama3.1` model
- **Spring AI auto-config exclusion**: The local profile excludes `AnthropicAutoConfiguration` to avoid bean conflicts (no Anthropic API key needed locally)
- **Database per service**: Each service with a database points to its own PostgreSQL database on `localhost:5432`
- **In-memory fallbacks**: SessionService gracefully falls back to in-memory storage if Redis is unavailable
- **DEBUG logging**: All AI and agent packages log at DEBUG level for easy troubleshooting

### Troubleshooting Local Debug Mode

| Problem | Solution |
|---------|----------|
| `expected single matching bean but found 3: anthropicChatModel...` | Ensure you're using `-Dspring-boot.run.profiles=local` — the local profile excludes conflicting auto-configs |
| `Connection refused` to Ollama | Run `ollama serve` and verify with `curl http://localhost:11434/api/tags` |
| `Connection refused` to PostgreSQL/Redis/Kafka | Ensure Docker containers are running: `docker compose -f deployment/docker/docker-compose.yaml ps` |
| Port already in use | Check what's using the port: `lsof -i :<port>` (macOS/Linux) or `netstat -ano \| findstr :<port>` (Windows) |
| Database does not exist | The `init-databases.sh` script creates all databases. Restart postgres: `docker compose -f deployment/docker/docker-compose.yaml restart postgres` |
| Ollama model not found | Pull the model: `ollama pull llama3.1` |
| Slow first LLM response | First request to Ollama loads the model into memory (~10-30s). Subsequent requests are faster. |

---

## 7. Run Services Individually (Default Profile)

Uses the default profile with `.env` file — for when you need cloud LLM providers. For local development with Ollama, see [Section 6](#6-local-debug-mode-recommended-for-development) instead.

### Load environment variables

**macOS / Linux (bash/zsh):**
```bash
export $(cat .env | grep -v '^#' | xargs)
```

**Windows PowerShell:**
```powershell
Get-Content .env | Where-Object { $_ -notmatch '^#' -and $_ -match '=' } | ForEach-Object {
    $parts = $_ -split '=', 2
    [System.Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), 'Process')
}
```

**Windows CMD:**
```cmd
for /f "usebackq tokens=1,2 delims==" %%a in (".env") do set %%a=%%b
```

### Start services in order

Open separate terminal tabs/windows for each service. Run from the project root:

```bash
# 1. Config Server (start first, wait for it to be ready)
mvn -pl config-server spring-boot:run

# 2. Vault services (can start in parallel once config-server is up)
mvn -pl vault-identity-service spring-boot:run \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:postgresql://localhost:5432/vault_identity"

mvn -pl vault-policy-service spring-boot:run \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:postgresql://localhost:5432/vault_policy"

mvn -pl vault-audit-service spring-boot:run \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:postgresql://localhost:5432/vault_audit"

mvn -pl vault-anomaly-service spring-boot:run

# 3. MCP servers
mvn -pl mcp-core-banking-server spring-boot:run \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:postgresql://localhost:5432/core_banking"

mvn -pl mcp-card-management-server spring-boot:run

# 4. Agent services
mvn -pl agent-account-service spring-boot:run \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:postgresql://localhost:5432/account_agent"

mvn -pl agent-card-service spring-boot:run

# 5. Orchestrator
mvn -pl agent-orchestrator-service spring-boot:run

# 6. API Gateway (start last)
mvn -pl api-gateway spring-boot:run
```

### IntelliJ IDEA / VS Code Setup

**IntelliJ IDEA:**
1. File → Open → select `bank-agent/pom.xml` → Open as Project
2. Ensure Project SDK is set to Java 21 (File → Project Structure)
3. Run/Debug any `*Application.java` main class directly
4. Add environment variables in Run Configuration → Environment variables → paste from `.env`

**VS Code:**
1. Install "Extension Pack for Java" and "Spring Boot Extension Pack"
2. Open the `bank-agent` folder
3. Create `.vscode/launch.json` (see below)
4. Press F5 to run any service

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Orchestrator",
      "request": "launch",
      "mainClass": "com.idfcfirstbank.agent.orchestrator.OrchestratorApplication",
      "projectName": "agent-orchestrator-service",
      "envFile": "${workspaceFolder}/.env"
    },
    {
      "type": "java",
      "name": "Vault Identity",
      "request": "launch",
      "mainClass": "com.idfcfirstbank.agent.vault.identity.VaultIdentityApplication",
      "projectName": "vault-identity-service",
      "envFile": "${workspaceFolder}/.env"
    },
    {
      "type": "java",
      "name": "API Gateway",
      "request": "launch",
      "mainClass": "com.idfcfirstbank.agent.gateway.ApiGatewayApplication",
      "projectName": "api-gateway",
      "envFile": "${workspaceFolder}/.env"
    }
  ]
}
```

---

## 8. Run All Services via Docker Compose

For a full stack run without any IDE — everything in containers:

### Step 1: Build Docker images

```bash
# From project root — build all JARs first
mvn clean package -DskipTests

# Build Docker images for each service
docker build -t ai-agent-platform/config-server:1.0.0-SNAPSHOT ./config-server
docker build -t ai-agent-platform/api-gateway:1.0.0-SNAPSHOT ./api-gateway
docker build -t ai-agent-platform/vault-identity:1.0.0-SNAPSHOT ./vault-identity-service
docker build -t ai-agent-platform/vault-policy:1.0.0-SNAPSHOT ./vault-policy-service
docker build -t ai-agent-platform/vault-audit:1.0.0-SNAPSHOT ./vault-audit-service
docker build -t ai-agent-platform/vault-anomaly:1.0.0-SNAPSHOT ./vault-anomaly-service
docker build -t ai-agent-platform/orchestrator:1.0.0-SNAPSHOT ./agent-orchestrator-service
docker build -t ai-agent-platform/account-agent:1.0.0-SNAPSHOT ./agent-account-service
docker build -t ai-agent-platform/card-agent:1.0.0-SNAPSHOT ./agent-card-service
docker build -t ai-agent-platform/core-banking-mcp:1.0.0-SNAPSHOT ./mcp-core-banking-server
docker build -t ai-agent-platform/card-mcp:1.0.0-SNAPSHOT ./mcp-card-management-server
```

**Or use the build script** (create once, reuse):

**macOS/Linux — `build-all.sh`:**
```bash
#!/bin/bash
set -e
echo "=== Building Maven artifacts ==="
mvn clean package -DskipTests

echo "=== Building Docker images ==="
for dir in config-server api-gateway vault-identity-service vault-policy-service \
  vault-audit-service vault-anomaly-service agent-orchestrator-service \
  agent-account-service agent-card-service mcp-core-banking-server \
  mcp-card-management-server; do
  name=$(echo "$dir" | sed 's/-service$//' | sed 's/-server$//')
  echo "Building $name..."
  docker build -t "ai-agent-platform/${name}:1.0.0-SNAPSHOT" "./${dir}"
done
echo "=== All images built ==="
```

**Windows — `build-all.ps1`:**
```powershell
Write-Host "=== Building Maven artifacts ===" -ForegroundColor Cyan
mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) { exit 1 }

$services = @{
    "config-server" = "config-server"
    "api-gateway" = "api-gateway"
    "vault-identity-service" = "vault-identity"
    "vault-policy-service" = "vault-policy"
    "vault-audit-service" = "vault-audit"
    "vault-anomaly-service" = "vault-anomaly"
    "agent-orchestrator-service" = "orchestrator"
    "agent-account-service" = "account-agent"
    "agent-card-service" = "card-agent"
    "mcp-core-banking-server" = "core-banking-mcp"
    "mcp-card-management-server" = "card-mcp"
}

Write-Host "=== Building Docker images ===" -ForegroundColor Cyan
foreach ($dir in $services.Keys) {
    $name = $services[$dir]
    Write-Host "Building $name..." -ForegroundColor Yellow
    docker build -t "ai-agent-platform/${name}:1.0.0-SNAPSHOT" "./$dir"
    if ($LASTEXITCODE -ne 0) { exit 1 }
}
Write-Host "=== All images built ===" -ForegroundColor Green
```

### Step 2: Start everything

```bash
cd deployment/docker

# Set your LLM API key
export ANTHROPIC_API_KEY=sk-ant-your-key-here   # macOS/Linux
# $env:ANTHROPIC_API_KEY="sk-ant-your-key-here" # Windows PowerShell

# Start all services
docker compose up -d

# With monitoring (Prometheus + Grafana)
docker compose --profile monitoring up -d

# Check status
docker compose ps

# View logs
docker compose logs -f orchestrator
docker compose logs -f vault-identity
```

---

## 9. Flutter Admin Dashboard

### Install Flutter dependencies

```bash
cd admin-dashboard
flutter pub get
```

### Run in browser (Chrome)

```bash
flutter run -d chrome --web-port 4200
```

### Configure API endpoint

The dashboard connects to the API Gateway. Edit `lib/config/app_config.dart` or set at runtime:

```dart
// Default: http://localhost:8080
// Change if your gateway runs on a different port
```

### Build for deployment

```bash
flutter build web --release
# Output: admin-dashboard/build/web/
```

---

## 10. Verify Everything is Running

### Health check all services

**macOS/Linux:**
```bash
#!/bin/bash
services=(
  "Config Server|http://localhost:8888/actuator/health"
  "API Gateway|http://localhost:8080/actuator/health"
  "Vault Identity|http://localhost:8081/actuator/health"
  "Vault Policy|http://localhost:8082/actuator/health"
  "Vault Audit|http://localhost:8083/actuator/health"
  "Orchestrator|http://localhost:8084/actuator/health"
  "Account Agent|http://localhost:8085/actuator/health"
  "Core Banking MCP|http://localhost:8086/actuator/health"
  "Vault Anomaly|http://localhost:8087/actuator/health"
  "Card Agent|http://localhost:8088/actuator/health"
  "Card MCP|http://localhost:8089/actuator/health"
  "OPA|http://localhost:8181/health"
)

for svc in "${services[@]}"; do
  IFS='|' read -r name url <<< "$svc"
  status=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null)
  if [ "$status" = "200" ]; then
    echo "  [OK]  $name ($url)"
  else
    echo "  [FAIL] $name ($url) - HTTP $status"
  fi
done
```

**Windows PowerShell:**
```powershell
$services = @(
    @{Name="Config Server"; Url="http://localhost:8888/actuator/health"},
    @{Name="API Gateway"; Url="http://localhost:8080/actuator/health"},
    @{Name="Vault Identity"; Url="http://localhost:8081/actuator/health"},
    @{Name="Vault Policy"; Url="http://localhost:8082/actuator/health"},
    @{Name="Vault Audit"; Url="http://localhost:8083/actuator/health"},
    @{Name="Orchestrator"; Url="http://localhost:8084/actuator/health"},
    @{Name="Account Agent"; Url="http://localhost:8085/actuator/health"},
    @{Name="Core Banking MCP"; Url="http://localhost:8086/actuator/health"},
    @{Name="Vault Anomaly"; Url="http://localhost:8087/actuator/health"},
    @{Name="Card Agent"; Url="http://localhost:8088/actuator/health"},
    @{Name="Card MCP"; Url="http://localhost:8089/actuator/health"},
    @{Name="OPA"; Url="http://localhost:8181/health"}
)

foreach ($svc in $services) {
    try {
        $response = Invoke-WebRequest -Uri $svc.Url -TimeoutSec 5 -ErrorAction Stop
        Write-Host "  [OK]  $($svc.Name)" -ForegroundColor Green
    } catch {
        Write-Host "  [FAIL] $($svc.Name) - $($_.Exception.Message)" -ForegroundColor Red
    }
}
```

### Quick smoke test

```bash
# 1. Authenticate an agent
curl -X POST http://localhost:8081/api/v1/auth/agent/authenticate \
  -H "Content-Type: application/json" \
  -d '{"agentId":"account-agent","secret":"dev-secret"}'

# 2. Check a balance (Tier 0, no LLM needed)
curl -X POST http://localhost:8084/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"test-001","customerId":"CUST001","message":"What is my account balance?"}'

# 3. Check policy evaluation
curl -X POST http://localhost:8082/api/v1/policies/evaluate \
  -H "Content-Type: application/json" \
  -d '{"agentId":"account-agent","actionType":"VIEW_BALANCE","customerId":"CUST001"}'
```

---

## 11. Common Issues & Troubleshooting

### Docker issues

| Problem | Solution |
|---------|----------|
| `port is already allocated` | Stop conflicting service: `lsof -i :8080` (macOS) or `netstat -ano \| findstr :8080` (Windows) |
| `Cannot connect to Docker daemon` | Start Docker Desktop. On Windows, ensure WSL 2 is enabled. |
| Kafka fails to start | Ensure Zookeeper is healthy first: `docker compose up -d zookeeper && sleep 10 && docker compose up -d kafka` |
| PostgreSQL init-databases.sh fails | On Windows, ensure the script has LF line endings (not CRLF). Run: `git config core.autocrlf input` |
| Out of memory | Increase Docker Desktop memory to 12 GB+ in Settings → Resources |

### Java / Maven issues

| Problem | Solution |
|---------|----------|
| `java: invalid source release: 21` | Ensure `JAVA_HOME` points to JDK 21. Run: `echo $JAVA_HOME` / `echo %JAVA_HOME%` |
| Maven can't resolve Spring AI | Spring AI uses milestone repo. Check `pom.xml` has `https://repo.spring.io/milestone` in repositories. |
| `Connection refused` to localhost services | Start infrastructure Docker containers first (Step 3) |
| Flyway migration fails | Ensure the target database exists. Run: `docker exec platform-postgres psql -U platform -l` |

### LLM Provider issues

| Problem | Solution |
|---------|----------|
| `401 Unauthorized` from Anthropic/OpenAI | Verify API key in `.env`. Check no trailing whitespace. |
| Ollama connection refused | Ensure `ollama serve` is running and model is pulled: `ollama list` |
| Azure OpenAI 404 | Verify `LLM_BASE_URL` includes your resource name and API version |
| Tier 0 queries still calling LLM | This is correct behavior — Tier 0 routes should NOT call LLM. Check TierRouter logs. |

### Flutter issues

| Problem | Solution |
|---------|----------|
| `flutter: command not found` | Add Flutter to PATH. macOS: add to `~/.zshrc`. Windows: add to System Environment Variables. |
| Chrome not detected | Run `flutter doctor` and install Chrome, or use `flutter run -d web-server` |
| API connection failed in dashboard | Ensure API Gateway (port 8080) is running and CORS is configured |

### Windows-specific notes

- **Line endings**: Configure git to handle line endings correctly:
  ```bash
  git config --global core.autocrlf input
  ```
- **Long paths**: Enable long path support if builds fail:
  ```powershell
  # Run as Administrator
  git config --global core.longpaths true
  ```
- **Execution policy** for PowerShell scripts:
  ```powershell
  Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
  ```

---

## 12. Port Reference

| Port | Service | Purpose |
|------|---------|---------|
| 5432 | PostgreSQL | Database |
| 6379 | Redis | Cache & session store |
| 2181 | Zookeeper | Kafka coordination |
| 29092 | Kafka | Event streaming (host access) |
| 9092 | Kafka | Event streaming (container-to-container) |
| 8181 | OPA | Policy engine |
| 8888 | Config Server | Centralized configuration |
| 8080 | API Gateway | Single entry point for all APIs |
| 8081 | Vault Identity | Authentication & JWT |
| 8082 | Vault Policy | Policy evaluation |
| 8083 | Vault Audit | Immutable audit trail |
| 8084 | Agent Orchestrator | Intent routing & LLM orchestration |
| 8085 | Agent Account | Account domain agent |
| 8086 | MCP Core Banking | Finacle banking tools |
| 8087 | Vault Anomaly | Behavioral anomaly detection |
| 8088 | Agent Card | Card domain agent (PCI-DSS) |
| 8089 | MCP Card Management | Card system tools |
| 4200 | Flutter Dashboard | Admin UI (dev server) |
| 9090 | Prometheus | Metrics collection |
| 3000 | Grafana | Metrics dashboards |

---

## 13. Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `LLM_PROVIDER` | `anthropic` | LLM provider: `anthropic`, `openai`, `ollama`, `azure-openai`, `mistral` |
| `LLM_API_KEY` | *(none)* | API key for the selected LLM provider |
| `LLM_MODEL` | `claude-sonnet-4-20250514` | Model name/ID |
| `LLM_BASE_URL` | *(provider default)* | Override base URL (required for Azure, Ollama, Mistral) |
| `LLM_TEMPERATURE` | `0.7` | LLM temperature (0.0-1.0) |
| `LLM_MAX_TOKENS` | `4096` | Max response tokens |
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | *(empty)* | Redis password |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `SPRING_DATASOURCE_USERNAME` | `platform` | PostgreSQL username |
| `SPRING_DATASOURCE_PASSWORD` | `platform_dev_password` | PostgreSQL password |
| `SPRING_PROFILES_ACTIVE` | *(none)* | Active Spring profiles (e.g., `docker`, `pci-dss`) |
| `ANTHROPIC_API_KEY` | *(none)* | Used by docker-compose for Anthropic provider |
| `OPENAI_API_KEY` | *(none)* | Used by docker-compose for OpenAI provider |

---

## Quick Start (TL;DR)

### Option A: Local Debug Mode with Ollama (No API Key Needed)

```bash
# 1. Clone and enter project
git clone https://github.com/dileepjexpert/bank-agent.git && cd bank-agent

# 2. Start infrastructure
cd deployment/docker && docker compose up -d postgres redis zookeeper kafka opa && cd ../..

# 3. Start Ollama with llama3.1
ollama serve &
ollama pull llama3.1

# 4. Build
mvn clean install -DskipTests

# 5. Run orchestrator with local profile
mvn spring-boot:run -pl agent-orchestrator-service -Dspring-boot.run.profiles=local

# 6. Open browser: http://localhost:8084/demo.html
```

### Option B: With Cloud LLM Provider (API Key Required)

```bash
# 1. Clone and enter project
git clone https://github.com/dileepjexpert/bank-agent.git && cd bank-agent

# 2. Start infrastructure
cd deployment/docker && docker compose up -d postgres redis zookeeper kafka opa && cd ../..

# 3. Create .env with your LLM API key (see Section 5)
cp .env.example .env  # then edit with your key

# 4. Build
mvn clean install -DskipTests

# 5. Run orchestrator (the main service)
export $(cat .env | grep -v '^#' | xargs)
mvn -pl agent-orchestrator-service spring-boot:run

# 6. Run Flutter dashboard
cd admin-dashboard && flutter pub get && flutter run -d chrome --web-port 4200
```
