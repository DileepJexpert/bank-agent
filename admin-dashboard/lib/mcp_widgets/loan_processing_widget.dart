import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../providers/mcp_widget_provider.dart';
import '../config/theme.dart';

class LoanProcessingWidget extends StatefulWidget {
  const LoanProcessingWidget({super.key});

  @override
  State<LoanProcessingWidget> createState() => _LoanProcessingWidgetState();
}

class _LoanProcessingWidgetState extends State<LoanProcessingWidget> {
  final _currencyFormat = NumberFormat.currency(
    locale: 'en_IN',
    symbol: '\u20B9',
    decimalDigits: 2,
  );

  @override
  void initState() {
    super.initState();
    final provider = context.read<McpWidgetProvider>();
    provider.loadLoanProcessing();
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<McpWidgetProvider>(
      builder: (context, provider, _) {
        if (provider.loading && provider.loanProcessing == null) {
          return const Card(
            child: Padding(
              padding: EdgeInsets.all(48),
              child: Center(child: CircularProgressIndicator()),
            ),
          );
        }

        if (provider.error != null && provider.loanProcessing == null) {
          return Card(
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.error_outline,
                        color: AppTheme.errorRed, size: 48),
                    const SizedBox(height: 12),
                    Text('Failed to load loan processing data',
                        style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 8),
                    Text(provider.error!,
                        style: Theme.of(context).textTheme.bodySmall),
                    const SizedBox(height: 16),
                    ElevatedButton.icon(
                      onPressed: () => provider.loadLoanProcessing(),
                      icon: const Icon(Icons.refresh, size: 18),
                      label: const Text('Retry'),
                    ),
                  ],
                ),
              ),
            ),
          );
        }

        final data = provider.loanProcessing;
        if (data == null) return const SizedBox.shrink();

        return _buildContent(context, data);
      },
    );
  }

  Widget _buildContent(BuildContext context, Map<String, dynamic> data) {
    final customer =
        data['customer'] as Map<String, dynamic>? ?? {};
    final eligibility =
        data['eligibility'] as Map<String, dynamic>? ?? {};
    final documents =
        (data['documents'] as List?)?.cast<Map<String, dynamic>>() ?? [];
    final underwritingNotes = data['underwritingNotes'] as String? ?? '';

    final allDocsVerified = documents.isNotEmpty &&
        documents.every(
            (d) => (d['status'] ?? '').toString().toUpperCase() == 'VERIFIED');

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildHeader(context),
          const SizedBox(height: 16),
          _buildEligibilitySummary(context, customer, eligibility),
          const SizedBox(height: 16),
          _buildDocumentsChecklist(context, documents),
          const SizedBox(height: 16),
          if (underwritingNotes.isNotEmpty) ...[
            _buildUnderwritingNotes(context, underwritingNotes),
            const SizedBox(height: 16),
          ],
          _buildActionButtons(context, allDocsVerified),
        ],
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      children: [
        Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: AppTheme.primaryBlue.withValues(alpha: 0.1),
            borderRadius: BorderRadius.circular(12),
          ),
          child: const Icon(Icons.assignment,
              color: AppTheme.primaryBlue, size: 24),
        ),
        const SizedBox(width: 12),
        Text(
          'Loan Processing',
          style: theme.textTheme.titleLarge?.copyWith(
            fontWeight: FontWeight.w800,
          ),
        ),
      ],
    );
  }

  // --- Eligibility Summary ---

  Widget _buildEligibilitySummary(BuildContext context,
      Map<String, dynamic> customer, Map<String, dynamic> eligibility) {
    final theme = Theme.of(context);
    final name = customer['name'] ?? '-';
    final customerId = customer['customerId'] ?? '-';
    final cibilScore = (customer['cibilScore'] ?? 0) as num;
    final income = (customer['income'] ?? 0).toDouble();
    final employer = customer['employer'] ?? '-';
    final employmentType =
        (customer['employmentType'] ?? '').toString().toUpperCase();
    final existingEMI = (customer['existingEMI'] ?? 0).toDouble();

    final eligible = eligibility['eligible'] == true;
    final maxAmount = (eligibility['maxAmount'] ?? 0).toDouble();
    final interestRate = (eligibility['interestRate'] ?? 0).toDouble();
    final foir = (eligibility['foir'] ?? 0).toDouble();
    final recommendation =
        (eligibility['recommendation'] ?? '').toString().toUpperCase();

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Customer header
            Row(
              children: [
                CircleAvatar(
                  radius: 22,
                  backgroundColor:
                      AppTheme.primaryBlue.withValues(alpha: 0.1),
                  child: Text(
                    name.isNotEmpty ? name[0].toUpperCase() : '?',
                    style: theme.textTheme.titleMedium?.copyWith(
                      color: AppTheme.primaryBlue,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(name,
                          style: theme.textTheme.titleMedium
                              ?.copyWith(fontWeight: FontWeight.w700)),
                      Text('ID: $customerId',
                          style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.onSurface
                                  .withValues(alpha: 0.6))),
                    ],
                  ),
                ),
                _buildRecommendationBadge(context, recommendation),
              ],
            ),
            const Divider(height: 28),

            // CIBIL Score + Key metrics
            LayoutBuilder(
              builder: (context, constraints) {
                final isWide = constraints.maxWidth > 550;
                if (isWide) {
                  return Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _buildCibilGauge(context, cibilScore),
                      const SizedBox(width: 24),
                      Expanded(
                          child: _buildMetricsGrid(
                              context,
                              income,
                              employer,
                              employmentType,
                              existingEMI,
                              foir,
                              maxAmount,
                              interestRate,
                              eligible)),
                    ],
                  );
                }
                return Column(
                  children: [
                    _buildCibilGauge(context, cibilScore),
                    const SizedBox(height: 16),
                    _buildMetricsGrid(context, income, employer,
                        employmentType, existingEMI, foir, maxAmount,
                        interestRate, eligible),
                  ],
                );
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCibilGauge(BuildContext context, num cibilScore) {
    final theme = Theme.of(context);
    final score = cibilScore.toDouble().clamp(300.0, 900.0);
    final progress = (score - 300) / 600; // 300-900 range

    Color gaugeColor;
    if (score < 600) {
      gaugeColor = AppTheme.errorRed;
    } else if (score <= 750) {
      gaugeColor = AppTheme.warningAmber;
    } else {
      gaugeColor = AppTheme.successGreen;
    }

    return Column(
      children: [
        SizedBox(
          width: 100,
          height: 100,
          child: Stack(
            alignment: Alignment.center,
            children: [
              SizedBox(
                width: 100,
                height: 100,
                child: CircularProgressIndicator(
                  value: progress,
                  strokeWidth: 8,
                  backgroundColor: gaugeColor.withValues(alpha: 0.15),
                  valueColor: AlwaysStoppedAnimation(gaugeColor),
                ),
              ),
              Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    cibilScore.toInt().toString(),
                    style: theme.textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.w900,
                      color: gaugeColor,
                    ),
                  ),
                  Text(
                    'CIBIL',
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: theme.colorScheme.onSurface
                          .withValues(alpha: 0.5),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 6),
        Text(
          score < 600
              ? 'Poor'
              : score <= 750
                  ? 'Fair'
                  : 'Excellent',
          style: theme.textTheme.labelMedium?.copyWith(
            color: gaugeColor,
            fontWeight: FontWeight.w700,
          ),
        ),
      ],
    );
  }

  Widget _buildMetricsGrid(
    BuildContext context,
    double income,
    String employer,
    String employmentType,
    double existingEMI,
    double foir,
    double maxAmount,
    double interestRate,
    bool eligible,
  ) {
    final theme = Theme.of(context);

    Color empBadgeColor;
    switch (employmentType) {
      case 'SALARIED':
        empBadgeColor = AppTheme.primaryBlue;
        break;
      case 'SELF-EMPLOYED':
      case 'SELF_EMPLOYED':
        empBadgeColor = AppTheme.warningAmber;
        break;
      default:
        empBadgeColor = Colors.grey;
    }

    return Wrap(
      spacing: 20,
      runSpacing: 14,
      children: [
        _buildMetricTile(
          context,
          'Monthly Income',
          _currencyFormat.format(income),
          Icons.currency_rupee,
        ),
        _buildMetricTile(
          context,
          'Employer',
          employer,
          Icons.business,
        ),
        _buildMetricWidget(
          context,
          'Employment Type',
          Container(
            padding:
                const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
            decoration: BoxDecoration(
              color: empBadgeColor.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(12),
              border:
                  Border.all(color: empBadgeColor.withValues(alpha: 0.3)),
            ),
            child: Text(
              employmentType,
              style: theme.textTheme.labelSmall?.copyWith(
                color: empBadgeColor,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ),
        _buildMetricTile(
          context,
          'Existing EMI',
          _currencyFormat.format(existingEMI),
          Icons.payments,
        ),
        _buildMetricTile(
          context,
          'FOIR',
          '${foir.toStringAsFixed(1)}%',
          Icons.pie_chart_outline,
        ),
        _buildMetricTile(
          context,
          'Max Eligible Amount',
          _currencyFormat.format(maxAmount),
          Icons.account_balance_wallet,
        ),
        _buildMetricTile(
          context,
          'Interest Rate',
          '${interestRate.toStringAsFixed(2)}% p.a.',
          Icons.trending_up,
        ),
      ],
    );
  }

  Widget _buildMetricTile(
      BuildContext context, String label, String value, IconData icon) {
    final theme = Theme.of(context);
    return SizedBox(
      width: 160,
      child: Row(
        children: [
          Icon(icon, size: 16, color: AppTheme.primaryBlue.withValues(alpha: 0.7)),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label,
                    style: theme.textTheme.labelSmall?.copyWith(
                        color: theme.colorScheme.onSurface
                            .withValues(alpha: 0.5))),
                Text(value,
                    style: theme.textTheme.bodySmall
                        ?.copyWith(fontWeight: FontWeight.w600),
                    overflow: TextOverflow.ellipsis),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMetricWidget(
      BuildContext context, String label, Widget child) {
    final theme = Theme.of(context);
    return SizedBox(
      width: 160,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label,
              style: theme.textTheme.labelSmall?.copyWith(
                  color: theme.colorScheme.onSurface
                      .withValues(alpha: 0.5))),
          const SizedBox(height: 4),
          child,
        ],
      ),
    );
  }

  Widget _buildRecommendationBadge(BuildContext context, String recommendation) {
    Color color;
    IconData icon;
    switch (recommendation) {
      case 'APPROVE':
        color = AppTheme.successGreen;
        icon = Icons.check_circle;
        break;
      case 'REJECT':
        color = AppTheme.errorRed;
        icon = Icons.cancel;
        break;
      case 'ESCALATE':
        color = AppTheme.warningAmber;
        icon = Icons.escalator_warning;
        break;
      default:
        color = Colors.grey;
        icon = Icons.help_outline;
    }
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: color, size: 16),
          const SizedBox(width: 6),
          Text(
            recommendation,
            style: Theme.of(context).textTheme.labelMedium?.copyWith(
                  color: color,
                  fontWeight: FontWeight.w700,
                ),
          ),
        ],
      ),
    );
  }

  // --- Documents Checklist ---

  Widget _buildDocumentsChecklist(
      BuildContext context, List<Map<String, dynamic>> documents) {
    final theme = Theme.of(context);
    final pendingCount = documents
        .where(
            (d) => (d['status'] ?? '').toString().toUpperCase() != 'VERIFIED')
        .length;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.folder_open,
                    color: AppTheme.primaryBlue, size: 22),
                const SizedBox(width: 8),
                Text(
                  'Required Documents',
                  style: theme.textTheme.titleMedium
                      ?.copyWith(fontWeight: FontWeight.w700),
                ),
                const Spacer(),
                if (pendingCount > 0)
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: AppTheme.errorRed.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      '$pendingCount pending',
                      style: theme.textTheme.labelSmall?.copyWith(
                        color: AppTheme.errorRed,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  )
                else
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: AppTheme.successGreen.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      'All verified',
                      style: theme.textTheme.labelSmall?.copyWith(
                        color: AppTheme.successGreen,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
              ],
            ),
            const Divider(height: 20),
            ...documents.map((doc) {
              final status =
                  (doc['status'] ?? '').toString().toUpperCase();
              final isVerified = status == 'VERIFIED';
              final uploadedAt = doc['uploadedAt'];

              return ListTile(
                contentPadding: EdgeInsets.zero,
                leading: Icon(
                  isVerified ? Icons.check_circle : Icons.cancel,
                  color: isVerified
                      ? AppTheme.successGreen
                      : AppTheme.errorRed,
                  size: 24,
                ),
                title: Text(
                  doc['name'] ?? 'Unknown Document',
                  style: theme.textTheme.bodyMedium
                      ?.copyWith(fontWeight: FontWeight.w600),
                ),
                subtitle: Text(
                  uploadedAt != null
                      ? 'Uploaded: $uploadedAt'
                      : 'Not uploaded',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurface
                        .withValues(alpha: 0.5),
                  ),
                ),
                trailing: Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 8, vertical: 2),
                  decoration: BoxDecoration(
                    color: (isVerified
                            ? AppTheme.successGreen
                            : AppTheme.warningAmber)
                        .withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    status,
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: isVerified
                          ? AppTheme.successGreen
                          : AppTheme.warningAmber,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              );
            }),
          ],
        ),
      ),
    );
  }

  // --- Underwriting Notes ---

  Widget _buildUnderwritingNotes(BuildContext context, String notes) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.auto_awesome,
                    color: AppTheme.primaryBlue, size: 22),
                const SizedBox(width: 8),
                Text(
                  'Underwriting Notes',
                  style: theme.textTheme.titleMedium
                      ?.copyWith(fontWeight: FontWeight.w700),
                ),
                const SizedBox(width: 8),
                Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 8, vertical: 2),
                  decoration: BoxDecoration(
                    color: AppTheme.primaryBlue.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    'AI Generated',
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: AppTheme.primaryBlue,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ),
            const Divider(height: 20),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: theme.colorScheme.surface,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: theme.dividerColor),
              ),
              child: SelectableText(
                notes,
                style: theme.textTheme.bodyMedium?.copyWith(
                  height: 1.5,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // --- Action Buttons ---

  Widget _buildActionButtons(BuildContext context, bool allDocsVerified) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Actions',
              style: Theme.of(context)
                  .textTheme
                  .titleMedium
                  ?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 4),
            if (!allDocsVerified)
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Row(
                  children: [
                    const Icon(Icons.info_outline,
                        color: AppTheme.warningAmber, size: 16),
                    const SizedBox(width: 6),
                    Text(
                      'Approve is disabled until all documents are verified.',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: AppTheme.warningAmber,
                          ),
                    ),
                  ],
                ),
              ),
            const Divider(height: 8),
            const SizedBox(height: 12),
            Wrap(
              spacing: 12,
              runSpacing: 12,
              children: [
                ElevatedButton.icon(
                  onPressed: allDocsVerified
                      ? () => _showActionDialog(context, 'Approve')
                      : null,
                  icon: const Icon(Icons.check_circle, size: 18),
                  label: const Text('Approve'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTheme.successGreen,
                    foregroundColor: Colors.white,
                    disabledBackgroundColor:
                        AppTheme.successGreen.withValues(alpha: 0.3),
                    disabledForegroundColor: Colors.white54,
                    padding: const EdgeInsets.symmetric(
                        horizontal: 24, vertical: 14),
                  ),
                ),
                ElevatedButton.icon(
                  onPressed: () => _showActionDialog(context, 'Reject'),
                  icon: const Icon(Icons.cancel, size: 18),
                  label: const Text('Reject'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTheme.errorRed,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(
                        horizontal: 24, vertical: 14),
                  ),
                ),
                ElevatedButton.icon(
                  onPressed: () => _showActionDialog(context, 'Escalate'),
                  icon: const Icon(Icons.escalator_warning, size: 18),
                  label: const Text('Escalate'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTheme.warningAmber,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(
                        horizontal: 24, vertical: 14),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  void _showActionDialog(BuildContext context, String action) {
    final commentController = TextEditingController();
    final theme = Theme.of(context);

    Color actionColor;
    switch (action) {
      case 'Approve':
        actionColor = AppTheme.successGreen;
        break;
      case 'Reject':
        actionColor = AppTheme.errorRed;
        break;
      case 'Escalate':
        actionColor = AppTheme.warningAmber;
        break;
      default:
        actionColor = AppTheme.primaryBlue;
    }

    showDialog(
      context: context,
      builder: (dialogContext) {
        return AlertDialog(
          title: Row(
            children: [
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: actionColor.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(
                  action == 'Approve'
                      ? Icons.check_circle
                      : action == 'Reject'
                          ? Icons.cancel
                          : Icons.escalator_warning,
                  color: actionColor,
                  size: 20,
                ),
              ),
              const SizedBox(width: 10),
              Text('$action Loan Application'),
            ],
          ),
          content: SizedBox(
            width: 400,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Please provide mandatory comments for this action:',
                  style: theme.textTheme.bodyMedium,
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: commentController,
                  maxLines: 4,
                  decoration: InputDecoration(
                    hintText: 'Enter your comments here...',
                    border: const OutlineInputBorder(),
                    filled: true,
                    fillColor: theme.colorScheme.surface,
                  ),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: () {
                if (commentController.text.trim().isEmpty) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('Comments are mandatory.'),
                      backgroundColor: AppTheme.errorRed,
                    ),
                  );
                  return;
                }
                Navigator.of(dialogContext).pop();
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(
                        'Loan application ${action.toLowerCase()}d successfully.'),
                    backgroundColor: actionColor,
                  ),
                );
              },
              style: ElevatedButton.styleFrom(
                backgroundColor: actionColor,
                foregroundColor: Colors.white,
              ),
              child: Text(action),
            ),
          ],
        );
      },
    );
  }
}
