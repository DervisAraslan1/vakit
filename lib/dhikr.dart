import 'dart:convert';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'kaza.dart';
import 'native.dart';
import 'theme.dart';

class _Dhikr {
  const _Dhikr(this.id, this.name, this.arabic, this.meaning);
  final String id, name, arabic, meaning;
}

const _dhikrs = [
  _Dhikr('subhanallah', 'Sübhânallah', 'سُبْحَانَ ٱللَّٰهِ', "Allah'ı her türlü eksiklikten tenzih ederim"),
  _Dhikr('elhamdulillah', 'Elhamdülillah', 'ٱلْحَمْدُ لِلَّٰهِ', "Hamd Allah'a mahsustur"),
  _Dhikr('allahuekber', 'Allâhu Ekber', 'ٱللَّٰهُ أَكْبَرُ', 'Allah en büyüktür'),
  _Dhikr('tevhid', 'Lâ ilâhe illallah', 'لَا إِلَٰهَ إِلَّا ٱللَّٰهُ', "Allah'tan başka ilah yoktur"),
  _Dhikr('estagfirullah', 'Estağfirullah', 'أَسْتَغْفِرُ ٱللَّٰهَ', "Allah'tan bağışlanma dilerim"),
  _Dhikr('salavat', 'Salavat', 'ٱللَّٰهُمَّ صَلِّ عَلَىٰ مُحَمَّدٍ', "Allah'ım, Muhammed'e salât eyle"),
];

/// Hedef seçenekleri; 0 = serbest (hedefsiz).
const _targets = [33, 99, 100, 0];

/// Zikirmatik: halkaya her dokunuş bir sayar, hedefe varınca tur atar.
class DhikrPage extends StatefulWidget {
  const DhikrPage({super.key});

  @override
  State<DhikrPage> createState() => _DhikrPageState();
}

class _DhikrPageState extends State<DhikrPage> with TickerProviderStateMixin {
  var _selected = _dhikrs.first.id;
  var _target = 33;

  /// Üstteki seçim: zikirmatik ya da kaza takibi.
  var _kaza = false;
  final _counts = <String, int>{};

  late final _pop = AnimationController(vsync: this, duration: const Duration(milliseconds: 160));
  late final _ripple = AnimationController(vsync: this, duration: const Duration(milliseconds: 700));

  int get _count => _counts[_selected] ?? 0;

  @override
  void initState() {
    super.initState();
    _restore();
  }

  @override
  void dispose() {
    _pop.dispose();
    _ripple.dispose();
    super.dispose();
  }

  Future<void> _restore() async {
    final raw = await Native.getString('dhikr');
    if (raw == null || !mounted) return;
    try {
      final data = jsonDecode(raw) as Map<String, dynamic>;
      setState(() {
        _selected = data['selected'] as String? ?? _selected;
        _target = data['target'] as int? ?? _target;
        _counts.addAll((data['counts'] as Map<String, dynamic>).cast<String, int>());
      });
    } on Object {
      // Bozuk kayıt: sıfırdan başla.
    }
  }

  void _save() => Native.setString(
        'dhikr',
        jsonEncode({'selected': _selected, 'target': _target, 'counts': _counts}),
      );

  void _tap() {
    setState(() => _counts[_selected] = _count + 1);
    final lapDone = _target > 0 && _count % _target == 0;
    if (lapDone) {
      HapticFeedback.heavyImpact();
      _ripple.forward(from: 0);
    } else {
      HapticFeedback.lightImpact();
    }
    _pop.forward(from: 0).then((_) => _pop.reverse());
    _save();
  }

