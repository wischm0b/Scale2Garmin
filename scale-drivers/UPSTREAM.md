# openScale-Treiber in Scale2Garmin

Quelle: https://github.com/oliexdev/openScale

Fest eingebundener Stand: `ac5753b062112e4d74772e6b50b084cd68c59175`.
Lizenz: GPL-3.0-or-later; Copyright-Hinweise stehen in den Quelldateien.

Übernommen wurden die 64 registrierten Handler-Varianten, ihre Protokollbibliotheken
und die GATT-, Broadcast- und Classic-SPP-Adapter. Die Reihenfolge der Erkennung
entspricht openScale. Die funktionierende native BS44x-Verbindung bleibt in der App
erhalten; die weiteren Modelle verwenden dieses Modul. Eine openScale-Installation
ist nicht erforderlich.

Anpassungen:

- Compose-Konfigurationsoberflächen und ihre Imports entfernt; Konfiguration und
  Benutzer-/Consent-Abfragen erfolgen über die native Scale2Garmin-Oberfläche.
- `ModernScaleAdapter` verwendet die App-Profile und private SharedPreferences
  statt openScale-Datenbank und Facades. Treibereinstellungen sind nach Handler und
  Geräteadresse getrennt. Die App liefert ein ausgewähltes Profil an den Treiber.
- UI-Metadaten an den typisierten Messwerten wurden entfernt. Einheitentypen und
  Umrechnungen bleiben erhalten. Scale2Garmin übernimmt Gewicht, Fett, Wasser,
  Muskelanteil, Knochenmasse und Grundumsatz; weitere Treiberwerte werden nicht in
  die Scale2Garmin-Datenbank oder nach Garmin übernommen.
- Protokoll- und Messdatenlogging deaktiviert.
- Beim Ende einer Sitzung werden Handler und Transport geschlossen; der
  Broadcast-Adapter gibt zusätzlich den BluetoothCentralManager frei.
- Originale Testbibliotheken und Registry-Tests übernommen; Factory-Aufruf auf
  `DriverCatalog` umbenannt, Android-Testversion auf API 35 gesetzt.

`scripts/import_openscale.py` dokumentiert den initialen Import. Danach erfolgten
die oben beschriebenen Integrationsänderungen; das Skript ist kein Updater.

## Modellgrenzen

Ein eingebundener Treiber ist keine Bestätigung für jede Hardware- oder Firmware-
Variante. Entscheidend sind die vom Gerät übertragenen Bluetooth-Kennungen und der
Funktionsumfang des jeweiligen openScale-Treibers. Die Modellliste wird aus den
übernommenen Registry-Testfällen erzeugt und liegt unter `docs/SUPPORTED_SCALES.md`.

BLE-Modelle werden bei der Suche erkannt. Classic-SPP-Waagen zunächst in den
Android-Bluetooth-Einstellungen koppeln; erkannte gekoppelte Modelle erscheinen
danach in der Geräteliste. Xiaomi S400 benötigt ggf. seinen Bind-Key, bestimmte
Huawei-Modelle benötigen gerätespezifische Schlüssel. Diese können in den
waagenspezifischen Einstellungen eingegeben werden; sie werden nicht beschafft.

Zusätzliche Körperwerte werden teilweise vom ursprünglichen openScale-Treiber
aus Impedanz und Profilangaben geschätzt. Daher müssen Geburtsdatum, Größe,
Geschlecht und Aktivitätsstufe korrekt sein. Für die BS444 bleiben die von der
Waage gelieferten Werte maßgeblich.
