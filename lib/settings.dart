import 'package:flutter/material.dart';

import 'native.dart';
import 'sky.dart';
import 'theme.dart';

/// Türkçe harflere duyarlı küçük harfe çevirme (arama için).
String _lower(String s) => s.replaceAll('İ', 'i').replaceAll('I', 'ı').toLowerCase();

/// Alttan açılan, aramalı seçim listesi.
Future<T?> pick<T>(
  BuildContext context, {
  required String title,
  required List<T> items,
  required String Function(T) label,
  T? selected,
}) {
  return showModalBottomSheet<T>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    backgroundColor: card,
    shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(28))),
    builder: (context) {
      var query = '';
      return StatefulBuilder(
        builder: (context, setState) {
          final shown = items.where((x) => _lower(label(x)).contains(_lower(query))).toList();
          return Padding(
            padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
            child: SizedBox(
              height: MediaQuery.sizeOf(context).height * 0.72,
              child: Column(children: [
                Container(
                  width: 40,
                  height: 4,
                  margin: const EdgeInsets.only(top: 10, bottom: 14),
                  decoration: BoxDecoration(color: white(0.2), borderRadius: BorderRadius.circular(2)),
                ),
                Text(title, style: ts(17, w: 800)),
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 14, 16, 8),
                  child: TextField(
                    onChanged: (v) => setState(() => query = v.trim()),
                    style: ts(15, w: 600),
                    cursorColor: gold,
                    decoration: InputDecoration(
                      hintText: 'Ara',
                      hintStyle: ts(15, color: white(0.4)),
                      prefixIcon: Icon(Icons.search_rounded, color: white(0.5)),
                      filled: true,
                      fillColor: white(0.07),
                      contentPadding: const EdgeInsets.symmetric(vertical: 12),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(16),
                        borderSide: BorderSide.none,
                      ),
                    ),
                  ),
                ),
                Expanded(
                  child: ListView.builder(
                    itemCount: shown.length,
                    itemBuilder: (context, i) {
                      final on = shown[i] == selected;
                      return InkWell(
                        onTap: () => Navigator.pop(context, shown[i]),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
                          child: Row(children: [
                            Expanded(
                              child: Text(
                                label(shown[i]),
                                style: ts(16, w: on ? 800 : 500, color: on ? gold : Colors.white),
                              ),
                            ),
                            if (on) const Icon(Icons.check_rounded, color: gold, size: 20),
                          ]),
                        ),
                      );
                    },
                  ),
                ),
              ]),
            ),
          );
        },
      );
    },
  );
}

/// İl seçtirir; seçilen ilin merkezi için resmi vakitleri çeker.
Future<void> changeCity(BuildContext context, AppState s) async {
  final city = await pick<String>(context, title: 'İl seç', items: s.cities, label: (c) => c, selected: s.city);
  if (city == null || city == s.city || !context.mounted) return;
  toast(context, 'Vakitler güncelleniyor…');
  final ok = await Native.setLocation(city);
  await app.load();
  if (!ok && context.mounted) toast(context, 'İnternet yok: vakitler hesapla gösteriliyor');
}

/// İl ve ilçeyi konumdan bulur; sonucu kısa bir mesajla bildirir.
Future<void> useMyLocation(BuildContext context) async {
  toast(context, 'Konum bulunuyor…');
  final r = await Native.locate();
  await app.load();
  if (!context.mounted) return;
  toast(context, switch (r['error']) {
    'denied' => 'Konum izni verilmedi; ili elle seçebilirsin',
    'disabled' => 'Telefonun konum servisi kapalı',
    'nolocation' => 'Konum alınamadı, biraz sonra tekrar dene',
    'abroad' => 'Türkiye dışındasın; ili elle seç',
    _ => 'Konum: ${r['place']}',
  });
}

/// Seçili ilin ilçelerini getirip seçtirir.
Future<void> changeDistrict(BuildContext context, AppState s) async {
  final List<District> list;
  try {
    list = await Native.districts(s.city);
  } on Object {
    if (context.mounted) toast(context, 'İlçe listesi alınamadı, internet bağlantını kontrol et');
    return;
  }
  if (!context.mounted) return;
  final current = list.where((d) => d.name == s.district).firstOrNull;
  final district =
      await pick<District>(context, title: 'İlçe seç', items: list, label: (d) => d.name, selected: current);
  if (district == null || !context.mounted) return;
  toast(context, 'Vakitler güncelleniyor…');
  final ok = await Native.setLocation(s.city, district: district);
  await app.load();
  if (!ok && context.mounted) toast(context, 'Vakitler alınamadı, internet bağlantını kontrol et');
}

