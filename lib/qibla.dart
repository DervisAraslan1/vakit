import 'dart:async';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'native.dart';
import 'theme.dart';

const _kaabaLat = 21.422487, _kaabaLng = 39.826206;

double _rad(double d) => d * pi / 180;

/// Kâbe'nin bulunduğu yön, gerçek kuzeyden saat yönünde derece.
double qiblaBearing(double lat, double lng) {
  final p1 = _rad(lat), p2 = _rad(_kaabaLat), dl = _rad(_kaabaLng - lng);
  final y = sin(dl) * cos(p2);
  final x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl);
  return (atan2(y, x) * 180 / pi + 360) % 360;
}

double distanceToKaaba(double lat, double lng) {
  final h = pow(sin(_rad(_kaabaLat - lat) / 2), 2) +
      cos(_rad(lat)) * cos(_rad(_kaabaLat)) * pow(sin(_rad(_kaabaLng - lng) / 2), 2);
  return 2 * 6371 * asin(sqrt(h));
}

/// -180..180 arası en kısa açı farkı.
double _diff(double a, double b) => ((a - b + 540) % 360) - 180;

/// Kıble pusulası. Sensör yalnızca sekme görünürken ([active]) dinlenir.
class QiblaPage extends StatefulWidget {
  const QiblaPage({super.key, required this.state, required this.active});
  final AppState state;
  final bool active;

  @override
  State<QiblaPage> createState() => _QiblaPageState();
}

class _QiblaPageState extends State<QiblaPage> {
  StreamSubscription<({double heading, int accuracy})>? _sub;
  double? _heading;
  int _accuracy = 3;
  String? _error;
  bool _aligned = false;

  @override
  void initState() {
    super.initState();
    if (widget.active) _listen();
  }

  @override
  void didUpdateWidget(QiblaPage old) {
    super.didUpdateWidget(old);
    if (widget.active && _sub == null) _listen();
    if (!widget.active && _sub != null) _stop();
  }

  @override
  void dispose() {
    _stop();
    super.dispose();
  }

  void _listen() {
    _sub = Native.compass().listen(
      (e) {
        final h = _heading;
        // Titremeyi yumuşat: yeni okumaya yavaşça yaklaş, 359°→0° geçişinde zıplama.
        final next = h == null ? e.heading : (h + _diff(e.heading, h) * 0.18 + 360) % 360;
        final aligned = _diff(qiblaBearing(widget.state.lat, widget.state.lng), next).abs() < 4;
        if (aligned && !_aligned) HapticFeedback.mediumImpact();
        setState(() {
          _heading = next;
          _accuracy = e.accuracy;
          _aligned = aligned;
          _error = null;
        });
      },
      onError: (Object e) => setState(() => _error = e is PlatformException ? e.message : '$e'),
    );
  }

  void _stop() {
    _sub?.cancel();
    _sub = null;
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.state;
    final inset = MediaQuery.paddingOf(context);
    final qibla = qiblaBearing(s.lat, s.lng);
    final km = distanceToKaaba(s.lat, s.lng);
    final heading = _heading;
    final turn = heading == null ? null : _diff(qibla, heading);

    final String status;
    if (_error != null) {
      status = _error!;
    } else if (heading == null || turn == null) {
      status = 'Pusula hazırlanıyor…';
    } else if (_aligned) {
      status = 'Kıble yönündesin';
    } else {
      status = turn > 0 ? '${turn.round()}° sağa dön' : '${(-turn).round()}° sola dön';
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(height: inset.top + 18),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24),
          child: Text('Kıble', style: ts(30, w: 800, spacing: -0.5)),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(24, 2, 24, 0),
          child: Text(s.place, style: ts(13, w: 600, color: white(0.6))),
        ),
        Expanded(
          child: Center(
            child: LayoutBuilder(
              builder: (context, box) {
                final size = min(box.maxWidth * 0.86, box.maxHeight * 0.86);
                return SizedBox.square(
                  dimension: size,
                  child: CustomPaint(
                    painter: _DialPainter(heading: heading ?? 0, qibla: qibla, aligned: _aligned),
                  ),
                );
              },
            ),
          ),
        ),
        Center(
          child: AnimatedDefaultTextStyle(
            duration: const Duration(milliseconds: 200),
            style: ts(20, w: 800, color: _aligned ? gold : Colors.white),
            child: Text(status, textAlign: TextAlign.center),
          ),
        ),
        const SizedBox(height: 16),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Row(children: [
            Expanded(child: _Stat(label: 'Kıble açısı', value: '${qibla.round()}°')),
            const SizedBox(width: 10),
            Expanded(child: _Stat(label: "Mekke'ye", value: '${_thousands(km.round())} km')),
          ]),
        ),
        if (_accuracy <= 1 && heading != null)
          const _Note(
            icon: Icons.screen_rotation_alt_rounded,
            text: 'Pusula hassasiyeti düşük. Telefonu havada birkaç kez 8 çizerek salla.',
          )
        else if (!s.coordsExact)
          const _Note(
            icon: Icons.near_me_disabled_rounded,
            text: "Açı il merkezine göre hesaplandı. Ayarlar'dan konumu açarsan daha doğru olur.",
          ),
        const SizedBox(height: 18),
      ],
    );
  }

  static String _thousands(int n) =>
      n.toString().replaceAllMapped(RegExp(r'\B(?=(\d{3})+(?!\d))'), (_) => '.');
}

