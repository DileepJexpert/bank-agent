import 'dart:convert';
import 'package:http/http.dart' as http;
import '../config/app_config.dart';
import '../services/api_service.dart';

class McpWidgetService {
  static final McpWidgetService _instance = McpWidgetService._internal();
  factory McpWidgetService() => _instance;
  McpWidgetService._internal();

  final ApiService _api = ApiService();

  // --- Core Banking MCP (port 8086) ---

  static const String _coreBankingBase =
      String.fromEnvironment('CORE_BANKING_URL', defaultValue: 'http://localhost:8086');
  static const String _cardMcpBase =
      String.fromEnvironment('CARD_MCP_URL', defaultValue: 'http://localhost:8089');
  static const String _orchestratorBase =
      String.fromEnvironment('ORCHESTRATOR_URL', defaultValue: 'http://localhost:8084');

  final http.Client _client = http.Client();

  Map<String, String> get _headers => {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      };

  // --- Account ---

  Future<Map<String, dynamic>> getAccountSummary(String customerId) async {
    try {
      final response = await _client
          .get(Uri.parse('$_coreBankingBase/api/v1/tools/getBalance?customerId=$customerId'),
              headers: _headers)
          .timeout(AppConfig.apiTimeout);
      return _handle(response);
    } catch (_) {
      return _mockAccountSummary(customerId);
    }
  }

  Future<List<Map<String, dynamic>>> getTransactions(
      String customerId, {int limit = 50, String? category, String? type, String? fromDate, String? toDate}) async {
    try {
      final params = <String, String>{
        'customerId': customerId,
        'limit': limit.toString(),
      };
      if (category != null) params['category'] = category;
      if (type != null) params['type'] = type;
      if (fromDate != null) params['fromDate'] = fromDate;
      if (toDate != null) params['toDate'] = toDate;
      final uri = Uri.parse('$_coreBankingBase/api/v1/tools/getTransactionHistory')
          .replace(queryParameters: params);
      final response = await _client.get(uri, headers: _headers).timeout(AppConfig.apiTimeout);
      final data = _handle(response);
      return (data['transactions'] as List?)?.cast<Map<String, dynamic>>() ?? [];
    } catch (_) {
      return _mockTransactions(customerId);
    }
  }

  Future<Map<String, dynamic>> createFD({
    required String customerId,
    required double amount,
    required int tenureDays,
    required bool autoRenewal,
  }) async {
    try {
      final response = await _client
          .post(Uri.parse('$_coreBankingBase/api/v1/tools/createFD'),
              headers: _headers,
              body: jsonEncode({
                'customerId': customerId,
                'amount': amount,
                'tenureDays': tenureDays,
                'autoRenewal': autoRenewal,
              }))
          .timeout(AppConfig.apiTimeout);
      return _handle(response);
    } catch (_) {
      return {
        'fdId': 'FD${DateTime.now().millisecondsSinceEpoch}',
        'amount': amount,
        'tenureDays': tenureDays,
        'interestRate': _fdRate(tenureDays),
        'maturityAmount': amount * (1 + _fdRate(tenureDays) / 100 * tenureDays / 365),
        'status': 'CREATED',
      };
    }
  }

  Future<Map<String, dynamic>> initiateTransfer({
    required String fromAccount,
    required String toAccount,
    required double amount,
    required String purpose,
    String? beneficiaryName,
  }) async {
    try {
      final response = await _client
          .post(Uri.parse('$_coreBankingBase/api/v1/tools/initiateTransfer'),
              headers: _headers,
              body: jsonEncode({
                'fromAccount': fromAccount,
                'toAccount': toAccount,
                'amount': amount,
                'purpose': purpose,
                'beneficiaryName': beneficiaryName,
              }))
          .timeout(AppConfig.apiTimeout);
      return _handle(response);
    } catch (_) {
      return {
        'referenceId': 'TXN${DateTime.now().millisecondsSinceEpoch}',
        'status': 'OTP_REQUIRED',
        'amount': amount,
        'toAccount': toAccount,
      };
    }
  }

  Future<Map<String, dynamic>> verifyOtp(String referenceId, String otp) async {
    return {
      'referenceId': referenceId,
      'status': 'SUCCESS',
      'timestamp': DateTime.now().toIso8601String(),
    };
  }

  // --- Card Management MCP ---

