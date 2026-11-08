import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../features/splash/splash_screen.dart';
import '../../features/onboarding/onboarding_screen.dart';
import '../../features/permissions/permissions_screen.dart';
import '../../features/shell/bottom_nav_shell.dart';
import '../../features/home/home_screen.dart';
import '../../features/apps/app_selection_screen.dart';
import '../../features/schedule/schedule_screen.dart';
import '../../features/stats/stats_screen.dart';
import '../../features/settings/settings_screen.dart';
import '../../features/focus/set_focus_time_screen.dart';
import '../../features/focus/focus_active_screen.dart';
import '../../features/diagnostics/diagnostics_screen.dart';
import '../../features/tasks/daily_plan_screen.dart';
import '../../features/content_shield/content_shield_screen.dart';

final GlobalKey<NavigatorState> _rootNavigatorKey = GlobalKey<NavigatorState>();
final GlobalKey<NavigatorState> _shellNavigatorKey =
    GlobalKey<NavigatorState>();

final appRouter = GoRouter(
  navigatorKey: _rootNavigatorKey,
  initialLocation: '/',
  routes: [
    GoRoute(path: '/', builder: (context, state) => const SplashScreen()),
    GoRoute(
      path: '/onboarding',
      builder: (context, state) => const OnboardingScreen(),
    ),
    GoRoute(
      path: '/permissions',
      builder: (context, state) => const PermissionsScreen(),
    ),
    StatefulShellRoute.indexedStack(
      builder: (context, state, navigationShell) {
        return BottomNavShell(navigationShell: navigationShell);
      },
      branches: [
        StatefulShellBranch(
          routes: [
            GoRoute(
              path: '/home',
              builder: (context, state) => const HomeScreen(),
            ),
          ],
        ),
        StatefulShellBranch(
          routes: [
            GoRoute(
              path: '/apps',
              builder: (context, state) => const AppSelectionScreen(),
            ),
          ],
        ),
        StatefulShellBranch(
          routes: [
            GoRoute(
              path: '/schedule',
              builder: (context, state) => const ScheduleScreen(),
            ),
          ],
        ),
        StatefulShellBranch(
          routes: [
            GoRoute(
              path: '/stats',
              builder: (context, state) => const StatsScreen(),
            ),
          ],
        ),
        StatefulShellBranch(
          routes: [
            GoRoute(
              path: '/settings',
              builder: (context, state) => const SettingsScreen(),
            ),
          ],
        ),
      ],
    ),
    GoRoute(
      path: '/set-focus',
      parentNavigatorKey: _rootNavigatorKey,
      builder: (context, state) => const SetFocusTimeScreen(),
    ),
    GoRoute(
      path: '/focus-active',
      parentNavigatorKey: _rootNavigatorKey,
      builder: (context, state) => const FocusActiveScreen(),
    ),
    GoRoute(
      path: '/diagnostics',
      parentNavigatorKey: _rootNavigatorKey,
      builder: (context, state) => const DiagnosticsScreen(),
    ),
    GoRoute(
      path: '/daily-plan',
      parentNavigatorKey: _rootNavigatorKey,
      builder: (context, state) => const DailyPlanScreen(),
    ),
    GoRoute(
      path: '/content-shield',
      parentNavigatorKey: _rootNavigatorKey,
      builder: (context, state) => const ContentShieldScreen(),
    ),
  ],
);