  Future<void> _reset() async {
    if (_count == 0) return;
    final yes = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: card,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
        title: Text('Sayaç sıfırlansın mı?', style: ts(18, w: 800)),
        content: Text(
          '${_dhikrs.firstWhere((d) => d.id == _selected).name}: $_count',
          style: ts(14, color: white(0.7)),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text('Vazgeç', style: ts(14, w: 700, color: white(0.7))),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text('Sıfırla', style: ts(14, w: 800, color: gold)),
          ),
        ],
      ),
    );
    if (yes == true) {
      setState(() => _counts[_selected] = 0);
      _save();
    }
  }

  @override
  Widget build(BuildContext context) {
    final inset = MediaQuery.paddingOf(context);
    final dhikr = _dhikrs.firstWhere((d) => d.id == _selected, orElse: () => _dhikrs.first);
    final count = _count;
    final laps = _target > 0 ? count ~/ _target : 0;
    // Tur tamamlandığında halka, bir sonraki dokunuşa kadar dolu kalır.
    final progress = _target == 0 || count == 0 ? 0.0 : ((count - 1) % _target + 1) / _target;

    return DecoratedBox(
      decoration: const BoxDecoration(
        gradient: RadialGradient(
          center: Alignment(0, -0.15),
          radius: 1.1,
          colors: [Color(0xFF1A2760), page],
        ),
      ),
      child: Column(
        children: [
          SizedBox(height: inset.top + 14),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 12, 0),
            child: Row(children: [
              Expanded(child: _Segment(kaza: _kaza, onChanged: (v) => setState(() => _kaza = v))),
              const SizedBox(width: 6),
              Opacity(
                opacity: _kaza ? 0 : 1,
                child: IconButton(
                  onPressed: _kaza ? null : _reset,
                  tooltip: 'Sayacı sıfırla',
                  icon: Icon(Icons.restart_alt_rounded, color: white(count == 0 ? 0.25 : 0.8)),
                ),
              ),
            ]),
          ),
          const SizedBox(height: 12),
          if (_kaza) const Expanded(child: KazaPage()) else ...[
          SizedBox(
            height: 40,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              itemCount: _dhikrs.length,
              separatorBuilder: (_, _) => const SizedBox(width: 8),
              itemBuilder: (context, i) => Chip2(
                label: _dhikrs[i].name,
                selected: _dhikrs[i].id == _selected,
                onTap: () {
                  setState(() => _selected = _dhikrs[i].id);
                  _save();
                },
              ),
            ),
          ),
          Expanded(
            child: Center(
              child: LayoutBuilder(
                builder: (context, box) {
                  final size = min(box.maxWidth * 0.8, box.maxHeight * 0.92);
                  return Semantics(
                    button: true,
                    label: '${dhikr.name}, $count. Saymak için dokun',
                    child: GestureDetector(
                      onTapDown: (_) => _tap(),
                      child: AnimatedBuilder(
                        animation: Listenable.merge([_pop, _ripple]),
                        builder: (context, _) => CustomPaint(
                          size: Size.square(size),
                          painter: _RingPainter(progress: progress, ripple: _ripple.value),
                          child: SizedBox.square(
                            dimension: size,
                            child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                              Text(
                                dhikr.arabic,
                                textDirection: TextDirection.rtl,
                                style: TextStyle(fontSize: size * 0.085, color: white(0.9), height: 1.4),
                              ),
                              Transform.scale(
                                scale: 1 + 0.09 * Curves.easeOut.transform(_pop.value),
                                child: Text(
                                  '$count',
                                  style: ts(size * 0.27, w: 800, tabular: true, spacing: -3, height: 1.1),
                                ),
                              ),
                              Text(
                                _target == 0 ? 'Serbest' : '$_target hedef  ·  $laps tur',
                                style: ts(13, w: 700, color: gold),
                              ),
                            ]),
                          ),
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 32),
            child: Text(
              dhikr.meaning,
              textAlign: TextAlign.center,
              style: ts(13, w: 500, color: white(0.6), height: 1.4),
            ),
          ),
          const SizedBox(height: 18),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Row(children: [
              for (final t in _targets) ...[
                Expanded(
                  child: Chip2(
                    label: t == 0 ? 'Serbest' : '$t',
                    selected: t == _target,
                    onTap: () {
                      setState(() => _target = t);
                      _save();
                    },
                  ),
                ),
                if (t != _targets.last) const SizedBox(width: 8),
              ],
            ]),
          ),
          SizedBox(height: inset.bottom + 20),
          ],
        ],
      ),
    );
  }
}

class _RingPainter extends CustomPainter {
  _RingPainter({required this.progress, required this.ripple});
  final double progress;

  /// Tur tamamlanınca dışa doğru yayılan halka, 0–1.
  final double ripple;

  @override
  void paint(Canvas canvas, Size size) {
    final center = size.center(Offset.zero);
    const stroke = 16.0;
    final radius = size.width / 2 - stroke;

    // İç yüzey: dokunma alanını belli eden hafif dolgu.
    canvas.drawCircle(center, radius - stroke, Paint()..color = Colors.white.withValues(alpha: 0.04));
    canvas.drawCircle(
      center,
      radius,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = stroke
        ..color = Colors.white.withValues(alpha: 0.08),
    );

    if (progress > 0) {
      final arc = Rect.fromCircle(center: center, radius: radius);
      final sweep = 2 * pi * progress;
      final paint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = stroke
        ..strokeCap = StrokeCap.round;
      canvas.drawArc(
        arc,
        -pi / 2,
        sweep,
        false,
        paint
          ..color = gold.withValues(alpha: 0.45)
          ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 14),
      );
      canvas.drawArc(
        arc,
        -pi / 2,
        sweep,
        false,
        paint
          ..color = gold
          ..maskFilter = null,
      );
    }

    if (ripple > 0 && ripple < 1) {
      canvas.drawCircle(
        center,
        radius + stroke + 40 * ripple,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 3
          ..color = gold.withValues(alpha: 0.6 * (1 - ripple)),
      );
    }
  }

  @override
  bool shouldRepaint(_RingPainter old) => old.progress != progress || old.ripple != ripple;
}

/// Zikirmatik / Kaza seçimi.
class _Segment extends StatelessWidget {
  const _Segment({required this.kaza, required this.onChanged});
  final bool kaza;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    Widget item(String label, bool value) => Expanded(
          child: Semantics(
            selected: kaza == value,
            button: true,
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: () {
                HapticFeedback.selectionClick();
                onChanged(value);
              },
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 180),
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: kaza == value ? gold : Colors.transparent,
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Text(label, style: ts(14, w: 800, color: kaza == value ? ink : white(0.7))),
              ),
            ),
          ),
        );
    return Container(
      height: 46,
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(color: white(0.07), borderRadius: BorderRadius.circular(18)),
      child: Row(children: [item('Zikirmatik', false), item('Kaza takibi', true)]),
    );
  }
}
