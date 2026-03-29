import 'package:flutter/material.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  String _selectedProvider = 'anthropic';
  final _apiKeyController = TextEditingController(text: '********');
  final _baseUrlController = TextEditingController();
  String _selectedModel = 'claude-sonnet-4-20250514';
  double _temperature = 0.7;
  int _maxTokens = 4096;

  final Map<String, List<String>> _providerModels = {
    'anthropic': ['claude-sonnet-4-20250514', 'claude-opus-4-20250514', 'claude-haiku-4-5-20251001'],
    'openai': ['gpt-4o', 'gpt-4o-mini', 'gpt-4-turbo'],
    'ollama': ['llama3.1:8b', 'llama3.1:70b', 'mistral:7b', 'codellama:13b'],
    'azure-openai': ['gpt-4o', 'gpt-4-turbo'],
    'mistral': ['mistral-large', 'mistral-medium', 'mistral-small'],
  };

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Platform Settings',
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
            ),
            const SizedBox(height: 24),
            _buildLlmConfiguration(),
            const SizedBox(height: 24),
            _buildAgentLlmMapping(),
            const SizedBox(height: 24),
            _buildSystemConfiguration(),
          ],
        ),
      ),
    );
  }

  Widget _buildLlmConfiguration() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.psychology, size: 28),
                const SizedBox(width: 12),
                Text('LLM Provider Configuration',
                    style: Theme.of(context).textTheme.titleLarge),
              ],
            ),
            const Divider(height: 32),
            Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('Provider',
                          style: TextStyle(fontWeight: FontWeight.w600)),
                      const SizedBox(height: 8),
                      DropdownButtonFormField<String>(
                        value: _selectedProvider,
                        decoration: const InputDecoration(
                          border: OutlineInputBorder(),
                          contentPadding: EdgeInsets.symmetric(
                              horizontal: 12, vertical: 8),
                        ),
                        items: _providerModels.keys.map((p) {
                          return DropdownMenuItem(
                            value: p,
                            child: Text(p.toUpperCase()),
                          );
                        }).toList(),
                        onChanged: (value) {
                          setState(() {
                            _selectedProvider = value!;
                            _selectedModel =
                                _providerModels[value]!.first;
                          });
                        },
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('Model',
                          style: TextStyle(fontWeight: FontWeight.w600)),
                      const SizedBox(height: 8),
                      DropdownButtonFormField<String>(
                        value: _selectedModel,
                        decoration: const InputDecoration(
                          border: OutlineInputBorder(),
                          contentPadding: EdgeInsets.symmetric(
                              horizontal: 12, vertical: 8),
                        ),
                        items: _providerModels[_selectedProvider]!.map((m) {
                          return DropdownMenuItem(value: m, child: Text(m));
                        }).toList(),
                        onChanged: (value) {
                          setState(() => _selectedModel = value!);
                        },
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('API Key',
                          style: TextStyle(fontWeight: FontWeight.w600)),
                      const SizedBox(height: 8),
                      TextField(
                        controller: _apiKeyController,
                        obscureText: true,
                        decoration: const InputDecoration(
                          border: OutlineInputBorder(),
                          hintText: 'Enter API key',
                          suffixIcon: Icon(Icons.visibility_off),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('Base URL (optional)',
                          style: TextStyle(fontWeight: FontWeight.w600)),
                      const SizedBox(height: 8),
                      TextField(
                        controller: _baseUrlController,
                        decoration: const InputDecoration(
                          border: OutlineInputBorder(),
                          hintText: 'Custom base URL for Ollama/Azure',
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
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Temperature: ${_temperature.toStringAsFixed(1)}',
                          style: const TextStyle(fontWeight: FontWeight.w600)),
                      Slider(
                        value: _temperature,
                        min: 0,
                        max: 2,
                        divisions: 20,
                        onChanged: (v) => setState(() => _temperature = v),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Max Tokens: $_maxTokens',
                          style: const TextStyle(fontWeight: FontWeight.w600)),
                      Slider(
                        value: _maxTokens.toDouble(),
                        min: 256,
                        max: 16384,
                        divisions: 64,
                        onChanged: (v) =>
                            setState(() => _maxTokens = v.toInt()),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Align(
              alignment: Alignment.centerRight,
              child: ElevatedButton.icon(
                onPressed: _saveConfiguration,
                icon: const Icon(Icons.save),
                label: const Text('Save LLM Configuration'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAgentLlmMapping() {
    final agents = [
      {'name': 'Orchestrator', 'provider': 'anthropic', 'model': 'claude-sonnet-4-20250514'},
      {'name': 'Account Agent', 'provider': 'anthropic', 'model': 'claude-sonnet-4-20250514'},
      {'name': 'Loans Agent', 'provider': 'openai', 'model': 'gpt-4o'},
      {'name': 'Card Agent', 'provider': 'anthropic', 'model': 'claude-sonnet-4-20250514'},
      {'name': 'Wealth Agent', 'provider': 'openai', 'model': 'gpt-4o'},
      {'name': 'Collections Agent', 'provider': 'ollama', 'model': 'llama3.1:8b'},
      {'name': 'Fraud Agent', 'provider': 'ollama', 'model': 'llama3.1:70b'},
    ];

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.route, size: 28),
                const SizedBox(width: 12),
                Text('Agent LLM Mapping',
                    style: Theme.of(context).textTheme.titleLarge),
              ],
            ),
            const SizedBox(height: 8),
            Text('Configure which LLM provider each agent uses',
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Colors.grey[600])),
            const SizedBox(height: 16),
            DataTable(
              columns: const [
                DataColumn(label: Text('Agent')),
                DataColumn(label: Text('Provider')),
                DataColumn(label: Text('Model')),
                DataColumn(label: Text('Actions')),
              ],
              rows: agents.map((a) {
                return DataRow(cells: [
                  DataCell(Text(a['name']!)),
                  DataCell(Text(a['provider']!.toUpperCase())),
                  DataCell(Text(a['model']!,
                      style: const TextStyle(fontFamily: 'monospace'))),
                  DataCell(IconButton(
                    icon: const Icon(Icons.edit, size: 18),
                    onPressed: () {},
                  )),
                ]);
              }).toList(),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSystemConfiguration() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.settings, size: 28),
                const SizedBox(width: 12),
                Text('System Configuration',
                    style: Theme.of(context).textTheme.titleLarge),
              ],
            ),
            const Divider(height: 32),
            _buildConfigRow('Kafka Bootstrap Servers', 'kafka:9092'),
            _buildConfigRow('Redis Host', 'redis:6379'),
            _buildConfigRow('PostgreSQL Host', 'postgres:5432'),
            _buildConfigRow('OPA Sidecar URL', 'http://localhost:8181'),
            _buildConfigRow('Vault Policy Git Repo', 'https://github.com/org/vault-policies.git'),
            _buildConfigRow('Log Level', 'INFO'),
            _buildConfigRow('PII Masking', 'ENABLED'),
            _buildConfigRow('Max Concurrent Sessions', '10,000'),
          ],
        ),
      ),
    );
  }

  Widget _buildConfigRow(String key, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        children: [
          SizedBox(
            width: 250,
            child: Text(key,
                style: const TextStyle(fontWeight: FontWeight.w600)),
          ),
          Expanded(
            child: Text(value,
                style: const TextStyle(fontFamily: 'monospace')),
          ),
          IconButton(
            icon: const Icon(Icons.edit, size: 18),
            onPressed: () {},
          ),
        ],
      ),
    );
  }

  void _saveConfiguration() {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Configuration saved successfully'),
        backgroundColor: Colors.green,
      ),
    );
  }
}
