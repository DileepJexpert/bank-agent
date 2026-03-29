class DashboardStatsModel {
  final int totalInteractions;
  final int activeAgents;
  final double avgResponseTime;
  final double successRate;
  final double costPerInteraction;
  final double tier0Percentage;
  final int activeSessions;
  final int totalAgents;
  final List<InteractionDataPoint> interactionTimeline;
  final Map<String, int> agentInteractions;

  const DashboardStatsModel({
    this.totalInteractions = 0,
    this.activeAgents = 0,
    this.avgResponseTime = 0.0,
    this.successRate = 0.0,
    this.costPerInteraction = 0.0,
    this.tier0Percentage = 0.0,
    this.activeSessions = 0,
    this.totalAgents = 0,
    this.interactionTimeline = const [],
    this.agentInteractions = const {},
  });

  factory DashboardStatsModel.fromJson(Map<String, dynamic> json) {
    return DashboardStatsModel(
      totalInteractions: json['total_interactions'] as int? ?? 0,
      activeAgents: json['active_agents'] as int? ?? 0,
      avgResponseTime:
          (json['avg_response_time'] as num?)?.toDouble() ?? 0.0,
      successRate: (json['success_rate'] as num?)?.toDouble() ?? 0.0,
      costPerInteraction:
          (json['cost_per_interaction'] as num?)?.toDouble() ?? 0.0,
      tier0Percentage:
          (json['tier0_percentage'] as num?)?.toDouble() ?? 0.0,
      activeSessions: json['active_sessions'] as int? ?? 0,
      totalAgents: json['total_agents'] as int? ?? 0,
      interactionTimeline: (json['interaction_timeline'] as List<dynamic>?)
              ?.map((e) =>
                  InteractionDataPoint.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
      agentInteractions:
          (json['agent_interactions'] as Map<String, dynamic>?)?.map(
                (k, v) => MapEntry(k, v as int),
              ) ??
              {},
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'total_interactions': totalInteractions,
      'active_agents': activeAgents,
      'avg_response_time': avgResponseTime,
      'success_rate': successRate,
      'cost_per_interaction': costPerInteraction,
      'tier0_percentage': tier0Percentage,
      'active_sessions': activeSessions,
      'total_agents': totalAgents,
      'interaction_timeline':
          interactionTimeline.map((e) => e.toJson()).toList(),
      'agent_interactions': agentInteractions,
    };
  }
}

class InteractionDataPoint {
  final String timestamp;
  final int count;
  final double successRate;

  const InteractionDataPoint({
    required this.timestamp,
    required this.count,
    this.successRate = 0.0,
  });

  factory InteractionDataPoint.fromJson(Map<String, dynamic> json) {
    return InteractionDataPoint(
      timestamp: json['timestamp'] as String,
      count: json['count'] as int? ?? 0,
      successRate: (json['success_rate'] as num?)?.toDouble() ?? 0.0,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'timestamp': timestamp,
      'count': count,
      'success_rate': successRate,
    };
  }
}
