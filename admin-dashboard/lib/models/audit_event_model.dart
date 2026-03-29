class AuditEventModel {
  final String id;
  final String timestamp;
  final String agentId;
  final String agentName;
  final String action;
  final String status;
  final String? userId;
  final String? sessionId;
  final String? detail;
  final int? durationMs;
  final Map<String, dynamic>? metadata;

  const AuditEventModel({
    required this.id,
    required this.timestamp,
    required this.agentId,
    required this.agentName,
    required this.action,
    required this.status,
    this.userId,
    this.sessionId,
    this.detail,
    this.durationMs,
    this.metadata,
  });

  factory AuditEventModel.fromJson(Map<String, dynamic> json) {
    return AuditEventModel(
      id: json['id'] as String,
      timestamp: json['timestamp'] as String,
      agentId: json['agent_id'] as String,
      agentName: json['agent_name'] as String? ?? json['agent_id'] as String,
      action: json['action'] as String,
      status: json['status'] as String,
      userId: json['user_id'] as String?,
      sessionId: json['session_id'] as String?,
      detail: json['detail'] as String?,
      durationMs: json['duration_ms'] as int?,
      metadata: json['metadata'] as Map<String, dynamic>?,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'timestamp': timestamp,
      'agent_id': agentId,
      'agent_name': agentName,
      'action': action,
      'status': status,
      'user_id': userId,
      'session_id': sessionId,
      'detail': detail,
      'duration_ms': durationMs,
      'metadata': metadata,
    };
  }

  DateTime get dateTime => DateTime.parse(timestamp);

  bool get isSuccess => status.toLowerCase() == 'success';
  bool get isFailure =>
      status.toLowerCase() == 'failure' || status.toLowerCase() == 'error';
}
