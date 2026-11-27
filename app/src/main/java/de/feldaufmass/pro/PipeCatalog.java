package de.feldaufmass.pro;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Offline pipe dimensions in millimetres; stable keys for local favourites. */
final class PipeCatalog {
 static final class Entry {
  final String medium,material,series;
  final int diameter;
  final double wall;
  Entry(String medium,String material,String series,int diameter,double wall){
   this.medium=medium;this.material=material;this.series=series;this.diameter=diameter;this.wall=wall;
  }
  String key(){return medium+"|"+material+"|"+series+"|"+diameter;}
  String label(){return medium+" · "+material+" · Ø "+diameter+" · "+series;}
  double diameterMetres(){return diameter/1000.0;}
  double wallMetres(){return wall/1000.0;}
 }
 private static final List<Entry> ENTRIES=build();
 private static List<Entry> build(){
  List<Entry> entries=new ArrayList<>();
  int[] diameters={32,40,50,63,75,90,110,125,140,160,180,200,225,250,280,315};
  double[] sdr11={3,3.7,4.6,5.8,6.8,8.2,10,11.4,12.7,14.6,16.4,18.2,20.5,22.7,25.4,28.6};
  double[] sdr17={2,2.4,3,3.8,4.5,5.4,6.6,7.4,8.3,9.5,10.7,11.9,13.4,14.8,16.6,18.7};
  for(String medium:new String[]{"Trinkwasser","Gas"}){
   for(int i=0;i<diameters.length;i++){
    entries.add(new Entry(medium,"PE 100","SDR 11",diameters[i],sdr11[i]));
    if(diameters[i]>=63)entries.add(new Entry(medium,"PE 100","SDR 17",diameters[i],sdr17[i]));
   }
  }
  return Collections.unmodifiableList(entries);
 }
 static List<Entry> entries(){return ENTRIES;}
 private final SharedPreferences preferences;
 PipeCatalog(Context context){preferences=context.getSharedPreferences("pipe_catalog",Context.MODE_PRIVATE);}
 boolean isFavorite(Entry entry){return preferences.getBoolean(entry.key(),false);}
 void toggleFavorite(Entry entry){preferences.edit().putBoolean(entry.key(),!isFavorite(entry)).apply();}
 List<Entry> favorites(){
  List<Entry> result=new ArrayList<>();
  for(Entry entry:ENTRIES)if(isFavorite(entry))result.add(entry);
  return result;
 }
}
