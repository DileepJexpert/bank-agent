import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:provider/provider.dart';

import '../config/theme.dart';
import '../providers/dashboard_provider.dart';
import '../widgets/kpi_card.dart';
import '../widgets/agent_status_card.dart';
import '../widgets/interaction_chart.dart';
import '../widgets/audit_table.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<DashboardProvider>().loadDashboard();
    });
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Consumer<DashboardProvider>(
      builder: (context, provider, _) {
        return Scaffold(
          backgroundColor: Theme.of(context).scaffoldBackgroundColor,
          body: provider.isLoading && provider.stats.totalInteractions == 0
              ? const Center(child: CircularProgressIndicator())
              : RefreshIndicator(
                  onRefresh: provider.refresh,
                  child: SingleChildScrollView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // Header
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  'Dashboard',
                                  style: GoogleFonts.inter(
                                    fontSize: 24,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  'AI Agent Platform overview and metrics',
                                  style: GoogleFonts.inter(
                                    fontSize: 14,
                                    color: isDark
                                        ? AppTheme.darkTextSecondary
                                        : AppTheme.lightTextSecondary,
                                  ),
                                ),
                              ],
                            ),
                            Row(
                              children: [
                                _SystemHealthBadge(isDark: isDark),
                                const SizedBox(width: 12),
                                ElevatedButton.icon(
                                  onPressed: () => provider.refresh(),
                                  icon: const Icon(Icons.refresh, size: 18),
                                  label: const Text('Refresh'),
                                ),
                              ],
                            ),
                          ],
                        ),
                        const SizedBox(height: 24),

                        // KPI Cards
                        LayoutBuilder(
                          builder: (context, constraints) {
                            final crossAxisCount =
                                constraints.maxWidth > 1200
                                    ? 6
                                    : constraints.maxWidth > 800
                                        ? 3
                                        : 2;
                            return Wrap(
                              spacing: 16,
                              runSpacing: 16,
                              children: [
                                _kpiSizedBox(
                                  constraints, crossAxisCount,
                                  child: KpiCard(
                                    title: 'Total Interactions',
                                    value: _formatNumber(
                                        provider.stats.totalInteractions),
                                    icon: Icons.chat_bubble_outline,
                                    iconColor: AppTheme.primaryBlue,
                                    changePercent: 12.5,
                                    isPositiveChange: true,
                                    subtitle: 'vs last 24h',
                                  ),
                                ),
                                _kpiSizedBox(
                                  constraints, crossAxisCount,
                                  child: KpiCard(
                                    title: 'Active Agents',
                                    value:
                                        '${provider.stats.activeAgents}/${provider.stats.totalAgents}',
                                    icon: Icons.smart_toy_outlined,
                                    iconColor: AppTheme.successGreen,
                                    subtitle: 'agents online',
                                  ),
                                ),
                                _kpiSizedBox(
                                  constraints, crossAxisCount,
                                  child: KpiCard(
                                    title: 'Avg Response Time',
                                    value:
                                        '${provider.stats.avgResponseTime.toStringAsFixed(2)}s',
                                    icon: Icons.speed,
                                    iconColor: AppTheme.accentBlue,
                                    changePercent: 8.3,
                                    isPositiveChange: true,
                                    subtitle: 'improvement',
                                  ),
                                ),
                                _kpiSizedBox(
                                  constraints, crossAxisCount,
                                  child: KpiCard(
                                    title: 'Success Rate',
                                    value:
                                        '${provider.stats.successRate.toStringAsFixed(1)}%',
                                    icon: Icons.check_circle_outline,
                                    iconColor: AppTheme.successGreen,
                                    changePercent: 0.5,
                                    isPositiveChange: true,
                                    subtitle: 'vs last week',
                                  ),
                                ),
                                _kpiSizedBox(
                                  constraints, crossAxisCount,
                                  child: KpiCard(
                                    title: 'Cost / Interaction',
                                    value:
                                        '\$${provider.stats.costPerInteraction.toStringAsFixed(4)}',
                                    icon: Icons.attach_money,
                                    iconColor: AppTheme.warningAmber,
                                    changePercent: 3.2,
                                    isPositiveChange: true,
                                    subtitle: 'reduction',
                                  ),
                                ),
                                _kpiSizedBox(
                                  constraints, crossAxisCount,
                                  child: KpiCard(
                                    title: 'Tier 0 Resolution',
                                    value:
                                        '${provider.stats.tier0Percentage.toStringAsFixed(1)}%',
                                    icon: Icons.auto_awesome,
                                    iconColor: AppTheme.infoBlue,
                                    changePercent: 2.1,
                                    isPositiveChange: true,
                                    subtitle: 'automated resolution',
                                  ),
                                ),
                              ],
                            );
                          },
                        ),
                        const SizedBox(height: 24),

                        // Chart
                        InteractionChart(
                          data: provider.stats.interactionTimeline,
                        ),
                        const SizedBox(height: 24),

                        // Agent Status Grid
                        Text(
                          'Agent Status',
                          style: GoogleFonts.inter(
                            fontSize: 18,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const SizedBox(height: 12),
                        LayoutBuilder(
                          builder: (context, constraints) {
                            final crossAxisCount =
                                constraints.maxWidth > 1000
                                    ? 3
                                    : constraints.maxWidth > 600
                                        ? 2
                                        : 1;
                            return Wrap(
                              spacing: 16,
                              runSpacing: 16,
                              children: provider.agents.map((agent) {
                                final width =
                                    (constraints.maxWidth -
                                            (crossAxisCount - 1) * 16) /
                                        crossAxisCount;
                                return SizedBox(
                                  width: width,
                                  child: AgentStatusCard(agent: agent),
                                );
                              }).toList(),
                            );
                          },
                        ),
                        const SizedBox(height: 24),

                        // Recent Audit Events
                        AuditTable(
                          events: provider.recentEvents,
                          compact: true,
                        ),
                      ],
                    ),
                  ),
                ),
        );
      },
    );
  }

  Widget _kpiSizedBox(
    BoxConstraints constraints,
    int crossAxisCount, {
    required Widget child,
  }) {
    final width =
        (constraints.maxWidth - (crossAxisCount - 1) * 16) / crossAxisCount;
    return SizedBox(width: width, child: child);
  }

  String _formatNumber(int number) {
    if (number >= 1000000) {
      return '${(number / 1000000).toStringAsFixed(1)}M';
    }
    if (number >= 1000) {
      return '${(number / 1000).toStringAsFixed(1)}K';
    }
    return number.toString();
  }
}

class _SystemHealthBadge extends StatelessWidget {
  final bool isDark;

  const _SystemHealthBadge({required this.isDark});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: AppTheme.successGreen.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(
          color: AppTheme.successGreen.withValues(alpha: 0.3),
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 8,
            height: 8,
            decoration: const BoxDecoration(
              color: AppTheme.successGreen,
              shape: BoxShape.circle,
            ),
          ),
          const SizedBox(width: 8),
          Text(
            'System Healthy',
            style: GoogleFonts.inter(
              fontSize: 13,
              fontWeight: FontWeight.w500,
              color: AppTheme.successGreen,
            ),
          ),
        ],
      ),
    );
  }
}
