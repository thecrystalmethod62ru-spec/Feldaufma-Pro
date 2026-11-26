# Feldaufmaß Pro – Entwicklungsplan

Android, deutsche Oberfläche, Offline-first. Gemeinsame Objektdaten für Zeichnung, Detail, Mengen und Export. Keine automatische Doppelzählung; Freigabe vor Abrechnung.

## 1 – Kernfunktionen
- [ ] Interaktiver Zeichnungseditor
- [ ] Zentrale Projektdatenbank
- [ ] Kollisionsprüfung und Vermeidung von Doppelzählung
- [ ] Rückgängig/Wiederholen und Änderungshistorie
- [ ] Mehrere Projekte und Sicherungen

## 2 – Abrechnung und Zeichnung
- [ ] GAEB / REB / LV
- [ ] Professioneller PDF- und DXF-Export
- [ ] Zeichnungsebenen
- [ ] Automatischer Längsschnitt

## 3 – Dokumentation
- [ ] GPS-Fotodokumentation
- [ ] KI-Unterlagenanalyse mit Freigabe und Datenschutz
- [ ] Bautagesbericht
- [ ] Nachtragsvorschläge mit menschlicher Prüfung

## 4 – Erweiterungen
- [ ] Bluetooth-Laserdistanzmesser
- [ ] GNSS-RTK mit Genauigkeitsangabe
- [ ] Zusammenarbeit mit Rollen, Konfliktlösung und Offline-Synchronisierung — PAUSIERT (Abschnitt 17; keine Entwicklung bis zur ausdrücklichen Freigabe)

## Abnahmekriterien
Jede Funktion benötigt Build, Tests und Geräteprüfung. Geometrische Mengen sind keine freigegebenen Abrechnungsmengen. Keine ungeprüfte automatische Zuordnung oder Übermittlung vertraulicher Unterlagen.

- [ ] GPS-Koordinaten mit Trassenpunkten verknüpfen und den zugehörigen Zeichnungspunkt beim Verschieben des Kartenpunkts automatisch aktualisieren.

## Rohrleitungsbau – Montage (eigenständiges Modul)
- [ ] Montagezeichnung mit Stationierung, Rohrlängen, Winkeln und Verbindungspunkten; Verknüpfung zur Trasse.
- [ ] Rohre mit DN, Außendurchmesser, Werkstoff, SDR und PN; Formstücke und Armaturen mit individuellen Eigenschaften.
- [ ] Verbindungsregister mit Schweiß-, Steck- und Flanschverbindungen, eindeutigen IDs und Fotodokumentation.
- [ ] Materialliste aus bestätigten Montageobjekten, getrennt von Erdbaumengen und Soll-Planung.
- [ ] Prüfprotokolle für Druckprüfung, Spülung und Desinfektion als Dokumentation, keine automatische technische Freigabe.
- [ ] Projektbezogene Offline-Speicherung, Bearbeitung, Sicherung und PDF-Export.

### Implementierter Umfang im Android-Stand

- Unter **Werkzeuge → Rohrleitungsbau – Montage** lassen sich Standardformstücke anlegen, nachträglich bearbeiten, einer Tiefbau-Station zuordnen und entfernen. Eine Bestätigung des Einbaus übernimmt das Formstück in die Materialliste. Anzahl, Typ, DN und Werkstoff erscheinen im PDF.
- Projektleitungen übernehmen DN bzw. Kabeltyp, Außendurchmesser, Werkstoff, SDR, PN und gemessene Einbaulänge ins Montageaufmaß. Bestätigte Längen werden pro Spezifikation in Metern summiert.
- Der Montageplan nutzt die eingegebenen Abschnittslängen und die Richtungen der Trassenskizze. Er zeigt nummerierte Positionen und erzeugt ein PDF mit Verzeichnis. Fehlende Stationen und uneindeutige seitliche Versätze an Ecken bleiben zur Prüfung offen.
- Das Verbindungsregister speichert Schweiß-, Steck- und Flanschverbindungen mit beständiger Kennung, eigener Nummer und zugeordneten Projektfotos. Der PDF-Bericht enthält die Verbindungseinträge und Fotoseiten.
- Der metrische DXF-Export enthält die Ebenen MONTAGE und VERBINDUNGEN mit Positionsmarkierungen und Beschriftung.
- Druckprüfung, Spülung und Desinfektion lassen sich mit Datum, Leitungsabschnitt, ausführender Person, Messwerten und Ergebnis dokumentieren und als PDF ausgeben.
- Die ZIP-Wiederherstellung unterstützt Mehrrohrprofile mit Positionsfixierung, Abzweigungen, Montageobjekte, Registerfotos und Überlappungseinstellungen.

Offen für die Abnahme sind unter anderem die Prüfung der Symbole an Normvorlagen, die Erkennung von Formstücken aus Handskizzen sowie die Feldprüfung mit Messgeräten.

### Umfang Formstückkatalog
- [ ] Nur Standardformstücke: Bögen 11,25°/22,5°/30°/45°/90°, T-Stücke, Kreuzstücke, Reduzierungen, Muffen, Flansche, Endkappen und Schieber. Keine Herstellerkataloge oder Sonderformstücke.
- [ ] Normgerechte Symbole anhand zugänglicher Normvorlagen prüfen; DN und Werkstoff editierbar, Drehung und Spiegelung möglich.

### Montageplan aus Handskizze
- [ ] Handskizze per Kamera, Galerie oder PDF importieren; zuschneiden und ausrichten.
- [ ] Leitungsverlauf, Abzweige, Standardformstücke, Verbindungen und handschriftliche Maße als überprüfbare Vorschläge erkennen.
- [ ] Unbekannte Maße als 'Maß fehlt' kennzeichnen; keine erfundenen Maße oder automatische Ist-Abrechnung.
- [ ] Bestätigte Vorschläge in editierbare Montagezeichnung mit Bemaßung, Legende und Materialliste überführen; Originalskizze als Quelle behalten.

### Gemeinsames Datenmodell Tiefbau / Rohrleitungsbau
- [ ] Gemeinsame stabile Trassen- und Stations-IDs; Montageobjekte mit Trassenabschnitt, Station, seitlichem Versatz und Höhenlage verknüpfen.
- [ ] Gemeinsame GPS-Referenz und gegenseitige Objektauswahl in Tiefbauzeichnung, Montagezeichnung und Regelprofil.
- [ ] Bei Geometrieänderungen abhängige Positionen neu berechnen; Konflikte und verlorene Referenzen zur Prüfung markieren, bestätigte Ist-Maße nicht stillschweigend ändern.
- [ ] Mehrere Leitungen je Graben unterstützen; getrennte Erdbaumengen und Montagemateriallisten ohne Doppelzählung.
- [ ] Gemeinsame Projektpersistenz, Sicherung und kombinierter oder separater Planexport.
