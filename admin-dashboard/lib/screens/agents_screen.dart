import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:provider/provider.dart';

import '../config/theme.dart';
import '../models/agent_model.dart';
import '../providers/agent_provider.dart';

class AgentsScreen extends StatefulWidget {
  const AgentsScreen({super.key});

  @override
  State<AgentsScreen> createState() => _AgentsScreenState();
}

class _AgentsScreenState extends State<AgentsScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<AgentProvider>().loadAgents();
    });
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Consumer<AgentProvider>(
      builder: (context, provider, _) {
        return Scaffold(
          backgroundColor: Theme.of(context).scaffoldBackgroundColor,
          body: provider.isLoading && provider.agents.isEmpty
              ? const Center(child: CircularProgressIndicator())
              : Row(
                  children: [
                    // Agent list
                    Expanded(
                      flex: provider.selectedAgent != null ? 5 : 10,
                      child: SingleChildScrollView(
                        padding: const EdgeInsets.all(24),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            _buildHeader(context, provider, isDark),
                            const SizedBox(height: 16),
                            _buildStatusSummary(provider, isDark),
                            const SizedBox(height: 20),
                            _buildAgentList(context, provider, isDark),
                          ],
                        ),
                      ),
                    ),
                    // Detail panel
                    if (provider.selectedAgent != null) ...[
                      VerticalDivider(
                        width: 1,
                        color:
                            isDark ? AppTheme.darkBorder : AppTheme.lightBorder,
                      ),
                      Expanded(
                        flex: 5,
                        child: _AgentDetailPanel(
                          agent: provider.selectedAgent!,
                          onClose: () => provider.selectAgent(null),
                          isDark: isDark,
                        ),
                      ),
                    ],
                  ],
                ),
        );
      },
    );
  }

  Widget _buildHeader(
      BuildContext context, AgentProvider provider, bool isDark) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Agents',
              style: GoogleFonts.inter(
                fontSize: 24,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              '${provider.agents.length} agents configured',
              style: GoogleFonts.inter(
                fontSize: 14,
                color: isDark
                    ? AppTheme.darkTextSecondary
                    : AppTheme.lightTextSecondary,
              ),
            ),
          ],
        ),
        ElevatedButton.icon(
          onPressed: () => _showCreateAgentDialog(context),
          icon: const Icon(Icons.add, size: 18),
          label: const Text('New Agent'),
        ),
      ],
    );
  }

  Widget _buildStatusSummary(AgentProvider provider, bool isDark) {
    return Row(
      children: [
        _StatusChip(
          label: 'Running',
          count: provider.runningCount,
          color: AppTheme.successGreen,
          isDark: isDark,
        ),
        const SizedBox(width: 12),
        _StatusChip(
          label: 'Stopped',
          count: provider.stoppedCount,
          color: AppTheme.darkTextSecondary,
          isDark: isDark,
        ),
        const SizedBox(width: 12),
        _StatusChip(
          label: 'Error',
          count: provider.errorCount,
          color: AppTheme.errorRed,
          isDark: isDark,
        ),
      ],
    );
  }

  Widget _buildAgentList(
      BuildContext context, AgentProvider provider, bool isDark) {
    return Card(
      child: Column(
        children: [
          // Table header
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
            decoration: BoxDecoration(
              color: isDark ? AppTheme.darkSurface : AppTheme.lightSurface,
              borderRadius:
                  const BorderRadius.vertical(top: Radius.circular(12)),
            ),
            child: Row(
              children: [
                Expanded(
                    flex: 3,
                    child: Text('AGENT',
                        style: _headerStyle(isDark))),
                Expanded(
                    flex: 2,
                    child:
                        Text('TYPE', style: _headerStyle(isDark))),
                Expanded(
                    flex: 2,
                    child: Text('STATUS',
                        style: _headerStyle(isDark))),
                Expanded(
                    flex: 1,
                    child: Text('INST.',
                        style: _headerStyle(isDark),
                        textAlign: TextAlign.center)),
                Expanded(
                    flex: 2,
                    child: Text('RESPONSE',
                        style: _headerStyle(isDark),
                        textAlign: TextAlign.center)),
                Expanded(
                    flex: 2,
                    child: Text('SUCCESS',
                        style: _headerStyle(isDark),
                        textAlign: TextAlign.center)),
                Expanded(
                    flex: 2,
                    child: Text('ACTIONS',
                        style: _headerStyle(isDark),
                        textAlign: TextAlign.center)),
              ],
            ),
          ),
          // Agent rows
          ...provider.agents.map((agent) => _buildAgentRow(
                context, provider, agent, isDark)),
        ],
      ),
    );
  }

  TextStyle _headerStyle(bool isDark) {
    return GoogleFonts.inter(
      fontSize: 11,
      fontWeight: FontWeight.w600,
      letterSpacing: 0.5,
      color: isDark ? AppTheme.darkTextSecondary : AppTheme.lightTextSecondary,
    );
  }

  Widget _buildAgentRow(BuildContext context, AgentProvider provider,
      AgentModel agent, bool isDark) {
    final statusColor = _statusColor(agent.status);
    final isSelected = provider.selectedAgent?.id == agent.id;

    return InkWell(
      onTap: () => provider.selectAgent(agent),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
        decoration: BoxDecoration(
          color: isSelected
              ? AppTheme.primaryBlue.withValues(alpha: 0.06)
              : Colors.transparent,
          border: Border(
            bottom: BorderSide(
              color: isDark ? AppTheme.darkBorder : AppTheme.lightBorder,
              width: 0.5,
            ),
          ),
        ),
        child: Row(
          children: [
            Expanded(
              flex: 3,
              child: Text(
                agent.name,
                style: GoogleFonts.inter(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                ),
                overflow: TextOverflow.ellipsis,
              ),
            ),
            Expanded(
              flex: 2,
              child: Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: AppTheme.primaryBlue.withValues(alpha: 0.08),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(
                  agent.type.toUpperCase(),
                  style: GoogleFonts.inter(
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                    color: AppTheme.primaryBlue,
                  ),
                  textAlign: TextAlign.center,
                ),
              ),
            ),
            Expanded(
              flex: 2,
              child: Row(
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
                    agent.status.value,
                    style: GoogleFonts.inter(
                      fontSize: 12,
                      fontWeight: FontWeight.w500,
                      color: statusColor,
                    ),
                  ),
                ],
              ),
            ),
            Expanded(
              flex: 1,
              child: Text(
                agent.activeInstances.toString(),
                style: GoogleFonts.inter(fontSize: 13),
                textAlign: TextAlign.center,
              ),
            ),
            Expanded(
              flex: 2,
              child: Text(
                '${agent.avgResponseTime.toStringAsFixed(2)}s',
                style: GoogleFonts.jetBrainsMono(fontSize: 12),
                textAlign: TextAlign.center,
              ),
            ),
            Expanded(
              flex: 2,
              child: Text(
                '${agent.successRate.toStringAsFixed(1)}%',
                style: GoogleFonts.jetBrainsMono(
                  fontSize: 12,
                  color: agent.successRate >= 95
                      ? AppTheme.successGreen
                      : agent.successRate >= 90
                          ? AppTheme.warningAmber
                          : AppTheme.errorRed,
                ),
                textAlign: TextAlign.center,
              ),
            ),
            Expanded(
              flex: 2,
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  if (agent.status == AgentStatus.stopped)
                    _ActionButton(
                      icon: Icons.play_arrow,
                      color: AppTheme.successGreen,
                      tooltip: 'Start',
                      onPressed: () => provider.startAgent(agent.id),
                    ),
                  if (agent.status == AgentStatus.running ||
                      agent.status == AgentStatus.degraded)
                    _ActionButton(
                      icon: Icons.stop,
                      color: AppTheme.errorRed,
                      tooltip: 'Stop',
                      onPressed: () => provider.stopAgent(agent.id),
                    ),
                  if (agent.status == AgentStatus.running ||
                      agent.status == AgentStatus.degraded)
                    _ActionButton(
                      icon: Icons.restart_alt,
                      color: AppTheme.warningAmber,
                      tooltip: 'Restart',
                      onPressed: () => provider.restartAgent(agent.id),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

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

  void _showCreateAgentDialog(BuildContext context) {
    final nameController = TextEditingController();
    final typeController = TextEditingController();

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('Create New Agent',
            style: GoogleFonts.inter(fontWeight: FontWeight.w600)),
        content: SizedBox(
          width: 400,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: nameController,
                decoration: const InputDecoration(
                  labelText: 'Agent Name',
                  hintText: 'e.g. Customer Support Agent',
                ),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: typeController,
                decoration: const InputDecoration(
                  labelText: 'Agent Type',
                  hintText: 'e.g. inquiry, transaction, card',
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () {
              if (nameController.text.isNotEmpty) {
                context.read<AgentProvider>().createAgent({
                  'name': nameController.text,
                  'type': typeController.text,
                });
                Navigator.pop(ctx);
              }
            },
            child: const Text('Create'),
          ),
        ],
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  final String label;
  final int count;
  final Color color;
  final bool isDark;

  const _StatusChip({
    required this.label,
    required this.count,
    required this.color,
    required this.isDark,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: color.withValues(alpha: 0.25)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 8,
            height: 8,
            decoration: BoxDecoration(color: color, shape: BoxShape.circle),
          ),
          const SizedBox(width: 8),
          Text(
            '$count $label',
            style: GoogleFonts.inter(
              fontSize: 13,
              fontWeight: FontWeight.w500,
              color: color,
            ),
          ),
        ],
      ),
    );
  }
}

