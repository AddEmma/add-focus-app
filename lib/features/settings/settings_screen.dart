import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/theme/app_theme.dart';
import 'package:go_router/go_router.dart';
import '../content_shield/providers/content_shield_provider.dart';
import 'providers/settings_provider.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(settingsProvider);
    final notifier = ref.read(settingsProvider.notifier);

    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        padding: const EdgeInsets.all(AppTheme.spacingM),
        children: [
          _buildSectionHeader(context, 'General'),
          _buildSwitchTile(
            context,
            icon: Icons.notifications_rounded,
            title: 'Notifications',
            subtitle: 'Get reminders to focus',
            value: settings.notificationsEnabled,
            onChanged: (val) => notifier.setNotificationsEnabled(val),
            delay: 100,
          ),
          _buildSwitchTile(
            context,
            icon: Icons.dark_mode_rounded,
            title: 'Dark Mode',
            subtitle: 'Always on',
            value: settings.darkMode,
            onChanged: (val) => notifier.setDarkMode(val),
            delay: 200,
          ),

          const SizedBox(height: AppTheme.spacingL),
          _buildSectionHeader(context, 'Content Shield (NSFW & Porn Blocker)'),
          _buildTile(
            context,
            icon: Icons.shield_rounded,
            title: '24/7 Porn & Sexy Content Blocker',
            trailing: ref.watch(contentShieldProvider).enabled ? 'Active' : 'Off',
            onTap: () => context.push('/content-shield'),
            delay: 250,
          ),

          const SizedBox(height: AppTheme.spacingL),
          _buildSectionHeader(context, 'Focus'),
          _buildSwitchTile(
            context,
            icon: Icons.lock_outline_rounded,
            title: 'Strict Mode',
            subtitle: 'Prevent cancelling focus sessions',
            value: settings.strictMode,
            onChanged: (val) => notifier.setStrictMode(val),
            delay: 300,
          ),
          _buildTile(
            context,
            icon: Icons.timer_rounded,
            title: 'Default Duration',
            trailing: '${settings.defaultDurationMinutes} min',
            onTap: () => _showDurationPicker(context, ref),
            delay: 400,
          ),

          const SizedBox(height: AppTheme.spacingL),
          _buildSectionHeader(context, 'About'),
          _buildTile(
            context,
            icon: Icons.info_outline_rounded,
            title: 'Version',
            trailing: '1.0.0',
            onTap: () {},
            delay: 500,
          ),
          _buildTile(
            context,
            icon: Icons.privacy_tip_outlined,
            title: 'Privacy Policy',
            trailing: '',
            onTap: () {},
            delay: 600,
          ),
        ],
      ),
    );
  }

  void _showDurationPicker(BuildContext context, WidgetRef ref) {
    showModalBottomSheet(
      context: context,
      backgroundColor: AppTheme.background,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(
          top: Radius.circular(AppTheme.radiusL),
        ),
      ),
      builder: (context) {
        return Container(
          padding: const EdgeInsets.all(AppTheme.spacingL),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                'Select Default Duration',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: AppTheme.spacingL),
              Wrap(
                spacing: AppTheme.spacingM,
                runSpacing: AppTheme.spacingM,
                children: [15, 25, 30, 45, 60, 90].map((mins) {
                  return ChoiceChip(
                    label: Text('$mins min'),
                    selected:
                        ref.watch(settingsProvider).defaultDurationMinutes ==
                        mins,
                    onSelected: (selected) {
                      if (selected) {
                        ref
                            .read(settingsProvider.notifier)
                            .setDefaultDuration(mins);
                        Navigator.pop(context);
                      }
                    },
                    selectedColor: AppTheme.primary,
                    labelStyle: TextStyle(
                      color:
                          ref.watch(settingsProvider).defaultDurationMinutes ==
                              mins
                          ? Colors.white
                          : Colors.white70,
                    ),
                  );
                }).toList(),
              ),
              const SizedBox(height: AppTheme.spacingL),
            ],
          ),
        );
      },
    );
  }

  Widget _buildSectionHeader(BuildContext context, String title) {
    return Padding(
      padding: const EdgeInsets.only(
        left: AppTheme.spacingS,
        bottom: AppTheme.spacingS,
      ),
      child: Text(
        title,
        style: Theme.of(context).textTheme.titleSmall?.copyWith(
          color: AppTheme.primary,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }

  Widget _buildSwitchTile(
    BuildContext context, {
    required IconData icon,
    required String title,
    required String subtitle,
    required bool value,
    required Function(bool) onChanged,
    required int delay,
  }) {
    return Container(
      margin: const EdgeInsets.only(bottom: AppTheme.spacingS),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(AppTheme.radiusM),
      ),
      child: SwitchListTile(
        value: value,
        onChanged: onChanged,
        title: Text(title, style: const TextStyle(fontWeight: FontWeight.bold)),
        subtitle: Text(
          subtitle,
          style: const TextStyle(color: AppTheme.textSecondary, fontSize: 12),
        ),
        secondary: Container(
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: AppTheme.background,
            borderRadius: BorderRadius.circular(AppTheme.radiusS),
          ),
          child: Icon(icon, color: Colors.white),
        ),
        activeColor: AppTheme.primary,
      ),
    ).animate().fadeIn(delay: delay.ms).slideY(begin: 0.1, end: 0);
  }

  Widget _buildTile(
    BuildContext context, {
    required IconData icon,
    required String title,
    required String trailing,
    required VoidCallback onTap,
    required int delay,
  }) {
    return Container(
      margin: const EdgeInsets.only(bottom: AppTheme.spacingS),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(AppTheme.radiusM),
      ),
      child: ListTile(
        onTap: onTap,
        title: Text(title, style: const TextStyle(fontWeight: FontWeight.bold)),
        leading: Container(
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: AppTheme.background,
            borderRadius: BorderRadius.circular(AppTheme.radiusS),
          ),
          child: Icon(icon, color: Colors.white),
        ),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              trailing,
              style: const TextStyle(color: AppTheme.textSecondary),
            ),
            const SizedBox(width: 8),
            const Icon(
              Icons.chevron_right_rounded,
              color: AppTheme.textSecondary,
            ),
          ],
        ),
      ),
    ).animate().fadeIn(delay: delay.ms).slideY(begin: 0.1, end: 0);
  }
}
