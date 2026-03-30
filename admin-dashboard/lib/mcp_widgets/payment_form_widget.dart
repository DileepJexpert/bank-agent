import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../providers/mcp_widget_provider.dart';
import '../config/theme.dart';

class PaymentFormWidget extends StatefulWidget {
  const PaymentFormWidget({super.key});

  @override
  State<PaymentFormWidget> createState() => _PaymentFormWidgetState();
}

class _PaymentFormWidgetState extends State<PaymentFormWidget> {
  final _formKey = GlobalKey<FormState>();
  final _currencyFormat = NumberFormat.currency(
    locale: 'en_IN',
    symbol: '\u20B9',
    decimalDigits: 2,
  );

  // Step: 'form', 'otp', 'success'
  String _step = 'form';

  // Form fields
  String? _selectedBeneficiary;
  final _accountController = TextEditingController();
  final _ifscController = TextEditingController();
  final _nameController = TextEditingController();
  final _amountController = TextEditingController();
  String _purpose = 'Family';

  // OTP fields
  final List<TextEditingController> _otpControllers =
      List.generate(6, (_) => TextEditingController());
  final List<FocusNode> _otpFocusNodes = List.generate(6, (_) => FocusNode());

  // Transfer result
  Map<String, dynamic>? _transferResult;
  bool _processing = false;
  String? _error;

  // Mock saved beneficiaries
  final List<Map<String, String>> _savedBeneficiaries = [
    {
      'id': 'ben_1',
      'name': 'Rahul Sharma',
      'account': '9876543210',
      'ifsc': 'IDFB0040101',
    },
    {
      'id': 'ben_2',
      'name': 'Priya Patel',
      'account': '1234567890',
      'ifsc': 'SBIN0001234',
    },
    {
      'id': 'ben_3',
      'name': 'Amit Kumar',
      'account': '5678901234',
      'ifsc': 'HDFC0000123',
    },
  ];

  final List<String> _purposes = [
    'Family',
    'Rent',
    'Salary',
    'Business',
    'Other',
  ];

  @override
  void initState() {
    super.initState();
    final provider = context.read<McpWidgetProvider>();
    if (provider.accountSummary == null) {
      provider.loadAccountSummary();
    }
  }

  @override
  void dispose() {
    _accountController.dispose();
    _ifscController.dispose();
    _nameController.dispose();
    _amountController.dispose();
    for (final c in _otpControllers) {
      c.dispose();
    }
    for (final f in _otpFocusNodes) {
      f.dispose();
    }
    super.dispose();
  }

  bool get _isNewBeneficiary => _selectedBeneficiary == 'new';

  String get _beneficiaryName {
    if (_isNewBeneficiary) return _nameController.text;
    final ben = _savedBeneficiaries.firstWhere(
      (b) => b['id'] == _selectedBeneficiary,
      orElse: () => {},
    );
    return ben['name'] ?? '';
  }

  String get _beneficiaryAccount {
    if (_isNewBeneficiary) return _accountController.text;
    final ben = _savedBeneficiaries.firstWhere(
      (b) => b['id'] == _selectedBeneficiary,
      orElse: () => {},
    );
    return ben['account'] ?? '';
  }

  Future<void> _handleConfirmPay() async {
    if (!_formKey.currentState!.validate()) return;
    if (_selectedBeneficiary == null) {
      setState(() => _error = 'Please select a beneficiary');
      return;
    }

    setState(() {
      _processing = true;
      _error = null;
    });

    try {
      final provider = context.read<McpWidgetProvider>();
      final amount = double.parse(_amountController.text.replaceAll(',', ''));
      _transferResult = await provider.initiateTransfer(
        toAccount: _beneficiaryAccount,
        amount: amount,
        purpose: _purpose,
        beneficiaryName: _beneficiaryName,
      );
      setState(() {
        _step = 'otp';
        _processing = false;
      });
    } catch (e) {
      setState(() {
        _error = 'Transfer initiation failed: ${e.toString()}';
        _processing = false;
      });
    }
  }

