import 'dart:async';
import 'dart:convert';
import 'package:web_socket_channel/web_socket_channel.dart';

import '../config/app_config.dart';

class WebSocketService {
  static final WebSocketService _instance = WebSocketService._internal();
  factory WebSocketService() => _instance;
  WebSocketService._internal();

  WebSocketChannel? _monitoringChannel;
  WebSocketChannel? _eventsChannel;

  final _monitoringController =
      StreamController<Map<String, dynamic>>.broadcast();
  final _eventsController =
      StreamController<Map<String, dynamic>>.broadcast();

  Stream<Map<String, dynamic>> get monitoringStream =>
      _monitoringController.stream;
  Stream<Map<String, dynamic>> get eventsStream => _eventsController.stream;

  bool _isConnected = false;
  Timer? _reconnectTimer;

  bool get isConnected => _isConnected;

  void connectMonitoring() {
    _connectChannel(
      '${AppConfig.wsBaseUrl}${AppConfig.wsMonitoring}',
      _monitoringController,
      isMonitoring: true,
    );
  }

  void connectEvents() {
    _connectChannel(
      '${AppConfig.wsBaseUrl}${AppConfig.wsEvents}',
      _eventsController,
      isMonitoring: false,
    );
  }

  void _connectChannel(
    String url,
    StreamController<Map<String, dynamic>> controller, {
    required bool isMonitoring,
  }) {
    try {
      final channel = WebSocketChannel.connect(Uri.parse(url));

      if (isMonitoring) {
        _monitoringChannel = channel;
      } else {
        _eventsChannel = channel;
      }

      _isConnected = true;

      channel.stream.listen(
        (message) {
          try {
            final data = jsonDecode(message as String) as Map<String, dynamic>;
            controller.add(data);
          } catch (e) {
            print('WebSocket parse error: $e');
          }
        },
        onError: (error) {
          print('WebSocket error: $error');
          _isConnected = false;
          _scheduleReconnect(url, controller, isMonitoring: isMonitoring);
        },
        onDone: () {
          _isConnected = false;
          _scheduleReconnect(url, controller, isMonitoring: isMonitoring);
        },
      );
    } catch (e) {
      print('WebSocket connection failed: $e');
      _scheduleReconnect(url, controller, isMonitoring: isMonitoring);
    }
  }

  void _scheduleReconnect(
    String url,
    StreamController<Map<String, dynamic>> controller, {
    required bool isMonitoring,
  }) {
    _reconnectTimer?.cancel();
    _reconnectTimer = Timer(AppConfig.wsReconnectDelay, () {
      _connectChannel(url, controller, isMonitoring: isMonitoring);
    });
  }

  void sendMonitoringMessage(Map<String, dynamic> data) {
    _monitoringChannel?.sink.add(jsonEncode(data));
  }

  void disconnect() {
    _reconnectTimer?.cancel();
    _monitoringChannel?.sink.close();
    _eventsChannel?.sink.close();
    _isConnected = false;
  }

  void dispose() {
    disconnect();
    _monitoringController.close();
    _eventsController.close();
  }
}
