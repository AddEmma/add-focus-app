import 'package:flutter/services.dart';
import 'dart:convert';
import 'dart:typed_data';

class AppInfo {
  final String appName;
  final String packageName;
  final Uint8List icon;

  AppInfo({
    required this.appName,
    required this.packageName,
    required this.icon,
  });

  factory AppInfo.fromMap(Map<String, dynamic> map) {
    return AppInfo(
      appName: map['appName'] as String,
      packageName: map['packageName'] as String,
      icon: base64Decode(map['icon'] as String),
    );
  }
}

class AppsService {
  static const platform = MethodChannel('com.addemma.focuslock/apps');

  Future<List<AppInfo>> getInstalledApps() async {
    try {
      final List<dynamic> result = await platform.invokeMethod(
        'getInstalledApps',
      );
      final apps = result
          .map((e) => AppInfo.fromMap(Map<String, dynamic>.from(e)))
          .toList();
      return apps;
    } on PlatformException catch (e) {
      print("Failed to get apps: '${e.message}'.");
      return [];
    }
  }
}
