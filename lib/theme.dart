import 'package:flutter/material.dart';

/// Renkler: gece laciverti zemin, altın vurgu.
const page = Color(0xFF070B1E);
const card = Color(0xFF111936);
const cardHi = Color(0xFF1B2757);
const gold = Color(0xFFFFD27A);
const ink = Color(0xFF0A1024);
const navBar = Color(0xFF0A1029);

Color white(double opacity) => Colors.white.withValues(alpha: opacity);

/// Manrope değişken yazı tipiyle metin stili. [w] 200–800 arası ağırlıktır.
TextStyle ts(
  double size, {
  int w = 500,
  Color color = Colors.white,
  double? height,
  double spacing = 0,
  bool tabular = false,
}) {
  return TextStyle(
    fontFamily: 'Manrope',
    fontSize: size,
    fontWeight: FontWeight.values[(w ~/ 100) - 1],
    fontVariations: [FontVariation('wght', w.toDouble())],
    fontFeatures: tabular ? const [FontFeature.tabularFigures()] : null,
    color: color,
    height: height,
    letterSpacing: spacing,
  );
}

/// Ekranın altında kısa bilgi mesajı.
void toast(BuildContext context, String message) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(
      content: Text(message, style: ts(14, w: 600, color: ink)),
      backgroundColor: gold,
      behavior: SnackBarBehavior.floating,
      margin: const EdgeInsets.fromLTRB(20, 0, 20, 16),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      duration: const Duration(seconds: 3),
    ));
}

/// Bölüm başlığı: küçük, aralıklı, soluk.
class SectionTitle extends StatelessWidget {
  const SectionTitle(this.text, {super.key});
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(24, 28, 24, 10),
        child: Text(text.replaceAll('i', 'İ').replaceAll('ı', 'I').toUpperCase(), style: ts(12, w: 700, color: white(0.5), spacing: 1.6)),
      );
}

/// Yuvarlak köşeli kart.
class Panel extends StatelessWidget {
  const Panel({super.key, required this.child, this.padding = const EdgeInsets.all(6), this.margin});
  final Widget child;
  final EdgeInsets padding;
  final EdgeInsets? margin;

  @override
  Widget build(BuildContext context) => Container(
        margin: margin ?? const EdgeInsets.symmetric(horizontal: 16),
        padding: padding,
        decoration: BoxDecoration(
          color: card,
          borderRadius: BorderRadius.circular(24),
          border: Border.all(color: white(0.06)),
        ),
        child: child,
      );
}

/// Altın renkli açma/kapama düğmesi.
class Toggle extends StatelessWidget {
  const Toggle({super.key, required this.value, required this.onChanged});
  final bool value;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) => Semantics(
        toggled: value,
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: () => onChanged(!value),
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 10),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 180),
              width: 50,
              height: 30,
              padding: const EdgeInsets.all(3),
              alignment: value ? Alignment.centerRight : Alignment.centerLeft,
              decoration: BoxDecoration(
                color: value ? gold : white(0.14),
                borderRadius: BorderRadius.circular(15),
              ),
              child: Container(
                width: 24,
                height: 24,
                decoration: BoxDecoration(color: value ? ink : Colors.white, shape: BoxShape.circle),
              ),
            ),
          ),
        ),
      );
}

/// Seçenek hapı (ör. "15 dk").
class Chip2 extends StatelessWidget {
  const Chip2({super.key, required this.label, required this.selected, required this.onTap});
  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => GestureDetector(
        onTap: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          height: 40,
          alignment: Alignment.center,
          padding: const EdgeInsets.symmetric(horizontal: 14),
          decoration: BoxDecoration(
            color: selected ? gold : white(0.08),
            borderRadius: BorderRadius.circular(14),
          ),
          child: Text(label, style: ts(13, w: selected ? 800 : 600, color: selected ? ink : Colors.white)),
        ),
      );
}
