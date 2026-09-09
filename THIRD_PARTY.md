# Referenzen und Lizenzen

Scale2Garmin wird als GPL-3.0-or-later bereitgestellt (siehe LICENSE), weil die
BS444-Implementierung auf den Protokollerkenntnissen von openScale aufbaut und
Version 0.3 weitere openScale-Treiber direkt einbindet. Einzelheiten der Anpassung:
`scale-drivers/UPSTREAM.md`.

- openScale, Copyright (C) 2025 olie.xdev <olie.xdeveloper@googlemail.com>, GPL-3.0-or-later.
  https://github.com/oliexdev/openScale
  Eingebundener Quellstand: ac5753b062112e4d74772e6b50b084cd68c59175.
  Referenz: android_app/app/src/main/java/com/health/openscale/core/bluetooth/scales/MedisanaBs44xHandler.kt
  Protokoll: https://github.com/oliexdev/openScale/wiki/Medisana-BS444
- Garmin-Anmeldeablauf nach Garth, Copyright (c) 2023 Matin Tamizi, MIT.
  Vollständiger Lizenztext: licenses/garth-MIT.txt.
  https://github.com/matin/garth/blob/main/src/garth/sso.py
- FIT-Profil gemäß Garmin FIT SDK. Der Encoder ist eigenständig implementiert;
  Garmin-SDK-Code ist nicht Bestandteil der APK. Das offizielle SDK 21.176.0
  wird ausschließlich als Testabhängigkeit für unabhängige Decodierung verwendet.
  https://github.com/garmin/fit-java-sdk
  https://developer.garmin.com/fit/protocol/
- Fit2Garmin und HC2Garmin wurden konzeptionell betrachtet. Ihr Code wird nicht eingebunden.

Weitere Laufzeitbibliotheken:

- blessed-kotlin 3.0.12, Copyright Wouter van Welie, MIT. `licenses/blessed-MIT.txt`.
- Kotlin 2.2.10 und kotlinx.coroutines 1.10.2, JetBrains, Apache-2.0.
  `licenses/kotlin-Apache-2.0.txt` und `licenses/coroutines-Apache-2.0.txt`.
- Health Connect connect-client 1.1.0, Android Open Source Project, Apache-2.0.
  https://developer.android.com/jetpack/androidx/releases/health-connect
- Health Connect External Protobuf 1.1.0 (Protocol Buffers), Copyright 2008 Google Inc.,
  BSD-3-Clause. `licenses/protobuf-BSD-3-Clause.txt`.
- AndroidX Core 1.15.0 und zugehörige AndroidX-Bibliotheken, Android Open Source
  Project, Apache-2.0 (derselbe vollständige Lizenztext in `licenses/kotlin-Apache-2.0.txt`).
- Bouncy Castle bcprov-jdk18on 1.80, Legion of the Bouncy Castle, MIT-artige Lizenz.
  `licenses/bouncycastle-LICENSE.html`.
- Timber 5.0.1, Jake Wharton, Apache-2.0. `licenses/timber-Apache-2.0.txt`.

Die Lizenztexte werden mit der APK unter `assets/licenses` mitgeliefert.

Kein von Medisana oder Garmin bereitgestelltes bzw. zertifiziertes Produkt.

Beim erstmaligen Garmin-Login werden die öffentlichen OAuth-Clientparameter
über HTTPS von thegarth.s3.amazonaws.com geladen, entsprechend Garth. Dorthin
gehen keine Garmin-Zugangsdaten oder Messwerte. Anmeldedaten, MFA-Codes und
Messwerte werden ausschließlich an Garmin gesendet. Die Garmin-Anbindung
verwendet inoffizielle Endpunkte und kann durch Änderungen von Garmin ausfallen.
