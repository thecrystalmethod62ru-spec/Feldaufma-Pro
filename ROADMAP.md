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