class _ActionButton extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String tooltip;
  final VoidCallback onPressed;

  const _ActionButton({
    required this.icon,
    required this.color,
    required this.tooltip,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip,
      child: InkWell(
        onTap: onPressed,
        borderRadius: BorderRadius.circular(6),
        child: Padding(
          padding: const EdgeInsets.all(4),
          child: Icon(icon, size: 18, color: color),
        ),
      ),
    );
  }
}

class _AgentDetailPanel extends StatelessWidget {
  final AgentModel agent;
  final VoidCallback onClose;
  final bool isDark;

  const _AgentDetailPanel({
    required this.agent,
    required this.onClose,
    required this.isDark,
  });

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Agent Details',
                style: GoogleFonts.inter(
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                ),
              ),
              IconButton(
                icon: const Icon(Icons.close),
                onPressed: onClose,
              ),
            ],
          ),
          const SizedBox(height: 20),
          Text(
            agent.name,
            style: GoogleFonts.inter(
              fontSize: 20,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 4),
          if (agent.description != null)
            Text(
              agent.description!,
              style: GoogleFonts.inter(
                fontSize: 13,
                color: isDark
                    ? AppTheme.darkTextSecondary
                    : AppTheme.lightTextSecondary,
              ),
            ),
          const SizedBox(height: 24),

          // Metrics
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Performance Metrics',
                      style: GoogleFonts.inter(
                          fontSize: 14, fontWeight: FontWeight.w600)),
                  const SizedBox(height: 16),
                  _DetailRow('Active Instances',
                      agent.activeInstances.toString(), isDark),
                  _DetailRow('Avg Response Time',
                      '${agent.avgResponseTime.toStringAsFixed(2)}s', isDark),
                  _DetailRow('Success Rate',
                      '${agent.successRate.toStringAsFixed(1)}%', isDark),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // Configuration
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Configuration',
                      style: GoogleFonts.inter(
                          fontSize: 14, fontWeight: FontWeight.w600)),
                  const SizedBox(height: 16),
                  _DetailRow('Type', agent.type.toUpperCase(), isDark),
                  _DetailRow('Status', agent.status.value.toUpperCase(), isDark),
                  _DetailRow(
                      'LLM Provider', agent.llmProvider ?? 'N/A', isDark),
                  _DetailRow('Model', agent.llmModel ?? 'N/A', isDark),
                  if (agent.lastActive != null)
                    _DetailRow(
                        'Last Active',
                        _formatDateTime(agent.lastActive!),
                        isDark),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // Actions
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () {
                    // Edit agent configuration
                  },
                  icon: const Icon(Icons.edit, size: 16),
                  label: const Text('Edit Config'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () {
                    context.read<AgentProvider>().deleteAgent(agent.id);
                  },
                  icon: const Icon(Icons.delete, size: 16),
                  label: const Text('Delete'),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: AppTheme.errorRed,
                    side: const BorderSide(color: AppTheme.errorRed),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  String _formatDateTime(DateTime dt) {
    final diff = DateTime.now().difference(dt);
    if (diff.inSeconds < 60) return '${diff.inSeconds}s ago';
    if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
    if (diff.inHours < 24) return '${diff.inHours}h ago';
    return '${diff.inDays}d ago';
  }
}

class _DetailRow extends StatelessWidget {
  final String label;
  final String value;
  final bool isDark;

  const _DetailRow(this.label, this.value, this.isDark);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: GoogleFonts.inter(
              fontSize: 13,
              color: isDark
                  ? AppTheme.darkTextSecondary
                  : AppTheme.lightTextSecondary,
            ),
          ),
          Text(
            value,
            style: GoogleFonts.inter(
              fontSize: 13,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}
