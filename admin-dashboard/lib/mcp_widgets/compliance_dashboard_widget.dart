import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:fl_chart/fl_chart.dart';
import 'package:intl/intl.dart';
import '../providers/mcp_widget_provider.dart';
import '../config/theme.dart';

class ComplianceDashboardWidget extends StatefulWidget {
  const ComplianceDashboardWidget({super.key});

  @override
  State<ComplianceDashboardWidget> createState() =>
      _ComplianceDashboardWidgetState();
}

class _ComplianceDashboardWidgetState extends State<ComplianceDashboardWidget> {
  final _currencyFormat = NumberFormat.currency(
    locale: 'en_IN',
    symbol: '\u20B9',
    decimalDigits: 2,
  );

  int? _expandedFlaggedRow;

  @override
  void initState() {
    super.initState();
    final provider = context.read<McpWidgetProvider>();
    provider.loadComplianceDashboard();
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<McpWidgetProvider>(
      builder: (context, provider, _) {
        if (provider.loading && provider.compliance == null) {
          return const Card(
            child: Padding(
              padding: EdgeInsets.all(48),
              child: Center(child: CircularProgressIndicator()),
            ),
          );
        }

        if (provider.error != null && provider.compliance == null) {
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
                    Text('Failed to load compliance data',
                        style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 8),
                    Text(provider.error!,
                        style: Theme.of(context).textTheme.bodySmall),
                    const SizedBox(height: 16),
                    ElevatedButton.icon(
                      onPressed: () => provider.loadComplianceDashboard(),
                      icon: const Icon(Icons.refresh, size: 18),
                      label: const Text('Retry'),
                    ),
                  ],
                ),
              ),
            ),
          );
        }

        final data = provider.compliance;
        if (data == null) return const SizedBox.shrink();

        return _buildContent(context, data);
      },
    );
  }

  Widget _buildContent(BuildContext context, Map<String, dynamic> data) {
    final summary =
        data['summary'] as Map<String, dynamic>? ?? {};
    final violationTrend =
        (data['violationTrend'] as List?)?.cast<Map<String, dynamic>>() ?? [];
    final flaggedTransactions =
        (data['flaggedTransactions'] as List?)?.cast<Map<String, dynamic>>() ??
            [];
    final regulatoryReturns =
        (data['regulatoryReturns'] as List?)?.cast<Map<String, dynamic>>() ??
            [];

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildHeader(context),
          const SizedBox(height: 16),
          _buildSummaryKPIs(context, summary),
          const SizedBox(height: 16),
          if (violationTrend.isNotEmpty) ...[
            _buildViolationTrendChart(context, violationTrend),
            const SizedBox(height: 16),
          ],
          if (flaggedTransactions.isNotEmpty) ...[
            _buildFlaggedTransactionsTable(context, flaggedTransactions),
            const SizedBox(height: 16),
          ],
          if (regulatoryReturns.isNotEmpty) ...[
            _buildRegulatoryReturns(context, regulatoryReturns),
            const SizedBox(height: 16),
          ],
          _buildKycPendingSection(context, summary),
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
          child: const Icon(Icons.verified_user,
              color: AppTheme.primaryBlue, size: 24),
        ),
        const SizedBox(width: 12),
        Text(
          'Compliance Dashboard',
          style: theme.textTheme.titleLarge?.copyWith(
            fontWeight: FontWeight.w800,
          ),
        ),
      ],
    );
  }

  // --- Summary KPI Cards ---

  Widget _buildSummaryKPIs(
      BuildContext context, Map<String, dynamic> summary) {
    final totalViolations = summary['totalViolations'] ?? 0;
    final criticalViolations = summary['criticalViolations'] ?? 0;
    final openItems = summary['openItems'] ?? 0;
    final resolvedToday = summary['resolvedToday'] ?? 0;
    final kycPending = summary['kycPending'] ?? 0;
    final kycOverdue = summary['kycOverdue'] ?? 0;

    return Wrap(
      spacing: 12,
      runSpacing: 12,
      children: [
        _buildKpiCard(
          context,
          'Total Violations',
          totalViolations.toString(),
          Icons.warning_amber,
          AppTheme.warningAmber,
        ),
        _buildKpiCard(
          context,
          'Critical',
          criticalViolations.toString(),
          Icons.dangerous,
          AppTheme.errorRed,
        ),
        _buildKpiCard(
          context,
          'Open Items',
          openItems.toString(),
          Icons.pending_actions,
          AppTheme.primaryBlue,
        ),
        _buildKpiCard(
          context,
          'Resolved Today',
          resolvedToday.toString(),
          Icons.check_circle_outline,
          AppTheme.successGreen,
        ),
        _buildKpiCard(
          context,
          'KYC Pending',
          kycPending.toString(),
          Icons.person_search,
          AppTheme.warningAmber,
        ),
        _buildKpiCard(
          context,
          'KYC Overdue',
          kycOverdue.toString(),
          Icons.person_off,
          (kycOverdue as num) > 0 ? AppTheme.errorRed : Colors.grey,
        ),
      ],
    );
  }

  Widget _buildKpiCard(BuildContext context, String label, String value,
      IconData icon, Color color) {
    final theme = Theme.of(context);
    return SizedBox(
      width: 160,
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(6),
                    decoration: BoxDecoration(
                      color: color.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Icon(icon, color: color, size: 18),
                  ),
                  const Spacer(),
                ],
              ),
              const SizedBox(height: 12),
              Text(
                value,
                style: theme.textTheme.headlineMedium?.copyWith(
                  fontWeight: FontWeight.w900,
                  color: color,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                label,
                style: theme.textTheme.labelSmall?.copyWith(
                  color: theme.colorScheme.onSurface
                      .withValues(alpha: 0.6),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // --- Violation Trend Chart ---

  Widget _buildViolationTrendChart(
      BuildContext context, List<Map<String, dynamic>> trend) {
    final theme = Theme.of(context);
    final maxCount = trend
        .map((t) => (t['count'] ?? 0) as num)
        .reduce((a, b) => a > b ? a : b)
        .toDouble();

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.bar_chart,
                    color: AppTheme.primaryBlue, size: 22),
                const SizedBox(width: 8),
                Text(
                  'Violation Trend (Last 7 Days)',
                  style: theme.textTheme.titleMedium
                      ?.copyWith(fontWeight: FontWeight.w700),
                ),
              ],
            ),
            const SizedBox(height: 20),
            SizedBox(
              height: 200,
              child: BarChart(
                BarChartData(
                  alignment: BarChartAlignment.spaceAround,
                  maxY: maxCount * 1.2,
                  barTouchData: BarTouchData(
                    touchTooltipData: BarTouchTooltipData(
                      getTooltipItems: (group, groupIndex, rod, rodIndex) {
                        final entry = trend[group.x.toInt()];
                        return [
                          BarTooltipItem(
                            '${entry['date']}\n${rod.toY.toInt()} violations',
                            const TextStyle(
                              color: Colors.white,
                              fontSize: 12,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ];
                      },
                    ),
                  ),
                  titlesData: FlTitlesData(
                    show: true,
                    bottomTitles: AxisTitles(
                      sideTitles: SideTitles(
                        showTitles: true,
                        getTitlesWidget: (value, meta) {
                          final idx = value.toInt();
                          if (idx < 0 || idx >= trend.length) {
                            return const SizedBox.shrink();
                          }
                          final date =
                              (trend[idx]['date'] ?? '').toString();
                          // Show short date label
                          final shortDate = date.length >= 5
                              ? date.substring(date.length - 5)
                              : date;
                          return Padding(
                            padding: const EdgeInsets.only(top: 8),
                            child: Text(
                              shortDate,
                              style: theme.textTheme.labelSmall?.copyWith(
                                color: theme.colorScheme.onSurface
                                    .withValues(alpha: 0.5),
                                fontSize: 10,
                              ),
                            ),
                          );
                        },
                        reservedSize: 28,
                      ),
                    ),
                    leftTitles: AxisTitles(
                      sideTitles: SideTitles(
                        showTitles: true,
                        reservedSize: 32,
                        getTitlesWidget: (value, meta) {
                          if (value == value.roundToDouble()) {
                            return Text(
                              value.toInt().toString(),
                              style: theme.textTheme.labelSmall?.copyWith(
                                color: theme.colorScheme.onSurface
                                    .withValues(alpha: 0.5),
                                fontSize: 10,
                              ),
                            );
                          }
                          return const SizedBox.shrink();
                        },
                      ),
                    ),
                    topTitles: const AxisTitles(
                        sideTitles: SideTitles(showTitles: false)),
                    rightTitles: const AxisTitles(
                        sideTitles: SideTitles(showTitles: false)),
                  ),
                  gridData: FlGridData(
                    show: true,
                    drawVerticalLine: false,
                    horizontalInterval: maxCount > 5 ? (maxCount / 4).ceilToDouble() : 1,
                    getDrawingHorizontalLine: (value) => FlLine(
                      color: theme.dividerColor,
                      strokeWidth: 1,
                    ),
                  ),
                  borderData: FlBorderData(show: false),
                  barGroups: trend.asMap().entries.map((entry) {
                    final count =
                        (entry.value['count'] ?? 0 as num).toDouble();
                    return BarChartGroupData(
                      x: entry.key,
                      barRods: [
                        BarChartRodData(
                          toY: count,
                          color: count > (maxCount * 0.7)
                              ? AppTheme.errorRed
                              : count > (maxCount * 0.4)
                                  ? AppTheme.warningAmber
                                  : AppTheme.primaryBlue,
                          width: 20,
                          borderRadius: const BorderRadius.vertical(
                              top: Radius.circular(4)),
                        ),
                      ],
                    );
                  }).toList(),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // --- Flagged Transactions Table ---

  Widget _buildFlaggedTransactionsTable(
      BuildContext context, List<Map<String, dynamic>> transactions) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.flag, color: AppTheme.errorRed, size: 22),
                const SizedBox(width: 8),
                Text(
                  'Flagged Transactions',
                  style: theme.textTheme.titleMedium
                      ?.copyWith(fontWeight: FontWeight.w700),
                ),
                const Spacer(),
                Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: AppTheme.errorRed.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    '${transactions.length} flagged',
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: AppTheme.errorRed,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: DataTable(
                headingTextStyle: theme.textTheme.labelMedium?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
                columns: const [
                  DataColumn(label: Text('ID')),
                  DataColumn(label: Text('Type')),
                  DataColumn(label: Text('Amount')),
                  DataColumn(label: Text('Risk Score')),
                  DataColumn(label: Text('Status')),
                  DataColumn(label: Text('Agent')),
                ],
                rows: transactions.asMap().entries.map((entry) {
                  final idx = entry.key;
                  final txn = entry.value;
                  final riskScore = (txn['riskScore'] ?? 0) as num;
                  final status =
                      (txn['status'] ?? '').toString().toUpperCase();
                  final amount = txn['amount'];

                  Color riskColor;
                  if (riskScore > 80) {
                    riskColor = AppTheme.errorRed;
                  } else if (riskScore > 60) {
                    riskColor = AppTheme.warningAmber;
                  } else {
                    riskColor = AppTheme.successGreen;
                  }

                  Color statusColor;
                  switch (status) {
                    case 'FLAGGED':
                      statusColor = AppTheme.errorRed;
                      break;
                    case 'REVIEW':
                    case 'UNDER REVIEW':
                      statusColor = AppTheme.warningAmber;
                      break;
                    case 'RESOLVED':
                      statusColor = AppTheme.successGreen;
                      break;
                    default:
                      statusColor = Colors.grey;
                  }

                  return DataRow(
                    selected: _expandedFlaggedRow == idx,
                    onSelectChanged: (_) {
                      setState(() {
                        _expandedFlaggedRow =
                            _expandedFlaggedRow == idx ? null : idx;
                      });
                    },
                    cells: [
                      DataCell(Text(txn['id']?.toString() ?? '-')),
                      DataCell(Text(txn['type'] ?? '-')),
                      DataCell(Text(
                        amount != null
                            ? _currencyFormat
                                .format((amount as num).toDouble())
                            : '-',
                      )),
                      DataCell(
                        Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 8, vertical: 2),
                          decoration: BoxDecoration(
                            color: riskColor.withValues(alpha: 0.1),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Text(
                            riskScore.toString(),
                            style: theme.textTheme.labelSmall?.copyWith(
                              color: riskColor,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                      ),
                      DataCell(
                        Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 8, vertical: 2),
                          decoration: BoxDecoration(
                            color: statusColor.withValues(alpha: 0.1),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Text(
                            status,
                            style: theme.textTheme.labelSmall?.copyWith(
                              color: statusColor,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ),
                      DataCell(Text(txn['agentId'] ?? '-')),
                    ],
                  );
                }).toList(),
              ),
            ),
            // Expanded detail panel for selected row
            if (_expandedFlaggedRow != null &&
                _expandedFlaggedRow! < transactions.length)
              _buildFlaggedDetailPanel(
                  context, transactions[_expandedFlaggedRow!]),
          ],
        ),
      ),
    );
  }

  Widget _buildFlaggedDetailPanel(
      BuildContext context, Map<String, dynamic> txn) {
    final theme = Theme.of(context);
    final riskScore = (txn['riskScore'] ?? 0) as num;
    final amount = txn['amount'];

    return Container(
      margin: const EdgeInsets.only(top: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: theme.dividerColor),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Transaction Detail',
            style: theme.textTheme.titleSmall
                ?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 10),
          Wrap(
            spacing: 24,
            runSpacing: 8,
            children: [
              _buildDetailItem(context, 'Transaction ID',
                  txn['id']?.toString() ?? '-'),
              _buildDetailItem(
                  context, 'Type', txn['type'] ?? '-'),
              _buildDetailItem(
                  context,
                  'Amount',
                  amount != null
                      ? _currencyFormat
                          .format((amount as num).toDouble())
                      : 'N/A'),
              _buildDetailItem(
                  context, 'Risk Score', riskScore.toString()),
              _buildDetailItem(
                  context, 'Status', txn['status'] ?? '-'),
              _buildDetailItem(
                  context, 'Agent ID', txn['agentId'] ?? '-'),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildDetailItem(
      BuildContext context, String label, String value) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label,
            style: theme.textTheme.labelSmall?.copyWith(
                color: theme.colorScheme.onSurface
                    .withValues(alpha: 0.5))),
        const SizedBox(height: 2),
        Text(value,
            style: theme.textTheme.bodyMedium
                ?.copyWith(fontWeight: FontWeight.w600)),
      ],
    );
  }

  // --- Regulatory Returns ---

  Widget _buildRegulatoryReturns(
      BuildContext context, List<Map<String, dynamic>> returns) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.gavel,
                    color: AppTheme.primaryBlue, size: 22),
                const SizedBox(width: 8),
                Text(
                  'Regulatory Returns',
                  style: theme.textTheme.titleMedium
                      ?.copyWith(fontWeight: FontWeight.w700),
                ),
              ],
            ),
            const SizedBox(height: 12),
            ...returns
                .map((ret) => _buildReturnCard(context, ret))
                .toList(),
          ],
        ),
      ),
    );
  }

  Widget _buildReturnCard(
      BuildContext context, Map<String, dynamic> ret) {
    final theme = Theme.of(context);
    final name = ret['name'] ?? '-';
    final status = (ret['status'] ?? '').toString().toUpperCase();
    final dueDate = ret['dueDate'] ?? '-';

    Color statusColor;
    switch (status) {
      case 'FILED':
        statusColor = AppTheme.successGreen;
        break;
      case 'PENDING':
        statusColor = AppTheme.warningAmber;
        break;
      case 'OVERDUE':
        statusColor = AppTheme.errorRed;
        break;
      default:
        statusColor = Colors.grey;
    }

    // Calculate days until due
    String daysUntilDue = '';
    try {
      final due = DateTime.parse(dueDate);
      final now = DateTime.now();
      final diff = due.difference(now).inDays;
      if (diff > 0) {
        daysUntilDue = '$diff days remaining';
      } else if (diff == 0) {
        daysUntilDue = 'Due today';
      } else {
        daysUntilDue = '${diff.abs()} days overdue';
      }
    } catch (_) {
      daysUntilDue = '';
    }

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: theme.dividerColor),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: statusColor.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Icon(
              status == 'FILED'
                  ? Icons.check_circle
                  : status == 'OVERDUE'
                      ? Icons.error
                      : Icons.schedule,
              color: statusColor,
              size: 20,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(name,
                    style: theme.textTheme.bodyMedium
                        ?.copyWith(fontWeight: FontWeight.w600)),
                const SizedBox(height: 2),
                Text(
                  'Due: $dueDate',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurface
                        .withValues(alpha: 0.5),
                  ),
                ),
                if (daysUntilDue.isNotEmpty)
                  Text(
                    daysUntilDue,
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: daysUntilDue.contains('overdue')
                          ? AppTheme.errorRed
                          : daysUntilDue.contains('today')
                              ? AppTheme.warningAmber
                              : AppTheme.successGreen,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
              ],
            ),
          ),
          Container(
            padding:
                const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              color: statusColor.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(12),
              border:
                  Border.all(color: statusColor.withValues(alpha: 0.3)),
            ),
            child: Text(
              status,
              style: theme.textTheme.labelSmall?.copyWith(
                color: statusColor,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ],
      ),
    );
  }

  // --- KYC Pending Section ---

  Widget _buildKycPendingSection(
      BuildContext context, Map<String, dynamic> summary) {
    final theme = Theme.of(context);
    final kycPending = (summary['kycPending'] ?? 0) as num;
    final kycOverdue = (summary['kycOverdue'] ?? 0) as num;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.person_search,
                    color: AppTheme.primaryBlue, size: 22),
                const SizedBox(width: 8),
                Text(
                  'KYC Status',
                  style: theme.textTheme.titleMedium
                      ?.copyWith(fontWeight: FontWeight.w700),
                ),
              ],
            ),
            const Divider(height: 20),
            Row(
              children: [
                Expanded(
                  child: Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: AppTheme.warningAmber.withValues(alpha: 0.08),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(
                          color:
                              AppTheme.warningAmber.withValues(alpha: 0.2)),
                    ),
                    child: Column(
                      children: [
                        Text(
                          kycPending.toString(),
                          style: theme.textTheme.headlineMedium?.copyWith(
                            fontWeight: FontWeight.w900,
                            color: AppTheme.warningAmber,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'KYC Pending',
                          style: theme.textTheme.labelMedium?.copyWith(
                            color: AppTheme.warningAmber,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: (kycOverdue > 0
                              ? AppTheme.errorRed
                              : Colors.grey)
                          .withValues(alpha: 0.08),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(
                          color: (kycOverdue > 0
                                  ? AppTheme.errorRed
                                  : Colors.grey)
                              .withValues(alpha: 0.2)),
                    ),
                    child: Column(
                      children: [
                        Text(
                          kycOverdue.toString(),
                          style: theme.textTheme.headlineMedium?.copyWith(
                            fontWeight: FontWeight.w900,
                            color: kycOverdue > 0
                                ? AppTheme.errorRed
                                : Colors.grey,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Overdue',
                          style: theme.textTheme.labelMedium?.copyWith(
                            color: kycOverdue > 0
                                ? AppTheme.errorRed
                                : Colors.grey,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
            if (kycOverdue > 0) ...[
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: AppTheme.errorRed.withValues(alpha: 0.08),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(
                      color: AppTheme.errorRed.withValues(alpha: 0.2)),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.warning_amber,
                        color: AppTheme.errorRed, size: 18),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        '$kycOverdue KYC verifications are overdue and require immediate attention.',
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: AppTheme.errorRed,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
