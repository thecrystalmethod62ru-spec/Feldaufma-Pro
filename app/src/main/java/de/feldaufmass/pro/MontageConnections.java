package de.feldaufmass.pro;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.UUID;

/** Project-local register of field-documented pipe joints. */
final class MontageConnections {
    static final String KEY = "montageConnections";
    private static final String[] TYPES = {"Schweißverbindung", "Steckverbindung", "Flanschverbindung"};
    private final MainActivity activity;
    private final String projectId;
    private final SharedPreferences prefs;

    MontageConnections(MainActivity activity) {
        this.activity = activity;
        projectId = activity.activeProject;
        prefs = activity.projectStorage(projectId);
    }

    void show() {
        try {
            JSONArray rows = new JSONArray(prefs.getString(KEY, "[]"));
            String[] labels = new String[rows.length()];
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                labels[i] = row.getString("number") + " · " + row.getString("type")
                        + (row.has("station") ? " · Station " + row.getString("station") + " m" : " · Station offen")
                        + " · Fotos: " + activity.dailyPhotos(row).length();
            }
            AlertDialog.Builder dialog = new AlertDialog.Builder(activity).setTitle("Verbindungsregister")
                    .setPositiveButton("Neue Verbindung", (d, w) -> edit(rows, -1))
                    .setNegativeButton("Zurück", (d, w) -> activity.showMontage());
            if (labels.length == 0) dialog.setMessage("Keine Verbindungen erfasst.");
            else {
                dialog.setItems(labels, (d, i) -> actions(rows, i));
                dialog.setNeutralButton("Als PDF speichern", (d, w) -> activity.exportConnectionsPdf(projectId));
            }
            dialog.show();
        } catch (JSONException invalid) {
            activity.notice("Verbindungsregister konnte nicht gelesen werden.");
        }
    }

    int appendPages(android.graphics.pdf.PdfDocument pdf,int page)throws org.json.JSONException{
            JSONArray rows = new JSONArray(prefs.getString(KEY, "[]"));
            String project = prefs.getString("project", "Projekt");
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                JSONArray photos = activity.dailyPhotos(row);
                String header = project + "\nVerbindung " + row.getString("number") + " · " + row.getString("type");
                String body = header + "\nStation: " + (row.has("station") ? row.getString("station") + " m" : "offen")
                        + "\nKennung: " + row.getString("id") + "\nFotos: " + photos.length()
                        + "\n\nBemerkungen\n" + row.optString("notes");
                page = activity.dailyTextPdf(pdf, page, "Montage – Verbindungsregister", body, null);
                for (int j = 0; j < photos.length(); j++) {
                    JSONObject photo = photos.getJSONObject(j);
                    android.graphics.Bitmap bitmap = activity.reportPhoto(photo.getString("file"));
                    String caption = header + "\n" + photo.optString("info");
                    if (bitmap == null) caption += "\nBilddatei fehlt oder ist nicht lesbar.";
                    page = activity.dailyTextPdf(pdf, page, "Verbindungsfoto " + (j + 1), caption, bitmap);
                }
            }
            return page;
    }
    void writePdf(android.net.Uri target) {
        android.graphics.pdf.PdfDocument pdf = new android.graphics.pdf.PdfDocument();
        try {
            if(appendPages(pdf,1)==1){activity.notice("Keine Einträge vorhanden.");return;}
            try (java.io.OutputStream stream = activity.getContentResolver().openOutputStream(target, "wt")) {
                if (stream == null) throw new java.io.IOException();
                pdf.writeTo(stream);
            }
            activity.notice("Verbindungsregister gespeichert.");
        } catch (Exception e) { activity.notice("Verbindungsregister konnte nicht exportiert werden."); }
        finally { pdf.close(); }
    }

    private void actions(JSONArray rows, int index) {
        JSONObject row = rows.optJSONObject(index);
        new AlertDialog.Builder(activity).setTitle(row.optString("number"))
                .setItems(new String[]{"Bearbeiten", "Fotos zuordnen", "Entfernen"}, (d, choice) -> {
                    if (choice == 0) edit(rows, index);
                    if (choice == 1) activity.chooseProjectPhotos(row, "Fotos zur Verbindung", photos -> {
                        try {
                            JSONObject updated = new JSONObject(row.toString()).put("photos", photos);
                            if (save(rows, index, updated)) show();
                        } catch (JSONException invalid) { activity.notice("Fotozuordnung fehlgeschlagen."); }
                    });
                    if (choice == 2) new AlertDialog.Builder(activity).setTitle("Verbindung entfernen?")
                            .setMessage(row.optString("number") + " aus dem Register entfernen?")
                            .setNegativeButton("Abbrechen", (q, w) -> show())
                            .setPositiveButton("Entfernen", (q, w) -> { if (save(rows, index, null)) show(); }).show();
                }).setNegativeButton("Zurück", (d,w) -> show()).show();
    }

    private void edit(JSONArray rows, int index) {
        JSONObject old = rows.optJSONObject(index);
        LinearLayout form = activity.page();
        EditText number = field(form, "Verbindungsnummer", "number", old);
        Spinner type = activity.choices(form, TYPES);
        if (old != null) type.setSelection(Math.max(0, Arrays.asList(TYPES).indexOf(old.optString("type"))));
        EditText station = field(form, "Station (m, optional)", "station", old);
        station.setInputType(8194);
        EditText notes = field(form, "Bemerkungen", "notes", old);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Verbindung dokumentieren").setView(scroll)
                .setNegativeButton("Abbrechen", (d,w) -> show()).setPositiveButton("Speichern", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            String label = number.getText().toString().trim();
            if (label.isEmpty()) { number.setError("Verbindungsnummer eingeben"); return; }
            for (int i = 0; i < rows.length(); i++) {
                if (i != index && label.equalsIgnoreCase(rows.optJSONObject(i).optString("number"))) {
                    number.setError("Verbindungsnummer bereits vergeben"); return;
                }
            }
            try {
                JSONObject row = old == null ? new JSONObject().put("id", UUID.randomUUID().toString())
                        : new JSONObject(old.toString());
                String raw = station.getText().toString().trim().replace(',', '.');
                if (raw.isEmpty()) row.remove("station");
                else {
                    double value = Double.parseDouble(raw);
                    if (!Double.isFinite(value) || value < 0) throw new NumberFormatException();
                    row.put("station", value);
                }
                row.put("number", label).put("type", type.getSelectedItem().toString())
                        .put("notes", notes.getText().toString().trim());
                if (save(rows, index, row)) { dialog.dismiss(); show(); }
            } catch (NumberFormatException invalid) { station.setError("Gültige Station ab 0 m eingeben"); }
            catch (JSONException invalid) { activity.notice("Verbindung konnte nicht gespeichert werden."); }
        }));
        dialog.show();
    }

    private EditText field(LinearLayout form, String label, String key, JSONObject old) {
        form.addView(activity.sheetText(label, 14, false));
        EditText field = new EditText(activity);
        if (old != null) field.setText(old.optString(key));
        form.addView(field);
        return field;
    }

    private boolean save(JSONArray original, int index, JSONObject replacement) {
        try {
            String current = prefs.getString(KEY, "[]");
            JSONArray next = new JSONArray(current);
            if (!projectId.equals(activity.activeProject) || !next.toString().equals(original.toString())) {
                activity.notice("Projekt oder Register geändert. Register erneut öffnen."); return false;
            }
            if (replacement == null) next.remove(index);
            else if (index < 0) next.put(replacement);
            else next.put(index, replacement);
            if (prefs.edit().putString(KEY, next.toString()).commit()) {
                activity.recordProjectEvent("Montage-Verbindungsregister geändert");
                return true;
            }
        } catch (JSONException invalid) { /* Display the storage error below. */ }
        activity.notice("Verbindungsregister konnte nicht gespeichert werden.");
        return false;
    }
}