  Future<Map<String, dynamic>> getCardDetails(String customerId) async {
    try {
      final response = await _client
          .get(Uri.parse('$_cardMcpBase/api/v1/tools/getRewardPoints?customerId=$customerId'),
              headers: _headers)
          .timeout(AppConfig.apiTimeout);
      return _handle(response);
    } catch (_) {
      return _mockCardDetails(customerId);
    }
  }

  Future<Map<String, dynamic>> blockCard(String customerId, String cardLast4, String reason) async {
    try {
      final response = await _client
          .post(Uri.parse('$_cardMcpBase/api/v1/tools/blockCard'),
              headers: _headers,
              body: jsonEncode({
                'customerId': customerId,
                'cardLast4': cardLast4,
                'reason': reason,
              }))
          .timeout(AppConfig.apiTimeout);
      return _handle(response);
    } catch (_) {
      return {'blocked': true, 'blockReference': 'BLK${DateTime.now().millisecondsSinceEpoch}', 'cardLast4': cardLast4};
    }
  }

  Future<Map<String, dynamic>> toggleCardFeature(String customerId, String feature, bool enabled) async {
    return {'feature': feature, 'enabled': enabled, 'status': 'SUCCESS'};
  }

  Future<Map<String, dynamic>> getRewardPoints(String customerId) async {
    try {
      final response = await _client
          .get(Uri.parse('$_cardMcpBase/api/v1/tools/getRewardPoints?customerId=$customerId'),
              headers: _headers)
          .timeout(AppConfig.apiTimeout);
      return _handle(response);
    } catch (_) {
      return _mockRewardPoints();
    }
  }

  Future<Map<String, dynamic>> redeemPoints(String customerId, String itemId, int points) async {
    return {
      'redemptionId': 'RDM${DateTime.now().millisecondsSinceEpoch}',
      'itemId': itemId,
      'pointsUsed': points,
      'status': 'SUCCESS',
    };
  }

  Future<Map<String, dynamic>> raiseDispute({
    required String customerId,
    required String transactionId,
    required String reason,
    required String description,
    double? amount,
  }) async {
    try {
      final response = await _client
          .post(Uri.parse('$_cardMcpBase/api/v1/tools/raiseDispute'),
              headers: _headers,
              body: jsonEncode({
                'customerId': customerId,
                'transactionId': transactionId,
                'reason': reason,
                'description': description,
                'amount': amount,
              }))
          .timeout(AppConfig.apiTimeout);
      return _handle(response);
    } catch (_) {
      return {
        'disputeId': 'DSP${DateTime.now().millisecondsSinceEpoch}',
        'status': 'SUBMITTED',
        'expectedResolutionDate': DateTime.now().add(const Duration(days: 15)).toIso8601String(),
        'transactionId': transactionId,
      };
    }
  }

  Future<Map<String, dynamic>> convertToEMI({
    required String customerId,
    required String transactionId,
    required int tenureMonths,
    required double amount,
  }) async {
    try {
      final response = await _client
          .post(Uri.parse('$_cardMcpBase/api/v1/tools/convertToEMI'),
              headers: _headers,
              body: jsonEncode({
                'customerId': customerId,
                'transactionId': transactionId,
                'tenureMonths': tenureMonths,
                'amount': amount,
              }))
          .timeout(AppConfig.apiTimeout);
      return _handle(response);
    } catch (_) {
      final rate = tenureMonths <= 6 ? 14.0 : (tenureMonths <= 12 ? 13.0 : 12.0);
      final emi = amount * rate / 1200 / (1 - 1 / _pow(1 + rate / 1200, tenureMonths));
      return {
        'emiId': 'EMI${DateTime.now().millisecondsSinceEpoch}',
        'emiAmount': emi.roundToDouble(),
        'interestRate': rate,
        'tenureMonths': tenureMonths,
        'totalCost': (emi * tenureMonths).roundToDouble(),
        'status': 'SUCCESS',
      };
    }
  }

  // --- Loan Eligibility (EMI Calculator) ---

  Future<Map<String, dynamic>> checkLoanEligibility(String customerId) async {
    return {
      'eligible': true,
      'maxAmount': 5000000.0,
      'minAmount': 50000.0,
      'cibilScore': 782,
      'interestRate': 10.5,
      'processingFee': 1.0,
    };
  }

