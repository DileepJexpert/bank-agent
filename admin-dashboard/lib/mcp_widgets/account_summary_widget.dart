import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:fl_chart/fl_chart.dart';
import 'package:intl/intl.dart';
import '../providers/mcp_widget_provider.dart';
import '../models/mcp_widget_models.dart';
import '../config/theme.dart';

class AccountSummaryWidget extends StatefulWidget {
  const AccountSummaryWidget({super.key});

  @override
  State<AccountSummaryWidget> createState() => _AccountSummaryWidgetState();
}

class _AccountSummaryWidgetState extends State<AccountSummaryWidget> {
  final _currencyFormat = NumberFormat.currency(
    locale: 'en_IN',
    symbol: '\u20B9',
    decimalDigits: 2,
  );

  @override
  void initState() {
    super.initState();
    final provider = context.read<McpWidgetProvider>();
    provider.loadAccountSummary();
    provider.loadTransactions();
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<McpWidgetProvider>(
      builder: (context, provider, _) {
        if (provider.loading && provider.accountSummary == null) {
          return const Card(
            child: Padding(
              padding: EdgeInsets.all(48),
              child: Center(child: CircularProgressIndicator()),
            ),
          );
        }

        if (provider.error != null && provider.accountSummary == null) {
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
                    Text('Failed to load account summary',
                        style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 8),
                    Text(provider.error!,
                        style: Theme.of(context).textTheme.bodySmall),
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
        if (account == null) return const SizedBox.shrink();

        return LayoutBuilder(
          builder: (context, constraints) {
            final isCompact = constraints.maxWidth < 600;
            return isCompact
                ? _buildCompactLayout(context, account, provider)
                : _buildExpandedLayout(context, account, provider);
          },
        );
      },
    );
  }

  Widget _buildExpandedLayout(
    BuildContext context,
    AccountSummary account,
    McpWidgetProvider provider,
  ) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildHeader(context, account),
            const SizedBox(height: 24),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  flex: 3,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _buildBalanceDisplay(context, account),
                      const SizedBox(height: 20),
                      _buildAccountDetails(context, account),
                    ],
                  ),
                ),
                const SizedBox(width: 24),
                Expanded(
                  flex: 2,
                  child: _buildSparklineChart(context, account),
                ),
              ],
            ),
            const Divider(height: 32),
            _buildRecentTransactions(context, provider),
            const SizedBox(height: 20),
            _buildQuickActions(context, compact: false),
          ],
        ),
      ),
    );
  }

  Widget _buildCompactLayout(
    BuildContext context,
    AccountSummary account,
    McpWidgetProvider provider,
  ) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildHeader(context, account),
            const SizedBox(height: 16),
            _buildBalanceDisplay(context, account),
            const SizedBox(height: 16),
            _buildAccountDetails(context, account),
            const SizedBox(height: 16),
            _buildSparklineChart(context, account),
            const Divider(height: 24),
            _buildRecentTransactions(context, provider),
            const SizedBox(height: 16),
            _buildQuickActions(context, compact: true),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context, AccountSummary account) {
    final theme = Theme.of(context);
    return Row(
      children: [
        Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: AppTheme.primaryBlue.withValues(alpha: 0.1),
            borderRadius: BorderRadius.circular(12),
          ),
          child: const Icon(Icons.account_balance,
              color: AppTheme.primaryBlue, size: 24),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Account Summary',
                  style: theme.textTheme.titleMedium
                      ?.copyWith(fontWeight: FontWeight.w700)),
              const SizedBox(height: 2),
              Text(account.customerId,
                  style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurface
                          .withValues(alpha: 0.6))),
            ],
          ),
        ),
        _buildAccountTypeBadge(context, account.accountType),
        const SizedBox(width: 8),
        _buildStatusIndicator(context, account.status),
      ],
    );
  }

  Widget _buildAccountTypeBadge(BuildContext context, String type) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: AppTheme.primaryBlue.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(20),
        border:
            Border.all(color: AppTheme.primaryBlue.withValues(alpha: 0.3)),
      ),
      child: Text(
        type,
        style: Theme.of(context).textTheme.labelSmall?.copyWith(
              color: AppTheme.primaryBlue,
              fontWeight: FontWeight.w600,
            ),
      ),
    );
  }

  Widget _buildStatusIndicator(BuildContext context, String status) {
    final isActive = status.toUpperCase() == 'ACTIVE';
    final color = isActive ? AppTheme.successGreen : AppTheme.errorRed;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 8,
          height: 8,
          decoration: BoxDecoration(
            color: color,
            shape: BoxShape.circle,
            boxShadow: [
              BoxShadow(
                  color: color.withValues(alpha: 0.4),
                  blurRadius: 4,
                  spreadRadius: 1),
            ],
          ),
        ),
        const SizedBox(width: 6),
        Text(
          status,
          style: Theme.of(context).textTheme.labelSmall?.copyWith(
                color: color,
                fontWeight: FontWeight.w600,
              ),
        ),
      ],
    );
  }

  Widget _buildBalanceDisplay(BuildContext context, AccountSummary account) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Available Balance',
          style: theme.textTheme.bodySmall?.copyWith(
            color: theme.colorScheme.onSurface.withValues(alpha: 0.6),
            fontWeight: FontWeight.w500,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          _currencyFormat.format(account.balance),
          style: theme.textTheme.headlineLarge?.copyWith(
            fontWeight: FontWeight.w800,
            color: AppTheme.primaryBlue,
            letterSpacing: -0.5,
          ),
        ),
      ],
    );
  }

  Widget _buildAccountDetails(BuildContext context, AccountSummary account) {
    final theme = Theme.of(context);
    final detailStyle = theme.textTheme.bodySmall?.copyWith(
      color: theme.colorScheme.onSurface.withValues(alpha: 0.7),
    );
    final valueStyle = theme.textTheme.bodyMedium?.copyWith(
      fontWeight: FontWeight.w600,
    );

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: theme.dividerColor),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Account No.', style: detailStyle),
                const SizedBox(height: 2),
                Text(account.accountNumber, style: valueStyle),
              ],
            ),
          ),
          Container(width: 1, height: 36, color: theme.dividerColor),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('IFSC', style: detailStyle),
                const SizedBox(height: 2),
                Text(account.ifsc, style: valueStyle),
              ],
            ),
          ),
          Container(width: 1, height: 36, color: theme.dividerColor),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Branch', style: detailStyle),
                const SizedBox(height: 2),
                Text(account.branch, style: valueStyle),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSparklineChart(BuildContext context, AccountSummary account) {
    final theme = Theme.of(context);
    if (account.balanceTrend.isEmpty) {
      return Container(
        height: 140,
        decoration: BoxDecoration(
          color: theme.colorScheme.surface,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: theme.dividerColor),
        ),
        child: const Center(child: Text('No trend data')),
      );
    }

    final spots = account.balanceTrend.asMap().entries.map((entry) {
      return FlSpot(entry.key.toDouble(), entry.value.balance);
    }).toList();

    final minY = spots.map((s) => s.y).reduce((a, b) => a < b ? a : b);
    final maxY = spots.map((s) => s.y).reduce((a, b) => a > b ? a : b);
    final padding = (maxY - minY) * 0.1;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: theme.dividerColor),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '30-Day Balance Trend',
            style: theme.textTheme.labelMedium?.copyWith(
              fontWeight: FontWeight.w600,
              color: theme.colorScheme.onSurface.withValues(alpha: 0.7),
            ),
          ),
          const SizedBox(height: 12),
          SizedBox(
            height: 120,
            child: LineChart(
              LineChartData(
                gridData: const FlGridData(show: false),
                titlesData: const FlTitlesData(show: false),
                borderData: FlBorderData(show: false),
                minY: minY - padding,
                maxY: maxY + padding,
                lineTouchData: LineTouchData(
                  touchTooltipData: LineTouchTooltipData(
                    getTooltipItems: (spots) {
                      return spots.map((spot) {
                        return LineTooltipItem(
                          _currencyFormat.format(spot.y),
                          const TextStyle(
                            color: Colors.white,
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                          ),
                        );
                      }).toList();
                    },
                  ),
                ),
                lineBarsData: [
                  LineChartBarData(
                    spots: spots,
                    isCurved: true,
                    curveSmoothness: 0.3,
                    color: AppTheme.primaryBlue,
                    barWidth: 2.5,
                    isStrokeCapRound: true,
                    dotData: const FlDotData(show: false),
                    belowBarData: BarAreaData(
                      show: true,
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [
                          AppTheme.primaryBlue.withValues(alpha: 0.25),
                          AppTheme.primaryBlue.withValues(alpha: 0.0),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildRecentTransactions(
      BuildContext context, McpWidgetProvider provider) {
    final theme = Theme.of(context);
    final recentTxns = provider.transactions.take(3).toList();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              'Recent Transactions',
              style: theme.textTheme.titleSmall
                  ?.copyWith(fontWeight: FontWeight.w700),
            ),
            TextButton(
              onPressed: () {},
              child: const Text('View All'),
            ),
          ],
        ),
        const SizedBox(height: 8),
        if (recentTxns.isEmpty)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 16),
            child: Center(
              child: Text(
                'No recent transactions',
                style: theme.textTheme.bodySmall?.copyWith(
                  color:
                      theme.colorScheme.onSurface.withValues(alpha: 0.5),
                ),
              ),
            ),
          )
        else
          ...recentTxns.map((txn) => _buildTransactionTile(context, txn)),
      ],
    );
  }

  Widget _buildTransactionTile(BuildContext context, Transaction txn) {
    final theme = Theme.of(context);
    final isCredit = txn.isCredit;
    final amountColor = isCredit ? AppTheme.successGreen : AppTheme.errorRed;
    final amountPrefix = isCredit ? '+ ' : '- ';

    return Container(
      margin: const EdgeInsets.only(bottom: 6),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(8),
        color: theme.colorScheme.surface,
      ),
      child: Row(
        children: [
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              color: amountColor.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Icon(
              isCredit
                  ? Icons.arrow_downward_rounded
                  : Icons.arrow_upward_rounded,
              color: amountColor,
              size: 18,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(txn.description,
                    style: theme.textTheme.bodyMedium
                        ?.copyWith(fontWeight: FontWeight.w600),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis),
                Text(txn.date,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurface
                          .withValues(alpha: 0.5),
                    )),
              ],
            ),
          ),
          Text(
            '$amountPrefix${_currencyFormat.format(txn.amount.abs())}',
            style: theme.textTheme.bodyMedium?.copyWith(
              fontWeight: FontWeight.w700,
              color: amountColor,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildQuickActions(BuildContext context, {required bool compact}) {
    final actions = [
      const _QuickAction(Icons.swap_horiz_rounded, 'Transfer'),
      const _QuickAction(Icons.receipt_long_rounded, 'Pay Bill'),
      const _QuickAction(Icons.description_rounded, 'View Statement'),
      const _QuickAction(Icons.savings_rounded, 'Create FD'),
    ];

    if (compact) {
      return Wrap(
        spacing: 8,
        runSpacing: 8,
        children:
            actions.map((a) => _buildActionButton(context, a)).toList(),
      );
    }

    return Row(
      children: actions
          .map((a) => Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: _buildActionButton(context, a),
                ),
              ))
          .toList(),
    );
  }

  Widget _buildActionButton(BuildContext context, _QuickAction action) {
    return OutlinedButton.icon(
      onPressed: () {},
      icon: Icon(action.icon, size: 16),
      label: Text(action.label, style: const TextStyle(fontSize: 12)),
      style: OutlinedButton.styleFrom(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      ),
    );
  }
}

class _QuickAction {
  final IconData icon;
  final String label;
  const _QuickAction(this.icon, this.label);
}
