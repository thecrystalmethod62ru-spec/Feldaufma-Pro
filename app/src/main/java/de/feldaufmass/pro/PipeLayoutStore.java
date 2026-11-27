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
        saveSectionWithSurfaces(from,to,width,depth,pipes,null);
    }
    void saveSectionWithSurfaces(double from,double to,double width,double depth,List<PipeLayout.Pipe> pipes,JSONArray surfaces) throws JSONException {
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
        SharedPreferences.Editor editor=prefs.edit().putString(SECTION_KEY,next.toString());
        if(surfaces!=null)editor.putString("segments",surfaces.toString());
        if(!editor.commit())throw new IllegalStateException("Abschnitt konnte nicht gespeichert werden");
    }
    public void deleteSection(double from,double to) throws JSONException {
        JSONArray rows=sections(),next=new JSONArray();boolean found=false;
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.getDouble("from")==from&&row.getDouble("to")==to){found=true;continue;}next.put(row);}
        if(!found)throw new IllegalArgumentException("Abschnitt nicht gefunden");
        if(!prefs.edit().putString(SECTION_KEY,next.toString()).commit())throw new IllegalStateException("Löschen fehlgeschlagen");
    }
    /** Remap station ranges without changing profile attributes. Reject ambiguous overlaps. */
    static JSONArray remapSections(JSONArray rows, double[] oldStations, double[] newStations) throws JSONException {
        if(oldStations.length!=newStations.length||oldStations.length<2)throw new IllegalArgumentException("Ungültige Stationsgrenzen");
        for(int i=0;i<oldStations.length;i++){
            if(!Double.isFinite(oldStations[i])||!Double.isFinite(newStations[i])||(i>0&&(oldStations[i]<=oldStations[i-1]||newStations[i]<=newStations[i-1])))throw new IllegalArgumentException("Ungültige Stationsgrenzen");
        }
        JSONArray result=new JSONArray(rows.toString());
        for(int i=0;i<result.length();i++){
            JSONObject row=result.getJSONObject(i);
            double from=remapStation(row.getDouble("from"),oldStations,newStations),to=remapStation(row.getDouble("to"),oldStations,newStations);
            if(to<=from)throw new IllegalArgumentException("Ungültiger Abschnitt");
            row.put("from",from).put("to",to);
        }
        return result;
    }
    private static double remapStation(double value,double[] oldStations,double[] newStations){
        if(!Double.isFinite(value)||value<oldStations[0]||value>oldStations[oldStations.length-1])throw new IllegalArgumentException("Station außerhalb der Trasse");
        for(int i=1;i<oldStations.length;i++)if(value<=oldStations[i]){
            if(value==oldStations[i])return newStations[i];
            double fraction=(value-oldStations[i-1])/(oldStations[i]-oldStations[i-1]);
            return newStations[i-1]+fraction*(newStations[i]-newStations[i-1]);
        }
        throw new IllegalArgumentException("Station außerhalb der Trasse");
    }
    public JSONArray sections() throws JSONException {return new JSONArray(prefs.getString(SECTION_KEY,"[]"));}
}
