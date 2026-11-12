import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

/// Uygulamanın tek durum kaynağı. Vakit girince ve uygulamaya dönülünce kendini yeniler.
class AppModel extends ChangeNotifier with WidgetsBindingObserver {
  AppState? state;
  Timer? _timer;

  Future<void> init() async {
    WidgetsBinding.instance.addObserver(this);
    await load();
    // İlk açılışta il/ilçeyi konumdan bulmayı bir kez dene; reddedilirse elle seçilir.
    if (await Native.getString('locAsked') == null) {
      await Native.setString('locAsked', '1');
      if ((await Native.locate())['ok'] == true) await load();
    } else {
      await _autoLocate();
    }
    // Resmi tablo eksik ya da eskiyse çek, sonra ekranı tazele.
    if (await Native.refresh()) await load();
  }

  /// Konum açıksa ve kullanıcı başka yere gittiyse il/ilçeyi sessizce günceller.
  Future<void> _autoLocate() async {
    if ((await Native.locate(silent: true))['changed'] == true) await load();
  }

  Future<void> load() async {
    final s = await Native.state();
    state = s;
    notifyListeners();
    _timer?.cancel();
    final wait = s.nextTime.difference(DateTime.now()) + const Duration(seconds: 1);
    _timer = Timer(wait.isNegative ? const Duration(seconds: 5) : wait, load);
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      load();
      _autoLocate();
    }
  }
}

final app = AppModel();

const prayerNames = ['İmsak', 'Güneş', 'Öğle', 'İkindi', 'Akşam', 'Yatsı'];
const prayerDative = ["İmsak'a", "Güneş'e", "Öğle'ye", "İkindi'ye", "Akşam'a", "Yatsı'ya"];

class MosqueInfo {
  const MosqueInfo(this.id, this.name);
  final String id;
  final String name;
}

class District {
  const District(this.id, this.name);
  final String id;
  final String name;
}

/// Yerel taraftan gelen anlık durum: vakitler, seçimler, bildirim ayarları.
class AppState {
  AppState.from(Map<dynamic, dynamic> m)
      : city = m['city'] as String,
        district = m['district'] as String,
        mosque = m['mosque'] as String,
        night = m['night'] as bool,
        official = m['official'] as bool,
        hijri = m['hijri'] as String,
        date = m['date'] as String,
        next = m['next'] as int,
        current = m['current'] as int,
        afterIsha = m['afterIsha'] as bool,
        nextTime = _ms(m['nextTime']),
        currentStart = _ms(m['currentStart']),
        times = [for (final t in m['times'] as List) _ms(t)],
        timeTexts = (m['timeTexts'] as List).cast<String>(),
        nextText = m['nextText'] as String,
        notif = m['notif'] as bool,
        prayerOn = (m['prayerOn'] as List).cast<bool>(),
        lead = m['lead'] as int,
        atTime = m['atTime'] as bool,
        canPost = m['canPost'] as bool,
        batteryOk = m['batteryOk'] as bool,
        missed = m['missed'] as bool,
        needsAutostart = m['needsAutostart'] as bool,
        autoLocation = m['autoLocation'] as bool,
        lat = (m['lat'] as num).toDouble(),
        lng = (m['lng'] as num).toDouble(),
        coordsExact = m['coordsExact'] as bool,
        ramadan = m['ramadan'] as bool,
        ramadanDay = m['ramadanDay'] as int?,
        nextLabel = m['nextLabel'] as String,
        soundMode = m['soundMode'] as String,
        soundName = m['soundName'] as String,
        canPickFile = m['canPickFile'] as bool,
        mosques = [for (final x in m['mosques'] as List) MosqueInfo(x['id'] as String, x['name'] as String)],
        cities = (m['cities'] as List).cast<String>();

  static DateTime _ms(dynamic v) => DateTime.fromMillisecondsSinceEpoch(v as int);

  final String city, district, mosque, hijri, date, nextText;
  final bool night, official, afterIsha, notif, atTime, canPost, batteryOk, missed, needsAutostart;

  /// İl/ilçe konumdan mı geliyor.
  final bool autoLocation;

  /// Kıble hesabı için koordinat; [coordsExact] değilse il merkezidir.
  final double lat, lng;
  final bool coordsExact;

  /// Sıradaki vakit Ramazan gününe mi düşüyor; bugün Ramazan'ın kaçıncı günü.
  final bool ramadan;
  final int? ramadanDay;

  /// "İkindi'ye", Ramazan'da "İftara" / "Sahura".
  final String nextLabel;

  /// "Vakit girdi" sesi: default, silent ya da custom ([soundName] ile).
  final String soundMode, soundName;
  final bool canPickFile;

  String get soundLabel => switch (soundMode) {
        'silent' => 'Sessiz',
        'custom' => soundName,
        _ => 'Telefonun sesi',
      };

  /// Sıradaki ve içinde bulunulan vaktin indeksi (0 = İmsak ... 5 = Yatsı).
  final int next, current;
  final int lead;
  final DateTime nextTime, currentStart;
  final List<DateTime> times;
  final List<String> timeTexts;
  final List<bool> prayerOn;
  final List<MosqueInfo> mosques;
  final List<String> cities;

