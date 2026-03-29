import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'config/theme.dart';
import 'providers/dashboard_provider.dart';
import 'providers/agent_provider.dart';
import 'providers/policy_provider.dart';
import 'screens/login_screen.dart';
import 'screens/dashboard_screen.dart';
import 'screens/agents_screen.dart';
import 'screens/policies_screen.dart';
import 'screens/audit_screen.dart';
import 'screens/monitoring_screen.dart';
import 'screens/settings_screen.dart';
import 'widgets/sidebar_navigation.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const AdminDashboardApp());
}

class AdminDashboardApp extends StatefulWidget {
  const AdminDashboardApp({super.key});

  @override
  State<AdminDashboardApp> createState() => _AdminDashboardAppState();
}

class _AdminDashboardAppState extends State<AdminDashboardApp> {
  ThemeMode _themeMode = ThemeMode.dark;

  void _toggleTheme() {
    setState(() {
      _themeMode =
          _themeMode == ThemeMode.dark ? ThemeMode.light : ThemeMode.dark;
    });
  }

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => DashboardProvider()),
        ChangeNotifierProvider(create: (_) => AgentProvider()),
        ChangeNotifierProvider(create: (_) => PolicyProvider()),
      ],
      child: MaterialApp(
        title: 'AI Agent Platform - Admin Dashboard',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.lightTheme,
        darkTheme: AppTheme.darkTheme,
        themeMode: _themeMode,
        initialRoute: '/login',
        onGenerateRoute: (settings) {
          return MaterialPageRoute(
            builder: (context) {
              if (settings.name == '/login') {
                return const LoginScreen();
              }
              return AppShell(
                route: settings.name ?? '/dashboard',
                themeMode: _themeMode,
                onToggleTheme: _toggleTheme,
              );
            },
          );
        },
      ),
    );
  }
}

class AppShell extends StatefulWidget {
  final String route;
  final ThemeMode themeMode;
  final VoidCallback onToggleTheme;

  const AppShell({
    super.key,
    required this.route,
    required this.themeMode,
    required this.onToggleTheme,
  });

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  late String _currentRoute;

  @override
  void initState() {
    super.initState();
    _currentRoute = widget.route;
  }

  void _onNavigate(String route) {
    setState(() {
      _currentRoute = route;
    });
  }

  Widget _buildScreen() {
    switch (_currentRoute) {
      case '/dashboard':
        return const DashboardScreen();
      case '/agents':
        return const AgentsScreen();
      case '/policies':
        return const PoliciesScreen();
      case '/audit':
        return const AuditScreen();
      case '/monitoring':
        return const MonitoringScreen();
      case '/settings':
        return const SettingsScreen();
      default:
        return const DashboardScreen();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Row(
        children: [
          SidebarNavigation(
            currentRoute: _currentRoute,
            onNavigate: _onNavigate,
            themeMode: widget.themeMode,
            onToggleTheme: widget.onToggleTheme,
          ),
          Expanded(
            child: _buildScreen(),
          ),
        ],
      ),
    );
  }
}
