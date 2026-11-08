import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/theme/app_theme.dart';
import 'models/content_shield_state.dart';
import 'providers/content_shield_provider.dart';

class ContentShieldScreen extends ConsumerStatefulWidget {
  const ContentShieldScreen({super.key});

  @override
  ConsumerState<ContentShieldScreen> createState() =>
      _ContentShieldScreenState();
}

class _ContentShieldScreenState extends ConsumerState<ContentShieldScreen> {
  final TextEditingController _keywordController = TextEditingController();

  @override
  void dispose() {
    _keywordController.dispose();
    super.dispose();
  }

  String _formatRemaining(int totalSeconds) {
    final mins = totalSeconds ~/ 60;
    final secs = totalSeconds % 60;
    return '${mins.toString().padLeft(2, '0')}:${secs.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final shield = ref.watch(contentShieldProvider);
    final notifier = ref.read(contentShieldProvider.notifier);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Content Shield (NSFW Blocker)'),
        actions: [
          IconButton(
            tooltip: 'Test Shield Overlay',
            icon: const Icon(Icons.visibility_rounded),
            onPressed: () => notifier.testShieldOverlay(),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(AppTheme.spacingM),
        children: [
          _buildHeroCard(context, shield, notifier),
          const SizedBox(height: AppTheme.spacingM),

          if (!shield.accessibilityGranted) ...[
            _buildAccessibilityWarningCard(context, notifier),
            const SizedBox(height: AppTheme.spacingM),
          ],

          if (shield.activeLocks.isNotEmpty) ...[
            _buildSectionHeader(context, 'Active Cooldown Locks 🔒'),
            ...shield.activeLocks.map((lock) => _buildActiveLockCard(lock)),
            const SizedBox(height: AppTheme.spacingM),
          ],

          _buildSectionHeader(context, '3-Layer Protection Engine'),
          _buildLayerTile(
            context,
            layerBadge: 'LAYER 1',
            icon: Icons.manage_search_rounded,
            color: const Color(0xFFFF4B72),
            title: 'Search, URL & Handle Blocker',
            subtitle:
                'Blocks explicit & sexy searches, adult URLs (including Incognito), and flagged Instagram/TikTok profiles as soon as you search or open them.',
            value: shield.enabled && shield.searchUrlFilterEnabled,
            onChanged: shield.enabled
                ? (v) => notifier.setSearchUrlFilterEnabled(v)
                : null,
          ),
          _buildLayerTile(
            context,
            layerBadge: 'LAYER 2',
            icon: Icons.remove_red_eye_rounded,
            color: const Color(0xFF6C63FF),
            title: 'On-Device Visual AI Scanner',
            subtitle:
                'Scans on-screen images & videos locally using AI to block half-naked, bikini, cleavage, and porn visuals even without text keywords.',
            value: shield.enabled && shield.visualAiScannerEnabled,
            onChanged: shield.enabled
                ? (v) => notifier.setVisualAiScannerEnabled(v)
                : null,
          ),
          _buildLayerTile(
            context,
            layerBadge: 'LAYER 3',
            icon: Icons.vpn_lock_rounded,
            color: AppTheme.secondary,
            title: 'SafeSearch & Adult DNS VPN',
            subtitle:
                'Routes DNS through CleanBrowsing Adult Filter & Cloudflare 1.1.1.3 to block porn domains and force Strict SafeSearch on Google, Bing & YouTube.',
            value: shield.safeSearchVpnActive,
            onChanged: (v) => notifier.toggleSafeSearchVpn(v),
          ),

          const SizedBox(height: AppTheme.spacingL),
          _buildSectionHeader(context, 'Detection Sensitivity'),
          _buildSensitivitySelector(context, shield, notifier),

          const SizedBox(height: AppTheme.spacingL),
          _buildSectionHeader(context, 'Violation Action & Cooldown Lock'),
          _buildCooldownSelector(context, shield, notifier),

          const SizedBox(height: AppTheme.spacingL),
          _buildSectionHeader(
            context,
            'Blocked Accounts, Handles & Custom Keywords',
          ),
          _buildCustomKeywordsCard(context, shield, notifier),

          if (shield.interceptHistory.isNotEmpty) ...[
            const SizedBox(height: AppTheme.spacingL),
            _buildSectionHeader(context, 'Recent Blocked Intercepts'),
            ...shield.interceptHistory
                .take(10)
                .map((item) => _buildHistoryTile(item)),
          ],

          const SizedBox(height: 40),
        ],
      ),
    );
  }

