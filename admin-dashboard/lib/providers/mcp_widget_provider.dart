import 'package:flutter/material.dart';
import '../models/mcp_widget_models.dart';
import '../services/mcp_widget_service.dart';

class McpWidgetProvider extends ChangeNotifier {
  final McpWidgetService _service = McpWidgetService();

  String _customerId = 'CUST001';
  bool _loading = false;
  String? _error;

  // Data holders
  AccountSummary? _accountSummary;
  List<Transaction> _transactions = [];
  CardDetails? _cardDetails;
  LoanEligibility? _loanEligibility;
  Map<String, dynamic>? _customer360;
  Map<String, dynamic>? _loanProcessing;
  Map<String, dynamic>? _compliance;
  Map<String, dynamic>? _rewardData;

  // Getters
  String get customerId => _customerId;
  bool get loading => _loading;
  String? get error => _error;
  AccountSummary? get accountSummary => _accountSummary;
  List<Transaction> get transactions => _transactions;
  CardDetails? get cardDetails => _cardDetails;
  LoanEligibility? get loanEligibility => _loanEligibility;
  Map<String, dynamic>? get customer360 => _customer360;
  Map<String, dynamic>? get loanProcessing => _loanProcessing;
  Map<String, dynamic>? get compliance => _compliance;
  Map<String, dynamic>? get rewardData => _rewardData;

  void setCustomerId(String id) {
    _customerId = id;
    notifyListeners();
  }

  Future<void> loadAccountSummary() async {
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      final data = await _service.getAccountSummary(_customerId);
      _accountSummary = AccountSummary.fromJson(data);
    } catch (e) {
      _error = e.toString();
    }
    _loading = false;
    notifyListeners();
  }

  Future<void> loadTransactions({String? category, String? type}) async {
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      final data = await _service.getTransactions(_customerId, category: category, type: type);
      _transactions = data.map((e) => Transaction.fromJson(e)).toList();
    } catch (e) {
      _error = e.toString();
    }
    _loading = false;
    notifyListeners();
  }

  Future<void> loadCardDetails() async {
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      final data = await _service.getCardDetails(_customerId);
      _cardDetails = CardDetails.fromJson(data);
    } catch (e) {
      _error = e.toString();
    }
    _loading = false;
    notifyListeners();
  }

  Future<void> loadRewardPoints() async {
    _loading = true;
    notifyListeners();
    try {
      _rewardData = await _service.getRewardPoints(_customerId);
    } catch (e) {
      _error = e.toString();
    }
    _loading = false;
    notifyListeners();
  }

  Future<void> loadLoanEligibility() async {
    _loading = true;
    notifyListeners();
    try {
      final data = await _service.checkLoanEligibility(_customerId);
      _loanEligibility = LoanEligibility.fromJson(data);
    } catch (e) {
      _error = e.toString();
    }
    _loading = false;
    notifyListeners();
  }

  EMIResult calculateEMI(double principal, int tenureMonths, double rate) {
    final data = _service.calculateEMI(principal, tenureMonths, rate);
    return EMIResult.fromJson(data);
  }

  Future<Map<String, dynamic>> createFD({
    required double amount,
    required int tenureDays,
    required bool autoRenewal,
  }) async {
    return _service.createFD(
      customerId: _customerId,
      amount: amount,
      tenureDays: tenureDays,
      autoRenewal: autoRenewal,
    );
  }

  Future<Map<String, dynamic>> initiateTransfer({
    required String toAccount,
    required double amount,
    required String purpose,
    String? beneficiaryName,
  }) async {
    return _service.initiateTransfer(
      fromAccount: _accountSummary?.accountNumber ?? '',
      toAccount: toAccount,
      amount: amount,
      purpose: purpose,
      beneficiaryName: beneficiaryName,
    );
  }

  Future<Map<String, dynamic>> blockCard(String reason) async {
    return _service.blockCard(_customerId, _cardDetails?.cardLast4 ?? '', reason);
  }

  Future<Map<String, dynamic>> raiseDispute({
    required String transactionId,
    required String reason,
    required String description,
    double? amount,
  }) async {
    return _service.raiseDispute(
      customerId: _customerId,
      transactionId: transactionId,
      reason: reason,
      description: description,
      amount: amount,
    );
  }

  Future<Map<String, dynamic>> redeemPoints(String itemId, int points) async {
    return _service.redeemPoints(_customerId, itemId, points);
  }

  // Branch co-pilot
  Future<void> loadCustomer360() async {
    _loading = true;
    notifyListeners();
    try {
      _customer360 = await _service.getCustomer360(_customerId);
    } catch (e) {
      _error = e.toString();
    }
    _loading = false;
    notifyListeners();
  }

  Future<void> loadLoanProcessing() async {
    _loading = true;
    notifyListeners();
    try {
      _loanProcessing = await _service.getLoanProcessingData(_customerId);
    } catch (e) {
      _error = e.toString();
    }
    _loading = false;
    notifyListeners();
  }

  Future<void> loadComplianceDashboard() async {
    _loading = true;
    notifyListeners();
    try {
      _compliance = await _service.getComplianceDashboard();
    } catch (e) {
      _error = e.toString();
    }
    _loading = false;
    notifyListeners();
  }
}
