import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../config/theme.dart';
import '../providers/mcp_widget_provider.dart';
import '../mcp_widgets/customer_360_widget.dart';
import '../mcp_widgets/loan_processing_widget.dart';
import '../mcp_widgets/compliance_dashboard_widget.dart';

class CopilotScreen extends StatefulWidget {
  const CopilotScreen({super.key});

  @override
  State<CopilotScreen> createState() => _CopilotScreenState();
}

class _CopilotScreenState extends State<CopilotScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  final _tabs = const [
    Tab(icon: Icon(Icons.person_search), text: 'Customer 360'),
    Tab(icon: Icon(Icons.assignment), text: 'Loan Processing'),
    Tab(icon: Icon(Icons.shield), text: 'Compliance'),
  ];

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: _tabs.length, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
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
              Icon(Icons.support_agent, color: AppTheme.primaryBlue, size: 28),
              const SizedBox(width: 12),
              Text(
                'Branch Co-Pilot',
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
              ),
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: AppTheme.warningAmber.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  'Employee-Facing',
                  style: TextStyle(
                    color: AppTheme.warningAmber,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
          ),
        ),
        // Tabs
        Container(
          color: Theme.of(context).cardColor,
          child: TabBar(
            controller: _tabController,
            tabs: _tabs,
            indicatorColor: AppTheme.primaryBlue,
            labelColor: AppTheme.primaryBlue,
            unselectedLabelColor: Theme.of(context)
                .textTheme
                .bodyMedium
                ?.color
                ?.withValues(alpha: 0.6),
          ),
        ),
        // Content
        Expanded(
          child: TabBarView(
            controller: _tabController,
            children: const [
              Customer360Widget(),
              LoanProcessingWidget(),
              ComplianceDashboardWidget(),
            ],
          ),
        ),
      ],
    );
  }
}