  Widget _buildHeroCard(
    BuildContext context,
    ContentShieldState shield,
    ContentShieldNotifier notifier,
  ) {
    final isProtected = shield.enabled;

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: isProtected
            ? const LinearGradient(
                colors: [Color(0xFF2B102F), Color(0xFF1A1A2E)],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              )
            : const LinearGradient(
                colors: [Color(0xFF1E1E1E), Color(0xFF161616)],
              ),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: isProtected
              ? const Color(0xFFFF4B72).withValues(alpha: 0.45)
              : Colors.white12,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: isProtected
                      ? const Color(0xFFFF4B72).withValues(alpha: 0.2)
                      : Colors.white10,
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  Icons.shield_rounded,
                  color: isProtected ? const Color(0xFFFF4B72) : Colors.white54,
                  size: 28,
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      isProtected
                          ? '24/7 CONTENT SHIELD ACTIVE'
                          : 'CONTENT SHIELD PAUSED',
                      style: TextStyle(
                        color: isProtected
                            ? const Color(0xFFFF4B72)
                            : Colors.white54,
                        fontWeight: FontWeight.bold,
                        fontSize: 12,
                        letterSpacing: 1.2,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      shield.blockSexyContent
                          ? 'Strict: Blocking Porn + Sexy/Half-Naked'
                          : 'Moderate: Blocking Explicit Porn Only',
                      style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.bold,
                        fontSize: 16,
                      ),
                    ),
                  ],
                ),
              ),
              Switch(
                value: shield.enabled,
                activeThumbColor: const Color(0xFFFF4B72),
                onChanged: (v) => notifier.setShieldEnabled(v),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              _buildMetricPill(
                icon: Icons.layers_rounded,
                label: '${shield.activeLayersCount}/3 Layers On',
                color: AppTheme.secondary,
              ),
              const SizedBox(width: 8),
              _buildMetricPill(
                icon: Icons.block_rounded,
                label: '${shield.totalBlockedCount} Blocked',
                color: const Color(0xFFFF4B72),
              ),
              const SizedBox(width: 8),
              _buildMetricPill(
                icon: Icons.timer_outlined,
                label: '${shield.cooldownMinutes}m Lock',
                color: AppTheme.primary,
              ),
            ],
          ),
        ],
      ),
    ).animate().fadeIn();
  }

  Widget _buildMetricPill({
    required IconData icon,
    required String label,
    required Color color,
  }) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 8),
        decoration: BoxDecoration(
          color: Colors.black26,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: color.withValues(alpha: 0.25)),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 14, color: color),
            const SizedBox(width: 5),
            Flexible(
              child: Text(
                label,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                  color: Colors.white,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAccessibilityWarningCard(
    BuildContext context,
    ContentShieldNotifier notifier,
  ) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.amber.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.amberAccent.withValues(alpha: 0.4)),
      ),
      child: Row(
        children: [
          const Icon(
            Icons.warning_amber_rounded,
            color: Colors.amberAccent,
            size: 28,
          ),
          const SizedBox(width: 12),
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Accessibility Service Required',
                  style: TextStyle(
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                  ),
                ),
                SizedBox(height: 4),
                Text(
                  'Enable FocusLock Accessibility Service so Layer 1 (Search/URL Filter) and Layer 2 (Visual AI Scanner) can inspect screens in real time.',
                  style: TextStyle(fontSize: 12, color: Colors.white70),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          ElevatedButton(
            onPressed: () => notifier.requestAccessibility(),
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.amberAccent,
              foregroundColor: Colors.black,
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            ),
            child: const Text('Enable'),
          ),
        ],
      ),
    );
  }

  Widget _buildActiveLockCard(CooldownLockItem lock) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFF2B1020),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: const Color(0xFFFF4B72).withValues(alpha: 0.4),
        ),
      ),
      child: Row(
        children: [
          const Icon(Icons.lock_clock_rounded, color: Color(0xFFFF4B72)),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  lock.packageName,
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  '${lock.matchedTrigger} · ${lock.details}',
                  style: const TextStyle(fontSize: 11, color: Colors.white70),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: const Color(0xFFFF4B72).withValues(alpha: 0.2),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Text(
              _formatRemaining(lock.remainingSeconds),
              style: const TextStyle(
                color: Color(0xFFFF4B72),
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildLayerTile(
    BuildContext context, {
    required String layerBadge,
    required IconData icon,
    required Color color,
    required String title,
    required String subtitle,
    required bool value,
    required ValueChanged<bool>? onChanged,
  }) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.symmetric(vertical: 6),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: value ? color.withValues(alpha: 0.35) : Colors.white10,
        ),
      ),
      child: SwitchListTile(
        value: value,
        onChanged: onChanged,
        activeThumbColor: color,
        secondary: Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: color.withValues(alpha: 0.15),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Icon(icon, color: color),
        ),
        title: Row(
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.2),
                borderRadius: BorderRadius.circular(4),
              ),
              child: Text(
                layerBadge,
                style: TextStyle(
                  fontSize: 9,
                  fontWeight: FontWeight.bold,
                  color: color,
                ),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                title,
                style: const TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 15,
                ),
              ),
            ),
          ],
        ),
        subtitle: Padding(
          padding: const EdgeInsets.only(top: 4),
          child: Text(
            subtitle,
            style: const TextStyle(color: AppTheme.textSecondary, fontSize: 12),
          ),
        ),
      ),
    );
  }

  Widget _buildSensitivitySelector(
    BuildContext context,
    ContentShieldState shield,
    ContentShieldNotifier notifier,
  ) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        children: [
          _buildSensitivityOption(
            selected: shield.blockSexyContent,
            accentColor: const Color(0xFFFF4B72),
            title: 'Strict Mode (Porn + Sexy / Half-Naked)',
            subtitle:
                'Blocks explicit porn AND half-naked, bikini, cleavage, lingerie, and thirst-trap pictures/videos across social media & browsers.',
            onTap: () => notifier.setBlockSexyContent(true),
          ),
          const Divider(color: Colors.white10),
          _buildSensitivityOption(
            selected: !shield.blockSexyContent,
            accentColor: AppTheme.primary,
            title: 'Moderate Mode (Explicit Porn Only)',
            subtitle:
                'Blocks explicit nudity, porn websites, and adult video searches only.',
            onTap: () => notifier.setBlockSexyContent(false),
          ),
        ],
      ),
    );
  }

  Widget _buildSensitivityOption({
    required bool selected,
    required Color accentColor,
    required String title,
    required String subtitle,
    required VoidCallback onTap,
  }) {
    return ListTile(
      onTap: onTap,
      leading: Icon(
        selected
            ? Icons.radio_button_checked_rounded
            : Icons.radio_button_off_rounded,
        color: selected ? accentColor : Colors.white54,
      ),
      title: Text(
        title,
        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
      ),
      subtitle: Text(
        subtitle,
        style: const TextStyle(fontSize: 12, color: AppTheme.textSecondary),
      ),
    );
  }

  Widget _buildCooldownSelector(
    BuildContext context,
    ContentShieldState shield,
    ContentShieldNotifier notifier,
  ) {
    const options = [5, 10, 15, 30, 60];
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'When sexy or explicit content is detected inside an app (like Instagram or Chrome), FocusLock navigates back and locks that app for:',
            style: TextStyle(fontSize: 12, color: AppTheme.textSecondary),
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: options.map((mins) {
              final isSelected = shield.cooldownMinutes == mins;
              return ChoiceChip(
                label: Text('$mins min lock'),
                selected: isSelected,
                selectedColor: const Color(0xFFFF4B72),
                onSelected: (selected) {
                  if (selected) notifier.setCooldownMinutes(mins);
                },
                labelStyle: TextStyle(
                  color: isSelected ? Colors.white : Colors.white70,
                  fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                ),
              );
            }).toList(),
          ),
        ],
      ),
    );
  }

  Widget _buildCustomKeywordsCard(
    BuildContext context,
    ContentShieldState shield,
    ContentShieldNotifier notifier,
  ) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Add specific Instagram/TikTok usernames, domains, or search keywords to block immediately:',
            style: TextStyle(fontSize: 12, color: AppTheme.textSecondary),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _keywordController,
                  decoration: const InputDecoration(
                    hintText: 'e.g. @username, keyword, or site.com',
                    isDense: true,
                  ),
                  onSubmitted: (val) {
                    notifier.addCustomKeywordOrHandle(val);
                    _keywordController.clear();
                  },
                ),
              ),
              const SizedBox(width: 8),
              ElevatedButton(
                onPressed: () {
                  notifier.addCustomKeywordOrHandle(_keywordController.text);
                  _keywordController.clear();
                },
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppTheme.primary,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 14,
                  ),
                ),
                child: const Text('Add'),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: shield.customKeywords.map((kw) {
              return Chip(
                label: Text(
                  kw,
                  style: const TextStyle(fontSize: 12, color: Colors.white),
                ),
                backgroundColor: Colors.white10,
                deleteIcon: const Icon(Icons.close_rounded, size: 16),
                onDeleted: () => notifier.removeCustomKeywordOrHandle(kw),
              );
            }).toList(),
          ),
        ],
      ),
    );
  }

  Widget _buildHistoryTile(NsfwInterceptEvent item) {
    final isVisual = item.layer == 'VISUAL_AI_SCANNER';
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white10),
      ),
      child: Row(
        children: [
          Icon(
            isVisual
                ? Icons.remove_red_eye_rounded
                : Icons.manage_search_rounded,
            color: isVisual ? AppTheme.primary : const Color(0xFFFF4B72),
            size: 20,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '${item.matchedTrigger} (${item.packageName})',
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 13,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  item.details,
                  style: const TextStyle(
                    fontSize: 11,
                    color: AppTheme.textSecondary,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSectionHeader(BuildContext context, String title) {
    return Padding(
      padding: const EdgeInsets.only(left: 4, bottom: 8),
      child: Text(
        title,
        style: Theme.of(context).textTheme.titleSmall?.copyWith(
          color: AppTheme.primary,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }
}
