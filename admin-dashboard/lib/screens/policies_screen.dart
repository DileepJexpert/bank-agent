import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';

import '../config/theme.dart';
import '../models/policy_model.dart';
import '../providers/policy_provider.dart';

class PoliciesScreen extends StatefulWidget {
  const PoliciesScreen({super.key});

  @override
  State<PoliciesScreen> createState() => _PoliciesScreenState();
}

class _PoliciesScreenState extends State<PoliciesScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<PolicyProvider>().loadPolicies();
    });
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Consumer<PolicyProvider>(
      builder: (context, provider, _) {
        return Scaffold(
          backgroundColor: Theme.of(context).scaffoldBackgroundColor,
          body: provider.isLoading && provider.policies.isEmpty
              ? const Center(child: CircularProgressIndicator())
              : Row(
                  children: [
                    // Policy list
                    Expanded(
                      flex: provider.selectedPolicy != null ? 4 : 10,
                      child: SingleChildScrollView(
                        padding: const EdgeInsets.all(24),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            _buildHeader(context, provider, isDark),
                            const SizedBox(height: 16),
                            _buildSummary(provider, isDark),
                            const SizedBox(height: 20),
                            ...provider.policies.map((policy) =>
                                _buildPolicyCard(
                                    context, provider, policy, isDark)),
                          ],
                        ),
                      ),
                    ),
                    // Editor panel
                    if (provider.selectedPolicy != null) ...[
                      VerticalDivider(
                        width: 1,
                        color:
                            isDark ? AppTheme.darkBorder : AppTheme.lightBorder,
                      ),
                      Expanded(
                        flex: 6,
                        child: _PolicyEditorPanel(
                          policy: provider.selectedPolicy!,
                          testResult: provider.testResult,
                          onClose: () => provider.selectPolicy(null),
                          onTest: (input) => provider.testPolicy(
                              provider.selectedPolicy!.id, input),
                          onSave: (code) {
                            final updated =
                                provider.selectedPolicy!.copyWith(
                              regoCode: code,
                              lastUpdated: DateTime.now(),
                            );
                            provider.updatePolicy(
                                provider.selectedPolicy!.id, updated);
                          },
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
      BuildContext context, PolicyProvider provider, bool isDark) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'OPA Policies',
              style: GoogleFonts.inter(
                fontSize: 24,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              'Manage Rego policies for agent governance',
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
          onPressed: () => _showCreatePolicyDialog(context),
          icon: const Icon(Icons.add, size: 18),
          label: const Text('New Policy'),
        ),
      ],
    );
  }

  Widget _buildSummary(PolicyProvider provider, bool isDark) {
    return Row(
      children: [
        _SummaryChip(
          label: 'Active',
          count: provider.activeCount,
          color: AppTheme.successGreen,
          isDark: isDark,
        ),
        const SizedBox(width: 12),
        _SummaryChip(
          label: 'Inactive',
          count: provider.inactiveCount,
          color: AppTheme.darkTextSecondary,
          isDark: isDark,
        ),
        const SizedBox(width: 12),
        _SummaryChip(
          label: 'Total',
          count: provider.policies.length,
          color: AppTheme.primaryBlue,
          isDark: isDark,
        ),
      ],
    );
  }

  Widget _buildPolicyCard(BuildContext context, PolicyProvider provider,
      PolicyModel policy, bool isDark) {
    final isSelected = provider.selectedPolicy?.id == policy.id;

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Card(
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
          side: BorderSide(
            color: isSelected
                ? AppTheme.primaryBlue
                : (isDark ? AppTheme.darkBorder : AppTheme.lightBorder),
            width: isSelected ? 2 : 1,
          ),
        ),
        child: InkWell(
          onTap: () => provider.selectPolicy(policy),
          borderRadius: BorderRadius.circular(12),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            policy.name,
                            style: GoogleFonts.inter(
                              fontSize: 15,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          if (policy.description != null) ...[
                            const SizedBox(height: 4),
                            Text(
                              policy.description!,
                              style: GoogleFonts.inter(
                                fontSize: 13,
                                color: isDark
                                    ? AppTheme.darkTextSecondary
                                    : AppTheme.lightTextSecondary,
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                    Switch(
                      value: policy.active,
                      onChanged: (_) => provider.togglePolicy(policy.id),
                      activeColor: AppTheme.primaryBlue,
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    _PolicyTag(
                        label: policy.type.toUpperCase(), isDark: isDark),
                    const SizedBox(width: 8),
                    if (policy.version != null)
                      _PolicyTag(
                          label: 'v${policy.version}', isDark: isDark),
                    const Spacer(),
                    Text(
                      'Updated ${DateFormat.yMd().format(policy.lastUpdated)}',
                      style: GoogleFonts.inter(
                        fontSize: 11,
                        color: isDark
                            ? AppTheme.darkTextSecondary
                            : AppTheme.lightTextSecondary,
                      ),
                    ),
                    if (policy.author != null) ...[
                      const SizedBox(width: 8),
                      Text(
                        'by ${policy.author}',
                        style: GoogleFonts.inter(
                          fontSize: 11,
                          color: isDark
                              ? AppTheme.darkTextSecondary
                              : AppTheme.lightTextSecondary,
                        ),
                      ),
                    ],
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _showCreatePolicyDialog(BuildContext context) {
    final nameController = TextEditingController();
    final descController = TextEditingController();
    String selectedType = 'custom';

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          title: Text('Create New Policy',
              style: GoogleFonts.inter(fontWeight: FontWeight.w600)),
          content: SizedBox(
            width: 420,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: nameController,
                  decoration: const InputDecoration(
                    labelText: 'Policy Name',
                    hintText: 'e.g. Transaction Velocity Check',
                  ),
                ),
                const SizedBox(height: 16),
                DropdownButtonFormField<String>(
                  value: selectedType,
                  decoration: const InputDecoration(labelText: 'Policy Type'),
                  items: PolicyType.values
                      .map((t) => DropdownMenuItem(
                          value: t.value, child: Text(t.value)))
                      .toList(),
                  onChanged: (v) =>
                      setDialogState(() => selectedType = v ?? 'custom'),
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: descController,
                  decoration: const InputDecoration(
                    labelText: 'Description',
                    hintText: 'Describe what this policy does',
                  ),
                  maxLines: 2,
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
                  final policy = PolicyModel(
                    id: 'pol-${DateTime.now().millisecondsSinceEpoch}',
                    name: nameController.text,
                    type: selectedType,
                    regoCode: 'package banking.new_policy\n\ndefault allow = false\n\n',
                    active: false,
                    lastUpdated: DateTime.now(),
                    description: descController.text.isNotEmpty
                        ? descController.text
                        : null,
                    author: 'admin',
                    version: 1,
                  );
                  context.read<PolicyProvider>().createPolicy(policy);
                  Navigator.pop(ctx);
                }
              },
              child: const Text('Create'),
            ),
          ],
        ),
      ),
    );
  }
}

class _SummaryChip extends StatelessWidget {
  final String label;
  final int count;
  final Color color;
  final bool isDark;

  const _SummaryChip({
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
      ),
      child: Text(
        '$count $label',
        style: GoogleFonts.inter(
          fontSize: 13,
          fontWeight: FontWeight.w600,
          color: color,
        ),
      ),
    );
  }
}

class _PolicyTag extends StatelessWidget {
  final String label;
  final bool isDark;

  const _PolicyTag({required this.label, required this.isDark});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: isDark ? AppTheme.darkSurface : AppTheme.lightSurface,
        borderRadius: BorderRadius.circular(4),
        border: Border.all(
          color: isDark ? AppTheme.darkBorder : AppTheme.lightBorder,
        ),
      ),
      child: Text(
        label,
        style: GoogleFonts.jetBrainsMono(
          fontSize: 10,
          fontWeight: FontWeight.w600,
          color: isDark
              ? AppTheme.darkTextSecondary
              : AppTheme.lightTextSecondary,
        ),
      ),
    );
  }
}

