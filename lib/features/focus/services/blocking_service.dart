import 'package:flutter/services.dart';

/// Service for communicating with native Android app blocking functionality
class BlockingService {
  static const _channel = MethodChannel('com.addemma.focuslock/blocking');
  static const _eventChannel = EventChannel(
    'com.addemma.focuslock/blocking_events',
  );

  /// Starts focus mode for the specified apps (to BLOCK) until the end time
  Future<bool> startBlocking({
    required List<String> blockedApps,
    required DateTime endTime,
  }) async {
    try {
      await _channel.invokeMethod('startBlocking', {
        'blockedApps': blockedApps,
        'endTimeMillis': endTime.millisecondsSinceEpoch,
      });
      return true;
    } on PlatformException catch (e) {
      print('Failed to start focus mode: ${e.message}');
      return false;
    }
  }

  /// Stops the app blocking service
  Future<bool> stopBlocking() async {
    try {
      await _channel.invokeMethod('stopBlocking');
      return true;
    } on PlatformException catch (e) {
      print('Failed to stop blocking: ${e.message}');
      return false;
    }
  }

  /// Checks if app blocking is currently active
  Future<bool> isBlockingActive() async {
    try {
      return await _channel.invokeMethod('isBlockingActive') ?? false;
    } on PlatformException {
      return false;
    }
  }

  /// Gets full status of blocking service
  Future<Map<String, dynamic>> getBlockingStatus() async {
    try {
      final result = await _channel.invokeMethod('getBlockingStatus');
      return Map<String, dynamic>.from(result ?? {});
    } on PlatformException {
      return {};
    }
  }

  /// Stream of blocking events (attempts, etc.)
  Stream<dynamic> get blockingEventsStream {
    return _eventChannel.receiveBroadcastStream();
  }

  /// Checks if the app has usage stats permission
  Future<bool> hasUsageStatsPermission() async {
    try {
      return await _channel.invokeMethod('hasUsageStatsPermission') ?? false;
    } on PlatformException {
      return false;
    }
  }

  /// Checks if the app has overlay permission
  Future<bool> hasOverlayPermission() async {
    try {
      return await _channel.invokeMethod('hasOverlayPermission') ?? false;
    } on PlatformException {
      return false;
    }
  }

  /// Opens the system settings to request usage stats permission
  Future<void> requestUsageStatsPermission() async {
    try {
      await _channel.invokeMethod('requestUsageStatsPermission');
    } on PlatformException catch (e) {
      print('Failed to request usage stats permission: ${e.message}');
    }
  }

  /// Opens the system settings to request overlay permission
  Future<void> requestOverlayPermission() async {
    try {
      await _channel.invokeMethod('requestOverlayPermission');
    } on PlatformException catch (e) {
      print('Failed to request overlay permission: ${e.message}');
    }
  }

  /// Checks if the app has notification permission (Android 13+)
  Future<bool> hasNotificationPermission() async {
    try {
      return await _channel.invokeMethod('hasNotificationPermission') ?? true;
    } on PlatformException {
      return true;
    }
  }

  /// Requests notification permission from the user
  Future<void> requestNotificationPermission() async {
    try {
      await _channel.invokeMethod('requestNotificationPermission');
    } on PlatformException catch (e) {
      print('Failed to request notification permission: ${e.message}');
    }
  }

  /// Checks if all required permissions are granted
  Future<bool> hasRequiredPermissions() async {
    final hasUsage = await hasUsageStatsPermission();
    final hasOverlay = await hasOverlayPermission();
    final hasAccessibility = await hasAccessibilityPermission();
    return hasUsage && hasOverlay && hasAccessibility;
  }

  /// Gets recent usage events for diagnostics
  Future<List<Map<String, dynamic>>> getRecentUsageEvents() async {
    try {
      final result = await _channel.invokeMethod('getRecentUsageEvents');
      if (result is List) {
        return result.map((e) => Map<String, dynamic>.from(e as Map)).toList();
      }
      return [];
    } on PlatformException catch (e) {
      print('Failed to get recent usage events: ${e.message}');
      return [];
    }
  }

  /// Force triggers the blocking overlay for testing purposes
  Future<void> testBlockingOverlay() async {
    try {
      await _channel.invokeMethod('test_overlay');
    } on PlatformException catch (e) {
      print('Failed to test overlay: ${e.message}');
    }
  }

  /// Checks if the app has accessibility permission
  Future<bool> hasAccessibilityPermission() async {
    try {
      return await _channel.invokeMethod('hasAccessibilityPermission') ?? false;
    } on PlatformException {
      return false;
    }
  }

  /// Opens the system settings to request accessibility permission
  Future<void> requestAccessibilityPermission() async {
    try {
      await _channel.invokeMethod('requestAccessibilityPermission');
    } on PlatformException catch (e) {
      print('Failed to request accessibility permission: ${e.message}');
    }
  }
}
