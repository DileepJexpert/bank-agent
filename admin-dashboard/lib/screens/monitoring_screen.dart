import 'package:flutter/material.dart';
import '../widgets/kpi_card.dart';

class MonitoringScreen extends StatefulWidget {
  const MonitoringScreen({super.key});

  @override
  State<MonitoringScreen> createState() => _MonitoringScreenState();
}

class _MonitoringScreenState extends State<MonitoringScreen> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'System Monitoring',
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
            ),
            const SizedBox(height: 24),
            _buildServiceHealthGrid(),
            const SizedBox(height: 24),
            _buildLlmUsageSection(),
            const SizedBox(height: 24),
            _buildKafkaMonitoring(),
          ],
        ),
      ),
    );
  }

  Widget _buildServiceHealthGrid() {
    final services = [
      _ServiceHealth('API Gateway', 'Healthy', Colors.green, '8080'),
      _ServiceHealth('Orchestrator', 'Healthy', Colors.green, '8084'),
      _ServiceHealth('Account Agent', 'Healthy', Colors.green, '8085'),
      _ServiceHealth('Vault Identity', 'Healthy', Colors.green, '8081'),
      _ServiceHealth('Vault Policy', 'Healthy', Colors.green, '8082'),
      _ServiceHealth('Vault Audit', 'Healthy', Colors.green, '8083'),
      _ServiceHealth('Core Banking MCP', 'Degraded', Colors.orange, '8086'),
      _ServiceHealth('Config Server', 'Healthy', Colors.green, '8888'),
    ];

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Service Health',
                style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 16),
            GridView.builder(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 4,
                childAspectRatio: 2.5,
                crossAxisSpacing: 12,
                mainAxisSpacing: 12,
              ),
              itemCount: services.length,
              itemBuilder: (context, index) {
                final s = services[index];
                return Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    border: Border.all(color: s.color.withValues(alpha: 0.5)),
                    borderRadius: BorderRadius.circular(8),
                    color: s.color.withValues(alpha: 0.1),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Row(
                        children: [
                          Icon(Icons.circle, size: 10, color: s.color),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(s.name,
                                style: const TextStyle(
                                    fontWeight: FontWeight.w600)),
                          ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Text('Port ${s.port} - ${s.status}',
                          style: Theme.of(context).textTheme.bodySmall),
                    ],
                  ),
                );
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildLlmUsageSection() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('LLM Usage & Cost',
                style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: KpiCard(
                    title: 'Total API Calls (Today)',
                    value: '12,847',
                    icon: Icons.api,
                    color: Colors.blue,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: KpiCard(
                    title: 'Estimated Cost (Today)',
                    value: '\$47.23',
                    icon: Icons.attach_money,
                    color: Colors.green,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: KpiCard(
                    title: 'Tier 0 (No LLM)',
                    value: '42.3%',
                    icon: Icons.speed,
                    color: Colors.purple,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: KpiCard(
                    title: 'Avg Latency',
                    value: '1.2s',
                    icon: Icons.timer,
                    color: Colors.orange,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            _buildLlmProviderTable(),
          ],
        ),
      ),
    );
  }

  Widget _buildLlmProviderTable() {
    return DataTable(
      columns: const [
        DataColumn(label: Text('Provider')),
        DataColumn(label: Text('Model')),
        DataColumn(label: Text('Calls')),
        DataColumn(label: Text('Avg Latency')),
        DataColumn(label: Text('Cost')),
        DataColumn(label: Text('Status')),
      ],
      rows: [
        _buildProviderRow(
            'Anthropic', 'claude-sonnet-4-20250514', '5,234', '1.1s', '\$28.40', true),
        _buildProviderRow(
            'OpenAI', 'gpt-4o', '2,103', '1.4s', '\$15.20', true),
        _buildProviderRow(
            'Ollama', 'llama3.1:8b', '5,510', '0.3s', '\$0.00', true),
      ],
    );
  }

  DataRow _buildProviderRow(String provider, String model, String calls,
      String latency, String cost, bool active) {
    return DataRow(cells: [
      DataCell(Text(provider)),
      DataCell(Text(model, style: const TextStyle(fontFamily: 'monospace'))),
      DataCell(Text(calls)),
      DataCell(Text(latency)),
      DataCell(Text(cost)),
      DataCell(Icon(
        active ? Icons.check_circle : Icons.cancel,
        color: active ? Colors.green : Colors.red,
        size: 20,
      )),
    ]);
  }

  Widget _buildKafkaMonitoring() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Kafka Topics',
                style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 16),
            DataTable(
              columns: const [
                DataColumn(label: Text('Topic')),
                DataColumn(label: Text('Partitions')),
                DataColumn(label: Text('Messages/sec')),
                DataColumn(label: Text('Consumer Lag')),
              ],
              rows: const [
                DataRow(cells: [
                  DataCell(Text('vault.audit.events')),
                  DataCell(Text('12')),
                  DataCell(Text('245')),
                  DataCell(Text('0')),
                ]),
                DataRow(cells: [
                  DataCell(Text('agent.conversations')),
                  DataCell(Text('6')),
                  DataCell(Text('89')),
                  DataCell(Text('3')),
                ]),
                DataRow(cells: [
                  DataCell(Text('fraud.transactions')),
                  DataCell(Text('24')),
                  DataCell(Text('1,247')),
                  DataCell(Text('12')),
                ]),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ServiceHealth {
  final String name;
  final String status;
  final Color color;
  final String port;

  _ServiceHealth(this.name, this.status, this.color, this.port);
}
