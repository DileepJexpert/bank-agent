# AI Agent Platform - Features & Local Testing Guide

> IDFC First Bank - Enterprise-grade Multi-Agent AI System with Agentic Vault Governance
> Built on Java 21 + Spring Boot 3.3 + Spring AI 1.0

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Features Developed](#2-features-developed)
3. [Module & Port Reference](#3-module--port-reference)
4. [Tech Stack](#4-tech-stack)
5. [Local Setup & Testing](#5-local-setup--testing)
6. [Smoke Tests & API Examples](#6-smoke-tests--api-examples)
7. [Environment Variables Reference](#7-environment-variables-reference)
8. [Troubleshooting](#8-troubleshooting)

---

## 1. Architecture Overview

```
Customer Channels (Chat / Voice / WhatsApp)
          |
    +-----v-----+
    | API Gateway| (Spring Cloud Gateway - port 8080)
    +-----+-----+
          |
    +-----v------+     +---------------+
    | Orchestrator|---->| Agentic Vault |
    |  (Router)   |     |  (Governance) |
    +--+---+---+--+     +------+--------+
       |   |   |               |
  +----v+ +v--+ +v---+   Policy Check
  |Acct | |Card| |Loan|        |
  |Agent| |Agt | |Agt |        |
  +--+--+ +-+--+ +-+--+       |
     |      |      |           |
    +v------v------v-----------v+
    |     MCP Server Layer       |
    | (Standardized Tool APIs)   |
    +----------+-----------------+
               |
    +----------v-----------------+
    |    Banking Systems          |
    | (Finacle, LOS, Cards...)   |
    +-----------------------------+
```

---

## 2. Features Developed

### A. API Gateway & Configuration

| Feature | Description |
|---------|-------------|
| **API Gateway** (port 8080) | Spring Cloud Gateway as the single entry point. Provides CORS handling, circuit breakers, rate limiting (Redis-backed), load balancing, and Agentic Vault policy interception on every request. |
| **Config Server** (port 8888) | Spring Cloud Config with Git-backed centralized configuration for all microservices. Supports dynamic property reload. |

### B. Agentic Vault - Governance Layer

| Feature | Description |
|---------|-------------|
| **Vault Identity Service** (port 8081) | Agent and customer authentication with JWT token issuance. Components: `AgentAuthService`, `CustomerAuthService`, `TokenService`, `JwksService`. |
| **Vault Policy Service** (port 8082) | OPA (Open Policy Agent) Rego-based policy evaluation engine (policy-as-code). Supports policy reload and centralized governance. |
| **Vault Audit Service** (port 8083) | Immutable audit trail powered by Kafka to PostgreSQL pipeline. Includes `AuditEventConsumer`, `AuditQueryService`, and `AuditExportService`. |
| **Vault Anomaly Service** (port 8087) | Behavioral anomaly and fraud detection. Components: `AgentBehaviorAnalyzer`, `PromptInjectionDetector`, `CrossInstanceCorrelator`, `BaselineService`. |

**OPA Governance Policies (Rego):**

| Policy File | Purpose |
|-------------|---------|
| `account-policies.rego` | Account agent action authorization |
| `base-policies.rego` | Core governance rules |
| `data-access-control.rego` | Row-level access control |
| `pci-dss-policies.rego` | PCI-DSS compliance enforcement |
| `regulatory-policies.rego` | Regulatory compliance rules |
| `temporal-policies.rego` | Time-based access restrictions |

### C. Agent Orchestrator (port 8084)

The central brain of the platform with:

- **Intent Detection** - Classifies user queries into banking domains
- **Language Detection** - Multi-language support
- **Tiered Routing Strategy:**
  - **Tier 0** - Java Rule Engine (~40% of queries, zero LLM cost): keyword matching, simple patterns
  - **Tier 1** - Small Model: simple classification tasks
  - **Tier 2** - Large LLM: complex queries (Claude, GPT-4, Mistral, etc.)
- **Session Management** - Conversation state machine with Redis-backed sessions
- **Multi-LLM Support** - Configurable providers: Anthropic Claude, OpenAI GPT-4, Ollama (local/free), Azure OpenAI, Mistral

### D. Domain Agents

| Agent | Port | Capabilities |
|-------|------|-------------|
| **Account Agent** | 8085 | Balance inquiry, account statements, transaction history, FD creation, cheque book requests. Uses Spring AI function calling with `AccountTools`. |
| **Card Agent** | 8088 | Card disputes, rewards, activation, EMI conversion, card blocking. Includes PCI-DSS security with `PciDssSecurityConfig` and `CardDataMaskingFilter` for sensitive data protection. |

### E. MCP Servers (Model Context Protocol - Standardized Tool Layer)

| MCP Server | Port | Tools Provided |
|------------|------|---------------|
| **Core Banking MCP** | 8086 | `get_balance`, `get_account_details`, `transfer_funds`, `create_fd`, `get_transaction_history` (Finacle integration) |
| **Card Management MCP** | 8089 | `block_card`, `file_dispute`, `get_rewards`, `activate_card`, `convert_emi` |

### F. Common Starter Library (`common-spring-boot-starter`)

Shared library across all services providing:
- Security utilities and JWT validation
- Vault client for policy checks
- PII masking filters
- Common error handling
- Observability (Micrometer metrics, correlation IDs)

### G. Admin Dashboard (Flutter Web - port 4200)

**Screens:**
- Login, Dashboard (KPIs), Agent Management, Policy Management, Audit Logs, Monitoring, Copilot, Settings

**MCP Widgets:**

| Widget | Purpose |
|--------|---------|
| `account_summary_widget` | Account overview |
| `transaction_table_widget` | Transaction history table |
| `payment_form_widget` | Payment processing |
| `card_management_widget` | Card operations |
| `dispute_form_widget` | Dispute filing |
| `fd_creation_widget` | Fixed deposit creation |
| `loan_processing_widget` | Loan operations |
| `emi_calculator_widget` | EMI calculations |
| `reward_catalog_widget` | Reward redemption |
| `compliance_dashboard_widget` | Policy compliance monitoring |
| `customer_360_widget` | Holistic customer view |

**State Management:** Provider pattern with `DashboardProvider`, `AgentProvider`, `PolicyProvider`, `McpWidgetProvider`
**Real-time Updates:** WebSocket integration via `WebSocketService`

### H. Deployment Infrastructure

| Component | Description |
|-----------|-------------|
| **Docker Compose** | Full-stack local deployment (15 containers) |
| **Kubernetes Manifests** | Namespaces, ConfigMaps, Deployments, Services, Ingress for AWS EKS |
| **Helm Charts** | Single-command EKS deployment with HPA auto-scaling |
| **CI/CD** | GitHub Actions for Maven build, Flutter analyze, Docker push, EKS deploy |
| **Monitoring** | Prometheus (port 9090) + Grafana (port 3000) with Micrometer metrics |

---

## 3. Module & Port Reference

| Port | Service | Description |
|------|---------|-------------|
| 5432 | PostgreSQL | Database |
| 6379 | Redis | Cache & session store |
| 2181 | Zookeeper | Kafka coordination |
| 9092 / 29092 | Kafka | Event streaming (container / host) |
| 8181 | OPA | Policy engine |
| 8888 | Config Server | Centralized configuration |
| 8080 | API Gateway | Single entry point |
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

## 4. Tech Stack

| Layer | Technology |
|-------|-----------|
| **Runtime** | Java 21 + GraalVM |
| **Framework** | Spring Boot 3.3, Spring AI 1.0, Spring Cloud 2023.0 |
| **LLM Integration** | Spring AI (Anthropic, OpenAI, Ollama, Azure, Mistral) |
| **Policy Engine** | OPA (Open Policy Agent) with Rego |
| **Messaging** | Apache Kafka + Spring Kafka |
| **Database** | PostgreSQL (Flyway migrations) + Redis |
| **Frontend** | Flutter Web (Material 3, Provider state management) |
| **Infrastructure** | AWS EKS, Helm, Docker |
| **Security** | JWT (JJWT), RBAC, PCI-DSS, PII masking |
| **Observability** | Micrometer + Prometheus + Grafana, structured JSON logging |
| **Resilience** | Resilience4j circuit breakers, rate limiting |

---

## 5. Local Setup & Testing

### 5.1 Prerequisites

| Tool | Version | Install (macOS) | Install (Windows) |
|------|---------|----------------|-------------------|
| Java JDK | 21+ | `brew install --cask temurin@21` | `winget install EclipseAdoptium.Temurin.21.JDK` |
| Maven | 3.9+ | `brew install maven` | `winget install Apache.Maven` |
| Docker Desktop | 4.25+ | `brew install --cask docker` | Download from docker.com |
| Git | 2.40+ | `brew install git` | `winget install Git.Git` |
| Flutter SDK | 3.16+ | `brew install --cask flutter` | Download from flutter.dev |

**Verify installations:**

```bash
java -version          # Should show 21.x.x
mvn -version           # Should show 3.9+
docker --version       # Should show 24+
docker compose version # Should show v2.20+
flutter --version      # Should show 3.16+
```

**Docker Desktop Settings** - Allocate at least 4 CPUs, 8 GB RAM, 30 GB Disk (recommended: 6 CPUs, 12 GB RAM).

### 5.2 Clone & Branch

```bash
git clone https://github.com/dileepjexpert/bank-agent.git
cd bank-agent
git checkout claude/ai-agent-platform-UahWl
```

### 5.3 Start Infrastructure (Docker)

```bash
cd deployment/docker
docker compose up -d postgres redis zookeeper kafka opa

# Wait ~30-45 seconds, then verify:
docker compose ps

# Health checks:
docker exec platform-postgres pg_isready -U platform     # accepting connections
docker exec platform-redis redis-cli ping                 # PONG
curl http://localhost:8181/health                          # {}
```

### 5.4 Configure LLM Provider

```bash
# From project root
cp .env.example .env
# Edit .env with your preferred LLM provider and API key
```

**LLM Options:**

```bash
# Option A: Anthropic Claude (recommended)
LLM_PROVIDER=anthropic
LLM_API_KEY=sk-ant-your-key-here
LLM_MODEL=claude-sonnet-4-20250514

# Option B: OpenAI
LLM_PROVIDER=openai
LLM_API_KEY=sk-your-openai-key
LLM_MODEL=gpt-4o

# Option C: Ollama (free, no API key needed)
LLM_PROVIDER=ollama
LLM_API_KEY=not-needed
LLM_MODEL=llama3.1
LLM_BASE_URL=http://localhost:11434

# Option D: Azure OpenAI
LLM_PROVIDER=azure-openai
LLM_API_KEY=your-azure-key
LLM_MODEL=gpt-4o
LLM_BASE_URL=https://your-resource.openai.azure.com

# Option E: Mistral
LLM_PROVIDER=mistral
LLM_API_KEY=your-mistral-key
LLM_MODEL=mistral-large-latest
```

**Using Ollama (completely free):**

```bash
# macOS
brew install ollama && ollama serve & && ollama pull llama3.1

# Windows - download from https://ollama.com/download/windows
ollama serve
ollama pull llama3.1
```

### 5.5 Build All Modules

```bash
cd /path/to/bank-agent

# Full build (skip tests for initial setup)
mvn clean install -DskipTests

# Build a single module (faster iteration)
mvn clean install -pl agent-orchestrator-service -am -DskipTests
```

### 5.6 Run Services (Development Mode)

Load environment variables, then start each service in a separate terminal:

**macOS / Linux:**

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

**Start services in order:**

```bash
# Terminal 1: Config Server (start first, wait for it to be ready)
mvn -pl config-server spring-boot:run

# Terminal 2-5: Vault Services (start after config-server is up)
mvn -pl vault-identity-service spring-boot:run
mvn -pl vault-policy-service spring-boot:run
mvn -pl vault-audit-service spring-boot:run
mvn -pl vault-anomaly-service spring-boot:run

# Terminal 6-7: MCP Servers
mvn -pl mcp-core-banking-server spring-boot:run
mvn -pl mcp-card-management-server spring-boot:run

# Terminal 8-9: Agent Services
mvn -pl agent-account-service spring-boot:run
mvn -pl agent-card-service spring-boot:run

# Terminal 10: Orchestrator
mvn -pl agent-orchestrator-service spring-boot:run

# Terminal 11: API Gateway (start last)
mvn -pl api-gateway spring-boot:run
```

### 5.7 Run via Docker Compose (Full Stack, No IDE)

```bash
# Build all JARs
mvn clean package -DskipTests

# Build Docker images
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

# Start everything
cd deployment/docker
export ANTHROPIC_API_KEY=sk-ant-your-key-here
docker compose up -d

# With monitoring (Prometheus + Grafana)
docker compose --profile monitoring up -d

# Check status
docker compose ps
```

### 5.8 Run Admin Dashboard

```bash
cd admin-dashboard
flutter pub get
flutter run -d chrome --web-port 4200
# Access at http://localhost:4200
```

### 5.9 IDE Setup

**IntelliJ IDEA:**
1. File > Open > select `bank-agent/pom.xml` > Open as Project
2. Set Project SDK to Java 21 (File > Project Structure)
3. Run/Debug any `*Application.java` main class
4. Add environment variables from `.env` in Run Configuration

**VS Code:**
1. Install "Extension Pack for Java" and "Spring Boot Extension Pack"
2. Open the `bank-agent` folder
3. Create `.vscode/launch.json` with envFile pointing to `.env`
4. Press F5 to run any service

---

## 6. Smoke Tests & API Examples

### 6.1 Health Check All Services

```bash
curl -s http://localhost:8888/actuator/health   # Config Server
curl -s http://localhost:8080/actuator/health   # API Gateway
curl -s http://localhost:8081/actuator/health   # Vault Identity
curl -s http://localhost:8082/actuator/health   # Vault Policy
curl -s http://localhost:8083/actuator/health   # Vault Audit
curl -s http://localhost:8084/actuator/health   # Orchestrator
curl -s http://localhost:8085/actuator/health   # Account Agent
curl -s http://localhost:8086/actuator/health   # Core Banking MCP
curl -s http://localhost:8087/actuator/health   # Vault Anomaly
curl -s http://localhost:8088/actuator/health   # Card Agent
curl -s http://localhost:8089/actuator/health   # Card MCP
curl -s http://localhost:8181/health            # OPA
```

### 6.2 Authenticate an Agent

```bash
curl -X POST http://localhost:8081/api/v1/auth/agent/authenticate \
  -H "Content-Type: application/json" \
  -d '{"agentId":"account-agent","secret":"dev-secret"}'
```

### 6.3 Chat with the Orchestrator

```bash
# Tier 0 query (rule engine, no LLM cost)
curl -X POST http://localhost:8084/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "test-001",
    "customerId": "CUST001",
    "message": "What is my account balance?"
  }'
```

### 6.4 Policy Evaluation

```bash
curl -X POST http://localhost:8082/api/v1/policies/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": "account-agent",
    "actionType": "VIEW_BALANCE",
    "customerId": "CUST001"
  }'
```

### 6.5 Quick Start (TL;DR)

```bash
# 1. Clone and enter project
git clone https://github.com/dileepjexpert/bank-agent.git && cd bank-agent

# 2. Start infrastructure
cd deployment/docker && docker compose up -d postgres redis zookeeper kafka opa && cd ../..

# 3. Create .env with your LLM API key
cp .env.example .env  # then edit with your key

# 4. Build
mvn clean install -DskipTests

# 5. Run orchestrator (the main service)
export $(cat .env | grep -v '^#' | xargs)
mvn -pl agent-orchestrator-service spring-boot:run

# 6. Run Flutter dashboard
cd admin-dashboard && flutter pub get && flutter run -d chrome --web-port 4200
```

---

## 7. Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `LLM_PROVIDER` | `anthropic` | Provider: `anthropic`, `openai`, `ollama`, `azure-openai`, `mistral` |
| `LLM_API_KEY` | *(required)* | API key for the selected LLM provider |
| `LLM_MODEL` | `claude-sonnet-4-20250514` | Model name/ID |
| `LLM_BASE_URL` | *(provider default)* | Override URL (required for Azure, Ollama, Mistral) |
| `LLM_TEMPERATURE` | `0.7` | LLM temperature (0.0-1.0) |
| `LLM_MAX_TOKENS` | `4096` | Max response tokens |
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | *(empty)* | Redis password |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `SPRING_DATASOURCE_USERNAME` | `platform` | PostgreSQL username |
| `SPRING_DATASOURCE_PASSWORD` | `platform_dev_password` | PostgreSQL password |
| `SPRING_PROFILES_ACTIVE` | *(none)* | Active Spring profiles (`docker`, `pci-dss`, `local`) |
| `ANTHROPIC_API_KEY` | *(none)* | Used by docker-compose for Anthropic provider |
| `OPENAI_API_KEY` | *(none)* | Used by docker-compose for OpenAI provider |

---

## 8. Troubleshooting

### Docker Issues

| Problem | Solution |
|---------|----------|
| `port is already allocated` | Stop conflicting service: `lsof -i :8080` (macOS) or `netstat -ano | findstr :8080` (Windows) |
| `Cannot connect to Docker daemon` | Start Docker Desktop. On Windows, ensure WSL 2 is enabled. |
| Kafka fails to start | Ensure Zookeeper is healthy first: `docker compose up -d zookeeper && sleep 10 && docker compose up -d kafka` |
| PostgreSQL init-databases.sh fails | On Windows, ensure the script has LF line endings (not CRLF) |
| Out of memory | Increase Docker Desktop memory to 12 GB+ in Settings > Resources |

### Java / Maven Issues

| Problem | Solution |
|---------|----------|
| `java: invalid source release: 21` | Ensure `JAVA_HOME` points to JDK 21 |
| Maven can't resolve Spring AI | Check `pom.xml` has `https://repo.spring.io/milestone` in repositories |
| `Connection refused` to localhost | Start infrastructure Docker containers first |
| Flyway migration fails | Ensure the target database exists: `docker exec platform-postgres psql -U platform -l` |

### LLM Provider Issues

| Problem | Solution |
|---------|----------|
| `401 Unauthorized` from Anthropic/OpenAI | Verify API key in `.env`. Check no trailing whitespace. |
| Ollama connection refused | Ensure `ollama serve` is running and model is pulled: `ollama list` |
| Tier 0 queries still calling LLM | This is correct — Tier 0 routes should NOT call LLM. Check TierRouter logs. |

### Flutter Issues

| Problem | Solution |
|---------|----------|
| `flutter: command not found` | Add Flutter to PATH |
| Chrome not detected | Run `flutter doctor` and install Chrome |
| API connection failed in dashboard | Ensure API Gateway (port 8080) is running and CORS is configured |

---

*Apache License 2.0 - See [LICENSE](LICENSE)*
