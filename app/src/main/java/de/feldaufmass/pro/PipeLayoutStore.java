package de.feldaufmass.pro;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

public final class PipeLayoutStore {
    private static final String KEY="metricMultiPipeLayoutV1";
    private final SharedPreferences prefs;
    public PipeLayoutStore(SharedPreferences prefs){this.prefs=prefs;}
    public List<PipeLayout.Pipe> load() throws JSONException {
        JSONArray array=new JSONArray(prefs.getString(KEY,"[]"));
        List<PipeLayout.Pipe> result=new ArrayList<>();
        for(int i=0;i<array.length();i++){
            JSONObject p=array.getJSONObject(i);
            result.add(new PipeLayout.Pipe(p.getString("name"),p.getDouble("offset"),p.getDouble("diameter"),p.getDouble("wall"),p.getDouble("bedding"),p.optDouble("minGap",0),p.optDouble("minCover",0)));
        }
        return result;
    }
    public void save(List<PipeLayout.Pipe> pipes) throws JSONException {
        JSONArray array=new JSONArray();
        for(PipeLayout.Pipe p:pipes){
            JSONObject o=new JSONObject();
            o.put("name",p.name);o.put("offset",p.offset);o.put("diameter",p.diameter);
            o.put("wall",p.wall);o.put("bedding",p.bedding);o.put("minGap",p.minGap);o.put("minCover",p.minCover);array.put(o);
        }
        if(!prefs.edit().putString(KEY,array.toString()).commit())
            throw new IllegalStateException("Rohrprofil konnte nicht gespeichert werden");
    }
}
