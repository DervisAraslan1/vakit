import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'native.dart';
import 'theme.dart';

const _prayers = [
  ('sabah', 'Sabah'),
  ('ogle', 'Öğle'),
  ('ikindi', 'İkindi'),
  ('aksam', 'Akşam'),
  ('yatsi', 'Yatsı'),
  ('vitir', 'Vitir'),
];

/// Kaza namazı takibi: her vakit için kalan borç; kıldıkça azalır.
class KazaPage extends StatefulWidget {
  const KazaPage({super.key});

  @override
  State<KazaPage> createState() => _KazaPageState();
}

class _KazaPageState extends State<KazaPage> {
  final _counts = <String, int>{};
  var _prayed = 0; // bugüne kadar "Kıldım" denen toplam

  int get _total => _counts.values.fold(0, (a, b) => a + b);

  @override
  void initState() {
    super.initState();
    _restore();
  }

  Future<void> _restore() async {
    final raw = await Native.getString('kaza');
    if (raw == null || !mounted) return;
    try {
      final data = jsonDecode(raw) as Map<String, dynamic>;
      setState(() {
        _counts.addAll((data['counts'] as Map<String, dynamic>).cast<String, int>());
        _prayed = data['prayed'] as int? ?? 0;
      });
    } on Object {
      // Bozuk kayıt: sıfırdan başla.
    }
  }

  void _save() => Native.setString('kaza', jsonEncode({'counts': _counts, 'prayed': _prayed}));

  void _change(String id, int delta) {
    final now = _counts[id] ?? 0;
    if (now + delta < 0) return;
    HapticFeedback.selectionClick();
    setState(() {
      _counts[id] = now + delta;
      if (delta < 0) _prayed++;
    });
    _save();
  }

  /// Sayıya dokununca toplu giriş (ör. yıllardır kılınmayan namazlar için 1825).
  Future<void> _edit(String id, String name) async {
    final controller = TextEditingController(text: '${_counts[id] ?? 0}');
    final value = await showDialog<int>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: card,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
        title: Text('$name kazası', style: ts(18, w: 800)),
        content: TextField(
          controller: controller,
          autofocus: true,
          keyboardType: TextInputType.number,
          inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(6)],
          style: ts(28, w: 800, tabular: true),
          cursorColor: gold,
          decoration: InputDecoration(
            helperText: 'Kalan kaza sayısı',
            helperStyle: ts(12, color: white(0.5)),
            enabledBorder: UnderlineInputBorder(borderSide: BorderSide(color: white(0.2))),
            focusedBorder: const UnderlineInputBorder(borderSide: BorderSide(color: gold, width: 2)),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('Vazgeç', style: ts(14, w: 700, color: white(0.7))),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, int.tryParse(controller.text) ?? 0),
            child: Text('Kaydet', style: ts(14, w: 800, color: gold)),
          ),
        ],
      ),
    );
    if (value == null) return;
    setState(() => _counts[id] = value);
    _save();
  }

  @override
  Widget build(BuildContext context) {
    final inset = MediaQuery.paddingOf(context);
    return ListView(
      padding: EdgeInsets.fromLTRB(16, 8, 16, inset.bottom + 24),
      children: [
        Container(
          padding: const EdgeInsets.fromLTRB(20, 18, 20, 18),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(24),
            gradient: const LinearGradient(colors: [Color(0xFF22306B), Color(0xFF141C45)]),
            border: Border.all(color: white(0.06)),
          ),
          child: Row(children: [
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text('Kalan kaza', style: ts(13, w: 600, color: white(0.6))),
                Text('$_total', style: ts(44, w: 800, tabular: true, height: 1.1)),
              ]),
            ),
            Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
              Text('Kılınan', style: ts(13, w: 600, color: white(0.6))),
              Text('$_prayed', style: ts(26, w: 800, color: gold, tabular: true, height: 1.2)),
            ]),
          ]),
        ),
        const SizedBox(height: 12),
        for (final (id, name) in _prayers) ...[
          Container(
            padding: const EdgeInsets.fromLTRB(18, 10, 10, 10),
            decoration: BoxDecoration(
              color: card,
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: white(0.05)),
            ),
            child: Row(children: [
              SizedBox(width: 74, child: Text(name, style: ts(16, w: 700))),
              Expanded(
                child: Semantics(
                  button: true,
                  label: '$name kazası ${_counts[id] ?? 0}. Değiştirmek için dokun',
                  child: GestureDetector(
                    onTap: () => _edit(id, name),
                    child: Text(
                      '${_counts[id] ?? 0}',
                      style: ts(26, w: 800, tabular: true, color: (_counts[id] ?? 0) == 0 ? white(0.3) : Colors.white),
                    ),
                  ),
                ),
              ),
              IconButton(
                onPressed: () => _change(id, 1),
                tooltip: '$name kazası ekle',
                style: IconButton.styleFrom(backgroundColor: white(0.08)),
                icon: const Icon(Icons.add_rounded, color: Colors.white),
              ),
              const SizedBox(width: 6),
              FilledButton(
                onPressed: (_counts[id] ?? 0) == 0 ? null : () => _change(id, -1),
                style: FilledButton.styleFrom(
                  backgroundColor: gold,
                  foregroundColor: ink,
                  disabledBackgroundColor: white(0.06),
                  disabledForegroundColor: white(0.3),
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                ),
                child: Text('Kıldım', style: ts(13, w: 800, color: (_counts[id] ?? 0) == 0 ? white(0.3) : ink)),
              ),
            ]),
          ),
          const SizedBox(height: 8),
        ],
        Padding(
          padding: const EdgeInsets.fromLTRB(8, 8, 8, 0),
          child: Text(
            'Kaza kıldıkça "Kıldım"a bas. Borç eklemek için + kullan; yıllık borcu toplu girmek için sayıya dokun.',
            style: ts(12, color: white(0.5), height: 1.5),
          ),
        ),
      ],
    );
  }
}
