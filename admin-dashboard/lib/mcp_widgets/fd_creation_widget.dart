import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../providers/mcp_widget_provider.dart';
import '../config/theme.dart';

class FdCreationWidget extends StatefulWidget {
  const FdCreationWidget({super.key});

  @override
  State<FdCreationWidget> createState() => _FdCreationWidgetState();
}

class _FdCreationWidgetState extends State<FdCreationWidget> {
  final _formKey = GlobalKey<FormState>();
  final _currencyFormat = NumberFormat.currency(
    locale: 'en_IN',
    symbol: '\u20B9',
    decimalDigits: 2,
  );

  final _amountController = TextEditingController();
  int _selectedTenureDays = 365;
  bool _autoRenewal = false;
  bool _seniorCitizen = false;
  bool _processing = false;
  String? _error;

  // Tenure options: label, days, base interest rate
  static const List<Map<String, dynamic>> _tenureOptions = [
    {'label': '7 Days', 'days': 7, 'rate': 4.0},
    {'label': '14 Days', 'days': 14, 'rate': 4.25},
    {'label': '30 Days', 'days': 30, 'rate': 4.5},
    {'label': '90 Days', 'days': 90, 'rate': 5.25},
    {'label': '180 Days', 'days': 180, 'rate': 5.75},
    {'label': '1 Year', 'days': 365, 'rate': 6.5},
    {'label': '2 Years', 'days': 730, 'rate': 7.0},
    {'label': '3 Years', 'days': 1095, 'rate': 7.25},
    {'label': '5 Years', 'days': 1825, 'rate': 7.0},
  ];

  @override
  void initState() {
    super.initState();
    final provider = context.read<McpWidgetProvider>();
    if (provider.accountSummary == null) {
      provider.loadAccountSummary();
    }
    _amountController.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _amountController.dispose();
    super.dispose();
  }

  double get _baseRate {
    final option = _tenureOptions.firstWhere(
      (o) => o['days'] == _selectedTenureDays,
      orElse: () => _tenureOptions[5],
    );
    return (option['rate'] as double);
  }

  double get _effectiveRate => _baseRate + (_seniorCitizen ? 0.5 : 0.0);

  double get _amount {
    final text = _amountController.text.replaceAll(',', '');
    return double.tryParse(text) ?? 0;
  }

  double get _maturityAmount {
    if (_amount <= 0) return 0;
    return _amount * (1 + _effectiveRate / 100 * _selectedTenureDays / 365);
  }

  double get _interestEarned => _maturityAmount - _amount;

  bool get _showTdsNote => _interestEarned > 40000;