  /// Görünen yer adı: ilçe il merkeziyse yalnızca il.
  String get place => district.isEmpty || district == city ? city : '$district, $city';
}

/// Takvimdeki bir gün.
class DayRow {
  DayRow.from(Map<dynamic, dynamic> m)
      : date = DateTime.parse(m['date'] as String),
        times = (m['times'] as List).cast<String>(),
        hijri = m['hijri'] as String,
        hijriLong = m['hijriLong'] as String,
        official = m['official'] as bool;

  final DateTime date;
  final List<String> times;

  /// "2.6.1448" (gün.ay.yıl); resmi tablo yoksa boş.
  final String hijri;
  final String hijriLong;
  final bool official;

  int? get hijriDay => hijri.isEmpty ? null : int.tryParse(hijri.split('.')[0]);
  int? get hijriMonth => hijri.isEmpty ? null : int.tryParse(hijri.split('.')[1]);
}

/// Kotlin tarafındaki "vakit/native" kanalı.
class Native {
  static const _c = MethodChannel('vakit/native');

  static Future<AppState> state() async => AppState.from((await _c.invokeMapMethod('state'))!);

  static Future<List<DayRow>> days(int count) async =>
      [for (final d in (await _c.invokeListMethod('days', {'count': count}))!) DayRow.from(d as Map)];

  /// Resmi tablo eskiyse ağdan yeniler. İnternet yoksa false döner.
  static Future<bool> refresh() async {
    try {
      return (await _c.invokeMethod<bool>('refresh')) ?? false;
    } on PlatformException {
      return false;
    }
  }

  static Future<List<District>> districts(String city) async => [
        for (final d in (await _c.invokeListMethod('districts', {'city': city}))!)
          District(d['id'] as String, d['name'] as String),
      ];

  static Future<bool> setLocation(String city, {District? district}) async {
    try {
      return (await _c.invokeMethod<bool>('setLocation', {
            'city': city,
            'districtId': district?.id,
            'districtName': district?.name,
          })) ??
          false;
    } on PlatformException {
      return false;
    }
  }

  /// Konumdan il/ilçe bulur ve vakitleri günceller.
  /// [silent]: izin istemez; yalnızca otomatik konum açıksa ve 3 saattir bakılmadıysa çalışır.
  /// Dönüş: ok, changed, place ya da error (denied, disabled, nolocation, abroad).
  static Future<Map<dynamic, dynamic>> locate({bool silent = false}) async {
    try {
      return (await _c.invokeMapMethod<dynamic, dynamic>('locate', {'silent': silent})) ?? const {};
    } on PlatformException catch (e) {
      return {'ok': false, 'error': e.code};
    }
  }

  static Future<void> set(String key, Object value, {int? index}) =>
      _c.invokeMethod('set', {'key': key, 'value': value, 'index': index});

  /// Bildirim iznini ister ve bildirimleri açar; izin verilmezse false.
  static Future<bool> enableNotifications() async =>
      (await _c.invokeMethod<bool>('enableNotifications')) ?? false;

  /// Ses seçtirir: kind "system" (telefonun sesleri) ya da "file" (ör. ezan mp3).
  /// Seçilen sesin adını, vazgeçilirse null döner.
  static Future<String?> pickSound(String kind) => _c.invokeMethod<String>('pickSound', {'kind': kind});

  static Future<void> setSound(String mode) => _c.invokeMethod('setSound', {'mode': mode});
  static Future<void> testNotification() => _c.invokeMethod('testNotification');

  /// Telefonun baktığı yön (gerçek kuzeye göre derece) ve manyetometre doğruluğu (0–3).
  static Stream<({double heading, int accuracy})> compass() =>
      const EventChannel('vakit/compass').receiveBroadcastStream().map((e) {
        final m = e as Map;
        return (heading: (m['heading'] as num).toDouble(), accuracy: m['accuracy'] as int);
      });

  static Future<void> fixBattery() => _c.invokeMethod('fixBattery');
  static Future<void> fixAutostart() => _c.invokeMethod('fixAutostart');

  static Future<String?> getString(String key) => _c.invokeMethod<String>('getString', {'key': key});
  static Future<void> setString(String key, String value) =>
      _c.invokeMethod('setString', {'key': key, 'value': value});

  static final _images = <String, Future<Uint8List>>{};

  /// Saydam zeminli cami görseli (kare PNG). Sonuçlar bellekte tutulur.
  static Future<Uint8List> mosqueImage(String id, bool night, {int size = 720}) => _images.putIfAbsent(
        '$id-$night-$size',
        () async => (await _c.invokeMethod<Uint8List>('mosqueImage', {'id': id, 'night': night, 'size': size}))!,
      );

  static Future<Uint8List> widgetPreview(String kind) async =>
      (await _c.invokeMethod<Uint8List>('widgetPreview', {'kind': kind}))!;

  static Future<bool> pinWidget(String kind) async =>
      (await _c.invokeMethod<bool>('pinWidget', {'kind': kind})) ?? false;
}
