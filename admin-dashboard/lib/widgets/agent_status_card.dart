import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../config/theme.dart';
import '../models/agent_model.dart';

class AgentStatusCard extends StatelessWidget {
  final AgentModel agent;
  final VoidCallback? onTap;

  const AgentStatusCard({
    super.key,
    required this.agent,
    this.onTap,
  });

  Color _statusColor(AgentStatus status) {
    switch (status) {
      case AgentStatus.running:
        return AppTheme.successGreen;
      case AgentStatus.stopped:
        return AppTheme.darkTextSecondary;
      case AgentStatus.error:
        return AppTheme.errorRed;
      case AgentStatus.starting:
        return AppTheme.warningAmber;
      case AgentStatus.degraded:
        return AppTheme.warningAmber;
    }
  }

  IconData _agentTypeIcon(String type) {
    switch (type) {
      case 'inquiry':
        return Icons.account_balance;
      case 'transaction':
        return Icons.swap_horiz;
      case 'card':
        return Icons.credit_card;
      case 'loan':
        return Icons.monetization_on;
      case 'kyc':
        return Icons.verified_user;
      case 'complaint':
        return Icons.support_agent;
      case 'faq':
        return Icons.help_outline;
      default:
        return Icons.smart_toy;
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final statusColor = _statusColor(agent.status);

    return Card(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: AppTheme.primaryBlue.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Icon(
                      _agentTypeIcon(agent.type),
                      color: AppTheme.primaryBlue,
                      size: 22,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          agent.name,
                          style: GoogleFonts.inter(
                            fontSize: 14,
                            fontWeight: FontWeight.w600,
                          ),
                          overflow: TextOverflow.ellipsis,
                        ),
                        const SizedBox(height: 2),
                        Text(
                          agent.type.toUpperCase(),
                          style: GoogleFonts.inter(
                            fontSize: 11,
                            fontWeight: FontWeight.w500,
                            color: isDark
                                ? AppTheme.darkTextSecondary
                                : AppTheme.lightTextSecondary,
                            letterSpacing: 0.5,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: statusColor.withValues(alpha: 0.12),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Container(
                          width: 7,
                          height: 7,
                          decoration: BoxDecoration(
                            color: statusColor,
                            shape: BoxShape.circle,
                          ),
                        ),
                        const SizedBox(width: 5),
                        Text(
                          agent.status.value.toUpperCase(),
                          style: GoogleFonts.inter(
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                            color: statusColor,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              Row(
                children: [
                  _MetricChip(
                    label: 'Instances',
                    value: agent.activeInstances.toString(),
                    isDark: isDark,
                  ),
                  const SizedBox(width: 12),
                  _MetricChip(
                    label: 'Avg Time',
                    value: '${agent.avgResponseTime.toStringAsFixed(2)}s',
                    isDark: isDark,
                  ),
                  const SizedBox(width: 12),
                  _MetricChip(
                    label: 'Success',
                    value: '${agent.successRate.toStringAsFixed(1)}%',
                    isDark: isDark,
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _MetricChip extends StatelessWidget {
  final String label;
  final String value;
  final bool isDark;

  const _MetricChip({
    required this.label,
    required this.value,
    required this.isDark,
  });

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 8),
        decoration: BoxDecoration(
          color: isDark
              ? AppTheme.darkSurface
              : AppTheme.lightSurface,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Column(
          children: [
            Text(
              value,
              style: GoogleFonts.inter(
                fontSize: 14,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              label,
              style: GoogleFonts.inter(
                fontSize: 10,
                color: isDark
                    ? AppTheme.darkTextSecondary
                    : AppTheme.lightTextSecondary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
