import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';

import '../config/theme.dart';
import '../models/audit_event_model.dart';
import '../services/api_service.dart';

class AuditScreen extends StatefulWidget {
  const AuditScreen({super.key});

  @override
  State<AuditScreen> createState() => _AuditScreenState();
}

class _AuditScreenState extends State<AuditScreen> {
  final ApiService _api = ApiService();

  List<AuditEventModel> _events = [];
  bool _isLoading = false;
  String _searchQuery = '';
  String _statusFilter = 'all';
  String _agentFilter = 'all';
  DateTimeRange? _dateRange;
  AuditEventModel? _selectedEvent;
  int _currentPage = 1;
  int _totalPages = 1;

  final _searchController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _loadEvents();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadEvents() async {
    setState(() => _isLoading = true);

    try {
      final data = await _api.getAuditEvents(
        page: _currentPage,
        search: _searchQuery.isNotEmpty ? _searchQuery : null,
        action: _statusFilter != 'all' ? _statusFilter : null,
        agentId: _agentFilter != 'all' ? _agentFilter : null,
        startDate: _dateRange?.start.toIso8601String(),
        endDate: _dateRange?.end.toIso8601String(),
      );
      final events = data['events'] as List<dynamic>? ?? [];
      _events = events
          .map((e) => AuditEventModel.fromJson(e as Map<String, dynamic>))
          .toList();
      _totalPages = data['total_pages'] as int? ?? 1;
    } catch (_) {
      _loadMockEvents();
    }

    setState(() => _isLoading = false);
  }

