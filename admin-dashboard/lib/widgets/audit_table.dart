import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';

import '../config/theme.dart';
import '../models/audit_event_model.dart';

class AuditTable extends StatelessWidget {
  final List<AuditEventModel> events;
  final bool compact;
  final VoidCallback? onViewAll;

  const AuditTable({
    super.key,
    required this.events,
    this.compact = false,
    this.onViewAll,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Recent Events',
                  style: GoogleFonts.inter(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                if (onViewAll != null)
                  TextButton(
                    onPressed: onViewAll,
                    child: Text(
                      'View All',
                      style: GoogleFonts.inter(
                        fontSize: 13,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 12),
            if (events.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 32),
                child: Center(
                  child: Text(
                    'No recent events',
                    style: GoogleFonts.inter(
                      color: isDark
                          ? AppTheme.darkTextSecondary
                          : AppTheme.lightTextSecondary,
                    ),
                  ),
                ),
              )
            else
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: DataTable(
                  columnSpacing: 24,
                  headingRowHeight: 40,
                  dataRowMinHeight: 44,
                  dataRowMaxHeight: 44,
                  headingTextStyle: GoogleFonts.inter(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: isDark
                        ? AppTheme.darkTextSecondary
                        : AppTheme.lightTextSecondary,
                  ),
                  columns: [
                    const DataColumn(label: Text('TIME')),
                    const DataColumn(label: Text('AGENT')),
                    const DataColumn(label: Text('ACTION')),
                    const DataColumn(label: Text('STATUS')),
                    if (!compact) const DataColumn(label: Text('USER')),
                    if (!compact)
                      const DataColumn(
                          label: Text('DURATION'), numeric: true),
                  ],
                  rows: events.map((event) {
                    final dt = DateTime.tryParse(event.timestamp);
                    final timeStr = dt != null
                        ? DateFormat.Hms().format(dt)
                        : event.timestamp;

                    return DataRow(
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
                        DataCell(
                          Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 8, vertical: 3),
                            decoration: BoxDecoration(
                              color: AppTheme.primaryBlue
                                  .withValues(alpha: 0.1),
                              borderRadius: BorderRadius.circular(4),
                            ),
                            child: Text(
                              event.action,
                              style: GoogleFonts.jetBrainsMono(
                                fontSize: 11,
                                fontWeight: FontWeight.w500,
                                color: AppTheme.primaryBlue,
                              ),
                            ),
                          ),
                        ),
                        DataCell(_StatusBadge(status: event.status)),
                        if (!compact)
                          DataCell(Text(
                            event.userId ?? '-',
                            style: GoogleFonts.inter(fontSize: 12),
                          )),
                        if (!compact)
                          DataCell(Text(
                            event.durationMs != null
                                ? '${event.durationMs}ms'
                                : '-',
                            style: GoogleFonts.jetBrainsMono(fontSize: 12),
                          )),
                      ],
                    );
                  }).toList(),
                ),
              ),
          ],
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
