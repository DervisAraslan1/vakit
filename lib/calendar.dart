import 'package:flutter/material.dart';

import 'native.dart';
import 'theme.dart';

const _months = ['Oca', 'Şub', 'Mar', 'Nis', 'May', 'Haz', 'Tem', 'Ağu', 'Eyl', 'Eki', 'Kas', 'Ara'];
const _weekdays = ['Pazartesi', 'Salı', 'Çarşamba', 'Perşembe', 'Cuma', 'Cumartesi', 'Pazar'];

/// Hicri tarihe göre o güne denk gelen dini gün ya da gece; yoksa null.
/// Kandiller, hicri günün başladığı akşamın miladi gününde gösterilir.
String? specialDay(DayRow day, DayRow? next) {
  final d = day.hijriDay, m = day.hijriMonth;
  if (d == null || m == null) return null;
  final found = <String>[];
  if (m == 1 && d == 1) found.add('Hicri Yılbaşı');
  if (m == 1 && d == 10) found.add('Aşure Günü');
  if (m == 3 && d == 11) found.add('Mevlid Kandili');
  if (m == 7 && d == 1) found.add('Üç Ayların Başlangıcı');
  // Regaib: Recep ayının ilk cumasına bağlanan perşembe gecesi.
  if (day.date.weekday == DateTime.thursday && next?.hijriMonth == 7 && (next?.hijriDay ?? 99) <= 7) {
    found.add('Regaib Kandili');
  }
  if (m == 7 && d == 26) found.add('Miraç Kandili');
  if (m == 8 && d == 14) found.add('Berat Kandili');
  if (m == 9 && d == 1) found.add('Ramazan Başlangıcı');
  if (m == 9 && d == 26) found.add('Kadir Gecesi');
  if (m == 9 && next?.hijriMonth == 10) found.add('Ramazan Bayramı Arefesi');
  if (m == 10 && d <= 3) found.add('Ramazan Bayramı $d. Gün');
  if (m == 12 && d == 9) found.add('Kurban Bayramı Arefesi');
  if (m == 12 && d >= 10 && d <= 13) found.add('Kurban Bayramı ${d - 9}. Gün');
  return found.isEmpty ? null : found.join(' · ');
}

/// 30 günlük vakit çizelgesi, hicri tarihler ve dini günlerle.
class CalendarPage extends StatefulWidget {
  const CalendarPage({super.key, required this.state});
  final AppState state;

  @override
  State<CalendarPage> createState() => _CalendarPageState();
}

class _CalendarPageState extends State<CalendarPage> {
  List<DayRow>? _days;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void didUpdateWidget(CalendarPage old) {
    super.didUpdateWidget(old);
    final a = old.state, b = widget.state;
    // Konum, veri kaynağı ya da gün değiştiyse çizelge de değişmiştir.
    if (a.place != b.place || a.official != b.official || a.date != b.date) _load();
  }

  Future<void> _load() async {
    final days = await Native.days(30);
    if (mounted) setState(() => _days = days);
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.state;
    final inset = MediaQuery.paddingOf(context);
    final days = _days;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(height: inset.top + 18),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24),
          child: Text('Takvim', style: ts(30, w: 800, spacing: -0.5)),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(24, 2, 24, 14),
          child: Text(
            [s.place, if (s.hijri.isNotEmpty) s.hijri].join(' · '),
            style: ts(13, w: 600, color: white(0.6)),
          ),
        ),
        if (!s.official)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
            child: Container(
              padding: const EdgeInsets.fromLTRB(16, 12, 8, 12),
              decoration: BoxDecoration(
                color: gold.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(18),
                border: Border.all(color: gold.withValues(alpha: 0.35)),
              ),
              child: Row(children: [
                Expanded(
                  child: Text(
                    'İnternet yok: vakitler hesapla gösteriliyor, hicri tarih ve dini günler eksik.',
                    style: ts(12, w: 600, height: 1.4),
                  ),
                ),
                TextButton(
                  onPressed: () async {
                    final ok = await Native.refresh();
                    await app.load();
                    if (!ok && context.mounted) toast(context, 'Bağlantı kurulamadı');
                  },
                  child: Text('Yenile', style: ts(13, w: 800, color: gold)),
                ),
              ]),
            ),
          ),
        // Sütun başlıkları kartlardaki saat sütunlarıyla aynı hizada durur.
        Padding(
          padding: const EdgeInsets.fromLTRB(30, 0, 30, 8),
          child: Row(children: [
            const SizedBox(width: 58),
            for (final n in prayerNames)
              Expanded(
                child: Text(n, textAlign: TextAlign.center, style: ts(11, w: 700, color: white(0.5))),
              ),
          ]),
        ),
        Expanded(
          child: days == null
              ? const Center(child: CircularProgressIndicator(color: gold))
              : ListView.separated(
                  padding: EdgeInsets.fromLTRB(16, 0, 16, inset.bottom + 16),
                  itemCount: days.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 8),
                  itemBuilder: (context, i) => _DayCard(
                    day: days[i],
                    special: specialDay(days[i], i + 1 < days.length ? days[i + 1] : null),
                    today: i == 0,
                  ),
                ),
        ),
      ],
    );
  }
}

class _DayCard extends StatelessWidget {
  const _DayCard({required this.day, required this.special, required this.today});
  final DayRow day;
  final String? special;
  final bool today;

  @override
  Widget build(BuildContext context) {
    final friday = day.date.weekday == DateTime.friday;
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
      decoration: BoxDecoration(
        color: today ? cardHi : card,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: today ? gold.withValues(alpha: 0.7) : white(0.05), width: today ? 1.5 : 1),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            SizedBox(
              width: 58,
              child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
                Text('${day.date.day}', style: ts(24, w: 800, color: today ? gold : Colors.white, height: 1)),
                const SizedBox(width: 4),
                Padding(
                  padding: const EdgeInsets.only(bottom: 2),
                  child: Text(_months[day.date.month - 1], style: ts(11, w: 700, color: white(0.6))),
                ),
              ]),
            ),
            for (final t in day.times)
              Expanded(
                child: Text(t, textAlign: TextAlign.center, style: ts(13, w: 700, tabular: true)),
              ),
          ]),
          const SizedBox(height: 8),
          Row(children: [
            Text(
              today ? 'Bugün' : _weekdays[day.date.weekday - 1],
              style: ts(11, w: 700, color: friday || today ? gold : white(0.55)),
            ),
            if (day.hijriLong.isNotEmpty) ...[
              Text('  ·  ', style: ts(11, color: white(0.3))),
              Flexible(
                child: Text(
                  day.hijriLong,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: ts(11, w: 600, color: white(0.55)),
                ),
              ),
            ],
            if (special != null) ...[
              const Spacer(),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
                decoration: BoxDecoration(color: gold, borderRadius: BorderRadius.circular(9)),
                child: Text(special!, style: ts(10.5, w: 800, color: ink)),
              ),
            ],
          ]),
        ],
      ),
    );
  }
}