  void _loadMockEvents() {
    final actions = [
      'balance_check', 'fund_transfer', 'card_block', 'eligibility_check',
      'document_verify', 'statement_request', 'pin_change', 'complaint_filed',
      'account_open', 'kyc_update',
    ];
    final agents = [
      ('agent-001', 'Account Inquiry Agent'),
      ('agent-002', 'Transaction Agent'),
      ('agent-003', 'Card Services Agent'),
      ('agent-004', 'Loan Assistant Agent'),
      ('agent-005', 'KYC Verification Agent'),
    ];

    _events = List.generate(50, (i) {
      final agentIdx = i % agents.length;
      final actionIdx = i % actions.length;
      final isSuccess = i % 7 != 0;

      return AuditEventModel(
        id: 'evt-${1000 + i}',
        timestamp: DateTime.now()
            .subtract(Duration(minutes: i * 3 + i * 2))
            .toIso8601String(),
        agentId: agents[agentIdx].$1,
        agentName: agents[agentIdx].$2,
        action: actions[actionIdx],
        status: isSuccess ? 'success' : 'failure',
        userId: 'user-${(i * 137 + 1000) % 9999}',
        sessionId: 'sess-${(i * 251 + 100) % 9999}',
        durationMs: 200 + (i * 73 % 3000),
        detail: isSuccess ? null : 'Timeout connecting to downstream service',
      );
    });

    // Apply filters locally
    if (_searchQuery.isNotEmpty) {
      final q = _searchQuery.toLowerCase();
      _events = _events.where((e) =>
          e.agentName.toLowerCase().contains(q) ||
          e.action.toLowerCase().contains(q) ||
          (e.userId?.toLowerCase().contains(q) ?? false)).toList();
    }
    if (_statusFilter != 'all') {
      _events = _events.where((e) => e.status == _statusFilter).toList();
    }
    _totalPages = (_events.length / 25).ceil().clamp(1, 100);
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      body: Row(
        children: [
          Expanded(
            flex: _selectedEvent != null ? 6 : 10,
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildHeader(isDark),
                  const SizedBox(height: 20),
                  _buildFilters(context, isDark),
                  const SizedBox(height: 16),
                  _buildTable(isDark),
                  const SizedBox(height: 16),
                  _buildPagination(isDark),
                ],
              ),
            ),
          ),
          if (_selectedEvent != null) ...[
            VerticalDivider(
              width: 1,
              color: isDark ? AppTheme.darkBorder : AppTheme.lightBorder,
            ),
            Expanded(
              flex: 4,
              child: _EventDetailPanel(
                event: _selectedEvent!,
                onClose: () => setState(() => _selectedEvent = null),
                isDark: isDark,
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildHeader(bool isDark) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Audit Log',
              style: GoogleFonts.inter(
                fontSize: 24,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              '${_events.length} events found',
              style: GoogleFonts.inter(
                fontSize: 14,
                color: isDark
                    ? AppTheme.darkTextSecondary
                    : AppTheme.lightTextSecondary,
              ),
            ),
          ],
        ),
        OutlinedButton.icon(
          onPressed: () {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('Exporting audit log as CSV...')),
            );
          },
          icon: const Icon(Icons.download, size: 18),
          label: const Text('Export CSV'),
        ),
      ],
    );
  }

  Widget _buildFilters(BuildContext context, bool isDark) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            // Search
            Expanded(
              flex: 3,
              child: TextField(
                controller: _searchController,
                decoration: InputDecoration(
                  hintText: 'Search events...',
                  prefixIcon:
                      const Icon(Icons.search, size: 20),
                  suffixIcon: _searchQuery.isNotEmpty
                      ? IconButton(
                          icon: const Icon(Icons.clear, size: 18),
                          onPressed: () {
                            _searchController.clear();
                            setState(() => _searchQuery = '');
                            _loadEvents();
                          },
                        )
                      : null,
                  isDense: true,
                ),
                onSubmitted: (value) {
                  setState(() => _searchQuery = value);
                  _loadEvents();
                },
              ),
            ),
            const SizedBox(width: 12),

            // Status filter
            Expanded(
              flex: 2,
              child: DropdownButtonFormField<String>(
                value: _statusFilter,
                decoration: const InputDecoration(
                  labelText: 'Status',
                  isDense: true,
                ),
                items: const [
                  DropdownMenuItem(value: 'all', child: Text('All')),
                  DropdownMenuItem(value: 'success', child: Text('Success')),
                  DropdownMenuItem(value: 'failure', child: Text('Failure')),
                ],
                onChanged: (v) {
                  setState(() => _statusFilter = v ?? 'all');
                  _loadEvents();
                },
              ),
            ),
            const SizedBox(width: 12),

            // Date range picker
            Expanded(
              flex: 2,
              child: InkWell(
                onTap: () async {
                  final range = await showDateRangePicker(
                    context: context,
                    firstDate:
                        DateTime.now().subtract(const Duration(days: 365)),
                    lastDate: DateTime.now(),
                    initialDateRange: _dateRange,
                  );
                  if (range != null) {
                    setState(() => _dateRange = range);
                    _loadEvents();
                  }
                },
                child: InputDecorator(
                  decoration: const InputDecoration(
                    labelText: 'Date Range',
                    isDense: true,
                    suffixIcon: Icon(Icons.calendar_today, size: 16),
                  ),
                  child: Text(
                    _dateRange != null
                        ? '${DateFormat.MMMd().format(_dateRange!.start)} - ${DateFormat.MMMd().format(_dateRange!.end)}'
                        : 'All time',
                    style: GoogleFonts.inter(fontSize: 13),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTable(bool isDark) {
    if (_isLoading) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(48),
          child: CircularProgressIndicator(),
        ),
      );
    }

    return Card(
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: DataTable(
          columnSpacing: 20,
          headingRowHeight: 44,
          dataRowMinHeight: 48,
          dataRowMaxHeight: 48,
          headingTextStyle: GoogleFonts.inter(
            fontSize: 11,
            fontWeight: FontWeight.w600,
            letterSpacing: 0.5,
            color: isDark
                ? AppTheme.darkTextSecondary
                : AppTheme.lightTextSecondary,
          ),
          columns: const [
            DataColumn(label: Text('TIMESTAMP')),
            DataColumn(label: Text('AGENT')),
            DataColumn(label: Text('ACTION')),
            DataColumn(label: Text('STATUS')),
            DataColumn(label: Text('USER')),
            DataColumn(label: Text('SESSION')),
            DataColumn(label: Text('DURATION'), numeric: true),
          ],
          rows: _events.take(25).map((event) {
            final dt = DateTime.tryParse(event.timestamp);
            final timeStr =
                dt != null ? DateFormat('MMM d, HH:mm:ss').format(dt) : event.timestamp;

            return DataRow(
              selected: _selectedEvent?.id == event.id,
              onSelectChanged: (_) =>
                  setState(() => _selectedEvent = event),
              cells: [
                DataCell(Text(
                  timeStr,
                  style: GoogleFonts.jetBrainsMono(fontSize: 12),
                )),
                DataCell(Text(
                  event.agentName,
                  style: GoogleFonts.inter(
                    fontSize: 13,
                    fontWeight: FontWeight.w500,
                  ),
                )),
                DataCell(_ActionBadge(action: event.action)),
                DataCell(_StatusBadge(status: event.status)),
                DataCell(Text(
                  event.userId ?? '-',
                  style: GoogleFonts.inter(fontSize: 12),
                )),
                DataCell(Text(
                  event.sessionId ?? '-',
                  style: GoogleFonts.jetBrainsMono(fontSize: 11),
                )),
                DataCell(Text(
                  event.durationMs != null ? '${event.durationMs}ms' : '-',
                  style: GoogleFonts.jetBrainsMono(fontSize: 12),
                )),
              ],
            );
          }).toList(),
        ),
      ),
    );
  }

  Widget _buildPagination(bool isDark) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        IconButton(
          icon: const Icon(Icons.chevron_left),
          onPressed: _currentPage > 1
              ? () {
                  setState(() => _currentPage--);
                  _loadEvents();
                }
              : null,
        ),
        const SizedBox(width: 8),
        Text(
          'Page $_currentPage of $_totalPages',
          style: GoogleFonts.inter(fontSize: 13),
        ),
        const SizedBox(width: 8),
        IconButton(
          icon: const Icon(Icons.chevron_right),
          onPressed: _currentPage < _totalPages
              ? () {
                  setState(() => _currentPage++);
                  _loadEvents();
                }
              : null,
        ),
      ],
    );
  }
}

