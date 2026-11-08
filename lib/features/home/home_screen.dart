import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:go_router/go_router.dart';
import 'dart:math' as math;
import '../../core/theme/app_theme.dart';
import '../focus/providers/focus_provider.dart';
import '../stats/providers/stats_provider.dart';
import '../tasks/providers/tasks_provider.dart';
import '../tasks/models/task.dart';
import '../tasks/widgets/add_task_bottom_sheet.dart';
import '../content_shield/providers/content_shield_provider.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  String _formatTime(int seconds) {
    final minutes = seconds ~/ 60;
    final remainingSeconds = seconds % 60;
    return '${minutes.toString().padLeft(2, '0')}:${remainingSeconds.toString().padLeft(2, '0')}';
  }

  String _greeting() {
    final hour = DateTime.now().hour;
    if (hour < 12) return 'Good morning 🌅';
    if (hour < 17) return 'Good afternoon ☀️';
    return 'Good evening 🌙';
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final focusSession = ref.watch(focusProvider);
    final statsAsync = ref.watch(statsProvider);
    final tasks = ref.watch(tasksProvider);
    final productivityScore = ref.watch(productivityScoreProvider);

    final pendingTasks = tasks.where((t) => !t.isCompleted).toList();
    final completedTasks = tasks.where((t) => t.isCompleted).toList();
    final totalTasks = tasks.length;
    final completedCount = completedTasks.length;

    // Find next task (first pending, sorted by priority)
    final sortedPending = List<FocusTask>.from(pendingTasks)
      ..sort((a, b) => a.priority.sortOrder.compareTo(b.priority.sortOrder));
    final nextTask = sortedPending.isNotEmpty ? sortedPending.first : null;

    return Scaffold(
      body: CustomScrollView(
        slivers: [
          // Hero Header
          SliverToBoxAdapter(
            child: _buildHeroHeader(
              context,
              ref,
              focusSession,
              nextTask,
              totalTasks,
              completedCount,
              productivityScore,
            ),
          ),

          // Today's Summary Strip
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: statsAsync.when(
                data: (data) {
                  final today = DateTime.now().toIso8601String().split('T')[0];
                  final todaySecs = data.dailyUsage[today] ?? 0;
                  final todayHours = (todaySecs / 3600).toStringAsFixed(1);
                  return _buildStatsStrip(
                    context,
                    focusSession,
                    todayHours,
                    data.currentStreak,
                    productivityScore,
                  );
                },
                loading: () => const SizedBox(height: 80),
                error: (_, __) => const SizedBox(),
              ),
            ),
          ),

          // 24/7 Content Shield (Porn & Sexy Content Blocker) Card
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
              child: _buildContentShieldBanner(context, ref),
            ),
          ),

          // Tasks Section Header
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 8, 16, 8),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    "Today's Tasks",
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  Row(
                    children: [
                      TextButton.icon(
                        onPressed: () => context.push('/daily-plan'),
                        icon: const Icon(Icons.open_in_new_rounded, size: 14),
                        label: const Text('View All'),
                        style: TextButton.styleFrom(
                          foregroundColor: AppTheme.primary,
                        ),
                      ),
                    ],
                  ),
                ],
              ).animate().fadeIn(delay: 200.ms),
            ),
          ),

          // Tasks List (top 5)
          if (tasks.isEmpty)
            SliverToBoxAdapter(child: _buildEmptyTasksCard(context, ref))
          else
            SliverList(
              delegate: SliverChildBuilderDelegate(
                (context, index) {
                  if (index >= math.min(tasks.length, 5)) return null;
                  final task = tasks[index];
                  return Padding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                    child: _buildTaskTile(context, ref, task, index),
                  );
                },
                childCount: math.min(tasks.length, 5),
              ),
            ),

          if (tasks.length > 5)
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                child: TextButton(
                  onPressed: () => context.push('/daily-plan'),
                  child: Text(
                    '+ ${tasks.length - 5} more tasks',
                    style: const TextStyle(color: AppTheme.primary),
                  ),
                ),
              ),
            ),

          const SliverToBoxAdapter(child: SizedBox(height: 100)),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _showAddTask(context),
        backgroundColor: AppTheme.primary,
        child: const Icon(Icons.add_task_rounded),
      ).animate().scale(delay: 400.ms),
    );
  }

  Widget _buildHeroHeader(
    BuildContext context,
    WidgetRef ref,
    FocusSession focusSession,
    FocusTask? nextTask,
    int totalTasks,
    int completedCount,
    int productivityScore,
  ) {
    final isActive = focusSession.status == FocusStatus.active;

    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      decoration: BoxDecoration(
        gradient: isActive
            ? AppTheme.fireGradient
            : const LinearGradient(
                colors: [Color(0xFF1A1A2E), Color(0xFF16213E)],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
      ),
      child: SafeArea(
        bottom: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Top bar
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    _greeting(),
                    style: const TextStyle(color: Colors.white70, fontSize: 14),
                  ),
                  Row(
                    children: [
                      IconButton(
                        icon: const Icon(
                          Icons.bug_report_outlined,
                          color: Colors.white38,
                          size: 20,
                        ),
                        onPressed: () => context.push('/diagnostics'),
                      ),
                      IconButton(
                        icon: const Icon(
                          Icons.notifications_none_rounded,
                          color: Colors.white70,
                        ),
                        onPressed: () {},
                      ),
                    ],
                  ),
                ],
              ),

              const SizedBox(height: 12),

              if (isActive) ...[
                // Active focus session card
                _buildActiveFocusCard(context, ref, focusSession),
              ] else if (nextTask != null) ...[
                // Next task card
                _buildNextTaskCard(context, nextTask, totalTasks, completedCount),
              ] else ...[
                // Empty state
                _buildNoTasksHero(context, totalTasks, completedCount),
              ],
            ],
          ),
        ),
      ),
    ).animate().fadeIn();
  }

  Widget _buildActiveFocusCard(
    BuildContext context,
    WidgetRef ref,
    FocusSession session,
  ) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.15),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: Colors.white24),
      ),
      child: Column(
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: Colors.white.withOpacity(0.2),
                  shape: BoxShape.circle,
                ),
                child: const Icon(Icons.lock_rounded, color: Colors.white, size: 24),
              ).animate(onPlay: (c) => c.repeat())
                .shimmer(duration: 2.seconds, delay: 1.seconds),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'FOCUS LOCKED 🔒',
                      style: TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.bold,
                        fontSize: 12,
                        letterSpacing: 1.5,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      _formatTime(session.remainingSeconds),
                      style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.bold,
                        fontSize: 28,
                        fontFeatures: [FontFeature.tabularFigures()],
                      ),
                    ),
                  ],
                ),
              ),
              ElevatedButton(
                onPressed: () => context.push('/focus-active'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.white,
                  foregroundColor: const Color(0xFFFF512F),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 8,
                  ),
                  minimumSize: Size.zero,
                ),
                child: const Text('View', style: TextStyle(fontWeight: FontWeight.bold)),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              const Icon(Icons.shield_rounded, size: 14, color: Colors.white60),
              const SizedBox(width: 6),
              Text(
                '${session.blockedApps.length} apps blocked · ${session.blockedAttempts} attempts blocked',
                style: const TextStyle(color: Colors.white60, fontSize: 12),
              ),
            ],
          ),
        ],
      ),
    ).animate().scale(duration: 400.ms, curve: Curves.easeOutBack);
  }

  Widget _buildNextTaskCard(
    BuildContext context,
    FocusTask task,
    int total,
    int completed,
  ) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Up Next 👇',
          style: TextStyle(color: Colors.white60, fontSize: 13),
        ),
        const SizedBox(height: 10),
        Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            color: Colors.white.withOpacity(0.08),
            borderRadius: BorderRadius.circular(18),
            border: Border.all(color: Colors.white.withOpacity(0.12)),
          ),
          child: Column(
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: task.priority.color.withOpacity(0.2),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(
                      task.priority.icon,
                      color: task.priority.color,
                      size: 22,
                    ),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          task.title,
                          style: const TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.bold,
                            fontSize: 16,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          '${task.estimatedMinutes} min · ${task.priority.label} priority',
                          style: const TextStyle(
                            color: Colors.white54,
                            fontSize: 12,
                          ),
                        ),
                      ],
                    ),
                  ),
                  GestureDetector(
                    onTap: () => context.push('/set-focus', extra: {'task': task}),
                    child: Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        gradient: AppTheme.primaryGradient,
                        borderRadius: BorderRadius.circular(14),
                      ),
                      child: const Icon(
                        Icons.play_arrow_rounded,
                        color: Colors.white,
                        size: 22,
                      ),
                    ),
                  ),
                ],
              ),
              if (total > 0) ...[
                const SizedBox(height: 14),
                Row(
                  children: [
                    Expanded(
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(3),
                        child: LinearProgressIndicator(
                          value: total == 0 ? 0 : completed / total,
                          backgroundColor: Colors.white10,
                          valueColor: const AlwaysStoppedAnimation<Color>(
                            Colors.greenAccent,
                          ),
                          minHeight: 5,
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Text(
                      '$completed/$total',
                      style: const TextStyle(
                        color: Colors.white54,
                        fontSize: 12,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
              ],
            ],
          ),
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: OutlinedButton.icon(
                onPressed: () => context.push('/daily-plan'),
                icon: const Icon(Icons.schedule_rounded, size: 16),
                label: const Text('View Full Plan'),
                style: OutlinedButton.styleFrom(
                  foregroundColor: Colors.white70,
                  side: const BorderSide(color: Colors.white24),
                  padding: const EdgeInsets.symmetric(vertical: 10),
                ),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: ElevatedButton.icon(
                onPressed: () =>
                    context.push('/set-focus', extra: {'task': task}),
                icon: const Icon(Icons.flash_on_rounded, size: 16),
                label: const Text('Start Focus'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.white,
                  foregroundColor: const Color(0xFF1A1A2E),
                  padding: const EdgeInsets.symmetric(vertical: 10),
                ),
              ),
            ),
          ],
        ),
      ],
    ).animate().fadeIn(delay: 100.ms).slideY(begin: 0.1, end: 0);
  }

  Widget _buildNoTasksHero(
    BuildContext context,
    int total,
    int completed,
  ) {
    final allDone = total > 0 && completed == total;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          allDone ? '🎉 All tasks completed!' : 'No tasks planned',
          style: const TextStyle(
            color: Colors.white,
            fontWeight: FontWeight.bold,
            fontSize: 22,
          ),
        ),
        const SizedBox(height: 6),
        Text(
          allDone
              ? 'Amazing work! Take a well-deserved rest.'
              : 'Plan your day and crush it with FocusLock',
          style: const TextStyle(color: Colors.white60, fontSize: 14),
        ),
        const SizedBox(height: 16),
        ElevatedButton.icon(
          onPressed: () => context.push('/daily-plan'),
          icon: const Icon(Icons.add_task_rounded),
          label: const Text('Plan My Day'),
          style: ElevatedButton.styleFrom(
            backgroundColor: Colors.white,
            foregroundColor: const Color(0xFF1A1A2E),
          ),
        ),
      ],
    ).animate().fadeIn(delay: 100.ms);
  }

  Widget _buildStatsStrip(
    BuildContext context,
    FocusSession session,
    String todayHours,
    int streak,
    int score,
  ) {
    return Row(
      children: [
        _statChip(
          context,
          icon: Icons.timer_rounded,
          color: Colors.blueAccent,
          value: '${todayHours}h',
          label: 'Focus',
          delay: 300,
        ),
        const SizedBox(width: 8),
        _statChip(
          context,
          icon: Icons.local_fire_department_rounded,
          color: Colors.orangeAccent,
          value: '$streak',
          label: 'Streak',
          delay: 350,
        ),
        const SizedBox(width: 8),
        _statChip(
          context,
          icon: Icons.stars_rounded,
          color: AppTheme.primary,
          value: '$score',
          label: 'Score',
          delay: 400,
        ),
        if (session.status == FocusStatus.active) ...[
          const SizedBox(width: 8),
          _statChip(
            context,
            icon: Icons.shield_rounded,
            color: AppTheme.error,
            value: '${session.blockedAttempts}',
            label: 'Blocked',
            delay: 450,
          ),
        ],
      ],
    );
  }

  Widget _statChip(
    BuildContext context, {
    required IconData icon,
    required Color color,
    required String value,
    required String label,
    required int delay,
  }) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 12),
        decoration: BoxDecoration(
          color: AppTheme.surface,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: Colors.white.withOpacity(0.06)),
        ),
        child: Column(
          children: [
            Icon(icon, color: color, size: 18),
            const SizedBox(height: 4),
            Text(
              value,
              style: const TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 16,
                color: Colors.white,
              ),
            ),
            Text(
              label,
              style: const TextStyle(
                fontSize: 10,
                color: AppTheme.textSecondary,
              ),
            ),
          ],
        ),
      ).animate().fadeIn(delay: delay.ms).scale(
        begin: const Offset(0.95, 0.95),
        end: const Offset(1, 1),
      ),
    );
  }

  Widget _buildTaskTile(
    BuildContext context,
    WidgetRef ref,
    FocusTask task,
    int index,
  ) {
    return GestureDetector(
      onTap: () => context.push('/set-focus', extra: {'task': task}),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        decoration: BoxDecoration(
          color: AppTheme.surface,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
            color: task.isCompleted
                ? Colors.white.withOpacity(0.04)
                : task.priority.color.withOpacity(0.25),
          ),
        ),
        child: Row(
          children: [
            Container(
              width: 4,
              height: 42,
              decoration: BoxDecoration(
                color: task.isCompleted
                    ? Colors.white24
                    : task.priority.color,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    task.title,
                    style: TextStyle(
                      fontWeight: FontWeight.w600,
                      color: task.isCompleted ? Colors.white38 : Colors.white,
                      decoration: task.isCompleted
                          ? TextDecoration.lineThrough
                          : null,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Row(
                    children: [
                      Icon(
                        task.priority.icon,
                        size: 11,
                        color: task.isCompleted
                            ? Colors.white24
                            : task.priority.color,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        '${task.priority.label} · ${task.estimatedMinutes}m',
                        style: TextStyle(
                          fontSize: 11,
                          color: task.isCompleted
                              ? Colors.white24
                              : AppTheme.textSecondary,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            GestureDetector(
              onTap: () {
                if (task.isCompleted) {
                  ref.read(tasksProvider.notifier).uncompleteTask(task.id);
                } else {
                  ref.read(tasksProvider.notifier).completeTask(task.id);
                }
              },
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 250),
                width: 26,
                height: 26,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: task.isCompleted
                      ? Colors.greenAccent.withOpacity(0.2)
                      : Colors.transparent,
                  border: Border.all(
                    color: task.isCompleted ? Colors.greenAccent : Colors.white30,
                    width: 2,
                  ),
                ),
                child: task.isCompleted
                    ? const Icon(
                        Icons.check_rounded,
                        size: 14,
                        color: Colors.greenAccent,
                      )
                    : null,
              ),
            ),
          ],
        ),
      ).animate().fadeIn(delay: (300 + index * 60).ms).slideX(
        begin: -0.04,
        end: 0,
      ),
    );
  }

  Widget _buildEmptyTasksCard(BuildContext context, WidgetRef ref) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: GestureDetector(
        onTap: () => _showAddTask(context),
        child: Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            color: AppTheme.surface,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: AppTheme.primary.withOpacity(0.2),
              style: BorderStyle.solid,
            ),
          ),
          child: Column(
            children: [
              const Icon(
                Icons.add_task_rounded,
                size: 40,
                color: AppTheme.primary,
              ),
              const SizedBox(height: 12),
              Text(
                'No tasks yet',
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                'Tap to add your first task and\nlet AI plan your day',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: AppTheme.textSecondary,
                ),
              ),
            ],
          ),
        ).animate().fadeIn(delay: 300.ms).scale(),
      ),
    );
  }

  Widget _buildContentShieldBanner(BuildContext context, WidgetRef ref) {
    final shield = ref.watch(contentShieldProvider);
    final isEnabled = shield.enabled;

    return GestureDetector(
      onTap: () => context.push('/content-shield'),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        decoration: BoxDecoration(
          gradient: isEnabled
              ? const LinearGradient(
                  colors: [Color(0xFF28112B), Color(0xFF1A1A2E)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                )
              : null,
          color: isEnabled ? null : AppTheme.surface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: isEnabled
                ? const Color(0xFFFF4B72).withOpacity(0.4)
                : Colors.white10,
          ),
        ),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: isEnabled
                    ? const Color(0xFFFF4B72).withOpacity(0.2)
                    : Colors.white10,
                shape: BoxShape.circle,
              ),
              child: Icon(
                Icons.shield_rounded,
                color: isEnabled ? const Color(0xFFFF4B72) : Colors.white54,
                size: 22,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Text(
                        'Content Shield',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 15,
                          color: Colors.white,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 6,
                          vertical: 2,
                        ),
                        decoration: BoxDecoration(
                          color: isEnabled
                              ? const Color(0xFFFF4B72).withOpacity(0.2)
                              : Colors.white10,
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: Text(
                          isEnabled
                              ? (shield.blockSexyContent ? 'STRICT 24/7' : 'ON')
                              : 'PAUSED',
                          style: TextStyle(
                            fontSize: 9,
                            fontWeight: FontWeight.bold,
                            color: isEnabled
                                ? const Color(0xFFFF4B72)
                                : Colors.white54,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 3),
                  Text(
                    isEnabled
                        ? 'Visual AI + Search/URL + SafeSearch · ${shield.totalBlockedCount} blocked'
                        : 'Tap to enable porn & half-naked photo/video blocker',
                    style: const TextStyle(
                      fontSize: 11,
                      color: AppTheme.textSecondary,
                    ),
                  ),
                ],
              ),
            ),
            const Icon(
              Icons.chevron_right_rounded,
              color: Colors.white54,
            ),
          ],
        ),
      ).animate().fadeIn(delay: 250.ms),
    );
  }

  void _showAddTask(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => const AddTaskBottomSheet(),
    );
  }
}