  Future<void> _handleCreateFD() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() {
      _processing = true;
      _error = null;
    });

    try {
      final provider = context.read<McpWidgetProvider>();
      final result = await provider.createFD(
        amount: _amount,
        tenureDays: _selectedTenureDays,
        autoRenewal: _autoRenewal,
      );
      if (!mounted) return;
      _showSuccessDialog(result);
    } catch (e) {
      setState(() {
        _error = 'Failed to create FD: ${e.toString()}';
      });
    } finally {
      if (mounted) {
        setState(() => _processing = false);
      }
    }
  }

  void _showSuccessDialog(Map<String, dynamic> result) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        title: Row(
          children: [
            Icon(Icons.check_circle, color: AppTheme.successGreen, size: 28),
            const SizedBox(width: 12),
            const Text('FD Created Successfully'),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _detailRow('FD ID', result['fdId']?.toString() ?? 'N/A'),
            const Divider(height: 20),
            _detailRow('Amount', _currencyFormat.format(_amount)),
            const Divider(height: 20),
            _detailRow('Interest Rate', '${_effectiveRate.toStringAsFixed(2)}% p.a.'),
            const Divider(height: 20),
            _detailRow(
              'Maturity Amount',
              _currencyFormat.format(
                (result['maturityAmount'] as num?)?.toDouble() ?? _maturityAmount,
              ),
            ),
            const Divider(height: 20),
            _detailRow(
              'Tenure',
              _tenureOptions
                  .firstWhere(
                    (o) => o['days'] == _selectedTenureDays,
                    orElse: () => {'label': '$_selectedTenureDays Days'},
                  )['label']
                  .toString(),
            ),
            const Divider(height: 20),
            _detailRow('Auto Renewal', _autoRenewal ? 'Yes' : 'No'),
          ],
        ),
        actions: [
          ElevatedButton(
            onPressed: () {
              Navigator.of(ctx).pop();
              _resetForm();
            },
            child: const Text('Done'),
          ),
        ],
      ),
    );
  }

  void _resetForm() {
    setState(() {
      _amountController.clear();
      _selectedTenureDays = 365;
      _autoRenewal = false;
      _seniorCitizen = false;
      _error = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<McpWidgetProvider>(
      builder: (context, provider, _) {
        if (provider.loading && provider.accountSummary == null) {
          return const Center(child: CircularProgressIndicator());
        }

        if (provider.error != null && provider.accountSummary == null) {
          return Card(
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.error_outline, color: AppTheme.errorRed, size: 48),
                    const SizedBox(height: 12),
                    Text(
                      'Failed to load account details',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 16),
                    ElevatedButton.icon(
                      onPressed: () => provider.loadAccountSummary(),
                      icon: const Icon(Icons.refresh, size: 18),
                      label: const Text('Retry'),
                    ),
                  ],
                ),
              ),
            ),
          );
        }

        final account = provider.accountSummary;

        return Card(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Form(
              key: _formKey,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Header
                  Row(
                    children: [
                      Icon(Icons.savings_rounded, color: AppTheme.primaryBlue),
                      const SizedBox(width: 12),
                      Text(
                        'Create Fixed Deposit',
                        style: Theme.of(context).textTheme.titleLarge?.copyWith(
                              fontWeight: FontWeight.w700,
                            ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 24),

                  if (_error != null) ...[
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppTheme.errorRed.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Row(
                        children: [
                          Icon(Icons.error_outline,
                              color: AppTheme.errorRed, size: 20),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              _error!,
                              style: TextStyle(
                                  color: AppTheme.errorRed, fontSize: 13),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),
                  ],

                  // Amount input
                  Text(
                    'Deposit Amount',
                    style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                  ),
                  const SizedBox(height: 8),
                  TextFormField(
                    controller: _amountController,
                    decoration: InputDecoration(
                      prefixText: '\u20B9 ',
                      prefixStyle: TextStyle(
                        fontWeight: FontWeight.w700,
                        fontSize: 16,
                        color: Theme.of(context).textTheme.bodyLarge?.color,
                      ),
                      hintText: '0.00',
                    ),
                    keyboardType:
                        const TextInputType.numberWithOptions(decimal: true),
                    inputFormatters: [
                      FilteringTextInputFormatter.allow(RegExp(r'[\d,.]')),
                    ],
                    validator: (val) {
                      if (val == null || val.isEmpty) {
                        return 'Amount is required';
                      }
                      final amount =
                          double.tryParse(val.replaceAll(',', ''));
                      if (amount == null || amount <= 0) {
                        return 'Enter a valid amount';
                      }
                      if (amount < 5000) {
                        return 'Minimum FD amount is \u20B95,000';
                      }
                      if (account != null && amount > account.balance) {
                        return 'Insufficient balance';
                      }
                      return null;
                    },
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Available Balance: ${_currencyFormat.format(account?.balance ?? 0)}',
                    style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                  ),
                  const SizedBox(height: 24),

                  // Tenure selector
                  Text(
                    'Select Tenure',
                    style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                  ),
                  const SizedBox(height: 8),
                  ..._tenureOptions.map((option) {
                    final days = option['days'] as int;
                    final rate = (option['rate'] as double) +
                        (_seniorCitizen ? 0.5 : 0.0);
                    return RadioListTile<int>(
                      value: days,
                      groupValue: _selectedTenureDays,
                      onChanged: (val) {
                        if (val != null) {
                          setState(() => _selectedTenureDays = val);
                        }
                      },
                      title: Text(
                        option['label'] as String,
                        style: const TextStyle(fontSize: 14),
                      ),
                      subtitle: null,
                      secondary: Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 10, vertical: 4),
                        decoration: BoxDecoration(
                          color: days == _selectedTenureDays
                              ? AppTheme.primaryBlue.withValues(alpha: 0.1)
                              : Colors.grey.withValues(alpha: 0.1),
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: Text(
                          '${rate.toStringAsFixed(2)}% p.a.',
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: days == _selectedTenureDays
                                ? AppTheme.primaryBlue
                                : Colors.grey.shade700,
                          ),
                        ),
                      ),
                      dense: true,
                      contentPadding: EdgeInsets.zero,
                      activeColor: AppTheme.primaryBlue,
                    );
                  }),
                  const SizedBox(height: 16),

                  // Auto-renewal toggle
                  SwitchListTile(
                    value: _autoRenewal,
                    onChanged: (val) => setState(() => _autoRenewal = val),
                    title: const Text(
                      'Auto Renewal',
                      style:
                          TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                    ),
                    subtitle: const Text(
                      'Automatically renew FD on maturity',
                      style: TextStyle(fontSize: 12),
                    ),
                    contentPadding: EdgeInsets.zero,
                    activeColor: AppTheme.primaryBlue,
                  ),
                  const Divider(height: 8),

                  // Senior citizen toggle
                  SwitchListTile(
                    value: _seniorCitizen,
                    onChanged: (val) => setState(() => _seniorCitizen = val),
                    title: const Text(
                      'Senior Citizen',
                      style:
                          TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                    ),
                    subtitle: const Text(
                      'Additional 0.50% interest rate benefit',
                      style: TextStyle(fontSize: 12),
                    ),
                    contentPadding: EdgeInsets.zero,
                    activeColor: AppTheme.primaryBlue,
                  ),
                  const SizedBox(height: 24),

                  // Maturity calculator
                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: AppTheme.primaryBlue.withValues(alpha: 0.06),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(
                        color: AppTheme.primaryBlue.withValues(alpha: 0.2),
                      ),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Maturity Calculator',
                          style: TextStyle(
                            fontWeight: FontWeight.w700,
                            fontSize: 14,
                            color: AppTheme.primaryBlue,
                          ),
                        ),
                        const SizedBox(height: 16),
                        _detailRow(
                          'Deposit Amount',
                          _amount > 0
                              ? _currencyFormat.format(_amount)
                              : '--',
                        ),
                        const SizedBox(height: 8),
                        _detailRow(
                          'Interest Rate',
                          '${_effectiveRate.toStringAsFixed(2)}% p.a.',
                        ),
                        const SizedBox(height: 8),
                        _detailRow(
                          'Interest Earned',
                          _amount > 0
                              ? _currencyFormat.format(_interestEarned)
                              : '--',
                        ),
                        const Divider(height: 20),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            const Text(
                              'Maturity Amount',
                              style: TextStyle(
                                fontWeight: FontWeight.w700,
                                fontSize: 15,
                              ),
                            ),
                            Text(
                              _amount > 0
                                  ? _currencyFormat.format(_maturityAmount)
                                  : '--',
                              style: TextStyle(
                                fontWeight: FontWeight.w800,
                                fontSize: 16,
                                color: AppTheme.successGreen,
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),

                  // TDS note
                  if (_showTdsNote)
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppTheme.warningAmber.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(
                          color: AppTheme.warningAmber.withValues(alpha: 0.3),
                        ),
                      ),
                      child: Row(
                        children: [
                          Icon(Icons.info_outline,
                              color: AppTheme.warningAmber, size: 20),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              'TDS of 10% applicable on interest above \u20B940,000',
                              style: TextStyle(
                                fontSize: 12,
                                color: AppTheme.warningAmber,
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  const SizedBox(height: 24),

                  // Create FD button
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton.icon(
                      onPressed: _processing ? null : _handleCreateFD,
                      icon: _processing
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                          : const Icon(Icons.savings, size: 18),
                      label:
                          Text(_processing ? 'Creating FD...' : 'Create FD'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _detailRow(String label, String value) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: TextStyle(fontSize: 13, color: Colors.grey.shade600),
        ),
        Text(
          value,
          style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
        ),
      ],
    );
  }
}
