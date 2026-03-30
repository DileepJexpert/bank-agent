import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../providers/mcp_widget_provider.dart';
import '../models/mcp_widget_models.dart';
import '../config/theme.dart';

class EMICalculatorWidget extends StatefulWidget {
  const EMICalculatorWidget({super.key});

  @override
  State<EMICalculatorWidget> createState() => _EMICalculatorWidgetState();
}

class _EMICalculatorWidgetState extends State<EMICalculatorWidget> {
  double _loanAmount = 1000000;
  int _selectedTenure = 24;
  double _interestRate = 10.5;
  double _maxAmount = 5000000;
  double _processingFee = 1.0;
  final _currencyFormat = NumberFormat.currency(locale: 'en_IN', symbol: '\u20B9', decimalDigits: 0);
  final _tenureOptions = [6, 12, 24, 36, 48, 60];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      final provider = context.read<McpWidgetProvider>();
      await provider.loadLoanEligibility();
      if (provider.loanEligibility != null) {
        setState(() {
          _interestRate = provider.loanEligibility!.interestRate;
          _maxAmount = provider.loanEligibility!.maxAmount;
          _processingFee = provider.loanEligibility!.processingFee;
        });
      }
    });
  }

  EMIResult _calculate(double principal, int tenure) {
    return context.read<McpWidgetProvider>().calculateEMI(principal, tenure, _interestRate);
  }

  @override
  Widget build(BuildContext context) {
    final result = _calculate(_loanAmount, _selectedTenure);

    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Eligibility Badge
          Card(
            color: AppTheme.successGreen.withValues(alpha: 0.1),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  const Icon(Icons.check_circle, color: AppTheme.successGreen),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('You are eligible for a loan', style: TextStyle(fontWeight: FontWeight.w600, color: AppTheme.successGreen)),
                        Text('Max amount: ${_currencyFormat.format(_maxAmount)} at ${_interestRate}% p.a.',
                            style: TextStyle(fontSize: 13, color: Theme.of(context).textTheme.bodySmall?.color)),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),

          // Loan Amount Slider
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text('Loan Amount', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 16)),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                        decoration: BoxDecoration(
                          color: AppTheme.primaryBlue.withValues(alpha: 0.1),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Text(_currencyFormat.format(_loanAmount),
                            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppTheme.primaryBlue)),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  Slider(
                    value: _loanAmount,
                    min: 50000,
                    max: _maxAmount,
                    divisions: ((_maxAmount - 50000) / 50000).round(),
                    activeColor: AppTheme.primaryBlue,
                    label: _currencyFormat.format(_loanAmount),
                    onChanged: (v) => setState(() => _loanAmount = v),
                  ),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text('\u20B950K', style: TextStyle(fontSize: 12, color: Theme.of(context).textTheme.bodySmall?.color)),
                      Text(_currencyFormat.format(_maxAmount), style: TextStyle(fontSize: 12, color: Theme.of(context).textTheme.bodySmall?.color)),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // Tenure Selector
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Tenure (Months)', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 16)),
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: _tenureOptions.map((t) {
                      final selected = _selectedTenure == t;
                      return ChoiceChip(
                        label: Text('$t mo'),
                        selected: selected,
                        selectedColor: AppTheme.primaryBlue,
                        labelStyle: TextStyle(
                          color: selected ? Colors.white : null,
                          fontWeight: selected ? FontWeight.w600 : FontWeight.w400,
                        ),
                        onSelected: (_) => setState(() => _selectedTenure = t),
                      );
                    }).toList(),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // EMI Result Display
          Card(
            color: AppTheme.primaryBlue.withValues(alpha: 0.05),
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                children: [
                  Text('Monthly EMI', style: TextStyle(fontSize: 14, color: Theme.of(context).textTheme.bodySmall?.color)),
                  const SizedBox(height: 8),
                  Text(
                    NumberFormat.currency(locale: 'en_IN', symbol: '\u20B9', decimalDigits: 0).format(result.emi),
                    style: const TextStyle(fontSize: 36, fontWeight: FontWeight.bold, color: AppTheme.primaryBlue),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      _buildEmiDetail('Interest Rate', '${_interestRate}% p.a.'),
                      _buildEmiDetail('Total Interest', _currencyFormat.format(result.totalInterest)),
                      _buildEmiDetail('Total Payable', _currencyFormat.format(result.totalPayable)),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Text('Processing fee: ${_processingFee}% of loan amount',
                      style: TextStyle(fontSize: 12, color: Theme.of(context).textTheme.bodySmall?.color)),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),

          // Comparison Table
          const Text('Tenure Comparison', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 16)),
          const SizedBox(height: 12),
          Card(
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: DataTable(
                columns: const [
                  DataColumn(label: Text('Tenure', style: TextStyle(fontWeight: FontWeight.w600))),
                  DataColumn(label: Text('EMI', style: TextStyle(fontWeight: FontWeight.w600))),
                  DataColumn(label: Text('Total Interest', style: TextStyle(fontWeight: FontWeight.w600))),
                  DataColumn(label: Text('Total Payable', style: TextStyle(fontWeight: FontWeight.w600))),
                ],
                rows: [
                  for (final t in [12, 24, 36])
                    _buildComparisonRow(t),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),

          // Apply Now
          Center(
            child: SizedBox(
              width: 300,
              height: 48,
              child: ElevatedButton.icon(
                onPressed: () {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Loan application initiated'), backgroundColor: AppTheme.successGreen),
                  );
                },
                icon: const Icon(Icons.send),
                label: const Text('Apply Now', style: TextStyle(fontSize: 16)),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildEmiDetail(String label, String value) {
    return Column(
      children: [
        Text(label, style: TextStyle(fontSize: 12, color: Theme.of(context).textTheme.bodySmall?.color)),
        const SizedBox(height: 4),
        Text(value, style: const TextStyle(fontWeight: FontWeight.w600)),
      ],
    );
  }

  DataRow _buildComparisonRow(int tenure) {
    final r = _calculate(_loanAmount, tenure);
    final isCurrent = tenure == _selectedTenure;
    final style = isCurrent ? const TextStyle(fontWeight: FontWeight.bold, color: AppTheme.primaryBlue) : null;
    return DataRow(
      selected: isCurrent,
      cells: [
        DataCell(Text('$tenure months', style: style)),
        DataCell(Text(_currencyFormat.format(r.emi), style: style)),
        DataCell(Text(_currencyFormat.format(r.totalInterest), style: style)),
        DataCell(Text(_currencyFormat.format(r.totalPayable), style: style)),
      ],
    );
  }
}