class _ActionBadge extends StatelessWidget {
  final String action;

  const _ActionBadge({required this.action});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: AppTheme.primaryBlue.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(4),
      ),
      child: Text(
        action,
        style: GoogleFonts.jetBrainsMono(
          fontSize: 11,
          fontWeight: FontWeight.w500,
          color: AppTheme.primaryBlue,
        ),
      ),
    );
  }
}

class _StatusBadge extends StatelessWidget {
  final String status;

  const _StatusBadge({required this.status});

  @override
  Widget build(BuildContext context) {
    final isSuccess = status.toLowerCase() == 'success';
    final color = isSuccess ? AppTheme.successGreen : AppTheme.errorRed;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(4),
      ),
      child: Text(
        status.toUpperCase(),
        style: GoogleFonts.inter(
          fontSize: 11,
          fontWeight: FontWeight.w600,
          color: color,
        ),
      ),
    );
  }
}

class _EventDetailPanel extends StatelessWidget {
  final AuditEventModel event;
  final VoidCallback onClose;
  final bool isDark;

  const _EventDetailPanel({
    required this.event,
    required this.onClose,
    required this.isDark,
  });

  @override
  Widget build(BuildContext context) {
    final dt = DateTime.tryParse(event.timestamp);

    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Event Details',
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

          _detailField('Event ID', event.id),
          _detailField(
              'Timestamp',
              dt != null
                  ? DateFormat('yyyy-MM-dd HH:mm:ss').format(dt)
                  : event.timestamp),
          _detailField('Agent', event.agentName),
          _detailField('Agent ID', event.agentId),
          _detailField('Action', event.action),
          _detailField('Status', event.status),
          if (event.userId != null) _detailField('User ID', event.userId!),
          if (event.sessionId != null)
            _detailField('Session ID', event.sessionId!),
          if (event.durationMs != null)
            _detailField('Duration', '${event.durationMs}ms'),
          if (event.detail != null) ...[
            const SizedBox(height: 16),
            Text(
              'Details',
              style: GoogleFonts.inter(
                fontSize: 13,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 8),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: isDark
                    ? AppTheme.darkSurface
                    : AppTheme.lightSurface,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(
                  color: isDark
                      ? AppTheme.darkBorder
                      : AppTheme.lightBorder,
                ),
              ),
              child: Text(
                event.detail!,
                style: GoogleFonts.jetBrainsMono(fontSize: 12),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _detailField(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 100,
            child: Text(
              label,
              style: GoogleFonts.inter(
                fontSize: 13,
                color: isDark
                    ? AppTheme.darkTextSecondary
                    : AppTheme.lightTextSecondary,
              ),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: GoogleFonts.inter(
                fontSize: 13,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
