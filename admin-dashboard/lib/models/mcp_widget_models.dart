class AccountSummary {
  final String customerId;
  final String accountNumber;
  final String accountType;
  final double balance;
  final String currency;
  final String branch;
  final String ifsc;
  final String status;
  final List<BalancePoint> balanceTrend;

  AccountSummary({
    required this.customerId,
    required this.accountNumber,
    required this.accountType,
    required this.balance,
    this.currency = 'INR',
    this.branch = '',
    this.ifsc = '',
    this.status = 'ACTIVE',
    this.balanceTrend = const [],
  });

  factory AccountSummary.fromJson(Map<String, dynamic> json) {
    return AccountSummary(
      customerId: json['customerId'] ?? '',
      accountNumber: json['accountNumber'] ?? '',
      accountType: json['accountType'] ?? 'Savings',
      balance: (json['balance'] as num?)?.toDouble() ?? 0,
      currency: json['currency'] ?? 'INR',
      branch: json['branch'] ?? '',
      ifsc: json['ifsc'] ?? '',
      status: json['status'] ?? 'ACTIVE',
      balanceTrend: (json['balanceTrend'] as List?)
              ?.map((e) => BalancePoint.fromJson(e))
              .toList() ??
          [],
    );
  }
}

class BalancePoint {
  final String date;
  final double balance;

  BalancePoint({required this.date, required this.balance});

  factory BalancePoint.fromJson(Map<String, dynamic> json) {
    return BalancePoint(
      date: json['date'] ?? '',
      balance: (json['balance'] as num?)?.toDouble() ?? 0,
    );
  }
}

class Transaction {
  final String id;
  final String date;
  final String description;
  final String merchant;
  final String category;
  final double amount;
  final String type;
  final double balance;

  Transaction({
    required this.id,
    required this.date,
    required this.description,
    required this.merchant,
    required this.category,
    required this.amount,
    required this.type,
    required this.balance,
  });

  factory Transaction.fromJson(Map<String, dynamic> json) {
    return Transaction(
      id: json['id'] ?? '',
      date: json['date'] ?? '',
      description: json['description'] ?? '',
      merchant: json['merchant'] ?? '',
      category: json['category'] ?? '',
      amount: (json['amount'] as num?)?.toDouble() ?? 0,
      type: json['type'] ?? 'DEBIT',
      balance: (json['balance'] as num?)?.toDouble() ?? 0,
    );
  }

  bool get isCredit => type == 'CREDIT' || amount > 0;
}

class CardDetails {
  final String cardLast4;
  final String cardType;
  final String cardName;
  final String status;
  final double creditLimit;
  final double availableLimit;
  final double currentOutstanding;
  final String dueDate;
  final double minimumDue;
  final int rewardPoints;
  final double rewardValue;
  final bool internationalEnabled;
  final bool onlineEnabled;
  final bool contactlessEnabled;
  final String expiryDate;

  CardDetails({
    required this.cardLast4,
    required this.cardType,
    required this.cardName,
    required this.status,
    required this.creditLimit,
    required this.availableLimit,
    required this.currentOutstanding,
    required this.dueDate,
    required this.minimumDue,
    required this.rewardPoints,
    required this.rewardValue,
    this.internationalEnabled = true,
    this.onlineEnabled = true,
    this.contactlessEnabled = true,
    this.expiryDate = '',
  });

  double get utilizationPercent => creditLimit > 0 ? (currentOutstanding / creditLimit * 100) : 0;

  factory CardDetails.fromJson(Map<String, dynamic> json) {
    return CardDetails(
      cardLast4: json['cardLast4'] ?? '',
      cardType: json['cardType'] ?? '',
      cardName: json['cardName'] ?? '',
      status: json['status'] ?? 'ACTIVE',
      creditLimit: (json['creditLimit'] as num?)?.toDouble() ?? 0,
      availableLimit: (json['availableLimit'] as num?)?.toDouble() ?? 0,
      currentOutstanding: (json['currentOutstanding'] as num?)?.toDouble() ?? 0,
      dueDate: json['dueDate'] ?? '',
      minimumDue: (json['minimumDue'] as num?)?.toDouble() ?? 0,
      rewardPoints: (json['rewardPoints'] as num?)?.toInt() ?? 0,
      rewardValue: (json['rewardValue'] as num?)?.toDouble() ?? 0,
      internationalEnabled: json['internationalEnabled'] ?? true,
      onlineEnabled: json['onlineEnabled'] ?? true,
      contactlessEnabled: json['contactlessEnabled'] ?? true,
      expiryDate: json['expiryDate'] ?? '',
    );
  }
}

class RewardItem {
  final String id;
  final String name;
  final String category;
  final int points;
  final String image;

  RewardItem({
    required this.id,
    required this.name,
    required this.category,
    required this.points,
    this.image = '',
  });

  factory RewardItem.fromJson(Map<String, dynamic> json) {
    return RewardItem(
      id: json['id'] ?? '',
      name: json['name'] ?? '',
      category: json['category'] ?? '',
      points: (json['points'] as num?)?.toInt() ?? 0,
      image: json['image'] ?? '',
    );
  }
}

class LoanEligibility {
  final bool eligible;
  final double maxAmount;
  final double minAmount;
  final int cibilScore;
  final double interestRate;
  final double processingFee;

  LoanEligibility({
    required this.eligible,
    required this.maxAmount,
    required this.minAmount,
    required this.cibilScore,
    required this.interestRate,
    required this.processingFee,
  });

  factory LoanEligibility.fromJson(Map<String, dynamic> json) {
    return LoanEligibility(
      eligible: json['eligible'] ?? false,
      maxAmount: (json['maxAmount'] as num?)?.toDouble() ?? 0,
      minAmount: (json['minAmount'] as num?)?.toDouble() ?? 50000,
      cibilScore: (json['cibilScore'] as num?)?.toInt() ?? 0,
      interestRate: (json['interestRate'] as num?)?.toDouble() ?? 0,
      processingFee: (json['processingFee'] as num?)?.toDouble() ?? 0,
    );
  }
}

class EMIResult {
  final double emi;
  final double totalPayable;
  final double totalInterest;
  final double interestRate;
  final int tenureMonths;
  final double principal;

  EMIResult({
    required this.emi,
    required this.totalPayable,
    required this.totalInterest,
    required this.interestRate,
    required this.tenureMonths,
    required this.principal,
  });

  factory EMIResult.fromJson(Map<String, dynamic> json) {
    return EMIResult(
      emi: (json['emi'] as num?)?.toDouble() ?? 0,
      totalPayable: (json['totalPayable'] as num?)?.toDouble() ?? 0,
      totalInterest: (json['totalInterest'] as num?)?.toDouble() ?? 0,
      interestRate: (json['interestRate'] as num?)?.toDouble() ?? 0,
      tenureMonths: (json['tenureMonths'] as num?)?.toInt() ?? 0,
      principal: (json['principal'] as num?)?.toDouble() ?? 0,
    );
  }
}
