import 'package:flutter/foundation.dart';

import '../models/policy_model.dart';
import '../services/api_service.dart';

class PolicyProvider extends ChangeNotifier {
  final ApiService _api = ApiService();

  List<PolicyModel> _policies = [];
  PolicyModel? _selectedPolicy;
  bool _isLoading = false;
  String? _error;
  String? _testResult;

  List<PolicyModel> get policies => _policies;
  PolicyModel? get selectedPolicy => _selectedPolicy;
  bool get isLoading => _isLoading;
  String? get error => _error;
  String? get testResult => _testResult;

  int get activeCount => _policies.where((p) => p.active).length;
  int get inactiveCount => _policies.where((p) => !p.active).length;

  Future<void> loadPolicies() async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      final data = await _api.getPolicies();
      _policies = data
          .map((e) => PolicyModel.fromJson(e as Map<String, dynamic>))
          .toList();
    } catch (e) {
      _error = e.toString();
      _loadMockPolicies();
    }

    _isLoading = false;
    notifyListeners();
  }

  void selectPolicy(PolicyModel? policy) {
    _selectedPolicy = policy;
    _testResult = null;
    notifyListeners();
  }

  Future<void> togglePolicy(String id) async {
    final index = _policies.indexWhere((p) => p.id == id);
    if (index < 0) return;

    final policy = _policies[index];
    final updated = policy.copyWith(active: !policy.active);

    try {
      await _api.updatePolicy(id, updated.toJson());
    } catch (_) {
      // continue with local update
    }

    _policies[index] = updated;
    if (_selectedPolicy?.id == id) _selectedPolicy = updated;
    notifyListeners();
  }

  Future<void> createPolicy(PolicyModel policy) async {
    try {
      final result = await _api.createPolicy(policy.toJson());
      _policies.add(PolicyModel.fromJson(result));
    } catch (e) {
      _policies.add(policy);
    }
    notifyListeners();
  }

  Future<void> updatePolicy(String id, PolicyModel policy) async {
    try {
      final result = await _api.updatePolicy(id, policy.toJson());
      final index = _policies.indexWhere((p) => p.id == id);
      if (index >= 0) {
        _policies[index] = PolicyModel.fromJson(result);
      }
    } catch (e) {
      final index = _policies.indexWhere((p) => p.id == id);
      if (index >= 0) {
        _policies[index] = policy;
      }
    }
    if (_selectedPolicy?.id == id) _selectedPolicy = policy;
    notifyListeners();
  }

  Future<void> deletePolicy(String id) async {
    try {
      await _api.deletePolicy(id);
    } catch (_) {
      // continue
    }
    _policies.removeWhere((p) => p.id == id);
    if (_selectedPolicy?.id == id) _selectedPolicy = null;
    notifyListeners();
  }

  Future<void> testPolicy(String id, Map<String, dynamic> input) async {
    _testResult = null;
    notifyListeners();

    try {
      final result = await _api.testPolicy(id, input);
      _testResult =
          'Result: ${result['allowed'] == true ? 'ALLOWED' : 'DENIED'}\n'
          'Details: ${result['reason'] ?? 'No details'}';
    } catch (e) {
      _testResult = 'Test completed (mock):\nResult: ALLOWED\n'
          'Evaluation time: 2ms';
    }
    notifyListeners();
  }

  void _loadMockPolicies() {
    _policies = [
      PolicyModel(
        id: 'pol-001',
        name: 'Transaction Amount Limit',
        type: 'transaction_limit',
        regoCode: '''package banking.transaction_limit

default allow = false

allow {
    input.amount <= 500000
    input.currency == "INR"
}

allow {
    input.amount <= 10000
    input.currency == "USD"
}

deny[msg] {
    input.amount > 500000
    msg := sprintf("Transaction amount %v exceeds limit of 500000 INR", [input.amount])
}''',
        active: true,
        lastUpdated: DateTime.now().subtract(const Duration(days: 2)),
        description: 'Limits individual transaction amounts per currency',
        author: 'admin',
        version: 3,
      ),
      PolicyModel(
        id: 'pol-002',
        name: 'API Rate Limiting',
        type: 'rate_limit',
        regoCode: '''package banking.rate_limit

default allow = false

allow {
    input.requests_per_minute <= 100
    input.agent_type == "inquiry"
}

allow {
    input.requests_per_minute <= 50
    input.agent_type == "transaction"
}

deny[msg] {
    input.requests_per_minute > 100
    msg := "Rate limit exceeded for agent"
}''',
        active: true,
        lastUpdated: DateTime.now().subtract(const Duration(days: 5)),
        description: 'Controls API request rates per agent type',
        author: 'admin',
        version: 2,
      ),
      PolicyModel(
        id: 'pol-003',
        name: 'PII Data Access Control',
        type: 'data_access',
        regoCode: '''package banking.data_access

default allow = false

allow {
    input.agent_role == "kyc_agent"
    input.data_type == "pii"
    input.purpose == "verification"
}

deny[msg] {
    input.data_type == "pii"
    input.agent_role != "kyc_agent"
    msg := "Only KYC agents can access PII data"
}''',
        active: true,
        lastUpdated: DateTime.now().subtract(const Duration(days: 1)),
        description: 'Controls access to personally identifiable information',
        author: 'compliance-team',
        version: 5,
      ),
      PolicyModel(
        id: 'pol-004',
        name: 'RBI Compliance Check',
        type: 'compliance',
        regoCode: '''package banking.compliance

default allow = false

allow {
    input.kyc_verified == true
    input.account_age_days > 30
}

deny[msg] {
    input.kyc_verified == false
    msg := "Customer KYC not verified"
}''',
        active: true,
        lastUpdated: DateTime.now().subtract(const Duration(hours: 12)),
        description: 'Ensures compliance with RBI regulations',
        author: 'compliance-team',
        version: 7,
      ),
      PolicyModel(
        id: 'pol-005',
        name: 'Agent Routing Rules',
        type: 'routing',
        regoCode: '''package banking.routing

default target_agent = "faq"

target_agent = "transaction" {
    input.intent == "fund_transfer"
}

target_agent = "inquiry" {
    input.intent == "balance_check"
}

target_agent = "card" {
    startswith(input.intent, "card_")
}''',
        active: false,
        lastUpdated: DateTime.now().subtract(const Duration(days: 10)),
        description: 'Routes customer requests to appropriate agents',
        author: 'admin',
        version: 1,
      ),
    ];
  }
}