  Map<String, dynamic> calculateEMI(double principal, int tenureMonths, double rate) {
    final monthlyRate = rate / 1200;
    final emi = principal * monthlyRate / (1 - 1 / _pow(1 + monthlyRate, tenureMonths));
    final totalPayable = emi * tenureMonths;
    return {
      'emi': emi.roundToDouble(),
      'totalPayable': totalPayable.roundToDouble(),
      'totalInterest': (totalPayable - principal).roundToDouble(),
      'interestRate': rate,
      'tenureMonths': tenureMonths,
      'principal': principal,
    };
  }

  // --- Branch Co-pilot ---

  Future<Map<String, dynamic>> getCustomer360(String customerId) async {
    return _mockCustomer360(customerId);
  }

  Future<Map<String, dynamic>> getLoanProcessingData(String customerId) async {
    return _mockLoanProcessing(customerId);
  }

  Future<Map<String, dynamic>> getComplianceDashboard() async {
    return _mockComplianceDashboard();
  }

  // --- Helpers ---

  Map<String, dynamic> _handle(http.Response response) {
    if (response.statusCode >= 200 && response.statusCode < 300) {
      if (response.body.isEmpty) return {};
      return jsonDecode(response.body) as Map<String, dynamic>;
    }
    throw Exception('MCP call failed: ${response.statusCode}');
  }

  double _pow(double base, int exp) {
    double result = 1.0;
    for (int i = 0; i < exp; i++) {
      result *= base;
    }
    return result;
  }

  double _fdRate(int tenureDays) {
    if (tenureDays <= 45) return 4.50;
    if (tenureDays <= 90) return 5.25;
    if (tenureDays <= 180) return 5.75;
    if (tenureDays <= 365) return 6.50;
    if (tenureDays <= 730) return 7.00;
    if (tenureDays <= 1095) return 7.25;
    return 7.00;
  }

  // --- Mock Data ---

  Map<String, dynamic> _mockAccountSummary(String customerId) => {
        'customerId': customerId,
        'accountNumber': 'XXXX XXXX 4521',
        'accountType': 'Savings Account',
        'balance': 284750.50,
        'currency': 'INR',
        'branch': 'Mumbai - Bandra West',
        'ifsc': 'IDFB0040001',
        'status': 'ACTIVE',
        'balanceTrend': [
          {'date': '2026-03-01', 'balance': 265000},
          {'date': '2026-03-05', 'balance': 245000},
          {'date': '2026-03-10', 'balance': 310000},
          {'date': '2026-03-15', 'balance': 295000},
          {'date': '2026-03-20', 'balance': 278000},
          {'date': '2026-03-25', 'balance': 290000},
          {'date': '2026-03-30', 'balance': 284750},
        ],
      };

  List<Map<String, dynamic>> _mockTransactions(String customerId) => [
        {'id': 'TXN001', 'date': '2026-03-30', 'description': 'Salary Credit - TCS Ltd', 'merchant': 'TCS Ltd', 'category': 'Income', 'amount': 125000.00, 'type': 'CREDIT', 'balance': 284750.50},
        {'id': 'TXN002', 'date': '2026-03-29', 'description': 'Amazon India Shopping', 'merchant': 'Amazon', 'category': 'Shopping', 'amount': -4599.00, 'type': 'DEBIT', 'balance': 159750.50},
        {'id': 'TXN003', 'date': '2026-03-28', 'description': 'Swiggy Food Order', 'merchant': 'Swiggy', 'category': 'Dining', 'amount': -856.00, 'type': 'DEBIT', 'balance': 164349.50},
        {'id': 'TXN004', 'date': '2026-03-27', 'description': 'Electricity Bill - Adani', 'merchant': 'Adani Electricity', 'category': 'Utilities', 'amount': -3200.00, 'type': 'DEBIT', 'balance': 165205.50},
        {'id': 'TXN005', 'date': '2026-03-26', 'description': 'UPI Transfer to Rajesh', 'merchant': 'UPI', 'category': 'Transfer', 'amount': -15000.00, 'type': 'DEBIT', 'balance': 168405.50},
        {'id': 'TXN006', 'date': '2026-03-25', 'description': 'Netflix Subscription', 'merchant': 'Netflix', 'category': 'Entertainment', 'amount': -649.00, 'type': 'DEBIT', 'balance': 183405.50},
        {'id': 'TXN007', 'date': '2026-03-24', 'description': 'Petrol - Indian Oil', 'merchant': 'Indian Oil', 'category': 'Transport', 'amount': -2500.00, 'type': 'DEBIT', 'balance': 184054.50},
        {'id': 'TXN008', 'date': '2026-03-23', 'description': 'Mutual Fund SIP', 'merchant': 'HDFC AMC', 'category': 'Investment', 'amount': -10000.00, 'type': 'DEBIT', 'balance': 186554.50},
        {'id': 'TXN009', 'date': '2026-03-22', 'description': 'Zomato Gold', 'merchant': 'Zomato', 'category': 'Dining', 'amount': -1200.00, 'type': 'DEBIT', 'balance': 196554.50},
        {'id': 'TXN010', 'date': '2026-03-21', 'description': 'Medical - Apollo Pharmacy', 'merchant': 'Apollo', 'category': 'Healthcare', 'amount': -1850.00, 'type': 'DEBIT', 'balance': 197754.50},
      ];