/// Ayarlar: konum, bildirimler, cami galerisi ve ana ekran widget'ları.
class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key, required this.state});
  final AppState state;

  static const _leads = [5, 10, 15, 30, 45];

  /// (tür, başlık, genişlik dp, yükseklik dp) — Kotlin'deki Kind ile aynı.
  static const _widgets = [
    ('LARGE', '4×2', 320.0, 150.0),
    ('WIDE', '4×1', 320.0, 70.0),
    ('SQUARE', '2×2', 155.0, 155.0),
    ('SMALL', '2×1', 155.0, 70.0),
    ('TINY', '1×1', 70.0, 70.0),
  ];

  /// "Vakit girdi" bildiriminin sesini seçtirir.
  Future<void> _chooseSound(BuildContext context, AppState s) async {
    final choice = await showModalBottomSheet<String>(
      context: context,
      backgroundColor: card,
      useSafeArea: true,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(28))),
      builder: (context) {
        Widget option(String id, IconData icon, String title, String subtitle, bool selected) => ListTile(
              onTap: () => Navigator.pop(context, id),
              contentPadding: const EdgeInsets.symmetric(horizontal: 24, vertical: 2),
              leading: Icon(icon, color: selected ? gold : white(0.7)),
              title: Text(title, style: ts(16, w: 700, color: selected ? gold : Colors.white)),
              subtitle: Text(subtitle, style: ts(12.5, color: white(0.55), height: 1.35)),
              trailing: selected ? const Icon(Icons.check_rounded, color: gold) : null,
            );
        return Padding(
          padding: const EdgeInsets.only(bottom: 16),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Container(
              width: 40,
              height: 4,
              margin: const EdgeInsets.only(top: 10, bottom: 14),
              decoration: BoxDecoration(color: white(0.2), borderRadius: BorderRadius.circular(2)),
            ),
            Text('Vakit girince çalacak ses', style: ts(17, w: 800)),
            const SizedBox(height: 8),
            option('default', Icons.notifications_rounded, 'Telefonun bildirim sesi', 'Diğer bildirimlerle aynı ses',
                s.soundMode == 'default'),
            if (s.canPickFile)
              option('file', Icons.library_music_rounded, 'Ezan dosyası seç',
                  'İndirdiğin bir ezan ya da sela kaydını (mp3) seç; tamamı çalar', false),
            option('system', Icons.music_note_rounded, 'Telefondaki seslerden seç',
                s.soundMode == 'custom' ? 'Şu an: ${s.soundName}' : 'Zil ve bildirim sesleri', s.soundMode == 'custom'),
            option('silent', Icons.vibration_rounded, 'Sessiz', 'Yalnızca bildirim, ses yok', s.soundMode == 'silent'),
          ]),
        );
      },
    );
    if (choice == null) return;
    if (choice == 'default' || choice == 'silent') {
      await Native.setSound(choice);
    } else {
      try {
        final name = await Native.pickSound(choice);
        if (name != null && context.mounted) toast(context, 'Vakit sesi: $name');
      } on Object {
        if (context.mounted) toast(context, 'Ses dosyası eklenemedi');
      }
    }
    await app.load();
  }

  Future<void> _set(String key, Object value) async {
    await Native.set(key, value);
    await app.load();
  }

  Widget _row(String label, Widget trailing, {VoidCallback? onTap, String? value}) => InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(18),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 4, 12, 4),
          child: SizedBox(
            height: 48,
            child: Row(children: [
              Text(label, style: ts(15, w: 600)),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  value ?? '',
                  textAlign: TextAlign.end,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: ts(15, w: 700, color: gold),
                ),
              ),
              trailing,
            ]),
          ),
        ),
      );

  /// Yalnızca gerçekten bir sorun varken görünen, tek düğmeli uyarı.
  Widget? _warning(BuildContext context, AppState s) {
    if (!s.notif) return null;
    final (String text, String action, Future<void> Function() fix) = switch (s) {
      AppState(canPost: false) => (
          'Bildirim izni kapalı; vakit bildirimleri gösterilemiyor.',
          'İzin ver',
          () async => Native.enableNotifications(),
        ),
      AppState(batteryOk: false) => ('Pil tasarrufu bildirimleri geciktirebilir.', 'Düzelt', Native.fixBattery),
      AppState(missed: true, needsAutostart: true) => (
          "Bir bildirim zamanında gelmedi. Açılan listede Vakit'i açman yeterli.",
          'Düzelt',
          Native.fixAutostart,
        ),
      _ => ('', '', () async {}),
    };
    if (text.isEmpty) return null;
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 12),
      padding: const EdgeInsets.fromLTRB(16, 12, 10, 12),
      decoration: BoxDecoration(
        color: gold.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: gold.withValues(alpha: 0.4)),
      ),
      child: Row(children: [
        Expanded(child: Text(text, style: ts(13, w: 600, height: 1.4))),
        const SizedBox(width: 10),
        GestureDetector(
          onTap: () async {
            await fix();
            await app.load();
          },
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            decoration: BoxDecoration(color: gold, borderRadius: BorderRadius.circular(14)),
            child: Text(action, style: ts(13, w: 800, color: ink)),
          ),
        ),
      ]),
    );
  }

  @override
  Widget build(BuildContext context) {
    final s = state;
    final inset = MediaQuery.paddingOf(context);
    final chevron = Icon(Icons.chevron_right_rounded, color: white(0.4));
    final warning = _warning(context, s);
    final sky = skyColors(Phase.day);

    return ListView(
      padding: EdgeInsets.only(top: inset.top + 18, bottom: inset.bottom + 24),
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24),
          child: Text('Ayarlar', style: ts(30, w: 800, spacing: -0.5)),
        ),

        const SectionTitle('Konum'),
        Panel(
          child: Column(children: [
            _row(
              'Konumumu kullan',
              Toggle(
                value: s.autoLocation,
                onChanged: (on) async {
                  if (on) {
                    await useMyLocation(context);
                  } else {
                    await _set('autoLocation', false);
                  }
                },
              ),
            ),
            _row('İl', chevron, value: s.city, onTap: () => changeCity(context, s)),
            _row(
              'İlçe',
              chevron,
              value: s.district.isEmpty ? 'Merkez' : s.district,
              onTap: () => changeDistrict(context, s),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 6, 16, 12),
              child: Row(children: [
                Icon(
                  s.official ? Icons.verified_rounded : Icons.cloud_off_rounded,
                  size: 15,
                  color: s.official ? gold : white(0.5),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    s.official
                        ? 'Vakitler Diyanet İşleri Başkanlığı verisidir.'
                        : 'Diyanet verisi alınamadı; vakitler cihazda hesaplanıyor.',
                    style: ts(12, w: 500, color: white(0.55)),
                  ),
                ),
              ]),
            ),
          ]),
        ),

        const SectionTitle('Bildirimler'),
        ?warning,
        Panel(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            _row(
              'Vakit bildirimleri',
              Toggle(
                value: s.notif,
                onChanged: (on) async {
                  if (on) {
                    final ok = await Native.enableNotifications();
                    if (!ok && context.mounted) toast(context, 'Bildirim izni verilmedi');
                    await app.load();
                  } else {
                    await _set('notif', false);
                  }
                },
              ),
            ),
            if (s.notif) ...[
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 10),
                child: Text('Vakitten önce hatırlat', style: ts(15, w: 600)),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 10),
                child: Row(children: [
                  for (final m in _leads)
                    Expanded(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 3),
                        child: Chip2(label: '$m dk', selected: m == s.lead, onTap: () => _set('lead', m)),
                      ),
                    ),
                ]),
              ),
              const SizedBox(height: 8),
              _row('Vakit girince de bildir', Toggle(value: s.atTime, onChanged: (on) => _set('atTime', on))),
              if (s.atTime)
                _row(
                  'Vakit sesi',
                  chevron,
                  value: s.soundLabel,
                  onTap: () => _chooseSound(context, s),
                ),
              _row(
                'Bildirimi dene',
                Icon(Icons.send_rounded, color: white(0.5), size: 20),
                onTap: () async {
                  await Native.testNotification();
                  if (context.mounted) toast(context, 'Deneme bildirimi gönderildi');
                },
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                child: Text(
                  'Hangi vakitlerde bildirim geleceğini ana ekrandaki zillerden seçebilirsin.',
                  style: ts(12, color: white(0.5), height: 1.4),
                ),
              ),
            ],
          ]),
        ),

        const SectionTitle('Cami'),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: GridView.count(
            crossAxisCount: 3,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            padding: EdgeInsets.zero,
            mainAxisSpacing: 10,
            crossAxisSpacing: 10,
            childAspectRatio: 0.82,
            children: [
              for (final m in s.mosques)
                GestureDetector(
                  onTap: () => _set('mosque', m.id),
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 180),
                    padding: const EdgeInsets.all(5),
                    decoration: BoxDecoration(
                      color: m.id == s.mosque ? gold.withValues(alpha: 0.14) : card,
                      borderRadius: BorderRadius.circular(22),
                      border: Border.all(
                        color: m.id == s.mosque ? gold : white(0.06),
                        width: m.id == s.mosque ? 2 : 1,
                      ),
                    ),
                    child: Column(children: [
                      Expanded(
                        child: Container(
                          width: double.infinity,
                          padding: const EdgeInsets.fromLTRB(8, 10, 8, 6),
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(17),
                            gradient: LinearGradient(
                              begin: Alignment.topCenter,
                              end: Alignment.bottomCenter,
                              colors: sky,
                            ),
                          ),
                          child: FutureBuilder(
                            future: Native.mosqueImage(m.id, false, size: 300),
                            builder: (context, snap) => snap.hasData
                                ? Image.memory(snap.data!, fit: BoxFit.contain, alignment: Alignment.bottomCenter)
                                : const SizedBox.shrink(),
                          ),
                        ),
                      ),
                      Padding(
                        padding: const EdgeInsets.fromLTRB(2, 7, 2, 4),
                        child: Text(
                          m.name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: ts(
                            11.5,
                            w: m.id == s.mosque ? 800 : 600,
                            color: m.id == s.mosque ? gold : Colors.white,
                          ),
                        ),
                      ),
                    ]),
                  ),
                ),
            ],
          ),
        ),

        const SectionTitle('Ana ekran'),
        SizedBox(
          height: 170,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: _widgets.length,
            separatorBuilder: (_, _) => const SizedBox(width: 14),
            itemBuilder: (context, i) {
              final (kind, title, w, h) = _widgets[i];
              return GestureDetector(
                onTap: () async {
                  final ok = await Native.pinWidget(kind);
                  if (!ok && context.mounted) {
                    toast(context, "Ana ekrana basılı tut, Widget'lar > Vakit'i seç");
                  }
                },
                child: Column(mainAxisAlignment: MainAxisAlignment.end, children: [
                  ClipRRect(
                    borderRadius: BorderRadius.circular(20),
                    child: SizedBox(
                      width: w * 0.85,
                      height: h * 0.85,
                      child: FutureBuilder(
                        // Cami ya da vakit değişince önizleme de yenilensin.
                        key: ValueKey('$kind-${s.mosque}-${s.next}-${s.place}'),
                        future: Native.widgetPreview(kind),
                        builder: (context, snap) => snap.hasData
                            ? Image.memory(snap.data!, fit: BoxFit.fill, gaplessPlayback: true)
                            : const ColoredBox(color: card),
                      ),
                    ),
                  ),
                  const SizedBox(height: 10),
                  Text('$title  ·  Ekle', style: ts(12, w: 700, color: white(0.6))),
                ]),
              );
            },
          ),
        ),

        Padding(
          padding: const EdgeInsets.fromLTRB(24, 28, 24, 0),
          child: Text(
            'Vakitler Diyanet İşleri Başkanlığı verisinden ilçe bazında alınır ve 30 gün için '
            'saklanır. İnternet yokken Diyanet yöntemiyle cihazda hesaplanır; bu durumda '
            '1 dakikaya kadar fark olabilir.',
            style: ts(12, color: white(0.45), height: 1.5),
          ),
        ),
      ],
    );
  }
}
