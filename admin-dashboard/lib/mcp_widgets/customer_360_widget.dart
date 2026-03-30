import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../providers/mcp_widget_provider.dart';
import '../config/theme.dart';

class Customer360Widget extends StatefulWidget {
  const Customer360Widget({super.key});

  @override
  State<Customer360Widget> createState() => _Customer360WidgetState();
}

class _Customer360WidgetState extends State<Customer360Widget> {
  final _currencyFormat = NumberFormat.currency(
    locale: 'en_IN',
    symbol: '\u20B9',
    decimalDigits: 2,
  );
  final _searchController = TextEditingController();

  @override
  void initState() {
    super.initState();
    final provider = context.read<McpWidgetProvider>();
    provider.loadCustomer360();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _onSearch(McpWidgetProvider provider) {
    final value = _searchController.text.trim();
    if (value.isNotEmpty) {
      provider.setCustomerId(value);
      provider.loadCustomer360();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<McpWidgetProvider>(
      builder: (context, provider, _) {
        return SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildSearchBar(provider),
              const SizedBox(height: 20),
              if (provider.loading && provider.customer360 == null)
                const Card(
                  child: Padding(
                    padding: EdgeInsets.all(48),
                    child: Center(child: CircularProgressIndicator()),
                  ),
                )
              else if (provider.error != null && provider.customer360 == null)
                _buildErrorCard(context, provider)
              else if (provider.customer360 != null)
                _buildContent(context, provider),
            ],
          ),
        );
      },
    );
  }

  Widget _buildSearchBar(McpWidgetProvider provider) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: AppTheme.primaryBlue.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Icon(Icons.person_search,
                  color: AppTheme.primaryBlue, size: 24),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: TextField(
                controller: _searchController,
                decoration: const InputDecoration(
                  hintText: 'Search by Customer ID or Name',
                  prefixIcon: Icon(Icons.search),
                  border: OutlineInputBorder(),
                ),
                onSubmitted: (_) => _onSearch(provider),
              ),
            ),
            const SizedBox(width: 12),
            ElevatedButton.icon(
              onPressed: () => _onSearch(provider),
              icon: const Icon(Icons.search, size: 18),
              label: const Text('Search'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildErrorCard(BuildContext context, McpWidgetProvider provider) {
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
              Text('Failed to load customer data',
                  style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              Text(provider.error!,
                  style: Theme.of(context).textTheme.bodySmall),
              const SizedBox(height: 16),
              ElevatedButton.icon(
                onPressed: () => provider.loadCustomer360(),
                icon: const Icon(Icons.refresh, size: 18),
                label: const Text('Retry'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildContent(BuildContext context, McpWidgetProvider provider) {
    final data = provider.customer360!;
    final profile = data['profile'] as Map<String, dynamic>? ?? {};
    final accounts = (data['accounts'] as List?)?.cast<Map<String, dynamic>>() ?? [];
    final loans = (data['loans'] as List?)?.cast<Map<String, dynamic>>() ?? [];
    final cards = (data['cards'] as List?)?.cast<Map<String, dynamic>>() ?? [];
    final interactions = (data['recentInteractions'] as List?)?.cast<Map<String, dynamic>>() ?? [];
    final pendingRequests = (data['pendingRequests'] as List?)?.cast<Map<String, dynamic>>() ?? [];
    final crossSell = (data['crossSellRecommendations'] as List?)?.cast<Map<String, dynamic>>() ?? [];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildProfileCard(context, profile),
        const SizedBox(height: 16),
        if (accounts.isNotEmpty) ...[
          _buildSectionHeader(context, 'Accounts', Icons.account_balance),
          const SizedBox(height: 8),
          _buildAccountsList(context, accounts),
          const SizedBox(height: 16),
        ],
        if (loans.isNotEmpty) ...[
          _buildSectionHeader(context, 'Loans', Icons.credit_score),
          const SizedBox(height: 8),
          _buildLoansList(context, loans),
          const SizedBox(height: 16),
        ],
        if (cards.isNotEmpty) ...[
          _buildSectionHeader(context, 'Cards', Icons.credit_card),
          const SizedBox(height: 8),
          _buildCardsList(context, cards),
          const SizedBox(height: 16),
        ],
        if (interactions.isNotEmpty) ...[
          _buildSectionHeader(context, 'Recent Interactions', Icons.history),
          const SizedBox(height: 8),
          _buildInteractionsTable(context, interactions),
          const SizedBox(height: 16),
        ],
        if (pendingRequests.isNotEmpty) ...[
          _buildSectionHeader(context, 'Pending Requests', Icons.pending_actions),
          const SizedBox(height: 8),
          _buildPendingRequests(context, pendingRequests),
          const SizedBox(height: 16),
        ],
        if (crossSell.isNotEmpty) ...[
          _buildSectionHeader(context, 'Cross-Sell Recommendations', Icons.recommend),
          const SizedBox(height: 8),
          _buildCrossSellRecommendations(context, crossSell),
        ],
      ],
    );
  }

  Widget _buildSectionHeader(BuildContext context, String title, IconData icon) {
    final theme = Theme.of(context);
    return Row(
      children: [
        Icon(icon, color: AppTheme.primaryBlue, size: 20),
        const SizedBox(width: 8),
        Text(
          title,
          style: theme.textTheme.titleMedium?.copyWith(
            fontWeight: FontWeight.w700,
          ),
        ),
      ],
    );
  }

  // --- Profile Card ---

  Widget _buildProfileCard(BuildContext context, Map<String, dynamic> profile) {
    final theme = Theme.of(context);
    final name = profile['name'] ?? 'Unknown';
    final segment = (profile['segment'] ?? '').toString().toUpperCase();
    final rm = profile['relationshipManager'] ?? '-';
    final since = profile['customerSince'] ?? '-';
    final riskProfile = profile['riskProfile'] ?? '-';
    final npsScore = (profile['npsScore'] ?? 0).toDouble();
    final phone = _maskPhone(profile['phone'] ?? '');
    final email = _maskEmail(profile['email'] ?? '');

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final isWide = constraints.maxWidth > 600;
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    CircleAvatar(
                      radius: 28,
                      backgroundColor: AppTheme.primaryBlue.withValues(alpha: 0.1),
                      child: Text(
                        name.isNotEmpty ? name[0].toUpperCase() : '?',
                        style: theme.textTheme.headlineSmall?.copyWith(
                          color: AppTheme.primaryBlue,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Flexible(
                                child: Text(
                                  name,
                                  style: theme.textTheme.headlineSmall?.copyWith(
                                    fontWeight: FontWeight.w800,
                                  ),
                                ),
                              ),
                              const SizedBox(width: 10),
                              _buildSegmentBadge(context, segment),
                            ],
                          ),
                          const SizedBox(height: 4),
                          Text(
                            'Customer ID: ${profile['customerId'] ?? '-'}',
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.onSurface.withValues(alpha: 0.6),
                            ),
                          ),
                        ],
                      ),
                    ),
                    _buildNpsGauge(context, npsScore),
                  ],
                ),
                const Divider(height: 28),
                Wrap(
                  spacing: isWide ? 32 : 16,
                  runSpacing: 12,
                  children: [
                    _buildInfoChip(context, Icons.person_outline, 'RM', rm),
                    _buildInfoChip(context, Icons.calendar_today, 'Since', since),
                    _buildInfoChip(context, Icons.shield_outlined, 'Risk', riskProfile),
                    _buildInfoChip(context, Icons.phone, 'Phone', phone),
                    _buildInfoChip(context, Icons.email_outlined, 'Email', email),
                    _buildInfoChip(context, Icons.language, 'Language',
                        profile['preferredLanguage'] ?? '-'),
                  ],
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _buildSegmentBadge(BuildContext context, String segment) {
    Color color;
    switch (segment) {
      case 'PREMIUM':
        color = AppTheme.primaryBlue;
        break;
      case 'HNI':
        color = const Color(0xFFFFB300);
        break;
      case 'MASS':
      default:
        color = Colors.grey;
    }
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Text(
        segment,
        style: Theme.of(context).textTheme.labelSmall?.copyWith(
              color: color,
              fontWeight: FontWeight.w700,
            ),
      ),
    );
  }

  Widget _buildNpsGauge(BuildContext context, double npsScore) {
    final clampedScore = npsScore.clamp(0.0, 10.0);
    final color = clampedScore >= 8
        ? AppTheme.successGreen
        : clampedScore >= 5
            ? AppTheme.warningAmber
            : AppTheme.errorRed;
    return Column(
      children: [
        SizedBox(
          width: 56,
          height: 56,
          child: Stack(
            alignment: Alignment.center,
            children: [
              CircularProgressIndicator(
                value: clampedScore / 10.0,
                strokeWidth: 5,
                backgroundColor: color.withValues(alpha: 0.15),
                valueColor: AlwaysStoppedAnimation(color),
              ),
              Text(
                npsScore.toStringAsFixed(1),
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                      fontWeight: FontWeight.w800,
                      color: color,
                    ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 4),
        Text(
          'NPS',
          style: Theme.of(context).textTheme.labelSmall?.copyWith(
                color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.5),
              ),
        ),
      ],
    );
  }

  Widget _buildInfoChip(
      BuildContext context, IconData icon, String label, String value) {
    final theme = Theme.of(context);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 16, color: AppTheme.primaryBlue.withValues(alpha: 0.7)),
        const SizedBox(width: 6),
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label,
                style: theme.textTheme.labelSmall?.copyWith(
                    color: theme.colorScheme.onSurface.withValues(alpha: 0.5))),
            Text(value,
                style: theme.textTheme.bodySmall?.copyWith(
                    fontWeight: FontWeight.w600)),
          ],
        ),
      ],
    );
  }

  String _maskPhone(String phone) {
    if (phone.length < 6) return phone;
    return '${phone.substring(0, 3)}${'*' * (phone.length - 5)}${phone.substring(phone.length - 2)}';
  }

  String _maskEmail(String email) {
    final parts = email.split('@');
    if (parts.length != 2) return email;
    final name = parts[0];
    final domain = parts[1];
    if (name.length <= 2) return email;
    return '${name.substring(0, 2)}${'*' * (name.length - 2)}@$domain';
  }

  // --- Accounts Section ---

  Widget _buildAccountsList(
      BuildContext context, List<Map<String, dynamic>> accounts) {
    return Column(
      children: accounts
          .map((acct) => _buildAccountCard(context, acct))
          .toList(),
    );
  }

  Widget _buildAccountCard(BuildContext context, Map<String, dynamic> acct) {
    final theme = Theme.of(context);
    final type = (acct['type'] ?? '').toString();
    final number = acct['number'] ?? '-';
    final balance = (acct['balance'] ?? 0).toDouble();
    final status = (acct['status'] ?? '').toString().toUpperCase();
    final maturity = acct['maturity'];

    IconData typeIcon;
    switch (type.toUpperCase()) {
      case 'SAVINGS':
        typeIcon = Icons.savings;
        break;
      case 'CURRENT':
        typeIcon = Icons.account_balance;
        break;
      case 'FD':
      case 'FIXED DEPOSIT':
        typeIcon = Icons.lock_clock;
        break;
      default:
        typeIcon = Icons.account_balance_wallet;
    }

    final isActive = status == 'ACTIVE';
    final statusColor = isActive ? AppTheme.successGreen : AppTheme.warningAmber;

    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: AppTheme.primaryBlue.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Icon(typeIcon, color: AppTheme.primaryBlue, size: 22),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(type,
                      style: theme.textTheme.titleSmall
                          ?.copyWith(fontWeight: FontWeight.w700)),
                  const SizedBox(height: 2),
                  Text('A/C: $number',
                      style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.onSurface
                              .withValues(alpha: 0.6))),
                  if (maturity != null)
                    Text('Maturity: $maturity',
                        style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.onSurface
                                .withValues(alpha: 0.5))),
                ],
              ),
            ),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(
                  _currencyFormat.format(balance),
                  style: theme.textTheme.titleSmall?.copyWith(
                    fontWeight: FontWeight.w800,
                    color: AppTheme.primaryBlue,
                  ),
                ),
                const SizedBox(height: 4),
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
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
              ],
            ),
          ],
        ),
      ),
    );
  }

  // --- Loans Section ---

  Widget _buildLoansList(
      BuildContext context, List<Map<String, dynamic>> loans) {
    return Column(
      children: loans.map((loan) => _buildLoanCard(context, loan)).toList(),
    );
  }

  Widget _buildLoanCard(BuildContext context, Map<String, dynamic> loan) {
    final theme = Theme.of(context);
    final type = loan['type'] ?? '-';
    final amount = (loan['amount'] ?? 0).toDouble();
    final outstanding = (loan['outstanding'] ?? 0).toDouble();
    final emi = (loan['emi'] ?? 0).toDouble();
    final emiStatus = (loan['emiStatus'] ?? '').toString().toUpperCase();
    final nextDue = loan['nextDue'] ?? '-';

    final isOverdue = emiStatus == 'OVERDUE';
    final emiColor = isOverdue ? AppTheme.errorRed : AppTheme.successGreen;

    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: AppTheme.primaryBlue.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(Icons.credit_score,
                      color: AppTheme.primaryBlue, size: 20),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(type,
                      style: theme.textTheme.titleSmall
                          ?.copyWith(fontWeight: FontWeight.w700)),
                ),
                Chip(
                  label: Text(emiStatus),
                  labelStyle: TextStyle(
                    color: emiColor,
                    fontWeight: FontWeight.w600,
                    fontSize: 11,
                  ),
                  backgroundColor: emiColor.withValues(alpha: 0.1),
                  side: BorderSide(color: emiColor.withValues(alpha: 0.3)),
                  padding: EdgeInsets.zero,
                  visualDensity: VisualDensity.compact,
                ),
              ],
            ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 24,
              runSpacing: 8,
              children: [
                _buildLoanDetail(context, 'Original Amount',
                    _currencyFormat.format(amount)),
                _buildLoanDetail(context, 'Outstanding',
                    _currencyFormat.format(outstanding)),
                _buildLoanDetail(
                    context, 'EMI', _currencyFormat.format(emi)),
                _buildLoanDetail(context, 'Next Due', nextDue),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildLoanDetail(BuildContext context, String label, String value) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label,
            style: theme.textTheme.labelSmall?.copyWith(
                color:
                    theme.colorScheme.onSurface.withValues(alpha: 0.5))),
        const SizedBox(height: 2),
        Text(value,
            style: theme.textTheme.bodyMedium
                ?.copyWith(fontWeight: FontWeight.w600)),
      ],
    );
  }

  // --- Cards Section ---

  Widget _buildCardsList(
      BuildContext context, List<Map<String, dynamic>> cards) {
    return Column(
      children: cards.map((card) => _buildCardItem(context, card)).toList(),
    );
  }

  Widget _buildCardItem(BuildContext context, Map<String, dynamic> card) {
    final theme = Theme.of(context);
    final type = card['type'] ?? '-';
    final last4 = card['last4'] ?? '****';
    final limit = card['limit'];
    final utilization = card['utilization'];
    final status = (card['status'] ?? '').toString().toUpperCase();

    final isActive = status == 'ACTIVE';
    final statusColor = isActive ? AppTheme.successGreen : AppTheme.warningAmber;

    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: AppTheme.primaryBlue.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(Icons.credit_card,
                      color: AppTheme.primaryBlue, size: 20),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(type,
                          style: theme.textTheme.titleSmall
                              ?.copyWith(fontWeight: FontWeight.w700)),
                      Text('**** **** **** $last4',
                          style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.onSurface
                                  .withValues(alpha: 0.6))),
                    ],
                  ),
                ),
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
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
              ],
            ),
            if (limit != null && utilization != null) ...[
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text('Utilization',
                                style: theme.textTheme.labelSmall?.copyWith(
                                    color: theme.colorScheme.onSurface
                                        .withValues(alpha: 0.5))),
                            Text(
                              '${(utilization as num).toStringAsFixed(0)}%',
                              style: theme.textTheme.labelSmall?.copyWith(
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 4),
                        ClipRRect(
                          borderRadius: BorderRadius.circular(4),
                          child: LinearProgressIndicator(
                            value: (utilization as num).toDouble() / 100.0,
                            minHeight: 6,
                            backgroundColor: theme.dividerColor,
                            valueColor: AlwaysStoppedAnimation(
                              utilization > 80
                                  ? AppTheme.errorRed
                                  : utilization > 50
                                      ? AppTheme.warningAmber
                                      : AppTheme.successGreen,
                            ),
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Limit: ${_currencyFormat.format((limit as num).toDouble())}',
                          style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.onSurface
                                  .withValues(alpha: 0.5)),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  // --- Recent Interactions ---

  Widget _buildInteractionsTable(
      BuildContext context, List<Map<String, dynamic>> interactions) {
    final theme = Theme.of(context);
    return Card(
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: DataTable(
          headingTextStyle: theme.textTheme.labelMedium?.copyWith(
            fontWeight: FontWeight.w700,
          ),
          columns: const [
            DataColumn(label: Text('Date')),
            DataColumn(label: Text('Channel')),
            DataColumn(label: Text('Agent')),
            DataColumn(label: Text('Summary')),
          ],
          rows: interactions.map((interaction) {
            final channel =
                (interaction['channel'] ?? '').toString().toLowerCase();
            IconData channelIcon;
            switch (channel) {
              case 'chat':
                channelIcon = Icons.chat_bubble;
                break;
              case 'phone':
              case 'call':
                channelIcon = Icons.phone;
                break;
              case 'branch':
              case 'store':
                channelIcon = Icons.store;
                break;
              case 'email':
              case 'message':
                channelIcon = Icons.message;
                break;
              default:
                channelIcon = Icons.help_outline;
            }
            return DataRow(cells: [
              DataCell(Text(interaction['date'] ?? '-')),
              DataCell(Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(channelIcon, size: 16, color: AppTheme.primaryBlue),
                  const SizedBox(width: 6),
                  Text(interaction['channel'] ?? '-'),
                ],
              )),
              DataCell(Text(interaction['agent'] ?? '-')),
              DataCell(
                ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 250),
                  child: Text(
                    interaction['summary'] ?? '-',
                    overflow: TextOverflow.ellipsis,
                    maxLines: 2,
                  ),
                ),
              ),
            ]);
          }).toList(),
        ),
      ),
    );
  }

  // --- Pending Requests ---

  Widget _buildPendingRequests(
      BuildContext context, List<Map<String, dynamic>> requests) {
    return Column(
      children:
          requests.map((req) => _buildPendingRequestCard(context, req)).toList(),
    );
  }

  Widget _buildPendingRequestCard(
      BuildContext context, Map<String, dynamic> req) {
    final theme = Theme.of(context);
    final type = req['type'] ?? '-';
    final status = (req['status'] ?? '').toString().toUpperCase();
    final daysOpen = (req['daysOpen'] ?? 0) as num;
    final sla = (req['sla'] ?? 1) as num;
    final progress = sla > 0 ? (daysOpen / sla).clamp(0.0, 1.0) : 0.0;

    Color statusColor;
    switch (status) {
      case 'OPEN':
        statusColor = AppTheme.warningAmber;
        break;
      case 'IN PROGRESS':
      case 'IN_PROGRESS':
        statusColor = AppTheme.primaryBlue;
        break;
      case 'RESOLVED':
        statusColor = AppTheme.successGreen;
        break;
      default:
        statusColor = Colors.grey;
    }

    final isNearSla = progress > 0.7;
    final progressColor = isNearSla ? AppTheme.errorRed : AppTheme.primaryBlue;

    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Text(type,
                          style: theme.textTheme.bodyMedium
                              ?.copyWith(fontWeight: FontWeight.w600)),
                      const SizedBox(width: 8),
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
                    ],
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Expanded(
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(4),
                          child: LinearProgressIndicator(
                            value: progress.toDouble(),
                            minHeight: 6,
                            backgroundColor: theme.dividerColor,
                            valueColor: AlwaysStoppedAnimation(progressColor),
                          ),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Text(
                        '${daysOpen.toInt()} of ${sla.toInt()} days used',
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: progressColor,
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
      ),
    );
  }

  // --- Cross-Sell Recommendations ---

  Widget _buildCrossSellRecommendations(
      BuildContext context, List<Map<String, dynamic>> recommendations) {
    return Column(
      children: recommendations
          .map((rec) => _buildCrossSellCard(context, rec))
          .toList(),
    );
  }

  Widget _buildCrossSellCard(BuildContext context, Map<String, dynamic> rec) {
    final theme = Theme.of(context);
    final product = rec['product'] ?? '-';
    final reason = rec['reason'] ?? '-';
    final confidence = ((rec['confidence'] ?? 0) as num).toDouble();

    Color confColor;
    if (confidence >= 0.8) {
      confColor = AppTheme.successGreen;
    } else if (confidence >= 0.5) {
      confColor = AppTheme.warningAmber;
    } else {
      confColor = Colors.grey;
    }

    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: AppTheme.primaryBlue.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Icon(Icons.recommend,
                  color: AppTheme.primaryBlue, size: 20),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(product,
                      style: theme.textTheme.bodyMedium
                          ?.copyWith(fontWeight: FontWeight.w700)),
                  const SizedBox(height: 2),
                  Text(reason,
                      style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.onSurface
                              .withValues(alpha: 0.6))),
                ],
              ),
            ),
            const SizedBox(width: 12),
            SizedBox(
              width: 80,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    '${(confidence * 100).toStringAsFixed(0)}%',
                    style: theme.textTheme.labelMedium?.copyWith(
                      color: confColor,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 4),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(4),
                    child: LinearProgressIndicator(
                      value: confidence,
                      minHeight: 5,
                      backgroundColor: theme.dividerColor,
                      valueColor: AlwaysStoppedAnimation(confColor),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
