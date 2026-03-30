import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../providers/mcp_widget_provider.dart';
import '../config/theme.dart';

class DisputeFormWidget extends StatefulWidget {
  const DisputeFormWidget({super.key});

  @override
  State<DisputeFormWidget> createState() => _DisputeFormWidgetState();
}

class _DisputeFormWidgetState extends State<DisputeFormWidget> {
  final _formKey = GlobalKey<FormState>();
  final _currencyFormat = NumberFormat.currency(
    locale: 'en_IN',
    symbol: '\u20B9',
    decimalDigits: 2,
  );

  final _descriptionController = TextEditingController();
  String? _selectedTransactionId;
  String? _selectedReason;
  bool _processing = false;
  String? _error;
  Map<String, dynamic>? _disputeResult;

  static const Map<String, int> _resolutionDays = {
    'Not Authorized': 10,
    'Duplicate Charge': 7,
    'Goods Not Received': 15,
    'Amount Incorrect': 7,
    'Other': 21,
  };

  @override
  void initState() {
    super.initState();
    final provider = context.read<McpWidgetProvider>();
    provider.loadTransactions();
  }

  @override
  void dispose() {
    _descriptionController.dispose();
    super.dispose();
  }

  Future<void> _handleSubmitDispute() async {
    if (!_formKey.currentState!.validate()) return;
    if (_selectedTransactionId == null) {
      setState(() => _error = 'Please select a transaction');
      return;
    }
    if (_selectedReason == null) {
      setState(() => _error = 'Please select a dispute reason');
      return;
    }

    setState(() {
      _processing = true;
      _error = null;
    });

    try {
      final provider = context.read<McpWidgetProvider>();
      final transaction = provider.transactions.firstWhere(
        (t) => t.id == _selectedTransactionId,
      );

      final result = await provider.raiseDispute(
        transactionId: _selectedTransactionId!,
        reason: _selectedReason!,
        description: _descriptionController.text,
        amount: transaction.amount.abs(),
      );

      setState(() {
        _disputeResult = result;
        _processing = false;
      });
    } catch (e) {
      setState(() {
        _error = 'Failed to raise dispute: ${e.toString()}';
        _processing = false;
      });
    }
  }

