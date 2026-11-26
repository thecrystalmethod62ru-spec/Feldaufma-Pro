package de.feldaufmass.pro;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class MontageProtocols {
    static final String KEY = "montageProtocols";
    private static final String[] TYPES = {"Druckprüfung", "Spülung", "Desinfektion"};
    private static final String[] KEYS = {"date", "section", "operator", "values", "result"};
    private static final String[] LABELS = {"Datum (JJJJ-MM-TT)", "Leitungsabschnitt", "Durchgeführt von",
            "Messwerte und Einheiten", "Ergebnis und Bemerkungen"};
    private final MainActivity activity;
    private final SharedPreferences prefs;
    private final String projectId;

    MontageProtocols(MainActivity activity) {
        this.activity = activity;
        projectId = activity.activeProject;
        prefs = activity.projectStorage(projectId);
    }

    void show() {
        try {
            JSONArray rows = new JSONArray(prefs.getString(KEY, "[]"));
            String[] labels = new String[rows.length()];
            for (int i=0; i<labels.length; i++) {
                JSONObject row = rows.getJSONObject(i);
                labels[i] = row.getString("type")+" · "+row.getString("date")+" · "+row.getString("section");
            }
            AlertDialog.Builder menu = new AlertDialog.Builder(activity).setTitle("Montageprotokolle")
                    .setPositiveButton("Neues Protokoll", (d,w) -> edit(rows,-1))
                    .setNegativeButton("Zurück", (d,w) -> activity.showMontage());
            if (rows.length()==0) menu.setMessage("Noch keine Protokolle erfasst.");
            else menu.setItems(labels,(d,i) -> edit(rows,i)).setNeutralButton("Als PDF speichern",(d,w) -> {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
                intent.setType("application/pdf"); intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                intent.putExtra(android.content.Intent.EXTRA_TITLE,"Montage_Pruefprotokolle.pdf");
                activity.pendingProtocolsProject = projectId; activity.startActivityForResult(intent,432);
            });
            menu.show();
        } catch (JSONException invalid) { activity.notice("Protokolle konnten nicht gelesen werden."); }
    }

    private void edit(JSONArray rows,int index) {
        JSONObject old = rows.optJSONObject(index);
        LinearLayout form = activity.page();
        form.addView(activity.sheetText("Protokollart",14,true));
        Spinner type = activity.choices(form,TYPES);
        if (old!=null) type.setSelection(Math.max(0,Arrays.asList(TYPES).indexOf(old.optString("type"))));
        EditText[] fields = new EditText[KEYS.length];
        for (int i=0;i<KEYS.length;i++) {
            android.widget.TextView label=activity.sheetText(LABELS[i],14,true);
            form.addView(label);
            EditText field = new EditText(activity);
            field.setId(android.view.View.generateViewId());
            label.setLabelFor(field.getId());
            field.setSingleLine(i<3);
            if (old!=null) field.setText(old.optString(KEYS[i]));
            else if (i==0) field.setText(LocalDate.now().toString());
            fields[i]=field; form.addView(field);
        }
        ScrollView scroll = new ScrollView(activity); scroll.addView(form);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity).setTitle("Prüfung dokumentieren")
                .setView(scroll).setPositiveButton("Speichern",null).setNegativeButton("Abbrechen",(d,w)->show());
        if(old!=null) builder.setNeutralButton("Entfernen",(d,w)->new AlertDialog.Builder(activity)
                .setTitle("Protokoll entfernen?").setMessage(old.optString("type")+" · "+old.optString("section"))
                .setNegativeButton("Abbrechen",(a,b)->show())
                .setPositiveButton("Entfernen",(a,b)->{if(store(rows,index,null))show();}).show());
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            try {
                LocalDate.parse(fields[0].getText().toString().trim());
            } catch(DateTimeParseException invalid) {fields[0].setError("Gültiges Datum eingeben");return;}
            if(fields[1].getText().toString().trim().isEmpty()){fields[1].setError("Leitungsabschnitt angeben");return;}
            try {
                JSONObject row = old==null ? new JSONObject().put("id",UUID.randomUUID().toString()) : new JSONObject(old.toString());
                row.put("type",type.getSelectedItem().toString());
                for(int i=0;i<KEYS.length;i++) row.put(KEYS[i],fields[i].getText().toString().trim());
                if(store(rows,index,row)){dialog.dismiss();show();}
            } catch(JSONException invalid){activity.notice("Protokoll konnte nicht gespeichert werden.");}
        }));
        dialog.show();
    }

    private boolean store(JSONArray original,int index,JSONObject row) {
        try {
            JSONArray next = new JSONArray(prefs.getString(KEY,"[]"));
            if(!projectId.equals(activity.activeProject)||!next.toString().equals(original.toString())){
                activity.notice("Projektdaten geändert. Protokolle erneut öffnen.");return false;
            }
            if(row==null)next.remove(index);
            else if(index<0)next.put(row);
            else next.put(index,row);
            if(prefs.edit().putString(KEY,next.toString()).commit()){
                activity.recordProjectEvent("Montageprotokoll geändert");return true;
            }
        } catch(JSONException invalid){activity.notice("Protokolldaten ungültig.");return false;}
        activity.notice("Protokoll konnte nicht gespeichert werden.");return false;
    }

    void writePdf(Uri target) {
        PdfDocument pdf=new PdfDocument();
        try {
            JSONArray rows=new JSONArray(prefs.getString(KEY,"[]"));
            if(rows.length()==0){activity.notice("Noch keine Protokolle erfasst.");return;}
            int page=1;
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);
                StringBuilder body=new StringBuilder("Projekt: ").append(prefs.getString("project","Projekt"))
                        .append("\nProtokoll-ID: ").append(row.getString("id"));
                for(int j=0;j<KEYS.length;j++){
                    String value=row.optString(KEYS[j]);
                    body.append("\n\n").append(LABELS[j]).append("\n").append(value.isEmpty()?"Nicht erfasst":value);
                }
                page=activity.dailyTextPdf(pdf,page,row.getString("type")+" · Protokoll",body.toString(),null);
            }
            try(OutputStream out=activity.getContentResolver().openOutputStream(target,"wt")){
                if(out==null)throw new IOException();pdf.writeTo(out);
            }
            activity.notice("Montageprotokolle gespeichert.");
        }catch(Exception e){activity.notice("Montageprotokolle konnten nicht exportiert werden.");}
        finally{pdf.close();}
    }
}
