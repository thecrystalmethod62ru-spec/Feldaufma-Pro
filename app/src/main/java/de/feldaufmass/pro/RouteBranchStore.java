package de.feldaufmass.pro;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pass the active project's own preferences, never global preferences. */
public final class RouteBranchStore {
    static final String KEY="metricRouteBranchesV1";
    private final SharedPreferences prefs;
    public RouteBranchStore(SharedPreferences projectPrefs){
        if(projectPrefs==null)throw new IllegalArgumentException("Projekt fehlt");
        prefs=projectPrefs;
    }
    public List<RouteBranch> load() throws JSONException {
        return decode(prefs.getString(KEY,"[]"));
    }
    static List<RouteBranch> decode(String json) throws JSONException {
        JSONArray array=new JSONArray(json);
        List<RouteBranch> result=new ArrayList<>();
        Set<String> ids=new HashSet<>();
        for(int i=0;i<array.length();i++){
            RouteBranch branch=RouteBranch.fromJson(array.getJSONObject(i));
            if(!ids.add(branch.id))throw new JSONException("Doppelte Abzweig-ID");
            result.add(branch);
        }
        return result;
    }
    public void save(List<RouteBranch> branches) throws JSONException {
        JSONArray array=new JSONArray();
        Set<String> ids=new HashSet<>();
        for(RouteBranch branch:branches){
            if(branch==null||!ids.add(branch.id))throw new JSONException("Ungültige oder doppelte Abzweig-ID");
            array.put(branch.toJson());
        }
        if(!prefs.edit().putString(KEY,array.toString()).commit())
            throw new IllegalStateException("Abzweigungen konnten nicht gespeichert werden");
    }
}