  Map<String, dynamic> _mockCardDetails(String customerId) => {
        'cardLast4': '7842',
        'cardType': 'VISA Credit',
        'cardName': 'IDFC First Millennia',
        'status': 'ACTIVE',
        'creditLimit': 500000.0,
        'availableLimit': 387500.0,
        'currentOutstanding': 112500.0,
        'dueDate': '2026-04-15',
        'minimumDue': 5625.0,
        'rewardPoints': 24850,
        'rewardValue': 6212.50,
        'internationalEnabled': true,
        'onlineEnabled': true,
        'contactlessEnabled': true,
        'expiryDate': '12/2028',
      };

  Map<String, dynamic> _mockRewardPoints() => {
        'totalPoints': 24850,
        'valueInRupees': 6212.50,
        'expiryDate': '2027-03-31',
        'catalog': [
          {'id': 'RWD001', 'name': 'Amazon Gift Card Rs 500', 'category': 'Shopping', 'points': 2000, 'image': 'amazon'},
          {'id': 'RWD002', 'name': 'Flipkart Voucher Rs 1000', 'category': 'Shopping', 'points': 4000, 'image': 'flipkart'},
          {'id': 'RWD003', 'name': 'BookMyShow Rs 300', 'category': 'Entertainment', 'points': 1200, 'image': 'bookmyshow'},
          {'id': 'RWD004', 'name': 'Cashback Rs 250', 'category': 'Cashback', 'points': 1000, 'image': 'cashback'},
          {'id': 'RWD005', 'name': 'Taj Hotel Voucher Rs 2000', 'category': 'Travel', 'points': 8000, 'image': 'taj'},
          {'id': 'RWD006', 'name': 'Uber Rs 200', 'category': 'Transport', 'points': 800, 'image': 'uber'},
          {'id': 'RWD007', 'name': 'Starbucks Rs 500', 'category': 'Dining', 'points': 2000, 'image': 'starbucks'},
          {'id': 'RWD008', 'name': 'Airline Miles 1000', 'category': 'Travel', 'points': 5000, 'image': 'airline'},
        ],
      };

  Map<String, dynamic> _mockCustomer360(String customerId) => {
        'profile': {
          'customerId': customerId,
          'name': 'Rajesh Kumar Sharma',
          'segment': 'PREMIUM',
          'relationshipManager': 'Priya Singh',
          'customerSince': '2019-06-15',
          'riskProfile': 'MODERATE',
          'npsScore': 72,
          'preferredLanguage': 'English',
          'phone': 'XXXX XXX 8901',
          'email': 'r****@gmail.com',
        },
        'accounts': [
          {'type': 'Savings', 'number': 'XXXX4521', 'balance': 284750.50, 'status': 'ACTIVE'},
          {'type': 'FD', 'number': 'XXXX7823', 'balance': 500000.00, 'status': 'ACTIVE', 'maturity': '2026-09-15'},
          {'type': 'Current', 'number': 'XXXX1190', 'balance': 1250000.00, 'status': 'ACTIVE'},
        ],
        'loans': [
          {'type': 'Home Loan', 'amount': 4500000, 'outstanding': 3200000, 'emi': 42500, 'emiStatus': 'PAID', 'nextDue': '2026-04-05'},
          {'type': 'Car Loan', 'amount': 800000, 'outstanding': 450000, 'emi': 18500, 'emiStatus': 'PAID', 'nextDue': '2026-04-10'},
        ],
        'cards': [
          {'type': 'VISA Credit', 'last4': '7842', 'limit': 500000, 'utilization': 22.5, 'status': 'ACTIVE'},
          {'type': 'RuPay Debit', 'last4': '4521', 'status': 'ACTIVE'},
        ],
        'recentInteractions': [
          {'date': '2026-03-28', 'channel': 'Chat', 'agent': 'Account Agent', 'summary': 'Balance inquiry and mini statement'},
          {'date': '2026-03-25', 'channel': 'Voice', 'agent': 'Card Agent', 'summary': 'Reward points inquiry'},
          {'date': '2026-03-20', 'channel': 'Branch', 'agent': 'Branch RM', 'summary': 'FD renewal discussion'},
          {'date': '2026-03-15', 'channel': 'WhatsApp', 'agent': 'Account Agent', 'summary': 'Transaction alert query'},
        ],
        'pendingRequests': [
          {'type': 'Card Limit Increase', 'status': 'PENDING', 'daysOpen': 3, 'sla': 5},
          {'type': 'Address Change', 'status': 'IN_PROGRESS', 'daysOpen': 1, 'sla': 7},
        ],
        'crossSellRecommendations': [
          {'product': 'Mutual Fund SIP', 'reason': 'Growing savings balance (6 months trend)', 'confidence': 0.85},
          {'product': 'Travel Insurance', 'reason': '5 international transactions this quarter', 'confidence': 0.72},
          {'product': 'Credit Card Upgrade', 'reason': 'High usage, good payment history', 'confidence': 0.90},
        ],
      };

