# Scale2Garmin – Waage direkt zu Garmin

Android-App für **Bluetooth-Waage → Scale2Garmin → Garmin Connect**.
VitaDock+, Health Connect, Health Sync und eine separate openScale-App sind nicht erforderlich.
Android 12 oder neuer. Version **0.7.1**.

## Installieren und aktualisieren

Die aktuelle APK steht unter [GitHub Releases](https://github.com/wischm0b/Scale2Garmin/releases/latest).
Auf dem Handy über die vorhandene Version installieren. Nicht vorher deinstallieren:
Messungen, Garmin-Anmeldung, Waage und Einstellungen bleiben bei einem Update erhalten.

## Empfang und Erfolgsanzeige

- Der Empfang startet ausschließlich durch Herunterziehen der Übersicht oder das
  Verbindungssymbol. Öffnen/Fortsetzen der App und Speichern des Profils starten ihn nicht.
  Die Gerätesuche in den Einstellungen scannt nur zur Einrichtung; die Auswahl startet
  noch keine Messübertragung.
- Zum Herunterziehen muss die Übersicht am oberen Rand stehen. Die App hält die
  Abwärtsgeste nun selbst fest, bis die 64-dp-Auslöseschwelle erreicht ist. Zwei Finger
  bleiben für Zoom und Verschieben des Diagramms reserviert.
- Die Statuszeile gehört ausschließlich zum aktuellen Durchlauf. Gespeicherte alte
  Messungen schalten Scale2Garmin oder Garmin nicht schon beim Öffnen grün.
- Scale2Garmin wird nach Empfang einer Messung des gewählten Profils grün und fett;
  Garmin erst nach bestätigtem Upload dieser Messung.
- Der Bluetooth-Empfang endet nach einer kurzen Nachlaufzeit von zwei Sekunden
  nach einer passenden Messung, spätestens nach 90 Sekunden. Ein Garmin-Upload
  kann unabhängig davon zu Ende laufen. Der Spinner steht nur für laufende Arbeit.
- Zum erneuten Wiegen die Übersicht oben nach unten ziehen oder auf das kleine
  Verbindungssymbol tippen. Ein neuer Durchlauf setzt die Erfolgsanzeige zurück.
- Eine neu empfangene Messung wird einmalig in den Details ausgewählt und im Diagramm sichtbar gemacht. Spätere Statusmeldungen verändern eine danach manuell gewählte Messung nicht.
- Für neue Messungen wird eine Abweichung der Waagenuhr um bis zu fünf Minuten toleriert. Die Originalzeit wird beibehalten; alte Archivmessungen beenden den Empfang nicht.
- Die Android-Benachrichtigung erlaubt zusätzlich das vorzeitige Beenden.
- Automatische Garmin-Uploads sind optional und anfangs aus. Ausstehende Uploads
  können auch später über Androids Netzwerkjob laufen; Bluetooth startet dabei nicht.

## Waage einrichten

1. Unter **⚙ → Deine Waage → Nach Waage suchen** Bluetooth-Zugriff erlauben und
   die Waage aktivieren. Erkannte Waagen stehen im Dropdown vor anderen Geräten.
2. Waage und eigenen Speicherplatz auswählen. Bei der BS444 auf der Waage kg und
   ein eingerichtetes persönliches Profil verwenden; P0 eignet sich nicht für
   die Körperanalyse. VitaDock+ darf die Bluetooth-Verbindung nicht belegen.
3. Bei anderen Modellen das **Waagenprofil** ausfüllen. Das Profil wird separat
   für den ausgewählten Benutzer gespeichert. Falls die Waage eine eigene
   Benutzerzuordnung oder einen Code fordert, erscheint dazu ein Dialog.
4. Unter **Waagenspezifische Einstellungen** können bei Xiaomi S400 ein Bind-Key
   und eine MAC-Adresse sowie bei bestimmten Huawei-Modellen Schlüssel eingetragen
   werden. Diese Schlüssel werden nicht automatisch beschafft.
5. Classic-SPP-Modelle zunächst in Android koppeln. Unterstützte gekoppelte
   Modelle erscheinen anschließend ebenfalls in der Geräteliste.
6. Unter ⚙ Garmin verbinden, die erste Messung manuell synchronisieren und
   anschließend bei Bedarf die automatische Übertragung aktivieren. Eine andere
   Waage oder ein anderer Speicherplatz schaltet Auto-Upload zunächst aus.

Die App enthält die 64 Handler-Varianten des fest eingebundenen openScale-Stands.
[Modellliste](docs/SUPPORTED_SCALES.md) · [Quellen und Integrationsdetails](scale-drivers/UPSTREAM.md).
Ein Treiber ersetzt keinen Test der konkreten Hardware-/Firmware-Version.
Die ursprüngliche BS444-Verbindung wurde vom Nutzer mit Garmin erfolgreich getestet;
die neuen Modelle wurden hier über die übernommenen Referenztests geprüft.

Übernommen werden Gewicht, Körperfett, Wasser, Muskelanteil, Knochenmasse und
Grundumsatz, soweit vorhanden. Reine Gewichtsmessungen sind ebenfalls möglich.
Andere openScale-Messgrößen wie segmentale Werte werden derzeit nicht gespeichert
oder nach Garmin übertragen. Manche Treiber berechnen Körperwerte aus Impedanz und
Profilangaben. Bei der BS444 stammen sie weiterhin direkt von der Waage.

Garmins Muskelmassenfeld benötigt kg. Optional wird `Gewicht × Muskelanteil / 100`
übertragen. Der originale Prozentwert bleibt lokal erhalten.

## Optional: Health Connect

Unter **⚙ → Health Connect → Mit Health Connect synchronisieren** aktivieren und
Androids Schreibberechtigungen freigeben. Der ausgewählte Speicherplatz gilt für
vorhandene und neue Messungen. Einzelne Messwerte dürfen separat freigegeben werden.
Garmin-Anmeldung, Internet und Garmin-Auto-Upload sind dafür nicht erforderlich.
Auf Android 12/13 muss Health Connect installiert sein; ab Android 14 gehört es zum System.

Exportiert werden Gewicht (kg), Körperfett (%), Knochenmasse (kg), Körperwasser
(kg, berechnet als Gewicht × Wasseranteil / 100) und Grundumsatz (kcal/Tag).
Fehlende Werte werden ausgelassen. Muskelanteil wird nicht als fettfreie Masse
umgedeutet und nicht nach Health Connect exportiert.

Die App liest keine Health-Connect-Daten. Stabile Eintrags-IDs verhindern bei
Wiederholungen Duplikate derselben Scale2Garmin-Messung. Von anderen Apps bereits
exportierte Daten werden nicht erkannt oder gelöscht. Garmin und Health Connect
haben voneinander unabhängige Übertragungsbestätigungen. Änderungen am Speicherplatz
oder an der Waage schalten beide automatischen Ziele zunächst aus.

Ausschalten stoppt weitere Übertragungen; bereits exportierte Daten bleiben erhalten
und können in Health Connect verwaltet werden. Werden dort Daten gelöscht, werden
lokal bereits bestätigte Einträge nicht automatisch neu angelegt. Android plant
Hintergrundjobs und kann die Ausführung verzögern. Die Datenschutzansicht ist auch
aus dem Health-Connect-Berechtigungsdialog erreichbar.

## Diagramm und Oberfläche

Mit zwei Fingern horizontal auseinander-/zusammenziehen zoomt die Zeitachse.
Zwei Finger gemeinsam nach links/rechts bewegen verschiebt sie. Ein Finger bleibt
für Seitenscrollen und die Auswahl von Messpunkten verfügbar. Die Geste startet
keinen Waagenempfang. „Zeitausschnitt zurücksetzen“ stellt den gewählten Zeitraum
wieder her. Der Ausschnitt bleibt beim Wechsel in die Einstellungen und bei
Statusmeldungen erhalten. Verschieben ist innerhalb der verfügbaren Historie möglich;
die kleinste Zeitspanne beträgt eine Minute.

Im Menü „Messwert“ wählt ein Tippen auf den Namen genau eine Kurve und schließt das Menü. Die Checkboxen rechts schalten mehrere Kurven sofort um; mindestens eine bleibt aktiv. Der Pfeil oben klappt das Menü ein. Gewicht nutzt
links die kg-Achse; Körperfett, Muskelanteil, Körperwasser und Knochenanteil teilen
sich die Prozentachse (bei gemischter Auswahl rechts). Knochenanteil wird als
Knochenmasse / Körpergewicht × 100 berechnet; gespeicherte und exportierte kg-Werte
bleiben unverändert. Muskelmasse in kg und Grundumsatz sind nicht in der Diagrammauswahl.
Die vertikalen Grenzen bleiben beim Verschieben und Zoomen der Zeitachse stabil.
Dichte Messungen werden anhand der gesamten Historie gruppiert, damit Verschieben
keine Gruppen neu aufteilt. Je eine Nachbargruppe außerhalb des Ausschnitts wird
für die bis zum Rand gezeichneten Linien berücksichtigt. Fehlende Werte zählen
nicht zum Durchschnitt. Die Zeitachse ist für alle Kurven identisch.

Zeiträume: 7/30/90 Tage, 1 Jahr, alles. **Messwert** ersetzt den Begriff Messart.
Zeitraum und Messwert bleiben gespeichert. Punkte, die innerhalb von 12 dp ab dem
Beginn einer Gruppe liegen, werden als arithmetischer Durchschnitt dargestellt;
die x-Position entspricht dem durchschnittlichen Messzeitpunkt. Das verhindert
überlagerte Punkte bei dichtem Verlauf. Originalmessungen bleiben unverändert.
Antippen einer Gruppe zeigt ihren Durchschnitt und einen zugehörigen Einzelwert;
mit den Pfeilen bleiben alle einzelnen Messungen erreichbar.

Unter ⚙ sind **System / Dunkel / Hell** und **System / Deutsch / English** wählbar.
Buttons und Dialoge verwenden die gleichen Farben und abgerundete Formen.
Dialoggröße und Gestaltung stehen vor dem ersten Frame fest. Androids Fenster-Unschärfe
zeichnet den Hintergrund weich, sofern das Gerät diesen Effekt unterstützt; andernfalls
bleibt die Abdunklung. Es wird kein RenderEffect auf die darunterliegenden Buttons gelegt. Die Software-Vorschauen zeigen die native Dialoggestaltung;
der Hardware-Blur kann darin anders aussehen als auf dem Handy.

Übersetzungen: `translations/de-en.json`; `python scripts/update_translations.py`
erzeugt die deutschen und englischen Android-Ressourcen. Neue Einträge hinten
anfügen, bestehende IDs nicht umsortieren. Weitere Sprachen können als
`app/src/main/res/values-xx/strings.xml` ergänzt und in die Sprachauswahl aufgenommen werden.

## Fehlerbehandlung

- Keine Waage: Bluetooth einschalten, Waage aufwecken, andere Waagen-App schließen,
  anschließend die Übersicht nach unten ziehen.
- Körperanalyse fehlt bei BS444: barfuß wiegen, persönliches Profil prüfen.
- Übertragung unklar/unterbrochen: am Messpunkt **Upload prüfen** öffnen, zuerst
  Gewicht und Zeitpunkt in Garmin kontrollieren; erst dann erneut freigeben.
- HTTP 429: Garmin-Pause abwarten. Nicht wiederholt anmelden.
- Loginproblem: die alternative Browser-Anmeldung versuchen. Die Garmin-Anbindung
  nutzt inoffizielle Endpunkte und kann durch Garmin-Änderungen ausfallen.

## Entwicklung

JDK 17+, Android SDK 36.1, Gradle 8.13, Android Gradle Plugin 8.13.2. Unter Windows mit Android Studio:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME = Join-Path $PWD '.gradle-home'
.\gradlew.bat --no-daemon testDebugUnitTest assembleRelease lintDebug
```

`scripts/build.ps1` baut, prüft und legt APK und SHA-256-Prüfsummen unter `releases` ab.
Die Tests umfassen Android-12/15-Oberfläche, Empfangsende, Status, Pull-to-Refresh,
Diagrammgruppen, Übersetzungen, SQLite, FIT-Decodierung mit dem offiziellen Garmin-SDK,
OAuth sowie die übernommenen openScale-Protokoll- und Registry-Referenztests.
Health-Connect-Einheiten, Teilfreigaben, Wiederholungs-IDs und die Datenbankmigration werden zusätzlich getestet.
Der Release-Build entfernt ungenutzten Code mit R8. Treibernamen bleiben als gespeicherte
Geräte-IDs erhalten. Er verwendet für direkte Updates dieselbe lokale Testsignatur wie
die bisherigen Debug-APKs; für eine Store-Veröffentlichung ist ein eigener Signierschlüssel nötig.

## Datenschutz und Lizenz

Messungen und Profile liegen lokal in den privaten Appdaten. Garmin-Sitzungstoken
werden über Android Keystore verschlüsselt; Passwörter werden nicht gespeichert.
Bluetooth-Schlüssel liegen in privaten Treibereinstellungen. Backups und
Gerätetransfer der Appdaten sind ausgeschlossen. Keine Werbung oder Analytik;
Protokoll- und Messdatenlogging der Treiber ist deaktiviert.

Zugangsdaten und Messwerte gehen an Garmin. Beim ersten Login werden öffentliche
OAuth-Clientparameter vom Garth-S3-Endpunkt geladen, ohne Messwerte oder
Garmin-Zugangsdaten dorthin zu übertragen. Deinstallation entfernt lokale Daten.

GPL-3.0-or-later. Quellen und Bibliothekslizenzen: **THIRD_PARTY.md**, **LICENSE**,
**licenses/** und `assets/licenses` in der APK. Der vollständige Quellcode der
angepassten Treiber liegt in **scale-drivers/**.

## Logoentwürfe

Variante **B** ist seit Version 0.5.1 das App-Icon; der sichtbare Name lautet
**Scale2Garmin**. Das adaptive Android-Icon verwendet das Symbol ohne Schriftzug.
Originalentwürfe, Launcher-PNG und Prompts liegen in `design/logos/`.
Die Paketkennung bleibt `de.scalebridge`, damit bestehende Installationen aktualisiert werden können.

## Zeitstempel und wiederverwendete Waagenuhr

Die BS444 kann für eine neue Messung denselben Zeitstempel wie für die vorherige
Messung liefern. Die lokale ID enthält diesen Zeitstempel; bislang wurde die neue
Messung deshalb trotz geänderter Werte als Duplikat verworfen. Bei identischer ID
und abweichenden Messwerten behält die App nun den alten Datensatz und speichert den
neuen mit dem Empfangszeitpunkt des Telefons. Eine bytegenau identische Wiederholung
bleibt ein Duplikat. Normale Waagenzeiten und Archivmessungen werden nicht verändert.

Bei der BS44x ersetzt die App eine Gerätezeit außerhalb des aktuellen Empfangsfensters
durch die Empfangszeit des Smartphones. Die Diagnose bleibt lokal und erscheint nicht
mehr in den Einstellungen.

## Trends und Prognosen

Für jede im Diagramm eingeblendete Messgröße erscheint eine gestrichelte Trendlinie.
Die Berechnung fasst mehrere Werte eines Tages zusammen und verwendet eine robuste
Steigung, damit einzelne ungewöhnliche Messungen die Richtung nur wenig beeinflussen.
Berücksichtigt werden höchstens die letzten 90 Tage.

Unter dem Diagramm zeigt **Trends & Prognosen** die Veränderung pro Woche. Ab fünf
Messtagen über mindestens 14 Tage wird eine 14-Tage-Prognose berechnet; ab acht
Messtagen über mindestens sechs Wochen steigt der Horizont auf 30 Tage. Die angezeigte
Verlässlichkeit beruht auf der Streuung der Messwerte um den Trend. Prognosen verändern
keine gespeicherten Messwerte und sind als Schätzungen zu verstehen.

Über **Wunschgewicht festlegen** wird ein Ziel für den gerade ausgewählten Speicherplatz
gespeichert. Wenn der Gewichtstrend ausreichend belastbar ist und in Richtung des Ziels
läuft, zeigt die App die verbleibende Dauer und ein voraussichtliches Datum. Bei stabilem
oder gegenläufigem Verlauf sowie zu wenigen Daten erklärt sie, warum keine Zeitangabe
möglich ist. Das Wunschgewicht wird als gepunktete Linie eingezeichnet, sofern es im
aktuellen Wertebereich des Diagramms liegt.

## CSV-Sicherung und Import

Unter **⚙ → CSV-Sicherung** speichert **CSV-Backup speichern** sämtliche lokalen
Messungen über Androids System-Dateiauswahl. Dadurch kann als Ziel ein freigegebener
Ordner gewählt werden, den eine QNAP-Backup-App überwacht. Die Datei bleibt außerhalb
des privaten App-Speichers erhalten. Es ist keine zusätzliche Speicherberechtigung nötig.

Für automatische Sicherungen wird über die Android-Ordnerauswahl einmalig ein
Zielordner festgelegt. Nach jeder neuen Messung aktualisiert die App die vollständige
Datei `Scale2Garmin-Backup.csv`; es gibt keinen zeitgesteuerten täglichen Export. Mit
der optionalen Einstellung **Für jeden Tag eine eigene Sicherungsdatei** verwendet sie
stattdessen `Scale2Garmin-Backup-JJJJ-MM-TT.csv` und aktualisiert den jeweiligen
Tagesstand nach neuen Messungen. Die Ordnerfreigabe bleibt nach einem Neustart erhalten.

Das Format ist UTF-8, semikolongetrennt und verwendet ISO-8601-Zeitstempel sowie
sprachunabhängige Dezimalpunkte. Enthalten sind Speicherplatz, Gewicht, Körperfett,
Körperwasser, Muskelanteil, Knochenmasse, Grundumsatz, Waagenkennung,
Synchronisationsstatus und Notiz. Felder mit Sonderzeichen werden nach CSV-Regeln
maskiert.

**CSV importieren** stellt Scale2Garmin-Backups wieder her. Duplikate werden
übersprungen. Der Import läuft vollständig oder gar nicht; eine ungültige Zeile
hinterlässt keinen Teilimport. Dateien sind auf 10 MB und 100.000 Datenzeilen begrenzt.
Die Android-Dateiauswahl schränkt den vom Dateimanager gemeldeten Dateityp nicht ein.
Dadurch bleiben CSV-Dateien auch auswählbar, wenn ein Hersteller sie beispielsweise
als Excel-, Text- oder allgemeinen Dateityp meldet. Unter der Schaltfläche zeigt die
App den aktuellen Auswahl- und Importstatus an.
VitaDock+-Gewichtsexporte werden anhand ihrer Tabellenüberschrift erkannt; der vorangestellte Statistikblock wird ignoriert. Dezimalkommas und lokale Zeitstempel werden verarbeitet. Knochenanteil wird aus Prozent in kg umgerechnet, Muskelmasse (%) als Muskelanteil übernommen. BMI, Aktivität, Stimmung und Mahlzeit werden nicht importiert; Anmerkungen bleiben erhalten.
