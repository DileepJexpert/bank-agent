import 'package:flutter/foundation.dart';

import '../models/dashboard_stats_model.dart';
import '../models/agent_model.dart';
import '../models/audit_event_model.dart';
import '../services/api_service.dart';

class DashboardProvider extends ChangeNotifier {
  final ApiService _api = ApiService();

  DashboardStatsModel _stats = const DashboardStatsModel();
  List<AgentModel> _agents = [];
  List<AuditEventModel> _recentEvents = [];
  bool _isLoading = false;
  String? _error;

  DashboardStatsModel get stats => _stats;
  List<AgentModel> get agents => _agents;
  List<AuditEventModel> get recentEvents => _recentEvents;
  bool get isLoading => _isLoading;
  String? get error => _error;

  Future<void> loadDashboard() async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      await Future.wait([
        _loadStats(),
        _loadAgents(),
        _loadRecentEvents(),
      ]);
    } catch (e) {
      _error = e.toString();
      // Load mock data for demo
      _loadMockData();
    }

    _isLoading = false;
    notifyListeners();
  }

  Future<void> _loadStats() async {
    try {
      final data = await _api.getDashboardStats();
      _stats = DashboardStatsModel.fromJson(data);
    } catch (_) {
      rethrow;
    }
  }

  Future<void> _loadAgents() async {
    try {
      final data = await _api.getAgents();
      _agents = data
          .map((e) => AgentModel.fromJson(e as Map<String, dynamic>))
          .toList();
    } catch (_) {
      rethrow;
    }
  }

  Future<void> _loadRecentEvents() async {
    try {
      final data = await _api.getAuditEvents(pageSize: 10);
      final events = data['events'] as List<dynamic>? ?? [];
      _recentEvents = events
          .map((e) => AuditEventModel.fromJson(e as Map<String, dynamic>))
          .toList();
    } catch (_) {
      rethrow;
    }
  }

  void _loadMockData() {
    _stats = DashboardStatsModel(
      totalInteractions: 24853,
      activeAgents: 5,
      avgResponseTime: 1.23,
      successRate: 97.8,
      costPerInteraction: 0.0042,
      tier0Percentage: 73.5,
      activeSessions: 142,
      totalAgents: 7,
      interactionTimeline: List.generate(
        24,
        (i) => InteractionDataPoint(
          timestamp: DateTime.now()
              .subtract(Duration(hours: 23 - i))
              .toIso8601String(),
          count: 800 + (i * 47 % 400),
          successRate: 95.0 + (i % 5),
        ),
      ),
      agentInteractions: {
        'Account Inquiry': 8420,
        'Transaction': 5210,
        'Card Services': 3890,
        'Loan Assistant': 2940,
        'KYC Verification': 2180,
        'Complaint Handler': 1213,
        'General FAQ': 1000,
      },
    );

    _agents = [
      AgentModel(
        id: 'agent-001',
        name: 'Account Inquiry Agent',
        type: 'inquiry',
        status: AgentStatus.running,
        activeInstances: 3,
        avgResponseTime: 0.85,
        successRate: 98.5,
        llmProvider: 'Anthropic',
        llmModel: 'claude-sonnet-4-20250514',
        lastActive: DateTime.now(),
      ),
      AgentModel(
        id: 'agent-002',
        name: 'Transaction Agent',
        type: 'transaction',
        status: AgentStatus.running,
        activeInstances: 2,
        avgResponseTime: 1.42,
        successRate: 96.2,
        llmProvider: 'OpenAI',
        llmModel: 'gpt-4o',
        lastActive: DateTime.now(),
      ),
      AgentModel(
        id: 'agent-003',
        name: 'Card Services Agent',
        type: 'card',
        status: AgentStatus.running,
        activeInstances: 2,
        avgResponseTime: 1.15,
        successRate: 97.8,
        llmProvider: 'Anthropic',
        llmModel: 'claude-sonnet-4-20250514',
        lastActive: DateTime.now(),
      ),
      AgentModel(
        id: 'agent-004',
        name: 'Loan Assistant Agent',
        type: 'loan',
        status: AgentStatus.degraded,
        activeInstances: 1,
        avgResponseTime: 2.31,
        successRate: 92.1,
        llmProvider: 'OpenAI',
        llmModel: 'gpt-4o',
        lastActive: DateTime.now().subtract(const Duration(minutes: 5)),
      ),
      AgentModel(
        id: 'agent-005',
        name: 'KYC Verification Agent',
        type: 'kyc',
        status: AgentStatus.running,
        activeInstances: 2,
        avgResponseTime: 1.85,
        successRate: 99.1,
        llmProvider: 'Anthropic',
        llmModel: 'claude-sonnet-4-20250514',
        lastActive: DateTime.now(),
      ),
      AgentModel(
        id: 'agent-006',
        name: 'Complaint Handler Agent',
        type: 'complaint',
        status: AgentStatus.stopped,
        activeInstances: 0,
        avgResponseTime: 0.0,
        successRate: 0.0,
        llmProvider: 'Anthropic',
        llmModel: 'claude-sonnet-4-20250514',
      ),
      AgentModel(
        id: 'agent-007',
        name: 'General FAQ Agent',
        type: 'faq',
        status: AgentStatus.running,
        activeInstances: 1,
        avgResponseTime: 0.62,
        successRate: 99.5,
        llmProvider: 'Ollama',
        llmModel: 'llama3',
        lastActive: DateTime.now(),
      ),
    ];

    _recentEvents = [
      AuditEventModel(
        id: 'evt-001',
        timestamp: DateTime.now()
            .subtract(const Duration(minutes: 2))
            .toIso8601String(),
        agentId: 'agent-001',
        agentName: 'Account Inquiry Agent',
        action: 'balance_check',
        status: 'success',
        userId: 'user-8421',
        durationMs: 842,
      ),
      AuditEventModel(
        id: 'evt-002',
        timestamp: DateTime.now()
            .subtract(const Duration(minutes: 5))
            .toIso8601String(),
        agentId: 'agent-002',
        agentName: 'Transaction Agent',
        action: 'fund_transfer',
        status: 'success',
        userId: 'user-3291',
        durationMs: 1523,
      ),
      AuditEventModel(
        id: 'evt-003',
        timestamp: DateTime.now()
            .subtract(const Duration(minutes: 8))
            .toIso8601String(),
        agentId: 'agent-004',
        agentName: 'Loan Assistant Agent',
        action: 'eligibility_check',
        status: 'failure',
        userId: 'user-1052',
        durationMs: 3201,
        detail: 'Timeout connecting to core banking system',
      ),
      AuditEventModel(
        id: 'evt-004',
        timestamp: DateTime.now()
            .subtract(const Duration(minutes: 12))
            .toIso8601String(),
        agentId: 'agent-003',
        agentName: 'Card Services Agent',
        action: 'card_block',
        status: 'success',
        userId: 'user-6712',
        durationMs: 1105,
      ),
      AuditEventModel(
        id: 'evt-005',
        timestamp: DateTime.now()
            .subtract(const Duration(minutes: 15))
            .toIso8601String(),
        agentId: 'agent-005',
        agentName: 'KYC Verification Agent',
        action: 'document_verify',
        status: 'success',
        userId: 'user-2290',
        durationMs: 2410,
      ),
    ];
  }

  Future<void> refresh() async {
    await loadDashboard();
  }
}
