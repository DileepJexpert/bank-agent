import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../providers/mcp_widget_provider.dart';
import '../models/mcp_widget_models.dart';
import '../config/theme.dart';

class CardManagementWidget extends StatefulWidget {
  const CardManagementWidget({super.key});

  @override
  State<CardManagementWidget> createState() => _CardManagementWidgetState();
}

class _CardManagementWidgetState extends State<CardManagementWidget> {
  final _currencyFormat = NumberFormat.currency(
    locale: 'en_IN',
    symbol: '\u20B9',
    decimalDigits: 2,
  );

  // Local toggle state (synced from provider on load)
  bool _internationalEnabled = true;
  bool _onlineEnabled = true;
  bool _contactlessEnabled = true;
  bool _togglesInitialized = false;

  @override
  void initState() {
    super.initState();
    context.read<McpWidgetProvider>().loadCardDetails();
  }

  void _syncToggles(CardDetails card) {
    if (!_togglesInitialized) {
      _internationalEnabled = card.internationalEnabled;
      _onlineEnabled = card.onlineEnabled;
      _contactlessEnabled = card.contactlessEnabled;
      _togglesInitialized = true;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<McpWidgetProvider>(
      builder: (context, provider, _) {
        if (provider.loading && provider.cardDetails == null) {
          return const Card(
            child: Padding(
              padding: EdgeInsets.all(48),
              child: Center(child: CircularProgressIndicator()),
            ),
          );
        }

        if (provider.error != null && provider.cardDetails == null) {
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
                    Text('Failed to load card details',
                        style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 16),
                    ElevatedButton.icon(
                      onPressed: () => provider.loadCardDetails(),
                      icon: const Icon(Icons.refresh, size: 18),
                      label: const Text('Retry'),
                    ),
                  ],
                ),
              ),
            ),
          );
        }

        final card = provider.cardDetails;
        if (card == null) return const SizedBox.shrink();

        _syncToggles(card);

        return Card(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildHeader(context, card),
                const SizedBox(height: 24),
                _buildCardVisual(context, card),
                const SizedBox(height: 24),
                _buildCreditLimitSection(context, card),
                const SizedBox(height: 20),
                _buildFinancialDetails(context, card),
                const SizedBox(height: 20),
                _buildRewardsSection(context, card),
                const Divider(height: 32),
                _buildToggleSection(context),
                const Divider(height: 32),
                _buildActionButtons(context, provider),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildHeader(BuildContext context, CardDetails card) {
    final theme = Theme.of(context);
    final isActive = card.status.toUpperCase() == 'ACTIVE';
    final statusColor = isActive ? AppTheme.successGreen : AppTheme.errorRed;

    return Row(
      children: [
        Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: AppTheme.primaryBlue.withValues(alpha: 0.1),
            borderRadius: BorderRadius.circular(12),
          ),
          child: const Icon(Icons.credit_card,
              color: AppTheme.primaryBlue, size: 24),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Card Management',
                  style: theme.textTheme.titleMedium
                      ?.copyWith(fontWeight: FontWeight.w700)),
              const SizedBox(height: 2),
              Text('${card.cardType} Card',
                  style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurface
                          .withValues(alpha: 0.6))),
            ],
          ),
        ),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          decoration: BoxDecoration(
            color: statusColor.withValues(alpha: 0.1),
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: statusColor.withValues(alpha: 0.3)),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 8,
                height: 8,
                decoration: BoxDecoration(
                  color: statusColor,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 6),
              Text(
                card.status,
                style: theme.textTheme.labelSmall?.copyWith(
                  color: statusColor,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildCardVisual(BuildContext context, CardDetails card) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            AppTheme.primaryBlue,
            AppTheme.darkBlue,
          ],
        ),
        boxShadow: [
          BoxShadow(
            color: AppTheme.primaryBlue.withValues(alpha: 0.3),
            blurRadius: 20,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'IDFC FIRST BANK',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 14,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1.5,
                ),
              ),
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.2),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  card.cardType,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 28),
          // EMV chip placeholder
          Container(
            width: 40,
            height: 28,
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.25),
              borderRadius: BorderRadius.circular(4),
            ),
          ),
          const SizedBox(height: 16),
          Text(
            '\u2022\u2022\u2022\u2022  \u2022\u2022\u2022\u2022  \u2022\u2022\u2022\u2022  ${card.cardLast4}',
            style: const TextStyle(
              color: Colors.white,
              fontSize: 20,
              fontWeight: FontWeight.w600,
              letterSpacing: 3,
              fontFamily: 'monospace',
            ),
          ),
          const SizedBox(height: 20),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'CARD HOLDER',
                    style: TextStyle(
                      color: Colors.white.withValues(alpha: 0.6),
                      fontSize: 9,
                      fontWeight: FontWeight.w500,
                      letterSpacing: 1,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    card.cardName.toUpperCase(),
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      letterSpacing: 0.5,
                    ),
                  ),
                ],
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    'VALID THRU',
                    style: TextStyle(
                      color: Colors.white.withValues(alpha: 0.6),
                      fontSize: 9,
                      fontWeight: FontWeight.w500,
                      letterSpacing: 1,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    card.expiryDate,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildCreditLimitSection(BuildContext context, CardDetails card) {
    final theme = Theme.of(context);
    final utilization = card.utilizationPercent;
    Color barColor;
    if (utilization < 50) {
      barColor = AppTheme.successGreen;
    } else if (utilization < 80) {
      barColor = AppTheme.warningAmber;
    } else {
      barColor = AppTheme.errorRed;
    }

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: theme.dividerColor),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Credit Limit Utilization',
                  style: theme.textTheme.labelMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                    color: theme.colorScheme.onSurface
                        .withValues(alpha: 0.7),
                  )),
              Text(
                '${utilization.toStringAsFixed(1)}%',
                style: theme.textTheme.labelMedium?.copyWith(
                  fontWeight: FontWeight.w700,
                  color: barColor,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: LinearProgressIndicator(
              value: (utilization / 100).clamp(0.0, 1.0),
              minHeight: 10,
              backgroundColor: barColor.withValues(alpha: 0.15),
              valueColor: AlwaysStoppedAnimation<Color>(barColor),
            ),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Total Limit',
                        style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.onSurface
                                .withValues(alpha: 0.5))),
                    const SizedBox(height: 2),
                    Text(_currencyFormat.format(card.creditLimit),
                        style: theme.textTheme.bodyMedium
                            ?.copyWith(fontWeight: FontWeight.w700)),
                  ],
                ),
              ),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    Text('Used',
                        style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.onSurface
                                .withValues(alpha: 0.5))),
                    const SizedBox(height: 2),
                    Text(
                        _currencyFormat.format(card.currentOutstanding),
                        style: theme.textTheme.bodyMedium?.copyWith(
                            fontWeight: FontWeight.w700,
                            color: barColor)),
                  ],
                ),
              ),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text('Available',
                        style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.onSurface
                                .withValues(alpha: 0.5))),
                    const SizedBox(height: 2),
                    Text(_currencyFormat.format(card.availableLimit),
                        style: theme.textTheme.bodyMedium?.copyWith(
                            fontWeight: FontWeight.w700,
                            color: AppTheme.successGreen)),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildFinancialDetails(BuildContext context, CardDetails card) {
    return Row(
      children: [
        Expanded(
          child: _buildInfoTile(
            context,
            icon: Icons.calendar_today_outlined,
            label: 'Due Date',
            value: card.dueDate,
            color: AppTheme.warningAmber,
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: _buildInfoTile(
            context,
            icon: Icons.payment_outlined,
            label: 'Minimum Due',
            value: _currencyFormat.format(card.minimumDue),
            color: AppTheme.errorRed,
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: _buildInfoTile(
            context,
            icon: Icons.account_balance_wallet_outlined,
            label: 'Outstanding',
            value: _currencyFormat.format(card.currentOutstanding),
            color: AppTheme.primaryBlue,
          ),
        ),
      ],
    );
  }

  Widget _buildInfoTile(
    BuildContext context, {
    required IconData icon,
    required String label,
    required String value,
    required Color color,
  }) {
    final theme = Theme.of(context);
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
          Icon(icon, size: 20, color: color),
          const SizedBox(height: 8),
          Text(label,
              style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurface
                      .withValues(alpha: 0.6))),
          const SizedBox(height: 4),
          Text(value,
              style: theme.textTheme.bodyMedium?.copyWith(
                fontWeight: FontWeight.w700,
              )),
        ],
      ),
    );
  }

  Widget _buildRewardsSection(BuildContext context, CardDetails card) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [
            AppTheme.primaryBlue.withValues(alpha: 0.08),
            AppTheme.accentBlue.withValues(alpha: 0.04),
          ],
        ),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
            color: AppTheme.primaryBlue.withValues(alpha: 0.15)),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: AppTheme.primaryBlue.withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(10),
            ),
            child: const Icon(Icons.stars_rounded,
                color: AppTheme.primaryBlue, size: 24),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Reward Points',
                    style: theme.textTheme.labelMedium?.copyWith(
                      color: theme.colorScheme.onSurface
                          .withValues(alpha: 0.6),
                      fontWeight: FontWeight.w500,
                    )),
                const SizedBox(height: 4),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.baseline,
                  textBaseline: TextBaseline.alphabetic,
                  children: [
                    Text(
                      NumberFormat('#,##,###').format(card.rewardPoints),
                      style: theme.textTheme.headlineSmall?.copyWith(
                        fontWeight: FontWeight.w800,
                        color: AppTheme.primaryBlue,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      '= ${_currencyFormat.format(card.rewardValue)}',
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: AppTheme.successGreen,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildToggleSection(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Card Controls',
            style: theme.textTheme.titleSmall
                ?.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: 12),
        _buildToggleRow(
          context,
          icon: Icons.flight_takeoff_rounded,
          title: 'International Usage',
          subtitle: 'Enable transactions outside India',
          value: _internationalEnabled,
          onChanged: (val) => setState(() => _internationalEnabled = val),
        ),
        const SizedBox(height: 8),
        _buildToggleRow(
          context,
          icon: Icons.shopping_cart_outlined,
          title: 'Online Transactions',
          subtitle: 'Enable e-commerce and online payments',
          value: _onlineEnabled,
          onChanged: (val) => setState(() => _onlineEnabled = val),
        ),
        const SizedBox(height: 8),
        _buildToggleRow(
          context,
          icon: Icons.contactless_outlined,
          title: 'Contactless Payments',
          subtitle: 'Enable tap-to-pay at POS terminals',
          value: _contactlessEnabled,
          onChanged: (val) => setState(() => _contactlessEnabled = val),
        ),
      ],
    );
  }

  Widget _buildToggleRow(
    BuildContext context, {
    required IconData icon,
    required String title,
    required String subtitle,
    required bool value,
    required ValueChanged<bool> onChanged,
  }) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: theme.dividerColor),
      ),
      child: Row(
        children: [
          Icon(icon, size: 22, color: AppTheme.primaryBlue),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title,
                    style: theme.textTheme.bodyMedium
                        ?.copyWith(fontWeight: FontWeight.w600)),
                Text(subtitle,
                    style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onSurface
                            .withValues(alpha: 0.5))),
              ],
            ),
          ),
          Switch(
            value: value,
            onChanged: onChanged,
            activeColor: AppTheme.primaryBlue,
          ),
        ],
      ),
    );
  }

  Widget _buildActionButtons(
      BuildContext context, McpWidgetProvider provider) {
    return Wrap(
      spacing: 10,
      runSpacing: 10,
      children: [
        // Block Card
        ElevatedButton.icon(
          onPressed: () => _showBlockCardDialog(context, provider),
          icon: const Icon(Icons.block, size: 18),
          label: const Text('Block Card'),
          style: ElevatedButton.styleFrom(
            backgroundColor: AppTheme.errorRed,
            foregroundColor: Colors.white,
            padding:
                const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
          ),
        ),

        // Request Limit Increase
        OutlinedButton.icon(
          onPressed: () {},
          icon: const Icon(Icons.trending_up, size: 18),
          label: const Text('Request Limit Increase'),
          style: OutlinedButton.styleFrom(
            padding:
                const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
          ),
        ),

        // Convert to EMI
        OutlinedButton.icon(
          onPressed: () {},
          icon: const Icon(Icons.calendar_month_outlined, size: 18),
          label: const Text('Convert to EMI'),
          style: OutlinedButton.styleFrom(
            padding:
                const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
          ),
        ),

        // Redeem Points
        OutlinedButton.icon(
          onPressed: () {},
          icon: const Icon(Icons.redeem, size: 18),
          label: const Text('Redeem Points'),
          style: OutlinedButton.styleFrom(
            foregroundColor: AppTheme.primaryBlue,
            padding:
                const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
          ),
        ),
      ],
    );
  }

  Future<void> _showBlockCardDialog(
      BuildContext context, McpWidgetProvider provider) async {
    String? selectedReason;
    final reasons = [
      'Lost',
      'Stolen',
      'Damaged',
      'Suspicious Activity',
      'Other',
    ];

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (ctx, setDialogState) {
            return AlertDialog(
              title: const Row(
                children: [
                  Icon(Icons.warning_amber_rounded,
                      color: AppTheme.errorRed, size: 28),
                  SizedBox(width: 12),
                  Expanded(
                    child: Text('Block Card',
                        style: TextStyle(fontWeight: FontWeight.w700)),
                  ),
                ],
              ),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Are you sure you want to block your card ending in ${provider.cardDetails?.cardLast4}? This action cannot be undone immediately.',
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                  const SizedBox(height: 16),
                  DropdownButtonFormField<String>(
                    value: selectedReason,
                    decoration: const InputDecoration(
                      labelText: 'Reason for blocking',
                      border: OutlineInputBorder(),
                    ),
                    items: reasons
                        .map((r) =>
                            DropdownMenuItem(value: r, child: Text(r)))
                        .toList(),
                    onChanged: (val) {
                      setDialogState(() => selectedReason = val);
                    },
                  ),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.of(ctx).pop(false),
                  child: const Text('Cancel'),
                ),
                ElevatedButton(
                  onPressed: selectedReason != null
                      ? () => Navigator.of(ctx).pop(true)
                      : null,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTheme.errorRed,
                    foregroundColor: Colors.white,
                  ),
                  child: const Text('Block Card'),
                ),
              ],
            );
          },
        );
      },
    );

    if (confirmed == true && selectedReason != null && mounted) {
      try {
        await provider.blockCard(selectedReason!);
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Card blocked successfully'),
              backgroundColor: AppTheme.successGreen,
            ),
          );
          provider.loadCardDetails();
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Failed to block card: $e'),
              backgroundColor: AppTheme.errorRed,
            ),
          );
        }
      }
    }
  }
}
