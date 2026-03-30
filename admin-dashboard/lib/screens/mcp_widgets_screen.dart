import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../config/theme.dart';
import '../providers/mcp_widget_provider.dart';
import '../mcp_widgets/account_summary_widget.dart';
import '../mcp_widgets/transaction_table_widget.dart';
import '../mcp_widgets/emi_calculator_widget.dart';
import '../mcp_widgets/card_management_widget.dart';
import '../mcp_widgets/payment_form_widget.dart';
import '../mcp_widgets/fd_creation_widget.dart';
import '../mcp_widgets/dispute_form_widget.dart';
import '../mcp_widgets/reward_catalog_widget.dart';

class McpWidgetsScreen extends StatefulWidget {
  const McpWidgetsScreen({super.key});

  @override
  State<McpWidgetsScreen> createState() => _McpWidgetsScreenState();
}

class _McpWidgetsScreenState extends State<McpWidgetsScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final _customerIdController = TextEditingController(text: 'CUST001');

  final _tabs = const [
    Tab(icon: Icon(Icons.account_balance), text: 'Account'),
    Tab(icon: Icon(Icons.receipt_long), text: 'Transactions'),
    Tab(icon: Icon(Icons.credit_card), text: 'Card'),
    Tab(icon: Icon(Icons.calculate), text: 'EMI Calc'),
    Tab(icon: Icon(Icons.send), text: 'Payment'),
    Tab(icon: Icon(Icons.savings), text: 'Create FD'),
    Tab(icon: Icon(Icons.report_problem), text: 'Dispute'),
    Tab(icon: Icon(Icons.card_giftcard), text: 'Rewards'),
  ];

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: _tabs.length, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    _customerIdController.dispose();
    super.dispose();
  }

  void _updateCustomerId() {
    final provider = context.read<McpWidgetProvider>();
    provider.setCustomerId(_customerIdController.text.trim());
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        // Header
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: Theme.of(context).cardColor,
            border: Border(
              bottom: BorderSide(color: Theme.of(context).dividerColor),
            ),
          ),
          child: Row(
            children: [
              Icon(Icons.widgets, color: AppTheme.primaryBlue, size: 28),
              const SizedBox(width: 12),
              Text(
                'MCP UI Widgets',
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
              ),
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: AppTheme.primaryBlue.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  'Customer-Facing',
                  style: TextStyle(
                    color: AppTheme.primaryBlue,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              const Spacer(),
              // Customer ID input
              SizedBox(
                width: 200,
                child: TextField(
                  controller: _customerIdController,
                  decoration: InputDecoration(
                    labelText: 'Customer ID',
                    prefixIcon: const Icon(Icons.person, size: 20),
                    suffixIcon: IconButton(
                      icon: const Icon(Icons.search, size: 20),
                      onPressed: _updateCustomerId,
                    ),
                    isDense: true,
                    contentPadding: const EdgeInsets.symmetric(
                        horizontal: 12, vertical: 10),
                  ),
                  onSubmitted: (_) => _updateCustomerId(),
                ),
              ),
            ],
          ),
        ),
        // Tab bar
        Container(
          color: Theme.of(context).cardColor,
          child: TabBar(
            controller: _tabController,
            tabs: _tabs,
            isScrollable: true,
            indicatorColor: AppTheme.primaryBlue,
            labelColor: AppTheme.primaryBlue,
            unselectedLabelColor: Theme.of(context)
                .textTheme
                .bodyMedium
                ?.color
                ?.withValues(alpha: 0.6),
            tabAlignment: TabAlignment.start,
          ),
        ),
        // Tab content
        Expanded(
          child: TabBarView(
            controller: _tabController,
            children: const [
              AccountSummaryWidget(),
              TransactionTableWidget(),
              CardManagementWidget(),
              EMICalculatorWidget(),
              PaymentFormWidget(),
              FdCreationWidget(),
              DisputeFormWidget(),
              RewardCatalogWidget(),
            ],
          ),
        ),
      ],
    );
  }
}
