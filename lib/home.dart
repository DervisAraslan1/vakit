import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'native.dart';
import 'settings.dart';
import 'sky.dart';
import 'theme.dart';

/// Bir vaktin bildirimini açar/kapatır; bildirimler kapalıysa önce izin ister.
Future<void> togglePrayer(BuildContext context, AppState s, int i) async {
  HapticFeedback.selectionClick();
  if (!s.notif) {
    await Native.set('prayerOn', true, index: i);
    final ok = await Native.enableNotifications();
    if (!ok && context.mounted) toast(context, 'Bildirim izni verilmedi');
  } else {
    await Native.set('prayerOn', !s.prayerOn[i], index: i);
  }
  await app.load();
}

/// Ana ekran: canlı gökyüzü, geri sayım, kaydırılarak değişen cami ve vakit kartları.
class HomePage extends StatefulWidget {
  const HomePage({super.key, required this.state});
  final AppState state;

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  late final PageController _pages = PageController(initialPage: _mosqueIndex(widget.state));

  int _mosqueIndex(AppState s) => s.mosques.indexWhere((m) => m.id == s.mosque).clamp(0, s.mosques.length - 1);

  @override
  void didUpdateWidget(HomePage old) {
    super.didUpdateWidget(old);
    // Cami başka bir yerden (Ayarlar) değiştiyse sayfayı ona getir.
    final index = _mosqueIndex(widget.state);
    if (_pages.hasClients && _pages.page?.round() != index) _pages.jumpToPage(index);
  }

  @override
  void dispose() {
    _pages.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.state;
    final inset = MediaQuery.paddingOf(context);
    final height = MediaQuery.sizeOf(context).height;

    return Stack(
      children: [
        Positioned.fill(child: Sky(state: s, orbBand: (inset.top + 66, inset.top + 126))),
        // Gökyüzünü aşağıda sayfa rengine eritir; kartlar bu koyu zemine oturur.
        Positioned(
          left: 0,
          right: 0,
          bottom: 0,
          height: height * 0.5,
          child: const IgnorePointer(
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [Color(0x00070B1E), page],
                  stops: [0, 0.6],
                ),
              ),
            ),
          ),
        ),
        Column(
          children: [
            SizedBox(height: inset.top + 10),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Row(
                children: [
                  GestureDetector(
                    onTap: () => changeCity(context, s),
                    child: Container(
                      padding: const EdgeInsets.fromLTRB(12, 9, 10, 9),
                      decoration: BoxDecoration(
                        color: const Color(0xFF081230).withValues(alpha: 0.35),
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(color: white(0.12)),
                      ),
                      child: Row(mainAxisSize: MainAxisSize.min, children: [
                        Icon(s.autoLocation ? Icons.near_me_rounded : Icons.place_rounded, size: 16, color: gold),
                        const SizedBox(width: 6),
                        Text(s.place, style: ts(14, w: 700)),
                        Icon(Icons.expand_more_rounded, size: 18, color: white(0.7)),
                      ]),
                    ),
                  ),
                  const Spacer(),
                  Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
                    Text(s.date, style: ts(12, w: 600, color: white(0.9))),
                    if (s.hijri.isNotEmpty) Text(s.hijri, style: ts(11, w: 500, color: white(0.65))),
                  ]),
                ],
              ),
            ),
            const SizedBox(height: 92),
            if (s.ramadanDay != null)
              Padding(
                padding: const EdgeInsets.only(bottom: 6),
                child: Text('Ramazan · ${s.ramadanDay}. gün', style: ts(13, w: 800, color: gold, spacing: 0.4)),
              ),
            Text('${s.nextLabel} kalan', style: ts(16, w: 600, color: white(0.9))),
            _Countdown(target: s.nextTime),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
              decoration: BoxDecoration(color: white(0.14), borderRadius: BorderRadius.circular(12)),
              child: Text(
                '${s.afterIsha ? 'Yarın' : 'Bugün'} ${s.nextText}',
                style: ts(12, w: 700, tabular: true),
              ),
            ),
            Expanded(child: _MosquePager(state: s, controller: _pages)),
            _TimesPanel(state: s),
            SizedBox(height: inset.bottom + 12),
          ],
        ),
      ],
    );
  }
}

/// Saniye saniye akan büyük geri sayım.
class _Countdown extends StatelessWidget {
  const _Countdown({required this.target});
  final DateTime target;

  @override
  Widget build(BuildContext context) => StreamBuilder<void>(
        stream: Stream.periodic(const Duration(seconds: 1)),
        builder: (context, _) {
          var left = target.difference(DateTime.now());
          if (left.isNegative) left = Duration.zero;
          String two(int n) => n.toString().padLeft(2, '0');
          final h = left.inHours;
          final text = '${h > 0 ? '$h:' : ''}${two(left.inMinutes % 60)}:${two(left.inSeconds % 60)}';
          return FittedBox(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: Text(text, style: ts(72, w: 800, tabular: true, spacing: -2.5, height: 1.15)),
            ),
          );
        },
      );
}

/// Sağa sola kaydırarak cami değiştirme.
class _MosquePager extends StatelessWidget {
  const _MosquePager({required this.state, required this.controller});
  final AppState state;
  final PageController controller;

