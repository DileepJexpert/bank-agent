import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../providers/mcp_widget_provider.dart';
import '../config/theme.dart';

class RewardCatalogWidget extends StatefulWidget {
  const RewardCatalogWidget({super.key});

  @override
  State<RewardCatalogWidget> createState() => _RewardCatalogWidgetState();
}

class _RewardCatalogWidgetState extends State<RewardCatalogWidget> {
  final _currencyFormat = NumberFormat.currency(
    locale: 'en_IN',
    symbol: '\u20B9',
    decimalDigits: 2,
  );

  String _selectedCategory = 'All';
  String _sortOption = 'Popular';
  bool _redeeming = false;

  static const List<String> _categories = [
    'All',
    'Shopping',
    'Entertainment',
    'Cashback',
    'Travel',
    'Dining',
    'Transport',
  ];

  static const Map<String, IconData> _categoryIcons = {
    'Shopping': Icons.shopping_bag,
    'Entertainment': Icons.movie,
    'Cashback': Icons.account_balance_wallet,
    'Travel': Icons.flight,
    'Dining': Icons.restaurant,
    'Transport': Icons.directions_car,
  };

  @override
  void initState() {
    super.initState();
    final provider = context.read<McpWidgetProvider>();
    provider.loadRewardPoints();
  }

  IconData _getIconForCategory(String category) {
    return _categoryIcons[category] ?? Icons.card_giftcard;
  }

  List<Map<String, dynamic>> _getFilteredCatalog(
      List<Map<String, dynamic>> catalog) {
    var filtered = _selectedCategory == 'All'
        ? catalog.where((item) => item['category'] != 'Cashback').toList()
        : catalog
            .where((item) => item['category'] == _selectedCategory)
            .toList();

    switch (_sortOption) {
      case 'Points: Low to High':
        filtered.sort((a, b) =>
            ((a['points'] as num?) ?? 0).compareTo((b['points'] as num?) ?? 0));
        break;
      case 'Points: High to Low':
        filtered.sort((a, b) =>
            ((b['points'] as num?) ?? 0).compareTo((a['points'] as num?) ?? 0));
        break;
      case 'Popular':
      default:
        break;
    }

    return filtered;
  }

  List<Map<String, dynamic>> _getCashbackItems(
      List<Map<String, dynamic>> catalog) {
    return catalog
        .where((item) => item['category'] == 'Cashback')
        .toList();
  }

