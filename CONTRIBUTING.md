# Contributing to AI Agent Platform

Thank you for your interest in contributing to the AI Agent Platform!

## Getting Started

### Prerequisites
- Java 21 (LTS)
- Maven 3.9+
- Docker & Docker Compose
- Flutter SDK 3.x (for admin dashboard)
- kubectl & helm (for Kubernetes deployment)

### Local Development Setup

1. Clone the repository:
```bash
git clone https://github.com/dileepjexpert/bank-agent.git
cd bank-agent
```

2. Start infrastructure dependencies:
```bash
docker-compose -f deployment/docker/docker-compose.yaml up -d postgres redis kafka zookeeper
```

3. Build all modules:
```bash
mvn clean install -DskipTests
```

4. Run a specific service:
```bash
cd agent-orchestrator-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

5. Run the Flutter admin dashboard:
```bash
cd admin-dashboard
flutter pub get
flutter run -d chrome
```

## Project Structure

This is a Maven multi-module monorepo. Each module is a Spring Boot microservice.

| Module | Port | Description |
|--------|------|-------------|
| api-gateway | 8080 | Spring Cloud Gateway |
| config-server | 8888 | Centralized configuration |
| vault-identity-service | 8081 | Authentication & JWT |
| vault-policy-service | 8082 | OPA policy evaluation |
| vault-audit-service | 8083 | Audit trail |
| agent-orchestrator-service | 8084 | Intent routing & session management |
| agent-account-service | 8085 | Account domain agent |
| mcp-core-banking-server | 8086 | Core banking MCP server |

## Development Guidelines

### Code Style
- Follow standard Java conventions
- Use Java 21 features (records, pattern matching, text blocks) where appropriate
- Use Lombok judiciously (@Data, @Slf4j, @Builder)
- Use records for DTOs and value objects

### Commit Messages
- Use conventional commits: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`
- Keep the first line under 72 characters

### Pull Requests
- Create a feature branch from `main`
- Write tests for new functionality
- Ensure all tests pass: `mvn verify`
- Update documentation if needed

### LLM Configuration
When adding LLM features, always use Spring AI's abstraction layer to keep providers configurable. Never hardcode a specific LLM provider.

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