  @override
  Widget build(BuildContext context) {
    final s = state;
    final name = s.mosques.firstWhere((m) => m.id == s.mosque, orElse: () => s.mosques.first).name;
    return Stack(
      children: [
        PageView.builder(
          controller: controller,
          itemCount: s.mosques.length,
          onPageChanged: (i) async {
            HapticFeedback.selectionClick();
            await Native.set('mosque', s.mosques[i].id);
            await app.load();
          },
          itemBuilder: (context, i) => Padding(
            padding: const EdgeInsets.fromLTRB(28, 26, 28, 6),
            child: FutureBuilder(
              future: Native.mosqueImage(s.mosques[i].id, s.night),
              builder: (context, snap) => snap.hasData
                  ? Image.memory(
                      snap.data!,
                      fit: BoxFit.contain,
                      alignment: Alignment.bottomCenter,
                      gaplessPlayback: true,
                      filterQuality: FilterQuality.medium,
                    )
                  : const SizedBox.expand(),
            ),
          ),
        ),
        Positioned(
          top: 6,
          left: 0,
          right: 0,
          child: IgnorePointer(
            child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
              Icon(Icons.chevron_left_rounded, size: 16, color: white(0.5)),
              Text(name, style: ts(12, w: 700, color: white(0.8), spacing: 0.3)),
              Icon(Icons.chevron_right_rounded, size: 16, color: white(0.5)),
            ]),
          ),
        ),
      ],
    );
  }
}

/// Altı vakit: buzlu cam panel içinde ilerleme çubuğu ve 3×2 kartlar.
class _TimesPanel extends StatelessWidget {
  const _TimesPanel({required this.state});
  final AppState state;

  static const _icons = [
    Icons.bedtime_rounded,
    Icons.wb_twilight_rounded,
    Icons.wb_sunny_rounded,
    Icons.light_mode_outlined,
    Icons.wb_twilight_rounded,
    Icons.nights_stay_rounded,
  ];

  @override
  Widget build(BuildContext context) {
    final s = state;
    Widget tile(int i) {
      final on = i == s.current;
      final passed = s.afterIsha ? i < 5 : s.next > 0 && i < s.current;
      final bell = s.notif && s.prayerOn[i];
      final fg = on ? ink : Colors.white;
      return Expanded(
        child: Opacity(
          opacity: passed ? 0.5 : 1,
          child: Container(
            height: 76,
            decoration: BoxDecoration(
              gradient: on ? const LinearGradient(colors: [Color(0xFFFFE3A3), gold]) : null,
              color: on ? null : white(0.07),
              borderRadius: BorderRadius.circular(18),
            ),
            child: Stack(children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(12, 10, 0, 0),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Row(children: [
                    Icon(_icons[i], size: 14, color: fg.withValues(alpha: 0.75)),
                    const SizedBox(width: 5),
                    Text(prayerNames[i], style: ts(12, w: 700, color: fg.withValues(alpha: 0.8))),
                  ]),
                  const SizedBox(height: 4),
                  Text(s.timeTexts[i], style: ts(22, w: 800, color: fg, tabular: true, spacing: -0.5)),
                ]),
              ),
              Positioned(
                top: 0,
                right: 0,
                child: Semantics(
                  button: true,
                  label: '${prayerNames[i]} bildirimi ${bell ? 'açık' : 'kapalı'}',
                  child: GestureDetector(
                    behavior: HitTestBehavior.opaque,
                    onTap: () => togglePrayer(context, s, i),
                    child: Padding(
                      padding: const EdgeInsets.all(9),
                      child: Icon(
                        bell ? Icons.notifications_rounded : Icons.notifications_off_outlined,
                        size: 16,
                        color: bell ? (on ? ink : gold) : fg.withValues(alpha: 0.35),
                      ),
                    ),
                  ),
                ),
              ),
            ]),
          ),
        ),
      );
    }

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(28),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
          child: Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: const Color(0xFF0C1330).withValues(alpha: 0.7),
              borderRadius: BorderRadius.circular(28),
              border: Border.all(color: white(0.08)),
            ),
            child: Column(children: [
              _Progress(state: s),
              const SizedBox(height: 12),
              Row(children: [tile(0), const SizedBox(width: 8), tile(1), const SizedBox(width: 8), tile(2)]),
              const SizedBox(height: 8),
              Row(children: [tile(3), const SizedBox(width: 8), tile(4), const SizedBox(width: 8), tile(5)]),
            ]),
          ),
        ),
      ),
    );
  }
}

/// İçinde bulunulan vaktin ne kadarının geçtiğini gösteren çubuk.
class _Progress extends StatelessWidget {
  const _Progress({required this.state});
  final AppState state;

  @override
  Widget build(BuildContext context) {
    final s = state;
    return StreamBuilder<void>(
      stream: Stream.periodic(const Duration(seconds: 20)),
      builder: (context, _) {
        final total = s.nextTime.difference(s.currentStart).inSeconds;
        final done = DateTime.now().difference(s.currentStart).inSeconds;
        final value = total <= 0 ? 0.0 : (done / total).clamp(0.0, 1.0);
        return Padding(
          padding: const EdgeInsets.fromLTRB(6, 4, 6, 0),
          child: Row(children: [
            Text(prayerNames[s.current], style: ts(11, w: 700, color: gold)),
            const SizedBox(width: 10),
            Expanded(
              child: ClipRRect(
                borderRadius: BorderRadius.circular(3),
                child: LinearProgressIndicator(
                  value: value,
                  minHeight: 5,
                  backgroundColor: white(0.1),
                  color: gold,
                ),
              ),
            ),
            const SizedBox(width: 10),
            Text(prayerNames[s.next], style: ts(11, w: 700, color: white(0.6))),
          ]),
        );
      },
    );
  }
}
