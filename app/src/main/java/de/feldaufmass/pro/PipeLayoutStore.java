package de.feldaufmass.pro;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

public final class PipeLayoutStore {
    static final String KEY="metricMultiPipeLayoutV1";
    static final String SECTION_KEY="metricSectionPipeLayoutsV1";
    private final SharedPreferences prefs;
    public PipeLayoutStore(SharedPreferences prefs){this.prefs=prefs;}
    public List<PipeLayout.Pipe> load() throws JSONException {
        return decode(prefs.getString(KEY,"[]"));
    }
    static List<PipeLayout.Pipe> decode(String json) throws JSONException {
        JSONArray array=new JSONArray(json);
        List<PipeLayout.Pipe> result=new ArrayList<>();
        for(int i=0;i<array.length();i++){
            JSONObject p=array.getJSONObject(i);
            result.add(new PipeLayout.Pipe(p.getString("name"),p.getDouble("offset"),p.getDouble("diameter"),p.getDouble("wall"),p.getDouble("bedding"),p.optDouble("minGap",0),p.optDouble("minCover",0),p.optBoolean("fixed",false)));
        }
        return result;
    }
    static JSONArray encode(List<PipeLayout.Pipe> pipes) throws JSONException {
        JSONArray array=new JSONArray();
        for(PipeLayout.Pipe p:pipes){
            JSONObject o=new JSONObject();
            o.put("name",p.name);o.put("offset",p.offset);o.put("diameter",p.diameter);
            o.put("wall",p.wall);o.put("bedding",p.bedding);o.put("minGap",p.minGap);o.put("minCover",p.minCover);o.put("fixed",p.fixed);array.put(o);
        }
        return array;
    }
    public void save(List<PipeLayout.Pipe> pipes) throws JSONException {
        if(!prefs.edit().putString(KEY,encode(pipes).toString()).commit())
            throw new IllegalStateException("Rohrprofil konnte nicht gespeichert werden");
    }
    public void saveSection(double from,double to,double width,double depth,List<PipeLayout.Pipe> pipes) throws JSONException {
        if(!Double.isFinite(from)||!Double.isFinite(to)||from<0||to<=from||!Double.isFinite(width)||width<=0||!Double.isFinite(depth)||depth<=0)
            throw new IllegalArgumentException("Ungültige Abschnittsmaße");
        JSONArray old=new JSONArray(prefs.getString(SECTION_KEY,"[]")), next=new JSONArray();
        for(int i=0;i<old.length();i++){
            JSONObject row=old.getJSONObject(i);
            double a=row.getDouble("from"),b=row.getDouble("to");
            if(a==from&&b==to)continue;
            if(from<b&&to>a)throw new IllegalArgumentException("Abschnitt überschneidet vorhandenes Rohrprofil");
            next.put(row);
        }
        JSONObject row=new JSONObject();row.put("from",from);row.put("to",to);row.put("width",width);row.put("depth",depth);row.put("pipes",encode(pipes));next.put(row);
        if(!prefs.edit().putString(SECTION_KEY,next.toString()).commit())throw new IllegalStateException("Abschnitt konnte nicht gespeichert werden");
    }
    public void deleteSection(double from,double to) throws JSONException {
        JSONArray rows=sections(),next=new JSONArray();boolean found=false;
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.getDouble("from")==from&&row.getDouble("to")==to){found=true;continue;}next.put(row);}
        if(!found)throw new IllegalArgumentException("Abschnitt nicht gefunden");
        if(!prefs.edit().putString(SECTION_KEY,next.toString()).commit())throw new IllegalStateException("Löschen fehlgeschlagen");
    }
    public JSONArray sections() throws JSONException {return new JSONArray(prefs.getString(SECTION_KEY,"[]"));}
}
