import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:fl_chart/fl_chart.dart';
import 'package:intl/intl.dart';
import '../providers/mcp_widget_provider.dart';
import '../models/mcp_widget_models.dart';
import '../config/theme.dart';

class TransactionTableWidget extends StatefulWidget {
  const TransactionTableWidget({super.key});

  @override
  State<TransactionTableWidget> createState() => _TransactionTableWidgetState();
}

class _TransactionTableWidgetState extends State<TransactionTableWidget> {
  final _currencyFormat = NumberFormat.currency(
    locale: 'en_IN',
    symbol: '\u20B9',
    decimalDigits: 2,
  );

  // Filter state
  DateTimeRange? _dateRange;
  String _typeFilter = 'All';
  String _categoryFilter = 'All';
  String _searchQuery = '';
  final TextEditingController _searchController = TextEditingController();

  // Sort state
  int _sortColumnIndex = 0;
  bool _sortAscending = false;

  // Pagination state
  int _currentPage = 0;
  static const int _rowsPerPage = 10;

  // Expanded row
  String? _expandedRowId;

  @override
  void initState() {
    super.initState();
    context.read<McpWidgetProvider>().loadTransactions();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  List<Transaction> _applyFilters(List<Transaction> transactions) {
    var filtered = List<Transaction>.from(transactions);

    // Date range filter
    if (_dateRange != null) {
      filtered = filtered.where((txn) {
        try {
          final txnDate = DateFormat('yyyy-MM-dd').parse(txn.date);
          return !txnDate.isBefore(_dateRange!.start) &&
              !txnDate.isAfter(_dateRange!.end);
        } catch (_) {
          return true;
        }
      }).toList();
    }

    // Type filter
    if (_typeFilter != 'All') {
      filtered = filtered.where((txn) {
        if (_typeFilter == 'Credit') return txn.isCredit;
        if (_typeFilter == 'Debit') return !txn.isCredit;
        return true;
      }).toList();
    }

    // Category filter
    if (_categoryFilter != 'All') {
      filtered =
          filtered.where((txn) => txn.category == _categoryFilter).toList();
    }

    // Search filter
    if (_searchQuery.isNotEmpty) {
      final query = _searchQuery.toLowerCase();
      filtered = filtered.where((txn) {
        return txn.merchant.toLowerCase().contains(query) ||
            txn.description.toLowerCase().contains(query);
      }).toList();
    }

    return filtered;
  }

  List<Transaction> _applySorting(List<Transaction> transactions) {
    final sorted = List<Transaction>.from(transactions);
    sorted.sort((a, b) {
      int cmp;
      switch (_sortColumnIndex) {
        case 0:
          cmp = a.date.compareTo(b.date);
          break;
        case 1:
          cmp = a.description.compareTo(b.description);
          break;
        case 2:
          cmp = a.merchant.compareTo(b.merchant);
          break;
        case 3:
          cmp = a.category.compareTo(b.category);
          break;
        case 4:
          cmp = a.amount.compareTo(b.amount);
          break;
        case 5:
          cmp = a.balance.compareTo(b.balance);
          break;
        default:
          cmp = 0;
      }
      return _sortAscending ? cmp : -cmp;
    });
    return sorted;
  }

  Map<String, double> _computeCategoryBreakdown(List<Transaction> txns) {
    final map = <String, double>{};
    for (final txn in txns) {
      if (!txn.isCredit && txn.category.isNotEmpty) {
        map[txn.category] = (map[txn.category] ?? 0) + txn.amount.abs();
      }
    }
    return map;
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<McpWidgetProvider>(
      builder: (context, provider, _) {
        if (provider.loading && provider.transactions.isEmpty) {
          return const Card(
            child: Padding(
              padding: EdgeInsets.all(48),
              child: Center(child: CircularProgressIndicator()),
            ),
          );
        }

        if (provider.error != null && provider.transactions.isEmpty) {
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
                    Text('Failed to load transactions',
                        style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 16),
                    ElevatedButton.icon(
                      onPressed: () => provider.loadTransactions(),
                      icon: const Icon(Icons.refresh, size: 18),
                      label: const Text('Retry'),
                    ),
                  ],
                ),
              ),
            ),
          );
        }

        final allTransactions = provider.transactions;
        final categories = <String>{'All'};
        for (final txn in allTransactions) {
          if (txn.category.isNotEmpty) categories.add(txn.category);
        }

        final filtered = _applyFilters(allTransactions);
        final sorted = _applySorting(filtered);

        final totalPages = (sorted.length / _rowsPerPage).ceil();
        final safeCurrentPage = _currentPage.clamp(0, totalPages > 0 ? totalPages - 1 : 0);
        final pageStart = safeCurrentPage * _rowsPerPage;
        final pageEnd =
            (pageStart + _rowsPerPage).clamp(0, sorted.length);
        final pageItems = sorted.sublist(pageStart, pageEnd);

        final categoryBreakdown = _computeCategoryBreakdown(filtered);

        return Card(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildHeaderRow(context),
                const SizedBox(height: 20),
                _buildFilterRow(context, categories.toList()),
                const SizedBox(height: 16),
                _buildDataTable(context, pageItems),
                const SizedBox(height: 12),
                _buildPagination(
                    context, safeCurrentPage, totalPages, sorted.length),
                if (categoryBreakdown.isNotEmpty) ...[
                  const Divider(height: 32),
                  _buildCategoryChart(context, categoryBreakdown),
                ],
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildHeaderRow(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      children: [
        Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: AppTheme.primaryBlue.withValues(alpha: 0.1),
            borderRadius: BorderRadius.circular(12),
          ),
          child: const Icon(Icons.receipt_long,
              color: AppTheme.primaryBlue, size: 24),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Transaction History',
                  style: theme.textTheme.titleMedium
                      ?.copyWith(fontWeight: FontWeight.w700)),
              const SizedBox(height: 2),
              Text('View and filter your account transactions',
                  style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurface
                          .withValues(alpha: 0.6))),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildFilterRow(BuildContext context, List<String> categories) {
    final theme = Theme.of(context);
    return Wrap(
      spacing: 12,
      runSpacing: 12,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        // Date range picker
        SizedBox(
          width: 220,
          child: OutlinedButton.icon(
            onPressed: () async {
              final picked = await showDateRangePicker(
                context: context,
                firstDate: DateTime(2020),
                lastDate: DateTime.now(),
                initialDateRange: _dateRange,
                builder: (context, child) {
                  return Theme(
                    data: theme.copyWith(
                      colorScheme: theme.colorScheme.copyWith(
                        primary: AppTheme.primaryBlue,
                      ),
                    ),
                    child: child!,
                  );
                },
              );
              if (picked != null) {
                setState(() {
                  _dateRange = picked;
                  _currentPage = 0;
                });
              }
            },
            icon: const Icon(Icons.date_range, size: 18),
            label: Text(
              _dateRange != null
                  ? '${DateFormat('dd MMM').format(_dateRange!.start)} - ${DateFormat('dd MMM').format(_dateRange!.end)}'
                  : 'Select Date Range',
              style: const TextStyle(fontSize: 12),
            ),
            style: OutlinedButton.styleFrom(
              padding:
                  const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            ),
          ),
        ),
        if (_dateRange != null)
          IconButton(
            onPressed: () => setState(() {
              _dateRange = null;
              _currentPage = 0;
            }),
            icon: const Icon(Icons.clear, size: 18),
            tooltip: 'Clear date filter',
            style: IconButton.styleFrom(
              foregroundColor: AppTheme.errorRed,
            ),
          ),

        // Type dropdown
        SizedBox(
          width: 130,
          child: DropdownButtonFormField<String>(
            value: _typeFilter,
            decoration: const InputDecoration(
              labelText: 'Type',
              contentPadding:
                  EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              isDense: true,
            ),
            items: ['All', 'Credit', 'Debit']
                .map((t) => DropdownMenuItem(value: t, child: Text(t)))
                .toList(),
            onChanged: (val) {
              if (val != null) {
                setState(() {
                  _typeFilter = val;
                  _currentPage = 0;
                });
              }
            },
          ),
        ),

        // Category dropdown
        SizedBox(
          width: 160,
          child: DropdownButtonFormField<String>(
            value: categories.contains(_categoryFilter)
                ? _categoryFilter
                : 'All',
            decoration: const InputDecoration(
              labelText: 'Category',
              contentPadding:
                  EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              isDense: true,
            ),
            items: categories
                .map((c) => DropdownMenuItem(value: c, child: Text(c)))
                .toList(),
            onChanged: (val) {
              if (val != null) {
                setState(() {
                  _categoryFilter = val;
                  _currentPage = 0;
                });
              }
            },
          ),
        ),

        // Search field
        SizedBox(
          width: 220,
          child: TextField(
            controller: _searchController,
            decoration: InputDecoration(
              hintText: 'Search merchant...',
              prefixIcon: const Icon(Icons.search, size: 18),
              suffixIcon: _searchQuery.isNotEmpty
                  ? IconButton(
                      icon: const Icon(Icons.clear, size: 18),
                      onPressed: () {
                        _searchController.clear();
                        setState(() {
                          _searchQuery = '';
                          _currentPage = 0;
                        });
                      },
                    )
                  : null,
              contentPadding:
                  const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              isDense: true,
            ),
            onChanged: (val) => setState(() {
              _searchQuery = val;
              _currentPage = 0;
            }),
          ),
        ),
      ],
    );
  }

  Widget _buildDataTable(BuildContext context, List<Transaction> pageItems) {
    final theme = Theme.of(context);

    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          minWidth: MediaQuery.of(context).size.width - 96,
        ),
        child: DataTable(
          sortColumnIndex: _sortColumnIndex,
          sortAscending: _sortAscending,
          headingRowHeight: 48,
          dataRowMinHeight: 48,
          dataRowMaxHeight: 120,
          columnSpacing: 24,
          columns: [
            DataColumn(
              label: const Text('Date'),
              onSort: (i, asc) =>
                  setState(() { _sortColumnIndex = i; _sortAscending = asc; }),
            ),
            DataColumn(
              label: const Text('Description'),
              onSort: (i, asc) =>
                  setState(() { _sortColumnIndex = i; _sortAscending = asc; }),
            ),
            DataColumn(
              label: const Text('Merchant'),
              onSort: (i, asc) =>
                  setState(() { _sortColumnIndex = i; _sortAscending = asc; }),
            ),
            DataColumn(
              label: const Text('Category'),
              onSort: (i, asc) =>
                  setState(() { _sortColumnIndex = i; _sortAscending = asc; }),
            ),
            DataColumn(
              label: const Text('Amount'),
              numeric: true,
              onSort: (i, asc) =>
                  setState(() { _sortColumnIndex = i; _sortAscending = asc; }),
            ),
            DataColumn(
              label: const Text('Balance'),
              numeric: true,
              onSort: (i, asc) =>
                  setState(() { _sortColumnIndex = i; _sortAscending = asc; }),
            ),
          ],
          rows: pageItems.map((txn) {
            final isExpanded = _expandedRowId == txn.id;
            final isCredit = txn.isCredit;
            final amountColor =
                isCredit ? AppTheme.successGreen : AppTheme.errorRed;
            final prefix = isCredit ? '+' : '-';

            return DataRow(
              selected: isExpanded,
              onSelectChanged: (_) {
                setState(() {
                  _expandedRowId = isExpanded ? null : txn.id;
                });
              },
              cells: [
                DataCell(Text(txn.date,
                    style: theme.textTheme.bodySmall)),
                DataCell(
                  SizedBox(
                    width: 200,
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(txn.description,
                            style: theme.textTheme.bodySmall
                                ?.copyWith(fontWeight: FontWeight.w600),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis),
                        if (isExpanded) ...[
                          const SizedBox(height: 8),
                          Text('ID: ${txn.id}',
                              style: theme.textTheme.bodySmall?.copyWith(
                                  color: theme.colorScheme.onSurface
                                      .withValues(alpha: 0.5),
                                  fontSize: 11)),
                          const SizedBox(height: 6),
                          SizedBox(
                            height: 30,
                            child: OutlinedButton.icon(
                              onPressed: () {},
                              icon: const Icon(Icons.flag_outlined, size: 14),
                              label: const Text('Raise Dispute',
                                  style: TextStyle(fontSize: 11)),
                              style: OutlinedButton.styleFrom(
                                foregroundColor: AppTheme.warningAmber,
                                side: const BorderSide(
                                    color: AppTheme.warningAmber),
                                padding: const EdgeInsets.symmetric(
                                    horizontal: 8, vertical: 0),
                              ),
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
                DataCell(Text(txn.merchant,
                    style: theme.textTheme.bodySmall)),
                DataCell(
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: AppTheme.primaryBlue.withValues(alpha: 0.08),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(txn.category,
                        style: theme.textTheme.labelSmall?.copyWith(
                            color: AppTheme.primaryBlue,
                            fontWeight: FontWeight.w500)),
                  ),
                ),
                DataCell(
                  Text(
                    '$prefix ${_currencyFormat.format(txn.amount.abs())}',
                    style: theme.textTheme.bodySmall?.copyWith(
                      fontWeight: FontWeight.w700,
                      color: amountColor,
                    ),
                  ),
                ),
                DataCell(Text(
                  _currencyFormat.format(txn.balance),
                  style: theme.textTheme.bodySmall
                      ?.copyWith(fontWeight: FontWeight.w500),
                )),
              ],
            );
          }).toList(),
        ),
      ),
    );
  }

  Widget _buildPagination(
      BuildContext context, int currentPage, int totalPages, int totalItems) {
    final theme = Theme.of(context);
    final start = totalItems == 0 ? 0 : currentPage * _rowsPerPage + 1;
    final end = ((currentPage + 1) * _rowsPerPage).clamp(0, totalItems);

    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          totalItems == 0
              ? 'No transactions found'
              : 'Showing $start-$end of $totalItems',
          style: theme.textTheme.bodySmall?.copyWith(
            color: theme.colorScheme.onSurface.withValues(alpha: 0.6),
          ),
        ),
        Row(
          children: [
            OutlinedButton.icon(
              onPressed: currentPage > 0
                  ? () => setState(() => _currentPage--)
                  : null,
              icon: const Icon(Icons.chevron_left, size: 18),
              label: const Text('Previous', style: TextStyle(fontSize: 12)),
              style: OutlinedButton.styleFrom(
                padding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              ),
            ),
            const SizedBox(width: 8),
            Container(
              padding:
                  const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
              decoration: BoxDecoration(
                color: AppTheme.primaryBlue.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(6),
              ),
              child: Text(
                totalPages == 0
                    ? '0 / 0'
                    : '${currentPage + 1} / $totalPages',
                style: theme.textTheme.labelMedium?.copyWith(
                  fontWeight: FontWeight.w600,
                  color: AppTheme.primaryBlue,
                ),
              ),
            ),
            const SizedBox(width: 8),
            OutlinedButton.icon(
              onPressed: currentPage < totalPages - 1
                  ? () => setState(() => _currentPage++)
                  : null,
              icon: const Icon(Icons.chevron_right, size: 18),
              label: const Text('Next', style: TextStyle(fontSize: 12)),
              style: OutlinedButton.styleFrom(
                padding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildCategoryChart(
      BuildContext context, Map<String, double> breakdown) {
    final theme = Theme.of(context);
    final entries = breakdown.entries.toList()
      ..sort((a, b) => b.value.compareTo(a.value));
    final total = entries.fold<double>(0, (sum, e) => sum + e.value);

    final colors = [
      AppTheme.primaryBlue,
      AppTheme.accentBlue,
      AppTheme.successGreen,
      AppTheme.warningAmber,
      AppTheme.errorRed,
      AppTheme.infoBlue,
      const Color(0xFF7C4DFF),
      const Color(0xFF00BFA5),
      const Color(0xFFFF6D00),
      const Color(0xFFAA00FF),
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Category Spending Breakdown',
            style: theme.textTheme.titleSmall
                ?.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: 16),
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              width: 180,
              height: 180,
              child: PieChart(
                PieChartData(
                  sectionsSpace: 2,
                  centerSpaceRadius: 36,
                  sections: entries.asMap().entries.map((entry) {
                    final idx = entry.key;
                    final cat = entry.value;
                    final pct = total > 0 ? (cat.value / total * 100) : 0;
                    return PieChartSectionData(
                      value: cat.value,
                      color: colors[idx % colors.length],
                      radius: 40,
                      title: '${pct.toStringAsFixed(0)}%',
                      titleStyle: const TextStyle(
                        fontSize: 10,
                        fontWeight: FontWeight.w700,
                        color: Colors.white,
                      ),
                    );
                  }).toList(),
                ),
              ),
            ),
            const SizedBox(width: 24),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: entries.asMap().entries.map((entry) {
                  final idx = entry.key;
                  final cat = entry.value;
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: Row(
                      children: [
                        Container(
                          width: 12,
                          height: 12,
                          decoration: BoxDecoration(
                            color: colors[idx % colors.length],
                            borderRadius: BorderRadius.circular(3),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(cat.key,
                              style: theme.textTheme.bodySmall
                                  ?.copyWith(fontWeight: FontWeight.w500)),
                        ),
                        Text(
                          _currencyFormat.format(cat.value),
                          style: theme.textTheme.bodySmall?.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    ),
                  );
                }).toList(),
              ),
            ),
          ],
        ),
      ],
    );
  }
}
