package de.feldaufmass.pro;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Device-local reusable geometry templates, independent of project station ranges. */
final class ProfileTemplateStore {
 private static final String KEY="templates_v1";
 private final SharedPreferences prefs;
 ProfileTemplateStore(SharedPreferences prefs){this.prefs=prefs;}
 static final class Template {
  final String id,name;
  final double width,depth;
  final List<PipeLayout.Pipe> pipes;
  Template(String id,String name,double width,double depth,List<PipeLayout.Pipe> pipes){
   if(name==null||name.trim().isEmpty()||!Double.isFinite(width)||width<=0||!Double.isFinite(depth)||depth<=0)throw new IllegalArgumentException("Ungültiges Profil");
   this.id=id;this.name=name.trim();this.width=width;this.depth=depth;this.pipes=new ArrayList<>(pipes);
  }
  String label(){return name+String.format(Locale.GERMANY," · %.2f × %.2f m",width,depth);}
 }
 List<Template> load() throws org.json.JSONException{
  JSONArray rows=new JSONArray(prefs.getString(KEY,"[]"));List<Template> result=new ArrayList<>();
  for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);result.add(new Template(row.getString("id"),row.getString("name"),row.getDouble("width"),row.getDouble("depth"),PipeLayoutStore.decode(row.getJSONArray("pipes").toString())));}
  return result;
 }
 void add(String name,double width,double depth,List<PipeLayout.Pipe> pipes) throws org.json.JSONException{
  List<Template> rows=load();rows.add(new Template(java.util.UUID.randomUUID().toString(),name,width,depth,pipes));save(rows);
 }
 void remove(String id) throws org.json.JSONException{List<Template> rows=load();rows.removeIf(t->t.id.equals(id));save(rows);}
 private void save(List<Template> rows) throws org.json.JSONException{
  JSONArray json=new JSONArray();
  for(Template t:rows)json.put(new JSONObject().put("id",t.id).put("name",t.name).put("width",t.width).put("depth",t.depth).put("pipes",PipeLayoutStore.encode(t.pipes)));
  if(!prefs.edit().putString(KEY,json.toString()).commit())throw new IllegalStateException("Profilkatalog konnte nicht gespeichert werden");
 }
}
