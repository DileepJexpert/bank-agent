class AgentModel {
  final String id;
  final String name;
  final String type;
  final AgentStatus status;
  final int activeInstances;
  final double avgResponseTime;
  final double successRate;
  final String? description;
  final String? llmProvider;
  final String? llmModel;
  final DateTime? lastActive;
  final Map<String, dynamic>? configuration;

  const AgentModel({
    required this.id,
    required this.name,
    required this.type,
    required this.status,
    this.activeInstances = 0,
    this.avgResponseTime = 0.0,
    this.successRate = 0.0,
    this.description,
    this.llmProvider,
    this.llmModel,
    this.lastActive,
    this.configuration,
  });

  factory AgentModel.fromJson(Map<String, dynamic> json) {
    return AgentModel(
      id: json['id'] as String,
      name: json['name'] as String,
      type: json['type'] as String,
      status: AgentStatus.fromString(json['status'] as String),
      activeInstances: json['active_instances'] as int? ?? 0,
      avgResponseTime: (json['avg_response_time'] as num?)?.toDouble() ?? 0.0,
      successRate: (json['success_rate'] as num?)?.toDouble() ?? 0.0,
      description: json['description'] as String?,
      llmProvider: json['llm_provider'] as String?,
      llmModel: json['llm_model'] as String?,
      lastActive: json['last_active'] != null
          ? DateTime.parse(json['last_active'] as String)
          : null,
      configuration: json['configuration'] as Map<String, dynamic>?,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'type': type,
      'status': status.value,
      'active_instances': activeInstances,
      'avg_response_time': avgResponseTime,
      'success_rate': successRate,
      'description': description,
      'llm_provider': llmProvider,
      'llm_model': llmModel,
      'last_active': lastActive?.toIso8601String(),
      'configuration': configuration,
    };
  }

  AgentModel copyWith({
    String? id,
    String? name,
    String? type,
    AgentStatus? status,
    int? activeInstances,
    double? avgResponseTime,
    double? successRate,
    String? description,
    String? llmProvider,
    String? llmModel,
    DateTime? lastActive,
    Map<String, dynamic>? configuration,
  }) {
    return AgentModel(
      id: id ?? this.id,
      name: name ?? this.name,
      type: type ?? this.type,
      status: status ?? this.status,
      activeInstances: activeInstances ?? this.activeInstances,
      avgResponseTime: avgResponseTime ?? this.avgResponseTime,
      successRate: successRate ?? this.successRate,
      description: description ?? this.description,
      llmProvider: llmProvider ?? this.llmProvider,
      llmModel: llmModel ?? this.llmModel,
      lastActive: lastActive ?? this.lastActive,
      configuration: configuration ?? this.configuration,
    );
  }
}

enum AgentStatus {
  running('running'),
  stopped('stopped'),
  error('error'),
  starting('starting'),
  degraded('degraded');

  final String value;
  const AgentStatus(this.value);

  static AgentStatus fromString(String value) {
    return AgentStatus.values.firstWhere(
      (s) => s.value == value.toLowerCase(),
      orElse: () => AgentStatus.stopped,
    );
  }

  bool get isHealthy => this == AgentStatus.running;
}