class _Stat extends StatelessWidget {
  const _Stat({required this.label, required this.value});
  final String label, value;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
        decoration: BoxDecoration(color: card, borderRadius: BorderRadius.circular(18)),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(label, style: ts(12, w: 600, color: white(0.55))),
          const SizedBox(height: 2),
          Text(value, style: ts(22, w: 800, tabular: true)),
        ]),
      );
}

class _Note extends StatelessWidget {
  const _Note({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(24, 12, 24, 0),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Icon(icon, size: 16, color: white(0.5)),
          const SizedBox(width: 8),
          Expanded(child: Text(text, style: ts(12, w: 500, color: white(0.55), height: 1.4))),
        ]),
      );
}

/// Kadran: telefonla birlikte döner; Kâbe işareti kıble açısında, üstteki üçgen telefonun baktığı yön.
class _DialPainter extends CustomPainter {
  _DialPainter({required this.heading, required this.qibla, required this.aligned});
  final double heading, qibla;
  final bool aligned;

  @override
  void paint(Canvas canvas, Size size) {
    final c = size.center(Offset.zero);
    final r = size.width / 2;

    // Zemin ve hizalanınca altın parıltı.
    canvas.drawCircle(
      c,
      r,
      Paint()
        ..shader = RadialGradient(
          colors: [aligned ? const Color(0xFF3A3420) : const Color(0xFF16204A), card],
        ).createShader(Rect.fromCircle(center: c, radius: r)),
    );
    canvas.drawCircle(
      c,
      r - 1,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2
        ..color = aligned ? gold : white(0.1),
    );

    canvas.save();
    canvas.translate(c.dx, c.dy);
    canvas.rotate(-_rad(heading));

    // Derece çizgileri: her 5°, 30°'de uzun.
    final tick = Paint()..strokeCap = StrokeCap.round;
    for (var d = 0; d < 360; d += 5) {
      final long = d % 30 == 0;
      tick
        ..color = white(long ? 0.6 : 0.22)
        ..strokeWidth = long ? 2.4 : 1.4;
      final a = _rad(d.toDouble());
      final dir = Offset(sin(a), -cos(a));
      canvas.drawLine(dir * (r - 14), dir * (r - (long ? 30 : 22)), tick);
    }

    // Yön harfleri; kuzey kırmızı.
    const letters = {0: 'K', 90: 'D', 180: 'G', 270: 'B'};
    for (final e in letters.entries) {
      final a = _rad(e.key.toDouble());
      final p = Offset(sin(a), -cos(a)) * (r - 52);
      canvas.save();
      canvas.translate(p.dx, p.dy);
      canvas.rotate(a);
      final tp = TextPainter(
        text: TextSpan(
          text: e.value,
          style: ts(18, w: 800, color: e.key == 0 ? const Color(0xFFFF7A6B) : white(0.8)),
        ),
        textDirection: TextDirection.ltr,
      )..layout();
      tp.paint(canvas, Offset(-tp.width / 2, -tp.height / 2));
      canvas.restore();
    }

    // Kıble çizgisi ve Kâbe işareti.
    final q = _rad(qibla);
    final qDir = Offset(sin(q), -cos(q));
    final k = qDir * (r - 64);
    canvas.drawLine(
      Offset.zero,
      k,
      Paint()
        ..strokeWidth = 5
        ..strokeCap = StrokeCap.round
        ..shader = LinearGradient(colors: [gold.withValues(alpha: 0), gold]).createShader(Rect.fromPoints(Offset.zero, k)),
    );
    canvas.save();
    canvas.translate(k.dx, k.dy);
    canvas.rotate(q);
    const s = 13.0;
    canvas.drawRRect(
      RRect.fromRectAndRadius(const Rect.fromLTWH(-s, -s, s * 2, s * 2), const Radius.circular(3)),
      Paint()..color = const Color(0xFF111111),
    );
    canvas.drawRect(const Rect.fromLTWH(-s, -s + 6, s * 2, 4), Paint()..color = gold);
    canvas.drawRect(const Rect.fromLTWH(3, 1, 6, 10), Paint()..color = gold);
    canvas.restore();

    canvas.restore();

    // Merkez göbek ve telefonun baktığı yönü gösteren sabit üçgen.
    final fixed = Paint()..color = aligned ? gold : Colors.white;
    canvas.drawCircle(c, 7, fixed);
    canvas.drawPath(
      Path()
        ..moveTo(c.dx, 26)
        ..lineTo(c.dx - 11, 8)
        ..lineTo(c.dx + 11, 8)
        ..close(),
      fixed,
    );
  }

  @override
  bool shouldRepaint(_DialPainter old) => old.heading != heading || old.qibla != qibla || old.aligned != aligned;
}
