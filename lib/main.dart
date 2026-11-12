
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'calendar.dart';
import 'dhikr.dart';
import 'home.dart';
import 'native.dart';
import 'qibla.dart';
import 'settings.dart';
import 'theme.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    systemNavigationBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.light,
    systemNavigationBarIconBrightness: Brightness.light,
  ));
  app.init();
  runApp(const VakitApp());
}

class VakitApp extends StatelessWidget {
  const VakitApp({super.key});

  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'Vakit',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          brightness: Brightness.dark,
          fontFamily: 'Manrope',
          scaffoldBackgroundColor: page,
          colorScheme: const ColorScheme.dark(primary: gold, surface: card),
          splashFactory: NoSplash.splashFactory,
        ),
        home: const Shell(),
      );
}

/// Beş sekme ve alt gezinme çubuğu.
class Shell extends StatefulWidget {
  const Shell({super.key});

  @override
  State<Shell> createState() => _ShellState();
}

class _ShellState extends State<Shell> {
  int _tab = 0;

  static const _items = [
    (Icons.mosque_rounded, 'Vakitler'),
    (Icons.calendar_month_rounded, 'Takvim'),
    (Icons.explore_rounded, 'Kıble'),
    (Icons.adjust_rounded, 'Zikir'),
    (Icons.tune_rounded, 'Ayarlar'),
  ];

  @override
  Widget build(BuildContext context) => Scaffold(
        body: ListenableBuilder(
          listenable: app,
          builder: (context, _) {
            final s = app.state;
            if (s == null) return const Center(child: CircularProgressIndicator(color: gold));
            return IndexedStack(
              index: _tab,
              children: [
                HomePage(state: s),
                CalendarPage(state: s),
                QiblaPage(state: s, active: _tab == 2),
                const DhikrPage(),
                SettingsPage(state: s),
              ],
            );
          },
        ),
        bottomNavigationBar: Container(
          decoration: BoxDecoration(
            color: navBar,
            border: Border(top: BorderSide(color: white(0.07))),
          ),
          padding: EdgeInsets.only(bottom: MediaQuery.paddingOf(context).bottom),
          child: SizedBox(
            height: 68,
            child: Row(
              children: [
                for (var i = 0; i < _items.length; i++)
                  Expanded(
                    child: Semantics(
                      selected: i == _tab,
                      button: true,
                      child: GestureDetector(
                        behavior: HitTestBehavior.opaque,
                        onTap: () {
                          HapticFeedback.selectionClick();
                          setState(() => _tab = i);
                        },
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            AnimatedContainer(
                              duration: const Duration(milliseconds: 200),
                              curve: Curves.easeOutCubic,
                              width: i == _tab ? 58 : 36,
                              height: 30,
                              decoration: BoxDecoration(
                                color: i == _tab ? gold.withValues(alpha: 0.16) : Colors.transparent,
                                borderRadius: BorderRadius.circular(15),
                              ),
                              child: Icon(_items[i].$1, size: 22, color: i == _tab ? gold : white(0.5)),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              _items[i].$2,
                              style: ts(11.5, w: i == _tab ? 800 : 600, color: i == _tab ? gold : white(0.5)),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      );
}