  Future<void> _handleRedeem(
    McpWidgetProvider provider,
    Map<String, dynamic> item,
    int totalPoints,
  ) async {
    final itemPoints = (item['points'] as num?)?.toInt() ?? 0;
    final itemName = item['name']?.toString() ?? 'Item';
    final itemId = item['id']?.toString() ?? '';

    if (itemPoints > totalPoints) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Insufficient points. You need $itemPoints points.'),
          backgroundColor: AppTheme.errorRed,
        ),
      );
      return;
    }

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Confirm Redemption'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Are you sure you want to redeem:'),
            const SizedBox(height: 12),
            Row(
              children: [
                Icon(_getIconForCategory(
                    item['category']?.toString() ?? ''),
                    color: AppTheme.primaryBlue),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        itemName,
                        style: const TextStyle(fontWeight: FontWeight.w600),
                      ),
                      Text(
                        '$itemPoints points',
                        style: TextStyle(
                          fontSize: 13,
                          color: Colors.grey.shade600,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Text(
              'Remaining points: ${totalPoints - itemPoints}',
              style: TextStyle(fontSize: 13, color: Colors.grey.shade600),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('Redeem'),
          ),
        ],
      ),
    );

    if (confirmed != true || !mounted) return;

    setState(() => _redeeming = true);

    try {
      await provider.redeemPoints(itemId, itemPoints);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Row(
            children: [
              const Icon(Icons.check_circle, color: Colors.white, size: 20),
              const SizedBox(width: 8),
              Text('Successfully redeemed "$itemName"!'),
            ],
          ),
          backgroundColor: AppTheme.successGreen,
        ),
      );
      // Reload points to reflect updated balance
      provider.loadRewardPoints();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Redemption failed: ${e.toString()}'),
          backgroundColor: AppTheme.errorRed,
        ),
      );
    } finally {
      if (mounted) {
        setState(() => _redeeming = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<McpWidgetProvider>(
      builder: (context, provider, _) {
        if (provider.loading && provider.rewardData == null) {
          return const Center(child: CircularProgressIndicator());
        }

        if (provider.error != null && provider.rewardData == null) {
          return Card(
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.error_outline,
                        color: AppTheme.errorRed, size: 48),
                    const SizedBox(height: 12),
                    Text(
                      'Failed to load reward points',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      provider.error!,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                    const SizedBox(height: 16),
                    ElevatedButton.icon(
                      onPressed: () => provider.loadRewardPoints(),
                      icon: const Icon(Icons.refresh, size: 18),
                      label: const Text('Retry'),
                    ),
                  ],
                ),
              ),
            ),
          );
        }

        final data = provider.rewardData ?? {};
        final totalPoints = (data['totalPoints'] as num?)?.toInt() ?? 0;
        final valueInRupees =
            (data['valueInRupees'] as num?)?.toDouble() ?? 0;
        final expiryDate = data['expiryDate']?.toString() ?? '';
        final catalog = (data['catalog'] as List<dynamic>?)
                ?.map((e) => Map<String, dynamic>.from(e as Map))
                .toList() ??
            [];

        final cashbackItems = _getCashbackItems(catalog);
        final filteredCatalog = _getFilteredCatalog(catalog);

        return Card(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Header
                Row(
                  children: [
                    Icon(Icons.stars_rounded, color: AppTheme.primaryBlue),
                    const SizedBox(width: 12),
                    Text(
                      'Reward Points',
                      style:
                          Theme.of(context).textTheme.titleLarge?.copyWith(
                                fontWeight: FontWeight.w700,
                              ),
                    ),
                  ],
                ),
                const SizedBox(height: 24),

                // Points balance
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(20),
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      colors: [
                        AppTheme.primaryBlue,
                        AppTheme.primaryBlue.withValues(alpha: 0.8),
                      ],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Column(
                    children: [
                      const Text(
                        'Available Points',
                        style: TextStyle(
                          color: Colors.white70,
                          fontSize: 13,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        NumberFormat('#,##,###').format(totalPoints),
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 36,
                          fontWeight: FontWeight.w800,
                          letterSpacing: -1,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Worth ${_currencyFormat.format(valueInRupees)}',
                        style: const TextStyle(
                          color: Colors.white70,
                          fontSize: 14,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      if (expiryDate.isNotEmpty) ...[
                        const SizedBox(height: 12),
                        Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 12, vertical: 4),
                          decoration: BoxDecoration(
                            color: Colors.white.withValues(alpha: 0.15),
                            borderRadius: BorderRadius.circular(20),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Icon(Icons.schedule,
                                  color: Colors.white70, size: 14),
                              const SizedBox(width: 4),
                              Text(
                                'Points expire: $expiryDate',
                                style: const TextStyle(
                                  color: Colors.white70,
                                  fontSize: 11,
                                  fontWeight: FontWeight.w500,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
                const SizedBox(height: 20),

                // Cashback option at top
                if (cashbackItems.isNotEmpty) ...[
                  ...cashbackItems.map((item) => Container(
                        margin: const EdgeInsets.only(bottom: 12),
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color:
                              AppTheme.successGreen.withValues(alpha: 0.08),
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(
                            color: AppTheme.successGreen
                                .withValues(alpha: 0.3),
                          ),
                        ),
                        child: Row(
                          children: [
                            Container(
                              width: 44,
                              height: 44,
                              decoration: BoxDecoration(
                                color: AppTheme.successGreen
                                    .withValues(alpha: 0.15),
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Icon(
                                Icons.account_balance_wallet,
                                color: AppTheme.successGreen,
                                size: 24,
                              ),
                            ),
                            const SizedBox(width: 14),
                            Expanded(
                              child: Column(
                                crossAxisAlignment:
                                    CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    item['name']?.toString() ?? 'Cashback',
                                    style: const TextStyle(
                                      fontWeight: FontWeight.w700,
                                      fontSize: 14,
                                    ),
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    '${(item['points'] as num?)?.toInt() ?? 0} points',
                                    style: TextStyle(
                                      fontSize: 12,
                                      color: Colors.grey.shade600,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            ElevatedButton(
                              onPressed: _redeeming
                                  ? null
                                  : () => _handleRedeem(
                                        provider,
                                        item,
                                        totalPoints,
                                      ),
                              style: ElevatedButton.styleFrom(
                                backgroundColor: AppTheme.successGreen,
                                padding: const EdgeInsets.symmetric(
                                    horizontal: 16, vertical: 8),
                              ),
                              child: const Text(
                                'Redeem',
                                style: TextStyle(fontSize: 13),
                              ),
                            ),
                          ],
                        ),
                      )),
                  const SizedBox(height: 8),
                ],

                // Category filter
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: _categories.map((category) {
                      final isSelected = _selectedCategory == category;
                      return Padding(
                        padding: const EdgeInsets.only(right: 8),
                        child: FilterChip(
                          selected: isSelected,
                          label: Text(category),
                          onSelected: (selected) {
                            setState(
                                () => _selectedCategory = category);
                          },
                          selectedColor:
                              AppTheme.primaryBlue.withValues(alpha: 0.15),
                          checkmarkColor: AppTheme.primaryBlue,
                          labelStyle: TextStyle(
                            color: isSelected
                                ? AppTheme.primaryBlue
                                : null,
                            fontWeight: isSelected
                                ? FontWeight.w600
                                : FontWeight.normal,
                            fontSize: 13,
                          ),
                        ),
                      );
                    }).toList(),
                  ),
                ),
                const SizedBox(height: 16),

                // Sort options
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      '${filteredCatalog.length} items',
                      style: TextStyle(
                        fontSize: 13,
                        color: Colors.grey.shade600,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    Row(
                      children: [
                        Icon(Icons.sort, size: 18, color: Colors.grey.shade600),
                        const SizedBox(width: 4),
                        DropdownButton<String>(
                          value: _sortOption,
                          underline: const SizedBox.shrink(),
                          isDense: true,
                          style: TextStyle(
                            fontSize: 13,
                            color:
                                Theme.of(context).textTheme.bodyMedium?.color,
                          ),
                          items: const [
                            DropdownMenuItem(
                              value: 'Popular',
                              child: Text('Popular'),
                            ),
                            DropdownMenuItem(
                              value: 'Points: Low to High',
                              child: Text('Points: Low to High'),
                            ),
                            DropdownMenuItem(
                              value: 'Points: High to Low',
                              child: Text('Points: High to Low'),
                            ),
                          ],
                          onChanged: (val) {
                            if (val != null) {
                              setState(() => _sortOption = val);
                            }
                          },
                        ),
                      ],
                    ),
                  ],
                ),
                const SizedBox(height: 12),

                // Product grid
                if (filteredCatalog.isEmpty)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 32),
                    child: Center(
                      child: Column(
                        children: [
                          Icon(Icons.search_off,
                              size: 48, color: Colors.grey.shade400),
                          const SizedBox(height: 12),
                          Text(
                            'No items found in this category',
                            style: TextStyle(color: Colors.grey.shade600),
                          ),
                        ],
                      ),
                    ),
                  )
                else
                  GridView.builder(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    gridDelegate:
                        const SliverGridDelegateWithFixedCrossAxisCount(
                      crossAxisCount: 2,
                      crossAxisSpacing: 12,
                      mainAxisSpacing: 12,
                      childAspectRatio: 0.85,
                    ),
                    itemCount: filteredCatalog.length,
                    itemBuilder: (context, index) {
                      final item = filteredCatalog[index];
                      return _buildProductCard(
                          provider, item, totalPoints);
                    },
                  ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildProductCard(
    McpWidgetProvider provider,
    Map<String, dynamic> item,
    int totalPoints,
  ) {
    final name = item['name']?.toString() ?? 'Item';
    final category = item['category']?.toString() ?? '';
    final points = (item['points'] as num?)?.toInt() ?? 0;
    final canRedeem = points <= totalPoints;

    return Card(
      clipBehavior: Clip.antiAlias,
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Category icon
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: AppTheme.primaryBlue.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(
                _getIconForCategory(category),
                color: AppTheme.primaryBlue,
                size: 24,
              ),
            ),
            const SizedBox(height: 10),

            // Product name
            Text(
              name,
              style: const TextStyle(
                fontWeight: FontWeight.w600,
                fontSize: 13,
              ),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
            const SizedBox(height: 4),

            // Category label
            Text(
              category,
              style: TextStyle(
                fontSize: 11,
                color: Colors.grey.shade600,
              ),
            ),
            const Spacer(),

            // Points and redeem button
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  '$points pts',
                  style: TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 13,
                    color: AppTheme.primaryBlue,
                  ),
                ),
                SizedBox(
                  height: 30,
                  child: ElevatedButton(
                    onPressed: (_redeeming || !canRedeem)
                        ? null
                        : () =>
                            _handleRedeem(provider, item, totalPoints),
                    style: ElevatedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(horizontal: 12),
                      textStyle: const TextStyle(fontSize: 12),
                    ),
                    child: const Text('Redeem'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
