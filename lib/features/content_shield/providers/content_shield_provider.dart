import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../focus/providers/focus_provider.dart';
import '../../focus/services/blocking_service.dart';
import '../models/content_shield_state.dart';

class ContentShieldNotifier extends StateNotifier<ContentShieldState> {
  final BlockingService _blockingService;
  StreamSubscription? _eventSubscription;
  Timer? _pollTimer;

  static const _defaultHandles = [
    'buhle89127',
    'just_sh1vekaaa',
    'h34rtsfor_ditheto',
    'yasmine_kbf',
  ];

  ContentShieldNotifier(this._blockingService)
    : super(const ContentShieldState()) {
    _initialize();
  }

  Future<void> _initialize() async {
    final prefs = await SharedPreferences.getInstance();
    final enabled = prefs.getBool('shield_enabled') ?? true;
    final searchUrl = prefs.getBool('shield_search_url_enabled') ?? true;
    final visualAi = prefs.getBool('shield_visual_ai_enabled') ?? true;
    final blockSexy = prefs.getBool('shield_block_sexy') ?? true;
    final cooldownMins = prefs.getInt('shield_cooldown_minutes') ?? 10;
    final blockedCount = prefs.getInt('shield_blocked_count') ?? 0;
    final customCsv = prefs.getString('shield_custom_keywords_csv');

    final customKeywords = (customCsv != null && customCsv.trim().isNotEmpty)
        ? customCsv
              .split(',')
              .map((s) => s.trim())
              .where((s) => s.isNotEmpty)
              .toList()
        : List<String>.from(_defaultHandles);

    state = state.copyWith(
      enabled: enabled,
      searchUrlFilterEnabled: searchUrl,
      visualAiScannerEnabled: visualAi,
      blockSexyContent: blockSexy,
      cooldownMinutes: cooldownMins,
      totalBlockedCount: blockedCount,
      customKeywords: customKeywords,
    );

    await _syncToNative();
    await refreshNativeStatus();
    _subscribeToEvents();

    _pollTimer = Timer.periodic(const Duration(seconds: 3), (_) {
      refreshNativeStatus();
    });
  }

  void _subscribeToEvents() {
    _eventSubscription?.cancel();
    _eventSubscription = _blockingService.blockingEventsStream.listen((event) {
      if (event is Map && event['event'] == 'nsfw_blocked') {
        final newEvent = NsfwInterceptEvent.fromMap(event);
        final totalCount =
            (event['totalNsfwBlocked'] as num?)?.toInt() ??
            (state.totalBlockedCount + 1);
        final updatedHistory = [newEvent, ...state.interceptHistory].take(50).toList();
        state = state.copyWith(
          totalBlockedCount: totalCount,
          interceptHistory: updatedHistory,
        );
        refreshNativeStatus();
      }
    });
  }

  Future<void> refreshNativeStatus() async {
    final status = await _blockingService.getContentShieldStatus();
    if (status.isEmpty) return;

    final vpnActive = status['vpnActive'] == true;
    final accessActive = status['accessibilityActive'] == true;
    final nativeCount =
        (status['nsfwBlockedCount'] as num?)?.toInt() ?? state.totalBlockedCount;

    final rawLocks = status['activeLocks'];
    final locks = <CooldownLockItem>[];
    if (rawLocks is List) {
      for (final item in rawLocks) {
        if (item is Map) {
          locks.add(CooldownLockItem.fromMap(item));
        }
      }
    }

    final rawHistory = status['interceptHistory'];
    final history = <NsfwInterceptEvent>[];
    if (rawHistory is List && rawHistory.isNotEmpty) {
      for (final item in rawHistory) {
        if (item is Map) {
          history.add(NsfwInterceptEvent.fromMap(item));
        }
      }
    }

    state = state.copyWith(
      safeSearchVpnActive: vpnActive,
      accessibilityGranted: accessActive,
      totalBlockedCount: nativeCount > state.totalBlockedCount
          ? nativeCount
          : state.totalBlockedCount,
      activeLocks: locks,
      interceptHistory: history.isNotEmpty ? history : state.interceptHistory,
    );
  }

  Future<void> _syncToNative() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('shield_enabled', state.enabled);
    await prefs.setBool(
      'shield_search_url_enabled',
      state.searchUrlFilterEnabled,
    );
    await prefs.setBool(
      'shield_visual_ai_enabled',
      state.visualAiScannerEnabled,
    );
    await prefs.setBool('shield_block_sexy', state.blockSexyContent);
    await prefs.setInt('shield_cooldown_minutes', state.cooldownMinutes);
    await prefs.setString(
      'shield_custom_keywords_csv',
      state.customKeywords.join(','),
    );

    await _blockingService.configureContentShield(
      enabled: state.enabled,
      searchUrlFilterEnabled: state.searchUrlFilterEnabled,
      visualAiScannerEnabled: state.visualAiScannerEnabled,
      blockSexyContent: state.blockSexyContent,
      cooldownMinutes: state.cooldownMinutes,
      customKeywords: state.customKeywords,
    );
  }

  Future<void> setShieldEnabled(bool value) async {
    state = state.copyWith(enabled: value);
    await _syncToNative();
  }

  Future<void> setSearchUrlFilterEnabled(bool value) async {
    state = state.copyWith(searchUrlFilterEnabled: value);
    await _syncToNative();
  }

  Future<void> setVisualAiScannerEnabled(bool value) async {
    state = state.copyWith(visualAiScannerEnabled: value);
    await _syncToNative();
  }

  Future<void> setBlockSexyContent(bool value) async {
    state = state.copyWith(blockSexyContent: value);
    await _syncToNative();
  }

  Future<void> setCooldownMinutes(int minutes) async {
    state = state.copyWith(cooldownMinutes: minutes);
    await _syncToNative();
  }

  Future<void> toggleSafeSearchVpn(bool enable) async {
    if (enable) {
      final started = await _blockingService.startSafeSearchVpn();
      state = state.copyWith(safeSearchVpnActive: started);
    } else {
      await _blockingService.stopSafeSearchVpn();
      state = state.copyWith(safeSearchVpnActive: false);
    }
    await Future.delayed(const Duration(milliseconds: 500));
    await refreshNativeStatus();
  }

  Future<void> addCustomKeywordOrHandle(String rawInput) async {
    final cleaned = rawInput.trim().toLowerCase().replaceAll('@', '');
    if (cleaned.isEmpty) return;
    if (state.customKeywords.contains(cleaned)) return;

    final updated = [...state.customKeywords, cleaned];
    state = state.copyWith(customKeywords: updated);
    await _syncToNative();
  }

  Future<void> removeCustomKeywordOrHandle(String keyword) async {
    final updated = state.customKeywords.where((k) => k != keyword).toList();
    state = state.copyWith(customKeywords: updated);
    await _syncToNative();
  }

  Future<void> testShieldOverlay() async {
    await _blockingService.testNsfwShield();
  }

  Future<void> requestAccessibility() async {
    await _blockingService.requestAccessibilityPermission();
  }

  @override
  void dispose() {
    _eventSubscription?.cancel();
    _pollTimer?.cancel();
    super.dispose();
  }
}

final contentShieldProvider =
    StateNotifierProvider<ContentShieldNotifier, ContentShieldState>((ref) {
      final blockingService = ref.watch(blockingServiceProvider);
      return ContentShieldNotifier(blockingService);
    });
