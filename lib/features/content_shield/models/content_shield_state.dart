class CooldownLockItem {
  final String packageName;
  final int remainingSeconds;
  final String layer;
  final String category;
  final String matchedTrigger;
  final String details;

  const CooldownLockItem({
    required this.packageName,
    required this.remainingSeconds,
    required this.layer,
    required this.category,
    required this.matchedTrigger,
    required this.details,
  });

  factory CooldownLockItem.fromMap(Map<dynamic, dynamic> map) {
    return CooldownLockItem(
      packageName: map['packageName']?.toString() ?? '',
      remainingSeconds: (map['remainingSeconds'] as num?)?.toInt() ?? 0,
      layer: map['layer']?.toString() ?? 'CONTENT_SHIELD',
      category: map['category']?.toString() ?? 'SEXY_HALF_NAKED',
      matchedTrigger: map['matchedTrigger']?.toString() ?? '',
      details: map['details']?.toString() ?? '',
    );
  }
}

class NsfwInterceptEvent {
  final String packageName;
  final String layer;
  final String category;
  final String matchedTrigger;
  final String details;
  final int cooldownMinutes;
  final DateTime timestamp;

  const NsfwInterceptEvent({
    required this.packageName,
    required this.layer,
    required this.category,
    required this.matchedTrigger,
    required this.details,
    required this.cooldownMinutes,
    required this.timestamp,
  });

  factory NsfwInterceptEvent.fromMap(Map<dynamic, dynamic> map) {
    final tsMillis =
        (map['timestamp'] as num?)?.toInt() ??
        DateTime.now().millisecondsSinceEpoch;
    return NsfwInterceptEvent(
      packageName: map['packageName']?.toString() ?? '',
      layer: map['layer']?.toString() ?? 'CONTENT_SHIELD',
      category: map['category']?.toString() ?? 'SEXY_HALF_NAKED',
      matchedTrigger: map['matchedTrigger']?.toString() ?? '',
      details: map['details']?.toString() ?? '',
      cooldownMinutes: (map['cooldownMinutes'] as num?)?.toInt() ?? 10,
      timestamp: DateTime.fromMillisecondsSinceEpoch(tsMillis),
    );
  }
}

class ContentShieldState {
  final bool enabled;
  final bool searchUrlFilterEnabled;
  final bool visualAiScannerEnabled;
  final bool safeSearchVpnActive;
  final bool blockSexyContent; // true = Strict (Porn + Sexy/Half-Naked), false = Moderate (Explicit Porn only)
  final int cooldownMinutes;
  final bool accessibilityGranted;
  final int totalBlockedCount;
  final List<String> customKeywords;
  final List<CooldownLockItem> activeLocks;
  final List<NsfwInterceptEvent> interceptHistory;

  const ContentShieldState({
    this.enabled = true,
    this.searchUrlFilterEnabled = true,
    this.visualAiScannerEnabled = true,
    this.safeSearchVpnActive = false,
    this.blockSexyContent = true,
    this.cooldownMinutes = 10,
    this.accessibilityGranted = false,
    this.totalBlockedCount = 0,
    this.customKeywords = const [
      'buhle89127',
      'just_sh1vekaaa',
      'h34rtsfor_ditheto',
      'yasmine_kbf',
    ],
    this.activeLocks = const [],
    this.interceptHistory = const [],
  });

  int get activeLayersCount {
    if (!enabled) return 0;
    int count = 0;
    if (searchUrlFilterEnabled) count++;
    if (visualAiScannerEnabled) count++;
    if (safeSearchVpnActive) count++;
    return count;
  }

  ContentShieldState copyWith({
    bool? enabled,
    bool? searchUrlFilterEnabled,
    bool? visualAiScannerEnabled,
    bool? safeSearchVpnActive,
    bool? blockSexyContent,
    int? cooldownMinutes,
    bool? accessibilityGranted,
    int? totalBlockedCount,
    List<String>? customKeywords,
    List<CooldownLockItem>? activeLocks,
    List<NsfwInterceptEvent>? interceptHistory,
  }) {
    return ContentShieldState(
      enabled: enabled ?? this.enabled,
      searchUrlFilterEnabled:
          searchUrlFilterEnabled ?? this.searchUrlFilterEnabled,
      visualAiScannerEnabled:
          visualAiScannerEnabled ?? this.visualAiScannerEnabled,
      safeSearchVpnActive: safeSearchVpnActive ?? this.safeSearchVpnActive,
      blockSexyContent: blockSexyContent ?? this.blockSexyContent,
      cooldownMinutes: cooldownMinutes ?? this.cooldownMinutes,
      accessibilityGranted: accessibilityGranted ?? this.accessibilityGranted,
      totalBlockedCount: totalBlockedCount ?? this.totalBlockedCount,
      customKeywords: customKeywords ?? this.customKeywords,
      activeLocks: activeLocks ?? this.activeLocks,
      interceptHistory: interceptHistory ?? this.interceptHistory,
    );
  }
}
