# AI Agent Platform

Enterprise-grade multi-agent system with Agentic Vault governance, built on Java 21 + Spring Boot 3.x.

## Architecture

```
Customer Channels (Chat/Voice/WhatsApp)
          │
    ┌─────▼─────┐
    │ API Gateway│ (Spring Cloud Gateway)
    └─────┬─────┘
          │
    ┌─────▼──────┐     ┌─────────────┐
    │ Orchestrator│────▶│ Agentic Vault│
    │  (Router)   │     │  (Governance)│
    └──┬───┬───┬──┘     └──────┬──────┘
       │   │   │               │
  ┌────▼┐ ┌▼──┐ ┌▼───┐   Policy Check
  │Acct │ │Card│ │Loan│        │
  │Agent│ │Agt │ │Agt │        │
  └──┬──┘ └─┬──┘ └─┬──┘       │
     │      │      │           │
    ┌▼──────▼──────▼──────────▼┐
    │     MCP Server Layer      │
    │ (Standardized Tool APIs)  │
    └───────────┬───────────────┘
                │
    ┌───────────▼───────────────┐
    │    Banking Systems         │
    │ (Finacle, LOS, Cards...)  │
    └────────────────────────────┘
```

## Key Features

- **Multi-LLM Support**: Configurable providers — Anthropic Claude, OpenAI GPT-4, Ollama (local), Azure OpenAI, Mistral
- **Agentic Vault**: Centralized governance with OPA policy-as-code, JWT auth, immutable audit trail
- **Tiered Routing**: 40% of queries handled by Java rule engine (zero LLM cost), remaining by configurable LLM
- **MCP Servers**: Standardized tool interfaces for all banking system integrations
- **Admin Dashboard**: Flutter web app for managing agents, policies, monitoring, and LLM configuration
- **EKS Ready**: Helm charts, Kubernetes manifests, HPA, network policies for AWS EKS

## Modules

| Module | Port | Description |
|--------|------|-------------|
| `api-gateway` | 8080 | Spring Cloud Gateway with Vault policy interception |
| `config-server` | 8888 | Spring Cloud Config (Git-backed) |
| `vault-identity-service` | 8081 | Agent/customer authentication, JWT issuance |
| `vault-policy-service` | 8082 | OPA Rego policy evaluation engine |
| `vault-audit-service` | 8083 | Immutable audit trail (Kafka → PostgreSQL) |
| `agent-orchestrator-service` | 8084 | Intent detection, tiered routing, state machine |
| `agent-account-service` | 8085 | Account domain agent (balance, statements, FD) |
| `mcp-core-banking-server` | 8086 | Core banking MCP server (Finacle integration) |
| `admin-dashboard` | 80 | Flutter web admin dashboard |
| `common-spring-boot-starter` | — | Shared library (security, vault client, PII masking) |

## Tech Stack

- **Runtime**: Java 21 + GraalVM
- **Framework**: Spring Boot 3.3, Spring AI 1.0, Spring Cloud 2023.0
- **LLM**: Spring AI (Anthropic, OpenAI, Ollama, Azure, Mistral)
- **Policy Engine**: OPA (Open Policy Agent) with Rego
- **Messaging**: Apache Kafka + Spring Kafka
- **Database**: PostgreSQL + Redis
- **Frontend**: Flutter Web (Material 3)
- **Infrastructure**: AWS EKS, Helm, Docker
- **Observability**: Micrometer + Prometheus + Grafana

## Quick Start

### Prerequisites

- Java 21
- Maven 3.9+
- Docker & Docker Compose
- Flutter SDK 3.x (for admin dashboard)

### 1. Start Infrastructure

```bash
cd deployment/docker
docker-compose up -d postgres redis kafka zookeeper
```

### 2. Build All Modules

```bash
mvn clean install -DskipTests
```

### 3. Run Services

```bash
# Terminal 1: Config Server
cd config-server && mvn spring-boot:run

# Terminal 2: Vault services
cd vault-identity-service && mvn spring-boot:run
cd vault-policy-service && mvn spring-boot:run
cd vault-audit-service && mvn spring-boot:run

# Terminal 3: Gateway + Agents
cd api-gateway && mvn spring-boot:run
cd agent-orchestrator-service && mvn spring-boot:run
cd agent-account-service && mvn spring-boot:run

# Terminal 4: MCP Server
cd mcp-core-banking-server && mvn spring-boot:run
```

### 4. Run Admin Dashboard

```bash
cd admin-dashboard
flutter pub get
flutter run -d chrome
```

## LLM Configuration

Switch LLM providers by changing `application.yml`:

```yaml
agent:
  llm:
    provider: anthropic  # anthropic | openai | ollama | azure-openai | mistral
    model-name: claude-sonnet-4-20250514
    api-key: ${LLM_API_KEY}
    temperature: 0.7
    max-tokens: 4096

# Provider-specific settings
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
    openai:
      api-key: ${OPENAI_API_KEY}
    ollama:
      base-url: http://localhost:11434
```

Or configure per-agent via the Admin Dashboard under **Settings > Agent LLM Mapping**.

## EKS Deployment

```bash
# Deploy with Helm
helm upgrade --install ai-agent-platform \
  deployment/helm/ai-agent-platform \
  --values deployment/helm/ai-agent-platform/values.yaml \
  --set global.image.tag=latest
```

## Project Structure

```
bank-agent/
├── pom.xml                          # Parent POM
├── common-spring-boot-starter/      # Shared library
├── api-gateway/                     # Spring Cloud Gateway
├── config-server/                   # Centralized config
├── vault-identity-service/          # Authentication
├── vault-policy-service/            # Policy engine
├── vault-audit-service/             # Audit trail
├── agent-orchestrator-service/      # Intent routing
├── agent-account-service/           # Account agent
├── mcp-core-banking-server/         # Core banking MCP
├── admin-dashboard/                 # Flutter web dashboard
├── vault-policies/                  # OPA Rego policies
├── deployment/
│   ├── docker/                      # Docker Compose
│   ├── kubernetes/                  # K8s manifests
│   └── helm/                        # Helm charts
└── .github/workflows/               # CI/CD
```

## License

Apache License 2.0 — see [LICENSE](LICENSE)