class _PolicyEditorPanel extends StatefulWidget {
  final PolicyModel policy;
  final String? testResult;
  final VoidCallback onClose;
  final ValueChanged<Map<String, dynamic>> onTest;
  final ValueChanged<String> onSave;
  final bool isDark;

  const _PolicyEditorPanel({
    required this.policy,
    this.testResult,
    required this.onClose,
    required this.onTest,
    required this.onSave,
    required this.isDark,
  });

  @override
  State<_PolicyEditorPanel> createState() => _PolicyEditorPanelState();
}

class _PolicyEditorPanelState extends State<_PolicyEditorPanel> {
  late TextEditingController _codeController;
  final _testInputController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _codeController = TextEditingController(text: widget.policy.regoCode);
    _testInputController.text =
        '{\n  "amount": 100000,\n  "currency": "INR"\n}';
  }

  @override
  void didUpdateWidget(covariant _PolicyEditorPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.policy.id != widget.policy.id) {
      _codeController.text = widget.policy.regoCode;
    }
  }

  @override
  void dispose() {
    _codeController.dispose();
    _testInputController.dispose();
    super.dispose();
  }

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
                'Policy Editor',
                style: GoogleFonts.inter(
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                ),
              ),
              Row(
                children: [
                  OutlinedButton(
                    onPressed: () => widget.onSave(_codeController.text),
                    child: const Text('Save'),
                  ),
                  const SizedBox(width: 8),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: widget.onClose,
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            widget.policy.name,
            style: GoogleFonts.inter(
              fontSize: 14,
              color: widget.isDark
                  ? AppTheme.darkTextSecondary
                  : AppTheme.lightTextSecondary,
            ),
          ),
          const SizedBox(height: 20),

          // Rego code editor
          Text(
            'Rego Code',
            style: GoogleFonts.inter(
              fontSize: 13,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 8),
          Container(
            width: double.infinity,
            constraints: const BoxConstraints(minHeight: 300),
            decoration: BoxDecoration(
              color: widget.isDark
                  ? const Color(0xFF1A1D23)
                  : const Color(0xFFF5F5F5),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(
                color: widget.isDark
                    ? AppTheme.darkBorder
                    : AppTheme.lightBorder,
              ),
            ),
            child: TextField(
              controller: _codeController,
              maxLines: null,
              style: GoogleFonts.jetBrainsMono(
                fontSize: 13,
                height: 1.6,
                color: widget.isDark
                    ? const Color(0xFFD4D4D4)
                    : const Color(0xFF1E1E1E),
              ),
              decoration: InputDecoration(
                border: InputBorder.none,
                contentPadding: const EdgeInsets.all(16),
                hintText: 'Enter Rego policy code...',
                hintStyle: GoogleFonts.jetBrainsMono(
                  fontSize: 13,
                  color: widget.isDark
                      ? AppTheme.darkTextSecondary
                      : AppTheme.lightTextSecondary,
                ),
              ),
            ),
          ),
          const SizedBox(height: 24),

          // Test section
          Text(
            'Test Policy',
            style: GoogleFonts.inter(
              fontSize: 13,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 8),
          Container(
            width: double.infinity,
            height: 120,
            decoration: BoxDecoration(
              color: widget.isDark
                  ? const Color(0xFF1A1D23)
                  : const Color(0xFFF5F5F5),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(
                color: widget.isDark
                    ? AppTheme.darkBorder
                    : AppTheme.lightBorder,
              ),
            ),
            child: TextField(
              controller: _testInputController,
              maxLines: null,
              style: GoogleFonts.jetBrainsMono(
                fontSize: 12,
                color: widget.isDark
                    ? const Color(0xFFD4D4D4)
                    : const Color(0xFF1E1E1E),
              ),
              decoration: InputDecoration(
                border: InputBorder.none,
                contentPadding: const EdgeInsets.all(12),
                hintText: 'JSON test input...',
                hintStyle: GoogleFonts.jetBrainsMono(fontSize: 12),
              ),
            ),
          ),
          const SizedBox(height: 12),
          ElevatedButton.icon(
            onPressed: () {
              widget.onTest({'raw_input': _testInputController.text});
            },
            icon: const Icon(Icons.play_arrow, size: 18),
            label: const Text('Run Test'),
          ),

          if (widget.testResult != null) ...[
            const SizedBox(height: 16),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppTheme.successGreen.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(
                  color: AppTheme.successGreen.withValues(alpha: 0.25),
                ),
              ),
              child: Text(
                widget.testResult!,
                style: GoogleFonts.jetBrainsMono(
                  fontSize: 12,
                  color: AppTheme.successGreen,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
