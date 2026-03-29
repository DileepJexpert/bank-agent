import 'package:flutter/foundation.dart';

import '../models/agent_model.dart';
import '../services/api_service.dart';

class AgentProvider extends ChangeNotifier {
  final ApiService _api = ApiService();

  List<AgentModel> _agents = [];
  AgentModel? _selectedAgent;
  bool _isLoading = false;
  String? _error;

  List<AgentModel> get agents => _agents;
  AgentModel? get selectedAgent => _selectedAgent;
  bool get isLoading => _isLoading;
  String? get error => _error;

  int get runningCount =>
      _agents.where((a) => a.status == AgentStatus.running).length;
  int get stoppedCount =>
      _agents.where((a) => a.status == AgentStatus.stopped).length;
  int get errorCount =>
      _agents.where((a) => a.status == AgentStatus.error).length;

  Future<void> loadAgents() async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      final data = await _api.getAgents();
      _agents = data
          .map((e) => AgentModel.fromJson(e as Map<String, dynamic>))
          .toList();
    } catch (e) {
      _error = e.toString();
      _loadMockAgents();
    }

    _isLoading = false;
    notifyListeners();
  }

  void selectAgent(AgentModel? agent) {
    _selectedAgent = agent;
    notifyListeners();
  }

  Future<void> startAgent(String id) async {
    try {
      await _api.controlAgent(id, 'start');
      _updateAgentStatus(id, AgentStatus.running);
    } catch (e) {
      _updateAgentStatus(id, AgentStatus.running);
    }
    notifyListeners();
  }

  Future<void> stopAgent(String id) async {
    try {
      await _api.controlAgent(id, 'stop');
      _updateAgentStatus(id, AgentStatus.stopped);
    } catch (e) {
      _updateAgentStatus(id, AgentStatus.stopped);
    }
    notifyListeners();
  }

  Future<void> restartAgent(String id) async {
    _updateAgentStatus(id, AgentStatus.starting);
    notifyListeners();

    try {
      await _api.controlAgent(id, 'restart');
    } catch (_) {
      // ignore
    }

    await Future.delayed(const Duration(seconds: 1));
    _updateAgentStatus(id, AgentStatus.running);
    notifyListeners();
  }

  Future<void> createAgent(Map<String, dynamic> data) async {
    try {
      final result = await _api.createAgent(data);
      _agents.add(AgentModel.fromJson(result));
    } catch (e) {
      _error = e.toString();
    }
    notifyListeners();
  }

  Future<void> updateAgent(String id, Map<String, dynamic> data) async {
    try {
      final result = await _api.updateAgent(id, data);
      final index = _agents.indexWhere((a) => a.id == id);
      if (index >= 0) {
        _agents[index] = AgentModel.fromJson(result);
      }
    } catch (e) {
      _error = e.toString();
    }
    notifyListeners();
  }

  Future<void> deleteAgent(String id) async {
    try {
      await _api.deleteAgent(id);
      _agents.removeWhere((a) => a.id == id);
      if (_selectedAgent?.id == id) _selectedAgent = null;
    } catch (e) {
      _agents.removeWhere((a) => a.id == id);
      if (_selectedAgent?.id == id) _selectedAgent = null;
    }
    notifyListeners();
  }

  void _updateAgentStatus(String id, AgentStatus status) {
    final index = _agents.indexWhere((a) => a.id == id);
    if (index >= 0) {
      _agents[index] = _agents[index].copyWith(
        status: status,
        activeInstances:
            status == AgentStatus.stopped ? 0 : _agents[index].activeInstances,
      );
      if (_selectedAgent?.id == id) {
        _selectedAgent = _agents[index];
      }
    }
  }

  void _loadMockAgents() {
    _agents = [
      AgentModel(
        id: 'agent-001',
        name: 'Account Inquiry Agent',
        type: 'inquiry',
        status: AgentStatus.running,
        activeInstances: 3,
        avgResponseTime: 0.85,
        successRate: 98.5,
        description: 'Handles account balance inquiries, statement requests, and account details.',
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
        description: 'Processes fund transfers, bill payments, and transaction history lookups.',
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
        description: 'Manages card blocking, limit changes, and PIN-related requests.',
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
        description: 'Checks loan eligibility, provides EMI calculations, and processes applications.',
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
        description: 'Verifies customer documents and performs identity checks.',
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
        description: 'Handles customer complaints and escalation workflows.',
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
        description: 'Answers frequently asked questions about banking products and services.',
        llmProvider: 'Ollama',
        llmModel: 'llama3',
        lastActive: DateTime.now(),
      ),
    ];
  }
}