  void _resetForm() {
    setState(() {
      _selectedTransactionId = null;
      _selectedReason = null;
      _descriptionController.clear();
      _disputeResult = null;
      _error = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<McpWidgetProvider>(
      builder: (context, provider, _) {
        if (provider.loading && provider.transactions.isEmpty) {
          return const Center(child: CircularProgressIndicator());
        }

        if (provider.error != null && provider.transactions.isEmpty) {
          return Card(
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.error_outline,
                        color: AppTheme.errorRed, size: 48),
                    const SizedBox(height: 12),
                    Text(
                      'Failed to load transactions',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      provider.error!,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                    const SizedBox(height: 16),
                    ElevatedButton.icon(
                      onPressed: () => provider.loadTransactions(),
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
                // Header
                Row(
                  children: [
                    Icon(Icons.gavel_rounded, color: AppTheme.primaryBlue),
                    const SizedBox(width: 12),
                    Text(
                      'Raise a Dispute',
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

                if (_disputeResult != null)
                  _buildSuccessView()
                else
                  _buildFormView(provider),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildFormView(McpWidgetProvider provider) {
    final recentTransactions = provider.transactions.take(30).toList();
    final selectedTxn = _selectedTransactionId != null
        ? provider.transactions.firstWhere(
            (t) => t.id == _selectedTransactionId,
            orElse: () => provider.transactions.first,
          )
        : null;

    return Form(
      key: _formKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Transaction selector
          Text(
            'Select Transaction',
            style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
          ),
          const SizedBox(height: 8),
          DropdownButtonFormField<String>(
            value: _selectedTransactionId,
            decoration: const InputDecoration(
              hintText: 'Choose a recent transaction',
              prefixIcon: Icon(Icons.receipt_long_outlined),
            ),
            isExpanded: true,
            items: recentTransactions.map((txn) {
              return DropdownMenuItem(
                value: txn.id,
                child: Text(
                  '${txn.date} - ${txn.description} - ${_currencyFormat.format(txn.amount.abs())}',
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 13),
                ),
              );
            }).toList(),
            onChanged: (val) =>
                setState(() => _selectedTransactionId = val),
            validator: (val) =>
                val == null ? 'Please select a transaction' : null,
          ),
          const SizedBox(height: 16),

          // Selected transaction details
          if (selectedTxn != null) ...[
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Theme.of(context)
                    .colorScheme
                    .surface,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: Theme.of(context).dividerColor),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Transaction Details',
                    style: TextStyle(
                      fontWeight: FontWeight.w700,
                      fontSize: 13,
                      color: AppTheme.primaryBlue,
                    ),
                  ),
                  const SizedBox(height: 12),
                  _detailRow('Date', selectedTxn.date),
                  const SizedBox(height: 8),
                  _detailRow('Merchant', selectedTxn.merchant),
                  const SizedBox(height: 8),
                  _detailRow(
                    'Amount',
                    _currencyFormat.format(selectedTxn.amount.abs()),
                  ),
                  const SizedBox(height: 8),
                  _detailRow('Category', selectedTxn.category),
                ],
              ),
            ),
            const SizedBox(height: 24),
          ],

          // Dispute reason
          Text(
            'Dispute Reason',
            style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
          ),
          const SizedBox(height: 8),
          ..._resolutionDays.keys.map((reason) {
            return RadioListTile<String>(
              value: reason,
              groupValue: _selectedReason,
              onChanged: (val) => setState(() => _selectedReason = val),
              title: Text(reason, style: const TextStyle(fontSize: 14)),
              dense: true,
              contentPadding: EdgeInsets.zero,
              activeColor: AppTheme.primaryBlue,
            );
          }),
          const SizedBox(height: 16),

          // Description
          Text(
            'Description',
            style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
          ),
          const SizedBox(height: 8),
          TextFormField(
            controller: _descriptionController,
            maxLines: 4,
            decoration: const InputDecoration(
              hintText: 'Provide details about the dispute...',
              alignLabelWithHint: true,
            ),
            validator: (val) {
              if (val == null || val.trim().isEmpty) {
                return 'Please provide a description';
              }
              return null;
            },
          ),
          const SizedBox(height: 16),

          // Estimated resolution timeline
          if (_selectedReason != null)
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppTheme.infoBlue.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(
                  color: AppTheme.infoBlue.withValues(alpha: 0.2),
                ),
              ),
              child: Row(
                children: [
                  Icon(Icons.schedule, color: AppTheme.infoBlue, size: 20),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Estimated resolution: ${_resolutionDays[_selectedReason]} business days',
                      style: TextStyle(
                        fontSize: 13,
                        color: AppTheme.infoBlue,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          const SizedBox(height: 24),

          // Submit button
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: _processing ? null : _handleSubmitDispute,
              icon: _processing
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Colors.white,
                      ),
                    )
                  : const Icon(Icons.gavel, size: 18),
              label: Text(
                  _processing ? 'Submitting...' : 'Submit Dispute'),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSuccessView() {
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
          'Dispute Raised Successfully',
          style: Theme.of(context).textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.w700,
                color: AppTheme.successGreen,
              ),
        ),
        const SizedBox(height: 24),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              children: [
                _detailRow(
                  'Dispute ID',
                  _disputeResult?['disputeId']?.toString() ?? 'N/A',
                ),
                const Divider(height: 24),
                _detailRow(
                  'Status',
                  _disputeResult?['status']?.toString() ?? 'Under Review',
                ),
                const Divider(height: 24),
                _detailRow(
                  'Expected Resolution',
                  _disputeResult?['expectedResolutionDate']?.toString() ??
                      'N/A',
                ),
                const Divider(height: 24),
                _detailRow('Reason', _selectedReason ?? 'N/A'),
              ],
            ),
          ),
        ),
        const SizedBox(height: 24),
        SizedBox(
          width: double.infinity,
          child: ElevatedButton.icon(
            onPressed: _resetForm,
            icon: const Icon(Icons.replay, size: 18),
            label: const Text('Raise Another Dispute'),
          ),
        ),
      ],
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
