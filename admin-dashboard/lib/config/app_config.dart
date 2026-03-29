class AppConfig {
  static const String appName = 'AI Agent Platform';
  static const String appVersion = '1.0.0';

  // API Configuration
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8080',
  );

  static const String wsBaseUrl = String.fromEnvironment(
    'WS_BASE_URL',
    defaultValue: 'ws://localhost:8080',
  );

  // API Endpoints
  static const String authLogin = '/api/v1/auth/login';
  static const String agents = '/api/v1/agents';
  static const String policies = '/api/v1/policies';
  static const String auditEvents = '/api/v1/audit/events';
  static const String dashboardStats = '/api/v1/dashboard/stats';
  static const String monitoring = '/api/v1/monitoring';
  static const String settings = '/api/v1/settings';
  static const String llmConfig = '/api/v1/settings/llm';
  static const String healthCheck = '/api/v1/health';

  // WebSocket Endpoints
  static const String wsMonitoring = '/ws/monitoring';
  static const String wsEvents = '/ws/events';

  // Timeouts
  static const Duration apiTimeout = Duration(seconds: 30);
  static const Duration wsReconnectDelay = Duration(seconds: 5);

  // Pagination
  static const int defaultPageSize = 25;
  static const int maxPageSize = 100;
}

enum Environment { development, staging, production }

class EnvironmentConfig {
  final Environment environment;
  final String apiBaseUrl;
  final String wsBaseUrl;
  final bool enableLogging;

  const EnvironmentConfig({
    required this.environment,
    required this.apiBaseUrl,
    required this.wsBaseUrl,
    this.enableLogging = false,
  });

  static const EnvironmentConfig development = EnvironmentConfig(
    environment: Environment.development,
    apiBaseUrl: 'http://localhost:8080',
    wsBaseUrl: 'ws://localhost:8080',
    enableLogging: true,
  );

  static const EnvironmentConfig staging = EnvironmentConfig(
    environment: Environment.staging,
    apiBaseUrl: 'https://staging-api.agent-platform.internal',
    wsBaseUrl: 'wss://staging-api.agent-platform.internal',
    enableLogging: true,
  );

  static const EnvironmentConfig production = EnvironmentConfig(
    environment: Environment.production,
    apiBaseUrl: 'https://api.agent-platform.internal',
    wsBaseUrl: 'wss://api.agent-platform.internal',
    enableLogging: false,
  );
}