  Future<void> _handleOtpSubmit() async {
    final otp = _otpControllers.map((c) => c.text).join();
    if (otp.length < 6) {
      setState(() => _error = 'Please enter complete 6-digit OTP');
      return;
    }

    setState(() {
      _processing = true;
      _error = null;
    });

    // Simulate OTP verification delay
    await Future.delayed(const Duration(seconds: 1));

    setState(() {
      _step = 'success';
      _processing = false;
    });
  }

  void _resetForm() {
    setState(() {
      _step = 'form';
      _selectedBeneficiary = null;
      _accountController.clear();
      _ifscController.clear();
      _nameController.clear();
      _amountController.clear();
      _purpose = 'Family';
      _transferResult = null;
      _error = null;
      for (final c in _otpControllers) {
        c.clear();
      }
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
                    const SizedBox(height: 8),
                    Text(
                      provider.error!,
                      style: Theme.of(context).textTheme.bodySmall,
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

        return Card(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(Icons.send_rounded, color: AppTheme.primaryBlue),
                    const SizedBox(width: 12),
                    Text(
                      'Fund Transfer',
                      style: Theme.of(context).textTheme.titleLarge?.copyWith(
                            fontWeight: FontWeight.w700,
                          ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                _buildStepIndicator(),
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
                        Icon(Icons.error_outline, color: AppTheme.errorRed, size: 20),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            _error!,
                            style: TextStyle(color: AppTheme.errorRed, fontSize: 13),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                ],
                if (_step == 'form') _buildFormView(provider),
                if (_step == 'otp') _buildOtpView(),
                if (_step == 'success') _buildSuccessView(),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildStepIndicator() {
    final steps = ['Details', 'OTP', 'Confirmation'];
    final currentIndex = _step == 'form' ? 0 : (_step == 'otp' ? 1 : 2);

    return Row(
      children: List.generate(steps.length, (i) {
        final isActive = i <= currentIndex;
        return Expanded(
          child: Row(
            children: [
              Container(
                width: 28,
                height: 28,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: isActive ? AppTheme.primaryBlue : Colors.grey.shade300,
                ),
                child: Center(
                  child: i < currentIndex
                      ? const Icon(Icons.check, color: Colors.white, size: 16)
                      : Text(
                          '${i + 1}',
                          style: TextStyle(
                            color: isActive ? Colors.white : Colors.grey.shade600,
                            fontWeight: FontWeight.w600,
                            fontSize: 12,
                          ),
                        ),
                ),
              ),
              const SizedBox(width: 6),
              Flexible(
                child: Text(
                  steps[i],
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: isActive ? FontWeight.w600 : FontWeight.normal,
                    color: isActive ? AppTheme.primaryBlue : Colors.grey,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              if (i < steps.length - 1)
                Expanded(
                  child: Container(
                    height: 2,
                    margin: const EdgeInsets.symmetric(horizontal: 8),
                    color: i < currentIndex
                        ? AppTheme.primaryBlue
                        : Colors.grey.shade300,
                  ),
                ),
            ],
          ),
        );
      }),
    );
  }

  Widget _buildFormView(McpWidgetProvider provider) {
    final account = provider.accountSummary;

    return Form(
      key: _formKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Source account display
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppTheme.primaryBlue.withValues(alpha: 0.06),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: AppTheme.primaryBlue.withValues(alpha: 0.2),
              ),
            ),
            child: Row(
              children: [
                Icon(Icons.account_balance, color: AppTheme.primaryBlue),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Source Account',
                        style: TextStyle(
                          fontSize: 12,
                          color: Theme.of(context)
                              .textTheme
                              .bodySmall
                              ?.color
                              ?.withValues(alpha: 0.7),
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        account?.accountNumber ?? 'Loading...',
                        style: const TextStyle(
                          fontWeight: FontWeight.w600,
                          fontSize: 15,
                        ),
                      ),
                    ],
                  ),
                ),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    const Text(
                      'Available Balance',
                      style: TextStyle(fontSize: 11, color: Colors.grey),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      _currencyFormat.format(account?.balance ?? 0),
                      style: TextStyle(
                        fontWeight: FontWeight.w700,
                        fontSize: 15,
                        color: AppTheme.successGreen,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),

          // Beneficiary dropdown
          Text(
            'Beneficiary',
            style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
          ),
          const SizedBox(height: 8),
          DropdownButtonFormField<String>(
            value: _selectedBeneficiary,
            decoration: const InputDecoration(
              hintText: 'Select beneficiary',
              prefixIcon: Icon(Icons.person_outline),
            ),
            items: [
              ..._savedBeneficiaries.map(
                (b) => DropdownMenuItem(
                  value: b['id'],
                  child: Text('${b['name']} - A/c ${b['account']}'),
                ),
              ),
              const DropdownMenuItem(
                value: 'new',
                child: Row(
                  children: [
                    Icon(Icons.add_circle_outline, size: 18),
                    SizedBox(width: 8),
                    Text('Add New Beneficiary'),
                  ],
                ),
              ),
            ],
            onChanged: (val) => setState(() => _selectedBeneficiary = val),
            validator: (val) =>
                val == null ? 'Please select a beneficiary' : null,
          ),
          const SizedBox(height: 16),

          // New beneficiary fields
          if (_isNewBeneficiary) ...[
            TextFormField(
              controller: _nameController,
              decoration: const InputDecoration(
                labelText: 'Beneficiary Name',
                prefixIcon: Icon(Icons.person),
              ),
              validator: (val) =>
                  _isNewBeneficiary && (val == null || val.isEmpty)
                      ? 'Name is required'
                      : null,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _accountController,
              decoration: const InputDecoration(
                labelText: 'Account Number',
                prefixIcon: Icon(Icons.numbers),
              ),
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              validator: (val) =>
                  _isNewBeneficiary && (val == null || val.isEmpty)
                      ? 'Account number is required'
                      : null,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _ifscController,
              decoration: const InputDecoration(
                labelText: 'IFSC Code',
                prefixIcon: Icon(Icons.code),
              ),
              textCapitalization: TextCapitalization.characters,
              validator: (val) =>
                  _isNewBeneficiary && (val == null || val.isEmpty)
                      ? 'IFSC code is required'
                      : null,
            ),
            const SizedBox(height: 16),
          ],

          // Amount field
          Text(
            'Amount',
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
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            inputFormatters: [
              FilteringTextInputFormatter.allow(RegExp(r'[\d,.]')),
            ],
            validator: (val) {
              if (val == null || val.isEmpty) return 'Amount is required';
              final amount = double.tryParse(val.replaceAll(',', ''));
              if (amount == null || amount <= 0) return 'Enter a valid amount';
              if (account != null && amount > account.balance) {
                return 'Insufficient balance';
              }
              return null;
            },
          ),
          const SizedBox(height: 4),
          Text(
            'Available: ${_currencyFormat.format(account?.balance ?? 0)}',
            style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
          ),
          const SizedBox(height: 16),

          // Purpose dropdown
          Text(
            'Purpose',
            style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
          ),
          const SizedBox(height: 8),
          DropdownButtonFormField<String>(
            value: _purpose,
            decoration: const InputDecoration(
              prefixIcon: Icon(Icons.description_outlined),
            ),
            items: _purposes
                .map((p) => DropdownMenuItem(value: p, child: Text(p)))
                .toList(),
            onChanged: (val) {
              if (val != null) setState(() => _purpose = val);
            },
          ),
          const SizedBox(height: 24),

          // Confirm button
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: _processing ? null : _handleConfirmPay,
              icon: _processing
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Colors.white,
                      ),
                    )
                  : const Icon(Icons.lock_outline, size: 18),
              label: Text(_processing ? 'Processing...' : 'Confirm & Pay'),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildOtpView() {
    return Column(
      children: [
        Icon(Icons.phone_android, size: 48, color: AppTheme.primaryBlue),
        const SizedBox(height: 16),
        Text(
          'Enter OTP',
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w700,
              ),
        ),
        const SizedBox(height: 8),
        Text(
          'A 6-digit OTP has been sent to your registered mobile number',
          textAlign: TextAlign.center,
          style: TextStyle(fontSize: 13, color: Colors.grey.shade600),
        ),
        const SizedBox(height: 24),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: List.generate(6, (i) {
            return Container(
              width: 46,
              height: 54,
              margin: const EdgeInsets.symmetric(horizontal: 4),
              child: TextFormField(
                controller: _otpControllers[i],
                focusNode: _otpFocusNodes[i],
                keyboardType: TextInputType.number,
                textAlign: TextAlign.center,
                maxLength: 1,
                style: const TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.w700,
                ),
                decoration: InputDecoration(
                  counterText: '',
                  contentPadding: EdgeInsets.zero,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                onChanged: (val) {
                  if (val.isNotEmpty && i < 5) {
                    _otpFocusNodes[i + 1].requestFocus();
                  } else if (val.isEmpty && i > 0) {
                    _otpFocusNodes[i - 1].requestFocus();
                  }
                },
              ),
            );
          }),
        ),
        const SizedBox(height: 24),
        SizedBox(
          width: double.infinity,
          child: ElevatedButton(
            onPressed: _processing ? null : _handleOtpSubmit,
            child: _processing
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : const Text('Verify & Transfer'),
          ),
        ),
        const SizedBox(height: 12),
        TextButton(
          onPressed: () {},
          child: const Text('Resend OTP'),
        ),
      ],
    );
  }

  Widget _buildSuccessView() {
    final amount = double.tryParse(
          _amountController.text.replaceAll(',', ''),
        ) ??
        0;
    final now = DateFormat('dd MMM yyyy, hh:mm a').format(DateTime.now());

    return Column(
      children: [
        Container(
          width: 72,
          height: 72,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: AppTheme.successGreen.withValues(alpha: 0.12),
          ),
          child: Icon(
            Icons.check_circle,
            color: AppTheme.successGreen,
            size: 48,
          ),
        ),
        const SizedBox(height: 16),
        Text(
          'Transfer Successful!',
          style: Theme.of(context).textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.w700,
                color: AppTheme.successGreen,
              ),
        ),
        const SizedBox(height: 8),
        Text(
          _currencyFormat.format(amount),
          style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                fontWeight: FontWeight.w800,
              ),
        ),
        const SizedBox(height: 24),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              children: [
                _receiptRow(
                  'Reference Number',
                  _transferResult?['referenceId'] ?? 'N/A',
                ),
                const Divider(height: 24),
                _receiptRow('Beneficiary', _beneficiaryName),
                const Divider(height: 24),
                _receiptRow('Account', _beneficiaryAccount),
                const Divider(height: 24),
                _receiptRow('Amount', _currencyFormat.format(amount)),
                const Divider(height: 24),
                _receiptRow('Purpose', _purpose),
                const Divider(height: 24),
                _receiptRow(
                  'Status',
                  _transferResult?['status'] ?? 'Completed',
                ),
                const Divider(height: 24),
                _receiptRow('Date & Time', now),
              ],
            ),
          ),
        ),
        const SizedBox(height: 24),
        Row(
          children: [
            Expanded(
              child: OutlinedButton.icon(
                onPressed: () {},
                icon: const Icon(Icons.download, size: 18),
                label: const Text('Download'),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: ElevatedButton.icon(
                onPressed: _resetForm,
                icon: const Icon(Icons.replay, size: 18),
                label: const Text('New Transfer'),
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _receiptRow(String label, String value) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: TextStyle(fontSize: 13, color: Colors.grey.shade600),
        ),
        Flexible(
          child: Text(
            value,
            style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
            textAlign: TextAlign.right,
          ),
        ),
      ],
    );
  }
}
