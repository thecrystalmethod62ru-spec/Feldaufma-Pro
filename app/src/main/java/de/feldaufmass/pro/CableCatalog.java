package de.feldaufmass.pro;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Cable designations only; actual outside diameter depends on manufacturer. */
final class CableCatalog {
 static final class Entry {
  final String medium, designation;
  Entry(String medium,String designation){this.medium=medium;this.designation=designation;}
  String key(){return medium+"|"+designation;}
  String label(){return medium+" · "+designation;}
 }
 private static final List<Entry> ENTRIES=build();
 private static List<Entry> build(){
  List<Entry> result=new ArrayList<>();
  for(String size:new String[]{"4x16","4x25","4x35","4x50","4x70","4x95","4x120","4x150","4x185","4x240"})
   result.add(new Entry("Stromkabel","NAYY "+size+" mm²"));
  for(String size:new String[]{"3x1,5","3x2,5","5x2,5","5x4","5x6","5x10","5x16"})
   result.add(new Entry("Stromkabel","NYY-J "+size+" mm²"));
  for(String size:new String[]{"12","24","48","96","144"})
   result.add(new Entry("Glasfaser",size+" Fasern"));
  return Collections.unmodifiableList(result);
 }
 static List<Entry> entries(){return ENTRIES;}
 private final SharedPreferences preferences;
 CableCatalog(Context context){preferences=context.getSharedPreferences("cable_catalog",Context.MODE_PRIVATE);}
 boolean isFavorite(Entry entry){return preferences.getBoolean(entry.key(),false);}
 void toggleFavorite(Entry entry){preferences.edit().putBoolean(entry.key(),!isFavorite(entry)).apply();}
 List<Entry> favorites(){
  List<Entry> result=new ArrayList<>();
  for(Entry entry:ENTRIES)if(isFavorite(entry))result.add(entry);
  return result;
 }
}