  Map<String, dynamic> _mockLoanProcessing(String customerId) => {
        'customer': {
          'name': 'Rajesh Kumar Sharma',
          'customerId': customerId,
          'cibilScore': 782,
          'income': 150000,
          'employer': 'TCS Ltd',
          'employmentType': 'SALARIED',
          'existingEMI': 61000,
        },
        'eligibility': {
          'eligible': true,
          'maxAmount': 2500000,
          'interestRate': 10.5,
          'foir': 40.67,
          'recommendation': 'APPROVE',
        },
        'documents': [
          {'name': 'PAN Card', 'status': 'VERIFIED', 'uploadedAt': '2026-03-28'},
          {'name': 'Aadhaar Card', 'status': 'VERIFIED', 'uploadedAt': '2026-03-28'},
          {'name': 'Salary Slip (3 months)', 'status': 'VERIFIED', 'uploadedAt': '2026-03-29'},
          {'name': 'Bank Statement (6 months)', 'status': 'PENDING', 'uploadedAt': null},
          {'name': 'Form 16', 'status': 'PENDING', 'uploadedAt': null},
        ],
        'underwritingNotes': 'Customer has strong CIBIL score (782) and stable employment with TCS for 7 years. '
            'FOIR at 40.67% is within acceptable limits. Recommend approval up to Rs 25L at 10.5% for 20 years. '
            'Pending: bank statement and Form 16 verification.',
      };

  Map<String, dynamic> _mockComplianceDashboard() => {
        'summary': {
          'totalViolations': 12,
          'criticalViolations': 1,
          'openItems': 8,
          'resolvedToday': 4,
          'kycPending': 23,
          'kycOverdue': 5,
        },
        'violationTrend': [
          {'date': '2026-03-24', 'count': 3},
          {'date': '2026-03-25', 'count': 5},
          {'date': '2026-03-26', 'count': 2},
          {'date': '2026-03-27', 'count': 4},
          {'date': '2026-03-28', 'count': 1},
          {'date': '2026-03-29', 'count': 3},
          {'date': '2026-03-30', 'count': 2},
        ],
        'flaggedTransactions': [
          {'id': 'FLG001', 'type': 'High Value Transfer', 'amount': 950000, 'riskScore': 78, 'status': 'REVIEW', 'agentId': 'account-agent'},
          {'id': 'FLG002', 'type': 'Collections Call After Hours', 'riskScore': 92, 'status': 'FLAGGED', 'agentId': 'collections-agent'},
          {'id': 'FLG003', 'type': 'PII in Logs Detected', 'riskScore': 85, 'status': 'RESOLVED', 'agentId': 'orchestrator'},
          {'id': 'FLG004', 'type': 'FEMA Declaration Missing', 'riskScore': 70, 'status': 'REVIEW', 'agentId': 'account-agent'},
        ],
        'regulatoryReturns': [
          {'name': 'RBI Monthly Return', 'status': 'FILED', 'dueDate': '2026-04-05'},
          {'name': 'SEBI Quarterly Report', 'status': 'PENDING', 'dueDate': '2026-04-15'},
          {'name': 'FIU STR Filing', 'status': 'OVERDUE', 'dueDate': '2026-03-28'},
        ],
      };
}
