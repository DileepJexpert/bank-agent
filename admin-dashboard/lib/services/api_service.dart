import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

import '../config/app_config.dart';

class ApiService {
  static final ApiService _instance = ApiService._internal();
  factory ApiService() => _instance;
  ApiService._internal();

  String? _authToken;
  final http.Client _client = http.Client();

  String get _baseUrl => AppConfig.apiBaseUrl;

  Map<String, String> get _headers => {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        if (_authToken != null) 'Authorization': 'Bearer $_authToken',
      };

  Future<void> loadToken() async {
    final prefs = await SharedPreferences.getInstance();
    _authToken = prefs.getString('auth_token');
  }

  Future<void> setToken(String token) async {
    _authToken = token;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('auth_token', token);
  }

  Future<void> clearToken() async {
    _authToken = null;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('auth_token');
  }

  bool get isAuthenticated => _authToken != null;

  // --- Auth ---

  Future<Map<String, dynamic>> login(String username, String password) async {
    final response = await _post(AppConfig.authLogin, {
      'username': username,
      'password': password,
    });
    if (response.containsKey('token')) {
      await setToken(response['token'] as String);
    }
    return response;
  }

  Future<void> logout() async {
    await clearToken();
  }

  // --- Dashboard ---

  Future<Map<String, dynamic>> getDashboardStats() async {
    return _get(AppConfig.dashboardStats);
  }

  // --- Agents ---

  Future<List<dynamic>> getAgents() async {
    final response = await _get(AppConfig.agents);
    return response['agents'] as List<dynamic>? ?? [];
  }

  Future<Map<String, dynamic>> getAgent(String id) async {
    return _get('${AppConfig.agents}/$id');
  }

  Future<Map<String, dynamic>> createAgent(Map<String, dynamic> data) async {
    return _post(AppConfig.agents, data);
  }

  Future<Map<String, dynamic>> updateAgent(
      String id, Map<String, dynamic> data) async {
    return _put('${AppConfig.agents}/$id', data);
  }

  Future<void> deleteAgent(String id) async {
    await _delete('${AppConfig.agents}/$id');
  }

  Future<Map<String, dynamic>> controlAgent(
      String id, String action) async {
    return _post('${AppConfig.agents}/$id/$action', {});
  }

  // --- Policies ---

  Future<List<dynamic>> getPolicies() async {
    final response = await _get(AppConfig.policies);
    return response['policies'] as List<dynamic>? ?? [];
  }

  Future<Map<String, dynamic>> getPolicy(String id) async {
    return _get('${AppConfig.policies}/$id');
  }

  Future<Map<String, dynamic>> createPolicy(
      Map<String, dynamic> data) async {
    return _post(AppConfig.policies, data);
  }

  Future<Map<String, dynamic>> updatePolicy(
      String id, Map<String, dynamic> data) async {
    return _put('${AppConfig.policies}/$id', data);
  }

  Future<void> deletePolicy(String id) async {
    await _delete('${AppConfig.policies}/$id');
  }

  Future<Map<String, dynamic>> testPolicy(
      String id, Map<String, dynamic> input) async {
    return _post('${AppConfig.policies}/$id/test', input);
  }

  // --- Audit ---

  Future<Map<String, dynamic>> getAuditEvents({
    int page = 1,
    int pageSize = 25,
    String? agentId,
    String? action,
    String? startDate,
    String? endDate,
    String? search,
  }) async {
    final params = <String, String>{
      'page': page.toString(),
      'page_size': pageSize.toString(),
    };
    if (agentId != null) params['agent_id'] = agentId;
    if (action != null) params['action'] = action;
    if (startDate != null) params['start_date'] = startDate;
    if (endDate != null) params['end_date'] = endDate;
    if (search != null) params['search'] = search;

    final query = Uri(queryParameters: params).query;
    return _get('${AppConfig.auditEvents}?$query');
  }

  // --- Monitoring ---

  Future<Map<String, dynamic>> getMonitoringData() async {
    return _get(AppConfig.monitoring);
  }

  Future<Map<String, dynamic>> getHealthCheck() async {
    return _get(AppConfig.healthCheck);
  }

  // --- Settings ---

  Future<Map<String, dynamic>> getSettings() async {
    return _get(AppConfig.settings);
  }

  Future<Map<String, dynamic>> updateSettings(
      Map<String, dynamic> data) async {
    return _put(AppConfig.settings, data);
  }

  Future<Map<String, dynamic>> getLlmConfig() async {
    return _get(AppConfig.llmConfig);
  }

  Future<Map<String, dynamic>> updateLlmConfig(
      Map<String, dynamic> data) async {
    return _put(AppConfig.llmConfig, data);
  }

  // --- HTTP Helpers ---

  Future<Map<String, dynamic>> _get(String path) async {
    final uri = Uri.parse('$_baseUrl$path');
    final response = await _client
        .get(uri, headers: _headers)
        .timeout(AppConfig.apiTimeout);
    return _handleResponse(response);
  }

  Future<Map<String, dynamic>> _post(
      String path, Map<String, dynamic> body) async {
    final uri = Uri.parse('$_baseUrl$path');
    final response = await _client
        .post(uri, headers: _headers, body: jsonEncode(body))
        .timeout(AppConfig.apiTimeout);
    return _handleResponse(response);
  }

  Future<Map<String, dynamic>> _put(
      String path, Map<String, dynamic> body) async {
    final uri = Uri.parse('$_baseUrl$path');
    final response = await _client
        .put(uri, headers: _headers, body: jsonEncode(body))
        .timeout(AppConfig.apiTimeout);
    return _handleResponse(response);
  }

  Future<void> _delete(String path) async {
    final uri = Uri.parse('$_baseUrl$path');
    final response = await _client
        .delete(uri, headers: _headers)
        .timeout(AppConfig.apiTimeout);
    if (response.statusCode >= 400) {
      throw ApiException(
        statusCode: response.statusCode,
        message: _extractErrorMessage(response),
      );
    }
  }

  Map<String, dynamic> _handleResponse(http.Response response) {
    if (response.statusCode >= 200 && response.statusCode < 300) {
      if (response.body.isEmpty) return {};
      return jsonDecode(response.body) as Map<String, dynamic>;
    }
    throw ApiException(
      statusCode: response.statusCode,
      message: _extractErrorMessage(response),
    );
  }

  String _extractErrorMessage(http.Response response) {
    try {
      final body = jsonDecode(response.body) as Map<String, dynamic>;
      return body['error'] as String? ??
          body['message'] as String? ??
          'Request failed with status ${response.statusCode}';
    } catch (_) {
      return 'Request failed with status ${response.statusCode}';
    }
  }
}

class ApiException implements Exception {
  final int statusCode;
  final String message;

  const ApiException({required this.statusCode, required this.message});

  @override
  String toString() => 'ApiException($statusCode): $message';
}
