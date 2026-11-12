import 'dart:math';

import 'package:flutter/material.dart';

import 'native.dart';

/// Günün bölümü; gökyüzünün rengini belirler.
enum Phase { night, dawn, day, afternoon, dusk }

Phase phaseOf(AppState s) => switch (s.current) {
      0 => Phase.dawn, // imsak – güneş
      1 || 2 => Phase.day, // güneş – ikindi
      3 => Phase.afternoon, // ikindi – akşam
      4 => Phase.dusk, // akşam – yatsı
      _ => Phase.night,
    };

List<Color> skyColors(Phase p) => switch (p) {
      Phase.night => const [Color(0xFF050818), Color(0xFF0E1846), Color(0xFF233371)],
      Phase.dawn => const [Color(0xFF141C52), Color(0xFF6B4C93), Color(0xFFF2A65A)],
      Phase.day => const [Color(0xFF1F58B8), Color(0xFF4A8ADF), Color(0xFFA9CCF5)],
      Phase.afternoon => const [Color(0xFF2B5CAE), Color(0xFF7CA3DA), Color(0xFFF6C98A)],
      Phase.dusk => const [Color(0xFF151A52), Color(0xFF8A4A8C), Color(0xFFF08A5D)],
    };

/// Güneşin ya da ayın gökyüzündeki yolculuğu, 0 (doğuş) – 1 (batış).
double orbProgress(AppState s) {
  final now = DateTime.now();
  final sunrise = s.times[1];
  final sunset = s.times[4];
  double span(DateTime from, DateTime to) =>
      (now.difference(from).inSeconds / max(1, to.difference(from).inSeconds)).clamp(0.0, 1.0);
  if (!s.night) return span(sunrise, sunset);
  const day = Duration(days: 1);
  return now.isBefore(sunrise) ? span(sunset.subtract(day), sunrise) : span(sunset, sunrise.add(day));
}

/// Canlı gökyüzü: vakte göre renk, kırpışan yıldızlar, yay çizen güneş/ay.
class Sky extends StatefulWidget {
  const Sky({super.key, required this.state, required this.orbBand});
  final AppState state;

  /// Güneşin/ayın dolaştığı yatay şeridin üst ve alt sınırı (piksel).
  final (double, double) orbBand;

  @override
  State<Sky> createState() => _SkyState();
}

class _SkyState extends State<Sky> with SingleTickerProviderStateMixin {
  late final _anim = AnimationController(vsync: this, duration: const Duration(seconds: 9))..repeat();

  @override
  void dispose() {
    _anim.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.state;
    final phase = phaseOf(s);
    return RepaintBoundary(
      child: CustomPaint(
        size: Size.infinite,
        painter: _SkyPainter(
          anim: _anim,
          colors: skyColors(phase),
          stars: switch (phase) { Phase.night => 1.0, Phase.dawn || Phase.dusk => 0.4, _ => 0.0 },
          warmHorizon: phase == Phase.dawn || phase == Phase.dusk || phase == Phase.afternoon,
          moon: s.night,
          progress: orbProgress(s),
          band: widget.orbBand,
        ),
      ),
    );
  }
}

class _SkyPainter extends CustomPainter {
  _SkyPainter({
    required this.anim,
    required this.colors,
    required this.stars,
    required this.warmHorizon,
    required this.moon,
    required this.progress,
    required this.band,
  }) : super(repaint: anim);

  final Animation<double> anim;
  final List<Color> colors;
  final double stars;
  final bool warmHorizon;
  final bool moon;
  final double progress;
  final (double, double) band;

  /// [x, y, boyut, faz] — her açılışta aynı gökyüzü.
  static final _stars = List.generate(90, (i) {
    final r = Random(i * 7 + 3);
    return [r.nextDouble(), r.nextDouble() * 0.6, 0.5 + r.nextDouble() * 1.3, r.nextDouble()];
  });

  @override
  void paint(Canvas canvas, Size size) {
    final t = anim.value;
    final sky = Rect.fromLTWH(0, 0, size.width, size.height * 0.78);
    canvas.drawRect(
      Offset.zero & size,
      Paint()
        ..shader = LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: colors,
          stops: const [0, 0.5, 1],
        ).createShader(sky),
    );

    if (warmHorizon) {
      final center = Offset(size.width / 2, sky.bottom);
      canvas.drawCircle(
        center,
        size.width * 0.9,
        Paint()
          ..shader = RadialGradient(
            colors: [colors[2].withValues(alpha: 0.55), colors[2].withValues(alpha: 0)],
          ).createShader(Rect.fromCircle(center: center, radius: size.width * 0.9)),
      );
    }

    if (stars > 0) {
      final p = Paint();
      for (final s in _stars) {
        final twinkle = 0.55 + 0.45 * sin(2 * pi * (t + s[3]));
        p.color = Colors.white.withValues(alpha: stars * twinkle * 0.9);
        canvas.drawCircle(Offset(s[0] * size.width, s[1] * size.height), s[2], p);
      }
    }

    // Güneş/ay soldan sağa bir yay çizer; tepe noktası şeridin üst sınırıdır.
    final x = size.width * (0.14 + 0.72 * progress);
    final y = band.$2 - (band.$2 - band.$1) * sin(pi * progress);
    final c = Offset(x, y);
    final pulse = 1 + 0.06 * sin(2 * pi * t);
    final glow = moon ? const Color(0xFFBFD0FF) : const Color(0xFFFFE9A8);
    canvas.drawCircle(
      c,
      70 * pulse,
      Paint()
        ..shader = RadialGradient(
          colors: [glow.withValues(alpha: moon ? 0.28 : 0.5), glow.withValues(alpha: 0)],
        ).createShader(Rect.fromCircle(center: c, radius: 70 * pulse)),
    );
    if (moon) {
      final disc = Path()..addOval(Rect.fromCircle(center: c, radius: 15));
      final cut = Path()..addOval(Rect.fromCircle(center: c + const Offset(7, -4), radius: 13));
      canvas.drawPath(
        Path.combine(PathOperation.difference, disc, cut),
        Paint()..color = const Color(0xFFF6E7B4),
      );
    } else {
      canvas.drawCircle(c, 15, Paint()..color = const Color(0xFFFFDF8A));
    }
  }

  @override
  bool shouldRepaint(_SkyPainter old) =>
      old.colors != colors || old.progress != progress || old.moon != moon || old.band != band;
}
