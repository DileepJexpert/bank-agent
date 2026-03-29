import 'package:flutter/material.dart';
import 'package:fl_chart/fl_chart.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';

import '../config/theme.dart';
import '../models/dashboard_stats_model.dart';

class InteractionChart extends StatelessWidget {
  final List<InteractionDataPoint> data;
  final String title;

  const InteractionChart({
    super.key,
    required this.data,
    this.title = 'Interactions Over Time',
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
                  title,
                  style: GoogleFonts.inter(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                Row(
                  children: [
                    _LegendDot(
                      color: AppTheme.primaryBlue,
                      label: 'Interactions',
                      isDark: isDark,
                    ),
                    const SizedBox(width: 16),
                    _LegendDot(
                      color: AppTheme.successGreen,
                      label: 'Success Rate',
                      isDark: isDark,
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 24),
            SizedBox(
              height: 250,
              child: data.isEmpty
                  ? Center(
                      child: Text(
                        'No data available',
                        style: GoogleFonts.inter(
                          color: isDark
                              ? AppTheme.darkTextSecondary
                              : AppTheme.lightTextSecondary,
                        ),
                      ),
                    )
                  : LineChart(
                      LineChartData(
                        gridData: FlGridData(
                          show: true,
                          drawVerticalLine: false,
                          horizontalInterval: _calculateInterval(),
                          getDrawingHorizontalLine: (value) => FlLine(
                            color: isDark
                                ? AppTheme.darkBorder.withValues(alpha: 0.5)
                                : AppTheme.lightBorder,
                            strokeWidth: 1,
                          ),
                        ),
                        titlesData: FlTitlesData(
                          rightTitles: const AxisTitles(
                            sideTitles: SideTitles(showTitles: false),
                          ),
                          topTitles: const AxisTitles(
                            sideTitles: SideTitles(showTitles: false),
                          ),
                          bottomTitles: AxisTitles(
                            sideTitles: SideTitles(
                              showTitles: true,
                              reservedSize: 30,
                              interval: _xInterval(),
                              getTitlesWidget: (value, meta) {
                                final index = value.toInt();
                                if (index < 0 || index >= data.length) {
                                  return const SizedBox.shrink();
                                }
                                final dt =
                                    DateTime.tryParse(data[index].timestamp);
                                if (dt == null) {
                                  return const SizedBox.shrink();
                                }
                                return Padding(
                                  padding: const EdgeInsets.only(top: 8),
                                  child: Text(
                                    DateFormat.Hm().format(dt),
                                    style: GoogleFonts.inter(
                                      fontSize: 10,
                                      color: isDark
                                          ? AppTheme.darkTextSecondary
                                          : AppTheme.lightTextSecondary,
                                    ),
                                  ),
                                );
                              },
                            ),
                          ),
                          leftTitles: AxisTitles(
                            sideTitles: SideTitles(
                              showTitles: true,
                              reservedSize: 44,
                              interval: _calculateInterval(),
                              getTitlesWidget: (value, meta) {
                                return Text(
                                  _formatCount(value),
                                  style: GoogleFonts.inter(
                                    fontSize: 10,
                                    color: isDark
                                        ? AppTheme.darkTextSecondary
                                        : AppTheme.lightTextSecondary,
                                  ),
                                );
                              },
                            ),
                          ),
                        ),
                        borderData: FlBorderData(show: false),
                        lineBarsData: [
                          // Interaction count line
                          LineChartBarData(
                            spots: List.generate(
                              data.length,
                              (i) => FlSpot(
                                  i.toDouble(), data[i].count.toDouble()),
                            ),
                            isCurved: true,
                            curveSmoothness: 0.3,
                            color: AppTheme.primaryBlue,
                            barWidth: 2.5,
                            isStrokeCapRound: true,
                            dotData: const FlDotData(show: false),
                            belowBarData: BarAreaData(
                              show: true,
                              gradient: LinearGradient(
                                begin: Alignment.topCenter,
                                end: Alignment.bottomCenter,
                                colors: [
                                  AppTheme.primaryBlue.withValues(alpha: 0.2),
                                  AppTheme.primaryBlue.withValues(alpha: 0.0),
                                ],
                              ),
                            ),
                          ),
                        ],
                        lineTouchData: LineTouchData(
                          touchTooltipData: LineTouchTooltipData(
                            getTooltipItems: (touchedSpots) {
                              return touchedSpots.map((spot) {
                                final index = spot.x.toInt();
                                if (index < 0 || index >= data.length) {
                                  return null;
                                }
                                return LineTooltipItem(
                                  '${data[index].count} interactions\n${data[index].successRate.toStringAsFixed(1)}% success',
                                  GoogleFonts.inter(
                                    fontSize: 12,
                                    color: Colors.white,
                                    fontWeight: FontWeight.w500,
                                  ),
                                );
                              }).toList();
                            },
                          ),
                        ),
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }

  double _calculateInterval() {
    if (data.isEmpty) return 200;
    final maxVal =
        data.map((d) => d.count).reduce((a, b) => a > b ? a : b).toDouble();
    return (maxVal / 5).ceilToDouble();
  }

  double _xInterval() {
    if (data.length <= 6) return 1;
    return (data.length / 6).ceilToDouble();
  }

  String _formatCount(double value) {
    if (value >= 1000) {
      return '${(value / 1000).toStringAsFixed(1)}k';
    }
    return value.toInt().toString();
  }
}

class _LegendDot extends StatelessWidget {
  final Color color;
  final String label;
  final bool isDark;

  const _LegendDot({
    required this.color,
    required this.label,
    required this.isDark,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 8,
          height: 8,
          decoration: BoxDecoration(
            color: color,
            shape: BoxShape.circle,
          ),
        ),
        const SizedBox(width: 6),
        Text(
          label,
          style: GoogleFonts.inter(
            fontSize: 12,
            color: isDark
                ? AppTheme.darkTextSecondary
                : AppTheme.lightTextSecondary,
          ),
        ),
      ],
    );
  }
}
