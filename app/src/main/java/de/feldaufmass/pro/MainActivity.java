package de.feldaufmass.pro;
import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import android.view.*;
import android.graphics.*;
import android.content.SharedPreferences;

public class MainActivity extends Activity {
 static final String LEGACY_PROJECT="legacy";
 String activeProject=LEGACY_PROJECT;
 SharedPreferences projectRegistry(){return getSharedPreferences("project_registry",MODE_PRIVATE);}
 SharedPreferences projectStorage(String id){return LEGACY_PROJECT.equals(id)?super.getPreferences(MODE_PRIVATE):getSharedPreferences("project_"+id,MODE_PRIVATE);}
 @Override public SharedPreferences getPreferences(int mode){return projectStorage(activeProject);}
 void recordProjectEvent(String event){
  try{
   SharedPreferences history=getSharedPreferences("history_"+activeProject,MODE_PRIVATE);
   org.json.JSONArray old=new org.json.JSONArray(history.getString("events","[]")),next=new org.json.JSONArray();
   for(int i=Math.max(0,old.length()-499);i<old.length();i++)next.put(old.get(i));
   next.put(new org.json.JSONObject().put("time",System.currentTimeMillis()).put("event",event));
   if(!history.edit().putString("events",next.toString()).commit())notice("Projektverlauf konnte nicht gespeichert werden.");
  }catch(Exception e){notice("Projektverlauf konnte nicht gespeichert werden.");}
 }
 void showProjectHistory(){
  StringBuilder b=new StringBuilder();
  try{
   org.json.JSONArray events=new org.json.JSONArray(getSharedPreferences("history_"+activeProject,MODE_PRIVATE).getString("events","[]"));
   java.text.DateFormat format=java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT,java.util.Locale.GERMANY);
   for(int i=events.length()-1;i>=0;i--){org.json.JSONObject e=events.getJSONObject(i);b.append(format.format(new java.util.Date(e.getLong("time")))).append(" – ").append(e.getString("event")).append("\n\n");}
  }catch(Exception e){b.append("Projektverlauf nicht lesbar.");}
  new android.app.AlertDialog.Builder(this).setTitle("Projektverlauf").setMessage(b.length()==0?"Noch keine Änderungen erfasst.":b.toString()).setPositiveButton("Schließen",null).show();
 }
 static final int AUTO_BACKUP_COUNT=5;
 static final long AUTO_BACKUP_LIMIT=256L*1024*1024;
 static final java.util.concurrent.ExecutorService backupExecutor=java.util.concurrent.Executors.newSingleThreadExecutor();
 static final java.util.Set<String> pendingBackups=java.util.concurrent.ConcurrentHashMap.newKeySet();
 java.io.File backupDirectory(String id){return new java.io.File(getFilesDir(),"backups/"+id);}
 java.io.File[] automaticBackups(String id){
  java.io.File[] files=backupDirectory(id).listFiles(f->f.isFile()&&f.getName().matches("[0-9]+_[a-f0-9]{64}\\.zip"));
  if(files==null)return new java.io.File[0];
  java.util.Arrays.sort(files,(a,b)->b.getName().compareTo(a.getName()));return files;
 }
 static String backupFingerprint(java.util.Map<String,?> snapshot)throws Exception{
  java.security.MessageDigest md=java.security.MessageDigest.getInstance("SHA-256");
  org.json.JSONArray values=new org.json.JSONArray();
  for(String key:new java.util.TreeSet<>(snapshot.keySet())){
   if(key.equals("lastAutosave")||key.equals("routeUndo")||key.equals("routeRedo")||key.equals("routeHistoryHead"))continue;
   Object value=snapshot.get(key);values.put(new org.json.JSONArray().put(key).put(value.getClass().getSimpleName()).put(value));
  }
  StringBuilder hex=new StringBuilder();for(byte b:md.digest(values.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return hex.toString();
 }
 void scheduleAutomaticBackup(){
  final String id=activeProject;
  final java.util.Map<String,?> snapshot=new java.util.HashMap<>(getPreferences(0).getAll());
  if(snapshot.isEmpty()||!pendingBackups.add(id))return;
  backupExecutor.execute(()->{
   java.io.File temp=null;
   try{
    String hash=backupFingerprint(snapshot);
    java.io.File[] previous=automaticBackups(id);
    if(previous.length>0&&previous[0].getName().endsWith("_"+hash+".zip"))return;
    java.io.File dir=backupDirectory(id);if(!dir.isDirectory()&&!dir.mkdirs())throw new java.io.IOException("Sicherungsordner fehlt");
    temp=java.io.File.createTempFile("pending_",".tmp",dir);writeProjectBackup(temp,snapshot);
    if(temp.length()>AUTO_BACKUP_LIMIT)throw new java.io.IOException("Sicherung zu groß");
    java.io.File target=new java.io.File(dir,System.currentTimeMillis()+"_"+hash+".zip");
    if(!temp.renameTo(target))throw new java.io.IOException("Sicherung konnte nicht abgeschlossen werden");
    long total=0;int kept=0;
    for(java.io.File f:automaticBackups(id)){
     if(kept>=AUTO_BACKUP_COUNT||total+f.length()>AUTO_BACKUP_LIMIT){if(!f.delete())android.util.Log.w("Feldaufmass","Alte Sicherung nicht löschbar");}
     else{kept++;total+=f.length();}
    }
   }catch(Exception e){
    android.util.Log.w("Feldaufmass","Automatische Sicherung fehlgeschlagen",e);
    runOnUiThread(()->{if(!isDestroyed()&&id.equals(activeProject))notice("Automatische Sicherung fehlgeschlagen. Manuelle Sicherung prüfen.");});
   }finally{if(temp!=null)temp.delete();pendingBackups.remove(id);}
  });
 }
 void showAutomaticBackups(){
  java.io.File[] files=automaticBackups(activeProject);
  if(files.length==0){new android.app.AlertDialog.Builder(this).setTitle("Sicherungsversionen").setMessage("Noch keine automatische Sicherung vorhanden. Sicherungen entstehen bei geänderten Daten während der Nutzung.").setPositiveButton("Schließen",null).show();return;}
  String[] labels=new String[files.length];
  java.text.DateFormat format=java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,java.text.DateFormat.SHORT,java.util.Locale.GERMANY);
  for(int j=0;j<files.length;j++){
   long timestamp=Long.parseLong(files[j].getName().split("_")[0]);
   labels[j]=format.format(new java.util.Date(timestamp))+" · "+String.format(java.util.Locale.GERMANY,"%.1f MB",files[j].length()/1048576.0);
  }
  new android.app.AlertDialog.Builder(this).setTitle("Sicherungsversionen").setItems(labels,(d,w)->{
   new android.app.AlertDialog.Builder(this).setTitle("Version wiederherstellen").setMessage(labels[w]+"\nAls separates Projekt importieren?").setNegativeButton("Abbrechen",null).setPositiveButton("Wiederherstellen",(a,b)->importProjectBackup(android.net.Uri.fromFile(files[w]))).show();
  }).setNegativeButton("Schließen",null).show();
 }
 void requestProjectRestore(){
  new android.app.AlertDialog.Builder(this).setTitle("Projekt wiederherstellen").setMessage("Eine ZIP-Sicherung wird als neues Projekt importiert. Bestehende Projekte bleiben unverändert. Externe Unterlagen müssen neu verknüpft werden.").setNegativeButton("Abbrechen",null).setPositiveButton("ZIP auswählen",(d,w)->{
   android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);i.setType("application/zip");i.addCategory(android.content.Intent.CATEGORY_OPENABLE);startActivityForResult(i,415);
  }).show();
 }
 static long copyRestoreBytes(java.io.InputStream in,java.io.OutputStream out,long limit)throws java.io.IOException{
  byte[] buffer=new byte[32768];long total=0;int n;while((n=in.read(buffer))!=-1){total+=n;if(total>limit)throw new java.io.IOException("Sicherung zu groß");out.write(buffer,0,n);}return total;
 }
 void importProjectBackup(android.net.Uri source){
  new Thread(()->{
   String id=java.util.UUID.randomUUID().toString();
   java.util.List<java.io.File> created=new java.util.ArrayList<>();
   try(java.io.InputStream in=getContentResolver().openInputStream(source)){
    if(in==null)throw new java.io.IOException("Datei nicht verfügbar");
    String name=readProjectBackup(in,id,created);
    runOnUiThread(()->{
     java.util.Set<String> ids=new java.util.HashSet<>(projectRegistry().getStringSet("ids",java.util.Collections.emptySet()));ids.add(id);
     if(!projectRegistry().edit().putStringSet("ids",ids).commit()){deleteSharedPreferences("project_"+id);for(java.io.File f:created)f.delete();if(!isDestroyed())notice("Import fehlgeschlagen.");return;}
     if(!isDestroyed())notice(name+" importiert. Unter Werkzeuge → Projekte öffnen. Externe Unterlagen neu verknüpfen.");
    });
   }catch(Exception e){deleteSharedPreferences("project_"+id);for(java.io.File f:created)f.delete();runOnUiThread(()->{if(!isDestroyed())notice("Import fehlgeschlagen. Sicherung prüfen.");});}
  },"project-restore").start();
 }
 String readProjectBackup(java.io.InputStream input,String id,java.util.List<java.io.File> created)throws Exception{
  java.util.Map<String,String> photos=new java.util.HashMap<>();
  org.json.JSONObject manifest=null;
  java.io.File dir=new java.io.File(getFilesDir(),"photos");if(!dir.isDirectory()&&!dir.mkdirs())throw new java.io.IOException("Fotoordner fehlt");
  long remaining=512L*1024*1024;int count=0;
  try(java.util.zip.ZipInputStream zip=new java.util.zip.ZipInputStream(input)){
   java.util.zip.ZipEntry entry;
   while((entry=zip.getNextEntry())!=null){
    if(++count>5000||entry.isDirectory())throw new java.io.IOException("Ungültiges Archiv");
    String path=entry.getName();
    if(path.equals("project.json")){
     if(manifest!=null)throw new java.io.IOException("Doppeltes Manifest");
     java.io.ByteArrayOutputStream data=new java.io.ByteArrayOutputStream();
     remaining-=copyRestoreBytes(zip,data,Math.min(remaining,16L*1024*1024));
     manifest=new org.json.JSONObject(data.toString("UTF-8"));
    }else if(path.matches("photos/[A-Za-z0-9._-]+")){
     String old=path.substring(7);if(old.equals(".")||old.equals("..")||photos.containsKey(old))throw new java.io.IOException("Ungültiger Fotopfad");
     java.io.File f=java.io.File.createTempFile("import_",".jpg",dir);created.add(f);
     try(java.io.OutputStream out=new java.io.FileOutputStream(f)){remaining-=copyRestoreBytes(zip,out,remaining);}
     photos.put(old,f.getName());
    }else throw new java.io.IOException("Unbekannter Archiveintrag");
    zip.closeEntry();
   }
  }
  if(manifest==null||!"FeldaufmassPro".equals(manifest.optString("format"))||manifest.optInt("version")!=1)throw new java.io.IOException("Unbekanntes Sicherungsformat");
  org.json.JSONObject fields=manifest.getJSONObject("preferences");
  java.util.Set<String> strings=new java.util.HashSet<>(java.util.Arrays.asList("project","length","width","depth","base","pipe","asphalt","left","right","stoneLength","stoneWidth","stoneDepth","stoneColor","pattern","bedding","underlying","stationFrom","stationTo","objectStation","objectNote","sollLength","sollWidth","sollDepth","sollAsphalt","sollSource","routeSketch","objects","segments","documents","sollUtilities","montageFittings","metricWidthSections","objectUndo","objectRedo","elevationPoints","lvPositions","dailyReports","nachtraege","routeUnderlay","routeUnderlayTransform","sketchCalibration","independentGpsTrack","importedGpsReference",UTILITY_SHEETS_KEY));
  strings.addAll(java.util.Arrays.asList("metricAreaCorrection","metricAreaCorrectionReason","metricCrossSectionDimensions",
      "metricDepthSections","metricElevationPoints","metricMainExcavationDepth","metricMinimumCover","metricPipeMinGap",
      "metricPipeMinCover","metricPipeProfile","extraUtilities",MontageProtocols.KEY,MontageConnections.KEY,PipeLayoutStore.KEY,PipeLayoutStore.SECTION_KEY,"metricVerbauSections",RouteBranchStore.KEY));
  java.util.Set<String> ints=new java.util.HashSet<>(java.util.Arrays.asList("surface","handling","dictationLanguage","gpsDurationSeconds","metricCornerMode","metricOverlapMode","routeUnderlayAlpha","routeUnderlayHidden"));
  SharedPreferences.Editor ed=projectStorage(id).edit();
  java.util.Iterator<String> keys=fields.keys();String name="Importiertes Projekt";java.util.Set<String> used=new java.util.HashSet<>();
  while(keys.hasNext()){
   String key=keys.next();org.json.JSONObject field=fields.getJSONObject(key);String type=field.getString("type");Object value=field.get("value");
   if(strings.contains(key)&&type.equals("String")&&value instanceof String){
    String v=(String)value;
    if(key.equals("importedGpsReference")){
     org.json.JSONArray rows=new org.json.JSONArray(v);if(rows.length()<2||rows.length()>10000)throw new java.io.IOException("Ungültige GPX-Punktanzahl");
     for(int j=0;j<rows.length();j++){org.json.JSONObject row=rows.getJSONObject(j);double lat=row.getDouble("lat"),lon=row.getDouble("lon");if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new java.io.IOException("Ungültige GPX-Referenz");}
    }else if(key.equals("independentGpsTrack")){
     org.json.JSONArray rows=new org.json.JSONArray(v);if(rows.length()>10000)throw new java.io.IOException("GPS-Punktlimit überschritten");
     long previous=0;for(int j=0;j<rows.length();j++){org.json.JSONObject row=rows.getJSONObject(j);double lat=row.getDouble("lat"),lon=row.getDouble("lon"),accuracy=row.getDouble("accuracy");long time=row.getLong("time");if(!Double.isFinite(lat)||!Double.isFinite(lon)||!Double.isFinite(accuracy)||Math.abs(lat)>90||Math.abs(lon)>180||accuracy<=0||accuracy>15||time<=previous)throw new java.io.IOException("Ungültige GPS-Spur");previous=time;}
    }else if(key.equals("routeSketch")){
     org.json.JSONArray rows=new org.json.JSONArray(v);
     for(int j=0;j<rows.length();j++){
      if(rows.optJSONObject(j)!=null){RouteSketch.SingleObject.decode(rows.getJSONObject(j));continue;}
      org.json.JSONArray row=rows.getJSONArray(j);double x=row.getDouble(0),y=row.getDouble(1);
      if(!Double.isFinite(x)||!Double.isFinite(y)||x<0||x>1||y<0||y>1)throw new java.io.IOException("Ungültiger Trassenpunkt");
      org.json.JSONArray items=row.optJSONArray(4);if(items==null)continue;
      for(int k=0;k<items.length();k++){String old=items.getString(k),replacement=photos.get(old);if(replacement==null)throw new java.io.IOException("Foto fehlt");used.add(old);items.put(k,replacement);org.json.JSONObject meta=row.optJSONObject(10);if(meta!=null&&meta.has(old)){Object photoMeta=meta.remove(old);meta.put(replacement,photoMeta);}}
     }v=rows.toString();
    }else if(key.equals(UTILITY_SHEETS_KEY)){org.json.JSONArray sheets=new org.json.JSONArray(v);if(sheets.length()>MAX_UTILITY_SHEETS)throw new java.io.IOException("Zu viele Planblätter");for(int j=0;j<sheets.length();j++){org.json.JSONObject row=sheets.getJSONObject(j);String old=row.getString("file"),replacement=photos.get(old);if(replacement==null)throw new java.io.IOException("Planblatt fehlt");used.add(old);row.put("file",replacement);for(String dimensionKey:new String[]{"x","y","scale","rotation","alpha"}){double dimensionValue=row.getDouble(dimensionKey);if(!Double.isFinite(dimensionValue)||("scale".equals(dimensionKey)&&(dimensionValue<.1||dimensionValue>5))||("alpha".equals(dimensionKey)&&(dimensionValue<0||dimensionValue>255))||(("x".equals(dimensionKey)||"y".equals(dimensionKey))&&Math.abs(dimensionValue)>2)||("rotation".equals(dimensionKey)&&Math.abs(dimensionValue)>180))throw new java.io.IOException("Ungültige Planblatt-Ausrichtung");}}v=sheets.toString();
    }else if(key.equals("routeUnderlay")){if(!v.isEmpty()){String replacement=photos.get(v);if(replacement==null)throw new java.io.IOException("Zeichenvorlage fehlt");used.add(v);v=replacement;}}
    else if(key.equals("documents")){
     org.json.JSONArray docs=new org.json.JSONArray(v);for(int j=0;j<docs.length();j++){org.json.JSONObject doc=docs.getJSONObject(j);doc.remove("uri");doc.put("name",doc.optString("name")+" (neu verknüpfen)");}v=docs.toString();
    }else if(key.equals("dailyReports")||key.equals("nachtraege")||key.equals(MontageConnections.KEY)){org.json.JSONArray rows=new org.json.JSONArray(v);for(int j=0;j<rows.length();j++){org.json.JSONArray items=dailyPhotos(rows.getJSONObject(j));for(int k=0;k<items.length();k++){org.json.JSONObject photo=items.getJSONObject(k);String old=photo.getString("file"),replacement=photos.get(old);if(replacement==null)throw new java.io.IOException("Berichtsfoto fehlt");used.add(old);photo.put("file",replacement);}}v=rows.toString();
    }else if(key.equals("objects")||key.equals("segments")||key.equals("elevationPoints")||key.equals("lvPositions")||key.equals("sollUtilities")||key.equals("montageFittings")||key.equals("metricWidthSections")){org.json.JSONArray rows=new org.json.JSONArray(v);for(int j=0;j<rows.length();j++){org.json.JSONObject o=rows.getJSONObject(j);if(key.equals("metricWidthSections")){double from=o.getDouble("from"),to=o.getDouble("to"),width=o.getDouble("width");for(String side:new String[]{"left","right"})if(o.has(side)){double cut=o.getDouble(side);if(!Double.isFinite(cut)||cut<0)throw new org.json.JSONException("Ungültiger Nachschnitt");}if(!Double.isFinite(from)||!Double.isFinite(to)||!Double.isFinite(width)||from<0||to<=from||width<=0)throw new org.json.JSONException("Ungültiger Breitenabschnitt");for(int k=0;k<j;k++){org.json.JSONObject previous=rows.getJSONObject(k);if(from<previous.getDouble("to")&&to>previous.getDouble("from"))throw new org.json.JSONException("Überlappende Breitenabschnitte");}}}}
    else if(key.equals("objectUndo")||key.equals("objectRedo")){org.json.JSONArray rows=new org.json.JSONArray(v);for(int j=0;j<rows.length();j++)new org.json.JSONArray(rows.getString(j));}
    if(key.equals(PipeLayoutStore.KEY))PipeLayoutStore.decode(v);
    if(key.equals(PipeLayoutStore.SECTION_KEY)){
     org.json.JSONArray rows=new org.json.JSONArray(v);
     for(int j=0;j<rows.length();j++){
      org.json.JSONObject row=rows.getJSONObject(j);
      double from=row.getDouble("from"),to=row.getDouble("to"),bw=row.getDouble("width"),bd=row.getDouble("depth");
      if(!Double.isFinite(from+to+bw+bd)||from<0||to<=from||bw<=0||bd<=0)throw new org.json.JSONException("Ungültiges Abschnittsprofil");
      PipeLayoutStore.decode(row.getJSONArray("pipes").toString());
      for(int k=0;k<j;k++){org.json.JSONObject prev=rows.getJSONObject(k);if(from<prev.getDouble("to")&&to>prev.getDouble("from"))throw new org.json.JSONException("Überlappende Abschnittsprofile");}
     }
    }
    if(key.equals("metricVerbauSections")){
     org.json.JSONArray rows=new org.json.JSONArray(v);
     for(int j=0;j<rows.length();j++){
      org.json.JSONArray row=rows.getJSONArray(j);
      if(row.length()!=4)throw new org.json.JSONException("Ungültiger Verbauabschnitt");
      double from=row.getDouble(0),to=row.getDouble(1),left=row.getDouble(2),right=row.getDouble(3);
      if(!Double.isFinite(from+to+left+right)||from<0||to<=from||left<0||right<0||left+right<=0)throw new org.json.JSONException("Ungültige Verbaumaße");
      for(int k=0;k<j;k++){org.json.JSONArray prev=rows.getJSONArray(k);if(from<prev.getDouble(1)&&to>prev.getDouble(0)&&((left>0&&prev.getDouble(2)>0)||(right>0&&prev.getDouble(3)>0)))throw new org.json.JSONException("Überlappende Verbauabschnitte");}
     }
    }
    if(key.equals(RouteBranchStore.KEY))RouteBranchStore.decode(v);
    if(key.equals("extraUtilities")||key.equals(MontageProtocols.KEY))new org.json.JSONArray(v);
    if(key.equals("project")&&!v.trim().isEmpty())name=v.trim();
    ed.putString(key,v);
   }else if(ints.contains(key)&&type.equals("Integer")&&value instanceof Number){
    int n=field.getInt("value");if(key.equals("dictationLanguage")&&(n<0||n>1))throw new java.io.IOException("Ungültige Sprache");ed.putInt(key,n);
   }else if(key.equals("metricIncludeCorners")&&type.equals("Boolean")&&value instanceof Boolean){ed.putBoolean(key,(Boolean)value);}
   else if(key.equals("lastAutosave")&&type.equals("Long")&&value instanceof Number){ed.putLong(key,field.getLong("value"));}
   else throw new java.io.IOException("Unbekannte Projekteinstellung: "+key);
  }
  if(!used.equals(photos.keySet()))throw new java.io.IOException("Nicht zugeordnete Fotos");
  name+=" (Import)";ed.putString("project",name);if(!ed.commit())throw new java.io.IOException("Projekt konnte nicht gespeichert werden");
  return name;
 }
 void requestProjectBackup(){
  new android.app.AlertDialog.Builder(this).setTitle("Projekt sichern").setMessage("Gespeicherte Projektdaten und Fotos als ZIP exportieren. Externe Unterlagen bleiben Verweise. Bitte Änderungen vorher speichern.").setNegativeButton("Abbrechen",null).setPositiveButton("Exportieren",(d,w)->{
   android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);i.setType("application/zip");i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
   String name=getPreferences(0).getString("project","Projekt").replaceAll("[^\\p{L}\\p{N}._-]","_");
   i.putExtra(android.content.Intent.EXTRA_TITLE,name+"_"+System.currentTimeMillis()+".zip");startActivityForResult(i,414);
  }).show();
 }
 static void copyBackupBytes(java.io.InputStream in,java.io.OutputStream out)throws java.io.IOException{
  byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
 }
 void writeProjectBackup(java.io.File target,java.util.Map<String,?> snapshot)throws Exception{
  org.json.JSONObject fields=new org.json.JSONObject();
  for(java.util.Map.Entry<String,?> e:snapshot.entrySet()){
   if(e.getKey().equals("routeUndo")||e.getKey().equals("routeRedo")||e.getKey().equals("routeHistoryHead"))continue;
   Object v=e.getValue();
   if(!(v instanceof String||v instanceof Boolean||v instanceof Integer||v instanceof Long||v instanceof Float))throw new java.io.IOException("Nicht unterstützter Datentyp");
   fields.put(e.getKey(),new org.json.JSONObject().put("type",v.getClass().getSimpleName()).put("value",v));
  }
  org.json.JSONObject manifest=new org.json.JSONObject().put("format","FeldaufmassPro").put("version",1).put("createdAt",System.currentTimeMillis()).put("preferences",fields);
  java.util.Set<String> photos=new java.util.TreeSet<>();
  Object data=snapshot.get("routeSketch");
  org.json.JSONArray rows=new org.json.JSONArray(data==null?"[]":(String)data);
  for(int j=0;j<rows.length();j++){
   if(rows.optJSONObject(j)!=null){RouteSketch.SingleObject.decode(rows.getJSONObject(j));continue;}
   org.json.JSONArray items=rows.getJSONArray(j).optJSONArray(4);if(items==null)continue;
   for(int k=0;k<items.length();k++){
    String name=items.getString(k);if(!name.matches("[A-Za-z0-9._-]+")||name.equals(".")||name.equals(".."))throw new java.io.IOException("Ungültiger Fotodateiname");
    photos.add(name);
   }
  }
  Object template=snapshot.get("routeUnderlay");if(template instanceof String&&!((String)template).isEmpty()){String filename=(String)template;if(!filename.matches("[A-Za-z0-9._-]+")||filename.equals(".")||filename.equals(".."))throw new java.io.IOException("Ungültige Vorlage");photos.add(filename);}
  Object sheetsData=snapshot.get(UTILITY_SHEETS_KEY);if(sheetsData instanceof String){org.json.JSONArray sheets=new org.json.JSONArray((String)sheetsData);for(int j=0;j<sheets.length();j++){String name=sheets.getJSONObject(j).getString("file");if(!name.matches("[A-Za-z0-9._-]+")||name.equals(".")||name.equals(".."))throw new java.io.IOException("Ungültiger Planblattname");photos.add(name);}}
  for(String reportKey:new String[]{"dailyReports","nachtraege",MontageConnections.KEY}){
  Object reportsData=snapshot.get(reportKey);
  if(reportsData instanceof String){org.json.JSONArray reports=new org.json.JSONArray((String)reportsData);for(int j=0;j<reports.length();j++){org.json.JSONArray items=dailyPhotos(reports.getJSONObject(j));for(int k=0;k<items.length();k++){String name=items.getJSONObject(k).getString("file");if(!name.matches("[A-Za-z0-9._-]+")||name.equals(".")||name.equals(".."))throw new java.io.IOException("Ungültiger Fotodateiname");photos.add(name);}}}
  }
  try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(target))){
   zip.putNextEntry(new java.util.zip.ZipEntry("project.json"));zip.write(manifest.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
   for(String name:photos){
    java.io.File f=new java.io.File(getFilesDir(),"photos/"+name);
    if(!f.isFile())throw new java.io.IOException("Foto fehlt: "+name);
    zip.putNextEntry(new java.util.zip.ZipEntry("photos/"+name));try(java.io.InputStream in=new java.io.FileInputStream(f)){copyBackupBytes(in,zip);}zip.closeEntry();
   }
  }
 }
 void exportProjectBackup(android.net.Uri target){
  java.util.Map<String,?> snapshot=new java.util.HashMap<>(getPreferences(0).getAll());
  new Thread(()->{
   java.io.File temp=null;
   try{
    temp=java.io.File.createTempFile("project_backup_",".zip",getCacheDir());writeProjectBackup(temp,snapshot);
    try(java.io.InputStream in=new java.io.FileInputStream(temp);java.io.OutputStream out=getContentResolver().openOutputStream(target,"wt")){
     if(out==null)throw new java.io.IOException("Zieldatei nicht verfügbar");copyBackupBytes(in,out);
    }
    runOnUiThread(()->{if(!isDestroyed())notice("Projektsicherung exportiert.");});
   }catch(Exception e){runOnUiThread(()->{if(!isDestroyed())notice("Sicherung fehlgeschlagen. Zieldatei kann unvollständig sein.");});}
   finally{if(temp!=null)temp.delete();}
  },"project-backup").start();
 }
 boolean projectChosen=false;
 private static final String CLOUD_PREFS="cloud_shortcut";
 private android.net.Uri validatedCloudUri(String input){
  try{
   android.net.Uri uri=android.net.Uri.parse(input.trim());
   if("https".equalsIgnoreCase(uri.getScheme())&&"login.yoursecurecloud.de".equalsIgnoreCase(uri.getHost())&&uri.getUserInfo()==null&&(uri.getPort()==-1||uri.getPort()==443))return uri;
  }catch(RuntimeException ignored){}
  return null;
 }
 void launchCloud(android.net.Uri uri){
  try{startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,uri).addCategory(android.content.Intent.CATEGORY_BROWSABLE));}
  catch(android.content.ActivityNotFoundException|SecurityException ex){notice("Kein Browser verfügbar. Cloud konnte nicht geöffnet werden.");}
 }
 void openCloud(){
  SharedPreferences prefs=getSharedPreferences(CLOUD_PREFS,MODE_PRIVATE);
  android.net.Uri saved=validatedCloudUri(prefs.getString("url",""));
  if(saved!=null){launchCloud(saved);return;}
  EditText link=new EditText(this);link.setSingleLine(true);link.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);link.setHint("https://login.yoursecurecloud.de/…");
  LinearLayout form=page();form.addView(sheetText("Cloud-Link einmalig hinterlegen. Er gilt für alle Projekte auf diesem Gerät und bleibt außerhalb der Projektsicherungen.",14,false));form.addView(link);
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Cloud").setView(form).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern und öffnen",null).create();
  dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(button->{
   android.net.Uri uri=validatedCloudUri(link.getText().toString());
   if(uri==null){link.setError("Gültigen HTTPS-Link zu login.yoursecurecloud.de eingeben");return;}
   if(!prefs.edit().putString("url",uri.toString()).commit()){notice("Cloud-Link konnte nicht gespeichert werden.");return;}
   dialog.dismiss();launchCloud(uri);
  }));dialog.show();
 }
 void showProjectStart(){
  LinearLayout start=page();start.setGravity(Gravity.TOP);start.setBackgroundColor(Color.BLACK);start.setPadding(dp(20),dp(16),dp(20),dp(12));setContentView(start);fitSystemPanels(start);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);start.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout content=page();content.setPadding(0,0,0,0);content.setBackgroundColor(Color.BLACK);scroll.addView(content);
  TextView brand=sheetText("Feldaufmaß Pro",26,true);android.text.SpannableString brandText=new android.text.SpannableString("Feldaufmaß Pro");brandText.setSpan(new android.text.style.ForegroundColorSpan(0xff26d9d0),11,14,0);brand.setText(brandText);content.addView(brand);
  TextView subtitle=sheetText("Tiefbau · Leitungsbau",14,false);subtitle.setTextColor(0xff9aabaa);content.addView(subtitle);
  View space=new View(this);content.addView(space,new LinearLayout.LayoutParams(-1,dp(12)));
  overviewAction(content,"Neues Projekt erstellen","objects",()->newProjectDialog());
  Button create=(Button)content.getChildAt(content.getChildCount()-1);
  create.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33ffffff),proButtonShape(true),null));
  LinearLayout shortcuts=new LinearLayout(this);shortcuts.setGravity(Gravity.CENTER);content.addView(shortcuts,new LinearLayout.LayoutParams(-1,-2));
  String[] shortcutNames={"Zeichnung","Regelprofil","Mengen","PDF"},shortcutIcons={"ruler","layers","sections","document"};
  for(int j=0;j<shortcutNames.length;j++){
   Button shortcut=new Button(this);styleEditorButton(shortcut,false);compactToolIcon(shortcut,shortcutNames[j],shortcutIcons[j]);
   LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(64),1);sp.setMargins(dp(2),dp(4),dp(2),dp(4));shortcuts.addView(shortcut,sp);
   shortcut.setOnClickListener(v->showProjects());
  }
  LinearLayout recentHeader=new LinearLayout(this);recentHeader.setGravity(Gravity.CENTER_VERTICAL);recentHeader.setPadding(0,dp(12),0,dp(4));content.addView(recentHeader);
  recentHeader.addView(sheetText("Projekte",17,true),new LinearLayout.LayoutParams(0,-2,1));
  Button all=new Button(this);styleEditorButton(all,false);all.setText("Alle anzeigen ›");all.setTextColor(0xff26d9d0);all.setOnClickListener(v->showProjects());recentHeader.addView(all,new LinearLayout.LayoutParams(-2,dp(44)));
  java.util.ArrayList<String> ids=new java.util.ArrayList<>(projectRegistry().getStringSet("ids",java.util.Collections.emptySet()));
  ids.remove(LEGACY_PROJECT);
  java.util.Collections.sort(ids,(a,b)->projectStorage(a).getString("project","").compareToIgnoreCase(projectStorage(b).getString("project","")));
  if(!projectStorage(LEGACY_PROJECT).getString("project","").trim().isEmpty()||projectStorage(LEGACY_PROJECT).contains("routeSketch")||projectStorage(LEGACY_PROJECT).contains("segments")||projectStorage(LEGACY_PROJECT).contains("objects")||!projectStorage(LEGACY_PROJECT).getString("length","").trim().isEmpty())ids.add(LEGACY_PROJECT);
  if(ids.remove(activeProject))ids.add(0,activeProject);
  if(ids.isEmpty())content.addView(sheetText("Noch keine Projekte vorhanden.",14,false));
  for(String id:ids){
   String name=projectStorage(id).getString("project","").trim();
   overviewAction(content,name.isEmpty()?"Bestandsprojekt":name,"layers",()->{
    if(id.equals(activeProject)){enterProject();return;}
    if(projectRegistry().edit().putString("active",id).commit())restartForProject();else notice("Projektwechsel fehlgeschlagen.");
   });
  }
  overviewAction(start,"Cloud","cloud",()->openCloud());
  for(LinearLayout group:new LinearLayout[]{content,start})for(int k=0;k<group.getChildCount();k++){View child=group.getChildAt(k);if(child instanceof Button){Button b=(Button)child;b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setTextSize(14);b.setPadding(dp(12),dp(6),dp(12),dp(6));android.view.ViewGroup.LayoutParams lp=b.getLayoutParams();lp.height=dp(52);b.setLayoutParams(lp);}}
 }
 void enterProject(){projectChosen=true;startLiveGps();setContentView(shell);fitSystemPanels(shell);if(length.length()>0&&width.length()>0&&depth.length()>0)calculate();showPage(planPage);}
 void showProjects(){
  java.util.ArrayList<String> ids=new java.util.ArrayList<>(projectRegistry().getStringSet("ids",java.util.Collections.emptySet()));
  java.util.Collections.sort(ids);ids.remove(LEGACY_PROJECT);
  if(!projectStorage(LEGACY_PROJECT).getString("project","").trim().isEmpty() || projectStorage(LEGACY_PROJECT).contains("routeSketch") || !projectStorage(LEGACY_PROJECT).getString("length","").trim().isEmpty() || projectStorage(LEGACY_PROJECT).contains("objects") || projectStorage(LEGACY_PROJECT).contains("segments"))ids.add(0,LEGACY_PROJECT);
  if(ids.isEmpty()){new android.app.AlertDialog.Builder(this).setTitle("Projekte").setMessage("Noch keine Projekte vorhanden.").setPositiveButton("Neues Projekt erstellen",(d,w)->newProjectDialog()).setNegativeButton("Zurück",null).show();return;}
  String[] labels=new String[ids.size()];
  for(int j=0;j<ids.size();j++){String id=ids.get(j);String name=projectStorage(id).getString("project","");labels[j]=(id.equals(activeProject)?"● ":"")+ (name.trim().isEmpty()?"Bestandsprojekt":name);}
  new android.app.AlertDialog.Builder(this).setTitle("Projekte").setItems(labels,(d,w)->{String id=ids.get(w);if(!id.equals(activeProject)){if(!projectChosen){if(projectRegistry().edit().putString("active",id).commit())restartForProject();else notice("Projektwechsel fehlgeschlagen.");}else confirmProjectSwitch(id);}else if(!projectChosen)enterProject();}).setNeutralButton("Neues Projekt",(d,w)->newProjectDialog()).setNegativeButton("Schließen",null).show();
 }
 void newProjectDialog(){
  EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Projektname");
  android.app.Dialog dialog=new android.app.Dialog(this);
  LinearLayout root=page();root.setPadding(dp(20),dp(16),dp(20),dp(16));
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);
  root.addView(sheetText("Neues Projekt",24,true));
  root.addView(sheetText("Projektdaten",14,false));
  name.setTextColor(Color.WHITE);name.setHintTextColor(0xff9aabaa);name.setPadding(dp(12),dp(10),dp(12),dp(10));name.setBackground(editorShape(0xff202827,10));root.addView(name,new LinearLayout.LayoutParams(-1,-2));
  EditText site=projectFormField(root,"Baustelle / Ort");
  EditText client=projectFormField(root,"Auftraggeber");
  root.addView(sheetText("Projekttyp",14,true));
  android.widget.RadioGroup types=new android.widget.RadioGroup(this);
  String[] typeNames={"Tiefbau","Leitungsbau","Rohrleitungsbau","Sonstiges"};
  for(int k=0;k<typeNames.length;k++){android.widget.RadioButton option=new android.widget.RadioButton(this);option.setId(k+1);option.setText(typeNames[k]);option.setTextColor(Color.WHITE);option.setButtonTintList(android.content.res.ColorStateList.valueOf(TEAL));types.addView(option);}
  types.check(1);root.addView(types);
  root.addView(sheetText("Aktuelle Eingaben werden automatisch als Entwurf gespeichert.",13,false));
  overviewAction(root,"Abbrechen","back",()->dialog.dismiss());
  Button create=actionButtonSingle(root,"Projekt erstellen",()->{});
  styleEditorButton(create,true);create.setMinHeight(dp(48));
  dialog.setContentView(scroll);
  create.setOnClickListener(b->{
   String title=name.getText().toString().trim();if(title.isEmpty()){name.setError("Projektname erforderlich");return;}
   String id=java.util.UUID.randomUUID().toString();
   SharedPreferences defaults=getSharedPreferences("base_profile_defaults",MODE_PRIVATE);
   if(!projectStorage(id).edit().putString("width",String.format(java.util.Locale.GERMANY,"%.2f",defaults.getFloat("width",0.4f))).putString("depth",String.format(java.util.Locale.GERMANY,"%.2f",defaults.getFloat("depth",1.0f))).putString("project",title).putString("projectSite",site.getText().toString().trim()).putString("projectClient",client.getText().toString().trim()).putString("projectType",typeNames[Math.max(0,types.getCheckedRadioButtonId()-1)]).putInt("dictationLanguage",getPreferences(0).getInt("dictationLanguage",1)).commit()){notice("Projekt konnte nicht erstellt werden.");return;}
   java.util.Set<String> ids=new java.util.HashSet<>(projectRegistry().getStringSet("ids",java.util.Collections.emptySet()));ids.add(id);
   if(!projectRegistry().edit().putStringSet("ids",ids).putString("active",id).commit()){notice("Projekt konnte nicht aktiviert werden.");return;}
   dialog.dismiss();restartForProject();
  });dialog.show();
  if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.BLACK));dialog.getWindow().setLayout(-1,-2);dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
 }
 EditText projectFormField(LinearLayout root,String label){
  TextView caption=sheetText(label,14,true);caption.setPadding(0,dp(12),0,dp(4));root.addView(caption);
  EditText field=new EditText(this);field.setSingleLine(true);field.setHint(label);field.setTextColor(Color.WHITE);field.setHintTextColor(0xff9aabaa);field.setPadding(dp(12),dp(10),dp(12),dp(10));field.setBackground(editorShape(0xff202827,10));root.addView(field,new LinearLayout.LayoutParams(-1,-2));return field;
 }

 void confirmProjectSwitch(String id){
  new android.app.AlertDialog.Builder(this).setTitle("Projekt wechseln").setMessage("Aktuelle Eingaben werden automatisch als Entwurf gespeichert.").setNegativeButton("Abbrechen",null).setPositiveButton("Wechseln",(d,w)->{
   if(projectRegistry().edit().putString("active",id).commit())restartForProject();else notice("Projektwechsel fehlgeschlagen.");
  }).show();
 }
 void restartForProject(){
  saveDraft();
  selectedPoint=null;pendingPoint=null;pendingPhoto=null;speechVersion++;
  projectChosen=true;recreate();
 }
 static final long AUTOSAVE_INTERVAL_MS=300000L;
 final java.util.LinkedHashMap<String,EditText> autosaveFields=new java.util.LinkedHashMap<>();
 final android.os.Handler autosaveHandler=new android.os.Handler(android.os.Looper.getMainLooper());
 final Runnable autosaveTick=new Runnable(){public void run(){if(saveDraft())scheduleAutomaticBackup();autosaveHandler.postDelayed(this,AUTOSAVE_INTERVAL_MS);}};
 static final java.util.Set<String> PROFILE_KEYS=new java.util.HashSet<>(java.util.Arrays.asList("length","width","depth","base","pipe","asphalt","left","right","stoneLength","stoneWidth","stoneDepth","stoneColor","pattern","bedding","underlying"));
 boolean validProfileForAutosave(){
  try{
   double len=number(length),wid=number(width),dep=number(depth);
   if(!Double.isFinite(len*wid*dep)||len<=0||wid<=0||dep<=0)return false;
   String material=String.valueOf(surface.getSelectedItem());boolean stoneSelected=isStoneSurface(material);
   if(stoneSelected){optional(stoneLength);optional(stoneWidth);}
   double baseValue=optional(baseInput),pipeValue=optional(pipeInput);
   optional(leftInput);optional(rightInput);
   double top="Asphalt".equals(material)?optional(asphaltInput):stoneSelected?optional(stoneDepth)/100:Double.NaN;
   return (!Double.isFinite(pipeValue)||(pipeValue>0&&pipeValue/1000<=wid&&pipeValue/1000<=dep))
    &&(!Double.isFinite(top)||top<=dep)&&(!Double.isFinite(baseValue)||baseValue+(Double.isFinite(top)?top:0)<=dep);
  }catch(Exception e){return false;}
 }
 boolean saveDraft(){
  if(!projectChosen||project==null||surface==null||handling==null)return false;
  SharedPreferences prefs=getPreferences(0);SharedPreferences.Editor ed=prefs.edit();
  boolean valid=validProfileForAutosave();
  boolean confirmed=false;
  try{double a=Double.parseDouble(prefs.getString("length","").replace(',','.')),b=Double.parseDouble(prefs.getString("width","").replace(',','.')),c=Double.parseDouble(prefs.getString("depth","").replace(',','.'));confirmed=Double.isFinite(a*b*c)&&a>0&&b>0&&c>0;}catch(Exception ignored){}
  for(java.util.Map.Entry<String,EditText> entry:autosaveFields.entrySet()){
   if(!valid&&confirmed&&PROFILE_KEYS.contains(entry.getKey()))continue;
   ed.putString(entry.getKey(),entry.getValue().getText().toString());
  }
  if(valid||!confirmed)ed.putInt("surface",surface.getSelectedItemPosition()).putInt("handling",handling.getSelectedItemPosition());
  ed.putLong("lastAutosave",System.currentTimeMillis());
  boolean ok=ed.commit();if(!ok)notice("Automatische Speicherung fehlgeschlagen.");
  return ok;
 }
 EditText project,length,width,depth; TextView result; Sketch sketch;
 EditText objectStation,objectNote; Spinner objectType,objectSide; TextView objectList; org.json.JSONArray objects=new org.json.JSONArray(); EditText voiceText; Spinner voiceLanguage;
 double l,w,d,asphalt,left,right; EditText asphaltInput,leftInput,rightInput; Profile profile; Spinner surface; EditText stoneLength,stoneWidth,stoneDepth,stoneColor,pattern,bedding,underlying; Spinner handling; TextView legend; EditText stationFrom,stationTo; TextView segmentsView; org.json.JSONArray segments=new org.json.JSONArray(); EditText baseInput,pipeInput; double base=Double.NaN,pipe=Double.NaN; String covering="Unbekannt";
 EditText field(LinearLayout root,String label,String key,boolean numeric){
 TextView t=new TextView(this);t.setText(label);root.addView(t);
 EditText e=new EditText(this);e.setSingleLine(true);
 if(numeric)e.setInputType(8194);
 android.graphics.drawable.GradientDrawable fieldShape=editorShape(0xff1b1b1b,24);fieldShape.setStroke(dp(1),0xff626262);e.setBackground(fieldShape);e.setTextColor(Color.WHITE);e.setHintTextColor(0xff999999);e.setPadding(dp(16),dp(10),dp(16),dp(10));e.setMinHeight(dp(48));
 e.setText(getPreferences(0).getString(key,""));autosaveFields.put(key,e);root.addView(e);return e;
 }
 double number(EditText e){return Double.parseDouble(e.getText().toString().replace(',','.'));}
 EditText sollLength,sollWidth,sollDepth,sollAsphalt,sollSource; TextView sollComparison;
 LinearLayout documentsPage; TextView documentsList; org.json.JSONArray documents=new org.json.JSONArray();
 LinearLayout shell,body,planPage,measurePage,voicePage,profilePage,segmentPage,objectPage,quantityPage; TextView projectHeader; TextView detailQuantities; Button undoObjectsButton,redoObjectsButton; java.util.ArrayDeque<String> undoObjects=new java.util.ArrayDeque<>(),redoObjects=new java.util.ArrayDeque<>();
 TextView surfaceLayers,routeSummary;
 double topThickness=Double.NaN;
 LinearLayout routePreviewBox;
 String routeData(){return getPreferences(0).getString("routeSketch","[]");}

 RouteSketch livePreview,activeRouteEditor;
 android.location.Location liveFix;
 void updateLiveFix(android.location.Location fix){liveFix=fix;if(livePreview!=null)livePreview.setLiveFix(fix);if(activeRouteEditor!=null)activeRouteEditor.setLiveFix(fix);}
 void updateRecordedTrack(org.json.JSONArray rows){if(livePreview!=null)livePreview.setIndependentGpsTrack(rows);if(activeRouteEditor!=null)activeRouteEditor.setIndependentGpsTrack(rows);}
 boolean recordingGpsTrack=false;
 String gpsRecordingProject=null;
 org.json.JSONArray gpsTrack(){try{return new org.json.JSONArray(getPreferences(0).getString("independentGpsTrack","[]"));}catch(org.json.JSONException e){return new org.json.JSONArray();}}
 boolean gpsTrackStorageValid(){try{new org.json.JSONArray(getPreferences(0).getString("independentGpsTrack","[]"));return true;}catch(org.json.JSONException e){return false;}}
 void appendGpsTrack(android.location.Location fix){
  if(!recordingGpsTrack||!activeProject.equals(gpsRecordingProject)||fix==null||!fix.hasAccuracy()||!Float.isFinite(fix.getAccuracy())||fix.getAccuracy()<=0||fix.getAccuracy()>15)return;
  if(!Double.isFinite(fix.getLatitude())||!Double.isFinite(fix.getLongitude())||Math.abs(fix.getLatitude())>90||Math.abs(fix.getLongitude())>180)return;
  long age=android.os.SystemClock.elapsedRealtimeNanos()-fix.getElapsedRealtimeNanos();if(age<0||age>15000000000L)return;
  if(!gpsTrackStorageValid()){recordingGpsTrack=false;notice("GPS-Spur beschädigt. Sicherung prüfen; Daten wurden nicht überschrieben.");return;}
  org.json.JSONArray rows=gpsTrack();
  if(rows.length()>=10000){recordingGpsTrack=false;notice("GPS-Aufzeichnung: Punktlimit erreicht.");return;}
  if(rows.length()>0){
   org.json.JSONObject last=rows.optJSONObject(rows.length()-1);
   if(last!=null){float[] distance=new float[1];android.location.Location.distanceBetween(last.optDouble("lat"),last.optDouble("lon"),fix.getLatitude(),fix.getLongitude(),distance);
    if(fix.getTime()<=last.optLong("time")||distance[0]<Math.max(2,fix.getAccuracy()))return;}
  }
  try{org.json.JSONObject row=new org.json.JSONObject();row.put("lat",fix.getLatitude());row.put("lon",fix.getLongitude());row.put("accuracy",fix.getAccuracy());row.put("time",fix.getTime());rows.put(row);
   if(!getPreferences(0).edit().putString("independentGpsTrack",rows.toString()).commit()){recordingGpsTrack=false;notice("GPS-Aufzeichnung konnte nicht gespeichert werden.");}else updateRecordedTrack(rows);
  }catch(org.json.JSONException e){recordingGpsTrack=false;notice("Ungültiger GPS-Punkt.");}
 }
 void showGpsTrackComparison(){
  final String project=activeProject;
  final RouteSketch snapshot=new RouteSketch(this,routeData());
  snapshot.setIndependentGpsTrack(gpsTrack());
  final android.app.ProgressDialog progress=new android.app.ProgressDialog(this);
  progress.setMessage("Trassen werden verglichen …");progress.setCancelable(false);progress.show();
  new Thread(()->{
   String result;
   try{result=snapshot.gpsTrackComparison();}catch(RuntimeException e){result="Trassenvergleich fehlgeschlagen.";}
   final String message=result;
   runOnUiThread(()->{
    if(isDestroyed()||isFinishing())return;
    progress.dismiss();if(!project.equals(activeProject))return;
    new android.app.AlertDialog.Builder(this).setTitle("Trassenvergleich").setMessage(message).setNeutralButton("Abweichungen anzeigen",(d,w)->applyDrawingLayers(drawingLayers()|RouteSketch.LAYER_GPS_TRACK|RouteSketch.LAYER_ROUTE|RouteSketch.LAYER_GPS_DEVIATIONS)).setPositiveButton("Schließen",null).show();
   });
  }).start();
 }
 String pendingGpsExport=null;
 void requestGpsImport(){
  android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
  intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);intent.setType("*/*");startActivityForResult(intent,453);
 }
 void readGpsImport(android.net.Uri uri){
  final String project=activeProject;
  final String original=getPreferences(0).getString("importedGpsReference","");
  new Thread(()->{
   try(java.io.InputStream in=getContentResolver().openInputStream(uri)){
    if(in==null)throw new java.io.IOException("Datei fehlt");
    org.xmlpull.v1.XmlPullParser parser=android.util.Xml.newPullParser();parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_DOCDECL,false);parser.setInput(in,null);
    org.json.JSONArray rows=new org.json.JSONArray();int segments=0;boolean root=false;
    for(int event=parser.getEventType();event!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT;event=parser.next()){
     if(event==org.xmlpull.v1.XmlPullParser.DOCDECL)throw new java.io.IOException("DOCTYPE nicht erlaubt");
     if(event!=org.xmlpull.v1.XmlPullParser.START_TAG)continue;
     String tag=parser.getName();if(!root){if(!"gpx".equals(tag))throw new java.io.IOException("Keine GPX-Datei");root=true;}
     if("trkseg".equals(tag)&&++segments>1)throw new java.io.IOException("Mehrere GPX-Segmente nicht unterstützt");
     if(!"trkpt".equals(tag))continue;
     if(rows.length()>=10000)throw new java.io.IOException("GPS-Punktlimit überschritten");
     double lat=Double.parseDouble(parser.getAttributeValue(null,"lat")),lon=Double.parseDouble(parser.getAttributeValue(null,"lon"));
     if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new java.io.IOException("Ungültige Koordinaten");
     org.json.JSONObject row=new org.json.JSONObject();row.put("lat",lat);row.put("lon",lon);rows.put(row);
    }
    if(rows.length()<2)throw new java.io.IOException("Mindestens zwei Punkte erforderlich");
    runOnUiThread(()->{if(isFinishing()||isDestroyed()||!project.equals(activeProject))return;
     if(!original.equals(getPreferences(0).getString("importedGpsReference",""))){notice("Referenz geändert. Bitte erneut importieren.");return;}
     new android.app.AlertDialog.Builder(this).setTitle("GPX-Vorschau").setMessage(rows.length()+" Punkte. Genauigkeit unbekannt. "+(importedGpsReference().length()>0?"Vorhandene GPX-Referenz ersetzen?":"Import als separate Referenz?")).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",(d,w)->{
      if(!project.equals(activeProject)||!original.equals(getPreferences(0).getString("importedGpsReference",""))){notice("Referenz oder Projekt geändert. Bitte erneut importieren.");return;}
      if(getPreferences(0).edit().putString("importedGpsReference",rows.toString()).commit()){if(livePreview!=null)livePreview.setImportedGpsReference(rows);if(activeRouteEditor!=null)activeRouteEditor.setImportedGpsReference(rows);applyDrawingLayers(drawingLayers()|RouteSketch.LAYER_GPS_REFERENCE);notice("GPX-Referenz gespeichert.");}else notice("GPX konnte nicht gespeichert werden.");
     }).show();
    });
   }catch(Exception e){runOnUiThread(()->notice("GPX-Import fehlgeschlagen: "+e.getMessage()));}
  },"gps-import").start();
 }
 void deleteGpsReference(){
  final String project=activeProject;
  final String original=getPreferences(0).getString("importedGpsReference","");
  if(original.isEmpty()){notice("Keine GPX-Referenz vorhanden.");return;}
  new android.app.AlertDialog.Builder(this).setTitle("GPX-Referenz löschen").setMessage("Nur die importierte Referenz wird gelöscht. Manuelle Trasse und GPS-Aufzeichnung bleiben erhalten.").setNegativeButton("Abbrechen",null).setPositiveButton("Löschen",(d,w)->{
   if(!project.equals(activeProject)||!original.equals(getPreferences(0).getString("importedGpsReference",""))){notice("Referenz geändert. Bitte erneut öffnen.");return;}
   if(getPreferences(0).edit().remove("importedGpsReference").commit()){if(livePreview!=null)livePreview.setImportedGpsReference(new org.json.JSONArray());if(activeRouteEditor!=null)activeRouteEditor.setImportedGpsReference(new org.json.JSONArray());notice("GPX-Referenz gelöscht.");}else notice("GPX-Referenz konnte nicht gelöscht werden.");
  }).show();
 }
 void requestGpsExport(){
  new android.app.AlertDialog.Builder(this).setTitle("GPS-Dateien").setItems(new String[]{"GPX exportieren – GIS / Karte","CSV exportieren – Koordinatenliste","GPX importieren / ersetzen","GPX-Referenz löschen"},(d,w)->{
   if(w==3){deleteGpsReference();return;}
   if(w==2){requestGpsImport();return;}
   if(!gpsTrackStorageValid()||gpsTrack().length()==0){notice("Keine gültige GPS-Spur vorhanden.");return;}
   pendingGpsExport=gpsTrack().toString();
   android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
   intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);intent.setType(w==0?"application/gpx+xml":"text/csv");intent.putExtra(android.content.Intent.EXTRA_TITLE,w==0?"GPS-Trasse.gpx":"GPS-Trasse.csv");
   startActivityForResult(intent,w==0?451:452);
  }).show();
 }
 void writeGpsExport(android.net.Uri uri,boolean gpx,String snapshot){
  new Thread(()->{
   try(java.io.OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
    if(out==null)throw new java.io.IOException("Datei fehlt");
    org.json.JSONArray rows=new org.json.JSONArray(snapshot);
    java.io.Writer writer=new java.io.OutputStreamWriter(out,java.nio.charset.StandardCharsets.UTF_8);
    java.text.SimpleDateFormat date=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",java.util.Locale.US);date.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
    if(gpx)writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"Feldaufmass Pro\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>GPS-Trasse</name><trkseg>");
    else writer.write("latitude;longitude;accuracy_m;time_utc\n");
    for(int i=0;i<rows.length();i++){org.json.JSONObject row=rows.getJSONObject(i);double lat=row.getDouble("lat"),lon=row.getDouble("lon"),acc=row.getDouble("accuracy");String time=date.format(new java.util.Date(row.getLong("time")));
     if(gpx)writer.write(String.format(java.util.Locale.US,"<trkpt lat=\"%.8f\" lon=\"%.8f\"><time>%s</time></trkpt>",lat,lon,time));
     else writer.write(String.format(java.util.Locale.US,"%.8f;%.8f;%.2f;%s%n",lat,lon,acc,time));
    }
    if(gpx)writer.write("</trkseg></trk></gpx>");writer.flush();
    runOnUiThread(()->notice("GPS-Spur exportiert."));
   }catch(Exception e){runOnUiThread(()->notice("GPS-Export fehlgeschlagen."));}
  },"gps-export").start();
 }
 void showGpsTrackControls(){
  org.json.JSONArray rows=gpsTrack();
  new android.app.AlertDialog.Builder(this).setTitle("GPS-Aufzeichnung").setMessage((recordingGpsTrack?"Aufzeichnung aktiv":"Aufzeichnung gestoppt")+" · "+rows.length()+" Punkte\nUnabhängig von der manuellen Trasse.\nBeim Verlassen der App wird die Aufzeichnung gestoppt.")
   .setPositiveButton(recordingGpsTrack?"Stoppen":rows.length()==0?"Starten":"Fortsetzen",(d,w)->{
    if(recordingGpsTrack){recordingGpsTrack=false;gpsRecordingProject=null;notice("GPS-Aufzeichnung gestoppt.");return;}
    if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION},704);return;}
    if(!gpsTrackStorageValid()){notice("GPS-Spur beschädigt. Bitte Sicherung prüfen.");return;}
    android.location.LocationManager manager=(android.location.LocationManager)getSystemService(LOCATION_SERVICE);
    if(manager==null||!manager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)){notice("Bitte GPS in den Android-Einstellungen aktivieren.");return;}
    startLiveGps();if(liveManager==null){notice("GPS nicht verfügbar.");return;}gpsRecordingProject=activeProject;recordingGpsTrack=true;applyDrawingLayers(drawingLayers()|RouteSketch.LAYER_GPS_TRACK);notice("GPS-Aufzeichnung gestartet.");
   }).setNeutralButton("Spur löschen",(d,w)->new android.app.AlertDialog.Builder(this).setMessage("Gespeicherte GPS-Spur wirklich löschen?").setNegativeButton("Abbrechen",null).setPositiveButton("Löschen",(dd,ww)->{recordingGpsTrack=false;if(!getPreferences(0).edit().remove("independentGpsTrack").commit())notice("GPS-Spur konnte nicht gelöscht werden.");else updateRecordedTrack(gpsTrack());}).show()).setNegativeButton("Dateien",(d,w)->requestGpsExport()).show();
 }

 android.location.LocationManager liveManager;
 final android.location.LocationListener liveListener=new android.location.LocationListener(){
  public void onLocationChanged(android.location.Location fix){updateLiveFix(fix);appendGpsTrack(fix);}
  public void onProviderDisabled(String provider){updateLiveFix(null);if(recordingGpsTrack){recordingGpsTrack=false;gpsRecordingProject=null;notice("GPS deaktiviert. Aufzeichnung gestoppt; gespeicherte Punkte bleiben erhalten.");}}
  public void onProviderEnabled(String provider){}
  public void onStatusChanged(String provider,int status,android.os.Bundle extras){}
 };
 void startLiveGps(){
  if(liveManager!=null||checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
  try{android.location.LocationManager m=(android.location.LocationManager)getSystemService(LOCATION_SERVICE);
   m.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER,1000L,0f,liveListener);liveManager=m;
  }catch(RuntimeException ignored){}
 }
 @Override protected void onResume(){super.onResume();if(projectChosen)startLiveGps();autosaveHandler.removeCallbacks(autosaveTick);autosaveHandler.postDelayed(autosaveTick,AUTOSAVE_INTERVAL_MS);}
 @Override protected void onPause(){
  recordingGpsTrack=false;gpsRecordingProject=null;autosaveHandler.removeCallbacks(autosaveTick);saveDraft();
  if(liveManager!=null){liveManager.removeUpdates(liveListener);liveManager=null;}
  updateLiveFix(null);super.onPause();
 }
 org.json.JSONArray importedGpsReference(){try{return new org.json.JSONArray(getPreferences(0).getString("importedGpsReference","[]"));}catch(org.json.JSONException e){return new org.json.JSONArray();}}
 int drawingLayers(){
  android.content.SharedPreferences prefs=getSharedPreferences("drawing_view",MODE_PRIVATE);
  int mask=prefs.getInt(activeProject,RouteSketch.ALL_LAYERS)&RouteSketch.ALL_LAYERS;
  if(!prefs.getBoolean("surfaceDimensionsMigrated:"+activeProject,false)){
   if((mask&RouteSketch.LAYER_DIMENSIONS)!=0)mask|=RouteSketch.LAYER_SURFACE_DIMENSIONS;
   else mask&=~RouteSketch.LAYER_SURFACE_DIMENSIONS;
   prefs.edit().putInt(activeProject,mask).putBoolean("surfaceDimensionsMigrated:"+activeProject,true).apply();
  }
  if(!prefs.getBoolean("gpsTrackLayerMigrated:"+activeProject,false)){
   mask|=RouteSketch.LAYER_GPS_TRACK;
   prefs.edit().putInt(activeProject,mask).putBoolean("gpsTrackLayerMigrated:"+activeProject,true).apply();
  }
  if(!prefs.getBoolean("gpsDeviationLayerMigrated:"+activeProject,false)){
   mask&=~RouteSketch.LAYER_GPS_DEVIATIONS;
   prefs.edit().putInt(activeProject,mask).putBoolean("gpsDeviationLayerMigrated:"+activeProject,true).apply();
  }
  if(!prefs.getBoolean("gpsReferenceLayerMigrated:"+activeProject,false)){mask|=RouteSketch.LAYER_GPS_REFERENCE;prefs.edit().putInt(activeProject,mask).putBoolean("gpsReferenceLayerMigrated:"+activeProject,true).apply();}
  if(!prefs.getBoolean("utilitySheetsMigrated:"+activeProject,false)){mask|=RouteSketch.LAYER_SCHACHTSCHEINE;prefs.edit().putInt(activeProject,mask).putBoolean("utilitySheetsMigrated:"+activeProject,true).apply();}
  return mask;
 }
 void showDrawingLayers(){showDrawingLayers(null);}
 void showDrawingLayers(RouteSketch editorRoute){
  final String project=activeProject;
  String[] labels={"Leitungsverlauf / Punkte","Bemaßung","Punktnamen","GPS / Standort","Fotodokumentation","Objekte","Oberflächen","Oberflächenbemaßung","GPS-Aufzeichnung","GPS-Abweichungen","GPX-Referenz","Schachtscheine / Fremdmedien"};
  int[] masks={RouteSketch.LAYER_ROUTE,RouteSketch.LAYER_DIMENSIONS,RouteSketch.LAYER_NAMES,RouteSketch.LAYER_GPS,RouteSketch.LAYER_PHOTOS,RouteSketch.LAYER_OBJECTS,RouteSketch.LAYER_SURFACES,RouteSketch.LAYER_SURFACE_DIMENSIONS,RouteSketch.LAYER_GPS_TRACK,RouteSketch.LAYER_GPS_DEVIATIONS,RouteSketch.LAYER_GPS_REFERENCE,RouteSketch.LAYER_SCHACHTSCHEINE};
  boolean[] checked=new boolean[labels.length];int current=drawingLayers();
  java.util.function.IntConsumer apply=mask->{if(!project.equals(activeProject)){notice("Projekt geändert. Ebenen erneut öffnen.");return;}if(applyDrawingLayers(mask)&&editorRoute!=null)editorRoute.setVisibleLayers(mask);};
  for(int j=0;j<labels.length;j++)checked[j]=(current&masks[j])!=0;
  LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(16),dp(8),dp(16),dp(8));panel.setBackgroundColor(Color.BLACK);
  final android.app.AlertDialog[] dialog={null};
  overviewAction(panel,"Schachtscheine","map",()->{
   if(!project.equals(activeProject)){notice("Projekt geändert.");return;}
   int mask=0;for(int j=0;j<checked.length;j++)if(checked[j])mask|=masks[j];apply.accept(mask);
   dialog[0].dismiss();showUtilitySheets();
  });
  ScrollView scroll=new ScrollView(this);LinearLayout choices=new LinearLayout(this);choices.setOrientation(1);scroll.addView(choices);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  for(int j=0;j<labels.length;j++){final int index=j;CheckBox check=new CheckBox(this);check.setText(labels[j]);check.setTextColor(Color.WHITE);check.setChecked(checked[j]);check.setOnCheckedChangeListener((b,on)->checked[index]=on);choices.addView(check,new LinearLayout.LayoutParams(-1,dp(48)));}
  dialog[0]=new android.app.AlertDialog.Builder(this).setTitle("Zeichnungsebenen").setView(panel)
   .setNegativeButton("Abbrechen",null).setNeutralButton("Alle anzeigen",(d,w)->apply.accept(RouteSketch.ALL_LAYERS))
   .setPositiveButton("Übernehmen",(d,w)->{int mask=0;for(int j=0;j<checked.length;j++)if(checked[j])mask|=masks[j];apply.accept(mask);}).create();
  dialog[0].show();scroll.getLayoutParams().height=Math.min(dp(432),Math.max(dp(120),getResources().getDisplayMetrics().heightPixels-dp(250)));((LinearLayout.LayoutParams)scroll.getLayoutParams()).weight=0;scroll.requestLayout();
 }
 boolean applyDrawingLayers(int mask){
  if(!getSharedPreferences("drawing_view",MODE_PRIVATE).edit().putInt(activeProject,mask).commit()){notice("Ebenen konnten nicht gespeichert werden.");return false;}
  if(livePreview!=null)livePreview.setVisibleLayers(mask);
  if(activeRouteEditor!=null)activeRouteEditor.setVisibleLayers(mask);
  return true;
 }
 void refreshStationLayers(){
  if(livePreview==null)return;
  org.json.JSONArray obj=new org.json.JSONArray(),sections=new org.json.JSONArray();
  try{obj=new org.json.JSONArray(getPreferences(0).getString("objects","[]"));}catch(Exception ignored){}
  try{sections=new org.json.JSONArray(getPreferences(0).getString("segments","[]"));}catch(Exception ignored){}
  double savedWidth=Double.NaN;
  try{savedWidth=Double.parseDouble(getPreferences(0).getString("width","").replace(',','.'));}catch(Exception ignored){}
  livePreview.setTrenchWidth(savedWidth);
  livePreview.setStationLayers(obj,sections);
  if(livePreview.vertices.size()>=2&&(obj.length()>0||sections.length()>0)){
   double total=livePreview.measuredLength();
   String label=(livePreview.vertices.size()-1)+" Abschnitte · "+(Double.isFinite(total)?"Bemaßte Länge: "+reportMeasure(total):"Bemaßung unvollständig");
   int omitted=0;
   for(int j=0;j<obj.length();j++){org.json.JSONObject o=obj.optJSONObject(j);if(o==null||livePreview.stationPoint(o.optDouble("station"))==null)omitted++;}
   for(int j=0;j<sections.length();j++){org.json.JSONObject o=sections.optJSONObject(j);if(o==null||o.optDouble("from")>=o.optDouble("to")||livePreview.stationPoint(o.optDouble("from"))==null||livePreview.stationPoint(o.optDouble("to"))==null)omitted++;}
   label+=routeLengthWarning(total);
    if(omitted>0)label+=" · "+omitted+" Stationseinträge ohne Planposition";
   if(obj.length()>0)label+="\n* Ohne Abstand: schematisch angrenzend. Skizze nicht maßstabsgetreu." ;
   routeSummary.setText(label);
  }else if(livePreview.vertices.size()>=2){
   double total=livePreview.measuredLength();routeSummary.setText((livePreview.vertices.size()-1)+" Abschnitte · "+(Double.isFinite(total)?"Bemaßte Länge: "+reportMeasure(total):"Bemaßung unvollständig")+routeLengthWarning(total));
  }
 }
 String routeLengthWarning(double measured){
  if(!Double.isFinite(measured)||measured<=0)return "";
  try{double confirmed=Double.parseDouble(getPreferences(0).getString("length","").replace(',','.'));if(Double.isFinite(confirmed)&&confirmed>0&&Math.abs(confirmed-measured)>0.001)return "\nAbweichung: Berechnungslänge "+reportMeasure(confirmed)+" · Skizzenlänge "+reportMeasure(measured)+". Unter Maße prüfen / übernehmen.";}catch(NumberFormatException ignored){}
  return "";
 }
 void refreshRoute(){
  routePreviewBox.removeAllViews();
  RouteSketch preview=new RouteSketch(this,routeData());preview.darkPreview=true;preview.bindDimensionProject(activeProject);
  livePreview=preview;preview.setVisibleLayers(drawingLayers());loadUtilitySheets(preview);preview.setLiveFix(liveFix);preview.setIndependentGpsTrack(gpsTrack());preview.setImportedGpsReference(importedGpsReference());
  preview.pointSelected=i->{selectedPoint=preview.vertices.get(i).id;refreshRoute();pointMenu();};
  preview.selected=i->measureSegment(preview,i);
  preview.changed=()->{saveRoute(preview);refreshRoute();};
  preview.highlight=findPoint(preview,selectedPoint);
  if(!preview.vertices.isEmpty() && !routeData().equals(preview.encode()))getPreferences(0).edit().putString("routeSketch",preview.encode()).remove("routeHistoryHead").remove("routeUndo").remove("routeRedo").commit();
  routePreviewBox.addView(preview,new LinearLayout.LayoutParams(-1,dp(300)));
  refreshMainQuantities();
  double total=preview.measuredLength();
  if(preview.vertices.size()<2){routeSummary.setText(preview.singleObjects.isEmpty()?"Trasse mit dem Finger zeichnen":preview.singleObjects.size()==1?"Einzelobjekt · "+preview.singleObjects.get(0).type:"Einzelobjekte: "+preview.singleObjects.size());refreshStationLayers();return;}
  routeSummary.setText((preview.vertices.size()-1)+" Abschnitte · "+
    (Double.isFinite(total)?"Bemaßte Länge: "+reportMeasure(total):"Bemaßung unvollständig"));
  refreshStationLayers();
 }

 BleMeasure bleMeasure;
 java.util.function.DoubleConsumer pendingBleConsumer;
 void openBleMeasure(java.util.function.DoubleConsumer accept){
  String[] permissions=android.os.Build.VERSION.SDK_INT>=31?new String[]{android.Manifest.permission.BLUETOOTH_SCAN,android.Manifest.permission.BLUETOOTH_CONNECT}:new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION};
  for(String permission:permissions)if(checkSelfPermission(permission)!=android.content.pm.PackageManager.PERMISSION_GRANTED){pendingBleConsumer=accept;requestPermissions(permissions,703);return;}
  if(bleMeasure!=null)bleMeasure.close();
  bleMeasure=new BleMeasure(this,accept);bleMeasure.show();
 }
 void testBleMeasure(){
  openBleMeasure(value->new android.app.AlertDialog.Builder(this).setTitle("Bluetooth-Messwert").setMessage(reportMeasure(value)+"\nTestmessung, keine Projektdaten geändert. Im Trasseneditor kann ein Messwert direkt übernommen werden.").setPositiveButton("OK",null).show());
 }
 void offerNewRouteMeasurements(RouteSketch route,int first){
  LinearLayout fields=page();
  fields.addView(sheetText("Neue Abschnitte bemaßen. Leere Felder können später ergänzt werden.",14,false));
  java.util.ArrayList<EditText> inputs=new java.util.ArrayList<>();
  java.util.ArrayList<RouteSketch.Vertex> targets=new java.util.ArrayList<>();
  for(int i=first;i<route.vertices.size();i++){
   RouteSketch.Vertex vertex=route.vertices.get(i);if(Double.isFinite(vertex.metres))continue;
   fields.addView(sheetText("Abschnitt "+i+" · Länge (m)",14,true));
   EditText input=new EditText(this);input.setSingleLine(true);input.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,."));input.setHint("z. B. 12,50");fields.addView(input);inputs.add(input);targets.add(vertex);
  }
  if(inputs.isEmpty())return;
  ScrollView scroll=new ScrollView(this);scroll.addView(fields);
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Trasse bemaßen").setView(scroll).setNegativeButton("Später",null).setPositiveButton("Übernehmen",null).create();
  dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
   double[] values=new double[inputs.size()];
   for(int j=0;j<inputs.size();j++){
    EditText input=inputs.get(j);values[j]=Double.NaN;if(input.length()==0)continue;
    try{double value=number(input);if(!Double.isFinite(value)||value<=0)throw new NumberFormatException();values[j]=value;}
    catch(NumberFormatException e){input.setError("Positive Länge in Metern eingeben");return;}
   }
   for(int j=0;j<targets.size();j++)if(Double.isFinite(values[j])&&route.vertices.contains(targets.get(j))&&!Double.isFinite(targets.get(j).metres))targets.get(j).metres=values[j];
   if(route.changed!=null)route.changed.run();route.invalidate();dialog.dismiss();
  }));
  dialog.show();
 }
 void showRouteSegmentMenu(RouteSketch route,int i){
  if(i<1||i>=route.vertices.size())return;
  route.highlight=i;route.invalidate();
  android.widget.PopupMenu menu=new android.widget.PopupMenu(this,route);
  menu.getMenu().add(0,0,0,"Abschnitt "+i).setEnabled(false);
  menu.getMenu().add(0,1,1,"Länge ändern");
  menu.getMenu().add(0,2,2,"Winkel ändern");
  menu.getMenu().add(0,3,3,"Knickpunkt entfernen").setEnabled(i<route.vertices.size()-1);
  menu.setOnMenuItemClickListener(item->{switch(item.getItemId()){
   case 1:measureSegment(route,i);return true;
   case 2:new android.app.AlertDialog.Builder(this).setTitle("Winkel ändern").setItems(new String[]{"0°","15°","30°","45°","60°","75°","90°"},(d,w)->{if(i>=route.vertices.size())return;int previous=route.angle;route.angle=w*15;android.graphics.PointF p=route.vertices.get(i).point;route.movePoint(i,new android.graphics.PointF(p.x,p.y));route.angle=previous;if(route.changed!=null)route.changed.run();route.invalidate();}).show();return true;
   case 3:confirmDeleteRoutePoint(route,i);return true;
   default:return false;
  }});menu.show();
 }
 void measureSegment(RouteSketch route,int i){
  EditText metres=new EditText(this);
  metres.setSingleLine(true);metres.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,."));
  metres.setHint("Länge in m");
  double old=route.vertices.get(i).metres;
  if(Double.isFinite(old))metres.setText(String.valueOf(old).replace('.',','));
  LinearLayout measureForm=page();measureForm.addView(metres);
  Button bluetooth=new Button(this);bluetooth.setAllCaps(false);bluetooth.setText("Bluetooth-Messwert (Test)");measureForm.addView(bluetooth);
  bluetooth.setOnClickListener(v->openBleMeasure(value->metres.setText(java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.',','))));
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
    .setTitle("Abschnitt "+i+" bemaßen").setView(measureForm)
    .setNegativeButton("Abbrechen",null).setPositiveButton("Übernehmen",null).create();
  dialog.setOnShowListener(ignored->dialog.getButton(-1).setOnClickListener(v->{
   try{
    double value=metres.length()==0?Double.NaN:number(metres);
    if(metres.length()>0 && (!Double.isFinite(value)||value<=0))throw new NumberFormatException();
    route.vertices.get(i).metres=value;route.changed.run();route.invalidate();dialog.dismiss();
   }catch(NumberFormatException e){metres.setError("Positive Länge in Metern eingeben");}
  }));
  dialog.show();
 }

 static final class RouteEditHistory{
  final java.util.ArrayDeque<String> undo=new java.util.ArrayDeque<>(),redo=new java.util.ArrayDeque<>();
  String current;
  RouteEditHistory(String initial){current=initial;}
  void record(String next){if(current.equals(next))return;undo.push(current);while(undo.size()>50)undo.removeLast();current=next;redo.clear();}
  String step(boolean forward){java.util.ArrayDeque<String> from=forward?redo:undo,to=forward?undo:redo;if(from.isEmpty())return current;to.push(current);current=from.pop();return current;}
 }
 RouteEditHistory loadRouteHistory(String current){
  RouteEditHistory history=new RouteEditHistory(current);
  try{
   SharedPreferences prefs=getPreferences(0);
   if(!current.equals(prefs.getString("routeHistoryHead","")))return history;
   for(String key:new String[]{"routeUndo","routeRedo"}){
    org.json.JSONArray rows=new org.json.JSONArray(prefs.getString(key,"[]"));
    java.util.ArrayDeque<String> stack=key.equals("routeUndo")?history.undo:history.redo;
    for(int j=0;j<Math.min(50,rows.length());j++)stack.addLast(rows.getString(j));
   }
  }catch(Exception e){history.undo.clear();history.redo.clear();}
  return history;
 }
 boolean persistRouteHistory(RouteEditHistory history){return persistRouteHistory(history,getPreferences(0).edit());}
 boolean persistRouteHistory(RouteEditHistory history,SharedPreferences.Editor ed){
  try{
   String before=routeData();
   ed.putString("routeSketch",history.current)
    .putString("routeHistoryHead",history.current)
    .putString("routeUndo",new org.json.JSONArray(history.undo).toString())
    .putString("routeRedo",new org.json.JSONArray(history.redo).toString());
   if(ed.commit()){
    if(!before.equals(history.current))recordProjectEvent("Trasse geändert · "+new org.json.JSONArray(history.current).length()+" Punkte");
    return true;
   }
  }catch(Exception ignored){}
  notice("Trasse konnte nicht gespeichert werden. Bitte erneut versuchen.");return false;
 }
 static final String UTILITY_SHEETS_KEY="utilityPlanSheets";
 static final int MAX_UTILITY_SHEETS=12;
 final android.util.LruCache<String,android.graphics.Bitmap> utilityBitmaps=new android.util.LruCache<>(MAX_UTILITY_SHEETS);
 org.json.JSONArray utilitySheets()throws org.json.JSONException{return new org.json.JSONArray(getPreferences(0).getString(UTILITY_SHEETS_KEY,"[]"));}
 void loadUtilitySheets(RouteSketch route){
  if(route==null)return;
  java.util.List<RouteSketch.UtilitySheet> list=new java.util.ArrayList<>();
  try{
   org.json.JSONArray rows=utilitySheets();
   for(int j=0;j<rows.length();j++){
    org.json.JSONObject row=rows.getJSONObject(j);
    if(row.optBoolean("visible",true)){
     RouteSketch.UtilitySheet sheet=readUtilitySheet(row);
     if(sheet!=null)list.add(sheet);
    }
   }
  }catch(Exception e){notice("Schachtscheine konnten nicht vollständig geladen werden.");}
  route.setUtilitySheets(list);
 }
 RouteSketch.UtilitySheet readUtilitySheet(org.json.JSONObject row)throws org.json.JSONException{
  String name=row.getString("file");
  android.graphics.Bitmap bitmap=utilityBitmaps.get(name);
  if(bitmap==null){
   bitmap=reportPhoto(name);
   if(bitmap==null)return null;
   utilityBitmaps.put(name,bitmap);
  }
  return new RouteSketch.UtilitySheet(bitmap,(float)row.optDouble("x",0),(float)row.optDouble("y",0),(float)row.optDouble("scale",1),(float)row.optDouble("rotation",0),row.optInt("alpha",140));
 }
 void refreshUtilitySheets(){loadUtilitySheets(livePreview);loadUtilitySheets(activeRouteEditor);}
 boolean saveUtilitySheets(org.json.JSONArray rows){
  if(!getPreferences(0).edit().putString(UTILITY_SHEETS_KEY,rows.toString()).commit()){notice("Schachtscheine konnten nicht gespeichert werden.");return false;}
  refreshUtilitySheets();return true;
 }
 void showUtilitySheets(){
  try{
   final String projectId=activeProject;
   org.json.JSONArray rows=utilitySheets();
   LinearLayout form=page();
   form.addView(sheetText("Bestandspläne / Fremdmedien: manuell ausrichten. Lage und Aktualität beim Netzbetreiber prüfen. Keine Freigabe zum Schachten.",14,false));
   Button add=actionButtonSingle(form,"PDF / Foto hinzufügen",()->{});
   final android.app.AlertDialog[] dialog={null};
   for(int j=0;j<rows.length();j++){
    org.json.JSONObject row=rows.getJSONObject(j);final int index=j;
    Button button=actionButtonSingle(form,row.optString("name","Planblatt")+(row.optBoolean("visible",true)?" · sichtbar":" · ausgeblendet"),()->{if(!projectId.equals(activeProject))return;dialog[0].dismiss();editUtilitySheet(index);});button.setAllCaps(false);
   }
   ScrollView scroll=new ScrollView(this);scroll.addView(form);
   dialog[0]=new android.app.AlertDialog.Builder(this).setTitle("Schachtscheine ("+rows.length()+")").setView(scroll).setPositiveButton("Schließen",null).create();dialog[0].show();
   add.setOnClickListener(v->{if(!projectId.equals(activeProject))return;if(rows.length()>=MAX_UTILITY_SHEETS){notice("Maximal 12 Planblätter je Projekt.");return;}dialog[0].dismiss();android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);i.addCategory(android.content.Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(android.content.Intent.EXTRA_MIME_TYPES,new String[]{"image/*","application/pdf"});startActivityForResult(i,454);});
  }catch(Exception e){notice("Schachtscheine konnten nicht gelesen werden.");}
 }
 void editUtilitySheet(int index){
  try{
   final String projectId=activeProject;org.json.JSONArray rows=utilitySheets();String original=rows.toString();org.json.JSONObject row=rows.getJSONObject(index);
   LinearLayout form=page();RouteSketch preview=new RouteSketch(this,activeRouteEditor==null?routeData():activeRouteEditor.encode());preview.darkPreview=true;preview.setVisibleLayers(RouteSketch.LAYER_ROUTE|RouteSketch.LAYER_SCHACHTSCHEINE);form.addView(preview,new LinearLayout.LayoutParams(-1,dp(240)));
   RouteSketch.UtilitySheet selected=readUtilitySheet(row);
   if(selected==null){notice("Planblatt fehlt. Bitte neu importieren.");return;}
   CheckBox visible=new CheckBox(this);visible.setText("Planblatt anzeigen");visible.setChecked(row.optBoolean("visible",true));form.addView(visible);
   String[] keys={"x","y","scale","rotation","alpha"},labels={"Horizontal","Vertikal","Maßstab","Drehung","Deckkraft"};
   float[] mins={-2,-2,.1f,-180,0},maxs={2,2,5,180,255},values={(float)row.optDouble("x",0),(float)row.optDouble("y",0),(float)row.optDouble("scale",1),(float)row.optDouble("rotation",0),row.optInt("alpha",140)};
   java.util.List<RouteSketch.UtilitySheet> neighbors=new java.util.ArrayList<>();
   int insertionIndex=0;
   for(int j=0;j<rows.length();j++){
    if(j==index){insertionIndex=neighbors.size();continue;}
    org.json.JSONObject other=rows.getJSONObject(j);
    if(other.optBoolean("visible",true)){
     RouteSketch.UtilitySheet sheet=readUtilitySheet(other);
     if(sheet!=null)neighbors.add(sheet);
    }
   }
   final int position=insertionIndex;
   Runnable update=()->{
    java.util.List<RouteSketch.UtilitySheet> layers=new java.util.ArrayList<>(neighbors);
    if(visible.isChecked())layers.add(position,new RouteSketch.UtilitySheet(selected.bitmap,values[0],values[1],values[2],values[3],Math.round(values[4])));
    preview.setUtilitySheets(layers);
   };
   visible.setOnCheckedChangeListener((b,on)->update.run());
   for(int j=0;j<keys.length;j++){final int k=j;TextView label=sheetText(labels[j],14,false);form.addView(label);SeekBar bar=new SeekBar(this);bar.setMax(1000);bar.setProgress(Math.round((values[j]-mins[j])*1000/(maxs[j]-mins[j])));form.addView(bar);bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int p,boolean user){if(user){values[k]=mins[k]+(maxs[k]-mins[k])*p/1000f;update.run();}}});}
   update.run();ScrollView scroll=new ScrollView(this);scroll.addView(form);
   new android.app.AlertDialog.Builder(this).setTitle(row.optString("name","Schachtschein")).setView(scroll).setNegativeButton("Abbrechen",null).setNeutralButton("Entfernen",(d,b)->new android.app.AlertDialog.Builder(this).setMessage("Planblatt aus Projekt entfernen? Trasse bleibt unverändert.").setNegativeButton("Abbrechen",null).setPositiveButton("Entfernen",(x,y)->{try{if(!projectId.equals(activeProject)||!utilitySheets().toString().equals(original))return;rows.remove(index);if(saveUtilitySheets(rows))showUtilitySheets();}catch(Exception e){notice("Entfernen fehlgeschlagen.");}}).show()).setPositiveButton("Speichern",(d,b)->{try{if(!projectId.equals(activeProject)||!utilitySheets().toString().equals(original)){notice("Planblätter geändert. Neu öffnen.");return;}for(int k=0;k<keys.length;k++)row.put(keys[k],(double)values[k]);row.put("alpha",Math.round(values[4])).put("visible",visible.isChecked());if(saveUtilitySheets(rows))showUtilitySheets();}catch(Exception e){notice("Speichern fehlgeschlagen.");}}).show();
  }catch(Exception e){notice("Planblatt konnte nicht geöffnet werden.");}
 }
 void importUtilitySheet(android.net.Uri uri){
  String name="Schachtschein";try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()&&c.getString(0)!=null)name=c.getString(0);}catch(Exception ignored){}
  final String label=name;
  if("application/pdf".equals(getContentResolver().getType(uri))||name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")){
   try(android.os.ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"r");android.graphics.pdf.PdfRenderer pdf=new android.graphics.pdf.PdfRenderer(fd)){
    int count=pdf.getPageCount();if(count<=0){notice("PDF enthält keine Seiten.");return;}if(count==1){importUtilitySheetPage(uri,label,0);return;}
    String[] choices=new String[count];for(int j=0;j<count;j++)choices[j]="Blatt "+(j+1);
    new android.app.AlertDialog.Builder(this).setTitle("Schachtschein – Blatt auswählen").setItems(choices,(d,j)->importUtilitySheetPage(uri,label,j)).setNegativeButton("Abbrechen",null).show();
   }catch(Exception e){notice("PDF konnte nicht gelesen werden.");}
  }else importUtilitySheetPage(uri,label,-1);
 }
 void importUtilitySheetPage(android.net.Uri uri,String name,int pageIndex){
  final String projectId=activeProject;
  new Thread(()->{
   java.io.File file=null;android.graphics.Bitmap bitmap=null;
   try{
    if(pageIndex<0)bitmap=imageForOcr(uri);else try(android.os.ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"r");android.graphics.pdf.PdfRenderer pdf=new android.graphics.pdf.PdfRenderer(fd);android.graphics.pdf.PdfRenderer.Page page=pdf.openPage(pageIndex)){
     double factor=1600.0/Math.max(page.getWidth(),page.getHeight());bitmap=android.graphics.Bitmap.createBitmap(Math.max(1,(int)(page.getWidth()*factor)),Math.max(1,(int)(page.getHeight()*factor)),android.graphics.Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
    }
    if(bitmap==null)throw new java.io.IOException();
    if(Math.max(bitmap.getWidth(),bitmap.getHeight())>1600){double factor=1600.0/Math.max(bitmap.getWidth(),bitmap.getHeight());android.graphics.Bitmap resized=android.graphics.Bitmap.createScaledBitmap(bitmap,Math.max(1,(int)(bitmap.getWidth()*factor)),Math.max(1,(int)(bitmap.getHeight()*factor)),true);if(resized!=bitmap)bitmap.recycle();bitmap=resized;}
    java.io.File dir=new java.io.File(getFilesDir(),"photos");if(!dir.isDirectory()&&!dir.mkdirs())throw new java.io.IOException();
    file=java.io.File.createTempFile("schachtschein_",".png",dir);
    try(java.io.OutputStream out=new java.io.FileOutputStream(file)){if(!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out))throw new java.io.IOException();}
    final java.io.File saved=file;
    runOnUiThread(()->{
     if(isDestroyed()||isFinishing()||!projectId.equals(activeProject)){saved.delete();return;}
     try{org.json.JSONArray rows=utilitySheets();if(rows.length()>=MAX_UTILITY_SHEETS){saved.delete();notice("Maximal 12 Planblätter je Projekt.");return;}
      rows.put(new org.json.JSONObject().put("file",saved.getName()).put("name",name+(pageIndex>=0?" · Blatt "+(pageIndex+1):"")).put("visible",true).put("x",0).put("y",0).put("scale",1).put("rotation",0).put("alpha",140));
      if(!saveUtilitySheets(rows)){saved.delete();return;}applyDrawingLayers(drawingLayers()|RouteSketch.LAYER_SCHACHTSCHEINE);editUtilitySheet(rows.length()-1);
     }catch(Exception e){saved.delete();notice("Schachtschein konnte nicht gespeichert werden.");}
    });
   }catch(Exception e){if(file!=null)file.delete();runOnUiThread(()->{if(!isDestroyed())notice("Schachtschein konnte nicht geladen werden.");});}
   finally{if(bitmap!=null)bitmap.recycle();}
  },"utility-plan-import").start();
 }
 void chooseRouteUnderlay(){
  new android.app.AlertDialog.Builder(this).setTitle("Zeichnungsunterlage – PDF / Foto")
   .setMessage("PDF-Seite oder Foto als Hintergrund verwenden. Trasse und Maße bleiben unverändert. Maßstab bei Bedarf kalibrieren.")
   .setNegativeButton("Abbrechen",null).setPositiveButton("Datei auswählen",(d,w)->{
    android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);i.addCategory(android.content.Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(android.content.Intent.EXTRA_MIME_TYPES,new String[]{"image/*","application/pdf"});startActivityForResult(i,427);
   }).show();
 }
 void importRouteUnderlay(android.net.Uri uri){
  String mime=getContentResolver().getType(uri);
  if("application/pdf".equals(mime)||(uri.getLastPathSegment()!=null&&uri.getLastPathSegment().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf"))){
   try(android.os.ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"r");android.graphics.pdf.PdfRenderer pdf=new android.graphics.pdf.PdfRenderer(fd)){
    int count=pdf.getPageCount();if(count==0){notice("PDF enthält keine Seiten.");return;}
    if(count==1){importRouteUnderlay(uri,0);return;}
    String[] labels=new String[count];for(int k=0;k<count;k++)labels[k]="Blatt "+(k+1);
    new android.app.AlertDialog.Builder(this).setTitle("PDF-Blatt auswählen").setItems(labels,(d,k)->importRouteUnderlay(uri,k)).setNegativeButton("Abbrechen",null).show();
   }catch(Exception e){notice("PDF konnte nicht geöffnet werden.");}
  }else importRouteUnderlay(uri,-1);
 }
 void importRouteUnderlay(android.net.Uri uri,int pdfPage){
  String projectId=activeProject;String original=getPreferences(0).getString("routeUnderlay","");
  new Thread(()->{
   java.io.File file=null;android.graphics.Bitmap bitmap=null;
   try{
    if(pdfPage<0)bitmap=imageForOcr(uri);else{
     try(android.os.ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"r");android.graphics.pdf.PdfRenderer pdf=new android.graphics.pdf.PdfRenderer(fd);android.graphics.pdf.PdfRenderer.Page page=pdf.openPage(pdfPage)){
      double scale=3000.0/Math.max(page.getWidth(),page.getHeight());bitmap=android.graphics.Bitmap.createBitmap(Math.max(1,(int)(page.getWidth()*scale)),Math.max(1,(int)(page.getHeight()*scale)),android.graphics.Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
     }
    }if(bitmap==null)throw new java.io.IOException("Bild nicht lesbar");
    java.io.File dir=new java.io.File(getFilesDir(),"photos");if(!dir.isDirectory()&&!dir.mkdirs())throw new java.io.IOException("Bildordner fehlt");
    file=java.io.File.createTempFile("vorlage_",".png",dir);
    try(java.io.OutputStream out=new java.io.FileOutputStream(file)){if(!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out))throw new java.io.IOException("Bild konnte nicht gespeichert werden");}
    final java.io.File saved=file;
    runOnUiThread(()->{
     if(isDestroyed()||isFinishing()||!projectId.equals(activeProject)||!original.equals(getPreferences(0).getString("routeUnderlay",""))){saved.delete();return;}
     if(!getPreferences(0).edit().putString("routeUnderlay",saved.getName()).remove("routeUnderlayTransform").remove("routeUnderlayAlpha").remove("routeUnderlayHidden").remove("sketchCalibration").commit()){saved.delete();notice("Vorlage konnte nicht gespeichert werden.");return;}
     recordProjectEvent("Zeichenvorlage geändert");editRoute(false,true);
    });
   }catch(Exception e){if(file!=null)file.delete();runOnUiThread(()->{if(!isDestroyed())notice("Vorlage konnte nicht geladen werden.");});}
   finally{if(bitmap!=null)bitmap.recycle();}
  },"route-underlay").start();
 }
 double sketchCalibration(RouteSketch route,org.json.JSONObject calibration){
  if(calibration==null)return Double.NaN;
  int a=findPoint(route,calibration.optString("a")),b=findPoint(route,calibration.optString("b"));
  if(a<0||b<0||a==b)return Double.NaN;
  RouteSketch.Vertex first=route.vertices.get(a),second=route.vertices.get(b);
  if(first.point.x!=calibration.optDouble("ax")||first.point.y!=calibration.optDouble("ay")||second.point.x!=calibration.optDouble("bx")||second.point.y!=calibration.optDouble("by"))return Double.NaN;
  return RouteSketch.calibrationFactor(first,second,calibration.optDouble("metres"));
 }
 org.json.JSONObject savedSketchCalibration(){
  try{return new org.json.JSONObject(getPreferences(0).getString("sketchCalibration","{}"));}catch(Exception e){return new org.json.JSONObject();}
 }
 void calibrateSketch(RouteSketch route,java.util.function.Consumer<org.json.JSONObject> accept){
  if(route.vertices.size()<2){notice("Zuerst mindestens zwei Punkte zeichnen.");return;}
  LinearLayout form=page();
  form.addView(sheetText("Zwei Punkte mit bekanntem geradlinigem Abstand wählen. Nur für maßstäbliche Vorlagen geeignet. Schätzwerte sind keine Aufmaßwerte.",14,false));
  String[] labels=new String[route.vertices.size()];
  for(int j=0;j<labels.length;j++)labels[j]="Punkt "+(j+1)+(route.vertices.get(j).name.isEmpty()?"":" · "+route.vertices.get(j).name);
  form.addView(sheetText("Referenzpunkt A",14,true));Spinner a=choices(form,labels);
  form.addView(sheetText("Referenzpunkt B",14,true));Spinner b=choices(form,labels);b.setSelection(1);
  EditText distance=new EditText(this);distance.setHint("Bekannter Abstand (m)");distance.setInputType(8194);form.addView(distance);
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Zeichnungsmaßstab kalibrieren").setView(form).setNegativeButton("Abbrechen",null).setPositiveButton("Kalibrieren",null).create();
  dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(btn->{
   try{
    RouteSketch.Vertex first=route.vertices.get(a.getSelectedItemPosition()),second=route.vertices.get(b.getSelectedItemPosition());
    double known=number(distance);if(!Double.isFinite(RouteSketch.calibrationFactor(first,second,known))){distance.setError("Positive Distanz und zwei getrennte Punkte erforderlich");return;}
    org.json.JSONObject data=new org.json.JSONObject().put("a",first.id).put("b",second.id).put("ax",(double)first.point.x).put("ay",(double)first.point.y).put("bx",(double)second.point.x).put("by",(double)second.point.y).put("metres",known);
    accept.accept(data);dialog.dismiss();
   }catch(Exception e){distance.setError("Abstand prüfen");}
  }));dialog.show();
 }
 java.util.function.Consumer<String> pendingEditorSpeech;
 void startEditorDictation(java.util.function.Consumer<String> accept){
  pendingEditorSpeech=accept;
  android.content.Intent i=new android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
  i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
  i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE,voiceLanguage.getSelectedItemPosition()==0?"de-DE":"ru-RU");
  i.putExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
  try{startActivityForResult(i,428);}catch(Exception e){pendingEditorSpeech=null;notice("Keine Spracherkennung verfügbar.");}
 }
 void applyEditorSpeech(String speech,RouteSketch route,Spinner mode,LinearLayout historyBar,android.app.Dialog full){
  if(!full.isShowing())return;
  String command=speech.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[.!?]+$","");
  if(command.matches("(?:один объект|отдельный объект|объект без трассы|только домовое подключение|только хаусанслюс|только hausanschluss|einzelobjekt|objekt ohne trasse|nur hausanschluss)")){
   mode.setSelection(5);notice("Position des Einzelobjekts antippen.");return;
  }
  if(command.matches("(?:рисовать|режим рисования|zeichnen|zeichenmodus)")){mode.setSelection(0);return;}
  if(command.matches("(?:размеры|режим размеров|bemaßen|maß eintragen)")){mode.setSelection(1);return;}
  if(command.matches("(?:перемещение точек|переместить точки|punkte verschieben)")){mode.setSelection(2);return;}
  if(command.matches("(?:отмени|отмена|назад|rückgängig)")){historyBar.getChildAt(0).performClick();return;}
  if(command.matches("(?:повтори|повторить|wiederholen)")){historyBar.getChildAt(1).performClick();return;}
  java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?iu)^(?:измени\\s+|установи\\s+|ändere\\s+|setze\\s+)?(?:длин[ау]\\s+участка|länge\\s+(?:von\\s+)?abschnitt|abschnitt|участок)\\s+(\\d+)\\s*(?:на\\s+|auf\\s+|ist\\s+|[:=–—-]\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(мм|mm|см|cm|сантиметр[а-я]*|zentimeter|м|m|метр[а-я]*|meter)$").matcher(command);
  if(!m.matches()){notice("Editor: Zeichnen, Bemaßen, Punkte verschieben, Rückgängig oder Abschnitt 2 auf 12,5 m.");return;}
  try{
   int index=Integer.parseInt(m.group(1));double value=Double.parseDouble(m.group(2).replace(',','.'));String unit=m.group(3);
   if(unit.equals("мм")||unit.equals("mm"))value/=1000;else if(unit.equals("см")||unit.equals("cm")||unit.startsWith("сантиметр")||unit.equals("zentimeter"))value/=100;
   if(index<1||index>=route.vertices.size()||!Double.isFinite(value)||value<=0){notice("Abschnitt oder Maß ungültig.");return;}
   final double metres=value;final String before=route.encode();
   new android.app.AlertDialog.Builder(this).setTitle("Sprachmaß prüfen").setMessage("Abschnitt "+index+": "+reportMeasure(metres)+"\n"+(Double.isFinite(route.vertices.get(index).metres)?"Bisher: "+reportMeasure(route.vertices.get(index).metres):"Bisher: Maß fehlt"))
    .setNegativeButton("Abbrechen",null).setPositiveButton("Übernehmen",(d,w)->{
     if(!full.isShowing()||!before.equals(route.encode())){notice("Skizze geändert. Bitte erneut eingeben.");return;}
     route.vertices.get(index).metres=metres;if(route.changed!=null)route.changed.run();route.invalidate();
    }).show();
  }catch(Exception e){notice("Sprachmaß nicht erkannt.");}
 }
 void showFullscreenMeasures(RouteSketch route){
  if(route.vertices.size()<2){notice("Zuerst einen Abschnitt zeichnen.");return;}
  String[] labels=route.measurements().split("\n");
  new android.app.AlertDialog.Builder(this).setTitle("Abschnitte bemaßen").setItems(labels,(d,w)->{if(w+1<route.vertices.size())measureSegment(route,w+1);}).setNegativeButton("Schließen",null).show();
 }
 void showEditorObjects(RouteSketch route,org.json.JSONArray draft,Runnable refresh){
  LinearLayout form=page();ScrollView scroll=new ScrollView(this);scroll.addView(form);
  form.addView(sheetText("Änderungen werden erst mit der Trasse gespeichert. Überlappungen bleiben erhalten. Eintrag antippen, um ihn zu bearbeiten.",14,false));
  for(int j=0;j<draft.length();j++){final int index=j;org.json.JSONObject o=draft.optJSONObject(j);if(o==null)continue;Button b=new Button(this);b.setAllCaps(false);b.setText(o.optString("type")+" · "+o.optString("station")+" m"+detailSummary(o));b.setOnClickListener(v->chooseEditorObject(route,draft,refresh,index));form.addView(b);}
  new android.app.AlertDialog.Builder(this).setTitle("Trassenobjekte – Entwurf").setView(scroll).setNegativeButton("Schließen",null).setPositiveButton("Objekt hinzufügen",(d,w)->addEditorObject(route,draft,refresh)).show();
 }
 void chooseEditorObject(RouteSketch route,org.json.JSONArray draft,Runnable refresh,int index){
  org.json.JSONObject o=draft.optJSONObject(index);if(o==null)return;
  LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(12),dp(10),dp(12),dp(10));panel.setBackground(editorShape(0xff202827,16));
  TextView title=sheetText(o.optString("type"),17,true);title.setTextColor(0xffe3f0ed);panel.addView(title);
  TextView subtitle=sheetText("Station "+csvNumber(o.optDouble("station"))+" m · "+o.optString("side"),13,false);subtitle.setTextColor(0xff9edbd2);panel.addView(subtitle);
  android.widget.PopupWindow popup=new android.widget.PopupWindow(panel,Math.min(route.getWidth(),dp(360)),-2,true);popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));popup.setOutsideTouchable(true);
  LinearLayout actions=new LinearLayout(this);panel.addView(actions);
  Button edit=new Button(this),remove=new Button(this),close=new Button(this);
  edit.setText("Eigenschaften");remove.setText("Entfernen");close.setText("Schließen");
  for(Button button:new Button[]{edit,remove,close}){styleEditorButton(button,button==edit);actions.addView(button,new LinearLayout.LayoutParams(0,dp(48),1));}
  edit.setOnClickListener(v->{popup.dismiss();if(draft.optJSONObject(index)==o)editEditorObject(route,draft,refresh,index,Double.NaN);});
  remove.setOnClickListener(v->{popup.dismiss();if(draft.optJSONObject(index)==o)new android.app.AlertDialog.Builder(this).setTitle("Objekt entfernen?").setMessage("Die Trasse und andere Objekte bleiben unverändert.").setNegativeButton("Abbrechen",null).setPositiveButton("Entfernen",(x,y)->{if(draft.optJSONObject(index)==o){draft.remove(index);refresh.run();}}).show();});
  close.setOnClickListener(v->popup.dismiss());
  popup.showAtLocation(route,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,dp(12));
 }
 void pickEditorObject(RouteSketch route,org.json.JSONArray draft,Runnable refresh,java.util.ArrayList<Integer> hits){
  if(hits.isEmpty()){notice("Bitte ein Objekt auf der Zeichnung antippen.");return;}
  if(hits.size()==1){chooseEditorObject(route,draft,refresh,hits.get(0));return;}
  String[] labels=new String[hits.size()];for(int j=0;j<hits.size();j++){org.json.JSONObject o=draft.optJSONObject(hits.get(j));labels[j]=(hits.get(j)+1)+" · "+o.optString("type")+" · "+csvNumber(o.optDouble("station"))+" m";}
  new android.app.AlertDialog.Builder(this).setTitle("Überlappende Objekte auswählen").setItems(labels,(d,w)->chooseEditorObject(route,draft,refresh,hits.get(w))).setNegativeButton("Abbrechen",null).show();
 }
 void pickEditorObjectStation(RouteSketch route,org.json.JSONArray draft,Runnable refresh,java.util.ArrayList<RouteSketch.StationHit> hits){
  if(hits.isEmpty()){notice("Bitte nahe an einer vollständig bemaßten Trasse antippen.");return;}
  if(hits.size()==1){addEditorObject(route,draft,refresh,hits.get(0).station);return;}
  String[] labels=new String[hits.size()];
  for(int j=0;j<hits.size();j++){RouteSketch.StationHit hit=hits.get(j);labels[j]="Abschnitt "+hit.segment+" · Station "+csvNumber(hit.station)+" m";}
  new android.app.AlertDialog.Builder(this).setTitle("Mehrere mögliche Stationen").setItems(labels,(d,w)->addEditorObject(route,draft,refresh,hits.get(w).station)).setNegativeButton("Abbrechen",null).show();
 }
 void addEditorObject(RouteSketch route,org.json.JSONArray draft,Runnable refresh){addEditorObject(route,draft,refresh,Double.NaN);}
 void addEditorObject(RouteSketch route,org.json.JSONArray draft,Runnable refresh,double suggestedStation){editEditorObject(route,draft,refresh,-1,suggestedStation);}
 void editEditorObject(RouteSketch route,org.json.JSONArray draft,Runnable refresh,int index,double suggestedStation){
  org.json.JSONObject old=index<0?null:draft.optJSONObject(index);if(index>=0&&old==null)return;
  double total=route.measuredLength();
  if(!Double.isFinite(total)||total<=0){notice("Zuerst die Trasse vollständig bemaßen, damit Stationen eindeutig sind.");return;}
  LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(dp(8),0,dp(8),0);ScrollView scroll=new ScrollView(this);scroll.addView(form);
  java.util.ArrayList<String> types=new java.util.ArrayList<>(java.util.Arrays.asList("Hausanschluss","Suchschachtung","Hindernis","Startgrube","Zielgrube","KVS","Knickpunkt"));if(old!=null&&!types.contains(old.optString("type")))types.add(old.optString("type"));Spinner type=choices(form,types.toArray(new String[0]));if(old!=null)type.setSelection(types.indexOf(old.optString("type")));
  EditText station=new EditText(this);station.setInputType(8194);station.setHint("Station in m (0 – "+csvNumber(total)+")");if(Double.isFinite(suggestedStation)){station.setText(java.math.BigDecimal.valueOf(suggestedStation).stripTrailingZeros().toPlainString().replace('.',','));}if(old!=null)station.setText(old.optString("station"));form.addView(station);
  String[] sides={"Unbekannt","links","rechts","mittig"};Spinner side=choices(form,sides);if(old!=null)side.setSelection(Math.max(0,java.util.Arrays.asList(sides).indexOf(old.optString("side"))));
  String[] keys={"detailLength","detailWidth","detailDepth","detailOffset"},labels={"Länge (m)","Breite (m)","Tiefe (m)","Abstand zum Grabenrand (m)"};
  EditText[] fields=new EditText[keys.length];for(int j=0;j<fields.length;j++){fields[j]=new EditText(this);fields[j].setInputType(8194);fields[j].setHint(labels[j]+" – optional");if(old!=null)fields[j].setText(old.optString(keys[j],""));form.addView(fields[j]);addObjectMeasureSlider(form,fields[j],j==3?5:10,j==3?0:1);}
  LinearLayout angleRow=new LinearLayout(this);angleRow.setGravity(Gravity.CENTER_VERTICAL);form.addView(angleRow);
  TextView angleLabel=sheetText("Drehwinkel",13,false);angleLabel.setTextColor(0xffa4b9b5);angleRow.addView(angleLabel,new LinearLayout.LayoutParams(0,-2,1));
  TextView angleValue=sheetText("",16,true);angleValue.setTextColor(0xff69cdbb);angleRow.addView(angleValue);
  SeekBar rotationBar=new SeekBar(this);rotationBar.setMax(360);rotationBar.setProgress(Math.max(0,Math.min(360,180+(old==null?0:old.optInt("detailRotation",0)))));rotationBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff55c7b5));rotationBar.setThumbTintList(android.content.res.ColorStateList.valueOf(0xff69cdbb));form.addView(rotationBar,new LinearLayout.LayoutParams(-1,dp(36)));
  angleValue.setText((rotationBar.getProgress()-180)+"°");
  LinearLayout presets=new LinearLayout(this);form.addView(presets);for(int angle:new int[]{0,15,30,45,60,75,90}){Button preset=new Button(this);preset.setText(angle+"°");styleEditorButton(preset,false);presets.addView(preset,new LinearLayout.LayoutParams(0,dp(40),1));preset.setOnClickListener(v->rotationBar.setProgress(angle+180));}
  View separator=new View(this);separator.setBackgroundColor(0xff465452);LinearLayout.LayoutParams separatorParams=new LinearLayout.LayoutParams(-1,dp(1));separatorParams.setMargins(0,dp(12),0,dp(12));form.addView(separator,separatorParams);
  LinearLayout quantities=new LinearLayout(this);quantities.setPadding(dp(4),dp(4),dp(4),dp(8));form.addView(quantities);
  TextView[] results=new TextView[2];String[] resultLabels={"Fläche","Volumen"};
  for(int k=0;k<2;k++){LinearLayout column=new LinearLayout(this);column.setOrientation(1);quantities.addView(column,new LinearLayout.LayoutParams(0,-2,1));TextView caption=sheetText(resultLabels[k],13,false);caption.setTextColor(0xffa4b9b5);column.addView(caption);results[k]=sheetText("—",24,true);results[k].setTextColor(k==0?0xffe3f0ed:0xff69cdbb);column.addView(results[k]);}
  TextView quantityHint=sheetText("Geometrische Detailmengen – nicht zur Trasse addiert.",12,false);quantityHint.setTextColor(0xffa4b9b5);form.addView(quantityHint);
  Runnable updateQuantities=()->{
   double[] values={Double.NaN,Double.NaN,Double.NaN};
   for(int k=0;k<3;k++)try{double n=number(fields[k]);if(Double.isFinite(n)&&n>0)values[k]=n;}catch(Exception ignored){}
   double area=CivilGeometry.rectangularArea(values[0],values[1]),volume=area*values[2];
   results[0].setText(Double.isFinite(area)?String.format(java.util.Locale.GERMANY,"%.2f m²",area):"—");
   results[1].setText(Double.isFinite(volume)?String.format(java.util.Locale.GERMANY,"%.3f m³",volume):"—");
  };
  for(int k=0;k<3;k++)fields[k].addTextChangedListener(new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence value,int start,int count,int after){}
   public void onTextChanged(CharSequence value,int start,int before,int count){updateQuantities.run();}
   public void afterTextChanged(android.text.Editable value){}
  });updateQuantities.run();
  EditText note=new EditText(this);note.setHint("Bemerkung");if(old!=null)note.setText(old.optString("note"));form.addView(note);
  LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(12),dp(10),dp(12),dp(10));panel.setBackground(editorShape(0xff202827,16));
  TextView heading=sheetText(old==null?"Objekt hinzufügen":"Objekt bearbeiten",17,true);heading.setTextColor(0xffe3f0ed);panel.addView(heading);
  for(EditText field:new EditText[]{station,note,fields[0],fields[1],fields[2],fields[3]}){field.setTextColor(field==station||field==note?0xffe3f0ed:0xff69cdbb);field.setHintTextColor(0xffa4b9b5);field.setTextSize(14);}
  panel.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(dp(300),Math.max(dp(100),route.getHeight()/2))));
  LinearLayout actions=new LinearLayout(this);panel.addView(actions);Button cancel=new Button(this),apply=new Button(this);cancel.setText("Abbrechen");apply.setText("Übernehmen");styleEditorButton(cancel,false);styleEditorButton(apply,true);actions.addView(cancel,new LinearLayout.LayoutParams(0,dp(48),1));actions.addView(apply,new LinearLayout.LayoutParams(0,dp(48),1));
  android.widget.PopupWindow dialog=new android.widget.PopupWindow(panel,Math.min(route.getWidth(),dp(420)),-2,true);dialog.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));dialog.setOutsideTouchable(true);dialog.setInputMethodMode(android.widget.PopupWindow.INPUT_METHOD_NEEDED);dialog.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);cancel.setOnClickListener(v->dialog.dismiss());
  Runnable preview=()->{
   try{
    double st=number(station);if(!Double.isFinite(st)||st<0||st>route.measuredLength())return;
    org.json.JSONObject candidate=old==null?new org.json.JSONObject():new org.json.JSONObject(old.toString());
    String chosen=side.getSelectedItem().toString();candidate.put("detailRotation",rotationBar.getProgress()-180).put("station",st).put("type",type.getSelectedItem().toString()).put("side",chosen);
    for(int k=0;k<fields.length;k++){candidate.remove(keys[k]);if(fields[k].length()==0)continue;try{double n=number(fields[k]);if(Double.isFinite(n)&&(k==3?n>=0&&(chosen.equals("links")||chosen.equals("rechts")):n>0))candidate.put(keys[k],n);}catch(Exception ignored){}}
    org.json.JSONArray temporary=new org.json.JSONArray(draft.toString());if(index<0)temporary.put(candidate);else temporary.put(index,candidate);
    route.setStationLayers(temporary,route.surfaceSections);
   }catch(Exception ignored){}
  };
  rotationBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int progress,boolean user){angleValue.setText((progress-180)+"°");preview.run();}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}});
  android.text.TextWatcher previewWatcher=new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence value,int start,int count,int after){}
   public void onTextChanged(CharSequence value,int start,int before,int count){preview.run();}
   public void afterTextChanged(android.text.Editable value){}
  };
  station.addTextChangedListener(previewWatcher);for(EditText field:fields)field.addTextChangedListener(previewWatcher);
  android.widget.AdapterView.OnItemSelectedListener previewSelection=new android.widget.AdapterView.OnItemSelectedListener(){
   public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){preview.run();}
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
  };side.setOnItemSelectedListener(previewSelection);type.setOnItemSelectedListener(previewSelection);
  dialog.setOnDismissListener(()->route.setStationLayers(draft,route.surfaceSections));
  apply.setOnClickListener(btn->{
   try{
    double st=number(station),limit=route.measuredLength();if(!Double.isFinite(st)||!Double.isFinite(limit)||st<0||st>limit){station.setError("Station außerhalb der bemaßten Trasse");return;}
    String chosen=side.getSelectedItem().toString();
    org.json.JSONObject o=(old==null?new org.json.JSONObject():new org.json.JSONObject(old.toString())).put("detailRotation",rotationBar.getProgress()-180).put("station",st).put("type",type.getSelectedItem().toString()).put("side",chosen).put("note",note.getText().toString());
    for(int j=0;j<fields.length;j++){if(fields[j].length()==0){o.remove(keys[j]);continue;}double value=number(fields[j]);if(!Double.isFinite(value)||value<0||(j<3&&value==0)||(j==3&&!chosen.equals("links")&&!chosen.equals("rechts"))){fields[j].setError("Maß / Seite prüfen");return;}o.put(keys[j],fields[j].getText().toString());}
    if(old==null)draft.put(o);else if(draft.optJSONObject(index)==old){if(old.optInt("detailRotation",0)!=o.optInt("detailRotation",0)||!overlapGeometry(old).equals(overlapGeometry(o)))o.remove("acceptedOverlap");draft.put(index,o);}else{notice("Objekt wurde geändert.");return;}refresh.run();dialog.dismiss();
   }catch(Exception e){notice("Station und Maße prüfen.");}
  });dialog.showAtLocation(route,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,dp(8));preview.run();
 }
 void addObjectMeasureSlider(LinearLayout form,EditText field,int defaultMax,int minimumCm){
  android.widget.SeekBar bar=new android.widget.SeekBar(this);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff55c7b5));bar.setThumbTintList(android.content.res.ColorStateList.valueOf(0xff69cdbb));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(4),dp(4),dp(4),0);
  TextView label=sheetText(field.getHint().toString().replace(" – optional",""),13,false);label.setTextColor(0xffa4b9b5);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
  android.view.ViewGroup parent=(android.view.ViewGroup)field.getParent();int index=parent.indexOfChild(field);parent.removeView(field);field.setTextColor(0xff69cdbb);field.setTextSize(15);field.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);field.setBackground(editorShape(0xff202827,8));field.setPadding(dp(6),0,dp(6),0);row.addView(field,new LinearLayout.LayoutParams(dp(92),dp(40)));form.addView(row,index);
  bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff465452));form.addView(bar,new LinearLayout.LayoutParams(-1,dp(32)));
  Runnable sync=()->{try{double value=number(field);if(!Double.isFinite(value)||value<0||value>100000)return;int cm=(int)Math.round(value*100);bar.setMax(Math.max(defaultMax*100,cm)-minimumCm);bar.setProgress(Math.max(0,cm-minimumCm));}catch(Exception ignored){}};
  bar.setMax(defaultMax*100-minimumCm);sync.run();
  bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
   public void onProgressChanged(android.widget.SeekBar seek,int progress,boolean user){if(user)field.setText(String.format(java.util.Locale.GERMANY,"%.2f",(progress+minimumCm)/100.0));}
   public void onStartTrackingTouch(android.widget.SeekBar seek){}
   public void onStopTrackingTouch(android.widget.SeekBar seek){}
  });
  field.setOnFocusChangeListener((v,focused)->{if(!focused)sync.run();});
 }
 android.graphics.drawable.GradientDrawable editorShape(int color,int radius){
  android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));return shape;
 }
 android.graphics.drawable.GradientDrawable proButtonShape(boolean primary){
  android.graphics.drawable.GradientDrawable shape=primary?new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff009e96,0xff006b65}):editorShape(0xff101c1e,8);
  shape.setCornerRadius(dp(8));shape.setStroke(dp(1),primary?0xff26d9d0:0xff405658);return shape;
 }
 void styleEditorButton(Button b,boolean primary){
  b.setAllCaps(false);b.setTextSize(primary?15:12);b.setTextColor(Color.WHITE);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(4),dp(4),dp(4),dp(4));b.setBackgroundTintList(null);
  b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x3312665f),proButtonShape(primary),null));
 }
 void compactToolIcon(Button button,String label,String kind){
  android.graphics.drawable.Drawable icon=new android.graphics.drawable.Drawable(){
   final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
   @Override public void draw(Canvas c){
    c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);
    p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
    switch(kind){
     case "document":c.drawRoundRect(5,3,19,21,2,2,p);for(int yy=8;yy<=16;yy+=4)c.drawLine(8,yy,16,yy,p);break;
     case "save":c.drawRoundRect(4,3,20,21,2,2,p);c.drawRect(8,3,16,9,p);c.drawRect(8,13,16,21,p);break;
     case "back":c.drawLine(15,5,8,12,p);c.drawLine(8,12,15,19,p);break;
     case "map":{android.graphics.Path map=new android.graphics.Path();map.moveTo(3,5);map.lineTo(9,3);map.lineTo(15,6);map.lineTo(21,3);map.lineTo(21,19);map.lineTo(15,22);map.lineTo(9,19);map.lineTo(3,21);map.close();c.drawPath(map,p);c.drawLine(9,3,9,19,p);c.drawLine(15,6,15,22,p);break;}
     case "image":c.drawRoundRect(3,4,21,20,2,2,p);c.drawCircle(8,9,2,p);c.drawLine(4,18,11,12,p);c.drawLine(11,12,16,17,p);break;
     case "mic":c.drawRoundRect(9,3,15,14,3,3,p);c.drawArc(6,7,18,18,0,180,false,p);c.drawLine(12,18,12,22,p);c.drawLine(8,22,16,22,p);break;
     case "ruler":c.save();c.rotate(-45,12,12);c.drawRect(8,3,16,21,p);for(int y=6;y<20;y+=3)c.drawLine(8,y,y%2==0?12:11,y,p);c.restore();break;
     case "center":c.drawCircle(12,12,5,p);c.drawLine(12,2,12,7,p);c.drawLine(12,17,12,22,p);c.drawLine(2,12,7,12,p);c.drawLine(17,12,22,12,p);break;
     case "view":for(int x:new int[]{4,20})for(int y:new int[]{4,20}){c.drawLine(x,y,x+(x==4?5:-5),y,p);c.drawLine(x,y,x,y+(y==4?5:-5),p);}break;
     case "legend":for(int y=6;y<=18;y+=6){c.drawCircle(5,y,1,p);c.drawLine(9,y,20,y,p);}break;
     case "objects":c.drawRoundRect(5,5,19,19,2,2,p);c.drawLine(9,12,15,12,p);c.drawLine(12,9,12,15,p);break;
     case "sections":c.drawLine(3,12,21,12,p);for(int x:new int[]{3,9,15,21}){c.drawLine(x,7,x,17,p);}break;
     case "pipe":c.drawCircle(7.5f,12,6,p);c.drawCircle(7.5f,12,3.8f,p);c.drawCircle(19,14,4,p);c.drawCircle(19,14,2.1f,p);break;
     case "settings":c.drawCircle(12,12,7,p);c.drawCircle(12,12,3,p);for(int k=0;k<8;k++){c.save();c.rotate(k*45,12,12);c.drawLine(12,2,12,5,p);c.restore();}break;
     case "cloud":{android.graphics.Path cloud=new android.graphics.Path();cloud.moveTo(6,18);cloud.cubicTo(0,18,1,9,7,9);cloud.cubicTo(7,2,18,2,18,10);cloud.cubicTo(24,10,24,18,18,18);cloud.close();c.drawPath(cloud,p);break;}
      case "layers":c.drawLine(4,8,12,3,p);c.drawLine(12,3,20,8,p);c.drawLine(20,8,12,13,p);c.drawLine(12,13,4,8,p);c.drawLine(4,13,12,18,p);c.drawLine(12,18,20,13,p);c.drawLine(4,18,12,22,p);c.drawLine(12,22,20,18,p);break;
     default:for(int y=6;y<=18;y+=6)c.drawLine(4,y,20,y,p);
    }c.restore();
   }
   @Override public void setAlpha(int a){p.setAlpha(a);}
   @Override public void setColorFilter(android.graphics.ColorFilter f){p.setColorFilter(f);}
   @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
  };
  icon.setBounds(0,0,dp(20),dp(20));button.setText(label);button.setContentDescription(label);button.setCompoundDrawables(null,icon,null,null);button.setCompoundDrawablePadding(dp(3));button.setGravity(Gravity.CENTER);button.setMinHeight(dp(52));button.setTextSize(12);
 }
 void showFullscreenRoute(android.app.AlertDialog editor,LinearLayout form,RouteSketch route,Spinner mode,Spinner angles,LinearLayout historyBar,Runnable openObjects,LinearLayout underlayControls,boolean immersiveInitially){
  final boolean[] immersive={immersiveInitially};
  // Move the existing views, not a second sketch: unsaved data and undo history stay shared.
  View[] views={route,mode,angles,historyBar};
  int[] positions=new int[views.length];android.view.ViewGroup.LayoutParams[] params=new android.view.ViewGroup.LayoutParams[views.length];
  for(int j=0;j<views.length;j++){if(views[j].getParent()!=form)return;positions[j]=form.indexOfChild(views[j]);params[j]=views[j].getLayoutParams();}
  android.app.Dialog full=new android.app.Dialog(this,android.R.style.Theme_Material_NoActionBar){
   @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused&&immersive[0]&&getWindow()!=null){androidx.core.view.WindowInsetsControllerCompat c=androidx.core.view.WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView());c.setSystemBarsBehavior(androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());}}
  };
  full.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
  final int previousOrientation=getRequestedOrientation();
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.BLACK);
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(8),dp(4),dp(8),dp(4));root.addView(header);
  Button back=new Button(this),save=new Button(this);back.setAllCaps(false);save.setAllCaps(false);back.setText("‹");back.setContentDescription("Vollbild beenden");save.setText("Speichern");
  header.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
  LinearLayout heading=new LinearLayout(this);heading.setOrientation(1);heading.addView(sheetText("Feldaufmaß Pro",16,true));heading.addView(sheetText("Tiefbau · Zeichnungseditor",10,false));for(int k=0;k<heading.getChildCount();k++)if(heading.getChildAt(k) instanceof TextView)((TextView)heading.getChildAt(k)).setTextColor(0xffe9f2f1);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
  Spinner orientation=new Spinner(this);
  orientation.setAdapter(new android.widget.ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Auto","Quer","Hoch"}));
  header.addView(orientation,new LinearLayout.LayoutParams(dp(76),dp(48)));
  orientation.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
    if(!full.isShowing())return;
    if(position==0&&getRequestedOrientation()==previousOrientation)return;
    setRequestedOrientation(position==1?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:position==2?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
   }
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
  });
  for(View view:views)form.removeView(view);
  LinearLayout controls=new LinearLayout(this);controls.setPadding(dp(8),dp(4),dp(8),dp(4));controls.setBackground(editorShape(0xff101c1e,8));root.addView(controls);
  controls.addView(mode,new LinearLayout.LayoutParams(0,-2,1));controls.addView(angles,new LinearLayout.LayoutParams(0,-2,1));Spinner smartMode=new Spinner(this);smartMode.setAdapter(new android.widget.ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Freihand","Smart","Winkelraster 15°"}));smartMode.setSelection(route.smartDrawingMode);smartMode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){route.smartDrawingMode=pos;}public void onNothingSelected(android.widget.AdapterView<?> p){}});controls.addView(smartMode,new LinearLayout.LayoutParams(0,-2,1));
  Button layersButton=new Button(this);styleEditorButton(layersButton,false);compactToolIcon(layersButton,"","layers");layersButton.setContentDescription("Zeichnungsebenen; lange drücken für Schachtscheine");layersButton.setMinHeight(0);controls.addView(layersButton,new LinearLayout.LayoutParams(dp(44),dp(44)));layersButton.setOnClickListener(v->showDrawingLayers(route));
   layersButton.setOnLongClickListener(v->{showUtilitySheets();return true;});
  Button gpsButton=new Button(this);styleEditorButton(gpsButton,false);compactToolIcon(gpsButton,"","center");gpsButton.setContentDescription("GPS-Aufzeichnung");gpsButton.setMinHeight(0);controls.addView(gpsButton,new LinearLayout.LayoutParams(dp(44),dp(44)));gpsButton.setOnClickListener(v->showGpsTrackControls());
  route.darkEditor=true;route.invalidate();root.addView(route,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout sectionStrip=new LinearLayout(this);sectionStrip.setOrientation(1);sectionStrip.setPadding(dp(6),dp(2),dp(6),dp(2));
  TextView sectionTitle=sheetText("Abschnitte",13,true);sectionStrip.addView(sectionTitle);
  android.widget.HorizontalScrollView sectionScroll=new android.widget.HorizontalScrollView(this);sectionScroll.setHorizontalScrollBarEnabled(false);sectionStrip.addView(sectionScroll);sectionTitle.setText("Abschnitte ▾");final boolean[] sectionsCollapsed={true};sectionScroll.setVisibility(View.GONE);
  LinearLayout sectionCards=new LinearLayout(this);sectionScroll.addView(sectionCards);
  ScrollView verticalSections=new ScrollView(this);verticalSections.setFillViewport(false);verticalSections.setVisibility(View.GONE);
  sectionTitle.setOnClickListener(v->{sectionsCollapsed[0]=!sectionsCollapsed[0];int visibility=sectionsCollapsed[0]?View.GONE:View.VISIBLE;sectionScroll.setVisibility(visibility);verticalSections.setVisibility(visibility);});
  root.addView(sectionStrip,new LinearLayout.LayoutParams(-1,-2));
  final int[] selectedSection={-1};
  final TextView sectionPosition=sheetText("Abschnitt auswählen",12,false);sectionPosition.setTextColor(0xff69d6b7);sectionStrip.addView(sectionPosition,1);
  Runnable refreshSections=()->{
   sectionCards.removeAllViews();int count=Math.max(0,route.vertices.size()-1);
   sectionTitle.setText("Abschnitte ("+count+") ▾");sectionPosition.setText(selectedSection[0]>=0?"Abschnitt "+(selectedSection[0]+1)+" / "+count:"Abschnitt auswählen");double station=0;
   for(int j=0;j<count;j++){
    final int index=j;double length=route.vertices.get(j+1).metres;double end=station+length;
    Button card=new Button(this);styleEditorButton(card,selectedSection[0]==j);
    card.setText(String.format(java.util.Locale.GERMANY,"A%02d",j+1)+"\n"+(Double.isFinite(end)?String.format(java.util.Locale.GERMANY,"%.2f – %.2f m",station,end):"Maß fehlt"));
    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(104),dp(48));cp.setMargins(dp(2),0,dp(2),0);sectionCards.addView(card,cp);
    card.setOnClickListener(v->{if(!Double.isFinite(route.vertices.get(index+1).metres)||route.vertices.get(index+1).metres<=0){measureSegment(route,index+1);return;}selectedSection[0]=index;sectionPosition.setText("Abschnitt "+(index+1)+" / "+count);for(int k=0;k<sectionCards.getChildCount();k++)styleEditorButton((Button)sectionCards.getChildAt(k),k==index);});
    station=end;
   }
  };
  refreshSections.run();
  final Runnable originalRouteChanged=route.changed;
  route.changed=()->{if(originalRouteChanged!=null)originalRouteChanged.run();if(selectedSection[0]>=route.vertices.size()-1)selectedSection[0]=-1;refreshSections.run();};
  root.addView(historyBar,new LinearLayout.LayoutParams(-1,-2));
  for(int k=0;k<historyBar.getChildCount();k++)if(historyBar.getChildAt(k) instanceof Button)styleEditorButton((Button)historyBar.getChildAt(k),false);
  LinearLayout tools=new LinearLayout(this);tools.setPadding(dp(6),dp(3),dp(6),dp(3));root.addView(tools);
  Button mic=new Button(this),measureList=new Button(this),resetZoom=new Button(this);
  mic.setAllCaps(false);mic.setText("🎙");mic.setContentDescription("Sprachassistent");measureList.setAllCaps(false);measureList.setText("↔\nMaße");resetZoom.setAllCaps(false);resetZoom.setText("⌖\nAnsicht");
  header.addView(mic,new LinearLayout.LayoutParams(dp(44),dp(44)));tools.addView(measureList,new LinearLayout.LayoutParams(0,-2,1));tools.addView(resetZoom,new LinearLayout.LayoutParams(0,-2,1));
  mic.setOnClickListener(v->startEditorDictation(speech->applyEditorSpeech(speech,route,mode,historyBar,full)));
  measureList.setOnClickListener(v->showFullscreenMeasures(route));
  Button legendEdit=new Button(this);legendEdit.setAllCaps(false);legendEdit.setText("☷\nLegende");tools.addView(legendEdit,new LinearLayout.LayoutParams(0,-2,1));legendEdit.setOnClickListener(v->route.editLegend());
  resetZoom.setOnClickListener(v->{route.zoom=1;route.centerX=.5f;route.centerY=.5f;route.invalidate();});
  Button objectTools=new Button(this);objectTools.setAllCaps(false);objectTools.setText("□\nObjekte");tools.addView(objectTools,new LinearLayout.LayoutParams(0,-2,1));objectTools.setOnClickListener(v->openObjects.run());
  if(underlayControls!=null){
   Button underlay=new Button(this);underlay.setAllCaps(false);underlay.setText("▧\nVorlage");tools.addView(underlay,new LinearLayout.LayoutParams(0,-2,1));styleEditorButton(underlay,false);compactToolIcon(underlay,"Vorlage","image");
   underlay.setOnClickListener(v->{
    if(underlayControls.getParent()!=form)return;
    int position=form.indexOfChild(underlayControls);android.view.ViewGroup.LayoutParams originalParams=underlayControls.getLayoutParams();
    form.removeView(underlayControls);ScrollView panel=new ScrollView(this);panel.addView(underlayControls);
    android.app.AlertDialog settings=new android.app.AlertDialog.Builder(this).setTitle("Zeichenvorlage").setView(panel).setPositiveButton("Fertig",null).create();
    settings.setOnDismissListener(d->{panel.removeView(underlayControls);form.addView(underlayControls,Math.min(position,form.getChildCount()),originalParams);});settings.show();
   });
  }
  for(Button b:new Button[]{back,mic,measureList,resetZoom,legendEdit,objectTools})styleEditorButton(b,false);
  compactToolIcon(mic,"","mic");mic.setContentDescription("Sprachassistent");mic.setMinHeight(0);
  compactToolIcon(measureList,"Maße","ruler");compactToolIcon(resetZoom,"Zentrieren","center");compactToolIcon(legendEdit,"Legende","legend");compactToolIcon(objectTools,"Objekte","objects");
  styleEditorButton(save,true);compactToolIcon(save,"Speichern","save");LinearLayout.LayoutParams saveParams=new LinearLayout.LayoutParams(dp(88),dp(54));saveParams.setMargins(dp(6),0,dp(4),0);tools.addView(save,saveParams);compactToolIcon(back,"","back");back.setContentDescription("Vollbild beenden");
  Button drawingTab=new Button(this),profileTab=new Button(this);styleEditorButton(drawingTab,true);styleEditorButton(profileTab,false);compactToolIcon(drawingTab,"Zeichnungsebene","view");compactToolIcon(profileTab,"Regelprofil","layers");LinearLayout viewTabs=new LinearLayout(this);viewTabs.setPadding(dp(8),0,dp(8),dp(4));root.addView(viewTabs,1,new LinearLayout.LayoutParams(-1,dp(48)));for(Button tab:new Button[]{drawingTab,profileTab}){android.graphics.drawable.Drawable icon=tab.getCompoundDrawables()[1];tab.setCompoundDrawables(icon,null,null,null);tab.setMinHeight(0);tab.setMinimumHeight(0);tab.setTextSize(12);tab.setPadding(dp(8),0,dp(8),0);}viewTabs.addView(drawingTab,new LinearLayout.LayoutParams(0,dp(40),1));viewTabs.addView(profileTab,new LinearLayout.LayoutParams(0,dp(40),1));
  Button canvasToggle=new Button(this);canvasToggle.setText("⛶");canvasToggle.setContentDescription("Zeichenfläche maximieren");styleEditorButton(canvasToggle,false);
  compactToolIcon(canvasToggle,"","view");canvasToggle.setContentDescription("Zeichenfläche maximieren");header.addView(canvasToggle,new LinearLayout.LayoutParams(dp(44),dp(48)));
  android.widget.FrameLayout canvasFrame=new android.widget.FrameLayout(this);
  int canvasIndex=root.indexOfChild(route);root.removeView(route);root.addView(canvasFrame,canvasIndex,new LinearLayout.LayoutParams(-1,0,1));
  canvasFrame.addView(route,new android.widget.FrameLayout.LayoutParams(-1,-1));
  LinearLayout layerPanel=new LinearLayout(this);layerPanel.setOrientation(1);layerPanel.setPadding(dp(8),dp(4),dp(8),dp(4));layerPanel.setBackground(editorShape(0xff151b1b,8));
  layerPanel.addView(sheetText("Ebenen",14,true));
  ScrollView layerScroll=new ScrollView(this);LinearLayout layerChoices=new LinearLayout(this);layerChoices.setOrientation(1);layerScroll.addView(layerChoices);layerPanel.addView(layerScroll,new LinearLayout.LayoutParams(-1,0,1));
  String[] layerNames={"Trasse","Bemaßung","Punktnamen","GPS","Fotos","Objekte","Oberflächen","Oberflächenmaße","GPS-Spur","GPS-Abweichung","GPX-Referenz","Schachtscheine"};
  int[] layerMasks={RouteSketch.LAYER_ROUTE,RouteSketch.LAYER_DIMENSIONS,RouteSketch.LAYER_NAMES,RouteSketch.LAYER_GPS,RouteSketch.LAYER_PHOTOS,RouteSketch.LAYER_OBJECTS,RouteSketch.LAYER_SURFACES,RouteSketch.LAYER_SURFACE_DIMENSIONS,RouteSketch.LAYER_GPS_TRACK,RouteSketch.LAYER_GPS_DEVIATIONS,RouteSketch.LAYER_GPS_REFERENCE,RouteSketch.LAYER_SCHACHTSCHEINE};
  for(int k=0;k<layerNames.length;k++){final int mask=layerMasks[k];CheckBox check=new CheckBox(this);check.setText(layerNames[k]);check.setTextSize(12);check.setTextColor(Color.WHITE);check.setButtonTintList(android.content.res.ColorStateList.valueOf(TEAL));check.setChecked((drawingLayers()&mask)!=0);layerChoices.addView(check,new LinearLayout.LayoutParams(-1,dp(36)));check.setOnCheckedChangeListener((b,on)->{int current=drawingLayers();int next=on?current|mask:current&~mask;if(applyDrawingLayers(next))route.setVisibleLayers(next);});}
  CheckBox templateVisible=new CheckBox(this);templateVisible.setText("Unterlage PDF/Foto");templateVisible.setTextColor(Color.WHITE);templateVisible.setTextSize(12);templateVisible.setButtonTintList(android.content.res.ColorStateList.valueOf(TEAL));templateVisible.setChecked(getPreferences(0).getInt("routeUnderlayHidden",0)==0);layerChoices.addView(templateVisible);
  TextView alphaLabel=sheetText("Deckkraft",12,false);layerChoices.addView(alphaLabel);
  SeekBar layerOpacity=new SeekBar(this);layerOpacity.setMax(255);layerOpacity.setProgress(Math.max(0,Math.min(255,getPreferences(0).getInt("routeUnderlayAlpha",150))));layerChoices.addView(layerOpacity);
  alphaLabel.setText("Deckkraft "+Math.round(layerOpacity.getProgress()*100f/255)+" %");
  templateVisible.setOnCheckedChangeListener((b,on)->{route.setUnderlayVisible(on);getPreferences(0).edit().putInt("routeUnderlayHidden",on?0:1).apply();});
  layerOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int p,boolean user){alphaLabel.setText("Deckkraft "+Math.round(p*100f/255)+" %");if(user){route.setUnderlayAlpha(p);getPreferences(0).edit().putInt("routeUnderlayAlpha",p).apply();}}});
  overviewAction(layerPanel,"Schachtscheine","map",()->showUtilitySheets());
  android.widget.FrameLayout.LayoutParams layerParams=new android.widget.FrameLayout.LayoutParams(dp(160),-1,Gravity.RIGHT|Gravity.TOP);canvasFrame.addView(layerPanel,layerParams);layerPanel.setVisibility(View.GONE);
  layersButton.setOnClickListener(v->{if(layerPanel.getVisibility()==View.VISIBLE){layerPanel.setVisibility(View.GONE);return;}for(int k=0;k<layerMasks.length;k++)((CheckBox)layerChoices.getChildAt(k)).setChecked((drawingLayers()&layerMasks[k])!=0);boolean hasTemplate=!getPreferences(0).getString("routeUnderlay","").isEmpty();templateVisible.setEnabled(hasTemplate);layerOpacity.setEnabled(hasTemplate);templateVisible.setChecked(getPreferences(0).getInt("routeUnderlayHidden",0)==0);layerOpacity.setProgress(Math.max(0,Math.min(255,getPreferences(0).getInt("routeUnderlayAlpha",150))));if(layerPanel.getParent()==canvasFrame)layerPanel.bringToFront();layerPanel.setVisibility(View.VISIBLE);});

  LinearLayout workspace=new LinearLayout(this);workspace.setOrientation(LinearLayout.VERTICAL);root.removeView(canvasFrame);root.removeView(sectionStrip);root.addView(workspace,canvasIndex,new LinearLayout.LayoutParams(-1,0,1));workspace.addView(canvasFrame,new LinearLayout.LayoutParams(-1,0,1));workspace.addView(sectionStrip,new LinearLayout.LayoutParams(-1,-2));
  final boolean[] landscapeSections={false};
  root.addOnLayoutChangeListener((v,l,t,rr,bb,ol,ot,orr,ob)->{boolean landscape=rr-l>bb-t;if(landscape==landscapeSections[0])return;landscapeSections[0]=landscape;
   if(tools.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)tools.getParent()).removeView(tools);
   tools.setOrientation(landscape?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
   for(int k=0;k<tools.getChildCount();k++){View tool=tools.getChildAt(k);if(tool instanceof Button){Button button=(Button)tool;button.setMinHeight(landscape?0:dp(52));button.setMinimumHeight(landscape?0:dp(52));button.setPadding(dp(4),dp(2),dp(4),dp(2));android.graphics.drawable.Drawable icon=button.getCompoundDrawables()[1];if(landscape&&icon!=null){button.setCompoundDrawables(icon,null,null,null);button.setCompoundDrawablePadding(dp(4));}else if(!landscape){icon=button.getCompoundDrawables()[0];if(icon!=null)button.setCompoundDrawables(null,icon,null,null);}button.setSingleLine(true);button.setTextSize(landscape?10:12);}LinearLayout.LayoutParams lp=landscape?new LinearLayout.LayoutParams(-1,0,1):tool==save?new LinearLayout.LayoutParams(dp(88),dp(54)):new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(2),dp(1),dp(2),dp(1));tool.setLayoutParams(lp);}
   if(landscape)workspace.addView(tools,0,new LinearLayout.LayoutParams(dp(88),-1));else root.addView(tools,new LinearLayout.LayoutParams(-1,-2));
   workspace.setOrientation(landscape?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);canvasFrame.setLayoutParams(landscape?new LinearLayout.LayoutParams(0,-1,1):new LinearLayout.LayoutParams(-1,0,1));sectionStrip.setLayoutParams(landscape?new LinearLayout.LayoutParams(dp(140),-1):new LinearLayout.LayoutParams(-1,-2));workspace.removeView(sectionStrip);workspace.addView(sectionStrip,landscape?workspace.getChildCount():1);if(layerPanel.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)layerPanel.getParent()).removeView(layerPanel);if(landscape){workspace.addView(layerPanel,new LinearLayout.LayoutParams(dp(160),-1));}else{canvasFrame.addView(layerPanel,new android.widget.FrameLayout.LayoutParams(dp(160),-1,Gravity.RIGHT|Gravity.TOP));}if(landscape){sectionScroll.removeView(sectionCards);sectionStrip.removeView(sectionScroll);sectionCards.setOrientation(LinearLayout.VERTICAL);verticalSections.addView(sectionCards);sectionStrip.addView(verticalSections,new LinearLayout.LayoutParams(-1,0,1));}else{verticalSections.removeView(sectionCards);sectionStrip.removeView(verticalSections);sectionCards.setOrientation(LinearLayout.HORIZONTAL);sectionScroll.addView(sectionCards);sectionStrip.addView(sectionScroll);}sectionScroll.setVisibility(sectionsCollapsed[0]?View.GONE:View.VISIBLE);verticalSections.setVisibility(sectionsCollapsed[0]?View.GONE:View.VISIBLE);refreshSections.run();});
  LinearLayout floatingProfile=new LinearLayout(this);floatingProfile.setOrientation(1);floatingProfile.setPadding(dp(6),dp(4),dp(6),dp(4));floatingProfile.setBackground(editorShape(0xff151b1a,10));
  LinearLayout floatingHeader=new LinearLayout(this);floatingHeader.setGravity(Gravity.CENTER_VERTICAL);floatingProfile.addView(floatingHeader);
  TextView floatingTitle=sheetText("Regelprofil",12,true);floatingTitle.setTextColor(Color.WHITE);floatingHeader.addView(floatingTitle,new LinearLayout.LayoutParams(0,dp(30),1));
  Button collapseProfile=new Button(this);styleEditorButton(collapseProfile,false);collapseProfile.setText("−");floatingHeader.addView(collapseProfile,new LinearLayout.LayoutParams(dp(32),dp(30)));
  TextView profilePlaceholder=sheetText("Abschnitt auswählen",12,false);profilePlaceholder.setTextColor(0xffaeb9b8);floatingProfile.addView(profilePlaceholder,new LinearLayout.LayoutParams(-1,dp(36)));
  PipeLayoutView floatingCross=new PipeLayoutView(this);floatingCross.setEnabled(false);floatingCross.setVisibility(View.GONE);floatingProfile.addView(floatingCross,new LinearLayout.LayoutParams(-1,dp(140)));
  android.content.SharedPreferences baseProfilePrefs=getSharedPreferences("base_profile_defaults",MODE_PRIVATE);
  java.util.function.Consumer<PipeLayoutView> showBaseCross=cross->{
   double w=baseProfilePrefs.getFloat("width",0.4f),d=baseProfilePrefs.getFloat("depth",1.0f);
   try{double v=number(width);if(Double.isFinite(v)&&v>0)w=v;}catch(Exception ignored){}
   try{double v=number(depth);if(Double.isFinite(v)&&v>0)d=v;}catch(Exception ignored){}
   java.util.List<PipeLayout.Pipe> pipes=new java.util.ArrayList<>();
   try{pipes=new PipeLayoutStore(getPreferences(0)).load();}catch(Exception ignored){}
   cross.setLayoutData(pipes,w,d);cross.setAsphaltProfile("Asphalt".equals(covering));
   cross.setCivilProfile(0,0,0,0,route.configuredSurfaceColor(covering,route.legendSettings()));
   cross.setVisibility(View.VISIBLE);
  };
  Runnable updateFloatingCross=()->{
   showBaseCross.accept(floatingCross);profilePlaceholder.setVisibility(View.GONE);
   if(selectedSection[0]<0||selectedSection[0]+1>=route.vertices.size())return;
   double from=0;for(int j=0;j<selectedSection[0];j++)from+=route.vertices.get(j+1).metres;
   double to=from+route.vertices.get(selectedSection[0]+1).metres;
   if(!Double.isFinite(from)||!Double.isFinite(to)||to<=from)return;
   try{
    org.json.JSONArray rows=new PipeLayoutStore(getPreferences(0)).sections();org.json.JSONObject match=null;
    for(int j=0;j<rows.length();j++){org.json.JSONObject p=rows.getJSONObject(j);if(Math.abs(p.optDouble("from")-from)<0.001&&Math.abs(p.optDouble("to")-to)<0.001){match=p;break;}}
    if(match==null)return;
    double ww=match.optDouble("width"),dd=match.optDouble("depth");
    if(!Double.isFinite(ww)||!Double.isFinite(dd)||ww<=0||dd<=0){profilePlaceholder.setText("Profilmaße fehlen");return;}
    floatingCross.setLayoutData(PipeLayoutStore.decode(match.getJSONArray("pipes").toString()),ww,dd);
    org.json.JSONObject seg=findSurfaceSection(match);
    String type=seg==null?covering:seg.optString("surface",covering);
    floatingCross.setAsphaltProfile("Asphalt".equals(type));
    floatingCross.setCivilProfile(seg==null?0:seg.optDouble("left",0),seg==null?0:seg.optDouble("right",0),seg==null?0:seg.optDouble("topThickness",0),seg==null?0:seg.optDouble("baseThickness",0),route.configuredSurfaceColor(type,route.legendSettings()));

    profilePlaceholder.setVisibility(View.GONE);floatingCross.setVisibility(View.VISIBLE);
   }catch(Exception ex){profilePlaceholder.setText("Profil nicht verfügbar");}
  };

  Button resizeProfile=new Button(this);styleEditorButton(resizeProfile,false);resizeProfile.setText("");resizeProfile.setCompoundDrawablesWithIntrinsicBounds(null,new android.graphics.drawable.Drawable(){final android.graphics.Paint p=new android.graphics.Paint(3);public void draw(android.graphics.Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);p.setColor(Color.WHITE);p.setStrokeWidth(1.7f);p.setStrokeCap(android.graphics.Paint.Cap.ROUND);c.drawLine(5,19,19,5,p);c.drawLine(5,19,5,12,p);c.drawLine(5,19,12,19,p);c.drawLine(19,5,12,5,p);c.drawLine(19,5,19,12,p);c.restore();}public int getIntrinsicWidth(){return dp(20);}public int getIntrinsicHeight(){return dp(20);}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(android.graphics.ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}},null,null);resizeProfile.setPadding(dp(8),dp(10),dp(8),dp(4));resizeProfile.setContentDescription("Regelprofilgröße ändern");LinearLayout.LayoutParams resizeParams=new LinearLayout.LayoutParams(dp(32),dp(32));resizeParams.gravity=Gravity.RIGHT;floatingProfile.addView(resizeProfile,resizeParams);
  android.content.SharedPreferences floatingPrefs=getSharedPreferences("floating_profile_ui",MODE_PRIVATE);
  int savedWidth=Math.max(dp(120),Math.min(getResources().getDisplayMetrics().widthPixels,floatingPrefs.getInt("width",dp(140))));
  canvasFrame.addView(floatingProfile,new android.widget.FrameLayout.LayoutParams(savedWidth,-2,Gravity.TOP|Gravity.LEFT));
  android.view.ViewGroup.LayoutParams savedCrossParams=floatingCross.getLayoutParams();savedCrossParams.height=Math.max(dp(90),Math.min(dp(360),Math.round(savedWidth*0.78f)));floatingCross.setLayoutParams(savedCrossParams);
  canvasFrame.post(()->{int maxX=Math.max(0,canvasFrame.getWidth()-floatingProfile.getWidth()),maxY=Math.max(0,canvasFrame.getHeight()-floatingProfile.getHeight());floatingProfile.setX(maxX*Math.max(0f,Math.min(1f,floatingPrefs.getFloat("x",1f))));floatingProfile.setY(maxY*Math.max(0f,Math.min(1f,floatingPrefs.getFloat("y",1f))));});
  floatingTitle.setOnTouchListener(new View.OnTouchListener(){float x,y,bx,by;public boolean onTouch(View v,android.view.MotionEvent e){switch(e.getActionMasked()){case android.view.MotionEvent.ACTION_DOWN:x=e.getRawX();y=e.getRawY();bx=floatingProfile.getX();by=floatingProfile.getY();return true;case android.view.MotionEvent.ACTION_MOVE:floatingProfile.setX(Math.max(0,Math.min(canvasFrame.getWidth()-floatingProfile.getWidth(),bx+e.getRawX()-x)));floatingProfile.setY(Math.max(0,Math.min(canvasFrame.getHeight()-floatingProfile.getHeight(),by+e.getRawY()-y)));return true;default:return true;}}});
  resizeProfile.setOnTouchListener(new View.OnTouchListener(){float x;int initial;public boolean onTouch(View v,android.view.MotionEvent e){switch(e.getActionMasked()){case android.view.MotionEvent.ACTION_DOWN:x=e.getRawX();initial=floatingProfile.getWidth();return true;case android.view.MotionEvent.ACTION_MOVE:android.view.ViewGroup.LayoutParams lp=floatingProfile.getLayoutParams();lp.width=Math.max(dp(120),Math.min(canvasFrame.getWidth(),initial+(int)(e.getRawX()-x)));floatingProfile.setLayoutParams(lp);android.view.ViewGroup.LayoutParams crossParams=floatingCross.getLayoutParams();crossParams.height=Math.max(dp(90),Math.min(dp(360),Math.round(lp.width*0.78f)));floatingCross.setLayoutParams(crossParams);return true;default:return true;}}});
  android.view.ScaleGestureDetector floatingScale=new android.view.ScaleGestureDetector(this,new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener(){
   public boolean onScale(android.view.ScaleGestureDetector detector){
    android.view.ViewGroup.LayoutParams lp=floatingProfile.getLayoutParams();
    lp.width=Math.max(dp(120),Math.min(canvasFrame.getWidth(),Math.round(lp.width*detector.getScaleFactor())));
    floatingProfile.setLayoutParams(lp);
    android.view.ViewGroup.LayoutParams cp=floatingCross.getLayoutParams();cp.height=Math.max(dp(90),Math.min(dp(360),Math.round(lp.width*0.78f)));floatingCross.setLayoutParams(cp);return true;
   }
  });
  floatingCross.setEnabled(true);floatingCross.setOnTouchListener((v,e)->{floatingScale.onTouchEvent(e);return true;});
  canvasFrame.addOnLayoutChangeListener((v,l,t,rr,bb,ol,ot,orr,ob)->{
   int availableWidth=rr-l,availableHeight=bb-t;if(availableWidth<=0||availableHeight<=0)return;
   android.view.ViewGroup.LayoutParams lp=floatingProfile.getLayoutParams();
   int maxWidth=Math.max(1,Math.min(availableWidth,Math.max(dp(120),availableWidth/2)));
   int width=Math.min(lp.width,maxWidth);if(lp.width!=width){lp.width=width;floatingProfile.setLayoutParams(lp);}
   android.view.ViewGroup.LayoutParams cp=floatingCross.getLayoutParams();
   int height=Math.max(1,Math.min(Math.max(dp(90),Math.min(dp(360),Math.round(width*.78f))),availableHeight-dp(48)));
   if(cp.height!=height){cp.height=height;floatingCross.setLayoutParams(cp);}
   floatingProfile.setX(Math.max(0,Math.min(availableWidth-width,floatingProfile.getX())));
   floatingProfile.setY(Math.max(0,Math.min(availableHeight-floatingProfile.getHeight(),floatingProfile.getY())));
  });
  floatingProfile.addOnLayoutChangeListener((v,ll,tt,rr,bb,ol,ot,orr,ob)->{floatingProfile.setX(Math.max(0,Math.min(canvasFrame.getWidth()-floatingProfile.getWidth(),floatingProfile.getX())));floatingProfile.setY(Math.max(0,Math.min(canvasFrame.getHeight()-floatingProfile.getHeight(),floatingProfile.getY())));});
  collapseProfile.setOnClickListener(v->{boolean hide=resizeProfile.getVisibility()==View.VISIBLE;if(hide){profilePlaceholder.setVisibility(View.GONE);floatingCross.setVisibility(View.GONE);}else updateFloatingCross.run();resizeProfile.setVisibility(hide?View.GONE:View.VISIBLE);collapseProfile.setText(hide?"+":"−");});
  sectionPosition.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){floatingTitle.setText(selectedSection[0]<0?"Regelprofil":String.format(java.util.Locale.GERMANY,"Regelprofil · A%02d",selectedSection[0]+1));if(resizeProfile.getVisibility()==View.VISIBLE)updateFloatingCross.run();}public void afterTextChanged(android.text.Editable e){}});

  LinearLayout profilePage=new LinearLayout(this);profilePage.setOrientation(1);profilePage.setBackgroundColor(Color.BLACK);profilePage.setVisibility(View.GONE);
  TextView profilePageTitle=sheetText("Regelprofil",16,true);profilePageTitle.setTextColor(Color.WHITE);profilePage.addView(profilePageTitle);
  LinearLayout profileWork=new LinearLayout(this);profileWork.setOrientation(0);profilePage.addView(profileWork,new LinearLayout.LayoutParams(-1,0,1));
  PipeLayoutView fullCross=new PipeLayoutView(this);profileWork.addView(fullCross,new LinearLayout.LayoutParams(0,-1,3));
  LinearLayout profileInfo=new LinearLayout(this);profileInfo.setOrientation(1);profileInfo.setPadding(dp(8),dp(8),dp(8),dp(8));profileInfo.setBackgroundColor(0xff101c1e);
  ScrollView profileInfoScroll=new ScrollView(this);profileInfoScroll.addView(profileInfo);profileWork.addView(profileInfoScroll,new LinearLayout.LayoutParams(0,-1,2));
  profileWork.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
   if(right<=left||bottom<=top)return;
   boolean wide=right-left>=dp(560);
   int orientation=wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL;
   if(profileWork.getOrientation()==orientation)return;
   profileWork.setOrientation(orientation);
   fullCross.setLayoutParams(wide?new LinearLayout.LayoutParams(0,-1,3):new LinearLayout.LayoutParams(-1,0,3));
   profileInfoScroll.setLayoutParams(wide?new LinearLayout.LayoutParams(0,-1,2):new LinearLayout.LayoutParams(-1,0,2));
  });
  TextView profileDimensions=sheetText("",14,true);profileDimensions.setTextColor(0xff28d5c7);profileInfo.addView(sheetText("Profilparameter",14,true));profileInfo.addView(profileDimensions);
  TextView profileMaterial=sheetText("",12,false);profileMaterial.setTextColor(0xffcbd6d5);profileInfo.addView(profileMaterial);
  Button profileEdit=new Button(this);profileEdit.setText("Maße bearbeiten");styleEditorButton(profileEdit,false);profileInfo.addView(profileEdit);
  Button editSectionProfile=new Button(this);styleEditorButton(editSectionProfile,false);compactToolIcon(editSectionProfile,"Abschnitt bearbeiten","settings");LinearLayout profileActions=new LinearLayout(this);profilePage.addView(profileActions,new LinearLayout.LayoutParams(-1,dp(40)));profileActions.addView(editSectionProfile,new LinearLayout.LayoutParams(0,-1,1));editSectionProfile.setOnClickListener(v->{if(sectionProfilePanel.getParent()!=null)return;openSectionProfilePicker();showProfilePanel("Abschnitte",sectionProfilePanel);});
  canvasFrame.addView(profilePage,new android.widget.FrameLayout.LayoutParams(-1,-1));
  Runnable refreshFullProfile=()->{profilePageTitle.setText(selectedSection[0]<0?"Regelprofil · Standardprofil":"Regelprofil · "+sectionPosition.getText());showBaseCross.accept(fullCross);profileDimensions.setText(fullCross.dimensionSummary());profileMaterial.setText("Oberfläche: "+covering);if(selectedSection[0]<0||selectedSection[0]+1>=route.vertices.size())return;double start=0;for(int j=0;j<selectedSection[0];j++)start+=route.vertices.get(j+1).metres;double end=start+route.vertices.get(selectedSection[0]+1).metres;try{org.json.JSONArray rows=new PipeLayoutStore(getPreferences(0)).sections();for(int j=0;j<rows.length();j++){org.json.JSONObject p=rows.getJSONObject(j);if(Math.abs(p.optDouble("from")-start)>0.001||Math.abs(p.optDouble("to")-end)>0.001)continue;double w=p.optDouble("width"),d=p.optDouble("depth");if(w>0&&d>0){fullCross.setLayoutData(PipeLayoutStore.decode(p.getJSONArray("pipes").toString()),w,d);fullCross.setVisibility(View.VISIBLE);profileDimensions.setText(fullCross.dimensionSummary());}break;}}catch(Exception ignored){}};
  Button baseSettings=new Button(this);styleEditorButton(baseSettings,false);compactToolIcon(baseSettings,"Standard-Profil verwalten","settings");
  profileActions.addView(baseSettings,new LinearLayout.LayoutParams(0,-1,1));for(Button b:new Button[]{editSectionProfile,baseSettings}){android.graphics.drawable.Drawable icon=b.getCompoundDrawables()[1];b.setCompoundDrawables(icon,null,null,null);b.setMinHeight(0);b.setMinimumHeight(0);b.setTextSize(12);b.setPadding(dp(6),0,dp(6),0);}
  baseSettings.setOnClickListener(v->{
   LinearLayout fields=new LinearLayout(this);fields.setOrientation(1);fields.setPadding(dp(20),dp(8),dp(20),dp(8));
   EditText bw=new EditText(this),bd=new EditText(this);
   bw.setInputType(8194);bd.setInputType(8194);
   bw.setText(String.format(java.util.Locale.GERMANY,"%.2f",baseProfilePrefs.getFloat("width",0.4f)));
   bd.setText(String.format(java.util.Locale.GERMANY,"%.2f",baseProfilePrefs.getFloat("depth",1.0f)));
   fields.addView(sheetText("Grabenbreite (m)",14,false));fields.addView(bw);
   fields.addView(sheetText("Grabentiefe (m)",14,false));fields.addView(bd);
   fields.addView(sheetText("Vorgabe für fehlende Profilmaße. Gespeicherte Maße bleiben unverändert.",12,false));
   android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Standardprofil").setView(fields).setNegativeButton("Abbrechen",null).setNeutralButton("Zurücksetzen",null).setPositiveButton("Speichern",null).create();
   dialog.show();
   dialog.getButton(-3).setOnClickListener(x->{bw.setText("0,40");bd.setText("1,00");});
   dialog.getButton(-1).setOnClickListener(x->{try{
    double w=number(bw),d=number(bd);
    if(!Double.isFinite(w)||!Double.isFinite(d)||w<=0||d<=0||w>Float.MAX_VALUE||d>Float.MAX_VALUE)throw new IllegalArgumentException();
    baseProfilePrefs.edit().putFloat("width",(float)w).putFloat("depth",(float)d).apply();
    updateFloatingCross.run();refreshFullProfile.run();dialog.dismiss();
   }catch(Exception ex){bw.setError("Positive Maße eingeben");bd.setError("Positive Maße eingeben");}});
  });
  profileEdit.setOnClickListener(v->{if(selectedSection[0]>=0)editSectionProfile.performClick();else baseSettings.performClick();});
  drawingTab.setOnClickListener(v->{profilePage.setVisibility(View.GONE);route.setVisibility(View.VISIBLE);floatingProfile.setVisibility(View.VISIBLE);controls.setVisibility(View.VISIBLE);historyBar.setVisibility(View.VISIBLE);tools.setVisibility(View.VISIBLE);styleEditorButton(drawingTab,true);styleEditorButton(profileTab,false);});
  profileTab.setOnClickListener(v->{route.setVisibility(View.GONE);floatingProfile.setVisibility(View.GONE);controls.setVisibility(View.GONE);historyBar.setVisibility(View.GONE);tools.setVisibility(View.GONE);profilePage.setVisibility(View.VISIBLE);styleEditorButton(drawingTab,false);styleEditorButton(profileTab,true);refreshFullProfile.run();});
  sectionPosition.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){if(profilePage.getVisibility()==View.VISIBLE)refreshFullProfile.run();}public void afterTextChanged(android.text.Editable e){}});
  Button restoreTools=new Button(this);restoreTools.setText("☰");restoreTools.setContentDescription("Werkzeuge anzeigen");styleEditorButton(restoreTools,false);
  android.widget.FrameLayout.LayoutParams floating=new android.widget.FrameLayout.LayoutParams(dp(44),dp(44),Gravity.TOP|Gravity.RIGHT);floating.setMargins(dp(4),dp(4),dp(4),dp(4));canvasFrame.addView(restoreTools,floating);restoreTools.setVisibility(View.GONE);
  Button floatingSave=new Button(this);floatingSave.setAllCaps(false);floatingSave.setText("Speichern");styleEditorButton(floatingSave,true);compactToolIcon(floatingSave,"Speichern","save");
  android.widget.FrameLayout.LayoutParams floatingSaveParams=new android.widget.FrameLayout.LayoutParams(dp(88),dp(54),Gravity.BOTTOM|Gravity.RIGHT);floatingSaveParams.setMargins(dp(8),dp(8),dp(8),dp(8));canvasFrame.addView(floatingSave,floatingSaveParams);floatingSave.setVisibility(View.GONE);
  floatingSave.setOnClickListener(v->save.performClick());
  canvasToggle.setOnClickListener(v->{immersive[0]=true;if(full.getWindow()!=null)androidx.core.view.WindowCompat.getInsetsController(full.getWindow(),root).hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());layerPanel.setVisibility(View.GONE);for(View bar:new View[]{header,viewTabs,controls,historyBar,tools,save,sectionStrip})bar.setVisibility(View.GONE);restoreTools.setVisibility(View.VISIBLE);floatingSave.setVisibility(View.VISIBLE);});
  restoreTools.setOnTouchListener(new View.OnTouchListener(){
   float startX,startY,baseX,baseY;boolean moved;
   public boolean onTouch(View v,android.view.MotionEvent e){
    switch(e.getActionMasked()){
     case android.view.MotionEvent.ACTION_DOWN:startX=e.getRawX();startY=e.getRawY();baseX=v.getX();baseY=v.getY();moved=false;return true;
     case android.view.MotionEvent.ACTION_MOVE:
      float dx=e.getRawX()-startX,dy=e.getRawY()-startY;
      if(Math.hypot(dx,dy)>android.view.ViewConfiguration.get(MainActivity.this).getScaledTouchSlop())moved=true;
      if(moved){v.setX(Math.max(0,Math.min(canvasFrame.getWidth()-v.getWidth(),baseX+dx)));v.setY(Math.max(0,Math.min(canvasFrame.getHeight()-v.getHeight(),baseY+dy)));}return true;
     case android.view.MotionEvent.ACTION_UP:if(!moved)v.performClick();return true;
     case android.view.MotionEvent.ACTION_CANCEL:return true;
    }return true;
   }
  });
  canvasFrame.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(restoreTools.getVisibility()==View.VISIBLE){restoreTools.setX(Math.max(0,Math.min(canvasFrame.getWidth()-restoreTools.getWidth(),restoreTools.getX())));restoreTools.setY(Math.max(0,Math.min(canvasFrame.getHeight()-restoreTools.getHeight(),restoreTools.getY())));}});
  restoreTools.setOnClickListener(v->{immersive[0]=immersiveInitially;if(!immersive[0]&&full.getWindow()!=null)androidx.core.view.WindowCompat.getInsetsController(full.getWindow(),root).show(androidx.core.view.WindowInsetsCompat.Type.systemBars());for(View bar:new View[]{header,viewTabs,controls,historyBar,tools,save,sectionStrip})bar.setVisibility(View.VISIBLE);restoreTools.setVisibility(View.GONE);floatingSave.setVisibility(View.GONE);});
  full.setContentView(root);
  full.setOnDismissListener(d->{
   int maxX=Math.max(0,canvasFrame.getWidth()-floatingProfile.getWidth()),maxY=Math.max(0,canvasFrame.getHeight()-floatingProfile.getHeight());
   floatingPrefs.edit().putInt("width",floatingProfile.getLayoutParams().width).putFloat("x",maxX>0?floatingProfile.getX()/maxX:0f).putFloat("y",maxY>0?floatingProfile.getY()/maxY:0f).apply();
   pendingEditorSpeech=null;route.darkEditor=false;route.changed=originalRouteChanged;
   setRequestedOrientation(previousOrientation);
   for(View view:views){android.view.ViewParent parent=view.getParent();if(parent instanceof android.view.ViewGroup)((android.view.ViewGroup)parent).removeView(view);}
   // Original positions are ascending; restore before other controls.
   for(int j=0;j<views.length;j++)form.addView(views[j],Math.min(positions[j],form.getChildCount()),params[j]);
   route.invalidate();
  });
  back.setOnClickListener(v->{full.dismiss();if(editor.isShowing())editor.dismiss();});
  save.setOnClickListener(v->{
   if(editor.isShowing())editor.getButton(-1).performClick();
   if(!editor.isShowing())full.dismiss();
  });
  android.view.Window window=full.getWindow();
  if(window!=null){
   androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false);
   androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root,(view,insets)->{
    androidx.core.graphics.Insets safe=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout()|androidx.core.view.WindowInsetsCompat.Type.systemBars()|androidx.core.view.WindowInsetsCompat.Type.ime());
    view.setPadding(safe.left,safe.top,safe.right,safe.bottom);return insets;
   });
  }
  full.show();
  // Preserve the current orientation on entry; the orientation selector controls subsequent changes.
  if(window!=null){window.setLayout(-1,-1);androidx.core.view.WindowInsetsControllerCompat c=androidx.core.view.WindowCompat.getInsetsController(window,root);c.setSystemBarsBehavior(androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);if(immersive[0])c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());else c.show(androidx.core.view.WindowInsetsCompat.Type.systemBars());androidx.core.view.ViewCompat.requestApplyInsets(root);}
 }
 void deleteRoutePoint(RouteSketch route){
  if(route.vertices.size()<3){notice("Mindestens drei Punkte erforderlich.");return;}
  String[] labels=new String[route.vertices.size()-2];
  for(int i=1;i<route.vertices.size()-1;i++){RouteSketch.Vertex p=route.vertices.get(i);labels[i-1]="Punkt "+(i+1)+(p.name.isEmpty()?"":" · "+p.name);}
  new android.app.AlertDialog.Builder(this).setTitle("Punkt löschen und Trasse verbinden").setItems(labels,(d,which)->{
   confirmDeleteRoutePoint(route,which+1);
  }).setNegativeButton("Abbrechen",null).show();
 }
 void confirmDeleteRoutePoint(RouteSketch route,int index){
  if(index<=0||index>=route.vertices.size()-1){notice("Nur Zwischenpunkte können verbunden werden.");return;}
  RouteSketch.Vertex p=route.vertices.get(index);
  new android.app.AlertDialog.Builder(this).setTitle("Punkt "+(index+1)+" löschen?")
   .setMessage("Die Nachbarpunkte werden direkt verbunden. Die beiden bisherigen Abschnittsmaße werden verworfen; das neue Maß muss eingetragen werden. Punktname, GPS-Daten und Fotozuordnungen dieses Punktes entfallen. Rückgängig bleibt möglich.")
   .setNegativeButton("Abbrechen",null).setPositiveButton("Löschen und verbinden",(a,b)->{
    if(index>=route.vertices.size()-1||route.vertices.get(index)!=p)return;
    route.vertices.remove(index);route.vertices.get(index).metres=Double.NaN;
    route.highlight=-1;route.dragged=-1;if(route.changed!=null)route.changed.run();route.invalidate();
   }).show();
 }
 void editRoute(boolean measure){editRoute(measure,false);}
 void editRoute(boolean measure,boolean startFullscreen){
  LinearLayout form=page();
  ScrollView scroll=new ScrollView(this);scroll.addView(form);
  RouteSketch route=new RouteSketch(this,routeData());route.editable=true;route.darkPreview=true;route.bindDimensionProject(activeProject);route.setIndependentGpsTrack(gpsTrack());route.setImportedGpsReference(importedGpsReference());route.setVisibleLayers(drawingLayers());loadUtilitySheets(route);
  TextView hint=sheetText("Trasse mit dem Finger zeichnen. Loslassen zum Bearbeiten.",15,false);
  form.addView(hint);
  Button fullscreen=new Button(this);fullscreen.setAllCaps(false);fullscreen.setText("⛶ Vollbild");form.addView(fullscreen);
  form.addView(route,new LinearLayout.LayoutParams(-1,dp(260)));
  final org.json.JSONObject[] calibration={savedSketchCalibration()};
  final Runnable[] refreshCalibration={()->{}};
  final LinearLayout[] underlayControls={null};
  String template=getPreferences(0).getString("routeUnderlay","");
  if(!template.isEmpty()){
   android.graphics.Bitmap image=reportPhoto(template);
   if(image!=null){
    route.setUnderlay(image);
    LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);form.addView(panel);underlayControls[0]=panel;
    LinearLayout transform=fold(panel,"Unterlage ausrichten",false);
    CheckBox lock=new CheckBox(this);lock.setText("Unterlage sperren");lock.setChecked(true);transform.addView(lock);
    String transformKey="routeUnderlayTransform";
    float[] vals={0,0,1,0};
    try{org.json.JSONArray saved=new org.json.JSONArray(getPreferences(0).getString(transformKey,"[]"));for(int k=0;k<4;k++){double value=saved.optDouble(k,vals[k]);if(Double.isFinite(value))vals[k]=(float)value;}}catch(Exception ignored){}
    vals[0]=Math.max(-2,Math.min(2,vals[0]));vals[1]=Math.max(-2,Math.min(2,vals[1]));vals[2]=Math.max(.1f,Math.min(5,vals[2]));vals[3]=Math.max(-180,Math.min(180,vals[3]));
    route.setUnderlayTransform(vals[0],vals[1],vals[2],vals[3]);
    String[] names={"Horizontal verschieben","Vertikal verschieben","Maßstab der Unterlage","Drehung"};
    float[] mins={-2,-2,.1f,-180},maxs={2,2,5,180};
    java.util.ArrayList<SeekBar> bars=new java.util.ArrayList<>();
    for(int k=0;k<4;k++){final int index=k;TextView label=sheetText(names[k],14,false);transform.addView(label);SeekBar bar=new SeekBar(this);bar.setMax(1000);bar.setProgress(Math.round(1000*(vals[k]-mins[k])/(maxs[k]-mins[k])));bar.setEnabled(false);transform.addView(bar);bars.add(bar);
     bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){try{org.json.JSONArray data=new org.json.JSONArray();for(float value:vals)data.put((double)value);getPreferences(0).edit().putString(transformKey,data.toString()).remove("sketchCalibration").apply();calibration[0]=new org.json.JSONObject();refreshCalibration[0].run();}catch(Exception ignored){}}public void onProgressChanged(SeekBar b,int progress,boolean user){if(user&&!lock.isChecked()){vals[index]=mins[index]+(maxs[index]-mins[index])*progress/1000f;route.setUnderlayTransform(vals[0],vals[1],vals[2],vals[3]);}}});
    }
    lock.setOnCheckedChangeListener((b,checked)->{for(SeekBar bar:bars)bar.setEnabled(!checked);});

    android.widget.CheckBox showTemplate=new android.widget.CheckBox(this);showTemplate.setText("Zeichenvorlage anzeigen");showTemplate.setChecked(getPreferences(0).getInt("routeUnderlayHidden",0)==0);route.setUnderlayVisible(showTemplate.isChecked());panel.addView(showTemplate);
    showTemplate.setOnCheckedChangeListener((b,checked)->{route.setUnderlayVisible(checked);getPreferences(0).edit().putInt("routeUnderlayHidden",checked?0:1).apply();});
    panel.addView(sheetText("Deckkraft der Vorlage",13,false));
    android.widget.SeekBar opacity=new android.widget.SeekBar(this);opacity.setMax(255);opacity.setProgress(Math.max(0,Math.min(255,getPreferences(0).getInt("routeUnderlayAlpha",150))));route.setUnderlayAlpha(opacity.getProgress());panel.addView(opacity);
    opacity.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(android.widget.SeekBar bar,int progress,boolean user){route.setUnderlayAlpha(progress);if(user)getPreferences(0).edit().putInt("routeUnderlayAlpha",progress).apply();}public void onStartTrackingTouch(android.widget.SeekBar bar){}public void onStopTrackingTouch(android.widget.SeekBar bar){}});
    Button removeTemplate=new Button(this);removeTemplate.setAllCaps(false);removeTemplate.setText("Vorlage aus Projekt entfernen");panel.addView(removeTemplate);
    removeTemplate.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("Zeichenvorlage entfernen?").setMessage("Die gezeichnete Trasse und Maße bleiben erhalten.").setNegativeButton("Abbrechen",null).setPositiveButton("Entfernen",(d,w)->{
     if(template.equals(getPreferences(0).getString("routeUnderlay",""))&&getPreferences(0).edit().remove("routeUnderlay").remove("routeUnderlayTransform").remove("routeUnderlayAlpha").remove("routeUnderlayHidden").remove("sketchCalibration").commit()){route.setUnderlay(null);calibration[0]=new org.json.JSONObject();refreshCalibration[0].run();lock.setEnabled(false);for(SeekBar bar:bars)bar.setEnabled(false);showTemplate.setEnabled(false);opacity.setEnabled(false);removeTemplate.setEnabled(false);recordProjectEvent("Zeichenvorlage entfernt");}else notice("Vorlage konnte nicht entfernt werden.");
    }).show());
   }else form.addView(sheetText("Zeichenvorlage fehlt oder ist nicht lesbar.",13,false));
  }

  Spinner mode=choices(form,new String[]{"Zeichnen","Maß eintragen","Bearbeiten","Objekt platzieren","Objekt bearbeiten","Einzelobjekt ohne Trasse"});
  Spinner angles=choices(form,new String[]{"Freier Winkel","0°","15°","30°","45°","60°","75°","90°"});
  angles.setVisibility(View.GONE);
  onChoice(angles,index->route.angle=index==0?-1:(index-1)*15);
  onChoice(mode,index->{
   route.dimensioning=index==1;route.moving=index==2;route.placingObject=index==3;route.editingObjects=index==4;route.placingSingle=index==5;
   angles.setVisibility(route.moving?View.VISIBLE:View.GONE);
   hint.setText(new String[]{"Für ein Einzelobjekt einmal tippen. Für eine Trasse mit dem Finger zeichnen.","Abschnitt lange drücken. Freie Fläche ziehen zum Verschieben.","Punkte, Objekte und Maße ziehen. Freie Fläche verschiebt den Plan.","Trasse antippen, um ein Objekt an einer Station zu platzieren.","Objekt ziehen oder lange drücken für Einstellungen.","Position antippen und Objekttyp wählen."}[index]);
  });
  mode.setSelection(measure?1:route.vertices.isEmpty()&&route.singleObjects.isEmpty()?0:2);
  final String initialSaved=routeData();
  final String initialObjects=objects.toString();final org.json.JSONArray draftObjects=copyEditorObjects(initialObjects);
  Runnable refreshObjects=()->{route.setTrenchWidth(w);route.setStationLayers(draftObjects,segments);route.invalidate();};
  Runnable openObjects=()->showEditorObjects(route,draftObjects,refreshObjects);
  route.singleObjectSelected=(position,existing)->{
   String[] types={"Hausanschluss","Suchschachtung","Hindernis","Startgrube","Zielgrube","KVS","Kernbohrung","Knickpunkt"};
   java.util.function.Consumer<String> applyType=type->{
    if(existing==null)route.singleObjects.add(new RouteSketch.SingleObject(position,type));else existing.type=type;
    if(!route.layerVisible(RouteSketch.LAYER_OBJECTS))applyDrawingLayers(drawingLayers()|RouteSketch.LAYER_OBJECTS);
    route.changed.run();route.invalidate();mode.setSelection(2);
   };
   android.app.AlertDialog.Builder chooser=new android.app.AlertDialog.Builder(this)
    .setTitle(existing==null?"Einzelobjekt ohne Trasse":existing.type)
    .setItems(types,(dialog,index)->applyType.accept(types[index])).setNegativeButton("Abbrechen",null);
   if(existing!=null)chooser.setNeutralButton("Entfernen",(dialog,which)->{
    route.singleObjects.remove(existing);route.changed.run();route.invalidate();
   });
   else chooser.setNeutralButton("Spracheingabe",null);
   android.app.AlertDialog picker=chooser.show();
   if(existing==null)picker.getButton(-3).setOnClickListener(v->startEditorDictation(speech->{
    if(activeRouteEditor!=route||!picker.isShowing())return;
    String spoken=speech.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[.!?]+$","")
      .replaceFirst("^(?:(?:это|das ist) +)?(?:(?:просто|только|nur|ein) +)*","");
    String[] russian={"домовое подключение","поисковый шурф","препятствие","стартовый котлован","приёмный котлован","квс","керновое бурение","точка поворота"};
    for(int j=0;j<types.length;j++)if(types[j].equalsIgnoreCase(spoken)||russian[j].replace('ё','е').equals(spoken.replace('ё','е'))){
     applyType.accept(types[j]);picker.dismiss();return;
    }
    notice("Objekttyp erneut sprechen oder auswählen.");
   }));
  };
  route.stationSelected=hits->pickEditorObjectStation(route,draftObjects,refreshObjects,hits);
  route.objectSelected=hits->pickEditorObject(route,draftObjects,refreshObjects,hits);
  Button objectTools=new Button(this);objectTools.setAllCaps(false);objectTools.setText("Objekte hinzufügen");form.addView(objectTools);objectTools.setOnClickListener(v->openObjects.run());refreshObjects.run();
  RouteEditHistory history=loadRouteHistory(route.encode());
  LinearLayout historyBar=new LinearLayout(this);form.addView(historyBar);
  Button undo=new Button(this),redo=new Button(this);undo.setAllCaps(false);redo.setAllCaps(false);undo.setText("↶");undo.setContentDescription("Rückgängig");redo.setText("↷");redo.setContentDescription("Wiederholen");
  historyBar.addView(undo,new LinearLayout.LayoutParams(0,-2,1));historyBar.addView(redo,new LinearLayout.LayoutParams(0,-2,1));
  Button legendEdit=new Button(this);legendEdit.setAllCaps(false);legendEdit.setText("Legende bearbeiten");form.addView(legendEdit);legendEdit.setOnClickListener(v->route.editLegend());
  Button proportional=new Button(this);proportional.setAllCaps(false);proportional.setText("Maßstab");proportional.setContentDescription("Trasse proportional nach Abschnittsmaßen neu zeichnen");historyBar.addView(proportional,new LinearLayout.LayoutParams(0,-2,1));
  proportional.setOnClickListener(v->{
   for(RouteSketch.Vertex vertex:route.vertices)if(Double.isFinite(vertex.latitude)&&Double.isFinite(vertex.longitude)){notice("GPS-gebundene Trasse: proportionale Umformung nicht verfügbar.");return;}
   if(!getPreferences(0).getString("routeUnderlay","").isEmpty()||calibration[0].length()>0){notice("Zeichenvorlage oder Kalibrierung vorhanden. Umformung nicht verfügbar.");return;}
   if(!route.rebuildMeasuredGeometry())notice("Alle Abschnittsmaße und eindeutige Segmentrichtungen erforderlich.");
  });
  Button resetDimensions=new Button(this);resetDimensions.setAllCaps(false);resetDimensions.setText("Maße ↺");resetDimensions.setContentDescription("Maßpositionen zurücksetzen");historyBar.addView(resetDimensions,new LinearLayout.LayoutParams(0,-2,1));resetDimensions.setOnClickListener(v->route.resetDimensionLayout());
  Button deletePoint=new Button(this);deletePoint.setAllCaps(false);deletePoint.setText("Punkt −");deletePoint.setContentDescription("Punkt löschen");historyBar.addView(deletePoint,new LinearLayout.LayoutParams(0,-2,1));deletePoint.setOnClickListener(v->deleteRoutePoint(route));
  Button remove=new Button(this);remove.setText("Letzten Abschnitt entfernen");form.addView(remove);
  TextView scaleStatus=sheetText("",13,false);form.addView(scaleStatus);
  Button calibrate=new Button(this);calibrate.setAllCaps(false);calibrate.setText("Maßstab kalibrieren");form.addView(calibrate);
  Button clearScale=new Button(this);clearScale.setAllCaps(false);clearScale.setText("Kalibrierung entfernen");form.addView(clearScale);
  LinearLayout list=page();form.addView(list);
  route.selected=i->showRouteSegmentMenu(route,i);
  route.dimensionSelected=i->measureSegment(route,i);
  route.strokeAdded=first->{
   route.moving=true;route.dimensioning=false;route.placingObject=false;route.editingObjects=false;
   mode.setSelection(2);
   route.highlight=first;route.invalidate();
   android.widget.Toast.makeText(this,"Trasse fertig – Punkte ziehen, Maße später eintragen.",android.widget.Toast.LENGTH_SHORT).show();
  };
  route.pointSelected=i->{
   if(i<0||i>=route.vertices.size())return;
   route.highlight=i;route.invalidate();
   RouteSketch.Vertex p=route.vertices.get(i);
   android.widget.PopupMenu menu=new android.widget.PopupMenu(this,route);
   menu.getMenu().add(0,0,0,"Punkt "+(i+1)+(p.name.isEmpty()?"":" · "+p.name)).setEnabled(false);
   if(i>0&&i<route.vertices.size()-1)menu.getMenu().add(0,1,1,"Löschen und verbinden");
   else menu.getMenu().add(0,2,1,"Start- oder Endpunkt").setEnabled(false);
   menu.setOnMenuItemClickListener(item->{if(item.getItemId()==1){confirmDeleteRoutePoint(route,i);return true;}return false;});
   menu.show();
  };
  Runnable refreshEditor=()->{
   undo.setEnabled(!history.undo.isEmpty());redo.setEnabled(!history.redo.isEmpty());
   remove.setEnabled(route.vertices.size()>1);deletePoint.setEnabled(route.vertices.size()>2);
   list.removeAllViews();
   double factor=sketchCalibration(route,calibration[0]);
   scaleStatus.setText(Double.isFinite(factor)?"Maßstab aktiv – Schätzwerte, keine Istmaße":calibration[0].length()>0?"Referenzpunkte geändert – neu kalibrieren":"Kein Zeichnungsmaßstab");
   clearScale.setEnabled(calibration[0].length()>0);
   String[] lines=route.measurements().split("\n");
   for(int i=1;i<route.vertices.size();i++){
    int index=i;
    Button measureButton=new Button(this);measureButton.setAllCaps(false);measureButton.setText(lines[i-1]+(!Double.isFinite(route.vertices.get(i).metres)&&Double.isFinite(route.estimatedSegment(i,factor))?" · geschätzt ca. "+reportMeasure(route.estimatedSegment(i,factor)):""));
    measureButton.setOnClickListener(v->measureSegment(route,index));list.addView(measureButton);
   }
  };
  refreshCalibration[0]=refreshEditor;
  calibrate.setOnClickListener(v->calibrateSketch(route,data->{calibration[0]=data;refreshEditor.run();}));
  clearScale.setOnClickListener(v->{calibration[0]=new org.json.JSONObject();refreshEditor.run();});
  route.changed=()->{history.record(route.encode());refreshEditor.run();};
  java.util.function.Consumer<Boolean> restore=forward->{
   String snapshot=history.step(forward);RouteSketch parsed=new RouteSketch(this,snapshot);
   route.vertices.clear();route.vertices.addAll(parsed.vertices);route.singleObjects.clear();route.singleObjects.addAll(parsed.singleObjects);route.highlight=-1;route.dragged=-1;route.invalidate();refreshEditor.run();
  };
  undo.setOnClickListener(v->restore.accept(false));redo.setOnClickListener(v->restore.accept(true));
  remove.setOnClickListener(v->{
   route.vertices.remove(route.vertices.size()-1);
   if(route.vertices.size()==1)route.vertices.clear();
   route.changed.run();route.invalidate();
  });
  route.changed.run();
  android.app.AlertDialog editor=new android.app.AlertDialog.Builder(this).setTitle("Trasse skizzieren").setView(scroll)
   .setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();
  editor.setOnDismissListener(dialog->{if(activeRouteEditor==route)activeRouteEditor=null;});
  editor.show();activeRouteEditor=route;route.setLiveFix(liveFix);
  editor.getButton(-1).setOnClickListener(v->{
   if(!initialSaved.equals(routeData())){notice("Trasse wurde zwischenzeitlich geändert. Editor neu öffnen.");return;}
   history.record(route.encode());
   if(!initialObjects.equals(draftObjects.toString())){double total=route.measuredLength();if(!initialObjects.equals(objects.toString())){notice("Objekte zwischenzeitlich geändert. Editor neu öffnen.");return;}for(int j=0;j<draftObjects.length();j++){org.json.JSONObject entry=draftObjects.optJSONObject(j);if(unchangedEditorObject(initialObjects,entry))continue;double st=entry.optDouble("station");if(!Double.isFinite(total)||!Double.isFinite(st)||st<0||st>total){notice("Objektstation außerhalb der bemaßten Trasse. Bitte korrigieren.");return;}}}
   org.json.JSONArray objectsToSave=initialObjects.equals(draftObjects.toString())?objects:draftObjects;
   if(persistEditorRoute(history,objectsToSave,getPreferences(0).edit().putString("sketchCalibration",calibration[0].toString()))){
    refreshRoute();editor.dismiss();notice(route.vertices.isEmpty()&&!route.singleObjects.isEmpty()?"Einzelobjekte gespeichert.":"Trasse gespeichert.");
   }
  });
  fullscreen.setOnClickListener(v->showFullscreenRoute(editor,form,route,mode,angles,historyBar,openObjects,underlayControls[0],true));
  if(!measure)showFullscreenRoute(editor,form,route,mode,angles,historyBar,openObjects,underlayControls[0],startFullscreen);
 }

 static org.json.JSONArray copyEditorObjects(String source){try{return new org.json.JSONArray(source);}catch(Exception e){throw new IllegalStateException(e);}}
 static boolean unchangedEditorObject(String initial,org.json.JSONObject object){
  if(object==null)return false;org.json.JSONArray original=copyEditorObjects(initial);
  for(int j=0;j<original.length();j++){org.json.JSONObject old=original.optJSONObject(j);if(old!=null&&old.toString().equals(object.toString()))return true;}return false;
 }
 boolean persistEditorRoute(RouteEditHistory history,org.json.JSONArray draft){return persistEditorRoute(history,draft,getPreferences(0).edit());}
 boolean persistEditorRoute(RouteEditHistory history,org.json.JSONArray draft,SharedPreferences.Editor ed){
  if(draft.toString().equals(objects.toString()))return persistRouteHistory(history,ed);
  try{
   String before=routeData(),old=objects.toString();org.json.JSONArray merged=new org.json.JSONArray(draft.toString());
   java.util.ArrayDeque<String> nextUndo=new java.util.ArrayDeque<>(undoObjects);nextUndo.push(old);while(nextUndo.size()>HISTORY_LIMIT)nextUndo.removeLast();
   if(!ed.putString("routeSketch",history.current).putString("routeHistoryHead",history.current).putString("routeUndo",new org.json.JSONArray(history.undo).toString()).putString("routeRedo",new org.json.JSONArray(history.redo).toString()).putString("objects",merged.toString()).putString("objectUndo",new org.json.JSONArray(nextUndo).toString()).putString("objectRedo","[]").commit()){notice("Trasse und Objekte konnten nicht gespeichert werden.");return false;}
   objects=merged;undoObjects.clear();undoObjects.addAll(nextUndo);redoObjects.clear();
   if(!before.equals(history.current))recordProjectEvent("Trasse geändert");recordProjectEvent("Objekte bearbeitet · "+draft.length());
   showObjects();refreshDetailQuantities();sketch.invalidate();updateHistoryButtons();return true;
  }catch(Exception e){notice("Trasse und Objekte konnten nicht gespeichert werden.");return false;}
 }
 boolean saveRoute(RouteSketch route){try{RouteEditHistory history=loadRouteHistory(routeData());history.record(route.encode());return persistRouteHistory(history);}catch(RuntimeException e){notice("Trasse konnte nicht gespeichert werden.");return false;}}
 Spinner choices(LinearLayout parent,String[] labels){
  Spinner spinner=new Spinner(this);
  spinner.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels));
  parent.addView(spinner);return spinner;
 }
 void onChoice(Spinner spinner,java.util.function.IntConsumer action){
  spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
   public void onItemSelected(AdapterView<?> parent,View view,int index,long id){action.accept(index);}
   public void onNothingSelected(AdapterView<?> parent){}
  });
 }
 LinearLayout belagsFields,asphaltFields;
 Button belagsToggle;

 boolean isStoneSurface(String name){String n=name.toLowerCase(java.util.Locale.ROOT);return n.contains("pflaster")||n.contains("platten")||n.contains("rasengittersteine");}
 void setBelagsExpanded(boolean expanded){
  if(surface!=null&&!isStoneSurface(String.valueOf(surface.getSelectedItem())))expanded=false;
  belagsFields.setVisibility(expanded?View.VISIBLE:View.GONE);
  belagsToggle.setText(expanded?"Belagsdetails ausblenden ▴":"Belagsdetails anzeigen ▾");
 }

 void openBelagsDetails(){
  setBelagsExpanded(true);
  showPage(measurePage);
  belagsToggle.post(()->belagsToggle.requestRectangleOnScreen(
    new android.graphics.Rect(0,0,belagsToggle.getWidth(),belagsToggle.getHeight()),true));
 }

 void showSurfaceLayers(){
  SharedPreferences data=getPreferences(0);
  String material=covering.equals("Unbekannt")?"Material nicht erfasst":covering;
  String thickness=covering.equals("Asphalt")?data.getString("asphalt",""):isStoneSurface(covering)?data.getString("stoneDepth",""):"";
  String unit=covering.equals("Asphalt")?" m":" cm";
  StringBuilder layers=new StringBuilder("Deckschicht · ").append(material).append(" · ")
   .append(thickness.isEmpty()?"Stärke nicht erfasst":thickness+unit);
  String bed=data.getString("bedding",""),old=data.getString("underlying",""),baseValue=data.getString("base","");
  layers.append("\nBettung · ").append(bed.isEmpty()?"nicht erfasst":bed);
  if(!old.isEmpty())layers.append("\nUnterliegender Altbelag · ").append(old);
  layers.append("\nTragschicht · ").append(baseValue.isEmpty()?"Stärke nicht erfasst":baseValue+" m");
  surfaceLayers.setText(layers.toString());
 }
 static final int INK=0xff193747, UI_TEXT=0xfff2f2f2, TEAL=0xff008f89;
 TextView[] overviewValues=new TextView[4];
 LinearLayout stationRows;
 final java.util.LinkedHashMap<LinearLayout,Button> sheetTabs=new java.util.LinkedHashMap<>();
 Button toolsMenu;

 TextView sheetText(String text,int size,boolean bold) {
  TextView label=new TextView(this);
  label.setText(text);
  label.setTextSize(size);
  label.setTextColor(UI_TEXT);
  if(bold)label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
  return label;
 }

 void sheetHeading(LinearLayout page,String name) {
  TextView heading=sheetText(name,21,true);
  heading.setPadding(0,dp(12),0,dp(8));
  page.addView(heading);
 }

 void addOverview() {
  String[] names={"Trassenlänge","Grabenbreite","Grabentiefe","Trassenobjekte"};
  for(int row=0;row<2;row++){
   LinearLayout pair=new LinearLayout(this);
   planPage.addView(pair);
   for(int col=0;col<2;col++){
    int i=row*2+col;
    LinearLayout metric=page();
    metric.setBackground(editorShape(0xff171717,12));
    LinearLayout.LayoutParams box=new LinearLayout.LayoutParams(0,-2,1);
    box.setMargins(col==0?0:dp(3),0,col==0?dp(3):0,dp(6));
    pair.addView(metric,box);
    overviewValues[i]=sheetText("–",21,true);
    overviewValues[i].setTextColor(0xff58c99a);
    metric.addView(overviewValues[i]);
    metric.addView(sheetText(names[i],13,false));
   }
  }
 }

 void refreshOverview(){
  if(stationRows==null)return;
  double[] lengths={l,w,d};
  for(int i=0;i<lengths.length;i++)overviewValues[i].setText(lengths[i]>0?reportMeasure(lengths[i]):"–");
  overviewValues[3].setText(String.valueOf(objects.length()));
  stationRows.removeAllViews();
  java.util.List<Integer> sorted=new java.util.ArrayList<>();
  for(int i=0;i<objects.length();i++)if(objects.optJSONObject(i)!=null)sorted.add(i);
  sorted.sort((a,b)->Double.compare(objects.optJSONObject(a).optDouble("station"),objects.optJSONObject(b).optDouble("station")));
  if(sorted.isEmpty())stationRows.addView(sheetText("Keine Trassenobjekte erfasst.",15,false));
  for(int index:sorted){
   org.json.JSONObject object=objects.optJSONObject(index);
   Button entry=new Button(this);
   styleEditorButton(entry,false);entry.setTextSize(14);entry.setPadding(dp(12),dp(6),dp(12),dp(6));
   entry.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
   entry.setTextColor(objectWarning(index)?0xffff927d:UI_TEXT);
   entry.setText(String.format(java.util.Locale.GERMANY,"%.2f m   ·   %s",object.optDouble("station"),object.optString("type")));
   entry.setOnClickListener(v->detailObject(index));
   if(!overlapSignature(index).isEmpty()){
    entry.setOnLongClickListener(v->{confirmObjectOverlap(index);return true;});
    entry.setText(entry.getText()+(overlapAccepted(index)?" · bestätigt":" · ⚠ Überlappung"));
   }
   stationRows.addView(entry);
  }
 }

 void overviewAction(LinearLayout parent,String label,String kind,Runnable action){
  Button button=actionButtonSingle(parent,label,action);compactToolIcon(button,label,kind);
  android.graphics.drawable.Drawable icon=button.getCompoundDrawables()[1];
  button.setCompoundDrawables(icon,null,null,null);button.setCompoundDrawablePadding(dp(10));
  button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);button.setTextSize(14);button.setMinHeight(dp(48));button.setPadding(dp(12),dp(6),dp(12),dp(6));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(4);button.setLayoutParams(lp);
 }
 void addSheetTab(LinearLayout bar,String name,LinearLayout sheet){
  Button tab=new Button(this);
  styleEditorButton(tab,false);tab.setTextSize(14);
  tab.setSingleLine(true);
  tab.setText(name);
  bar.addView(tab,new LinearLayout.LayoutParams(0,-2,1));
  sheetTabs.put(sheet,tab);
  tab.setOnClickListener(v->showPage(sheet));
 }

 org.json.JSONArray elevationPoints(){
  try{return new org.json.JSONArray(getPreferences(0).getString("elevationPoints","[]"));}catch(Exception e){return new org.json.JSONArray();}
 }
 static double pipeSlope(org.json.JSONObject a,org.json.JSONObject b){
  double length=b.optDouble("station")-a.optDouble("station"),rise=b.optDouble("invert")-a.optDouble("invert");
  return length>0&&Double.isFinite(rise)?100*rise/length:Double.NaN;
 }
 String elevationText(org.json.JSONObject o){
  StringBuilder b=new StringBuilder();String[] keys={"ground","depth","invert"},names={"OK Gelände","Grabentiefe","Rohrsohle"};
  for(int j=0;j<keys.length;j++){double v=o.optDouble(keys[j]);if(Double.isFinite(v)){if(b.length()>0)b.append(" · ");b.append(names[j]).append(String.format(java.util.Locale.GERMANY," %.3f m",v));}}
  double ground=o.optDouble("ground"),depth=o.optDouble("depth");
  if(Double.isFinite(ground-depth)&&depth>0)b.append(String.format(java.util.Locale.GERMANY," · Grabensohle %.3f m",ground-depth));
  return b.toString();
 }
 boolean saveElevationPoints(org.json.JSONArray points){
  if(!getPreferences(0).edit().putString("elevationPoints",points.toString()).commit()){notice("Höhenpunkte konnten nicht gespeichert werden.");return false;}
  recordProjectEvent("Höhenpunkte geändert");return true;
 }
 void editElevationPoints(){
  org.json.JSONArray points=elevationPoints();String[] labels=new String[points.length()];
  for(int j=0;j<points.length();j++){org.json.JSONObject o=points.optJSONObject(j);labels[j]=o==null?"Ungültiger Eintrag":String.format(java.util.Locale.GERMANY,"Station %.3f m · %s",o.optDouble("station"),elevationText(o));}
  android.app.AlertDialog.Builder dialog=new android.app.AlertDialog.Builder(this).setTitle("Höhenpunkte").setNegativeButton("Schließen",null).setPositiveButton("Neuer Höhenpunkt",(d,w)->editElevationPoint(-1));
  if(labels.length>0)dialog.setItems(labels,(d,w)->editElevationPoint(w));else dialog.setMessage("Noch keine Höhenpunkte. Höhen im selben Bezugssystem eingeben.");
  dialog.show();
 }
 void editElevationPoint(int index){
  org.json.JSONArray original=elevationPoints();org.json.JSONObject old=index<0?null:original.optJSONObject(index);
  LinearLayout form=page();form.addView(sheetText("Station und mindestens einen Messwert eingeben. Alle Höhen im gleichen Bezugssystem.",14,false));
  String[] keys={"station","ground","depth","invert"},names={"Station (m)","OK Gelände (m)","Grabentiefe (m)","Rohrsohle (m)"};
  EditText[] fields=new EditText[4];
  for(int j=0;j<4;j++){form.addView(sheetText(names[j],14,false));fields[j]=new EditText(this);fields[j].setSingleLine(true);fields[j].setInputType(8194|4096);if(old!=null)fields[j].setText(old.optString(keys[j],""));form.addView(fields[j]);}
  android.app.AlertDialog.Builder builder=new android.app.AlertDialog.Builder(this).setTitle("Höhenpunkt").setView(form).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null);
  if(old!=null)builder.setNeutralButton("Löschen",(a,b)->new android.app.AlertDialog.Builder(this).setTitle("Höhenpunkt löschen?").setNegativeButton("Abbrechen",null).setPositiveButton("Löschen",(x,y)->{org.json.JSONArray next=elevationPoints();if(!next.toString().equals(original.toString())){notice("Höhenpunkte geändert. Neu öffnen.");return;}next.remove(index);if(saveElevationPoints(next))editElevationPoints();}).show());
  android.app.AlertDialog dialog=builder.create();
  dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(vv->{
   try{
    double measured=new RouteSketch(this,routeData()).measuredLength(),total=Double.isFinite(measured)&&measured>0?measured:l,station=number(fields[0]);
    if(!Double.isFinite(station)||!Double.isFinite(total)||total<=0||station<0||station>total){fields[0].setError("Station innerhalb der Trasse erforderlich");return;}
    org.json.JSONObject entry=new org.json.JSONObject().put("station",station);int values=0;
    for(int j=1;j<4;j++){if(fields[j].length()==0)continue;double n=number(fields[j]);if(!Double.isFinite(n)||(j==2&&n<=0)){fields[j].setError("Gültiges Maß erforderlich");return;}entry.put(keys[j],n);values++;}
    if(values==0){notice("Mindestens einen Höhen- oder Tiefenwert eingeben.");return;}
    if(!elevationPoints().toString().equals(original.toString())){notice("Höhenpunkte geändert. Neu öffnen.");return;}
    java.util.ArrayList<org.json.JSONObject> rows=new java.util.ArrayList<>();
    for(int j=0;j<original.length();j++){if(j==index)continue;org.json.JSONObject o=original.getJSONObject(j);if(Math.abs(o.optDouble("station")-station)<.000001){fields[0].setError("Station bereits vorhanden");return;}rows.add(o);}
    rows.add(entry);rows.sort((a,b)->Double.compare(a.optDouble("station"),b.optDouble("station")));
    if(saveElevationPoints(new org.json.JSONArray(rows))){dialog.dismiss();editElevationPoints();}
   }catch(Exception e){notice("Bitte gültige Zahlen eingeben.");}
  }));dialog.show();
 }
 String elevationReport(){
  org.json.JSONArray points=elevationPoints();StringBuilder b=new StringBuilder("Höhenpunkte · gemessene Werte\n");
  if(points.length()==0)return b.append("Noch keine Messwerte.").toString();
  for(int j=0;j<points.length();j++){
   org.json.JSONObject o=points.optJSONObject(j);if(o==null)continue;b.append(String.format(java.util.Locale.GERMANY,"%.3f m: %s\n",o.optDouble("station"),elevationText(o)));
   if(j>0){org.json.JSONObject a=points.optJSONObject(j-1);double slope=a==null?Double.NaN:pipeSlope(a,o);
    if(Double.isFinite(slope))b.append(String.format(java.util.Locale.GERMANY,"Rohrsohle Δ: %+.3f %% / %+.2f ‰ (in Stationsrichtung)\n",slope,slope*10));}
  }
  return b.toString();
 }
 static double elevationValue(org.json.JSONObject o,int series){
  if(o==null)return Double.NaN;
  if(series==0)return o.optDouble("ground");
  if(series==2)return o.optDouble("invert");
  double ground=o.optDouble("ground"),depth=o.optDouble("depth");
  return Double.isFinite(ground-depth)&&depth>0?ground-depth:Double.NaN;
 }
 boolean hasAbsoluteElevations(org.json.JSONArray points,double total){
  for(int j=0;j<points.length();j++){org.json.JSONObject o=points.optJSONObject(j);if(o==null)continue;double st=o.optDouble("station");if(!Double.isFinite(st)||st<0||st>total)continue;
   for(int k=0;k<3;k++)if(Double.isFinite(elevationValue(o,k)))return true;
  }return false;
 }
 class ElevationProfileView extends View{
  final org.json.JSONArray points,markers;final double total;
  final boolean absolute;
  final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  final int[] colors={0xff65717a,0xffa65e19,TEAL};
  ElevationProfileView(org.json.JSONArray points,org.json.JSONArray markers,double total,boolean absolute){
   super(MainActivity.this);this.points=points;this.markers=markers;this.total=total;this.absolute=absolute;setContentDescription("Längsschnitt Höhenprofil");
  }
  double value(org.json.JSONObject o,int k){if(absolute)return elevationValue(o,k);if(k!=1||o==null)return Double.NaN;double n=o.optDouble("depth");return Double.isFinite(n)&&n>0?-n:Double.NaN;}
  void label(Canvas c,String s,float x,float y){p.setColor(INK);p.setTextSize(dp(10));c.drawText(s,x,y,p);}
  protected void onDraw(Canvas c){
   c.drawColor(Color.WHITE);
   float left=dp(64),right=getWidth()-dp(20),top=dp(30),bottom=getHeight()-dp(48);
   if(right<=left||bottom<=top||!Double.isFinite(total)||total<=0)return;
   double lo=absolute?Double.POSITIVE_INFINITY:0,hi=absolute?Double.NEGATIVE_INFINITY:0;
   for(int j=0;j<points.length();j++){org.json.JSONObject o=points.optJSONObject(j);if(o==null)continue;double st=o.optDouble("station");if(!Double.isFinite(st)||st<0||st>total)continue;
    for(int k=0;k<3;k++){double v=value(o,k);if(Double.isFinite(v)){lo=Math.min(lo,v);hi=Math.max(hi,v);}}
   }
   if(!Double.isFinite(lo)||!Double.isFinite(hi)){label(c,"Keine darstellbaren Höhenwerte",left,top);return;}
   double pad=Math.max(.05,(hi-lo)*.08);lo-=pad;hi+=pad;
   double span=hi-lo;if(!Double.isFinite(span)||span<=0)return;
   for(int k=0;k<=4;k++){
    float y=top+(bottom-top)*k/4f;p.setColor(0xffd9e3e5);p.setStrokeWidth(dp(1));c.drawLine(left,y,right,y,p);
    label(c,String.format(java.util.Locale.GERMANY,"%.2f",hi-span*k/4),dp(3),y+dp(3));
    float x=left+(right-left)*k/4f;p.setColor(0xffd9e3e5);c.drawLine(x,top,x,bottom,p);
    String station=String.format(java.util.Locale.GERMANY,"%.1f",total*k/4);p.setTextSize(dp(10));label(c,station,Math.min(x,right-p.measureText(station)),bottom+dp(15));
   }
   label(c,absolute?"Höhe (m)":"Rel. Höhe (m)",left,dp(15));label(c,"Station (m)",left,bottom+dp(32));
   // Station bands show presence only, not vertical installation depth.
   try{
    org.json.JSONArray rows=new org.json.JSONArray(getPreferences(0).getString("metricVerbauSections","[]"));
    p.setColor(0xffa65e19);p.setStrokeWidth(dp(3));
    boolean shown=false;
    for(int j=0;j<rows.length();j++){
     org.json.JSONArray row=rows.optJSONArray(j);if(row==null)continue;
     double from=row.optDouble(0),to=row.optDouble(1);
     if(!Double.isFinite(from)||!Double.isFinite(to)||from<0||to>total||to<=from)continue;
     float x1=left+(right-left)*(float)(from/total),x2=left+(right-left)*(float)(to/total);
     if(row.optDouble(2,0)>0){c.drawLine(x1,top-dp(10),x2,top-dp(10),p);shown=true;}
     if(row.optDouble(3,0)>0){c.drawLine(x1,top-dp(4),x2,top-dp(4),p);shown=true;}
    }
    if(shown){label(c,"L",left-dp(14),top-dp(8));label(c,"R",left-dp(14),top-dp(2));}
   }catch(Exception ignored){}

   if(!absolute){float y=top+(bottom-top)*(float)(hi/span);p.setColor(colors[0]);p.setStrokeWidth(dp(2));c.drawLine(left,y,right,y,p);}
   for(int k=0;k<3;k++){
    if(!absolute&&k!=1)continue;
    float px=0,py=0;boolean prev=false;
    for(int j=0;j<points.length();j++){
     org.json.JSONObject o=points.optJSONObject(j);double st=o==null?Double.NaN:o.optDouble("station"),v=value(o,k);
     if(!Double.isFinite(st)||st<0||st>total||!Double.isFinite(v)){prev=false;continue;}
     float x=left+(right-left)*(float)(st/total),y=top+(bottom-top)*(float)((hi-v)/span);
     p.setColor(colors[k]);p.setStrokeWidth(dp(2));if(prev)c.drawLine(px,py,x,y,p);c.drawCircle(x,y,dp(3),p);px=x;py=y;prev=true;
    }
   }
   for(int j=0;j<markers.length();j++){org.json.JSONObject o=markers.optJSONObject(j);if(o==null)continue;double st=o.optDouble("station");if(!Double.isFinite(st)||st<0||st>total)continue;float x=left+(right-left)*(float)(st/total);p.setColor(0xff87979b);p.setStrokeWidth(dp(1));c.drawLine(x,top,x,top+dp(7),p);label(c,String.valueOf(j+1),Math.min(x+dp(2),right-dp(10)),top+dp(17));}
  }
 }
 void showLongitudinalProfile(){
  LinearLayout form=page();ScrollView scroll=new ScrollView(this);scroll.addView(form);
  RouteSketch route=new RouteSketch(this,routeData());
  double measured=route.measuredLength();
  boolean complete=Double.isFinite(measured)&&measured>0;
  final double total=complete?measured:l,depth=d;
  String caption=complete?"Stationierung aus bemaßter Skizze":"Stationierung aus gespeicherter Gesamttrassenlänge; Skizzenmaße unvollständig";
  form.addView(sheetText(caption,14,false));
  actionButtonSingle(form,"Höhenpunkte bearbeiten",()->editElevationPoints());
  form.addView(sheetText(elevationReport(),14,false));
  org.json.JSONArray points=elevationPoints();
  boolean absolute=hasAbsoluteElevations(points,total),relative=false;
  for(int j=0;j<points.length();j++){org.json.JSONObject o=points.optJSONObject(j);if(o!=null&&Double.isFinite(o.optDouble("depth"))&&o.optDouble("depth")>0&&o.optDouble("station")>=0&&o.optDouble("station")<=total)relative=true;}
  if(Double.isFinite(total)&&total>0&&(absolute||relative)){
   form.addView(sheetText(absolute?"Höhenprofil · gemeinsamer Höhenbezug":"Relatives Tiefenprofil · Oberfläche = 0 m (Bezug)",16,true));
   form.addView(sheetText(absolute?"Grau: OK Gelände · Braun: Grabensohle · Grün: Rohrsohle":"Grau: Bezugslinie · Braun: gemessene Grabentiefe",14,false));
   form.addView(new ElevationProfileView(points,objects,total,absolute),new LinearLayout.LayoutParams(-1,dp(300)));
   form.addView(sheetText("Verbindung nur zwischen benachbarten vorhandenen Messwerten. Keine Extrapolation. Fehlende Werte bleiben offen. Vertikal überhöht / nicht maßstäblich.",13,false));
  }else if(!Double.isFinite(total)||total<=0||!Double.isFinite(depth)||depth<=0){
   form.addView(sheetText("Zuerst gültige Trassenlänge und Grabentiefe speichern.",16,true));
  }else{
   form.addView(sheetText("Relatives Standardprofil mit einheitlicher gespeicherter Grabentiefe; keine Geländehöhen.",14,false));
   View drawing=new View(this){
    final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    void label(Canvas c,String s,float x,float y){p.setColor(INK);p.setTextSize(dp(11));c.drawText(s,x,y,p);}
    protected void onDraw(Canvas c){
     c.drawColor(Color.WHITE);
     float left=dp(48),right=getWidth()-dp(18),top=dp(45),bottom=getHeight()-dp(55);
     if(right<=left||bottom<=top)return;
     p.setStrokeWidth(dp(1));p.setColor(0xffd9e3e5);
     for(int k=0;k<=4;k++){float y=top+(bottom-top)*k/4f;c.drawLine(left,y,right,y,p);label(c,String.format(java.util.Locale.GERMANY,"%.2f",depth*k/4),dp(3),y+dp(4));p.setColor(0xffd9e3e5);}
     p.setColor(0xffe6d9bf);c.drawRect(left,top,right,bottom,p);
     p.setColor(0xff526570);p.setStrokeWidth(dp(3));c.drawLine(left,top,right,top,p);
     p.setColor(TEAL);c.drawLine(left,bottom,right,bottom,p);
     label(c,"OK Oberfläche (Bezug 0,00 m)",left,top-dp(10));
     label(c,"Grabensohle",left,bottom-dp(8));
     for(int k=0;k<=4;k++){float x=left+(right-left)*k/4f;p.setColor(0xff87979b);p.setStrokeWidth(dp(1));c.drawLine(x,bottom,x,bottom+dp(6),p);
      String value=String.format(java.util.Locale.GERMANY,"%.1f",total*k/4);p.setTextSize(dp(11));label(c,value,Math.min(right-p.measureText(value),x),bottom+dp(20));}
     label(c,"Station (m)",left,bottom+dp(38));
     for(int k=0;k<objects.length();k++){
      org.json.JSONObject o=objects.optJSONObject(k);if(o==null)continue;double st=o.optDouble("station");
      if(!Double.isFinite(st)||st<0||st>total)continue;
      float x=left+(right-left)*(float)(st/total);
      p.setColor(0xffa65e19);p.setStrokeWidth(dp(1));c.drawLine(x,top,x,bottom,p);c.drawCircle(x,top,dp(3),p);
      label(c,String.valueOf(k+1),Math.min(x+dp(3),right-dp(12)),top+dp(15+(k%3)*13));
     }
    }
   };
   form.addView(drawing,new LinearLayout.LayoutParams(-1,dp(260)));
   form.addView(sheetText(String.format(java.util.Locale.GERMANY,"Trassenlänge %.2f m · Grabentiefe %.2f m",total,depth),16,true));
   if(complete&&l>0&&Math.abs(total-l)>.01)form.addView(sheetText("Hinweis: Skizzenlänge und gespeicherte Gesamttrassenlänge weichen ab.",14,false));
   StringBuilder entries=new StringBuilder("Stationen und Objekte\n");
   if(complete){double station=0;for(int k=0;k<route.vertices.size();k++){RouteSketch.Vertex v=route.vertices.get(k);if(k>0)station+=v.metres;entries.append(String.format(java.util.Locale.GERMANY,"%.2f m – Punkt %d %s\n",station,k+1,v.name));}}
   for(int k=0;k<objects.length();k++){org.json.JSONObject o=objects.optJSONObject(k);if(o==null)continue;double st=o.optDouble("station");entries.append(String.format(java.util.Locale.GERMANY,"%d · %.2f m – %s%s\n",k+1,st,o.optString("type"),Double.isFinite(st)&&st>=0&&st<=total?"":" (außerhalb Profil)"));}
   form.addView(sheetText(entries.toString(),14,false));
  }
  new android.app.AlertDialog.Builder(this).setTitle("Längsschnitt").setView(scroll).setPositiveButton("Schließen",null).show();
 }
 org.json.JSONArray lvPositions(){
  try{return new org.json.JSONArray(getPreferences(0).getString("lvPositions","[]"));}catch(Exception e){return new org.json.JSONArray();}
 }
 boolean saveLv(org.json.JSONArray rows){
  if(!getPreferences(0).edit().putString("lvPositions",rows.toString()).commit()){notice("LV konnte nicht gespeichert werden.");return false;}
  recordProjectEvent("Leistungsverzeichnis geändert");return true;
 }
 String lvLabel(org.json.JSONObject o){return o.optString("oz")+" · "+o.optString("title")+" ["+o.optString("unit")+"]";}
 static double ansatzQuantity(org.json.JSONObject row,String unit){
  if(row==null||!unit.equals(row.optString("unit")))return Double.NaN;
  double value=row.optDouble("quantity");return Double.isFinite(value)&&value>=0?value:Double.NaN;
 }
 static boolean duplicateSegmentArea(org.json.JSONObject a,org.json.JSONObject b){
  if(a==null||b==null||!"segment".equals(a.optString("kind"))||!"segment".equals(b.optString("kind")))return false;
  String am=a.optString("mode"),bm=b.optString("mode");
  if(!("area".equals(am)||"cut".equals(am))||!("area".equals(bm)||"cut".equals(bm)))return false;
  try{org.json.JSONObject x=new org.json.JSONObject(a.getString("snapshot")),y=new org.json.JSONObject(b.getString("snapshot"));
   double af=x.getDouble("from"),at=x.getDouble("to"),bf=y.getDouble("from"),bt=y.getDouble("to");
   return Double.isFinite(af+at+bf+bt)&&at>af&&bt>bf&&Math.min(at,bt)>Math.max(af,bf)+1e-9;
  }catch(Exception e){return false;}
 }
 static boolean conflictingSegmentAreas(org.json.JSONArray rows){
  if(rows==null)return false;
  for(int j=0;j<rows.length();j++)for(int k=0;k<j;k++)if(duplicateSegmentArea(rows.optJSONObject(j),rows.optJSONObject(k)))return true;
  return false;
 }
 static double sumAnsatz(org.json.JSONObject position){
  org.json.JSONArray rows=position.optJSONArray("ansatz");
  if(rows==null||rows.length()==0||conflictingSegmentAreas(rows))return Double.NaN;
  double sum=0;for(int j=0;j<rows.length();j++){double q=ansatzQuantity(rows.optJSONObject(j),position.optString("unit"));if(!Double.isFinite(q))return Double.NaN;sum+=q;}
  return Double.isFinite(sum)?sum:Double.NaN;
 }
 java.util.ArrayList<org.json.JSONObject> ansatzCandidates(String unit){
  java.util.ArrayList<org.json.JSONObject> rows=new java.util.ArrayList<>();
  try{
   for(int j=0;j<segments.length();j++){
    org.json.JSONObject o=segments.optJSONObject(j);if(o==null)continue;double a=o.optDouble("from"),b=o.optDouble("to"),width=o.optDouble("width"),cut=o.optDouble("cutWidth"),len=b-a;
    if(!Double.isFinite(len)||a<0||b>l||len<=0)continue;
    String label="Abschnitt "+(j+1)+" · "+o.optString("surface")+" · "+csvNumber(a)+"–"+csvNumber(b)+" m";
    if("m".equals(unit))addAnsatzCandidate(rows,unit,label+" · Länge",len,"segment",o,"length");
    if("m²".equals(unit)&&width>0)addAnsatzCandidate(rows,unit,label+" · "+csvNumber(len)+" × "+csvNumber(width),len*width,"segment",o,"area");
    if("m²".equals(unit)&&cut>0)addAnsatzCandidate(rows,unit,label+" · mit Nachschnitt "+csvNumber(len)+" × "+csvNumber(cut),len*cut,"segment",o,"cut");
   }
   for(int j=0;j<objects.length();j++){
    org.json.JSONObject o=objects.optJSONObject(j);if(o==null)continue;double a=detailNumber(o,"detailLength"),b=detailNumber(o,"detailWidth"),h=detailNumber(o,"detailDepth");
    String label="Objekt "+(j+1)+" · "+o.optString("type")+" · "+csvNumber(o.optDouble("station"))+" m"+overlapStatus(j);
    if("m".equals(unit)&&a>0)addAnsatzCandidate(rows,unit,label+" · Länge",a,"object",o,"length");
    if("m²".equals(unit)&&a>0&&b>0)addAnsatzCandidate(rows,unit,label+" · "+csvNumber(a)+" × "+csvNumber(b),a*b,"object",o,"area");
    if("m³".equals(unit)&&a>0&&b>0&&h>0)addAnsatzCandidate(rows,unit,label+" · "+csvNumber(a)+" × "+csvNumber(b)+" × "+csvNumber(h),a*b*h,"object",o,"volume");
    if("St".equalsIgnoreCase(unit)||"Stk".equalsIgnoreCase(unit))addAnsatzCandidate(rows,unit,label,1,"object",o,"count");
   }
  }catch(Exception e){notice("Einige Mengenquellen konnten nicht gelesen werden.");}
  return rows;
 }
 void addAnsatzCandidate(java.util.ArrayList<org.json.JSONObject> rows,String unit,String label,double qty,String kind,org.json.JSONObject source,String mode)throws org.json.JSONException{
  if(!Double.isFinite(qty)||qty<0)return;
  String snapshot=kind.equals("object")?overlapGeometry(source):source.toString();
  rows.add(new org.json.JSONObject().put("unit",unit).put("label",label).put("quantity",qty).put("kind",kind).put("snapshot",snapshot).put("mode",mode));
 }
 boolean ansatzSourceCurrent(org.json.JSONObject row){
  if(row==null)return false;
  String kind=row.optString("kind"),snap=row.optString("snapshot");
  org.json.JSONArray list="object".equals(kind)?objects:"segment".equals(kind)?segments:null;if(list==null)return false;
  for(int j=0;j<list.length();j++){org.json.JSONObject o=list.optJSONObject(j);if(o!=null&&snap.equals("object".equals(kind)?overlapGeometry(o):o.toString()))return true;}
  return false;
 }
 void showLvAnsatz(int index){
  org.json.JSONArray original=lvPositions();org.json.JSONObject position=original.optJSONObject(index);if(position==null)return;
  org.json.JSONArray rows=position.optJSONArray("ansatz");if(rows==null)rows=new org.json.JSONArray();
  final org.json.JSONArray stored=rows;
  LinearLayout form=page();ScrollView scroll=new ScrollView(this);scroll.addView(form);
  form.addView(sheetText(lvLabel(position),16,true));
  form.addView(sheetText("Mengenansätze sind bestätigte Messwert-Schnappschüsse. Geänderte Quellen neu übernehmen. Keine automatische Addition zu anderen LV-Positionen oder Abzüge.",14,false));
  double sum=sumAnsatz(position);form.addView(sheetText("Summe: "+(Double.isFinite(sum)?csvNumber(sum)+" "+position.optString("unit"):"offen"),16,true));
  if(conflictingSegmentAreas(stored)){TextView warning=sheetText("Überlappende Flächenansätze: Grabenfläche und Fläche mit Nachschnitt nicht doppelt ansetzen. Überzähligen Ansatz entfernen.",14,false);warning.setTextColor(0xffffb16e);form.addView(warning);}
  for(int j=0;j<stored.length();j++){final int rowIndex=j;org.json.JSONObject row=stored.optJSONObject(j);if(row==null)continue;
   Button b=new Button(this);b.setAllCaps(false);b.setText(row.optString("label")+"\n"+csvNumber(ansatzQuantity(row,position.optString("unit")))+" "+row.optString("unit")+(ansatzSourceCurrent(row)?"":" · Quelle prüfen"));
   b.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("Mengenansatz entfernen?").setNegativeButton("Abbrechen",null).setPositiveButton("Entfernen",(x,y)->{if(!lvPositions().toString().equals(original.toString())){notice("LV geändert. Neu öffnen.");return;}stored.remove(rowIndex);persistAnsatz(original,index,stored);}).show());form.addView(b);
  }
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Mengenansätze").setView(scroll).setNegativeButton("Schließen",null).setPositiveButton("Messwert hinzufügen",(x,y)->chooseLvAnsatz(index)).create();dialog.show();
 }
 void chooseLvAnsatz(int index){
  org.json.JSONArray original=lvPositions();org.json.JSONObject position=original.optJSONObject(index);if(position==null)return;
  java.util.ArrayList<org.json.JSONObject> candidates=ansatzCandidates(position.optString("unit"));
  if(candidates.isEmpty()){notice("Keine passenden Messwerte für diese Einheit vorhanden.");return;}
  String[] labels=new String[candidates.size()];
  for(int j=0;j<labels.length;j++){org.json.JSONObject row=candidates.get(j);labels[j]=row.optString("label")+" = "+csvNumber(row.optDouble("quantity"))+" "+row.optString("unit");}
  new android.app.AlertDialog.Builder(this).setTitle("Messwert auswählen").setItems(labels,(a,b)->{
   org.json.JSONObject row=candidates.get(b);
   new android.app.AlertDialog.Builder(this).setTitle("Mengenansatz übernehmen?").setMessage(labels[b]+"\n\nDie Menge wird als eigener Ansatz gespeichert. Bereits zugeordnete Leistungen und LV-Regeln prüfen.").setNegativeButton("Abbrechen",null).setPositiveButton("Übernehmen",(x,y)->{
    if(!lvPositions().toString().equals(original.toString())||!ansatzSourceCurrent(row)){notice("Daten geändert. Neu öffnen.");return;}
    org.json.JSONArray stored=position.optJSONArray("ansatz");if(stored==null)stored=new org.json.JSONArray();
    for(int j=0;j<stored.length();j++){org.json.JSONObject old=stored.optJSONObject(j);if(old!=null&&old.optString("kind").equals(row.optString("kind"))&&old.optString("snapshot").equals(row.optString("snapshot"))&&old.optString("mode").equals(row.optString("mode"))){notice("Dieser Mengenansatz ist bereits vorhanden.");return;}}
    for(int j=0;j<stored.length();j++)if(duplicateSegmentArea(stored.optJSONObject(j),row)){notice("Überlappende Fläche bereits in dieser LV-Position. Vorhandenen Ansatz prüfen oder ersetzen.");return;}
    stored.put(row);persistAnsatz(original,index,stored);
   }).show();
  }).setNegativeButton("Abbrechen",null).show();
 }
 void persistAnsatz(org.json.JSONArray positions,int index,org.json.JSONArray rows){
  try{org.json.JSONObject position=positions.getJSONObject(index);position.put("ansatz",rows);position.put("source","ansatz");if(saveLv(positions))showLvAnsatz(index);}catch(Exception e){notice("Mengenansatz konnte nicht gespeichert werden.");}
 }
 double asphaltLongitudinalQuantity(boolean recut){
  if(!Double.isFinite(l)||l<=0)return Double.NaN;
  if(segments.length()==0){
   if(!"Asphalt".equals(covering))return Double.NaN;
   return recut?asphaltRecutQuantity(l,w,Double.NaN,left,right):2*l;
  }
  double total=0,covered=0;
  for(int i=0;i<segments.length();i++){
   org.json.JSONObject row=segments.optJSONObject(i);if(row==null)return Double.NaN;
   double a=row.optDouble("from"),b=row.optDouble("to");
   if(!Double.isFinite(a+b)||a<0||b<=a||b>l+1e-8)return Double.NaN;
   for(int j=0;j<i;j++){org.json.JSONObject prev=segments.optJSONObject(j);if(a<prev.optDouble("to")&&b>prev.optDouble("from"))return Double.NaN;}
   String type=row.optString("surface");if(type.isEmpty()||"Unbekannt".equals(type))return Double.NaN;
   covered+=b-a;
   if(!"Asphalt".equals(type))continue;
   double q=recut?asphaltRecutQuantity(b-a,row.optDouble("width"),row.optDouble("cutWidth"),row.optDouble("left"),row.optDouble("right")):2*(b-a);
   if(!Double.isFinite(q))return Double.NaN;total+=q;
  }
  return Math.abs(covered-l)<=1e-8?total:Double.NaN;
 }
 static double asphaltRecutQuantity(double length,double width,double cutWidth,double left,double right){
  if(Double.isFinite(left)&&left>=0&&Double.isFinite(right)&&right>=0)return length*((left>0?1:0)+(right>0?1:0));
  if(Double.isFinite(width)&&Double.isFinite(cutWidth)&&Math.abs(width-cutWidth)<1e-9&&!(left>0)&&!(right>0))return 0;
  return Double.NaN;
 }
 String asphaltLvBreakdown(org.json.JSONObject row){
  String source=row.optString("source");
  if(!"asphaltLong".equals(source)&&!"asphaltRecut".equals(source))return "";
  double longitudinal=asphaltLongitudinalQuantity("asphaltRecut".equals(source)),cross=row.optDouble("transverseCuts",0);
  StringBuilder text=new StringBuilder("\nLängsschnitt (rechnerisch): ").append(Double.isFinite(longitudinal)?csvNumber(longitudinal)+" m":"offen");
  if(Double.isFinite(cross)&&cross>0)text.append("\nQuer- und Endschnitte (manuell): ").append(csvNumber(cross)).append(" m");
  else if(!Double.isFinite(cross)||cross<0)text.append("\nQuer- und Endschnitte: Eingabe prüfen");
  return text.toString();
 }
 double lvQuantity(org.json.JSONObject o){
  String source=o.optString("source","manual");
  double value=Double.NaN;
  if("ansatz".equals(source))value=sumAnsatz(o);
  else if("manual".equals(source))value=o.optDouble("quantity");
  else if("asphaltLong".equals(source)||"asphaltRecut".equals(source)){
   if("m".equals(o.optString("unit"))||"lfm".equals(o.optString("unit"))){
    value=asphaltLongitudinalQuantity("asphaltRecut".equals(source));
    double extra=o.optDouble("transverseCuts",0);if(!Double.isFinite(extra)||extra<0)value=Double.NaN;else value+=extra;
   }
  }
  else if(l>0&&w>0&&d>0){
   if("length".equals(source)&&"m".equals(o.optString("unit")))value=l;
   if("area".equals(source)&&"m²".equals(o.optString("unit")))value=currentTrenchQuantities()[0];
   if("volume".equals(source)&&"m³".equals(o.optString("unit")))value=currentTrenchQuantities()[1];
   if("cut".equals(source)&&"m²".equals(o.optString("unit")))value=currentCutArea();
  }
  return Double.isFinite(value)&&value>=0?value:Double.NaN;
 }
 void requestGaebImport(){
  android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
  i.addCategory(android.content.Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,419);
 }
 void importGaeb(android.net.Uri uri){
  final String projectId=activeProject;
  new Thread(()->{
   try(java.io.InputStream in=getContentResolver().openInputStream(uri)){
    if(in==null)throw new java.io.IOException("Datei nicht lesbar");
    org.json.JSONArray imported=GaebImport.read(in);
    runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()&&projectId.equals(activeProject))previewGaeb(imported);});
   }catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())notice("GAEB-Import fehlgeschlagen: "+e.getMessage());});}
  }).start();
 }
 void previewGaeb(org.json.JSONArray imported){
  org.json.JSONArray existing=lvPositions();java.util.HashSet<String> known=new java.util.HashSet<>();
  for(int j=0;j<existing.length();j++){org.json.JSONObject o=existing.optJSONObject(j);if(o!=null)known.add(o.optString("oz"));}
  org.json.JSONArray merged=new org.json.JSONArray();for(int j=0;j<existing.length();j++)merged.put(existing.opt(j));
  int added=0,duplicates=0,missing=0;
  StringBuilder preview=new StringBuilder();
  for(int j=0;j<imported.length();j++){
   org.json.JSONObject o=imported.optJSONObject(j);if(o==null)continue;boolean duplicate=!known.add(o.optString("oz"));
   if(duplicate)duplicates++;else{merged.put(o);added++;}
   if(o.optString("unit").isEmpty()||!o.has("target"))missing++;
   if(j<50)preview.append(lvLabel(o)).append(" · Soll: ").append(o.has("target")?csvNumber(o.optDouble("target")):"offen").append(duplicate?" · vorhanden, übersprungen":"").append("\n");
  }
  LinearLayout form=page();ScrollView scroll=new ScrollView(this);scroll.addView(form);
  form.addView(sheetText(imported.length()+" Positionen gelesen · "+added+" neu · "+duplicates+" bereits vorhanden · "+missing+" ohne vollständige Sollmenge/Einheit",16,true));
  form.addView(sheetText("Vorhandene OZ bleiben unverändert. Keine automatische Mengenzuordnung. Sonderpositionen, OZ-Gliederung und Vertragstexte prüfen; keine GAEB-Schemaprüfung.",14,false));
  form.addView(sheetText(preview.toString()+(imported.length()>50?"\nWeitere Positionen im LV nach Import.":""),13,false));
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("GAEB – Importvorschau").setView(scroll).setNegativeButton("Abbrechen",null).setPositiveButton("Neue Positionen übernehmen",(a,b)->{
   if(!lvPositions().toString().equals(existing.toString())){notice("LV geändert. Import erneut starten.");return;}
   if(saveLv(merged))showLv();
  }).create();dialog.show();dialog.getButton(-1).setEnabled(added>0);
 }
 void showLv(){
  org.json.JSONArray rows=lvPositions();LinearLayout form=page();ScrollView scroll=new ScrollView(this);scroll.addView(form);
  form.addView(sheetText("Manuelles LV · Zuordnung durch Anwender. Geometrische Mengen vor Abrechnung prüfen.",14,false));
  for(int j=0;j<rows.length();j++){
   final int index=j;org.json.JSONObject o=rows.optJSONObject(j);if(o==null)continue;double qty=lvQuantity(o),target=o.optDouble("target");
   Button button=new Button(this);button.setAllCaps(false);button.setText(lvLabel(o)+"\nIst: "+(Double.isFinite(qty)?csvNumber(qty):"offen")+(Double.isFinite(target)?" · Soll: "+csvNumber(target):"")+asphaltLvBreakdown(o));
   button.setOnClickListener(v->editLv(index));form.addView(button);Button amounts=new Button(this);amounts.setAllCaps(false);amounts.setText("Mengenansätze · "+o.optString("oz"));amounts.setOnClickListener(v->showLvAnsatz(index));form.addView(amounts);
  }
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Leistungsverzeichnis").setView(scroll).setNegativeButton("Schließen",null).setNeutralButton("GAEB importieren",(a,b)->requestGaebImport()).setPositiveButton("Neue Position",(a,b)->editLv(-1)).create();dialog.show();
 }
 void editLv(int index){
  org.json.JSONArray original=lvPositions();org.json.JSONObject old=index<0?null:original.optJSONObject(index);
  LinearLayout form=page();ScrollView scroll=new ScrollView(this);scroll.addView(form);
  String[] keys={"oz","title","unit","target","quantity"},hints={"Ordnungszahl / LV-Position","Kurztext","Einheit (m, m², m³, St ...)","Sollmenge (optional)","Istmenge manuell (optional)"};
  EditText[] fields=new EditText[5];
  for(int j=0;j<fields.length;j++){form.addView(sheetText(hints[j],14,false));fields[j]=new EditText(this);fields[j].setSingleLine(true);if(j>=3)fields[j].setInputType(8194);if(old!=null)fields[j].setText(old.optString(keys[j],""));form.addView(fields[j]);}
  String[] sources={"manual","length","area","volume","cut","ansatz","asphaltLong","asphaltRecut"};String[] sourceLabels={"Manuelle Istmenge","Trassenlänge (m)","Grundfläche (m²)","Aushub – geometrisch (m³)","Fläche mit Nachschnitt (m²)","Mengenansätze","Asphaltschnitt längs – rechnerisch (m)","Rückschnitt längs – rechnerisch (m)"};
  form.addView(sheetText("Mengenquelle",14,false));Spinner source=choices(form,sourceLabels);source.setSelection(Math.max(0,java.util.Arrays.asList(sources).indexOf(old==null?"manual":old.optString("source"))));
  LinearLayout crossFields=page();form.addView(crossFields);
  crossFields.addView(sheetText("Ausgeführte Quer- und Endschnitte (m)",14,true));
  EditText crossLength=new EditText(this);crossLength.setSingleLine(true);crossLength.setInputType(8194);crossLength.setHint("Gesamtlänge zusätzlich, leer = 0");if(old!=null)crossLength.setText(old.optString("transverseCuts",""));crossFields.addView(crossLength);
  crossFields.addView(sheetText("Nur tatsächlich ausgeführte Schnitte dieser LV-Position. Nicht nochmals separat abrechnen.",13,false));
  java.util.function.IntConsumer crossVisibility=i->crossFields.setVisibility(i>=0&&i<sources.length&&("asphaltLong".equals(sources[i])||"asphaltRecut".equals(sources[i]))?View.VISIBLE:View.GONE);
  onChoice(source,crossVisibility);crossVisibility.accept(source.getSelectedItemPosition());
  form.addView(sheetText("Automatische Quellen verwenden gespeicherte Maße, keine LV-spezifischen Abzugsregeln. Längsschnitte rechnerisch; Quer- und Endschnitte nur nach Eingabe. Ausführung prüfen.",13,false));
  android.app.AlertDialog.Builder builder=new android.app.AlertDialog.Builder(this).setTitle("LV-Position").setView(scroll).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null);
  if(old!=null&&!old.optString("gaebLongText").isEmpty())form.addView(sheetText("GAEB-Langtext\n"+old.optString("gaebLongText"),13,false));
  if(old!=null)builder.setNeutralButton("Löschen",(a,b)->new android.app.AlertDialog.Builder(this).setTitle("LV-Position löschen?").setNegativeButton("Abbrechen",null).setPositiveButton("Löschen",(x,y)->{org.json.JSONArray next=lvPositions();if(!next.toString().equals(original.toString())){notice("LV geändert. Neu öffnen.");return;}next.remove(index);if(saveLv(next))showLv();}).show());
  android.app.AlertDialog dialog=builder.create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{
   try{
    String oz=fields[0].getText().toString().trim(),title=fields[1].getText().toString().trim(),unit=fields[2].getText().toString().trim();
    if(oz.isEmpty()||title.isEmpty()||unit.isEmpty()){notice("Ordnungszahl, Kurztext und Einheit erforderlich.");return;}
    String chosen=sources[source.getSelectedItemPosition()];
    String expected=("asphaltLong".equals(chosen)||"asphaltRecut".equals(chosen))?("lfm".equals(unit)?"lfm":"m"):"length".equals(chosen)?"m":"volume".equals(chosen)?"m³":("manual".equals(chosen)||"ansatz".equals(chosen))?unit:"m²";
    if(!expected.equals(unit)){fields[2].setError("Einheit "+expected+" erforderlich");return;}
    org.json.JSONObject item=old==null?new org.json.JSONObject():new org.json.JSONObject(old.toString());item.remove("target");item.remove("quantity");item.put("oz",oz).put("title",title).put("unit",unit).put("source",chosen);
    item.remove("transverseCuts");if(("asphaltLong".equals(chosen)||"asphaltRecut".equals(chosen))&&crossLength.length()>0){double extra=number(crossLength);if(!Double.isFinite(extra)||extra<0){crossLength.setError("Länge ab 0 m erforderlich");return;}item.put("transverseCuts",extra);}
    for(int j=3;j<5;j++){if(j==4&&!"manual".equals(chosen))continue;if(fields[j].length()==0)continue;double value=number(fields[j]);if(!Double.isFinite(value)||value<0){fields[j].setError("Menge ab 0 erforderlich");return;}item.put(keys[j],value);}
    org.json.JSONArray next=lvPositions();if(!next.toString().equals(original.toString())){notice("LV geändert. Neu öffnen.");return;}
    for(int j=0;j<next.length();j++){org.json.JSONObject o=next.optJSONObject(j);if(j!=index&&o!=null&&oz.equals(o.optString("oz"))){fields[0].setError("Ordnungszahl bereits vorhanden");return;}}
    if(index<0)next.put(item);else next.put(index,item);
    if(saveLv(next)){dialog.dismiss();showLv();}
   }catch(Exception e){notice("Bitte LV-Eingaben prüfen.");}
  }));dialog.show();
 }
 org.json.JSONArray dailyReports(){
  try{return new org.json.JSONArray(getPreferences(0).getString("dailyReports","[]"));}catch(Exception e){return new org.json.JSONArray();}
 }
 boolean saveDailyReports(org.json.JSONArray rows){
  if(!getPreferences(0).edit().putString("dailyReports",rows.toString()).commit()){notice("Bautagesbericht konnte nicht gespeichert werden.");return false;}
  recordProjectEvent("Bautagesbericht geändert");return true;
 }
 static final String[] DAILY_KEYS={"date","weather","crew","equipment","work","materials","incidents","notes"};
 static final String[] DAILY_LABELS={"Datum (JJJJ-MM-TT)","Wetter / Temperatur","Personal / Arbeitszeiten","Geräte / Maschinen","Ausgeführte Arbeiten / Stationen","Material / Lieferungen","Behinderungen / Besonderheiten","Bemerkungen"};
 org.json.JSONArray dailyPhotos(org.json.JSONObject report){
  org.json.JSONArray a=report==null?null:report.optJSONArray("photos");return a==null?new org.json.JSONArray():a;
 }
 void chooseDailyPhotos(int index){chooseRecordPhotos(index,false);}
 void chooseRecordPhotos(int index,boolean changeOrder){
  org.json.JSONArray original=changeOrder?nachtraege():dailyReports();org.json.JSONObject report=original.optJSONObject(index);if(report==null)return;
  chooseProjectPhotos(report,changeOrder?"Fotos zum Nachtrag":"Fotos zum Bautagesbericht",photos->{
   if(!(changeOrder?nachtraege():dailyReports()).toString().equals(original.toString())){notice("Bericht geändert. Neu öffnen.");return;}
   try{report.put("photos",photos).put("modifiedAt",System.currentTimeMillis());if(changeOrder){if(saveNachtraege(original))showNachtraege();}else if(saveDailyReports(original))showDailyReports();}catch(Exception e){notice("Fotos konnten nicht zugeordnet werden.");}
  });
 }
 void chooseProjectPhotos(org.json.JSONObject report,String title,java.util.function.Consumer<org.json.JSONArray> save){
  String projectId=activeProject;
  java.util.LinkedHashMap<String,org.json.JSONObject> entries=new java.util.LinkedHashMap<>();java.util.HashSet<String> selected=new java.util.HashSet<>();
  org.json.JSONArray prior=dailyPhotos(report);
  for(int j=0;j<prior.length();j++){org.json.JSONObject o=prior.optJSONObject(j);if(o!=null){entries.put(o.optString("file"),o);selected.add(o.optString("file"));}}
  RouteSketch route=new RouteSketch(this,routeData());
  try{
   for(int j=0;j<route.vertices.size();j++){RouteSketch.Vertex v=route.vertices.get(j);
    for(int k=0;k<v.photos.length();k++){
     String file=v.photos.optString(k);org.json.JSONObject meta=v.photoMetadata.optJSONObject(file);
     String info="Trassenpunkt "+(j+1)+(v.name.isEmpty()?"":" – "+v.name)+"\nStation bei Auswahl: "+reportMeasure(photoStation(route,j))+"\n"+photoInfo(meta);
     if(meta!=null&&!meta.optString("description").isEmpty())info+="\nBeschreibung: "+meta.optString("description");
     if(!entries.containsKey(file))entries.put(file,new org.json.JSONObject().put("file",file).put("label","Punkt "+(j+1)+" · Foto "+(k+1)+" · "+v.name).put("info",info));
    }
   }
  }catch(Exception e){notice("Fotoliste konnte nicht geladen werden.");return;}
  if(entries.isEmpty()){notice("Zuerst Fotos an Trassenpunkte anhängen.");return;}
  java.util.ArrayList<org.json.JSONObject> list=new java.util.ArrayList<>(entries.values());String[] labels=new String[list.size()];boolean[] checked=new boolean[list.size()];
  for(int j=0;j<list.size();j++){org.json.JSONObject o=list.get(j);labels[j]=o.optString("label");checked[j]=selected.contains(o.optString("file"));}
  new android.app.AlertDialog.Builder(this).setTitle(title).setMultiChoiceItems(labels,checked,(d,j,c)->checked[j]=c)
   .setNegativeButton("Abbrechen",null).setPositiveButton("Übernehmen",(d,w)->{
    if(!projectId.equals(activeProject)){notice("Projekt wurde gewechselt.");return;}
    org.json.JSONArray photos=new org.json.JSONArray();for(int j=0;j<list.size();j++)if(checked[j])photos.put(list.get(j));
    save.accept(photos);
   }).show();
 }
 String pendingDailyPdfId;
 void exportDailyPdf(int index){
  org.json.JSONObject report=dailyReports().optJSONObject(index);if(report==null)return;
  pendingDailyPdfId=report.optString("id");if(pendingDailyPdfId.isEmpty()){notice("Bericht erneut speichern.");return;}
  android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);i.addCategory(android.content.Intent.CATEGORY_OPENABLE);i.setType("application/pdf");i.putExtra(android.content.Intent.EXTRA_TITLE,"Bautagesbericht_"+report.optString("date")+".pdf");startActivityForResult(i,423);
 }
 int dailyTextPdf(android.graphics.pdf.PdfDocument pdf,int number,String title,String content,android.graphics.Bitmap bitmap){
  android.text.TextPaint tp=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);tp.setTextSize(13);tp.setColor(INK);
  android.text.StaticLayout layout=android.text.StaticLayout.Builder.obtain(content,0,content.length(),tp,523).build();
  Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  int line=0;boolean first=true;
  do{
   android.graphics.pdf.PdfDocument.Page page=pdf.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,number++).create());
   int y=first&&bitmap!=null?450:70,top=layout.getLineTop(line),end=line+1;
   while(end<layout.getLineCount()&&layout.getLineBottom(end)-top<=780-y)end++;
   try{Canvas c=page.getCanvas();pen.setColor(INK);pen.setTextSize(18);c.drawText(title,36,40,pen);pen.setTextSize(11);c.drawText("Seite "+(number-1),500,808,pen);
    if(first&&bitmap!=null){float scale=Math.min(523f/bitmap.getWidth(),370f/bitmap.getHeight()),bw=bitmap.getWidth()*scale,bh=bitmap.getHeight()*scale,x=36+(523-bw)/2,by=63+(370-bh)/2;c.drawBitmap(bitmap,null,new android.graphics.RectF(x,by,x+bw,by+bh),pen);}
    c.save();c.translate(36,y);c.clipRect(0,0,523,layout.getLineTop(end)-top);c.translate(0,-top);layout.draw(c);c.restore();
   }finally{pdf.finishPage(page);}line=end;first=false;
  }while(line<layout.getLineCount());
  return number;
 }
 void writeDailyPdf(android.net.Uri uri){
  String id=pendingDailyPdfId;pendingDailyPdfId=null;org.json.JSONObject report=null;org.json.JSONArray rows=dailyReports();
  for(int j=0;j<rows.length();j++){org.json.JSONObject o=rows.optJSONObject(j);if(o!=null&&id!=null&&id.equals(o.optString("id")))report=o;}
  if(report==null){notice("Bericht nicht gefunden. Export erneut starten.");return;}
  android.graphics.pdf.PdfDocument pdf=new android.graphics.pdf.PdfDocument();
  try(java.io.OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
   if(out==null)throw new java.io.IOException();
   appendDailyReport(pdf,1,report);
   pdf.writeTo(out);notice("Bautagesbericht gespeichert.");
  }catch(Exception e){notice("PDF konnte nicht gespeichert werden.");}finally{pdf.close();}
 }
 int appendDailyReport(android.graphics.pdf.PdfDocument pdf,int first,org.json.JSONObject report){
   String projectName=projectHeader.getText().toString();StringBuilder text=new StringBuilder("Projekt: "+projectName+"\nDatum: "+report.optString("date")+"\n");
   for(int j=1;j<DAILY_KEYS.length;j++){String value=report.optString(DAILY_KEYS[j]);text.append("\n").append(DAILY_LABELS[j]).append("\n").append(value.isEmpty()?"Nicht erfasst":value).append("\n");}
   org.json.JSONArray photos=dailyPhotos(report);text.append("\nZugeordnete Fotos: ").append(photos.length());
   int page=dailyTextPdf(pdf,first,"Bautagesbericht",text.toString(),null);
   for(int j=0;j<photos.length();j++){org.json.JSONObject o=photos.optJSONObject(j);if(o==null)continue;android.graphics.Bitmap bitmap=reportPhoto(o.optString("file"));String info=projectName+"\nBerichtsdatum: "+report.optString("date")+"\n"+o.optString("info");if(bitmap==null)info+="\nBilddatei fehlt oder ist nicht lesbar.";
    page=dailyTextPdf(pdf,page,"Foto "+(j+1)+" / "+photos.length(),info,bitmap);
   }
   return page;
 }
 void showDailyReports(){
  org.json.JSONArray rows=dailyReports();LinearLayout form=page();ScrollView scroll=new ScrollView(this);scroll.addView(form);
  if(rows.length()==0)form.addView(sheetText("Noch keine Bautagesberichte vorhanden.",15,false));
  for(int j=rows.length()-1;j>=0;j--){
   final int index=j;org.json.JSONObject o=rows.optJSONObject(j);if(o==null)continue;Button b=new Button(this);b.setAllCaps(false);
   String work=o.optString("work");b.setText(o.optString("date")+"\n"+work.substring(0,Math.min(100,work.length())));b.setOnClickListener(v->editDailyReport(index));form.addView(b);
   Button photos=new Button(this);photos.setAllCaps(false);photos.setText("Fotos zuordnen ("+dailyPhotos(o).length()+")");photos.setOnClickListener(v->chooseDailyPhotos(index));form.addView(photos);
   Button pdf=new Button(this);pdf.setAllCaps(false);pdf.setText("Bautagesbericht als PDF");pdf.setOnClickListener(v->exportDailyPdf(index));form.addView(pdf);
  }
  new android.app.AlertDialog.Builder(this).setTitle("Bautagesberichte").setView(scroll).setNegativeButton("Schließen",null).setPositiveButton("Neuer Bericht",(a,b)->editDailyReport(-1)).show();
 }
 void editDailyReport(int index){
  org.json.JSONArray original=dailyReports();org.json.JSONObject old=index<0?null:original.optJSONObject(index);
  LinearLayout form=page();ScrollView scroll=new ScrollView(this);scroll.addView(form);
  EditText[] fields=new EditText[DAILY_KEYS.length];
  for(int j=0;j<fields.length;j++){
   form.addView(sheetText(DAILY_LABELS[j],14,true));fields[j]=new EditText(this);
   if(j==0){fields[j].setSingleLine(true);fields[j].setInputType(1);}
   else{fields[j].setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);fields[j].setMinLines(2);fields[j].setGravity(Gravity.TOP);}
   fields[j].setText(old==null?(j==0?java.time.LocalDate.now().toString():""):old.optString(DAILY_KEYS[j]));form.addView(fields[j]);
  }
  android.app.AlertDialog.Builder builder=new android.app.AlertDialog.Builder(this).setTitle("Bautagesbericht").setView(scroll).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null);
  if(old!=null)builder.setNeutralButton("Löschen",(a,b)->new android.app.AlertDialog.Builder(this).setTitle("Bautagesbericht löschen?").setNegativeButton("Abbrechen",null).setPositiveButton("Löschen",(x,y)->{org.json.JSONArray next=dailyReports();if(!next.toString().equals(original.toString())){notice("Berichte geändert. Neu öffnen.");return;}next.remove(index);if(saveDailyReports(next))showDailyReports();}).show());
  android.app.AlertDialog dialog=builder.create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{
   String date=fields[0].getText().toString().trim();
   try{if(!java.time.LocalDate.parse(date).toString().equals(date)||!date.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException();}
   catch(Exception e){fields[0].setError("Gültiges Datum: JJJJ-MM-TT");return;}
   try{
    org.json.JSONArray next=dailyReports();if(!next.toString().equals(original.toString())){notice("Berichte geändert. Neu öffnen.");return;}
    org.json.JSONObject row=old==null?new org.json.JSONObject():new org.json.JSONObject(old.toString());
    row.put("id",row.optString("id",java.util.UUID.randomUUID().toString())).put("modifiedAt",System.currentTimeMillis());
    for(int j=0;j<fields.length;j++)row.put(DAILY_KEYS[j],fields[j].getText().toString().trim());
    java.util.ArrayList<org.json.JSONObject> sorted=new java.util.ArrayList<>();
    for(int j=0;j<next.length();j++){if(j==index)continue;org.json.JSONObject o=next.getJSONObject(j);if(date.equals(o.optString("date"))){fields[0].setError("Bericht für dieses Datum bereits vorhanden");return;}sorted.add(o);}
    sorted.add(row);sorted.sort((a,c)->a.optString("date").compareTo(c.optString("date")));
    if(saveDailyReports(new org.json.JSONArray(sorted))){dialog.dismiss();showDailyReports();}
   }catch(Exception e){notice("Bericht konnte nicht gespeichert werden.");}
  }));dialog.show();
 }
 org.json.JSONArray nachtraege(){
  try{return new org.json.JSONArray(getPreferences(0).getString("nachtraege","[]"));}catch(Exception e){return new org.json.JSONArray();}
 }
 boolean saveNachtraege(org.json.JSONArray rows){
  if(!getPreferences(0).edit().putString("nachtraege",rows.toString()).commit()){notice("Nachtrag konnte nicht gespeichert werden.");return false;}
  recordProjectEvent("Nachtragsliste geändert");return true;
 }
 boolean lvHasStaleAnsatz(org.json.JSONObject o){
  if(!"ansatz".equals(o.optString("source")))return false;
  org.json.JSONArray rows=o.optJSONArray("ansatz");if(rows==null)return false;
  for(int j=0;j<rows.length();j++)if(!ansatzSourceCurrent(rows.optJSONObject(j)))return true;
  return false;
 }
 boolean hasExplicitLvSource(org.json.JSONArray lv,String kind,String snapshot){
  for(int j=0;j<lv.length();j++){org.json.JSONObject o=lv.optJSONObject(j);if(o==null||!"ansatz".equals(o.optString("source")))continue;
   org.json.JSONArray rows=o.optJSONArray("ansatz");if(rows==null)continue;
   for(int k=0;k<rows.length();k++){org.json.JSONObject row=rows.optJSONObject(k);if(row!=null&&kind.equals(row.optString("kind"))&&snapshot.equals(row.optString("snapshot")))return true;}
  }return false;
 }
 void addNachtragHint(LinearLayout form,String title,String description){
  Button b=new Button(this);b.setAllCaps(false);b.setText(title+"\n"+description);b.setOnClickListener(v->editNachtrag(-1,title,description));form.addView(b);
 }
 String pendingNachtragPdfId;
 void exportNachtragPdf(int index){
  org.json.JSONObject row=nachtraege().optJSONObject(index);if(row==null)return;
  String id=row.optString("id");if(id.isEmpty()){notice("Nachtrag erneut speichern.");return;}
  new android.app.AlertDialog.Builder(this).setTitle("Nachtragsdokumentation als PDF")
   .setMessage("Enthält die gespeicherte Begründung, Mengenangaben, Status und ausgewählte Fotos. Kein automatisch anerkanntes Nachtragsangebot.")
   .setNegativeButton("Abbrechen",null).setPositiveButton("Exportieren",(d,w)->{
    pendingNachtragPdfId=id;android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);i.addCategory(android.content.Intent.CATEGORY_OPENABLE);i.setType("application/pdf");i.putExtra(android.content.Intent.EXTRA_TITLE,"Nachtrag_"+row.optString("date")+".pdf");startActivityForResult(i,424);
   }).show();
 }
 void writeNachtragPdf(android.net.Uri uri){
  String id=pendingNachtragPdfId;pendingNachtragPdfId=null;org.json.JSONObject row=null;org.json.JSONArray rows=nachtraege();
  for(int j=0;j<rows.length();j++){org.json.JSONObject o=rows.optJSONObject(j);if(o!=null&&id!=null&&id.equals(o.optString("id")))row=o;}
  if(row==null){notice("Nachtrag nicht gefunden. Export erneut starten.");return;}
  android.graphics.pdf.PdfDocument pdf=new android.graphics.pdf.PdfDocument();
  try(java.io.OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
   if(out==null)throw new java.io.IOException();
   appendNachtrag(pdf,1,row);
   pdf.writeTo(out);notice("Nachtragsdokumentation gespeichert.");
  }catch(Exception e){notice("PDF konnte nicht gespeichert werden.");}finally{pdf.close();}
 }
 int appendNachtrag(android.graphics.pdf.PdfDocument pdf,int first,org.json.JSONObject row){
   String projectName=projectHeader.getText().toString();StringBuilder text=new StringBuilder("Projekt: "+projectName+"\nDatum: "+row.optString("date")+"\nBearbeitungsstatus: "+row.optString("status")+"\n");
   String[] keys={"title","reason","order","evidence"},labels={"Bezeichnung / LV-Bezug","Sachverhalt / Begründung","Anordnung / Auftraggeber","Nachweise / Bezug"};
   for(int j=0;j<keys.length;j++){String value=row.optString(keys[j]);text.append("\n").append(labels[j]).append("\n").append(value.isEmpty()?"Nicht erfasst":value).append("\n");}
   double q=row.optDouble("quantity");text.append("\nZusätzliche Menge\n").append(Double.isFinite(q)?csvNumber(q)+" "+row.optString("unit"):"Nicht erfasst");
   org.json.JSONArray photos=dailyPhotos(row);text.append("\n\nZugeordnete Fotos: ").append(photos.length());
   text.append("\n\nDokumentation – kein automatisch ermittelter Vergütungsanspruch. Vertragsgrundlage, Mengen und Freigabe gesondert prüfen.");
   int page=dailyTextPdf(pdf,first,"Nachtragsdokumentation",text.toString(),null);
   for(int j=0;j<photos.length();j++){org.json.JSONObject o=photos.optJSONObject(j);if(o==null)continue;android.graphics.Bitmap bitmap=reportPhoto(o.optString("file"));
    String info=projectName+"\nNachtrag: "+row.optString("title")+"\n"+o.optString("info");if(bitmap==null)info+="\nBilddatei fehlt oder ist nicht lesbar.";
    page=dailyTextPdf(pdf,page,"Nachweisfoto "+(j+1)+" / "+photos.length(),info,bitmap);
   }
   return page;
 }
 void showNachtraege(){
  LinearLayout form=page();ScrollView scroll=new ScrollView(this);scroll.addView(form);
  form.addView(sheetText("Prüfhinweise, keine automatische Anerkennung oder Änderung von LV-Mengen. Vertrag, Anordnung und Abrechnung getrennt prüfen.",14,true));
  org.json.JSONArray lv=lvPositions();int hints=0;
  form.addView(sheetText("Soll / Ist – Auffälligkeiten",16,true));
  for(int j=0;j<lv.length();j++){
   org.json.JSONObject o=lv.optJSONObject(j);if(o==null)continue;double ist=lvQuantity(o),soll=o.optDouble("target");
   if(lvHasStaleAnsatz(o)){addNachtragHint(form,lvLabel(o),"Mengenansatz geändert – Quelle prüfen");hints++;continue;}
   if(Double.isFinite(ist)&&Double.isFinite(soll)&&ist-soll>0.0005){
    String note="Soll "+csvNumber(soll)+" · Ist "+csvNumber(ist)+" · Mehrmenge "+csvNumber(ist-soll)+" "+o.optString("unit");
    if(soll>0)note+=" ("+String.format(java.util.Locale.GERMANY,"%.1f",100*(ist-soll)/soll)+" % über Soll)";
    addNachtragHint(form,lvLabel(o),note);hints++;
   }
  }
  if(hints==0)form.addView(sheetText("Keine festgestellten Mehrmengen oder geänderten Mengenansätze. Fehlende Soll-/Istwerte nicht bewertbar.",13,false));
  form.addView(sheetText("Ohne explizite Mengenansatz-Zuordnung",16,true));
  form.addView(sheetText("Eine Leistung kann bereits über eine Haupttrassenmenge oder manuelle LV-Position erfasst sein.",13,false));
  for(int j=0;j<objects.length();j++){org.json.JSONObject o=objects.optJSONObject(j);if(o!=null&&!hasExplicitLvSource(lv,"object",overlapGeometry(o)))addNachtragHint(form,"Objekt "+(j+1)+" · "+o.optString("type"),"Station "+o.optString("station")+" m · LV-Zuordnung prüfen"+overlapStatus(j));}
  for(int j=0;j<segments.length();j++){org.json.JSONObject o=segments.optJSONObject(j);if(o!=null&&!hasExplicitLvSource(lv,"segment",o.toString()))addNachtragHint(form,"Abschnitt "+(j+1)+" · "+o.optString("surface"),"Station "+o.optString("from")+"–"+o.optString("to")+" m · LV-Zuordnung prüfen");}
  form.addView(sheetText("Dokumentierte Nachträge",16,true));
  org.json.JSONArray saved=nachtraege();
  if(saved.length()==0)form.addView(sheetText("Noch keine Nachträge erfasst.",13,false));
  for(int j=0;j<saved.length();j++){final int index=j;org.json.JSONObject o=saved.optJSONObject(j);if(o==null)continue;Button b=new Button(this);b.setAllCaps(false);b.setText(o.optString("title")+"\n"+o.optString("date")+" · "+o.optString("status"));b.setOnClickListener(v->editNachtrag(index,"",""));form.addView(b);Button photos=new Button(this);photos.setAllCaps(false);photos.setText("Nachweisfotos ("+dailyPhotos(o).length()+")");photos.setOnClickListener(v->chooseRecordPhotos(index,true));form.addView(photos);Button pdf=new Button(this);pdf.setAllCaps(false);pdf.setText("Nachtrag als PDF");pdf.setOnClickListener(v->exportNachtragPdf(index));form.addView(pdf);}
  new android.app.AlertDialog.Builder(this).setTitle("Nachtragsmanagement").setView(scroll).setNegativeButton("Schließen",null).setPositiveButton("Neuer Nachtrag",(d,w)->editNachtrag(-1,"","")).show();
 }
 void editNachtrag(int index,String suggestedTitle,String suggestedReason){
  org.json.JSONArray original=nachtraege();org.json.JSONObject old=index<0?null:original.optJSONObject(index);
  LinearLayout form=page();ScrollView scroll=new ScrollView(this);scroll.addView(form);
  String[] keys={"title","date","reason","order","evidence","quantity","unit"},labels={"Bezeichnung / LV-Bezug","Datum (JJJJ-MM-TT)","Beschreibung / Begründung","Anordnung / Auftraggeber","Nachweise (Foto, Bericht, Station)","Zusätzliche Menge (optional)","Einheit"};
  EditText[] fields=new EditText[keys.length];
  for(int j=0;j<fields.length;j++){form.addView(sheetText(labels[j],14,true));fields[j]=new EditText(this);if(j==5)fields[j].setInputType(8194);if(j==2||j==3||j==4)fields[j].setMinLines(2);else fields[j].setSingleLine(true);fields[j].setText(old==null?(j==0?suggestedTitle:j==1?java.time.LocalDate.now().toString():j==2?suggestedReason:""):old.optString(keys[j],""));form.addView(fields[j]);}
  String[] statuses={"Prüfen","Dokumentiert","Eingereicht","Bestätigt (manuell)","Abgelehnt"};
  form.addView(sheetText("Status (manuelle Dokumentation)",14,true));Spinner status=choices(form,statuses);status.setSelection(Math.max(0,java.util.Arrays.asList(statuses).indexOf(old==null?"Prüfen":old.optString("status"))));
  android.app.AlertDialog.Builder builder=new android.app.AlertDialog.Builder(this).setTitle("Nachtrag dokumentieren").setView(scroll).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null);
  if(old!=null)builder.setNeutralButton("Löschen",(a,b)->new android.app.AlertDialog.Builder(this).setTitle("Nachtrag löschen?").setNegativeButton("Abbrechen",null).setPositiveButton("Löschen",(x,y)->{org.json.JSONArray next=nachtraege();if(!next.toString().equals(original.toString())){notice("Daten geändert. Neu öffnen.");return;}next.remove(index);if(saveNachtraege(next))showNachtraege();}).show());
  android.app.AlertDialog dialog=builder.create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{
   try{
    String title=fields[0].getText().toString().trim(),date=fields[1].getText().toString().trim();
    if(title.isEmpty()){fields[0].setError("Bezeichnung erforderlich");return;}
    try{if(!date.matches("\\d{4}-\\d{2}-\\d{2}")||!java.time.LocalDate.parse(date).toString().equals(date))throw new IllegalArgumentException();}catch(Exception e){fields[1].setError("Gültiges Datum erforderlich");return;}
    org.json.JSONObject row=old==null?new org.json.JSONObject().put("id",java.util.UUID.randomUUID().toString()):new org.json.JSONObject(old.toString());
    for(int j=0;j<keys.length;j++)if(j!=5)row.put(keys[j],fields[j].getText().toString().trim());
    row.remove("quantity");if(fields[5].length()>0){double q=number(fields[5]);if(!Double.isFinite(q)||q<=0||row.optString("unit").isEmpty()){fields[5].setError("Positive Menge und Einheit erforderlich");return;}row.put("quantity",q);}
    row.put("status",status.getSelectedItem().toString()).put("modifiedAt",System.currentTimeMillis());
    org.json.JSONArray next=nachtraege();if(!next.toString().equals(original.toString())){notice("Daten geändert. Neu öffnen.");return;}
    if(index<0)next.put(row);else next.put(index,row);
    if(saveNachtraege(next)){dialog.dismiss();showNachtraege();}
   }catch(Exception e){notice("Nachtrag konnte nicht gespeichert werden.");}
  }));dialog.show();
 }
 void showMontage(){
  new android.app.AlertDialog.Builder(this).setTitle("Rohrleitungsbau – Montage")
   .setItems(new String[]{"Formstückkatalog","Projekt-Formstücke","Gesamtplan – Stationierung","Materialliste (eingebaut)","Verbindungsregister","Prüfprotokolle","Projektleitungen"},(d,w)->{if(w==0)showMontageCatalog();else if(w==1)showMontageInventory();else if(w==2)showMontageStationPlan();else if(w==3)showMontageMaterials();else if(w==4)new MontageConnections(this).show();else if(w==5)new MontageProtocols(this).show();else editUtilities();})
   .setNegativeButton("Schließen",null).show();
 }
 static final class MontageHistory {
  final java.util.ArrayList<String> states=new java.util.ArrayList<>();
  int cursor=0;
 }
 MontageHistory loadMontageHistory(String project){
  try{
   org.json.JSONObject data=new org.json.JSONObject(getSharedPreferences("montage_history",0).getString(project,"{}"));
   org.json.JSONArray states=data.getJSONArray("states");
   MontageHistory h=new MontageHistory();
   if(states.length()==0||states.length()>51)return null;
   for(int i=0;i<states.length();i++)h.states.add(states.getString(i));
   h.cursor=data.getInt("cursor");
   if(h.cursor<0||h.cursor>=h.states.size())return null;
   return h;
  }catch(Exception ex){return null;}
 }
 boolean persistMontageHistory(String project,MontageHistory h){
  try{
   org.json.JSONArray states=new org.json.JSONArray();
   for(String state:h.states)states.put(state);
   return getSharedPreferences("montage_history",0).edit().putString(project,new org.json.JSONObject().put("states",states).put("cursor",h.cursor).toString()).commit();
  }catch(Exception ex){return false;}
 }

 void rememberMontageStation(String project,String before,String after){
  if(before.equals(after))return;
  MontageHistory h=loadMontageHistory(project);
  if(h==null||!h.states.get(h.cursor).equals(before)){h=new MontageHistory();h.states.add(before);}
  while(h.states.size()>h.cursor+1)h.states.remove(h.states.size()-1);
  h.states.add(after);h.cursor++;
  if(h.states.size()>51){h.states.remove(0);h.cursor--;}
  if(!persistMontageHistory(project,h))notice("Montage gespeichert, Verlauf konnte nicht gespeichert werden.");
 }
 void restoreMontageStation(android.app.AlertDialog dialog,String project,boolean redo){
  MontageHistory h=loadMontageHistory(project);
  if(h==null||!project.equals(activeProject)){notice("Keine Änderung verfügbar.");return;}
  int next=h.cursor+(redo?1:-1);
  if(next<0||next>=h.states.size())return;
  String current=getPreferences(0).getString("montageFittings","[]");
  if(!current.equals(h.states.get(h.cursor))){notice("Montagedaten wurden inzwischen geändert.");return;}
  if(!getPreferences(0).edit().putString("montageFittings",h.states.get(next)).commit()){notice("Speichern fehlgeschlagen.");return;}
  h.cursor=next;
  if(!persistMontageHistory(project,h))notice("Position gespeichert, Verlauf konnte nicht gespeichert werden.");
  recordProjectEvent(redo?"Montageposition wiederholt":"Montageposition rückgängig");if(sketch!=null)sketch.invalidate();if(livePreview!=null)livePreview.invalidate();dialog.dismiss();showMontageStationPlan();
 }
 void showMontageStationPlan(){
  try{
   double total=Double.parseDouble(getPreferences(0).getString("length","").trim().replace(',','.'));
   if(!Double.isFinite(total)||total<=0){notice("Gültige Tiefbau-Trassenlänge erforderlich.");return;}
   org.json.JSONArray rows=new org.json.JSONArray(getPreferences(0).getString("montageFittings","[]"));
   java.util.ArrayList<org.json.JSONObject> linked=new java.util.ArrayList<>();int missing=0;
   for(int i=0;i<rows.length();i++){org.json.JSONObject o=rows.getJSONObject(i);double st=o.optDouble("station",Double.NaN);if(!Double.isFinite(st)||st<0||st>total)missing++;else linked.add(o);}
   linked.sort((x,y)->Double.compare(x.optDouble("station"),y.optDouble("station")));
   int outside=0;
   double nominalWidth=Double.NaN;
   try{nominalWidth=Double.parseDouble(getPreferences(0).getString("width","").trim().replace(',','.'));}catch(NumberFormatException ignored){}
   org.json.JSONArray localWidths;
   try{localWidths=new org.json.JSONArray(getPreferences(0).getString("metricWidthSections","[]"));}catch(Exception ex){localWidths=null;}
   int unknownWidth=0, envelopeOutside=0, envelopeUnknown=0;
   for(org.json.JSONObject fitting:linked){
    double station=fitting.optDouble("station"),lateral=fitting.optDouble("offset",0),width=nominalWidth;
    int matches=0;
    if(localWidths!=null)for(int k=0;k<localWidths.length();k++){
     org.json.JSONObject section=localWidths.optJSONObject(k);if(section==null)continue;
     double from=section.optDouble("from"),to=section.optDouble("to"),w=section.optDouble("width");
     if(Double.isFinite(from)&&Double.isFinite(to)&&station>=from&&(station<to||(station==total&&station==to))){matches++;width=w;}
    }
    if(localWidths==null||matches>1||!Double.isFinite(width)||width<=0||!Double.isFinite(lateral)){unknownWidth++;continue;}
    if(Math.abs(lateral)>width/2)outside++;
    double envelope=fitting.optDouble("lateralEnvelope");
    if(!Double.isFinite(envelope)||envelope<=0){envelopeUnknown++;continue;}
    double requiredWidth=MontageGeometry.requiredTrenchWidth(lateral,envelope);
    boolean exceeds=requiredWidth>width;
    double longitudinal=fitting.optDouble("longitudinalEnvelope");
    if(Double.isFinite(longitudinal)&&longitudinal>0){
     double start=Math.max(0,station-longitudinal/2),end=Math.min(total,station+longitudinal/2);
     for(int k=0;k<localWidths.length();k++){
      org.json.JSONObject section=localWidths.optJSONObject(k);if(section==null)continue;
      double from=section.optDouble("from"),to=section.optDouble("to"),sectionWidth=section.optDouble("width");
      if(Double.isFinite(from)&&Double.isFinite(to)&&from<to&&from<end&&to>start&&Double.isFinite(sectionWidth)&&sectionWidth>0&&requiredWidth>sectionWidth)exceeds=true;
     }
    }
    if(exceeds)envelopeOutside++;
   }

   LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(12),dp(8),dp(12),dp(8));
   TextView hint=new TextView(this);hint.setText("Lineare Stationsübersicht – keine maßstäbliche Montagegeometrie. Ohne gültige Station: "+missing);panel.addView(hint);
   if(outside>0)panel.addView(sheetText("Hinweis: "+outside+" Formstück-Mittelpunkte außerhalb der örtlichen Grabenbreite. Bauteilabmessungen prüfen.",14,true));
   ElevationProfile montageGround=null;
   try{
    java.util.List<double[]> groundPoints=new java.util.ArrayList<>();
    for(String line:getPreferences(0).getString("metricElevationPoints","").split("\\n")){
     if(line.trim().isEmpty())continue;
     String[] parts=line.split(";",-1);if(parts.length!=2)throw new IllegalArgumentException("Format Station;Höhe");
     groundPoints.add(new double[]{Double.parseDouble(parts[0].trim().replace(',','.')),Double.parseDouble(parts[1].trim().replace(',','.'))});
    }
    montageGround=new ElevationProfile(groundPoints,total);
   }catch(Exception ignored){}
   int depthOutside=0, depthUnknown=0;
   double nominalDepth=Double.NaN;
   java.util.List<double[]> depthRanges=new java.util.ArrayList<>();
   boolean depthDataValid=true;
   try{
    String raw=getPreferences(0).getString("metricMainExcavationDepth","").trim();
    if(raw.isEmpty())raw=getPreferences(0).getString("depth","").trim();
    if(!raw.isEmpty())nominalDepth=Double.parseDouble(raw.replace(',','.'));
    for(String line:getPreferences(0).getString("metricDepthSections","").split("\\n")){
     if(line.trim().isEmpty())continue;
     String[] parts=line.split(";",-1);if(parts.length!=3)throw new IllegalArgumentException();
     double from=Double.parseDouble(parts[0].trim().replace(',','.')),to=Double.parseDouble(parts[1].trim().replace(',','.')),d=Double.parseDouble(parts[2].trim().replace(',','.'));
     if(!Double.isFinite(from)||!Double.isFinite(to)||!Double.isFinite(d)||from<0||to>total||from>=to||d<=0)throw new IllegalArgumentException();
     depthRanges.add(new double[]{from,to,d});
    }
   }catch(Exception ex){depthDataValid=false;}
   for(org.json.JSONObject fitting:linked){
    double st=fitting.optDouble("station"),cover=fitting.optDouble("cover"),height=fitting.optDouble("verticalEnvelope");
    if(!Double.isFinite(cover)&&montageGround!=null)cover=montageGround.elevation(st)-fitting.optDouble("topElevation");
    double depth=nominalDepth;int matches=0;
    for(double[] section:depthRanges)if(st>=section[0]&&(st<section[1]||(st==total&&st==section[1]))){depth=section[2];matches++;}
    if(!depthDataValid||matches>1||!Double.isFinite(depth)||depth<=0||!Double.isFinite(cover)||cover<0||!Double.isFinite(height)||height<=0){depthUnknown++;continue;}
    if(MontageGeometry.exceedsExcavationDepth(cover,height,depth))depthOutside++;
   }
   if(depthOutside>0)panel.addView(sheetText("Hinweis: "+depthOutside+" Formstücke reichen an ihrer Station unter die Grabensohle. Bettung und Tiefenlage prüfen.",14,true));
   if(depthUnknown>0)panel.addView(sheetText("Tiefenprüfung bei "+depthUnknown+" Formstücken offen: Grabentiefe, Überdeckung oder Bauhöhe fehlen bzw. sind widersprüchlich.",14,false));
   int derivedElevations=0, inconsistentElevations=0;
   if(montageGround!=null)for(org.json.JSONObject fitting:linked){
    double top=fitting.optDouble("topElevation"),cover=fitting.optDouble("cover"),ground=montageGround.elevation(fitting.optDouble("station"));
    if(Double.isFinite(top)&&Double.isFinite(cover)&&cover>=0&&Double.isFinite(ground)&&MontageGeometry.elevationMismatch(ground,top,cover))inconsistentElevations++;
   }
   if(inconsistentElevations>0)panel.addView(sheetText("Hinweis: Bei "+inconsistentElevations+" Formstücken weichen Höhenkote und Überdeckung gegenüber der Tiefbau-GOK um mehr als 1 cm ab. Angaben prüfen.",14,true));
   java.util.Map<org.json.JSONObject,Double> derivedTops=new java.util.IdentityHashMap<>();
   if(montageGround!=null)for(org.json.JSONObject fitting:linked){
    if(Double.isFinite(fitting.optDouble("topElevation")))continue;
    double coverValue=fitting.optDouble("cover"),ground=montageGround.elevation(fitting.optDouble("station"));
    if(Double.isFinite(coverValue)&&coverValue>=0&&Double.isFinite(ground)){derivedTops.put(fitting,ground-coverValue);derivedElevations++;}
   }
   if(montageGround!=null){
    final ElevationProfile profile=montageGround;
    final java.util.List<double[]> ground=profile.stations();
    final java.util.List<double[]> boxes=new java.util.ArrayList<>();
    double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
    for(double[] p:ground){low=Math.min(low,p[1]);high=Math.max(high,p[1]);}
    for(org.json.JSONObject fitting:linked){
     double z=fitting.optDouble("topElevation"),h=fitting.optDouble("verticalEnvelope"),st=fitting.optDouble("station"),len=fitting.optDouble("longitudinalEnvelope");
     if(!Double.isFinite(z)&&derivedTops.containsKey(fitting))z=derivedTops.get(fitting);
     if(!Double.isFinite(z)||!Double.isFinite(st))continue;
     double bottom=Double.isFinite(h)&&h>0?z-h:z;
     boxes.add(new double[]{st,z,bottom,Double.isFinite(len)&&len>0?len:0});
     low=Math.min(low,bottom);high=Math.max(high,z);
    }
    // Keep flat profiles centered and leave vertical clearance for symbols and labels.
    final double elevationSpan=Math.max(0.1,high-low);
    final double base=low-(elevationSpan-(high-low))/2-elevationSpan*0.1,range=elevationSpan*1.2;
    panel.addView(sheetText("Längsschnitt – Formstücke und GOK (schematisch, überhöht)",16,true));
    android.view.View longitudinalView=new android.view.View(this){
     final android.graphics.Paint p=new android.graphics.Paint(3);
     @Override protected void onDraw(android.graphics.Canvas c){
      super.onDraw(c);float pad=24*getResources().getDisplayMetrics().density;
      float w=getWidth()-2*pad,h=getHeight()-2*pad;if(w<=0||h<=0)return;
      p.setTextSize(10*getResources().getDisplayMetrics().scaledDensity);
      p.setStrokeWidth(1);
      for(int i=0;i<=4;i++){
       float x=pad+w*i/4f,y=pad+h*i/4f;
       p.setColor(0xff444444);c.drawLine(x,pad,x,pad+h,p);c.drawLine(pad,y,pad+w,y,p);
       p.setColor(0xffcccccc);p.setTextAlign(android.graphics.Paint.Align.CENTER);
       c.drawText(String.format(java.util.Locale.GERMANY,"%.2f",total*i/4),x,pad+h+14*getResources().getDisplayMetrics().density,p);
       p.setTextAlign(android.graphics.Paint.Align.LEFT);
       c.drawText(String.format(java.util.Locale.GERMANY,"%.2f",base+range*(1-i/4.0)),2,y-2,p);
      }
      p.setTextAlign(android.graphics.Paint.Align.LEFT);
      p.setColor(0xffad987c);p.setStrokeWidth(3);
      for(int i=1;i<ground.size();i++){double[] a=ground.get(i-1),b=ground.get(i);c.drawLine(pad+(float)(a[0]/total*w),pad+(float)((1-(a[1]-base)/range)*h),pad+(float)(b[0]/total*w),pad+(float)((1-(b[1]-base)/range)*h),p);}
      for(int i=1;i<ground.size();i++){
       double[] a=ground.get(i-1),b=ground.get(i);
       float x=pad+(float)((a[0]+b[0])/2/total*w),y=pad+(float)((1-((a[1]+b[1])/2-base)/range)*h);
       p.setColor(0xffad987c);p.setTextAlign(android.graphics.Paint.Align.CENTER);
       c.drawText(String.format(java.util.Locale.GERMANY,"%+.1f %%",profile.fallPercent(a[0],b[0])),x,Math.max(12,y-7),p);
      }
      p.setTextAlign(android.graphics.Paint.Align.LEFT);
      p.setColor(0xffeab65d);
      for(double[] b:boxes){float x=pad+(float)(b[0]/total*w),y=pad+(float)((1-(b[1]-base)/range)*h),bottom=pad+(float)((1-(b[2]-base)/range)*h),half=(float)(b[3]/total*w/2);if(half>0&&bottom>y)c.drawRect(x-half,y,x+half,bottom,p);else c.drawCircle(x,y,4,p);}
     }
    };
    longitudinalView.setBackgroundColor(Color.BLACK);
    panel.addView(longitudinalView,new android.widget.LinearLayout.LayoutParams(-1,(int)(220*getResources().getDisplayMetrics().density)));
    panel.addView(sheetText("Darstellbare Formstücke: "+boxes.size()+" / "+linked.size()+". Fehlende Abmessungen als Punkt.",13,false));
   }
   if(derivedElevations>0)panel.addView(sheetText("Höhenkoten aus Tiefbau-GOK und Überdeckung berechnet: "+derivedElevations+". Gemeinsamen Höhenbezug prüfen.",14,false));
   int possibleConflicts=0, verticallySeparated=0, incompleteLongitudinalChecks=0;
   for(int i=0;i<linked.size();i++){
    org.json.JSONObject first=linked.get(i);
    double st=first.optDouble("station"),w=first.optDouble("lateralEnvelope"),off=first.optDouble("offset",0);
    if(!Double.isFinite(w)||w<=0||!Double.isFinite(off))continue;
    for(int j=i+1;j<linked.size();j++){
     org.json.JSONObject second=linked.get(j);
     double delta=Math.abs(second.optDouble("station")-st);
     double l1=first.optDouble("longitudinalEnvelope"),l2=second.optDouble("longitudinalEnvelope");
     boolean lengthsKnown=Double.isFinite(l1)&&l1>0&&Double.isFinite(l2)&&l2>0;
     if(!Double.isFinite(delta))continue;
     if(lengthsKnown&&delta>=(l1+l2)/2)continue;
     double w2=second.optDouble("lateralEnvelope"),off2=second.optDouble("offset",0);
     if(!Double.isFinite(w2)||w2<=0||!Double.isFinite(off2))continue;
     if(Math.abs(off-off2)<(w+w2)/2){
      if(!lengthsKnown&&delta>0.001){incompleteLongitudinalChecks++;continue;}
      double c1=first.optDouble("cover"),c2=second.optDouble("cover"),h1=first.optDouble("verticalEnvelope"),h2=second.optDouble("verticalEnvelope");
      // Cover is measured from the local surface; different stations may have different surface elevations.
      double z1=first.optDouble("topElevation"),z2=second.optDouble("topElevation");
      if(!Double.isFinite(z1)&&derivedTops.containsKey(first))z1=derivedTops.get(first);
      if(!Double.isFinite(z2)&&derivedTops.containsKey(second))z2=derivedTops.get(second);
      boolean separated=MontageGeometry.verticallySeparated(delta,c1,c2,h1,h2,z1,z2);
      if(separated)verticallySeparated++;else possibleConflicts++;
     }
    }
   }
   if(incompleteLongitudinalChecks>0)panel.addView(sheetText("Längsprüfung bei "+incompleteLongitudinalChecks+" Formstückpaaren offen: Längsabmessungen ergänzen.",14,false));
   if(verticallySeparated>0)panel.addView(sheetText("Bei "+verticallySeparated+" Überlappungen sind die angegebenen Höhen- oder Tiefenintervalle getrennt. Mindestabstand und Bezugshöhe prüfen.",14,false));
   if(possibleConflicts>0)panel.addView(sheetText("Hinweis: "+possibleConflicts+" mögliche Formstück-Überlappungen in der Draufsicht. Höhenlage, fehlende Längsmaße und Anschluss prüfen.",14,true));
   if(envelopeOutside>0)panel.addView(sheetText("Hinweis: "+envelopeOutside+" Formstücke überschreiten mit ihrer angegebenen Querabmessung die Grabenbreite.",14,true));
   if(envelopeUnknown>0)panel.addView(sheetText("Querabmessung fehlt bei "+envelopeUnknown+" Formstücken – vollständige Breitenprüfung offen.",14,false));
   if(unknownWidth>0)panel.addView(sheetText("Seitliche Lage bei "+unknownWidth+" Formstücken nicht prüfbar: Breitenangaben fehlen oder sind widersprüchlich.",14,false));
   android.view.View axis=new android.view.View(this){
    final android.graphics.Paint paint=new android.graphics.Paint(3);
    @Override protected void onDraw(android.graphics.Canvas c){
     super.onDraw(c);float left=dp(18),right=getWidth()-dp(18),y=dp(42);if(right<=left)return;
     paint.setColor(0xff9b876c);paint.setStrokeWidth(dp(18));c.drawLine(left,y,right,y,paint);
     paint.setColor(0xff398bda);paint.setStrokeWidth(dp(2));c.drawLine(left,y,right,y,paint);
     for(org.json.JSONObject o:linked){float x=left+(right-left)*(float)(o.optDouble("station")/total);paint.setColor(0xffe6a23c);c.drawCircle(x,y,dp(5),paint);}
    }
   };panel.addView(axis,new LinearLayout.LayoutParams(-1,dp(70)));
   final String stationProject=activeProject;
   final Runnable[] refreshStationPlanHolder=new Runnable[1];
   Runnable refreshStationPlan=()->{if(refreshStationPlanHolder[0]!=null)refreshStationPlanHolder[0].run();};
   axis.setContentDescription("Station auf der linearen Trasse wählen");
   axis.setOnTouchListener(new View.OnTouchListener(){float startX,startY;public boolean onTouch(View v,android.view.MotionEvent e){if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){startX=e.getX();startY=e.getY();return true;}if(e.getActionMasked()==android.view.MotionEvent.ACTION_UP){if(Math.hypot(e.getX()-startX,e.getY()-startY)>dp(12))return true;if(!stationProject.equals(activeProject)||v.getWidth()<=dp(36))return true;double st=Math.round(Math.max(0,Math.min(1,(e.getX()-dp(18))/(v.getWidth()-2.0*dp(18))))*total*1000)/1000.0;st=Math.min(total,st);final double chosen=st;String[] labels=new String[rows.length()];for(int j=0;j<labels.length;j++){try{labels[j]=montageDescription(rows.getJSONObject(j));}catch(Exception ex){labels[j]="Formstück "+(j+1);}}if(labels.length==0){notice("Zuerst Formstück hinzufügen.");return true;}new android.app.AlertDialog.Builder(MainActivity.this).setTitle(String.format(java.util.Locale.GERMANY,"Station %.3f m – Formstück wählen",chosen)).setItems(labels,(dd,j)->{try{if(!stationProject.equals(activeProject))throw new IllegalStateException();org.json.JSONArray latest=new org.json.JSONArray(getPreferences(0).getString("montageFittings","[]"));int idx=findMontageItem(latest,rows.getJSONObject(j));String before=latest.toString();setPlanStation(latest.getJSONObject(idx),chosen);if(!getPreferences(0).edit().putString("montageFittings",latest.toString()).commit())throw new java.io.IOException();rememberMontageStation(stationProject,before,latest.toString());recordProjectEvent("Formstück stationiert");if(sketch!=null)sketch.invalidate();if(livePreview!=null)livePreview.invalidate();refreshStationPlan.run();}catch(Exception ex){notice("Projektdaten geändert oder Speichern fehlgeschlagen.");}}).setNegativeButton("Abbrechen",null).show();return true;}return true;}});
   TextView range=new TextView(this);range.setText("0,00 m  —  "+total+" m");panel.addView(range);
   RouteSketch geometry=new RouteSketch(this,routeData());
   double measured=geometry.measuredLength();
   boolean metricPlan=DxfExport.metricAvailable(geometry)&&Math.abs(measured-total)<0.001;
   MontagePlanView map=metricPlan?new MontagePlanView(this,new DxfExport(new java.io.StringWriter(),geometry,true),rows):null;
   if(map!=null){
    panel.addView(sheetText("Montageplan · Abschnittslängen in Metern, Richtungen aus der Skizze",15,true));
    panel.addView(map,new LinearLayout.LayoutParams(-1,dp(240)));
   }else panel.addView(sheetText("Geometrische Zuordnung offen: Skizzenmaße fehlen oder stimmen nicht mit der Trassenlänge überein.",14,false));
   android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(panel);
   android.app.AlertDialog.Builder builder=new android.app.AlertDialog.Builder(this).setTitle("Gesamtplan – Stationierung").setView(scroll)
       .setNegativeButton("Zurück",(d,w)->showMontage());
   if(map!=null)builder.setPositiveButton("Plan als PDF",(d,w)->{
    android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);intent.setType("application/pdf");
    intent.putExtra(android.content.Intent.EXTRA_TITLE,"Montageplan.pdf");pendingPlanProject=activeProject;startActivityForResult(intent,431);
   });
   android.app.AlertDialog dialog=builder.create();
   refreshStationPlanHolder[0]=()->{dialog.dismiss();showMontageStationPlan();};
   MontageHistory history=loadMontageHistory(stationProject);
   if(history!=null){
    String current=getPreferences(0).getString("montageFittings","[]");
    LinearLayout actions=new LinearLayout(this);
    Button undo=new Button(this);undo.setText("Rückgängig");undo.setEnabled(history.cursor>0&&current.equals(history.states.get(history.cursor)));actions.addView(undo,new LinearLayout.LayoutParams(0,-2,1));undo.setOnClickListener(v->restoreMontageStation(dialog,stationProject,false));
    Button redo=new Button(this);redo.setText("Wiederholen");redo.setEnabled(history.cursor+1<history.states.size()&&current.equals(history.states.get(history.cursor)));actions.addView(redo,new LinearLayout.LayoutParams(0,-2,1));redo.setOnClickListener(v->restoreMontageStation(dialog,stationProject,true));panel.addView(actions);
   }
   if(map!=null){
    map.setOnTouchListener(new View.OnTouchListener(){
     float startX,startY;int dragged=-1;
     public boolean onTouch(View v,android.view.MotionEvent e){
      if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){startX=e.getX();startY=e.getY();dragged=map.fittingAt(startX,startY);if(dragged>=0)v.getParent().requestDisallowInterceptTouchEvent(true);return true;}
      if(e.getActionMasked()==android.view.MotionEvent.ACTION_MOVE&&dragged>=0){org.json.JSONObject row=rows.optJSONObject(dragged);if(row!=null)map.previewStation(dragged,map.stationAt(e.getX(),e.getY()),row.optDouble("offset",0));return true;}
      if(e.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL){map.clearPreview();dragged=-1;v.getParent().requestDisallowInterceptTouchEvent(false);return true;}
      if(e.getActionMasked()!=android.view.MotionEvent.ACTION_UP)return true;
      v.getParent().requestDisallowInterceptTouchEvent(false);
      if(dragged<0&&Math.hypot(e.getX()-startX,e.getY()-startY)>dp(12))return true;
      map.clearPreview();double pos=map.stationAt(e.getX(),e.getY());if(!Double.isFinite(pos)){dragged=-1;return true;}
      final double chosen=Math.min(total,Math.round(pos*1000)/1000.0);
      if(rows.length()==0){notice("Zuerst Formstück hinzufügen.");return true;}
      String[] labels=new String[rows.length()];
      for(int j=0;j<labels.length;j++){try{labels[j]=planFittingLabel(rows.getJSONObject(j),j,map);}catch(Exception ex){labels[j]="Formstück "+(j+1);}}
      java.util.function.IntConsumer assign=j->{
       try{
        if(!stationProject.equals(activeProject))throw new IllegalStateException();
        double currentLength=Double.parseDouble(getPreferences(0).getString("length","").trim().replace(',','.'));
        if(!Double.isFinite(currentLength)||Math.abs(currentLength-total)>0.000001)throw new IllegalStateException();
        org.json.JSONArray latest=new org.json.JSONArray(getPreferences(0).getString("montageFittings","[]"));
        int idx=findMontageItem(latest,rows.getJSONObject(j));
        String before=latest.toString();
        setPlanStation(latest.getJSONObject(idx),chosen);
        if(!getPreferences(0).edit().putString("montageFittings",latest.toString()).commit())throw new java.io.IOException();
        rememberMontageStation(stationProject,before,latest.toString());
        recordProjectEvent("Formstück im Montageplan stationiert");if(sketch!=null)sketch.invalidate();if(livePreview!=null)livePreview.invalidate();
        dialog.dismiss();showMontageStationPlan();
       }catch(Exception ex){notice("Projektdaten geändert oder Speichern fehlgeschlagen.");}
      };if(dragged>=0){if(Math.hypot(e.getX()-startX,e.getY()-startY)>dp(4))assign.accept(dragged);else{dialog.dismiss();org.json.JSONObject row=rows.optJSONObject(dragged);if(row!=null)showMontageFitting(MontageCatalog.Fitting.valueOf(row.optString("type")),row);}}else new android.app.AlertDialog.Builder(MainActivity.this).setTitle(String.format(java.util.Locale.GERMANY,"Station %.3f m – Formstück wählen",chosen)).setItems(labels,(dd,j)->assign.accept(j)).setNegativeButton("Abbrechen",null).show();dragged=-1;return true;
     }
    });
   }
   for(int i=0;i<rows.length();i++){
    org.json.JSONObject row=rows.getJSONObject(i);Button item=new Button(this);item.setAllCaps(false);
    item.setText(planFittingLabel(row,i,map));item.setOnClickListener(v->{dialog.dismiss();showMontageFitting(MontageCatalog.Fitting.valueOf(row.optString("type")),row);});panel.addView(item);
   }
   dialog.show();
  }catch(Exception e){notice("Stationsübersicht nicht verfügbar. Trassenlänge und Projektdaten prüfen.");}
 }
 String pendingPlanProject,pendingProtocolsProject;
 String planFittingLabel(org.json.JSONObject row,int index,MontagePlanView map)throws org.json.JSONException{
  String station=row.has("station")?row.optString("station")+" m":"offen";
  return (index+1)+". "+montageDescription(row)+" · Station "+station
      +(row.has("offset")?" · Versatz "+row.optString("offset")+" m":"")
      +(map!=null&&!map.hasPosition(index)?" · Lage offen":"");
 }
 MontagePlanView appendMontageDrawing(android.graphics.pdf.PdfDocument pdf,int pageNumber,RouteSketch route,org.json.JSONArray fittings,double total)throws Exception{
   MontagePlanView map=new MontagePlanView(this,new DxfExport(new java.io.StringWriter(),route,true),fittings);
   android.graphics.pdf.PdfDocument.Page page=pdf.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(842,595,pageNumber).create());
   String name=getPreferences(0).getString("project","Projekt");
   try{
    Canvas canvas=page.getCanvas();Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);pen.setColor(INK);pen.setTextSize(19);
    canvas.drawText("Montageplan",28,36,pen);pen.setTextSize(12);
    canvas.drawText(name,28,58,pen);canvas.save();canvas.translate(28,76);map.render(canvas,786,445);canvas.restore();
    canvas.drawText("0,00 – "+String.format(java.util.Locale.GERMANY,"%.2f m",total)+" · Abschnittslängen gemessen, Richtungen aus der Skizze",28,553,pen);
    canvas.drawText("Seite "+pageNumber,765,566,pen);
   }finally{pdf.finishPage(page);}
   return map;
 }
 void writeMontagePlan(android.net.Uri target){
  android.graphics.pdf.PdfDocument pdf=new android.graphics.pdf.PdfDocument();
  try{
   RouteSketch route=new RouteSketch(this,routeData());
   double total=Double.parseDouble(getPreferences(0).getString("length","").trim().replace(',','.'));
   if(!Double.isFinite(total)||!DxfExport.metricAvailable(route)||Math.abs(route.measuredLength()-total)>=0.001){notice("Trassenmaße prüfen und Export erneut starten.");return;}
   org.json.JSONArray fittings=new org.json.JSONArray(getPreferences(0).getString("montageFittings","[]"));
   MontagePlanView map=appendMontageDrawing(pdf,1,route,fittings,total);
   String name=getPreferences(0).getString("project","Projekt");
   StringBuilder list=new StringBuilder(name).append("\n\n");
   for(int i=0;i<fittings.length();i++)list.append(planFittingLabel(fittings.getJSONObject(i),i,map)).append("\n\n");
   if(fittings.length()==0)list.append("Keine Formstücke erfasst.");
   dailyTextPdf(pdf,2,"Montageplan · Formstückverzeichnis",list.toString(),null);
   try(java.io.OutputStream out=getContentResolver().openOutputStream(target,"wt")){
    if(out==null)throw new java.io.IOException();pdf.writeTo(out);
   }
   notice("Montageplan gespeichert.");
  }catch(Exception e){notice("Montageplan konnte nicht gespeichert werden.");}finally{pdf.close();}
 }
 void showMontageCatalog(){
  android.widget.ScrollView scroll=new android.widget.ScrollView(this);
  LinearLayout grid=new LinearLayout(this);grid.setOrientation(1);grid.setPadding(dp(8),dp(8),dp(8),dp(8));scroll.addView(grid);
  TextView hint=new TextView(this);hint.setText("Schematische Symbole – DIN-Prüfung ausstehend");grid.addView(hint);
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Formstückkatalog").setView(scroll).setNegativeButton("Zurück",(d,w)->showMontage()).create();
  LinearLayout row=null;int index=0;
  for(MontageCatalog.Fitting fitting:MontageCatalog.Fitting.values()){
   if(index++%3==0){row=new LinearLayout(this);row.setOrientation(0);grid.addView(row);}
   LinearLayout tile=new LinearLayout(this);tile.setOrientation(1);tile.setGravity(android.view.Gravity.CENTER);tile.setPadding(dp(4),dp(6),dp(4),dp(6));
   row.addView(tile,new LinearLayout.LayoutParams(0,-2,1));
   MontageSymbolView symbol=new MontageSymbolView(this,fitting);tile.addView(symbol,new LinearLayout.LayoutParams(-1,dp(72)));
   TextView label=new TextView(this);label.setText(fitting.label);label.setGravity(android.view.Gravity.CENTER);tile.addView(label,new LinearLayout.LayoutParams(-1,dp(48)));
   tile.setContentDescription(fitting.label);tile.setFocusable(true);tile.setOnClickListener(v->{dialog.dismiss();showMontageFitting(fitting,null);});
  }
  dialog.show();
 }
 static void setPlanStation(org.json.JSONObject fitting,double station) throws org.json.JSONException {
  fitting.put("station",station).put("stationLinked",true);
  fitting.remove("routePointId");
 }
 void chooseFittingStation(String projectId,EditText field){
  if(!projectId.equals(activeProject)){notice("Projekt wurde gewechselt.");return;}
  double max;
  try{max=Double.parseDouble(getPreferences(0).getString("length","").trim().replace(',','.'));}
  catch(NumberFormatException ex){max=Double.NaN;}
  if(!Double.isFinite(max)||max<=0){notice("Zuerst gültige Trassenlänge erfassen.");return;}
  java.util.Map<Double,String> pointIds=new java.util.HashMap<>();
  java.util.TreeMap<Double,String> stations=new java.util.TreeMap<>();
  stations.put(0.0,"");stations.put(max,"");
  org.json.JSONArray utilities=extraUtilities();
  for(int j=0;j<utilities.length();j++){
   org.json.JSONObject utility=utilities.optJSONObject(j);
   if(utility==null)continue;
   for(String key:new String[]{"routeFrom","routeTo"}){
    double value=utility.optDouble(key);
    if(Double.isFinite(value)&&value>=0&&value<=max)stations.put(value,"");
   }
  }
  RouteSketch route=new RouteSketch(this,routeData());
  java.math.BigDecimal distance=java.math.BigDecimal.ZERO;
  for(int j=0;j<route.vertices.size();j++){
   RouteSketch.Vertex point=route.vertices.get(j);
   if(j>0){
    if(!Double.isFinite(point.metres)||point.metres<=0)break;
    distance=distance.add(java.math.BigDecimal.valueOf(point.metres));
   }
   double value=distance.doubleValue();
   if(value>max)break;
   String name=point.name.trim();
   if(point.hasStoredId)pointIds.put(value,point.id);
   stations.put(value," · Punkt "+(j+1)+(name.isEmpty()?"":" · "+name));
  }
  java.util.List<Double> values=new java.util.ArrayList<>(stations.keySet());
  String[] labels=new String[values.size()];
  for(int j=0;j<labels.length;j++){
   double value=values.get(j);
   labels[j]=String.format(java.util.Locale.GERMANY,"Station %.3f m",value)+stations.get(value);
  }
  new android.app.AlertDialog.Builder(this).setTitle("Trassen- und Leitungsstationen")
   .setItems(labels,(d,j)->{
    double value=values.get(j);
    field.setText(java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString());
    field.setTag(pointIds.get(value));
   })
   .setNegativeButton("Abbrechen",null).show();
 }
 void showMontageFitting(MontageCatalog.Fitting fitting, org.json.JSONObject original){
  showMontageFitting(fitting,original,null,null,null);
 }
 void showMontageFitting(MontageCatalog.Fitting fitting,org.json.JSONObject original,String source,FittingTextParser.Candidate suggestion,java.util.function.Consumer<Boolean> reviewResult){
  final String projectId=activeProject;
  LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);
  MontageSymbolView preview=new MontageSymbolView(this,fitting);panel.addView(preview,new LinearLayout.LayoutParams(-1,dp(100)));
  EditText dn=new EditText(this);dn.setHint("DN (optional)");dn.setInputType(2);panel.addView(dn);
  EditText material=new EditText(this);material.setHint("Werkstoff (optional)");panel.addView(material);
  EditText rotation=new EditText(this);rotation.setHint("Drehwinkel (°)");rotation.setInputType(8194|4096);rotation.setText("0");panel.addView(rotation);
  android.widget.SeekBar rotationSlider=new android.widget.SeekBar(this);rotationSlider.setMax(360);rotationSlider.setContentDescription("Drehwinkel 0 bis 360 Grad");panel.addView(rotationSlider);
  rotationSlider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(android.widget.SeekBar bar,int progress,boolean fromUser){if(fromUser)rotation.setText(String.valueOf(progress));}public void onStartTrackingTouch(android.widget.SeekBar bar){}public void onStopTrackingTouch(android.widget.SeekBar bar){}});
  android.widget.CheckBox mirror=new android.widget.CheckBox(this);mirror.setText("Spiegeln");panel.addView(mirror);
  EditText station=new EditText(this);station.setHint("Station Tiefbau (m, optional)");station.setInputType(8194);panel.addView(station);
  Button pickStation=new Button(this);pickStation.setText("Station aus Projekt wählen");panel.addView(pickStation);
  pickStation.setOnClickListener(v->chooseFittingStation(projectId,station));
  EditText offset=new EditText(this);offset.setHint("Seitlicher Versatz (m, + rechts / − links)");offset.setInputType(8194|4096);panel.addView(offset);
  android.widget.SeekBar offsetSlider=new android.widget.SeekBar(this);offsetSlider.setMax(1000);offsetSlider.setProgress(500);offsetSlider.setContentDescription("Seitlicher Versatz minus fünf bis plus fünf Meter");panel.addView(offsetSlider);
  TextView offsetRange=new TextView(this);offsetRange.setText("−5 m links · 0 m · +5 m rechts");panel.addView(offsetRange);
  offsetSlider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(android.widget.SeekBar bar,int progress,boolean fromUser){if(fromUser)offset.setText(String.format(java.util.Locale.GERMANY,"%.2f",(progress-500)/100.0));}public void onStartTrackingTouch(android.widget.SeekBar bar){}public void onStopTrackingTouch(android.widget.SeekBar bar){}});
  offset.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void afterTextChanged(android.text.Editable s){}public void onTextChanged(CharSequence s,int start,int before,int count){try{double m=Double.parseDouble(s.toString().trim().replace(',','.'));if(Double.isFinite(m))offsetSlider.setProgress((int)Math.round(500+100*Math.max(-5,Math.min(5,m))));}catch(NumberFormatException ex){if(s.length()==0)offsetSlider.setProgress(500);}}});
  Button resetOffset=new Button(this);resetOffset.setText("Versatz auf 0 setzen");panel.addView(resetOffset);resetOffset.setOnClickListener(v->offset.setText("0"));

  EditText elevation=new EditText(this);elevation.setHint("Absolute Oberkante Formstück (m, gemeinsamer Höhenbezug, optional)");elevation.setInputType(8194|4096);panel.addView(elevation);
  EditText vertical=new EditText(this);vertical.setHint("Tatsächliche Bauhöhe (m, optional)");vertical.setInputType(8194);panel.addView(vertical);
  EditText longitudinal=new EditText(this);longitudinal.setHint("Tatsächliche Längsabmessung zur Trasse (m, optional)");longitudinal.setInputType(8194);panel.addView(longitudinal);
  EditText envelope=new EditText(this);envelope.setHint("Tatsächliche Querabmessung zur Trasse (m, optional)");envelope.setInputType(8194);panel.addView(envelope);
  EditText cover=new EditText(this);cover.setHint("Überdeckung bis Rohroberkante (m, optional)");cover.setInputType(8194);panel.addView(cover);
  android.widget.CheckBox installed=new android.widget.CheckBox(this);
  installed.setText("Einbau bestätigt · in Materialliste übernehmen");panel.addView(installed);
  if(original!=null){
   dn.setText(original.optString("dn"));material.setText(original.optString("material"));
   rotation.setText(original.optString("rotation","0"));mirror.setChecked(original.optBoolean("mirrored"));
   station.setText(original.optString("station"));offset.setText(original.optString("offset"));cover.setText(original.optString("cover"));
   elevation.setText(original.optString("topElevation"));
   vertical.setText(original.optString("verticalEnvelope"));
   longitudinal.setText(original.optString("longitudinalEnvelope"));
   envelope.setText(original.optString("lateralEnvelope"));
   installed.setChecked(original.optBoolean("installed",false));
  }
  if(suggestion!=null){
   material.setText(suggestion.material);
   dn.setText(suggestion.dn);station.setText(suggestion.station.replace(',','.'));
   panel.addView(sheetText("Quelle: "+source+" · Zeile "+suggestion.line+"\n"+suggestion.evidence,14,false));
  }else if(original!=null&&original.has("sourceLine")){
   panel.addView(sheetText("Quelle: "+original.optString("sourceName")+" · Zeile "+original.optInt("sourceLineNumber")+"\n"+original.optString("sourceLine"),14,false));
  }
  TextView pointStatus=new TextView(this);
  panel.addView(pointStatus,panel.indexOfChild(pickStation)+1);
  pointStatus.setVisibility(View.GONE);
  station.addTextChangedListener(new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){station.setTag(null);pointStatus.setVisibility(View.GONE);}
   public void afterTextChanged(android.text.Editable s){}
  });
  if(original!=null&&!original.optString("routePointId").isEmpty()){
   String id=original.optString("routePointId");
   station.setTag(id);
   RouteSketch route=new RouteSketch(this,routeData());
   int index=findPoint(route,id);
   String message="Zugeordneter Trassenpunkt fehlt. Station prüfen.";
   if(index>=0){
    RouteSketch.Vertex point=route.vertices.get(index);
    String label="Punkt "+(index+1)+(point.name.isEmpty()?"":" · "+point.name);
    double current=photoStation(route,index);
    if(!Double.isFinite(current))message=label+" · Abschnittslänge fehlt. Station prüfen.";
    else if(Math.abs(current-original.optDouble("station"))>1e-9)
     message=String.format(java.util.Locale.GERMANY,"%s · Jetzt bei %.3f m. Zum Übernehmen Punkt erneut wählen.",label,current);
    else message=label;
   }
   pointStatus.setText(message);
   pointStatus.setVisibility(View.VISIBLE);
  }
  Runnable updatePreview=()->{
   try{double degrees=MontageCatalog.normalizeRotation(Double.parseDouble(rotation.getText().toString().trim().replace(',','.')));preview.setRotation((float)degrees);if(!rotationSlider.isPressed())rotationSlider.setProgress((int)Math.round(degrees));}
   catch(IllegalArgumentException invalid){preview.setRotation(0);}
   preview.setScaleX(mirror.isChecked()?-1:1);
  };
  rotation.addTextChangedListener(new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){updatePreview.run();}
   public void afterTextChanged(android.text.Editable s){}
  });
  mirror.setOnCheckedChangeListener((button,checked)->updatePreview.run());updatePreview.run();
  android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(panel);
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle(fitting.label).setView(scroll).setNegativeButton("Zurück",(d,w)->{if(reviewResult!=null)reviewResult.accept(false);else if(original==null)showMontageCatalog();else showMontageInventory();}).setPositiveButton(original==null?"Zum Projekt hinzufügen":"Änderungen speichern",null).create();
  if(reviewResult!=null)dialog.setOnCancelListener(d->reviewResult.accept(false));
  if(original!=null)dialog.setButton(android.app.AlertDialog.BUTTON_NEUTRAL,"Entfernen",(android.content.DialogInterface.OnClickListener)(d,w)->
   new android.app.AlertDialog.Builder(this).setTitle("Formstück entfernen?")
    .setMessage("Das Formstück wird aus dem Projekt, dem Stationsplan und der Materialliste entfernt.")
    .setNegativeButton("Abbrechen",(confirm,which)->showMontageInventory())
    .setPositiveButton("Entfernen",(confirm,which)->{
     if(!projectId.equals(activeProject)){notice("Projekt wurde gewechselt.");return;}
     try{
      org.json.JSONArray rows=new org.json.JSONArray(getPreferences(0).getString("montageFittings","[]"));
      String before=rows.toString();
      rows.remove(findMontageItem(rows,original));
      if(!getPreferences(0).edit().putString("montageFittings",rows.toString()).commit())throw new java.io.IOException();
      rememberMontageStation(projectId,before,rows.toString());if(sketch!=null)sketch.invalidate();if(livePreview!=null)livePreview.invalidate();
      showMontageInventory();
     }catch(Exception e){notice("Formstück konnte nicht entfernt werden.");}
    }).show());
  dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{
   if(!projectId.equals(activeProject)){notice("Projekt wurde gewechselt.");return;}
   String diameter=dn.getText().toString().trim();
   if(!diameter.isEmpty()&&!diameter.matches("[1-9][0-9]{0,3}")){dn.setError("Ungültige Nennweite");return;}
   double angle;try{angle=MontageCatalog.normalizeRotation(Double.parseDouble(rotation.getText().toString().trim().replace(',','.')));}catch(Exception e){rotation.setError("Ungültiger Winkel");return;}
   try{
    org.json.JSONArray rows=new org.json.JSONArray(getPreferences(0).getString("montageFittings","[]"));
    String before=rows.toString();
    int index=original==null?-1:findMontageItem(rows,original);
    org.json.JSONObject item=original==null?new org.json.JSONObject().put("id",java.util.UUID.randomUUID().toString()).put("placed",false):new org.json.JSONObject(rows.getJSONObject(index).toString());
    item.put("type",fitting.name()).put("dn",diameter).put("material",material.getText().toString().trim()).put("rotation",angle).put("mirrored",mirror.isChecked()).put("installed",installed.isChecked());
    EditText[] coordinates={station,offset,cover};String[] keys={"station","offset","cover"};
    if(station.length()==0&&(offset.length()>0||cover.length()>0)){station.setError("Station für die Zuordnung erforderlich");return;}
    for(int i=0;i<coordinates.length;i++){String raw=coordinates[i].getText().toString().trim();if(raw.isEmpty()){item.remove(keys[i]);continue;}double value;
     try{value=Double.parseDouble(raw.replace(',','.'));if(!Double.isFinite(value)||(i!=1&&value<0))throw new IllegalArgumentException();}catch(Exception e){coordinates[i].setError("Ungültiges Maß");return;}
     if(i==0){String routeLength=getPreferences(0).getString("length","").trim();try{double max=Double.parseDouble(routeLength.replace(',','.'));if(!Double.isFinite(max)||max<=0||value>max)throw new IllegalArgumentException();}catch(Exception e){station.setError("Station innerhalb einer gültigen Tiefbau-Trassenlänge erforderlich");return;}}
     item.put(keys[i],value);
    }
    if(station.length()>0&&station.getTag() instanceof String)item.put("routePointId",station.getTag());
    else item.remove("routePointId");
    if(suggestion!=null)item.put("sourceName",source).put("sourceLine",suggestion.evidence).put("sourceLineNumber",suggestion.line);
    String rawElevation=elevation.getText().toString().trim();if(rawElevation.isEmpty())item.remove("topElevation");else{try{double value=Double.parseDouble(rawElevation.replace(',','.'));if(!Double.isFinite(value))throw new IllegalArgumentException();item.put("topElevation",value);}catch(Exception ex){elevation.setError("Gültige Höhenkote erforderlich");return;}}
    String rawVertical=vertical.getText().toString().trim();if(rawVertical.isEmpty())item.remove("verticalEnvelope");else{try{double value=Double.parseDouble(rawVertical.replace(',','.'));if(!Double.isFinite(value)||value<=0)throw new IllegalArgumentException();item.put("verticalEnvelope",value);}catch(Exception ex){vertical.setError("Positive Bauhöhe erforderlich");return;}}
    String rawLong=longitudinal.getText().toString().trim();if(rawLong.isEmpty())item.remove("longitudinalEnvelope");else{try{double value=Double.parseDouble(rawLong.replace(',','.'));if(!Double.isFinite(value)||value<=0)throw new IllegalArgumentException();item.put("longitudinalEnvelope",value);}catch(Exception ex){longitudinal.setError("Positive Längsabmessung erforderlich");return;}}
    String rawEnvelope=envelope.getText().toString().trim();if(rawEnvelope.isEmpty())item.remove("lateralEnvelope");else{try{double value=Double.parseDouble(rawEnvelope.replace(',','.'));if(!Double.isFinite(value)||value<=0)throw new IllegalArgumentException();item.put("lateralEnvelope",value);}catch(Exception ex){envelope.setError("Positive Querabmessung erforderlich");return;}}
    item.put("stationLinked",item.has("station"));if(index<0)rows.put(item);else rows.put(index,item);
    if(!getPreferences(0).edit().putString("montageFittings",rows.toString()).commit()){notice("Speichern fehlgeschlagen.");return;}
    rememberMontageStation(projectId,before,rows.toString());if(sketch!=null)sketch.invalidate();if(livePreview!=null)livePreview.invalidate();
    dialog.dismiss();notice("Formstück gespeichert.");if(reviewResult!=null)reviewResult.accept(true);else if(original==null)showMontageCatalog();else showMontageInventory();
   }catch(Exception e){notice("Formstück konnte nicht gespeichert werden.");}
  }));dialog.show();
 }
 int findMontageItem(org.json.JSONArray rows,org.json.JSONObject original) throws org.json.JSONException {
  String id=original.getString("id");
  for(int i=0;i<rows.length();i++){
   org.json.JSONObject row=rows.getJSONObject(i);
   if(id.equals(row.optString("id"))&&original.toString().equals(row.toString()))return i;
  }
  throw new org.json.JSONException("Formstück wurde geändert.");
 }
 String montageDescription(org.json.JSONObject row) throws org.json.JSONException {
  String dn=row.optString("dn"),material=row.optString("material");
  return MontageCatalog.Fitting.valueOf(row.getString("type")).label+" · DN "+(dn.isEmpty()?"unbekannt":dn)
      +" · "+(material.isEmpty()?"Werkstoff offen":material);
 }
 void showMontageInventory(){
  try{
   org.json.JSONArray rows=new org.json.JSONArray(getPreferences(0).getString("montageFittings","[]"));
   String[] labels=new String[rows.length()];
   for(int i=0;i<rows.length();i++){
    org.json.JSONObject row=rows.getJSONObject(i);
    labels[i]=montageDescription(row)+(row.has("station")?" · Station "+row.optString("station")+" m":" · ohne Station")
       +(row.optBoolean("installed")?" · Einbau bestätigt":" · Einbau offen");
   }
   android.app.AlertDialog.Builder dialog=new android.app.AlertDialog.Builder(this).setTitle("Projekt-Formstücke")
      .setNegativeButton("Zurück",(d,w)->showMontage());
   if(labels.length==0)dialog.setMessage("Keine Formstücke gespeichert.");
   else dialog.setItems(labels,(d,index)->{
    org.json.JSONObject row=rows.optJSONObject(index);
    showMontageFitting(MontageCatalog.Fitting.valueOf(row.optString("type")),row);
   });
   dialog.show();
  }catch(Exception e){notice("Formstücke konnten nicht gelesen werden.");}
 }
 String montageMaterialText(org.json.JSONArray rows) throws org.json.JSONException {
  java.util.SortedMap<String,Integer> quantities=new java.util.TreeMap<>();
  for(int i=0;i<rows.length();i++){
   org.json.JSONObject row=rows.getJSONObject(i);
   if(row.optBoolean("installed",false))quantities.merge(montageDescription(row),1,Integer::sum);
  }
  StringBuilder list=new StringBuilder();
  for(java.util.Map.Entry<String,Integer> entry:quantities.entrySet()){
   list.append(entry.getValue()).append(" Stk.  ").append(entry.getKey()).append('\n');
  }
  return list.toString().trim();
 }
 String projectMaterialText(SharedPreferences prefs)throws org.json.JSONException{
  String fittings=montageMaterialText(new org.json.JSONArray(prefs.getString("montageFittings","[]")));
  StringBuilder list=new StringBuilder();
  if(!fittings.isEmpty())list.append("Formstücke\n").append(fittings);
  org.json.JSONArray rows=new org.json.JSONArray(prefs.getString("extraUtilities","[]"));
  java.util.SortedMap<String,java.math.BigDecimal> lengths=new java.util.TreeMap<>();
  for(int i=0;i<rows.length();i++){
   org.json.JSONObject row=rows.getJSONObject(i);if(!row.optBoolean("installed",false))continue;
   double length=row.getDouble("installedLength");
   if(!Double.isFinite(length)||length<=0)throw new org.json.JSONException("Ungültige Einbaulänge");
   String spec=row.getString("type")+" · Ø "+row.getString("diameter")+" mm";
   if(!row.optString("dn").isEmpty())spec+=" · DN / Typ "+row.optString("dn");
   if(!row.optString("material").isEmpty())spec+=" · "+row.optString("material");
   if(row.has("sdr"))spec+=" · SDR "+row.optString("sdr");
   if(row.has("pn"))spec+=" · PN "+row.optString("pn");
   lengths.merge(spec,java.math.BigDecimal.valueOf(length),java.math.BigDecimal::add);
  }
  if(!lengths.isEmpty()){
   if(list.length()>0)list.append("\n\n");list.append("Leitungen");
   for(java.util.Map.Entry<String,java.math.BigDecimal> entry:lengths.entrySet()){
    list.append("\n").append(entry.getValue().stripTrailingZeros().toPlainString().replace('.',',')).append(" m  ").append(entry.getKey());
   }
  }
  return list.toString();
 }
 String pendingConnectionsProject;
 void exportConnectionsPdf(String projectId){
  android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
  intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);intent.setType("application/pdf");
  intent.putExtra(android.content.Intent.EXTRA_TITLE,"Montage_Verbindungsregister.pdf");
  pendingConnectionsProject=projectId;startActivityForResult(intent,430);
 }
 String pendingMaterialProject;
 void showMontageMaterials(){
  try{
   String list=projectMaterialText(getPreferences(0));
   android.app.AlertDialog.Builder dialog=new android.app.AlertDialog.Builder(this).setTitle("Materialliste · bestätigter Einbau")
       .setMessage(list.isEmpty()?"Noch kein Einbau bestätigt.":list)
       .setNegativeButton("Zurück",(d,w)->showMontage());
   if(!list.isEmpty())dialog.setPositiveButton("Als PDF speichern",(d,w)->{
    android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);intent.setType("application/pdf");
    intent.putExtra(android.content.Intent.EXTRA_TITLE,"Montage_Materialliste.pdf");
    pendingMaterialProject=activeProject;startActivityForResult(intent,429);
   });
   dialog.show();
  }catch(Exception e){notice("Materialliste konnte nicht gelesen werden.");}
 }
 void writeMaterialPdf(android.net.Uri uri,String projectId){
  if(projectId==null){notice("Materialliste erneut öffnen.");return;}
  android.graphics.pdf.PdfDocument pdf=new android.graphics.pdf.PdfDocument();
  try{
   SharedPreferences prefs=projectStorage(projectId);
   String list=projectMaterialText(prefs);
   if(list.isEmpty()){notice("Noch kein Einbau bestätigt.");return;}
   String body="Projekt: "+prefs.getString("project","Projekt")+"\n\nBestätigter Einbau\n\n"+list;
   dailyTextPdf(pdf,1,"Montage – Materialliste",body,null);
   try(java.io.OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
    if(out==null)throw new java.io.IOException();
    pdf.writeTo(out);
   }
   notice("Materialliste gespeichert.");
  }catch(Exception e){notice("Materialliste konnte nicht gespeichert werden.");}
  finally{pdf.close();}
 }
 void showMetricRoute(){
  RouteSketch route=new RouteSketch(this,routeData());
  if(!DxfExport.metricAvailable(route)){notice("Zuerst alle Trassenabschnitte bemaßen und gültige Richtungen zeichnen.");return;}
  DxfExport model=new DxfExport(new java.io.StringWriter(),route,true);
  final java.util.ArrayList<double[]> montagePoints=new java.util.ArrayList<>();
  try{
   org.json.JSONArray items=new org.json.JSONArray(getPreferences(0).getString("montageFittings","[]"));
   for(int i=0;i<items.length();i++){
    org.json.JSONObject row=items.optJSONObject(i);if(row==null)continue;
    double[] point=model.mountingPoint(row.optDouble("station",Double.NaN),row.optDouble("offset",0));
    if(point!=null)montagePoints.add(point);
   }
  }catch(Exception e){notice("Montage-Zuordnung konnte nicht vollständig gelesen werden.");}
  final org.json.JSONArray surfaces;
  org.json.JSONArray loadedSurfaces;try{loadedSurfaces=new org.json.JSONArray(getPreferences(0).getString("segments","[]"));}catch(Exception e){loadedSurfaces=new org.json.JSONArray();notice("Oberflächen konnten nicht gelesen werden.");}surfaces=loadedSurfaces;
  route.bindDimensionProject(activeProject);route.setStationLayers(new org.json.JSONArray(),surfaces);
  final org.json.JSONArray widthSections;
  org.json.JSONArray loadedWidths;try{loadedWidths=new org.json.JSONArray(getPreferences(0).getString("metricWidthSections","[]"));}catch(Exception e){notice("Breitenabschnitte konnten nicht gelesen werden.");return;}widthSections=loadedWidths;
  final double[] widths={Double.NaN,Double.NaN,Double.NaN};
  String[] widthKeys={"width","left","right"};
  for(int i=0;i<widthKeys.length;i++){try{double v=Double.parseDouble(getPreferences(0).getString(widthKeys[i],"").trim().replace(',','.'));if(Double.isFinite(v)&&v>=0&&(i!=0||v>0))widths[i]=v;}catch(Exception ignored){}}
  int crossings=0;
  for(int i=1;i<model.vertices.size();i++)for(int j=i+2;j<model.vertices.size();j++){
   double[] a1=model.vertices.get(i-1),a2=model.vertices.get(i),b1=model.vertices.get(j-1),b2=model.vertices.get(j);
   double rx=a2[0]-a1[0],ry=a2[1]-a1[1],sx=b2[0]-b1[0],sy=b2[1]-b1[1],den=rx*sy-ry*sx;
   if(Math.abs(den)<1e-10)continue;
   double qx=b1[0]-a1[0],qy=b1[1]-a1[1],t=(qx*sy-qy*sx)/den,u=(qx*ry-qy*rx)/den;
   if(t>1e-8&&t<1-1e-8&&u>1e-8&&u<1-1e-8)crossings++;
  }
  LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);
  TextView geometryStatus=new TextView(this);panel.addView(geometryStatus);
  TextView overlapStatus=new TextView(this);panel.addView(overlapStatus);
  final int axisCrossings=crossings;
  Runnable refreshGeometry=()->{
  int missingWidths=0;double totalLength=route.measuredLength();
  java.util.TreeSet<Double> widthCuts=new java.util.TreeSet<>();widthCuts.add(0.0);widthCuts.add(totalLength);
  for(int i=0;i<widthSections.length();i++){org.json.JSONObject o=widthSections.optJSONObject(i);if(o==null)continue;double from=o.optDouble("from"),to=o.optDouble("to");if(Double.isFinite(from)&&Double.isFinite(to)&&from>=0&&to<=totalLength&&to>from){widthCuts.add(from);widthCuts.add(to);}}
  java.util.ArrayList<Double> orderedCuts=new java.util.ArrayList<>(widthCuts);
  for(int i=1;i<orderedCuts.size();i++){double mid=(orderedCuts.get(i-1)+orderedCuts.get(i))/2;boolean defined=false;for(int j=0;j<widthSections.length();j++){org.json.JSONObject o=widthSections.optJSONObject(j);if(o!=null&&mid>=o.optDouble("from")&&mid<o.optDouble("to")&&o.optDouble("width")>0)defined=true;}if(!defined&&!Double.isFinite(widths[0]))missingWidths++;}
  // Conservative proximity check of non-neighboring trench strips.
  // This is a warning, not an exact polygon-union area computation.
  // Conservative overlap screening: maximum half-extent on each measured
  // centerline segment, including all station-specific trench and cutting widths.
  int possibleOverlaps=0;
  double[] segmentReach=new double[model.vertices.size()-1];
  double runningStation=0;
  for(int i=1;i<model.vertices.size();i++){
   double from=runningStation,to=from+route.vertices.get(i).metres;
   java.util.TreeSet<Double> cuts=new java.util.TreeSet<>();cuts.add(from);cuts.add(to);
   for(int j=0;j<widthSections.length();j++){org.json.JSONObject o=widthSections.optJSONObject(j);if(o==null)continue;double lo=o.optDouble("from",Double.NaN),hi=o.optDouble("to",Double.NaN);
    if(Double.isFinite(lo)&&lo>from&&lo<to)cuts.add(lo);
    if(Double.isFinite(hi)&&hi>from&&hi<to)cuts.add(hi);
   }
   java.util.ArrayList<Double> stations=new java.util.ArrayList<>(cuts);
   double maxReach=0;
   for(int k=1;k<stations.size();k++){
    double mid=(stations.get(k-1)+stations.get(k))/2,w=widths[0],left=widths[1],right=widths[2];
    for(int j=0;j<widthSections.length();j++){org.json.JSONObject o=widthSections.optJSONObject(j);if(o==null||mid<o.optDouble("from",Double.NaN)||mid>=o.optDouble("to",Double.NaN))continue;
     w=o.optDouble("width",Double.NaN);
     if(o.has("left"))left=o.optDouble("left",Double.NaN);
     if(o.has("right"))right=o.optDouble("right",Double.NaN);
    }
    if(!Double.isFinite(w)||w<=0){maxReach=Double.NaN;break;}
    if(!Double.isFinite(left)||left<0||!Double.isFinite(right)||right<0){maxReach=Double.NaN;break;}
    maxReach=Math.max(maxReach,w/2+Math.max(left,right));
   }
   segmentReach[i-1]=maxReach;runningStation=to;
  }
  int uncheckedPairs=0;
  for(int i=1;i<model.vertices.size();i++)for(int j=i+2;j<model.vertices.size();j++){
   double reachA=segmentReach[i-1],reachB=segmentReach[j-1];
   if(!Double.isFinite(reachA)||!Double.isFinite(reachB)){uncheckedPairs++;continue;}
   double[] a1=model.vertices.get(i-1),a2=model.vertices.get(i),b1=model.vertices.get(j-1),b2=model.vertices.get(j);
   double dx=a2[0]-a1[0],dy=a2[1]-a1[1],ex=b2[0]-b1[0],ey=b2[1]-b1[1];
   double den=dx*ey-dy*ex,dist=Double.POSITIVE_INFINITY;
   if(Math.abs(den)>1e-10){double qx=b1[0]-a1[0],qy=b1[1]-a1[1],t=(qx*ey-qy*ex)/den,u=(qx*dy-qy*dx)/den;if(t>=0&&t<=1&&u>=0&&u<=1)dist=0;}
   double[][] endpoints={a1,a2,b1,b2};
   for(int k=0;k<4;k++){
    double[] point=endpoints[k],start=k<2?b1:a1,end=k<2?b2:a2;
    double vx=end[0]-start[0],vy=end[1]-start[1],len2=vx*vx+vy*vy;
    if(len2<=0)continue;double t=Math.max(0,Math.min(1,((point[0]-start[0])*vx+(point[1]-start[1])*vy)/len2));
    dist=Math.min(dist,Math.hypot(point[0]-start[0]-t*vx,point[1]-start[1]-t*vy));
   }
   if(dist<=reachA+reachB+1e-8)possibleOverlaps++;
  }
   geometryStatus.setText(axisCrossings==0&&missingWidths==0?"Geometrie: keine Achsen-Selbstkreuzung erkannt; Flächenprüfung noch offen.":"Prüfung: "+axisCrossings+" Achsen-Selbstkreuzung(en), "+missingWidths+" Abschnitt(e) ohne Grabenbreite. Keine automatische Abrechnung.");
   overlapStatus.setText(possibleOverlaps==0&&uncheckedPairs==0?"Außenkontur: keine Nähe-Konflikte zwischen getrennten Abschnitten erkannt (keine Flächenfreigabe).":"Außenkontur: "+possibleOverlaps+" mögliche Überlappung(en); "+uncheckedPairs+" Paar(e) wegen fehlender Maße nicht geprüft. Flächen manuell prüfen.");
  };
  refreshGeometry.run();
  TextView overlapModeLabel=new TextView(this);overlapModeLabel.setText("Überlappungen – Kontrollberechnung");panel.addView(overlapModeLabel);
  Spinner overlapMode=new Spinner(this);
  String[] overlapModes={"Nur Kontrollwerte","Überlappung beibehalten (Brutto)","Überlappung abziehen (Netto)"};
  overlapMode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,overlapModes));
  panel.addView(overlapMode);
  TextView overlapLegend=new TextView(this);overlapLegend.setText("Orange: Überlappung bemaßter Abschnittsstreifen (ohne Eckergänzungen) · Grün: gewählte Eckergänzung (Kontrollmodell)");panel.addView(overlapLegend);
  final SharedPreferences overlapPrefs=projectStorage(activeProject);
  final RouteBranchStore branchStore=new RouteBranchStore(overlapPrefs);
  final Runnable[] branchChanged={()->{}};
  final double[] requestedBranchStation={Double.NaN};
  Button branchEditor=new Button(this);branchEditor.setText("Abzweigungen verwalten");panel.addView(branchEditor);
  branchEditor.setOnClickListener(v->{
   try{
    java.util.List<RouteBranch> branches=branchStore.load();
    String[] labels=new String[branches.size()+1];
    labels[0]="Abzweigung hinzufügen";
    for(int j=0;j<branches.size();j++)labels[j+1]=branches.get(j).name+" · "+branches.get(j).station+" m";
    android.content.DialogInterface.OnClickListener chooseBranch=(dialog,index)->{
     final RouteBranch existing=index>0?branches.get(index-1):null;
     android.widget.LinearLayout fields=new android.widget.LinearLayout(this);fields.setOrientation(android.widget.LinearLayout.VERTICAL);
     String[] hints={"Bezeichnung","Anschlussstation (m)","Abschnittslängen (m; mit ; trennen)","Richtungen (Grad; mit ; trennen; +X = 0°)","Grabenbreite (m)","Grabentiefe (m)","Oberfläche"};
     EditText[] inputs=new EditText[hints.length];
     for(int j=0;j<hints.length;j++){inputs[j]=new EditText(this);inputs[j].setSingleLine(true);inputs[j].setHint(hints[j]);fields.addView(inputs[j]);}
     if(existing==null&&Double.isFinite(requestedBranchStation[0]))inputs[1].setText(String.format(java.util.Locale.GERMANY,"%.3f",requestedBranchStation[0]));
     requestedBranchStation[0]=Double.NaN;
     if(existing!=null){
      try{
       org.json.JSONObject data=existing.toJson();
       StringBuilder ls=new StringBuilder(),ds=new StringBuilder();
       for(int j=0;j<data.getJSONArray("lengths").length();j++){if(j>0){ls.append("; ");ds.append("; ");}ls.append(data.getJSONArray("lengths").getDouble(j));ds.append(data.getJSONArray("directions").getDouble(j));}
       String[] values={existing.name,Double.toString(existing.station),ls.toString(),ds.toString(),Double.toString(existing.width),Double.toString(existing.depth),existing.surface};
       for(int j=0;j<values.length;j++)inputs[j].setText(values[j]);
      }catch(Exception ex){notice("Abzweigung konnte nicht geöffnet werden");return;}
     }
     android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(fields);
     android.app.AlertDialog.Builder builder=new android.app.AlertDialog.Builder(this).setTitle(existing==null?"Abzweigung hinzufügen":"Abzweigung bearbeiten").setView(scroll).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null);
     if(existing!=null)builder.setNeutralButton("Löschen",(d,w)->new android.app.AlertDialog.Builder(this).setMessage("Abzweigung wirklich löschen?").setNegativeButton("Abbrechen",null).setPositiveButton("Löschen",(dd,ww)->{
      try{java.util.List<RouteBranch> updated=branchStore.load();updated.removeIf(item->item.id.equals(existing.id));branchStore.save(updated);branchChanged[0].run();for(int k=0;k<panel.getChildCount();k++)panel.getChildAt(k).invalidate();notice("Abzweigung gelöscht");}catch(Exception ex){notice("Speichern fehlgeschlagen: "+ex.getMessage());}
     }).show());
     android.app.AlertDialog entry=builder.create();
     entry.setOnShowListener(shownDialog->entry.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{
      try{
       double[] n=new double[5];for(int j:new int[]{0,3,4})n[j]=Double.parseDouble(inputs[j+1].getText().toString().trim().replace(',','.'));
       String[] l=inputs[2].getText().toString().split(";",-1),d=inputs[3].getText().toString().split(";",-1);
       if(l.length!=d.length)throw new IllegalArgumentException("Für jeden Abschnitt eine Richtung eingeben");
       double[] lengths=new double[l.length],directions=new double[d.length];
       for(int j=0;j<l.length;j++){lengths[j]=Double.parseDouble(l[j].trim().replace(',','.'));directions[j]=Double.parseDouble(d[j].trim().replace(',','.'));}
       if(n[0]>route.measuredLength())throw new IllegalArgumentException("Station außerhalb der Haupttrasse");
       RouteBranch branch=new RouteBranch(existing==null?java.util.UUID.randomUUID().toString():existing.id,inputs[0].getText().toString(),n[0],n[3],n[4],inputs[6].getText().toString(),lengths,directions);
       java.util.List<RouteBranch> updated=branchStore.load();if(existing==null)updated.add(branch);else{int found=-1;for(int j=0;j<updated.size();j++)if(updated.get(j).id.equals(existing.id)){found=j;break;}if(found<0)throw new IllegalStateException("Abzweigung nicht gefunden");updated.set(found,branch);}branchStore.save(updated);branchChanged[0].run();entry.dismiss();notice("Abzweigung gespeichert");for(int k=0;k<panel.getChildCount();k++)panel.getChildAt(k).invalidate();
      }catch(Exception ex){notice("Eingaben prüfen: "+ex.getMessage());}
     }));entry.show();
    };
    if(Double.isFinite(requestedBranchStation[0]))chooseBranch.onClick(null,0);
    else new android.app.AlertDialog.Builder(this).setTitle("Trassenverzweigungen").setItems(labels,chooseBranch).setNegativeButton("Schließen",null).show();
   }catch(Exception ex){notice("Abzweigungen konnten nicht geladen werden: "+ex.getMessage());}
  });
  int storedOverlapMode=0;
  try{storedOverlapMode=overlapPrefs.getInt("metricOverlapMode",0);}catch(ClassCastException ignored){}
  final int[] savedOverlapMode={Math.max(0,Math.min(2,storedOverlapMode))};
  overlapMode.setSelection(savedOverlapMode[0]);
  final Spinner cornerMode=new Spinner(this);
  final String[] cornerLabels={"Ohne Eckergänzung","Abgeschrägte Außenecken","Gehrung (mit Begrenzung)"};
  cornerMode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,cornerLabels));
  final int[] savedCornerMode={Math.max(0,Math.min(2,overlapPrefs.getInt("metricCornerMode",overlapPrefs.getBoolean("metricIncludeCorners",false)?1:0)))};
  cornerMode.setSelection(savedCornerMode[0]);panel.addView(cornerMode);
  final EditText correction=new EditText(this);correction.setSingleLine(true);correction.setHint("Flächenkorrektur (+/− m²)");correction.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
  final EditText correctionReason=new EditText(this);correctionReason.setHint("Begründung der Korrektur");correctionReason.setSingleLine(true);
  correction.setText(overlapPrefs.getString("metricAreaCorrection","0"));
  correctionReason.setText(overlapPrefs.getString("metricAreaCorrectionReason",""));
  panel.addView(correction);panel.addView(correctionReason);
  Button saveCorrection=new Button(this);saveCorrection.setText("Flächenkorrektur speichern");panel.addView(saveCorrection);
  TextView nominalArea=new TextView(this);panel.addView(nominalArea);
  final EditText mainExcavationDepth=new EditText(this);
  mainExcavationDepth.setHint("Hauptgraben – einheitliche Grabentiefe (m)");
  mainExcavationDepth.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
  mainExcavationDepth.setText(overlapPrefs.getString("metricMainExcavationDepth",""));
  panel.addView(mainExcavationDepth);
  final EditText depthSectionsInput=new EditText(this);
  depthSectionsInput.setHint("Grabentiefe abschnittsweise: von;bis;Tiefe (je Zeile)");
  depthSectionsInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
  depthSectionsInput.setText(overlapPrefs.getString("metricDepthSections",""));
  panel.addView(depthSectionsInput);
  final EditText elevationInput=new EditText(this);
  elevationInput.setHint("GOK-Höhenpunkte: Station;Höhe (m), je Zeile");
  elevationInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
  elevationInput.setText(overlapPrefs.getString("metricElevationPoints",""));
  panel.addView(elevationInput);
  final TextView elevationStatus=new TextView(this);panel.addView(elevationStatus);
  final ElevationProfile[] surveyedProfile={null};

  final EditText pipeProfileInput=new EditText(this);
  pipeProfileInput.setHint("Rohrprofil: Außendurchmesser;Wandstärke;Bettung (m)");
  pipeProfileInput.setText(overlapPrefs.getString("metricPipeProfile",""));
  panel.addView(pipeProfileInput);
  final PipeLayoutStore multiPipeStore=new PipeLayoutStore(overlapPrefs);
  final Button multiPipeButton=new Button(this);
  multiPipeButton.setText("Mehrfachbelegung bearbeiten");
  panel.addView(multiPipeButton);
  final Button crossSectionButton=new Button(this);
  crossSectionButton.setText("Rohrquerschnitt öffnen");panel.addView(crossSectionButton);
  crossSectionButton.setOnClickListener(v->{
   final EditText dimensions=new EditText(this);
   dimensions.setHint("Grabenbreite;Grabentiefe (m)");
   String savedCrossDimensions=overlapPrefs.getString("metricCrossSectionDimensions","");
   try{
    double projectWidth=number(width),projectDepth=number(depth);
    if(Double.isFinite(projectWidth)&&projectWidth>0&&Double.isFinite(projectDepth)&&projectDepth>0)
     savedCrossDimensions=projectWidth+";"+projectDepth;
   }catch(Exception ignored){}
   dimensions.setText(savedCrossDimensions);
   new android.app.AlertDialog.Builder(this).setTitle("Querschnitt – Bezugsmaße").setView(dimensions).setNegativeButton("Abbrechen",null).setPositiveButton("Öffnen",(d,w)->{
    try{
     String[] parts=dimensions.getText().toString().split(";",-1);
     if(parts.length!=2)throw new IllegalArgumentException("Breite und Tiefe erforderlich");
     double bw=Double.parseDouble(parts[0].trim().replace(',','.')),bd=Double.parseDouble(parts[1].trim().replace(',','.'));
     final java.util.List<PipeLayout.Pipe> pipes=multiPipeStore.load();
     final PipeLayoutView cross=new PipeLayoutView(this);
     cross.setLayoutData(pipes,bw,bd);
     android.widget.LinearLayout content=new android.widget.LinearLayout(this);content.setOrientation(1);
     content.addView(cross,new android.widget.LinearLayout.LayoutParams(-1,dp(300)));
     final EditText limits=new EditText(this);
     limits.setHint("Mindestabstand;Mindestüberdeckung (m)");
     final double[] requirements={Double.parseDouble(overlapPrefs.getString("metricPipeMinGap","0")),Double.parseDouble(overlapPrefs.getString("metricPipeMinCover","0"))};
     PipeLayout.warnings(pipes,bw,bd,requirements[0],requirements[1]);
     limits.setText(requirements[0]+";"+requirements[1]);content.addView(limits);
     final Button applyLimits=new Button(this);applyLimits.setText("Grenzwerte übernehmen");content.addView(applyLimits);
     final TextView warnings=new TextView(this);content.addView(warnings);
     Runnable refresh=()->{cross.setLayoutData(pipes,bw,bd);cross.setRequirements(requirements[0],requirements[1]);warnings.setText(android.text.TextUtils.join("\n",PipeLayout.dimensions(pipes,bw,bd))+"\n"+android.text.TextUtils.join("\n",PipeLayout.warnings(pipes,bw,bd,requirements[0],requirements[1])));};
     final Button optimizePipes=new Button(this);optimizePipes.setText("Rohranordnung optimieren");content.addView(optimizePipes,content.indexOfChild(warnings));
     optimizePipes.setOnClickListener(button->{
      try{
       if(pipes.isEmpty()){warnings.setText("Keine Leitungen vorhanden.");return;}
       final java.util.List<PipeLayout.Pipe> rowProposal=PipeLayout.proposeRow(pipes,bw,bd,requirements[0],requirements[1]);
       final boolean layered=rowProposal.isEmpty();
       final java.util.List<PipeLayout.Pipe> proposal=layered?PipeLayout.proposeLayers(pipes,bw,bd,requirements[0],requirements[1]):rowProposal;
       if(proposal.isEmpty()){new android.app.AlertDialog.Builder(this).setMessage("Keine passende Anordnung gefunden. Grabenmaße und Grenzwerte prüfen.").setPositiveButton("OK",null).show();return;}
       PipeLayoutView preview=new PipeLayoutView(this);
       preview.setLayoutData(proposal,bw,bd);preview.setRequirements(requirements[0],requirements[1]);
       android.widget.LinearLayout previewContent=new android.widget.LinearLayout(this);previewContent.setOrientation(1);
       previewContent.addView(preview,new android.widget.LinearLayout.LayoutParams(-1,dp(240)));
       TextView previewInfo=new TextView(this);previewInfo.setText((layered?"Vorschlag: mehrlagig. Höhenlage kann sich ändern; fachlich prüfen.\n":"Vorschlag: Bettung unverändert.\n")+android.text.TextUtils.join("\n",PipeLayout.dimensions(proposal,bw,bd)));
       android.widget.ScrollView previewScroll=new android.widget.ScrollView(this);previewScroll.addView(previewInfo);previewContent.addView(previewScroll,new android.widget.LinearLayout.LayoutParams(-1,dp(140)));
       new android.app.AlertDialog.Builder(this).setTitle("Rohranordnung – Vorschau").setView(previewContent).setNegativeButton("Abbrechen",null).setPositiveButton("Übernehmen",(dialog,which)->{
        try{multiPipeStore.save(proposal);pipes.clear();pipes.addAll(proposal);refresh.run();}
        catch(Exception ex){warnings.setText("Speichern fehlgeschlagen: "+ex.getMessage());}
       }).show();
      }catch(Exception ex){warnings.setText("Optimierung: "+ex.getMessage());}
     });
     applyLimits.setOnClickListener(button->{
      try{
       String[] values=limits.getText().toString().split(";",-1);
       if(values.length!=2)throw new IllegalArgumentException("Zwei Werte erforderlich");
       double gap=Double.parseDouble(values[0].trim().replace(',','.')),cover=Double.parseDouble(values[1].trim().replace(',','.'));
       PipeLayout.warnings(pipes,bw,bd,gap,cover);
       if(!overlapPrefs.edit().putString("metricPipeMinGap",String.valueOf(gap)).putString("metricPipeMinCover",String.valueOf(cover)).commit())throw new IllegalStateException("Grenzwerte konnten nicht gespeichert werden");
       requirements[0]=gap;requirements[1]=cover;limits.setError(null);refresh.run();
      }catch(Exception ex){limits.setError(ex.getMessage());}
     });
     if(!overlapPrefs.edit().putString("metricCrossSectionDimensions",bw+";"+bd).commit())throw new IllegalStateException("Bezugsmaße konnten nicht gespeichert werden");
     final Button saveSectionProfile=new Button(this);saveSectionProfile.setText("Als Abschnittsprofil speichern");content.addView(saveSectionProfile);
     saveSectionProfile.setOnClickListener(button->{
      LinearLayout interval=new LinearLayout(this);interval.setOrientation(1);
      EditText from=new EditText(this),to=new EditText(this);
      from.setHint("Station von (m)");to.setHint("Station bis (m)");from.setInputType(8194);to.setInputType(8194);interval.addView(from);interval.addView(to);
      android.app.AlertDialog sectionDialog=new android.app.AlertDialog.Builder(this).setTitle("Regelprofil – Stationierung").setView(interval).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();
      sectionDialog.setOnShowListener(dialog->sectionDialog.getButton(-1).setOnClickListener(save->{
       try{multiPipeStore.saveSection(number(from),number(to),bw,bd,pipes);refreshMainQuantities();sectionDialog.dismiss();android.widget.Toast.makeText(this,"Abschnittsprofil gespeichert",android.widget.Toast.LENGTH_SHORT).show();}
       catch(Exception ex){to.setError(ex.getMessage());}
      }));sectionDialog.show();
     });
     final Button syncTrench=new Button(this);syncTrench.setAllCaps(false);syncTrench.setText("Grabenmaße in Tiefbau übernehmen");content.addView(syncTrench,1);
     syncTrench.setOnClickListener(button->new android.app.AlertDialog.Builder(this).setTitle("Grabenmaße übernehmen?").setMessage("Breite "+bw+" m und Tiefe "+bd+" m werden als allgemeine Grabenmaße des Projekts übernommen. Abschnittsbezogene Maße bleiben unverändert.").setNegativeButton("Abbrechen",null).setPositiveButton("Übernehmen",(dialog,which)->{
      if(!overlapPrefs.edit().putString("width",String.valueOf(bw)).putString("depth",String.valueOf(bd)).commit()){warnings.setText("Grabenmaße konnten nicht gespeichert werden.");return;}
      width.setText(String.valueOf(bw));depth.setText(String.valueOf(bd));
      if(profile!=null)profile.invalidate();if(sketch!=null)sketch.invalidate();
      android.widget.Toast.makeText(this,"Grabenmaße übernommen",android.widget.Toast.LENGTH_SHORT).show();
     }).show());
     refresh.run();
     final android.widget.PopupWindow[] activePipePanel={null};
     final PipeLayoutView.Move[] draftMove={null};
     cross.setMove((index,offset,bedding)->{
      try{
       if(activePipePanel[0]!=null&&activePipePanel[0].isShowing()){if(draftMove[0]!=null)draftMove[0].move(index,offset,bedding);return;}
       PipeLayout.Pipe pipe=pipes.get(index);
       java.util.List<PipeLayout.Pipe> next=new java.util.ArrayList<>(pipes);
       next.set(index,new PipeLayout.Pipe(pipe.name,offset,pipe.diameter,pipe.wall,bedding,pipe.minGap,pipe.minCover,pipe.fixed));
       multiPipeStore.save(next);pipes.clear();pipes.addAll(next);refresh.run();
      }catch(Exception ex){refresh.run();warnings.setText("Speichern fehlgeschlagen: "+ex.getMessage());}
     });
     cross.setSelection(index->{
      if(activePipePanel[0]!=null&&activePipePanel[0].isShowing())return;
      PipeLayout.Pipe pipe=pipes.get(index);
      new android.app.AlertDialog.Builder(this).setTitle(pipe.name).setItems(new String[]{"Bearbeiten","Duplizieren","Löschen",pipe.fixed?"Position freigeben":"Position fixieren"},(menu,action)->{
       if(action==3){
        try{
         java.util.List<PipeLayout.Pipe> next=new java.util.ArrayList<>(pipes);
         next.set(index,new PipeLayout.Pipe(pipe.name,pipe.offset,pipe.diameter,pipe.wall,pipe.bedding,pipe.minGap,pipe.minCover,!pipe.fixed));
         multiPipeStore.save(next);pipes.clear();pipes.addAll(next);refresh.run();
        }catch(Exception ex){warnings.setText(ex.getMessage());}
        return;
       }
       if(action==0){
        final android.widget.LinearLayout fields=new android.widget.LinearLayout(this);fields.setOrientation(1);
        final EditText offset=new EditText(this), bedding=new EditText(this);
        offset.setHint("Achslage zur Grabenmitte (m)");offset.setText(String.valueOf(pipe.offset));
        bedding.setHint("Rohrunterkante über Grabensohle (m)");bedding.setText(String.valueOf(pipe.bedding));
        offset.setEnabled(!pipe.fixed);bedding.setEnabled(!pipe.fixed);fields.addView(offset);fields.addView(bedding);
        final EditText diameterInput=new EditText(this),wallInput=new EditText(this);
        diameterInput.setHint("Außendurchmesser (m)");diameterInput.setText(String.valueOf(pipe.diameter));
        wallInput.setHint("Wandstärke (m)");wallInput.setText(String.valueOf(pipe.wall));
        fields.addView(diameterInput);fields.addView(wallInput);
        utilitySlider(fields,diameterInput,"Außendurchmesser (m)",0.001,Math.max(pipe.diameter,Math.max(bw,bd)),0.001,true);
        utilitySlider(fields,wallInput,"Wandstärke (m)",0,Math.max(pipe.wall,pipe.diameter/2),0.001,true);
        final EditText gapInput=new EditText(this),coverInput=new EditText(this);
        gapInput.setHint("Rohr-Mindestabstand (m)");gapInput.setText(String.valueOf(pipe.minGap));
        coverInput.setHint("Rohr-Mindestüberdeckung (m)");coverInput.setText(String.valueOf(pipe.minCover));
        fields.addView(gapInput);fields.addView(coverInput);
        utilitySlider(fields,offset,"Achslage (m)",-bw/2,bw/2,0.01,!pipe.fixed);
        utilitySlider(fields,bedding,"Rohrunterkante (m)",0,bd,0.01,!pipe.fixed);
        utilitySlider(fields,gapInput,"Mindestabstand (m)",0,bw,0.01,true);
        utilitySlider(fields,coverInput,"Mindestüberdeckung (m)",0,bd,0.01,true);
        final PipeLayoutView livePreview=new PipeLayoutView(this);
        fields.addView(livePreview,0,new LinearLayout.LayoutParams(-1,dp(200)));
        final TextView liveWarnings=new TextView(this);fields.addView(liveWarnings,1);
        Runnable previewUpdate=()->{try{
         double o=number(offset),b=number(bedding),g=number(gapInput),c=number(coverInput);
         if(!Double.isFinite(o+b+g+c)||b<0||g<0||c<0)throw new IllegalArgumentException("Ungültige Maße");
         java.util.List<PipeLayout.Pipe> draft=new java.util.ArrayList<>(pipes);
         draft.set(index,new PipeLayout.Pipe(pipe.name,o,number(diameterInput),number(wallInput),b,g,c,pipe.fixed));
         cross.setLayoutData(draft,bw,bd);cross.setSelectedPipe(index);livePreview.setLayoutData(draft,bw,bd);livePreview.setSelectedPipe(index);livePreview.setRequirements(requirements[0],requirements[1]);
         String issues=android.text.TextUtils.join("\n",PipeLayout.warnings(draft,bw,bd,requirements[0],requirements[1]));liveWarnings.setText(issues.isEmpty()?"Keine Konflikte":issues);warnings.setText(android.text.TextUtils.join("\n",PipeLayout.dimensions(draft,bw,bd))+"\n"+issues);
        }catch(Exception ex){liveWarnings.setText("Maße prüfen");}};
        for(EditText f:new EditText[]{offset,bedding,gapInput,coverInput,diameterInput,wallInput})f.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){previewUpdate.run();}public void afterTextChanged(android.text.Editable e){}});
        previewUpdate.run();
        ScrollView editScroll=new ScrollView(this);editScroll.addView(fields);
        final android.widget.LinearLayout sheet=new android.widget.LinearLayout(this);sheet.setOrientation(1);sheet.setBackgroundColor(0xff202827);
        TextView dragHandle=new TextView(this);dragHandle.setText("☰  Leitung bearbeiten");dragHandle.setTextColor(Color.WHITE);dragHandle.setPadding(dp(12),dp(8),dp(12),dp(8));dragHandle.setContentDescription("Einstellungsfenster verschieben");android.widget.LinearLayout titleBar=new android.widget.LinearLayout(this);sheet.addView(titleBar);titleBar.addView(dragHandle,new android.widget.LinearLayout.LayoutParams(0,-2,1));Button collapse=new Button(this);collapse.setText("−");titleBar.addView(collapse,new android.widget.LinearLayout.LayoutParams(dp(48),dp(44)));
        sheet.addView(editScroll,new android.widget.LinearLayout.LayoutParams(-1,0,1));
        android.widget.LinearLayout actions=new android.widget.LinearLayout(this);sheet.addView(actions);
        Button cancel=new Button(this),apply=new Button(this);cancel.setText("Abbrechen");apply.setText("Speichern");actions.addView(cancel,new android.widget.LinearLayout.LayoutParams(0,-2,1));actions.addView(apply,new android.widget.LinearLayout.LayoutParams(0,-2,1));
        final android.widget.PopupWindow edit=new android.widget.PopupWindow(sheet,Math.min(dp(420),getResources().getDisplayMetrics().widthPixels),Math.min(dp(360),getResources().getDisplayMetrics().heightPixels/2),true);
        edit.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xff202827));edit.setOutsideTouchable(false);if(android.os.Build.VERSION.SDK_INT>=29)edit.setTouchModal(false);activePipePanel[0]=edit;edit.setOnDismissListener(()->{activePipePanel[0]=null;draftMove[0]=null;cross.setSelectedPipe(-1);refresh.run();});
        draftMove[0]=(movedIndex,o,b)->{if(movedIndex!=index||pipe.fixed){previewUpdate.run();return;}offset.setText(String.valueOf(o));bedding.setText(String.valueOf(b));previewUpdate.run();};edit.setInputMethodMode(android.widget.PopupWindow.INPUT_METHOD_NEEDED);
        cancel.setOnClickListener(button->edit.dismiss());
        apply.setOnClickListener(button->{
         try{
          double o=Double.parseDouble(offset.getText().toString().trim().replace(',','.'));
          double b=Double.parseDouble(bedding.getText().toString().trim().replace(',','.'));
          java.util.List<PipeLayout.Pipe> next=new java.util.ArrayList<>(pipes);
          next.set(index,new PipeLayout.Pipe(pipe.name,o,number(diameterInput),number(wallInput),b,Double.parseDouble(gapInput.getText().toString().trim().replace(',','.')),Double.parseDouble(coverInput.getText().toString().trim().replace(',','.')),pipe.fixed));
          multiPipeStore.save(next);pipes.clear();pipes.addAll(next);refresh.run();edit.dismiss();
         }catch(Exception ex){bedding.setError("Ungültige Lage oder Speicherfehler: "+ex.getMessage());}
        });android.graphics.Rect visible=new android.graphics.Rect();cross.getWindowVisibleDisplayFrame(visible);
        final int[] panelPosition={Math.max(0,Math.min(Math.max(0,visible.width()-edit.getWidth()),overlapPrefs.getInt("pipePanelX",visible.width()-edit.getWidth()))),Math.max(0,Math.min(Math.max(0,visible.height()-edit.getHeight()),overlapPrefs.getInt("pipePanelY",visible.height()-edit.getHeight())))};
        edit.showAtLocation(cross,Gravity.TOP|Gravity.LEFT,panelPosition[0],panelPosition[1]);
        final int expandedHeight=edit.getHeight();
        collapse.setContentDescription("Einstellungen einklappen");
        collapse.setOnClickListener(button->{
         boolean expanded=editScroll.getVisibility()==View.VISIBLE;
         editScroll.setVisibility(expanded?View.GONE:View.VISIBLE);actions.setVisibility(expanded?View.GONE:View.VISIBLE);
         collapse.setText(expanded?"+":"−");collapse.setContentDescription(expanded?"Einstellungen ausklappen":"Einstellungen einklappen");
         int height=expanded?dp(48):expandedHeight;
         android.graphics.Rect frame=new android.graphics.Rect();cross.getWindowVisibleDisplayFrame(frame);
         panelPosition[1]=Math.max(0,Math.min(panelPosition[1],frame.height()-height));
         edit.update(panelPosition[0],panelPosition[1],-1,height);
        });
        dragHandle.setOnTouchListener(new View.OnTouchListener(){
         float downX,downY;int baseX,baseY;
         public boolean onTouch(View view,android.view.MotionEvent event){
          switch(event.getActionMasked()){
           case android.view.MotionEvent.ACTION_DOWN:
            downX=event.getRawX();downY=event.getRawY();baseX=panelPosition[0];baseY=panelPosition[1];return true;
           case android.view.MotionEvent.ACTION_MOVE:
            android.graphics.Rect bounds=new android.graphics.Rect();cross.getWindowVisibleDisplayFrame(bounds);
            int x=Math.max(0,Math.min(Math.max(0,bounds.width()-edit.getWidth()),baseX+Math.round(event.getRawX()-downX)));
            int y=Math.max(0,Math.min(Math.max(0,bounds.height()-edit.getHeight()),baseY+Math.round(event.getRawY()-downY)));
            panelPosition[0]=x;panelPosition[1]=y;edit.update(x,y,-1,-1);return true;
           case android.view.MotionEvent.ACTION_UP:overlapPrefs.edit().putInt("pipePanelX",panelPosition[0]).putInt("pipePanelY",panelPosition[1]).apply();return true;
           case android.view.MotionEvent.ACTION_CANCEL:return true;
           default:return true;
          }
         }
        });return;
       }
       Runnable change=()->{
        java.util.List<PipeLayout.Pipe> next=new java.util.ArrayList<>(pipes);
        if(action==1)next.add(new PipeLayout.Pipe(pipe.name+" (Kopie)",pipe.offset,pipe.diameter,pipe.wall,pipe.bedding,pipe.minGap,pipe.minCover));else next.remove(index);
        try{multiPipeStore.save(next);pipes.clear();pipes.addAll(next);refresh.run();multiPipeButton.setText("Mehrfachbelegung ("+pipes.size()+") bearbeiten");}
        catch(Exception ex){warnings.setText(ex.getMessage());}
       };
       if(action==2)new android.app.AlertDialog.Builder(this).setMessage("Leitung löschen?").setNegativeButton("Abbrechen",null).setPositiveButton("Löschen",(x,y)->change.run()).show();else change.run();
      }).show();
     });
     new android.app.AlertDialog.Builder(this).setTitle("Rohrquerschnitt").setView(content).setPositiveButton("Schließen",null).show();
    }catch(Exception ex){new android.app.AlertDialog.Builder(this).setMessage("Querschnitt: "+ex.getMessage()).setPositiveButton("OK",null).show();}
   }).show();
  });

  multiPipeButton.setOnClickListener(v->{
   try{
    final java.util.List<PipeLayout.Pipe> pipes=multiPipeStore.load();
    final EditText editor=new EditText(this);
    editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    editor.setHint("Name;Versatz;Außendurchmesser;Wandstärke;Bettung (m)");
    StringBuilder rows=new StringBuilder();
    for(PipeLayout.Pipe pipe:pipes)rows.append(pipe.name).append(';').append(pipe.offset).append(';').append(pipe.diameter).append(';').append(pipe.wall).append(';').append(pipe.bedding).append('\n');
    editor.setText(rows.toString());
    android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Mehrfachbelegung – Maße in m").setMessage("Eine Leitung je Zeile. Versatz negativ links, positiv rechts.").setView(editor).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();
    dialog.setOnShowListener(ignored->dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(save->{
     try{
      java.util.List<PipeLayout.Pipe> updated=new java.util.ArrayList<>();
      for(String line:editor.getText().toString().split("\\n")){
       if(line.trim().isEmpty())continue;
       String[] fields=line.split(";",-1);
       if(fields.length!=5)throw new IllegalArgumentException("Fünf Felder je Leitung erforderlich");
       double[] d=new double[4];
       for(int k=0;k<4;k++)d[k]=Double.parseDouble(fields[k+1].trim().replace(',','.'));
       updated.add(new PipeLayout.Pipe(fields[0].trim(),d[0],d[1],d[2],d[3]));
      }
      multiPipeStore.save(updated);multiPipeButton.setText("Mehrfachbelegung ("+updated.size()+") bearbeiten");dialog.dismiss();
     }catch(Exception ex){editor.setError(ex.getMessage());}
    }));
    dialog.show();
   }catch(Exception ex){new android.app.AlertDialog.Builder(this).setMessage("Rohrprofil konnte nicht geladen werden: "+ex.getMessage()).setPositiveButton("OK",null).show();}
  });

  final EditText minimumCoverInput=new EditText(this);
  minimumCoverInput.setHint("Mindestüberdeckung (m), optional");
  minimumCoverInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
  minimumCoverInput.setText(overlapPrefs.getString("metricMinimumCover",""));
  panel.addView(minimumCoverInput);

  final TextView pipeProfileStatus=new TextView(this);panel.addView(pipeProfileStatus);
  final double[][] pipeDimensions={null};
  final java.util.List<double[]> profileData=new java.util.ArrayList<>();
  final android.view.View longitudinalProfile=new android.view.View(this){
   @Override protected void onDraw(android.graphics.Canvas c){
    super.onDraw(c);
    android.graphics.Paint p=new android.graphics.Paint(3);
    p.setColor(0xff777777);p.setTextSize(dp(12));
    if(profileData.isEmpty()){c.drawText("Längsschnitt: Tiefenangaben fehlen oder sind ungültig",dp(8),dp(24),p);return;}
    double total=0,maxDepth=0;
    for(double[] v:profileData){total+=v[0];maxDepth=Math.max(maxDepth,v[1]);}
    if(total<=0||maxDepth<=0)return;
    float left=dp(12),top=dp(32),width=getWidth()-dp(24),height=getHeight()-dp(76);
    if(width<=0||height<=0)return;
    if(surveyedProfile[0]!=null){
     ElevationProfile ep=surveyedProfile[0];
     java.util.TreeSet<Double> stations=new java.util.TreeSet<>();
     double cursor=0;stations.add(0.0);
     for(double[] v:profileData){cursor+=v[0];stations.add(cursor);}
     for(double[] point:ep.stations())if(point[0]<=total)stations.add(point[0]);
     double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
     for(double st:stations){double z=ep.elevation(st);if(Double.isFinite(z)){low=Math.min(low,z-maxDepth);high=Math.max(high,z);}}
     if(Double.isFinite(low)&&high>low){
      double range=high-low;double st0=0;
      for(double[] v:profileData){
       double st1=st0+v[0];
       java.util.TreeSet<Double> cuts=new java.util.TreeSet<>();cuts.add(st0);cuts.add(st1);
       for(double st:stations)if(st>st0&&st<st1)cuts.add(st);
       Double prev=null;
       for(double st:cuts){
        if(prev!=null){
         double za=ep.elevation(prev),zb=ep.elevation(st);
         if(Double.isFinite(za)&&Double.isFinite(zb)){
          float x1=left+(float)(prev/total*width),x2=left+(float)(st/total*width);
          float y1=top+(float)((high-za)/range*height),y2=top+(float)((high-zb)/range*height),h=(float)(v[1]/range*height);
          android.graphics.Path shape=new android.graphics.Path();shape.moveTo(x1,y1);shape.lineTo(x2,y2);shape.lineTo(x2,y2+h);shape.lineTo(x1,y1+h);shape.close();
          p.setColor(0xffb79b70);c.drawPath(shape,p);
          p.setColor(0xff68945d);p.setStrokeWidth(dp(2));c.drawLine(x1,y1,x2,y2,p);
          p.setColor(0xff555555);c.drawLine(x1,y1+h,x2,y2+h,p);
          if(pipeDimensions[0]!=null){
           double[] dims=pipeDimensions[0];float bottom=h-(float)(dims[2]/range*height),upper=bottom-(float)(dims[0]/range*height);
           p.setColor(0xff397db5);c.drawLine(x1,y1+bottom,x2,y2+bottom,p);c.drawLine(x1,y1+upper,x2,y2+upper,p);
          }

         }
        }
        prev=st;
       }
       st0=st1;
      }
      p.setColor(0xff777777);c.drawText("Längsschnitt – vermessene GOK / Grabensohle",left,dp(16),p);
      c.drawText("0,00 m",left,getHeight()-dp(12),p);
      String end=String.format(java.util.Locale.GERMANY,"%.2f m",total);c.drawText(end,left+width-p.measureText(end),getHeight()-dp(12),p);
      return;
     }
    }

    p.setColor(0xff68945d);p.setStrokeWidth(dp(2));c.drawLine(left,top,left+width,top,p);
    double station=0;
    for(double[] v:profileData){
     float x1=left+(float)(station/total*width),x2=left+(float)((station+v[0])/total*width),y=top+(float)(v[1]/maxDepth*height);
     p.setColor(0xffb79b70);c.drawRect(x1,top,x2,y,p);
     p.setColor(0xff555555);c.drawLine(x1,y,x2,y,p);c.drawLine(x1,top,x1,y,p);
     if(x2-x1>dp(48))c.drawText(String.format(java.util.Locale.GERMANY,"%.2f m",v[1]),x1+dp(3),y-dp(4),p);
     station+=v[0];
    }
    p.setColor(0xff777777);c.drawText("0,00 m",left,getHeight()-dp(12),p);
    String end=String.format(java.util.Locale.GERMANY,"%.2f m",total);c.drawText(end,left+width-p.measureText(end),getHeight()-dp(12),p);
    c.drawText("Längsschnitt – relative Tiefe, gemeinsame GOK",left,dp(16),p);
   }
  };
  panel.addView(longitudinalProfile,new LinearLayout.LayoutParams(-1,dp(210)));


  Runnable refreshNominalArea=()->{
   java.util.ArrayList<double[]> mainVolumeStrips=new java.util.ArrayList<>();
   java.util.List<double[]> depthSections=new java.util.ArrayList<>();
   boolean depthSectionsValid=true;
   try{
    for(String line:depthSectionsInput.getText().toString().split("\\n")){
     if(line.trim().isEmpty())continue;
     String[] parts=line.trim().split(";",-1);
     if(parts.length!=3)throw new IllegalArgumentException();
     double[] v=new double[3];
     for(int i=0;i<3;i++){v[i]=Double.parseDouble(parts[i].trim().replace(',','.'));if(!Double.isFinite(v[i]))throw new IllegalArgumentException();}
     if(v[0]<0||v[1]<=v[0]||v[1]>route.measuredLength()+1e-8||v[2]<=0)throw new IllegalArgumentException();
     for(double[] old:depthSections)if(v[0]<old[1]&&v[1]>old[0])throw new IllegalArgumentException();
     depthSections.add(v);
    }
   }catch(Exception ex){depthSectionsValid=false;depthSections.clear();}

   double total=route.measuredLength();
   java.util.ArrayList<double[]> strips=new java.util.ArrayList<>();
   double station=0;
   for(int k=1;k<model.vertices.size();k++){
    double[] a=model.vertices.get(k-1),b=model.vertices.get(k);
    double length=route.vertices.get(k).metres;
    double end=station+length;
    java.util.TreeSet<Double> split=new java.util.TreeSet<>();split.add(station);split.add(end);
    for(int j=0;j<widthSections.length();j++){
     org.json.JSONObject o=widthSections.optJSONObject(j);if(o==null)continue;
     double lo=o.optDouble("from"),hi=o.optDouble("to");
     if(lo>station&&lo<end)split.add(lo);if(hi>station&&hi<end)split.add(hi);
    }
    for(double[] section:depthSections){if(section[0]>station&&section[0]<end)split.add(section[0]);if(section[1]>station&&section[1]<end)split.add(section[1]);}
    java.util.ArrayList<Double> ss=new java.util.ArrayList<>(split);
    for(int j=1;j<ss.size();j++){
     double lo=ss.get(j-1),hi=ss.get(j),mid=(lo+hi)/2,w=widths[0],l=widths[1],rr=widths[2];
     for(int q=0;q<widthSections.length();q++){
      org.json.JSONObject o=widthSections.optJSONObject(q);if(o==null||!(mid>=o.optDouble("from")&&mid<o.optDouble("to")))continue;
      w=o.optDouble("width");if(o.has("left"))l=o.optDouble("left");if(o.has("right"))rr=o.optDouble("right");
     }
     double t=(lo-station)/length,u=(hi-station)/length;
     mainVolumeStrips.add(new double[]{a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t,a[0]+(b[0]-a[0])*u,a[1]+(b[1]-a[1])*u,w/2,w/2,0});
     for(double[] section:depthSections)if(mid>=section[0]&&mid<section[1])mainVolumeStrips.get(mainVolumeStrips.size()-1)[6]=section[2];

     strips.add(new double[]{a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t,a[0]+(b[0]-a[0])*u,a[1]+(b[1]-a[1])*u,w>0&&l>=0?w/2+l:Double.NaN,w>0&&rr>=0?w/2+rr:Double.NaN});
    }
    station=end;
   }
   surveyedProfile[0]=null;
   if(elevationInput.getText().toString().trim().isEmpty())elevationStatus.setText("GOK: relative Darstellung");
   else try{
    java.util.List<double[]> elevations=new java.util.ArrayList<>();
    for(String line:elevationInput.getText().toString().split("\\n")){
     if(line.trim().isEmpty())continue;
     String[] parts=line.split(";",-1);if(parts.length!=2)throw new IllegalArgumentException("Format Station;Höhe");
     elevations.add(new double[]{Double.parseDouble(parts[0].trim().replace(',','.')),Double.parseDouble(parts[1].trim().replace(',','.'))});
    }
    surveyedProfile[0]=new ElevationProfile(elevations,total);
    StringBuilder slopes=new StringBuilder("GOK – Gefälle (+ abwärts):");
    java.util.List<double[]> points=surveyedProfile[0].stations();
    for(int k=1;k<points.size();k++){
     double from=points.get(k-1)[0],to=points.get(k)[0];
     slopes.append(String.format(java.util.Locale.GERMANY,"\n%.2f–%.2f m: %+.2f %%",from,to,surveyedProfile[0].fallPercent(from,to)));
    }
    if(points.get(0)[0]>0||points.get(points.size()-1)[0]<total)slopes.append("\nGOK nur im vermessenen Bereich bekannt.");
    elevationStatus.setText(slopes.toString());
   }catch(Exception ex){elevationStatus.setText("GOK: ungültige Höhenpunkte – "+ex.getMessage());}
   profileData.clear();
   if(depthSectionsValid){
    double fallback=Double.NaN;
    try{fallback=Double.parseDouble(mainExcavationDepth.getText().toString().trim().replace(',','.'));}catch(Exception ignored){}
    boolean complete=true;
    for(double[] strip:mainVolumeStrips){
     double depth=strip[6]>0?strip[6]:fallback;
     double len=Math.hypot(strip[2]-strip[0],strip[3]-strip[1]);
     if(!Double.isFinite(depth)||depth<=0||!Double.isFinite(len)){complete=false;break;}
     if(len>0)profileData.add(new double[]{len,depth});
    }
    if(!complete)profileData.clear();
   }
   pipeDimensions[0]=null;
   pipeProfileStatus.setText("");
   if(!pipeProfileInput.getText().toString().trim().isEmpty())try{
    String[] parts=pipeProfileInput.getText().toString().split(";",-1);
    if(parts.length!=3)throw new IllegalArgumentException("Drei Maße erforderlich");
    double[] dims=new double[3];for(int k=0;k<3;k++)dims[k]=Double.parseDouble(parts[k].trim().replace(',','.'));
    new PipeSection(0,1,dims[2],dims[0],dims[1]);
    pipeDimensions[0]=dims;
    double minCover=Double.POSITIVE_INFINITY;
    for(double[] v:profileData)minCover=Math.min(minCover,new PipeSection(0,v[1],dims[2],dims[0],dims[1]).cover);
    pipeProfileStatus.setText(Double.isFinite(minCover)?String.format(java.util.Locale.GERMANY,"Kleinste Rohrüberdeckung: %.2f m%s",minCover,minCover<0?" – Rohr ragt über GOK":""):"Rohrprofil: Grabentiefe fehlt");
    if(!minimumCoverInput.getText().toString().trim().isEmpty()){
     double minimum=Double.parseDouble(minimumCoverInput.getText().toString().trim().replace(',','.'));
     if(!Double.isFinite(minimum)||minimum<0)throw new IllegalArgumentException("Ungültige Mindestüberdeckung");
     double profileStation=0;StringBuilder warnings=new StringBuilder();
     for(double[] v:profileData){
      PipeSection section=new PipeSection(0,v[1],dims[2],dims[0],dims[1]);
      if(!section.meetsCover(minimum))warnings.append(String.format(java.util.Locale.GERMANY,"\nStation %.2f–%.2f m: Überdeckung %.2f m < %.2f m",profileStation,profileStation+v[0],section.cover,minimum));
      profileStation+=v[0];
     }
     if(profileData.isEmpty())warnings.append("\nKeine Prüfung ohne Grabentiefe.");
     else if(warnings.length()==0)warnings.append("\nEingegebene Mindestüberdeckung eingehalten.");
     pipeProfileStatus.append(warnings.toString());
    }
   }catch(Exception ex){pipeProfileStatus.setText("Rohrprofil: "+ex.getMessage());}
   longitudinalProfile.invalidate();
   java.util.TreeSet<Double> cuts=new java.util.TreeSet<>();cuts.add(0.0);cuts.add(total);
   for(org.json.JSONArray sections:new org.json.JSONArray[]{widthSections,surfaces})for(int j=0;j<sections.length();j++){
    org.json.JSONObject o=sections.optJSONObject(j);if(o==null)continue;double from=o.optDouble("from",Double.NaN),to=o.optDouble("to",Double.NaN);
    if(Double.isFinite(from)&&Double.isFinite(to)&&from>=0&&to<=total&&to>from){cuts.add(from);cuts.add(to);}
   }
   java.util.ArrayList<Double> stations=new java.util.ArrayList<>(cuts);
   java.util.TreeMap<String,Double> bySurface=new java.util.TreeMap<>(),cutsBySurface=new java.util.TreeMap<>();
   double area=0,unmeasured=0,unknownSurface=0,nachschnittArea=0,nachschnittLeft=0,nachschnittRight=0;boolean missingCuts=false;int surfaceConflicts=0;
   for(int i=1;i<stations.size();i++){
    double from=stations.get(i-1),to=stations.get(i),mid=(from+to)/2,w=widths[0];
    for(int j=0;j<widthSections.length();j++){org.json.JSONObject o=widthSections.optJSONObject(j);if(o!=null&&mid>=o.optDouble("from",Double.NaN)&&mid<o.optDouble("to",Double.NaN))w=o.optDouble("width",Double.NaN);}
    if(!Double.isFinite(w)||w<=0){unmeasured+=to-from;continue;}
    double part=CivilGeometry.rectangularArea(to-from,w);area+=part;
    double cutLeft=widths[1],cutRight=widths[2];for(int j=0;j<widthSections.length();j++){org.json.JSONObject o=widthSections.optJSONObject(j);if(o!=null&&mid>=o.optDouble("from",Double.NaN)&&mid<o.optDouble("to",Double.NaN)){if(o.has("left"))cutLeft=o.optDouble("left",Double.NaN);if(o.has("right"))cutRight=o.optDouble("right",Double.NaN);}}
    if(Double.isFinite(cutLeft)&&cutLeft>=0&&Double.isFinite(cutRight)&&cutRight>=0){nachschnittLeft+=(to-from)*cutLeft;nachschnittRight+=(to-from)*cutRight;nachschnittArea+=(to-from)*(cutLeft+cutRight);}else missingCuts=true;
    String surface=null;int matches=0;
    for(int j=0;j<surfaces.length();j++){org.json.JSONObject o=surfaces.optJSONObject(j);if(o==null)continue;
     double lo=o.optDouble("from",Double.NaN),hi=o.optDouble("to",Double.NaN);String name=o.optString("surface","").trim();
     if(Double.isFinite(lo)&&Double.isFinite(hi)&&lo>=0&&hi<=total&&lo<=mid&&mid<hi&&!name.isEmpty()){surface=name;matches++;}
    }
    if(matches==1){bySurface.put(surface,bySurface.containsKey(surface)?bySurface.get(surface)+part:part);if(Double.isFinite(cutLeft)&&cutLeft>=0&&Double.isFinite(cutRight)&&cutRight>=0){double extra=CivilGeometry.rectangularArea(to-from,cutLeft+cutRight);cutsBySurface.put(surface,cutsBySurface.containsKey(surface)?cutsBySurface.get(surface)+extra:extra);}}
    else{unknownSurface+=part;if(matches>1)surfaceConflicts++;}
   }
   StringBuilder result=new StringBuilder(String.format(java.util.Locale.GERMANY,unmeasured>0?"Rechnerische Grabenfläche (nur bemaßte Abschnitte): %.2f m²":"Rechnerische Grabenfläche (Länge × Breite): %.2f m²",area));
   if(!missingCuts&&unmeasured==0)result.append(String.format(java.util.Locale.GERMANY,"\\nNachschnitt links: %.2f m²\\nNachschnitt rechts: %.2f m²\\nNachschnitt gesamt: %.2f m²",nachschnittLeft,nachschnittRight,nachschnittArea));
   else if(!missingCuts)result.append(String.format(java.util.Locale.GERMANY,"\\nNachschnitt (nur bemaßte Abschnitte): %.2f m²",nachschnittArea));
   else result.append("\\nNachschnitt-Zusatzfläche: unvollständig bemaßt (links/rechts).");
   if(!missingCuts&&unmeasured==0)result.append(String.format(java.util.Locale.GERMANY,"\\nRechnerische Gesamtfläche inkl. Nachschnitt: %.2f m²",area+nachschnittArea));
   else result.append("\\nGesamtfläche: nicht vollständig berechenbar (fehlende Maße).");
   for(java.util.Map.Entry<String,Double> item:bySurface.entrySet()){result.append(String.format(java.util.Locale.GERMANY,"\\n%s · Graben: %.2f m²",item.getKey(),item.getValue()));Double cut=cutsBySurface.get(item.getKey());if(cut!=null)result.append(String.format(java.util.Locale.GERMANY," · Nachschnitt: %.2f m²",cut));}
   if(unknownSurface>0)result.append(String.format(java.util.Locale.GERMANY,"\\nOberfläche unklar / nicht zugeordnet: %.2f m²",unknownSurface));
   if(unmeasured>0)result.append(String.format(java.util.Locale.GERMANY,"\\nNicht bemaßte Länge: %.2f m",unmeasured));
   if(surfaceConflicts>0)result.append("\\nWarnung: überlappende Oberflächenzuordnungen.");
   result.append("\\nNur rechnerischer Kontrollwert; keine Abrechnungsfreigabe.");
   if(!missingCuts&&unmeasured==0){
    try{
     StripOverlap.Result geometric=StripOverlap.calculate(strips);
     try{
      java.util.List<double[]> combined=new java.util.ArrayList<>(strips);
      java.util.List<RouteBranch> branches=branchStore.load();
      double branchLength=0,branchArea=0;
      double branchVolume=0;
      java.util.List<double[]> branchVolumeStrips=new java.util.ArrayList<>();
      java.util.Map<String,double[]> branchQuantities=new java.util.TreeMap<>();

      for(RouteBranch branch:branches){
       java.util.List<double[]> points=branch.vertices(model.vertices);
       branchLength+=branch.length();branchArea+=branch.nominalArea();
       branchVolume+=branch.nominalVolume();
       String surface=branch.surface.trim().isEmpty()?"Nicht zugeordnet":branch.surface;
       double[] quantities=branchQuantities.get(surface);
       if(quantities==null){quantities=new double[3];branchQuantities.put(surface,quantities);}
       quantities[0]+=branch.length();quantities[1]+=branch.nominalArea();quantities[2]+=branch.nominalVolume();
       for(int k=1;k<points.size();k++){
        double[] p=points.get(k-1),q=points.get(k);
        combined.add(new double[]{p[0],p[1],q[0],q[1],branch.width/2,branch.width/2});
        branchVolumeStrips.add(new double[]{p[0],p[1],q[0],q[1],branch.width/2,branch.width/2,branch.depth});
       }
      }
      if(!branches.isEmpty()){
       StripOverlap.Result combinedArea=StripOverlap.calculate(combined);
       result.append(String.format(java.util.Locale.GERMANY,"\nGesamttrasse inkl. Abzweigungen (gerade Streifen):\nAbzweiglänge: %.2f m\nAbzweigfläche nominal: %.2f m²\nBrutto gesamt: %.2f m²\nÜberlappung gesamt: %.2f m²\nNetto gesamt: %.2f m²",branchLength,branchArea,combinedArea.gross,combinedArea.overlap,combinedArea.net));
       result.append(String.format(java.util.Locale.GERMANY,"\nAbzweige – Aushub nominal: %.2f m³",branchVolume));
       for(java.util.Map.Entry<String,double[]> entry:branchQuantities.entrySet()){
        double[] q=entry.getValue();
        result.append(String.format(java.util.Locale.GERMANY,"\n%s (Abzweige): %.2f m · %.2f m² · %.2f m³",entry.getKey(),q[0],q[1],q[2]));
       }
       result.append("\nAushub brutto (Länge × Breite × Tiefe); räumliche Überschneidungen nicht abgezogen.");
       int combinedMode=overlapMode.getSelectedItemPosition();
       StripVolume.Result branchVolumes=StripVolume.calculate(branchVolumeStrips);
       result.append(String.format(java.util.Locale.GERMANY,"\nAushub nur Abzweigungen (gemeinsame Bezugshöhe):\nBrutto: %.2f m³\nVolumenüberschneidung: %.2f m³\nNetto: %.2f m³",branchVolumes.gross,branchVolumes.overlap,branchVolumes.net));
       if(combinedMode==1||combinedMode==2)result.append(String.format(java.util.Locale.GERMANY,"\nAushub-Kontrollwert Abzweigungen: %.2f m³",combinedMode==1?branchVolumes.gross:branchVolumes.net));
       result.append("\nHauptgraben nicht in dieser Volumenprüfung enthalten; keine Abrechnungsfreigabe.");
       try{
        if(!depthSectionsValid)throw new IllegalArgumentException("Ungültige oder überlappende Tiefenabschnitte");
        double depth=Double.NaN;
        try{depth=Double.parseDouble(mainExcavationDepth.getText().toString().trim().replace(',','.'));}catch(NumberFormatException ignored){}

        java.util.List<double[]> allVolume=new java.util.ArrayList<>(branchVolumeStrips);
        for(double[] strip:mainVolumeStrips){if(strip[6]<=0)strip[6]=depth;if(!Double.isFinite(strip[6])||strip[6]<=0)throw new IllegalArgumentException("Grabentiefe fehlt");allVolume.add(strip);}
        StripVolume.Result all=StripVolume.calculate(allVolume);
        result.append(String.format(java.util.Locale.GERMANY,"\nAushub Gesamttrasse inkl. Abzweigungen:\nBrutto: %.2f m³\nÜberschneidung: %.2f m³\nNetto: %.2f m³",all.gross,all.overlap,all.net));
        if(combinedMode==1||combinedMode==2)result.append(String.format(java.util.Locale.GERMANY,"\nAushub-Kontrollwert Gesamttrasse: %.2f m³",combinedMode==1?all.gross:all.net));
        result.append("\nAbschnittsweise Hauptgrabentiefe, gemeinsame Bezugshöhe, ohne Nachschnitt und Eckergänzungen.");
       }catch(Exception ex){result.append("\nGesamt-Aushub: Tiefenangaben fehlen oder Tiefenabschnitte sind ungültig.");}


       if(combinedMode==1||combinedMode==2)result.append(String.format(java.util.Locale.GERMANY,"\nKontrollwert Gesamttrasse: %.2f m²",combinedMode==1?combinedArea.gross:combinedArea.net));
       result.append("\nAbzweige ohne Nachschnitt und Eckergänzungen; keine automatische Abrechnung.");
      }
     }catch(Exception ex){result.append("\nGesamtfläche inkl. Abzweigungen nicht vollständig berechenbar.");}

     StripOverlap.Result withCorners=StripOverlap.calculateWithCorners(strips);
     result.append(String.format(java.util.Locale.GERMANY,"\nAlternative mit abgeschrägten Außenecken:\nBruttofläche: %.2f m²\nNettofläche: %.2f m²\nNetto-Differenz: %+.2f m²",withCorners.gross,withCorners.net,withCorners.net-geometric.net));
     result.append("\nEckmodell nur zur Kontrolle; noch nicht mit Zeichnung oder Abrechnung abgeglichen.");
     result.append(String.format(java.util.Locale.GERMANY,"\nGeometrische Kontrollwerte (inkl. Nachschnitt):\nBruttofläche: %.2f m²\nÜberlappungsfläche: %.2f m²\nNettofläche: %.2f m²",geometric.gross,geometric.overlap,geometric.net));
     result.append("\nGerade Abschnittsstreifen ohne Eckergänzungen; keine automatische Abrechnung.");
     int mode=overlapMode.getSelectedItemPosition();
     if(mode==1||mode==2){
      int corner=cornerMode.getSelectedItemPosition();
      StripOverlap.Result selected=corner==2?StripOverlap.calculateWithMiter(strips):corner==1?withCorners:geometric;
      double base=mode==1?selected.gross:selected.net;
      result.append("\nGewähltes Eckmodell: "+cornerLabels[Math.max(0,Math.min(2,corner))]);
      result.append(String.format(java.util.Locale.GERMANY,"\nGewählter Kontrollwert: %.2f m²",base));
      double delta=Double.parseDouble(overlapPrefs.getString("metricAreaCorrection","0"));
      if(Double.isFinite(delta)&&Double.isFinite(base+delta)&&base+delta>=0){
       result.append(String.format(java.util.Locale.GERMANY,"\nManuelle Korrektur: %+.2f m²\nKorrigierter Kontrollwert: %.2f m²",delta,base+delta));
       if(delta!=0)result.append("\nBegründung: "+overlapPrefs.getString("metricAreaCorrectionReason",""));
      }else result.append("\nKorrektur ungültig für aktuelle Fläche.");
     }
    }catch(RuntimeException e){result.append("\nGeometrische Flächenprüfung nicht verfügbar.");}
   }
   nominalArea.setText(result.toString());
  };
  minimumCoverInput.addTextChangedListener(new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){}
   public void afterTextChanged(android.text.Editable e){overlapPrefs.edit().putString("metricMinimumCover",e.toString()).apply();refreshNominalArea.run();}
  });
  pipeProfileInput.addTextChangedListener(new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){}
   public void afterTextChanged(android.text.Editable e){overlapPrefs.edit().putString("metricPipeProfile",e.toString()).apply();refreshNominalArea.run();}
  });
  elevationInput.addTextChangedListener(new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){}
   public void afterTextChanged(android.text.Editable e){overlapPrefs.edit().putString("metricElevationPoints",e.toString()).apply();refreshNominalArea.run();}
  });
  depthSectionsInput.addTextChangedListener(new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){}
   public void afterTextChanged(android.text.Editable e){overlapPrefs.edit().putString("metricDepthSections",e.toString()).apply();refreshNominalArea.run();}
  });
  mainExcavationDepth.addTextChangedListener(new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){}
   public void afterTextChanged(android.text.Editable e){overlapPrefs.edit().putString("metricMainExcavationDepth",e.toString()).apply();refreshNominalArea.run();}
  });
  cornerMode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   @Override public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int position,long id){
    if(position<0||position>2)return;
    if(position!=savedCornerMode[0]){
     if(!overlapPrefs.edit().putInt("metricCornerMode",position).commit()){
      cornerMode.setSelection(savedCornerMode[0]);notice("Eckmodell konnte nicht gespeichert werden.");return;
     }
     savedCornerMode[0]=position;
    }
    refreshNominalArea.run();panel.invalidate();
    for(int i=0;i<panel.getChildCount();i++)panel.getChildAt(i).invalidate();
   }
   @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
  });
  saveCorrection.setOnClickListener(v->{
   try{
    double delta=Double.parseDouble(correction.getText().toString().trim().replace(',','.'));
    String reason=correctionReason.getText().toString().trim();
    if(!Double.isFinite(delta)||(delta!=0&&reason.isEmpty())){notice("Gültige Korrektur und Begründung erforderlich.");return;}
    if(!overlapPrefs.edit().putString("metricAreaCorrection",Double.toString(delta)).putString("metricAreaCorrectionReason",reason).commit()){notice("Speichern fehlgeschlagen.");return;}
    refreshNominalArea.run();notice("Korrektur gespeichert; Kontrollwert prüfen.");
   }catch(NumberFormatException e){notice("Bitte gültige Fläche eingeben.");}
  });
  overlapMode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   @Override public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int position,long id){
    if(position<0||position>2)return;
    if(position!=savedOverlapMode[0]){
     if(!overlapPrefs.edit().putInt("metricOverlapMode",position).commit()){
      overlapMode.setSelection(savedOverlapMode[0]);notice("Berechnungsart konnte nicht gespeichert werden.");return;
     }
     savedOverlapMode[0]=position;
    }
    refreshNominalArea.run();
   }
   @Override public void onNothingSelected(android.widget.AdapterView<?> parent){refreshNominalArea.run();}
  });
  refreshNominalArea.run();
  TextView info=new TextView(this);info.setText("Gemessene Längen · Richtungen aus Skizze (nicht vermessen)");panel.addView(info);
  TextView widthHint=new TextView(this);widthHint.setText("Grabenbreite: "+(Double.isFinite(widths[0])?widths[0]+" m":"offen")+" · Nachschnitt links: "+(Double.isFinite(widths[1])?widths[1]+" m":"offen")+" · rechts: "+(Double.isFinite(widths[2])?widths[2]+" m":"offen")+"\nVariable Breiten: Abschnittskonturen an Knicken schematisch; keine Flächenberechnung.");panel.addView(widthHint);
  TextView surfaceHint=new TextView(this);surfaceHint.setText("Oberflächen: "+surfaces.length()+" gespeicherte Abschnitte · Standardfarben und Stationsmaße; ungültige Stationen werden ausgelassen.");panel.addView(surfaceHint);
  TextView montageHint=new TextView(this);montageHint.setText("Montage: "+montagePoints.size()+" darstellbare Stationspunkte. Fehlender Versatz = Achse (Vorschau); Eckversatz nicht dargestellt.");panel.addView(montageHint);
  android.view.View drawing=new android.view.View(this){
   final java.util.List<float[]> branchHits=new java.util.ArrayList<>();
   final java.util.List<String> branchHitIds=new java.util.ArrayList<>();
   double touchScale,touchOx,touchOy;
   double mainTapStation=Double.NaN;
   final java.util.List<double[]> utilityWarningIntervals=new java.util.ArrayList<>();
   int downHit=-1;float downX,downY,previewX,previewY;boolean previewActive=false;
   @Override public boolean onTouchEvent(android.view.MotionEvent event){
    if(event.getAction()==android.view.MotionEvent.ACTION_DOWN){
     previewActive=false;downX=event.getX();downY=event.getY();downHit=-1;double distance=dp(18);
     for(int i=0;i<branchHits.size();i++){float[] p=branchHits.get(i);double d=Math.hypot(downX-p[0],downY-p[1]);if(d<distance){distance=d;downHit=i;}}
     mainTapStation=Double.NaN;
     if(downHit<0&&touchScale>0){
      double x=(downX-touchOx)/touchScale,y=(touchOy-downY)/touchScale,station=0,best=dp(18)/touchScale;
      for(int k=1;k<model.vertices.size();k++){
       double[] p=model.vertices.get(k-1),q=model.vertices.get(k);double dx=q[0]-p[0],dy=q[1]-p[1],n=Math.hypot(dx,dy);
       if(n<=0)continue;
       double f=Math.max(0,Math.min(1,((x-p[0])*dx+(y-p[1])*dy)/(n*n)));
       double dist=Math.hypot(x-p[0]-f*dx,y-p[1]-f*dy);
       if(dist<best){best=dist;mainTapStation=station+f*n;}
       station+=n;
      }
     }
     if(downHit<0&&!Double.isFinite(mainTapStation))return false;
     getParent().requestDisallowInterceptTouchEvent(true);return true;
    }
    if(event.getAction()==android.view.MotionEvent.ACTION_CANCEL){downHit=-1;previewActive=false;invalidate();getParent().requestDisallowInterceptTouchEvent(false);return true;}
    if(event.getAction()==android.view.MotionEvent.ACTION_MOVE&&downHit>=0){
     previewX=event.getX();previewY=event.getY();
     previewActive=downHit<branchHits.size()&&branchHits.get(downHit)[2]>0&&Math.hypot(previewX-downX,previewY-downY)>dp(8);
     invalidate();return true;
    }
    if(event.getAction()!=android.view.MotionEvent.ACTION_UP)return downHit>=0;
    previewActive=false;invalidate();
    getParent().requestDisallowInterceptTouchEvent(false);
    int best=downHit;downHit=-1;
    if(best<0&&Double.isFinite(mainTapStation)){
     double station=mainTapStation;mainTapStation=Double.NaN;
     if(Math.hypot(event.getX()-downX,event.getY()-downY)<=dp(8)){
      boolean warning=false;for(double[] interval:utilityWarningIntervals)if(station>=interval[0]&&station<=interval[1]){warning=true;break;}
      String[] actions=warning?new String[]{"Leitungen prüfen / bearbeiten","Abzweigung hinzufügen"}:new String[]{"Abzweigung hinzufügen"};
      final boolean hasWarning=warning;
      new android.app.AlertDialog.Builder(MainActivity.this).setTitle(String.format(java.util.Locale.GERMANY,"Station %.3f m",station)).setItems(actions,(dialog,which)->{if(hasWarning&&which==0){
       org.json.JSONArray rows=extraUtilities();java.util.ArrayList<String> labels=new java.util.ArrayList<>();java.util.ArrayList<Integer> indices=new java.util.ArrayList<>();
       for(int k=0;k<rows.length();k++){org.json.JSONObject u=rows.optJSONObject(k);if(u!=null&&station>=u.optDouble("routeFrom",0)&&station<=u.optDouble("routeTo",Double.POSITIVE_INFINITY)){indices.add(k);labels.add(u.optString("name",u.optString("type"))+" · "+u.optDouble("diameter")+" mm");}}
       if(indices.isEmpty()){notice("Keine Leitung an dieser Station");return;}
       if(indices.size()==1){editUtilityPosition(indices.get(0),station,()->invalidate());return;}
       new android.app.AlertDialog.Builder(MainActivity.this).setTitle("Leitung an dieser Station wählen").setItems(labels.toArray(new String[0]),(dd,ii)->editUtilityPosition(indices.get(ii),station,()->invalidate())).setNegativeButton("Abbrechen",null).show();return;
      }requestedBranchStation[0]=station;branchEditor.performClick();}).setNegativeButton("Abbrechen",null).show();
     }
     return true;
    }
    if(best>=0&&best<branchHits.size()&&Math.hypot(event.getX()-downX,event.getY()-downY)>dp(8)){
     final String moveId=branchHitIds.get(best);final int moveIndex=(int)branchHits.get(best)[2];
     if(moveIndex==0||touchScale<=0){notice("Anschlussstation über Abzweigungen verwalten ändern");return true;}
     final double x=(event.getX()-touchOx)/touchScale,y=(touchOy-event.getY())/touchScale;
     new android.app.AlertDialog.Builder(MainActivity.this).setTitle("Punkt verschieben").setItems(new String[]{"Mit Winkelraster 15°","Ohne Winkelraster"},(dialog,which)->{
      try{java.util.List<RouteBranch> branches=branchStore.load();boolean found=false;
       for(int j=0;j<branches.size();j++)if(branches.get(j).id.equals(moveId)){branches.set(j,BranchPointEditor.move(branches.get(j),model.vertices,moveIndex,x,y,which==0));found=true;break;}
       if(!found)throw new IllegalStateException("Abzweigung nicht gefunden");
       branchStore.save(branches);branchChanged[0].run();invalidate();notice("Punkt verschoben");
      }catch(Exception ex){notice("Verschieben fehlgeschlagen: "+ex.getMessage());}
     }).setNegativeButton("Abbrechen",null).show();return true;
    }
    if(best<0)return true;
    final String id=branchHitIds.get(best);final int index=(int)branchHits.get(best)[2];
    if(index==0){notice("Anschlussstation über Abzweigungen verwalten ändern");return true;}
    new android.app.AlertDialog.Builder(MainActivity.this).setTitle("Knickpunkt "+index).setItems(new String[]{"Punkt löschen und verbinden","Knickpunkt danach einfügen"},(dialog,which)->{
     if(which==1){
      try{
       java.util.List<RouteBranch> branches=branchStore.load();boolean found=false;
       for(int j=0;j<branches.size();j++)if(branches.get(j).id.equals(id)){
        RouteBranch branch=branches.get(j);java.util.List<double[]> points=branch.vertices(model.vertices);
        if(index>=points.size()-1)throw new IllegalArgumentException("Kein Folgeabschnitt vorhanden");
        double[] a=points.get(index),b=points.get(index+1);
        branches.set(j,BranchPointEditor.insert(branch,model.vertices,index,(a[0]+b[0])/2,(a[1]+b[1])/2));found=true;break;
       }
       if(!found)throw new IllegalStateException("Abzweigung nicht gefunden");
       branchStore.save(branches);branchChanged[0].run();invalidate();notice("Knickpunkt eingefügt – zum Verschieben ziehen");
      }catch(Exception ex){notice("Einfügen fehlgeschlagen: "+ex.getMessage());}
      return;
     }
     new android.app.AlertDialog.Builder(MainActivity.this).setMessage("Punkt löschen und Abschnittslängen neu berechnen?").setNegativeButton("Abbrechen",null).setPositiveButton("Löschen",(d,w)->{
      try{java.util.List<RouteBranch> branches=branchStore.load();boolean found=false;
       for(int j=0;j<branches.size();j++)if(branches.get(j).id.equals(id)){branches.set(j,BranchPointEditor.remove(branches.get(j),model.vertices,index));found=true;break;}
       if(!found)throw new IllegalStateException("Abzweigung nicht gefunden");
       branchStore.save(branches);branchChanged[0].run();invalidate();notice("Punkt gelöscht");
      }catch(Exception ex){notice("Punkt konnte nicht gelöscht werden: "+ex.getMessage());}
     }).show();
    }).show();performClick();return true;
   }
   @Override public boolean performClick(){super.performClick();return true;}
   final android.graphics.Paint pen=new android.graphics.Paint(3);
   double cutAt(double station,String side,double fallback){
    for(int j=0;j<widthSections.length();j++){org.json.JSONObject o=widthSections.optJSONObject(j);if(o==null)continue;if(station>=o.optDouble("from",Double.NaN)&&station<o.optDouble("to",Double.NaN)&&o.has(side)){double v=o.optDouble(side,Double.NaN);if(Double.isFinite(v)&&v>=0)return v;}}
    return fallback;
   }
   double widthAt(double station){
    for(int j=0;j<widthSections.length();j++){org.json.JSONObject o=widthSections.optJSONObject(j);if(o==null)continue;double from=o.optDouble("from"),to=o.optDouble("to"),w=o.optDouble("width");if(from>=0&&to<=route.measuredLength()&&station>=from&&station<to&&Double.isFinite(w)&&w>0)return w;}
    return widths[0];
   }
   @Override protected void onDraw(android.graphics.Canvas c){
    super.onDraw(c);branchHits.clear();branchHitIds.clear();double minX=0,maxX=0,minY=0,maxY=0;
    for(double[] p:model.vertices){minX=Math.min(minX,p[0]);maxX=Math.max(maxX,p[0]);minY=Math.min(minY,p[1]);maxY=Math.max(maxY,p[1]);}
    for(double[] p:montagePoints){minX=Math.min(minX,p[0]);maxX=Math.max(maxX,p[0]);minY=Math.min(minY,p[1]);maxY=Math.max(maxY,p[1]);}
    double margin=Double.isFinite(widths[0])?widths[0]/2+Math.max(Double.isFinite(widths[1])?widths[1]:0,Double.isFinite(widths[2])?widths[2]:0):0;
    for(int j=0;j<widthSections.length();j++){org.json.JSONObject o=widthSections.optJSONObject(j);if(o!=null){double w=o.optDouble("width");if(Double.isFinite(w)&&w>0){double left=o.has("left")?o.optDouble("left",0):(Double.isFinite(widths[1])?widths[1]:0),right=o.has("right")?o.optDouble("right",0):(Double.isFinite(widths[2])?widths[2]:0);margin=Math.max(margin,w/2+Math.max(left,right));}}}
    java.util.List<RouteBranch> visibleBranches=new java.util.ArrayList<>();
    java.util.List<java.util.List<double[]>> branchPoints=new java.util.ArrayList<>();
    int branchErrors=0;
    try{
     for(RouteBranch branch:branchStore.load()){
      try{
       java.util.List<double[]> points=branch.vertices(model.vertices);
       visibleBranches.add(branch);branchPoints.add(points);
       for(double[] p:points){minX=Math.min(minX,p[0]-branch.width/2);maxX=Math.max(maxX,p[0]+branch.width/2);minY=Math.min(minY,p[1]-branch.width/2);maxY=Math.max(maxY,p[1]+branch.width/2);}
      }catch(IllegalArgumentException ex){branchErrors++;}
     }
    }catch(Exception ex){branchErrors++;}
    margin*=2;minX-=margin;maxX+=margin;minY-=margin;maxY+=margin;
    double scale=Math.min((getWidth()-dp(48))/Math.max(.001,maxX-minX),(getHeight()-dp(64))/Math.max(.001,maxY-minY));if(!Double.isFinite(scale)||scale<=0)return;
    double ox=(getWidth()-(maxX-minX)*scale)/2-minX*scale,oy=(getHeight()-(maxY-minY)*scale)/2+maxY*scale;
    touchScale=scale;touchOx=ox;touchOy=oy;
    if(widthSections.length()==0){
    if(Double.isFinite(widths[0])){
     double half=widths[0]/2,left=Double.isFinite(widths[1])?widths[1]:0,right=Double.isFinite(widths[2])?widths[2]:0;
     double[][] bands={{-half-right,half+left},{-half,half}};
     for(int j=0;j<bands.length;j++){
      android.graphics.Path shape=new android.graphics.Path();
      java.util.ArrayList<double[]> polygon=new java.util.ArrayList<>();
      for(int side=0;side<2;side++){
       double offset=bands[j][side];int count=model.vertices.size();
       for(int k=0;k<count;k++){
        int i=side==0?k:count-1-k;
        double[] v=model.vertices.get(i);
        int prev=Math.max(0,i-1),next=Math.min(count-1,i+1);
        double[] a=model.vertices.get(prev),b=model.vertices.get(next);
        double dx1=v[0]-a[0],dy1=v[1]-a[1],dx2=b[0]-v[0],dy2=b[1]-v[1];
        if(i==0){dx1=dx2;dy1=dy2;}if(i==count-1){dx2=dx1;dy2=dy1;}
        double n1=Math.hypot(dx1,dy1),n2=Math.hypot(dx2,dy2);
        double nx1=-dy1/n1,ny1=dx1/n1,nx2=-dy2/n2,ny2=dx2/n2;
        double denom=1+nx1*nx2+ny1*ny2;
        if(denom>=0.5){polygon.add(new double[]{v[0]+offset*(nx1+nx2)/denom,v[1]+offset*(ny1+ny2)/denom});}
        else{
         if(side==0){polygon.add(new double[]{v[0]+offset*nx1,v[1]+offset*ny1});polygon.add(new double[]{v[0]+offset*nx2,v[1]+offset*ny2});}
         else{polygon.add(new double[]{v[0]+offset*nx2,v[1]+offset*ny2});polygon.add(new double[]{v[0]+offset*nx1,v[1]+offset*ny1});}
        }
       }
      }
      for(int i=0;i<polygon.size();i++){double[] q=polygon.get(i);float x=(float)(ox+q[0]*scale),y=(float)(oy-q[1]*scale);if(i==0)shape.moveTo(x,y);else shape.lineTo(x,y);}
      shape.close();pen.setColor(j==0?0xff777e86:0xffb79b70);c.drawPath(shape,pen);
     }
    }
    if(Double.isFinite(widths[0])){
     double total=route.measuredLength();
     for(int j=0;j<surfaces.length();j++){
      org.json.JSONObject section=surfaces.optJSONObject(j);if(section==null)continue;
      double from=section.optDouble("from"),to=section.optDouble("to");String name=section.optString("surface");
      if(!Double.isFinite(from)||!Double.isFinite(to)||from<0||to>total||to<=from||name.trim().isEmpty())continue;
      double start=0;
      for(int i=1;i<model.vertices.size();i++){
       double end=start+route.vertices.get(i).metres,lo=Math.max(start,from),hi=Math.min(end,to);
       if(hi>lo){double[] u=model.station(lo),v=model.station(hi);if(u!=null&&v!=null){
        pen.setColor(route.configuredSurfaceColor(name,route.legendSettings()));pen.setStrokeWidth((float)(widths[0]*scale));pen.setStrokeCap(android.graphics.Paint.Cap.BUTT);
        c.drawLine((float)(ox+u[0]*scale),(float)(oy-u[1]*scale),(float)(ox+v[0]*scale),(float)(oy-v[1]*scale),pen);
       }}start=end;
      }
      double[] mid=model.station((from+to)/2);if(mid!=null){pen.setColor(0xff777777);pen.setTextSize(dp(11));c.drawText(name,(float)(ox+mid[0]*scale),(float)(oy-mid[1]*scale+widths[0]*scale/2+dp(14)),pen);}
     }
    }
    }else{
     java.util.TreeSet<Double> cuts=new java.util.TreeSet<>();cuts.add(0.0);double total=route.measuredLength(),station=0;
     for(int i=1;i<route.vertices.size();i++){station+=route.vertices.get(i).metres;cuts.add(station);}
     for(org.json.JSONArray arr:new org.json.JSONArray[]{widthSections,surfaces})for(int j=0;j<arr.length();j++){org.json.JSONObject o=arr.optJSONObject(j);if(o==null)continue;double from=o.optDouble("from"),to=o.optDouble("to");if(Double.isFinite(from)&&Double.isFinite(to)&&from>=0&&to<=total&&to>from){cuts.add(from);cuts.add(to);}}
     java.util.ArrayList<Double> stations=new java.util.ArrayList<>(cuts);
     double left=Double.isFinite(widths[1])?widths[1]:0,right=Double.isFinite(widths[2])?widths[2]:0;
     for(int pass=0;pass<2;pass++)for(int i=1;i<stations.size();i++){
      double from=stations.get(i-1),to=stations.get(i),mid=(from+to)/2,w=widthAt(mid);if(!Double.isFinite(w)||w<=0)continue;
      double[] u=model.station(from),v=model.station(to);if(u==null||v==null)continue;
      double dx=v[0]-u[0],dy=v[1]-u[1],n=Math.hypot(dx,dy);if(n<=0)continue;
      double nx=-dy/n,ny=dx/n,lo=-w/2-(pass==0?cutAt(mid,"right",right):0),hi=w/2+(pass==0?cutAt(mid,"left",left):0);
      android.graphics.Path shape=new android.graphics.Path();double[][] points={{u[0]+nx*lo,u[1]+ny*lo},{v[0]+nx*lo,v[1]+ny*lo},{v[0]+nx*hi,v[1]+ny*hi},{u[0]+nx*hi,u[1]+ny*hi}};
      for(int k=0;k<4;k++){float x=(float)(ox+points[k][0]*scale),y=(float)(oy-points[k][1]*scale);if(k==0)shape.moveTo(x,y);else shape.lineTo(x,y);}shape.close();
      int color=pass==0?0xff777e86:0xffb79b70;
      if(pass==1)for(int j=0;j<surfaces.length();j++){org.json.JSONObject o=surfaces.optJSONObject(j);if(o!=null&&mid>=o.optDouble("from")&&mid<o.optDouble("to")&&!o.optString("surface").trim().isEmpty())color=route.configuredSurfaceColor(o.optString("surface"),route.legendSettings());}
      pen.setColor(color);c.drawPath(shape,pen);
     }
     // Join neighboring offset strips at bends. These patches are visual only.
     double bendStation=0;
     for(int i=1;i<model.vertices.size()-1;i++){
      bendStation+=route.vertices.get(i).metres;
      double[] prev=model.vertices.get(i-1),corner=model.vertices.get(i),next=model.vertices.get(i+1);
      double d1x=corner[0]-prev[0],d1y=corner[1]-prev[1],d2x=next[0]-corner[0],d2y=next[1]-corner[1];
      double n1=Math.hypot(d1x,d1y),n2=Math.hypot(d2x,d2y);if(n1<=0||n2<=0)continue;
      double ax=-d1y/n1,ay=d1x/n1,bx=-d2y/n2,by=d2x/n2;
      double before=widthAt(Math.max(0,bendStation-0.000001)),after=widthAt(Math.min(total-0.000001,bendStation+0.000001));
      if(!Double.isFinite(before)||!Double.isFinite(after))continue;
      for(int pass=0;pass<2;pass++){
       double lo1=-before/2-(pass==0?cutAt(Math.max(0,bendStation-0.000001),"right",right):0),hi1=before/2+(pass==0?cutAt(Math.max(0,bendStation-0.000001),"left",left):0);
       double lo2=-after/2-(pass==0?cutAt(Math.min(total-0.000001,bendStation+0.000001),"right",right):0),hi2=after/2+(pass==0?cutAt(Math.min(total-0.000001,bendStation+0.000001),"left",left):0);
       android.graphics.Path patch=new android.graphics.Path();
       double[][] q={{corner[0]+ax*lo1,corner[1]+ay*lo1},{corner[0]+bx*lo2,corner[1]+by*lo2},{corner[0]+bx*hi2,corner[1]+by*hi2},{corner[0]+ax*hi1,corner[1]+ay*hi1}};
       for(int k=0;k<4;k++){float x=(float)(ox+q[k][0]*scale),y=(float)(oy-q[k][1]*scale);if(k==0)patch.moveTo(x,y);else patch.lineTo(x,y);}patch.close();
       int color=pass==0?0xff777e86:0xffb79b70;
       if(pass==1)for(int j=0;j<surfaces.length();j++){org.json.JSONObject o=surfaces.optJSONObject(j);if(o!=null&&bendStation>=o.optDouble("from")&&bendStation<o.optDouble("to")&&!o.optString("surface").trim().isEmpty())color=route.configuredSurfaceColor(o.optString("surface"),route.legendSettings());}
       pen.setColor(color);c.drawPath(patch,pen);
      }
     }
     for(int j=0;j<widthSections.length();j++){org.json.JSONObject o=widthSections.optJSONObject(j);if(o==null)continue;double from=o.optDouble("from"),to=o.optDouble("to"),w=o.optDouble("width");if(!(from>=0&&to<=total&&to>from&&Double.isFinite(w)&&w>0))continue;double[] q=model.station((from+to)/2);if(q==null)continue;pen.setColor(0xff555555);pen.setTextSize(dp(11));c.drawText(String.format(java.util.Locale.GERMANY,"B %.2f m",w),(float)(ox+q[0]*scale),(float)(oy-q[1]*scale-dp(38)),pen);}
    }
    for(int j=0;j<visibleBranches.size();j++){
     RouteBranch branch=visibleBranches.get(j);java.util.List<double[]> points=branchPoints.get(j);
     for(int k=1;k<points.size();k++){
      double[] a=points.get(k-1),b=points.get(k);
      float ax=(float)(ox+a[0]*scale),ay=(float)(oy-a[1]*scale),bx=(float)(ox+b[0]*scale),by=(float)(oy-b[1]*scale);
      pen.setStrokeCap(android.graphics.Paint.Cap.BUTT);pen.setColor(route.configuredSurfaceColor(branch.surface,route.legendSettings()));pen.setStrokeWidth((float)(branch.width*scale));c.drawLine(ax,ay,bx,by,pen);
      pen.setColor(0xff398bda);pen.setStrokeWidth(dp(2));c.drawLine(ax,ay,bx,by,pen);
      pen.setColor(0xff555555);pen.setTextSize(dp(11));c.drawText(String.format(java.util.Locale.GERMANY,"%.2f m",Math.hypot(b[0]-a[0],b[1]-a[1])),(ax+bx)/2,(ay+by)/2-dp(8),pen);
     }
     for(int k=0;k<points.size();k++){double[] point=points.get(k);float px=(float)(ox+point[0]*scale),py=(float)(oy-point[1]*scale);branchHits.add(new float[]{px,py,k});branchHitIds.add(branch.id);pen.setColor(0xff398bda);c.drawCircle(px,py,dp(4),pen);}
     double[] p=points.get(0);pen.setColor(0xff398bda);c.drawCircle((float)(ox+p[0]*scale),(float)(oy-p[1]*scale),dp(4),pen);
     p=points.get(points.size()-1);pen.setColor(0xff555555);pen.setTextSize(dp(11));c.drawText(branch.name,(float)(ox+p[0]*scale),(float)(oy-p[1]*scale-dp(14)),pen);
    }
    if(branchErrors>0){pen.setColor(0xffcc4444);pen.setTextSize(dp(11));c.drawText("Abzweigungen prüfen: "+branchErrors,dp(8),dp(14),pen);}
    // Highlight overlap of measured flat-ended strips, independent of billing mode.
    android.graphics.Path covered=new android.graphics.Path(), repeated=new android.graphics.Path();
    java.util.TreeSet<Double> overlapCuts=new java.util.TreeSet<>();overlapCuts.add(0.0);
    double overlapStation=0;
    for(int k=1;k<route.vertices.size();k++){overlapStation+=route.vertices.get(k).metres;overlapCuts.add(overlapStation);}
    for(int k=0;k<widthSections.length();k++){
     org.json.JSONObject o=widthSections.optJSONObject(k);if(o==null)continue;
     double f=o.optDouble("from"),t=o.optDouble("to");
     if(f>=0&&t<=overlapStation&&t>f){overlapCuts.add(f);overlapCuts.add(t);}
    }
    java.util.ArrayList<Double> oc=new java.util.ArrayList<>(overlapCuts);
    double[] previousStrip=null;
    for(int k=1;k<oc.size();k++){
     double f=oc.get(k-1),t=oc.get(k),mid=(f+t)/2,w=widthAt(mid),l=cutAt(mid,"left",widths[1]),r=cutAt(mid,"right",widths[2]);
     if(!Double.isFinite(w)||w<=0||!Double.isFinite(l)||l<0||!Double.isFinite(r)||r<0)continue;
     double[] u=model.station(f),v=model.station(t);if(u==null||v==null)continue;
     double dx=v[0]-u[0],dy=v[1]-u[1],n=Math.hypot(dx,dy);if(n<=0)continue;
     double nx=-dy/n,ny=dx/n,hi=w/2+l,lo=-w/2-r;
     double[][] pts={{u[0]+nx*lo,u[1]+ny*lo},{v[0]+nx*lo,v[1]+ny*lo},{v[0]+nx*hi,v[1]+ny*hi},{u[0]+nx*hi,u[1]+ny*hi}};
     android.graphics.Path strip=new android.graphics.Path();
     for(int q=0;q<4;q++){float x=(float)(ox+pts[q][0]*scale),y=(float)(oy-pts[q][1]*scale);if(q==0)strip.moveTo(x,y);else strip.lineTo(x,y);}strip.close();
     double[] currentStrip={u[0],u[1],v[0],v[1],hi,-lo};
     if(previousStrip!=null){
      double[][] corner=savedCornerMode[0]==2?StripOverlap.miterCorner(previousStrip,currentStrip):savedCornerMode[0]==1?StripOverlap.cornerFiller(previousStrip,currentStrip):null;
      if(corner!=null){
       android.graphics.Path patch=new android.graphics.Path();
       for(int q=0;q<corner.length;q++){float x=(float)(ox+corner[q][0]*scale),y=(float)(oy-corner[q][1]*scale);if(q==0)patch.moveTo(x,y);else patch.lineTo(x,y);}
       patch.close();pen.setStyle(android.graphics.Paint.Style.FILL);pen.setColor(0x9948a868);c.drawPath(patch,pen);
      }
     }
     previousStrip=currentStrip;
     android.graphics.Path intersection=new android.graphics.Path();
     if(intersection.op(covered,strip,android.graphics.Path.Op.INTERSECT))repeated.op(intersection,android.graphics.Path.Op.UNION);
     covered.op(strip,android.graphics.Path.Op.UNION);
    }
    pen.setStyle(android.graphics.Paint.Style.FILL);pen.setColor(0x99ff9800);c.drawPath(repeated,pen);
    for(int i=1;i<model.vertices.size();i++){
     double[] a=model.vertices.get(i-1),b=model.vertices.get(i);
     float ax=(float)(ox+a[0]*scale),ay=(float)(oy-a[1]*scale),bx=(float)(ox+b[0]*scale),by=(float)(oy-b[1]*scale);
     pen.setColor(0xff398bda);pen.setStrokeWidth(dp(2));c.drawLine(ax,ay,bx,by,pen);
     pen.setTextSize(dp(12));pen.setColor(0xff777777);c.drawText(String.format(java.util.Locale.GERMANY,"%.2f m",route.vertices.get(i).metres),(ax+bx)/2,(ay+by)/2-dp(8),pen);
    }
    // Surface dimensions use measured station intervals, not screen distances.
    java.util.TreeSet<Double> boundaries=new java.util.TreeSet<>();
    double total=route.measuredLength();
    for(int j=0;j<surfaces.length();j++){
     org.json.JSONObject section=surfaces.optJSONObject(j);if(section==null)continue;
     double from=section.optDouble("from"),to=section.optDouble("to");
     if(!Double.isFinite(from)||!Double.isFinite(to)||from<0||to>total||to<=from||section.optString("surface").trim().isEmpty())continue;
     boundaries.add(from);boundaries.add(to);
     double[] mid=model.station((from+to)/2);
     if(mid!=null){pen.setColor(0xff555555);pen.setTextSize(dp(11));pen.setTextAlign(android.graphics.Paint.Align.CENTER);
      c.drawText(String.format(java.util.Locale.GERMANY,"%.2f m",to-from),(float)(ox+mid[0]*scale),(float)(oy-mid[1]*scale-dp(23)),pen);}
    }
    int labelIndex=0;
    for(double station:boundaries){
     double[] q=model.station(station);if(q==null)continue;
     float x=(float)(ox+q[0]*scale),y=(float)(oy-q[1]*scale);
     pen.setColor(0xff555555);pen.setStrokeWidth(dp(1));c.drawCircle(x,y,dp(3),pen);
     float labelY=y+dp(28+14*(labelIndex++%2));c.drawLine(x,y+dp(4),x,labelY-dp(12),pen);
     pen.setTextSize(dp(10));pen.setTextAlign(android.graphics.Paint.Align.CENTER);
     c.drawText(String.format(java.util.Locale.GERMANY,"St. %.2f",station),x,labelY,pen);
    }
    pen.setTextAlign(android.graphics.Paint.Align.LEFT);
    utilityWarningIntervals.clear();
    java.util.TreeSet<Double> warningCuts=new java.util.TreeSet<>();
    double warningTotal=route.measuredLength(),warningStation=0;
    warningCuts.add(0.0);
    for(int k=1;k<route.vertices.size();k++){warningStation+=route.vertices.get(k).metres;warningCuts.add(warningStation);}
    for(int k=0;k<widthSections.length();k++){org.json.JSONObject section=widthSections.optJSONObject(k);if(section!=null)for(String key:new String[]{"from","to"}){double value=section.optDouble(key);if(value>0&&value<warningTotal)warningCuts.add(value);}}
    java.util.ArrayList<double[]> warningDepths=new java.util.ArrayList<>();
    double warningDefaultDepth=d;
    try{
     String raw=getPreferences(0).getString("metricMainExcavationDepth","").trim();
     if(!raw.isEmpty()){warningDefaultDepth=Double.parseDouble(raw.replace(',','.'));if(!Double.isFinite(warningDefaultDepth)||warningDefaultDepth<=0)throw new IllegalArgumentException();}
     for(String line:getPreferences(0).getString("metricDepthSections","").split("\\n")){
      if(line.trim().isEmpty())continue;
      String[] parts=line.trim().split(";",-1);if(parts.length!=3)throw new IllegalArgumentException();
      double[] v=new double[3];for(int k=0;k<3;k++){v[k]=Double.parseDouble(parts[k].trim().replace(',','.'));if(!Double.isFinite(v[k]))throw new IllegalArgumentException();}
      if(v[0]<0||v[1]<=v[0]||v[1]>warningTotal+1e-8||v[2]<=0)throw new IllegalArgumentException();
      for(double[] old:warningDepths)if(v[0]<old[1]&&v[1]>old[0])throw new IllegalArgumentException();
      warningDepths.add(v);
     }
    }catch(Exception ex){warningDepths.clear();warningDefaultDepth=Double.NaN;pen.setColor(0xffb3261e);pen.setTextSize(dp(11));c.drawText("Tiefenabschnitte ungültig",dp(12),dp(16),pen);}
    for(double[] section:warningDepths){warningCuts.add(section[0]);warningCuts.add(Math.min(warningTotal,section[1]));}
    org.json.JSONArray warningUtilities=extraUtilities();
    for(int k=0;k<warningUtilities.length();k++){org.json.JSONObject u=warningUtilities.optJSONObject(k);if(u!=null)for(String key:new String[]{"routeFrom","routeTo"}){double value=u.optDouble(key);if(value>0&&value<warningTotal)warningCuts.add(value);}}
    Double warningPrevious=null;
    for(double stop:warningCuts){
     if(warningPrevious==null){warningPrevious=stop;continue;}
     double begin=warningPrevious;warningPrevious=stop;double mid=(begin+stop)/2;
     double warningDepth=warningDefaultDepth;for(double[] section:warningDepths)if(mid>=section[0]&&mid<section[1]){warningDepth=section[2];break;}
     boolean invalid=false;
     for(int k=0;k<warningUtilities.length();k++){org.json.JSONObject u=warningUtilities.optJSONObject(k);if(u!=null&&mid>=u.optDouble("routeFrom",0)&&mid<u.optDouble("routeTo",Double.POSITIVE_INFINITY)&&utilityOutsideProfile(u,widthAt(mid),warningDepth)){invalid=true;break;}}
     if(invalid){utilityWarningIntervals.add(new double[]{begin,stop});double[] u=model.station(begin),v=model.station(stop);if(u!=null&&v!=null){pen.setColor(0xffb3261e);pen.setStrokeWidth(dp(4));c.drawLine((float)(ox+u[0]*scale),(float)(oy-u[1]*scale),(float)(ox+v[0]*scale),(float)(oy-v[1]*scale),pen);}}
    }
    pen.setColor(0xffe6a23c);
    for(double[] p:montagePoints)c.drawCircle((float)(ox+p[0]*scale),(float)(oy-p[1]*scale),dp(5),pen);
    if(previewActive&&downHit>=0&&downHit<branchHits.size()){
     String id=branchHitIds.get(downHit);int index=(int)branchHits.get(downHit)[2];
     pen.setColor(0xffe6a23c);pen.setStrokeWidth(dp(2));pen.setTextSize(dp(12));
     for(int j=0;j<branchHits.size();j++){
      float[] p=branchHits.get(j);
      if(!id.equals(branchHitIds.get(j))||Math.abs(p[2]-index)!=1)continue;
      c.drawLine(p[0],p[1],previewX,previewY,pen);
      c.drawText(String.format(java.util.Locale.GERMANY,"%.2f m",Math.hypot(previewX-p[0],previewY-p[1])/scale),(p[0]+previewX)/2,(p[1]+previewY)/2-dp(8),pen);
     }
     c.drawCircle(previewX,previewY,dp(6),pen);
    }
    double bar=Math.pow(10,Math.floor(Math.log10((getWidth()/4.0)/scale)));
    if(Double.isFinite(bar)&&bar>0){float end=dp(12)+(float)(bar*scale);pen.setColor(0xff777777);pen.setStrokeWidth(dp(2));c.drawLine(dp(12),getHeight()-dp(12),end,getHeight()-dp(12),pen);pen.setTextSize(dp(11));c.drawText(String.format(java.util.Locale.GERMANY,"%.3g m",bar),dp(12),getHeight()-dp(18),pen);}
   }
  };panel.addView(drawing,new LinearLayout.LayoutParams(-1,dp(300)));
  LinearLayout legendPanel=new LinearLayout(this);legendPanel.setOrientation(1);panel.addView(legendPanel);
  Runnable refreshLegend=()->{
   legendPanel.removeAllViews();
   org.json.JSONObject settings=route.legendSettings();
   java.util.LinkedHashSet<String> names=new java.util.LinkedHashSet<>();
   for(int j=0;j<surfaces.length();j++){org.json.JSONObject o=surfaces.optJSONObject(j);if(o==null)continue;double from=o.optDouble("from"),to=o.optDouble("to");String name=o.optString("surface").trim();if(Double.isFinite(from)&&Double.isFinite(to)&&from>=0&&to<=route.measuredLength()&&to>from&&!name.isEmpty()&&Double.isFinite(widths[0]))names.add(name);}
   try{for(RouteBranch branch:branchStore.load())if(!branch.surface.trim().isEmpty())names.add(branch.surface);}catch(Exception ex){TextView warning=new TextView(this);warning.setText("Abzweiglegende konnte nicht vollständig geladen werden");legendPanel.addView(warning);}
   java.util.ArrayList<String> ordered=new java.util.ArrayList<>(names);
   ordered.sort((u,v)->Integer.compare(settings.optJSONObject(u)==null?1000:settings.optJSONObject(u).optInt("order",1000),settings.optJSONObject(v)==null?1000:settings.optJSONObject(v).optInt("order",1000)));
   if(!ordered.isEmpty()){TextView heading=new TextView(this);heading.setText("Oberflächenlegende");legendPanel.addView(heading);}
   for(String name:ordered){org.json.JSONObject item=settings.optJSONObject(name);if(item!=null&&!item.optBoolean("visible",true))continue;
    LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
    android.view.View swatch=new android.view.View(this);swatch.setBackgroundColor(route.configuredSurfaceColor(name,settings));row.addView(swatch,new LinearLayout.LayoutParams(dp(20),dp(14)));
    TextView label=new TextView(this);label.setText("  "+(item==null?name:item.optString("name",name)));row.addView(label);legendPanel.addView(row);
   }
   drawing.invalidate();
  };
  branchChanged[0]=()->{refreshLegend.run();refreshNominalArea.run();};
  refreshLegend.run();
  Button addWidth=new Button(this);addWidth.setText("Grabenbreite abschnittsweise");panel.addView(addWidth);
  addWidth.setOnClickListener(v->{
   LinearLayout fields=new LinearLayout(this);fields.setOrientation(1);
   EditText[] inputs=new EditText[5];String[] hints={"Station von (m)","Station bis (m)","Grabenbreite (m)","Nachschnitt links (m, optional)","Nachschnitt rechts (m, optional)"};
   for(int i=0;i<3;i++){inputs[i]=new EditText(this);inputs[i].setHint(hints[i]);inputs[i].setInputType(8194);fields.addView(inputs[i]);}
   android.app.AlertDialog edit=new android.app.AlertDialog.Builder(this).setTitle("Breitenabschnitt hinzufügen").setView(fields).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();
   edit.setOnShowListener(vv->edit.getButton(-1).setOnClickListener(vvv->{
    try{double[] values=new double[5];for(int i=0;i<3;i++)values[i]=Double.parseDouble(inputs[i].getText().toString().trim().replace(',','.'));
     for(int i=3;i<5;i++){String raw=inputs[i].getText().toString().trim();values[i]=raw.isEmpty()?Double.NaN:Double.parseDouble(raw.replace(',','.'));if(!Double.isNaN(values[i])&&(!Double.isFinite(values[i])||values[i]<0)){notice("Nachschnitt muss mindestens 0 m betragen.");return;}}
     double from=values[0],to=values[1],width=values[2];
     if(!Double.isFinite(from)||!Double.isFinite(to)||!Double.isFinite(width)||from<0||to<=from||to>route.measuredLength()||width<=0){notice("Ungültige Stationen oder Breite.");return;}
     for(int i=0;i<widthSections.length();i++){org.json.JSONObject o=widthSections.optJSONObject(i);if(o!=null&&from<o.optDouble("to")&&to>o.optDouble("from")){notice("Breitenabschnitte dürfen sich nicht überschneiden.");return;}}
     org.json.JSONArray next=new org.json.JSONArray(widthSections.toString());org.json.JSONObject entry=new org.json.JSONObject().put("from",from).put("to",to).put("width",width);
     if(Double.isFinite(values[3]))entry.put("left",values[3]);if(Double.isFinite(values[4]))entry.put("right",values[4]);next.put(entry);
     if(!getPreferences(0).edit().putString("metricWidthSections",next.toString()).commit()){notice("Speichern fehlgeschlagen.");return;}
     widthSections.put(next.getJSONObject(next.length()-1));refreshNominalArea.run();refreshGeometry.run();drawing.invalidate();edit.dismiss();notice("Breitenabschnitt gespeichert.");
    }catch(Exception e){notice("Bitte gültige Maße eingeben.");}
   }));edit.show();
  });
  Button listWidths=new Button(this);listWidths.setText("Breitenabschnitte verwalten");panel.addView(listWidths);
  listWidths.setOnClickListener(v->{
   String[] labels=new String[widthSections.length()];for(int i=0;i<labels.length;i++){org.json.JSONObject o=widthSections.optJSONObject(i);labels[i]=o==null?"Ungültig":String.format(java.util.Locale.GERMANY,"%.2f – %.2f m · B %.2f m · L %s · R %s",o.optDouble("from"),o.optDouble("to"),o.optDouble("width"),o.has("left")?String.format(java.util.Locale.GERMANY,"%.2f",o.optDouble("left")):"Standard",o.has("right")?String.format(java.util.Locale.GERMANY,"%.2f",o.optDouble("right")):"Standard");}
   if(labels.length==0){notice("Keine Breitenabschnitte.");return;}
   new android.app.AlertDialog.Builder(this).setTitle("Abschnitt zum Löschen wählen").setItems(labels,(d,i)->new android.app.AlertDialog.Builder(this).setMessage(labels[i]+" löschen?").setNegativeButton("Abbrechen",null).setPositiveButton("Löschen",(dd,ww)->{
    try{org.json.JSONArray next=new org.json.JSONArray(widthSections.toString());next.remove(i);if(getPreferences(0).edit().putString("metricWidthSections",next.toString()).commit()){widthSections.remove(i);refreshNominalArea.run();refreshGeometry.run();drawing.invalidate();}else notice("Speichern fehlgeschlagen.");}catch(Exception e){notice("Löschen fehlgeschlagen.");}
   }).show()).setNegativeButton("Schließen",null).show();
  });
  Button editWidth=new Button(this);editWidth.setText("Breitenabschnitt bearbeiten");panel.addView(editWidth);
  editWidth.setOnClickListener(v->{
   if(widthSections.length()==0){notice("Keine Breitenabschnitte.");return;}
   String[] names=new String[widthSections.length()];
   for(int i=0;i<names.length;i++){org.json.JSONObject o=widthSections.optJSONObject(i);names[i]=o==null?"Ungültig":String.format(java.util.Locale.GERMANY,"%.2f – %.2f m · B %.2f m",o.optDouble("from"),o.optDouble("to"),o.optDouble("width"));}
   new android.app.AlertDialog.Builder(this).setTitle("Abschnitt auswählen").setItems(names,(d,index)->{
    org.json.JSONObject original=widthSections.optJSONObject(index);if(original==null){notice("Ungültiger Abschnitt.");return;}
    LinearLayout fields=new LinearLayout(this);fields.setOrientation(1);
    EditText[] entries=new EditText[5];String[] hints={"Station von (m)","Station bis (m)","Grabenbreite (m)","Nachschnitt links (m, leer = Standard)","Nachschnitt rechts (m, leer = Standard)"};
    String[] keys={"from","to","width","left","right"};
    for(int k=0;k<entries.length;k++){entries[k]=new EditText(this);entries[k].setHint(hints[k]);entries[k].setInputType(8194);
     if(original.has(keys[k]))entries[k].setText(String.format(java.util.Locale.GERMANY,"%.3f",original.optDouble(keys[k])));
     fields.addView(entries[k]);
    }
    android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Breitenabschnitt bearbeiten").setView(fields).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();
    dialog.setOnShowListener(vv->dialog.getButton(-1).setOnClickListener(save->{
     try{
      double[] vals=new double[5];for(int k=0;k<5;k++){String raw=entries[k].getText().toString().trim();vals[k]=raw.isEmpty()&&k>=3?Double.NaN:Double.parseDouble(raw.replace(',','.'));}
      if(!Double.isFinite(vals[0])||!Double.isFinite(vals[1])||!Double.isFinite(vals[2])||vals[0]<0||vals[1]<=vals[0]||vals[1]>route.measuredLength()||vals[2]<=0){notice("Ungültige Stationen oder Grabenbreite.");return;}
      for(int k=3;k<5;k++)if(!Double.isNaN(vals[k])&&(!Double.isFinite(vals[k])||vals[k]<0)){notice("Ungültiger Nachschnitt.");return;}
      for(int k=0;k<widthSections.length();k++){if(k==index)continue;org.json.JSONObject o=widthSections.optJSONObject(k);if(o!=null&&vals[0]<o.optDouble("to")&&vals[1]>o.optDouble("from")){notice("Breitenabschnitte dürfen sich nicht überschneiden.");return;}}
      org.json.JSONObject updated=new org.json.JSONObject().put("from",vals[0]).put("to",vals[1]).put("width",vals[2]);
      if(Double.isFinite(vals[3]))updated.put("left",vals[3]);if(Double.isFinite(vals[4]))updated.put("right",vals[4]);
      org.json.JSONArray next=new org.json.JSONArray(widthSections.toString());next.put(index,updated);
      if(!getPreferences(0).edit().putString("metricWidthSections",next.toString()).commit()){notice("Speichern fehlgeschlagen.");return;}
      widthSections.put(index,updated);refreshNominalArea.run();refreshGeometry.run();drawing.invalidate();dialog.dismiss();notice("Breitenabschnitt aktualisiert; Geometrieprüfung erneuert.");
     }catch(Exception e){notice("Bitte gültige Maße eingeben.");}
    }));dialog.show();
   }).setNegativeButton("Schließen",null).show();
  });
  Button editLegend=new Button(this);editLegend.setText("Legende bearbeiten");panel.addView(editLegend);editLegend.setOnClickListener(v->route.editLegend());
  Button reloadLegend=new Button(this);reloadLegend.setText("Legende aktualisieren");panel.addView(reloadLegend);reloadLegend.setOnClickListener(v->refreshLegend.run());
  ScrollView scroll=new ScrollView(this);scroll.addView(panel);
  new android.app.AlertDialog.Builder(this).setTitle("Tiefbau – Maßstäbliche Vorschau").setView(scroll).setPositiveButton("Schließen",null).show();
 }
 void showTools(){
  String[] names={"Maße","Beläge","Objekte","Unterlagen / PDF","Sprachassistent"};
  LinearLayout[] pages={measurePage,segmentPage,objectPage,documentsPage,voicePage};
  PopupMenu menu=new PopupMenu(this,toolsMenu);
  for(int i=0;i<names.length;i++)menu.getMenu().add(0,i,Menu.NONE,names[i]);
  menu.getMenu().add(0,100,Menu.NONE,"Projekte");
  menu.getMenu().add(0,101,Menu.NONE,"Projekt sichern");
  menu.getMenu().add(0,102,Menu.NONE,"Projekt wiederherstellen");
  menu.getMenu().add(0,103,Menu.NONE,"Projektverlauf");
  menu.getMenu().add(0,104,Menu.NONE,"Sicherungsversionen");
  menu.getMenu().add(0,105,Menu.NONE,"Längsschnitt");
  menu.getMenu().add(0,106,Menu.NONE,"DXF exportieren");
  menu.getMenu().add(0,107,Menu.NONE,"Mengen als CSV exportieren");
  menu.getMenu().add(0,108,Menu.NONE,"Leistungsverzeichnis");
  menu.getMenu().add(0,109,Menu.NONE,"DA11 exportieren (Test)");
  menu.getMenu().add(0,110,Menu.NONE,"GAEB X31 exportieren (Test)");
  menu.getMenu().add(0,111,Menu.NONE,"Fotobericht als PDF");
  menu.getMenu().add(0,112,Menu.NONE,"Bautagesbericht");
  menu.getMenu().add(0,113,Menu.NONE,"Nachtragsmanagement");
  menu.getMenu().add(0,114,Menu.NONE,"PDF-Unterlagen analysieren");
  menu.getMenu().add(0,115,Menu.NONE,"Foto / Handskizze analysieren");
  menu.getMenu().add(0,116,Menu.NONE,"Zeichnungsunterlage – PDF / Foto");
  menu.getMenu().add(0,117,Menu.NONE,"Bluetooth-Messgerät (Test)");
  menu.getMenu().add(0,118,Menu.NONE,"Rohrleitungsbau – Montage");
  menu.getMenu().add(0,119,Menu.NONE,"Tiefbau – Maßstäbliche Vorschau");
  menu.getMenu().add(0,120,Menu.NONE,"Schachtscheine");
  menu.setOnMenuItemClickListener(item->{if(item.getItemId()==120)showUtilitySheets();else if(item.getItemId()==119)showMetricRoute();else if(item.getItemId()==118)showMontage();else if(item.getItemId()==117)testBleMeasure();else if(item.getItemId()==116)chooseRouteUnderlay();else if(item.getItemId()==115)chooseImageAnalysis();else if(item.getItemId()==114)choosePdfAnalysis();else if(item.getItemId()==113)showNachtraege();else if(item.getItemId()==112)showDailyReports();else if(item.getItemId()==111)exportPhotoReport();else if(item.getItemId()==110)exportX31();else if(item.getItemId()==109)exportReb();else if(item.getItemId()==108)showLv();else if(item.getItemId()==107)exportCsv();else if(item.getItemId()==106)exportDxf();else if(item.getItemId()==105)showLongitudinalProfile();else if(item.getItemId()==104)showAutomaticBackups();else if(item.getItemId()==103)showProjectHistory();else if(item.getItemId()==102)requestProjectRestore();else if(item.getItemId()==101)requestProjectBackup();else if(item.getItemId()==100)showProjects();else showPage(pages[item.getItemId()]);return true;});
  menu.show();
 }
 LinearLayout page(){LinearLayout p=new LinearLayout(this);p.setOrientation(1);p.setBackgroundColor(Color.BLACK);p.setPadding(dp(12),dp(8),dp(12),dp(8));return p;}
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 void showPage(LinearLayout p){
  for(java.util.Map.Entry<LinearLayout,Button> entry:sheetTabs.entrySet()){
   boolean selected=entry.getKey()==p;
   Button tab=entry.getValue();
   tab.setSelected(selected);
   tab.setBackground(editorShape(selected?0xff28564c:0xff202827,10));
   tab.setTextColor(selected?Color.WHITE:0xffb5b5b5);
  }
  if(p==profilePage&&profile!=null)profile.invalidate();if(p==documentsPage)compareSoll();if(p==quantityPage)refreshDetailQuantities();body.removeAllViews();ScrollView sc=new ScrollView(this);sc.setFillViewport(true);if(p.getParent()!=null)((android.view.ViewGroup)p.getParent()).removeView(p);sc.addView(p);body.addView(sc,new LinearLayout.LayoutParams(-1,-1));}
 private void fitSystemPanels(LinearLayout root){
  Window window=getWindow();
  androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false);
  window.setStatusBarColor(Color.TRANSPARENT);
  window.setNavigationBarColor(Color.TRANSPARENT);
  if(android.os.Build.VERSION.SDK_INT>=29)window.setNavigationBarContrastEnforced(false);
  window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
  androidx.core.view.WindowInsetsControllerCompat controller=
    androidx.core.view.WindowCompat.getInsetsController(window,root);
  controller.setAppearanceLightStatusBars(false);
  controller.setAppearanceLightNavigationBars(false);
  final int baseLeft=root.getPaddingLeft(),baseTop=root.getPaddingTop(),baseRight=root.getPaddingRight(),baseBottom=root.getPaddingBottom();
  androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root,(view,insets)->{
   int types=androidx.core.view.WindowInsetsCompat.Type.systemBars()
      |androidx.core.view.WindowInsetsCompat.Type.displayCutout()
      |androidx.core.view.WindowInsetsCompat.Type.ime();
   androidx.core.graphics.Insets safe=insets.getInsets(types);
   view.setPadding(baseLeft+safe.left,baseTop+safe.top,baseRight+safe.right,baseBottom+safe.bottom);
   return insets;
  });
  androidx.core.view.ViewCompat.requestApplyInsets(root);
 }

 public void onCreate(Bundle savedInstanceState){super.onCreate(savedInstanceState);activeProject=projectRegistry().getString("active",LEGACY_PROJECT);
 if(savedInstanceState!=null){pendingPdfProject=savedInstanceState.getString("pdfProject");pendingPdfLayers=savedInstanceState.getInt("pdfLayers",-1);String pdfScope=savedInstanceState.getString("pdfScope");if(pdfScope!=null)pendingPdfScope=PdfScope.valueOf(pdfScope);projectChosen=savedInstanceState.getBoolean("projectChosen",false);selectedPoint=savedInstanceState.getString("selectedPoint");pendingPoint=savedInstanceState.getString("pendingPoint");pendingPhoto=savedInstanceState.getString("pendingPhoto");pendingMaterialProject=savedInstanceState.getString("pendingMaterialProject");pendingConnectionsProject=savedInstanceState.getString("pendingConnectionsProject");pendingPlanProject=savedInstanceState.getString("pendingPlanProject");pendingProtocolsProject=savedInstanceState.getString("pendingProtocolsProject");}
 shell=page();shell.setPadding(0,0,0,0);shell.setBackgroundColor(Color.BLACK);setContentView(shell);fitSystemPanels(shell);
 planPage=page();measurePage=page();voicePage=page();profilePage=page();segmentPage=page();objectPage=page();quantityPage=page();
 LinearLayout header=new LinearLayout(this);
 header.setGravity(Gravity.CENTER_VERTICAL);
 header.setPadding(dp(16),dp(4),dp(8),dp(4));
 header.setBackgroundColor(Color.BLACK);
 shell.addView(header);
 projectHeader=sheetText(getPreferences(0).getString("project","Feldaufmaß Pro"),22,true);
 projectHeader.setTextColor(UI_TEXT);
 projectHeader.setMaxLines(2);
 projectHeader.setEllipsize(android.text.TextUtils.TruncateAt.END);
 header.addView(projectHeader,new LinearLayout.LayoutParams(0,-2,1));
 toolsMenu=new Button(this);
 toolsMenu.setAllCaps(false);
 toolsMenu.setText("Werkzeuge");styleEditorButton(toolsMenu,false);compactToolIcon(toolsMenu,"Werkzeuge","menu");
 header.addView(toolsMenu);
 toolsMenu.setOnClickListener(v->showTools());
 LinearLayout tabs=new LinearLayout(this);shell.addView(tabs);
 addSheetTab(tabs,"Trasse",planPage);
 addSheetTab(tabs,"Regelprofil",profilePage);
 addSheetTab(tabs,"Mengen",quantityPage);
 body=new LinearLayout(this);shell.addView(body,new LinearLayout.LayoutParams(-1,0,1));
 LinearLayout dock=new LinearLayout(this);dock.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
 dock.setBackgroundColor(Color.BLACK);dock.setPadding(dp(8),dp(4),dp(8),dp(4));shell.addView(dock);
 ImageButton assistant=new ImageButton(this);assistant.setImageResource(android.R.drawable.ic_btn_speak_now);assistant.setBackground(editorShape(0xff202827,12));assistant.setColorFilter(Color.WHITE);
 assistant.setContentDescription("Sprachassistent starten; lange drücken für Assistent");
 header.addView(assistant,header.indexOfChild(toolsMenu),new LinearLayout.LayoutParams(dp(48),dp(48)));
 assistant.setOnClickListener(v->startDictation());
 assistant.setOnLongClickListener(v->{showPage(voicePage);return true;});
 Button cloudButton=actionButtonSingle(dock,"Cloud",()->openCloud());compactToolIcon(cloudButton,"Cloud","cloud");android.graphics.drawable.Drawable cloudIcon=cloudButton.getCompoundDrawables()[1];cloudButton.setCompoundDrawables(cloudIcon,null,null,null);cloudButton.setCompoundDrawablePadding(dp(6));cloudButton.setMinHeight(dp(48));LinearLayout.LayoutParams cloudParams=new LinearLayout.LayoutParams(dp(96),dp(48));cloudParams.setMargins(0,0,dp(8),0);cloudButton.setLayoutParams(cloudParams);
 Button persistentSave=actionButtonSingle(dock,"Speichern",()->{if(calculate())Toast.makeText(this,"Gespeichert",Toast.LENGTH_SHORT).show();});
 compactToolIcon(persistentSave,"Speichern","save");
 android.graphics.drawable.Drawable saveIcon=persistentSave.getCompoundDrawables()[1];
 persistentSave.setCompoundDrawables(saveIcon,null,null,null);persistentSave.setCompoundDrawablePadding(dp(6));persistentSave.setMinHeight(dp(48));
 persistentSave.setLayoutParams(new LinearLayout.LayoutParams(dp(124),dp(48)));
 overviewAction(planPage,"Zeichnungsebenen","layers",()->showDrawingLayers());
 overviewAction(planPage,"GPS-Aufzeichnung","center",()->showGpsTrackControls());
 overviewAction(planPage,"Trassenvergleich","sections",()->showGpsTrackComparison());
 overviewAction(planPage,"Zeichnungsunterlage – PDF / Foto","image",()->chooseRouteUnderlay());
 overviewAction(planPage,"Schachtscheine","map",()->showUtilitySheets());
 routePreviewBox=page();planPage.addView(routePreviewBox);
 routeSummary=sheetText("",15,false);planPage.addView(routeSummary);
 LinearLayout actions=new LinearLayout(this);planPage.addView(actions);
 actionButton(actions,"Maß eintragen",()->editRoute(true));actionButton(actions,"Trasse zeichnen",()->editRoute(false));
 actionButton(planPage,"Vollbild zeichnen",()->editRoute(false,true));
 refreshRoute();
 addOverview();
 sheetHeading(planPage,"Lineare Stationierung");
 sheetHeading(quantityPage,"Mengenermittlung");
 sheetHeading(measurePage,"Trassenmaße");
 documentsPage=page();TextView dh=new TextView(this);dh.setText("Ausführungsunterlagen – Soll");dh.setTextSize(22);documentsPage.addView(dh);
 Button importDoc=new Button(this);importDoc.setText("Unterlagen hinzufügen");documentsPage.addView(importDoc);importDoc.setOnClickListener(v->{android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(android.content.Intent.CATEGORY_OPENABLE);i.putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION|android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,411);});
 TextView cloudInfo=new TextView(this);cloudInfo.setText("Your Secure Cloud: In der offiziellen App anmelden. Im Android-Dateidialog SecureCloud auswählen, sofern verfügbar. Keine Zugangsdaten in Feldaufmaß Pro erforderlich.");documentsPage.addView(cloudInfo);
 Button pdfExport=new Button(this);pdfExport.setText("Feldaufmaß als PDF speichern");documentsPage.addView(pdfExport);pdfExport.setOnClickListener(v->exportPdf());
 Button cloudImport=new Button(this);cloudImport.setText("Aus SecureCloud importieren");documentsPage.addView(cloudImport);cloudImport.setOnClickListener(v->importDoc.performClick());
 documentsList=new TextView(this);documentsPage.addView(documentsList);try{documents=new org.json.JSONArray(getPreferences(0).getString("documents","[]"));}catch(Exception ignored){}showDocuments();
 TextView info=new TextView(this);info.setText("Unterlagen sind Planungsdaten (Soll). Automatische KI-Auswertung noch nicht verfügbar. Ist-Maße bleiben unverändert.");documentsPage.addView(info);
 Button planAssistant=new Button(this);planAssistant.setText("KI-Planassistent – Unterlagen prüfen");documentsPage.addView(planAssistant);planAssistant.setOnClickListener(v->choosePlanDocument());
 Button planText=new Button(this);planText.setText("Soll-Leitungen aus Text erkennen");documentsPage.addView(planText);planText.setOnClickListener(v->openPlanTextAnalysis());
 Button savedSoll=new Button(this);savedSoll.setText("Gespeicherte Soll-Leitungen");documentsPage.addView(savedSoll);savedSoll.setOnClickListener(v->showSavedSollUtilities());
 TextView sh=new TextView(this);sh.setText("Soll-Maße aus Ausführungsunterlagen");sh.setTextSize(20);documentsPage.addView(sh);
 sollLength=field(documentsPage,"Trassenlänge Soll (m)","sollLength",true);
 sollWidth=field(documentsPage,"Grabenbreite Soll (m)","sollWidth",true);
 sollDepth=field(documentsPage,"Grabentiefe Soll (m)","sollDepth",true);
 sollAsphalt=field(documentsPage,"Asphaltstärke Soll (m)","sollAsphalt",true);
 sollSource=field(documentsPage,"Quelle / Planstand / Seite","sollSource",false);
 Button saveSoll=new Button(this);saveSoll.setText("Soll-Daten speichern und vergleichen");documentsPage.addView(saveSoll);saveSoll.setOnClickListener(v->saveSoll());
 sollComparison=new TextView(this);documentsPage.addView(sollComparison);
 LinearLayout root=voicePage;
 TextView title=new TextView(this);title.setText("Feldaufmaß Pro");title.setTextSize(28);root.addView(title);
 TextView voiceHeading=new TextView(this);voiceHeading.setText("Sprachassistent – Diktat");voiceHeading.setTextSize(20);root.addView(voiceHeading);
 voiceLanguage=new Spinner(this);voiceLanguage.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Deutsch","Russisch"}));root.addView(voiceLanguage);voiceLanguage.setSelection(getPreferences(0).getInt("dictationLanguage",1));onChoice(voiceLanguage,index->getPreferences(0).edit().putInt("dictationLanguage",index).apply());
 Button mic=new Button(this);mic.setText("Spracheingabe starten");root.addView(mic);
 voiceText=new EditText(this);voiceText.setHint("Diktat prüfen oder bearbeiten");root.addView(voiceText);
 actionButtonSingle(root,"Welche Angaben fehlen?",()->showMissingAssistantData());
 actionButtonSingle(root,"Text senden",()->{String message=voiceText.getText().toString().trim();if(!message.isEmpty())handleSpeech(message);});
 Button applyVoice=new Button(this);applyVoice.setText("Maße aus Diktat übernehmen");root.addView(applyVoice);
 mic.setOnClickListener(v->startDictation());applyVoice.setOnClickListener(v->applyDictation());
 actionButtonSingle(root,"Als Objektbemerkung verwenden",()->{
  String note=voiceText.getText().toString().trim();
  if(note.isEmpty())return;
  objectNote.append((objectNote.length()==0?"":"\n")+note);showPage(objectPage);
 });
 root=measurePage;
 project=new EditText(this);project.setText(getPreferences(0).getString("project",""));
 length=field(root,"Trassenlänge (m)","length",true);
 Button adopt=new Button(this);adopt.setText("Bemaßte Skizzenlänge übernehmen");root.addView(adopt);
 adopt.setOnClickListener(v->{
  double total=new RouteSketch(this,routeData()).measuredLength();
  if(Double.isFinite(total)&&total>0){length.setText(java.math.BigDecimal.valueOf(total).stripTrailingZeros().toPlainString());notice("Skizzenlänge übernommen. Maße prüfen und speichern.");}
  else Toast.makeText(this,"Zuerst alle Skizzenabschnitte bemaßen.",Toast.LENGTH_LONG).show();
 });
 width=field(root,"Grabenbreite (m)","width",true);
 depth=field(root,"Grabentiefe (m)","depth",true);
 TextView surfaceLabel=new TextView(this);surfaceLabel.setText("Oberfläche");root.addView(surfaceLabel);
 surface=new Spinner(this);String[] types={"Unbekannt","Asphalt","Pflaster","Gehwegplatten","Unbefestigt","Betonpflaster – Rechteck","Betonpflaster – Quadrat","Doppel-T-Pflaster","Wellenverbundpflaster","Sechseckpflaster","Achteckpflaster","Verbundpflaster – sonstige","Betonpflaster – Mehrformat","Ökopflaster / Sickerpflaster","Rasengittersteine","Rasenfugenpflaster","Natursteinpflaster – Großpflaster","Natursteinpflaster – Kleinpflaster","Natursteinpflaster – Mosaikpflaster","Natursteinpflaster – sonstige","Klinkerpflaster","Ziegelpflaster","Beton-Gehwegplatten","Natursteinplatten","Waschbetonplatten","Taktilplatten – Rippen","Taktilplatten – Noppen","Rinnenpflaster","Schlackenpflaster","Holzpflaster","Sonstiges","Beton","Betonpflaster","Großpflaster","Kleinpflaster","Gehwegplatten 1 × 1 m","Verbundpflaster","Schotter","Kies","Sand","Unbefestigte Fläche","Rasen","Grünfläche","Wassergebundene Decke","Sonstige Oberfläche","Pflastersteine","Großsteinpflaster","Kleinsteinpflaster"};
 java.util.ArrayList<String> surfaceTypes=new java.util.ArrayList<>(java.util.Arrays.asList(types));
 android.content.SharedPreferences catalog=getSharedPreferences("surface_catalog",MODE_PRIVATE);
 try{org.json.JSONArray custom=new org.json.JSONArray(catalog.getString("custom","[]"));for(int i=0;i<custom.length();i++){String name=custom.optString(i).trim();if(!name.isEmpty()&&!surfaceTypes.contains(name))surfaceTypes.add(name);}}catch(Exception ignored){}
 ArrayAdapter<String> surfaceAdapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,surfaceTypes);
 surface.setAdapter(surfaceAdapter);root.addView(surface);
 surface.setSelection(Math.min(surfaceTypes.size()-1,Math.max(0,getPreferences(0).getInt("surface",0))));
 Button addSurface=new Button(this);styleEditorButton(addSurface,false);addSurface.setTextSize(14);addSurface.setText("Eigene Oberfläche hinzufügen");root.addView(addSurface);
 addSurface.setOnClickListener(v->{
  EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Bezeichnung der Oberfläche");input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Eigene Oberfläche hinzufügen").setView(input).setNegativeButton("Abbrechen",null).setPositiveButton("Hinzufügen",null).create();
  dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(y->{
   String name=input.getText().toString().trim();if(name.isEmpty()){input.setError("Bezeichnung eingeben");return;}
   for(int i=0;i<surfaceTypes.size();i++)if(surfaceTypes.get(i).equalsIgnoreCase(name)){surface.setSelection(i);dialog.dismiss();return;}
   org.json.JSONArray custom=new org.json.JSONArray();for(int i=types.length;i<surfaceTypes.size();i++)custom.put(surfaceTypes.get(i));custom.put(name);
   if(!catalog.edit().putString("custom",custom.toString()).commit()){input.setError("Speichern fehlgeschlagen");return;}
   surfaceAdapter.add(name);surfaceAdapter.notifyDataSetChanged();surface.setSelection(surfaceTypes.size()-1);dialog.dismiss();
  }));dialog.show();
 });
 belagsToggle=new Button(this);
 styleEditorButton(belagsToggle,false);belagsToggle.setTextSize(14);belagsToggle.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
 root.addView(belagsToggle);
 belagsFields=new LinearLayout(this);
 belagsFields.setOrientation(LinearLayout.VERTICAL);
 root.addView(belagsFields);
 setBelagsExpanded(false);
 belagsToggle.setOnClickListener(v->setBelagsExpanded(belagsFields.getVisibility()!=View.VISIBLE));
 stoneLength=field(belagsFields,"Steinlänge (cm, optional)","stoneLength",true);
 stoneWidth=field(belagsFields,"Steinbreite (cm, optional)","stoneWidth",true);
 stoneDepth=field(belagsFields,"Belagsstärke (cm, optional)","stoneDepth",true);
 stoneColor=field(belagsFields,"Farbe (optional)","stoneColor",false);
 pattern=field(belagsFields,"Verlegemuster (optional)","pattern",false);
 bedding=field(belagsFields,"Bettung – Material / Stärke (optional)","bedding",false);
 LinearLayout optionalCatalog=fold(root,"Zusätzliche Leistungen / Katalog",false);
 LinearLayout oldBelag=fold(optionalCatalog,"Altbelag und Wiederherstellung",false);
 underlying=field(oldBelag,"Unterliegender Altbelag (z. B. Großpflaster)","underlying",false);
 TextView handlingLabel=new TextView(this);handlingLabel.setText("Umgang mit Altbelag");oldBelag.addView(handlingLabel);
 handling=new Spinner(this);String[] options={"Nicht erfasst","Aufnahme","Aufnahme und Entsorgung","Aufnahme und Wiederverwendung","Wiederherstellung"};
 handling.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,options));oldBelag.addView(handling);handling.setSelection(Math.min(4,Math.max(0,getPreferences(0).getInt("handling",0))));
 baseInput=field(fold(optionalCatalog,"Tragschicht",false),"Tragschichtstärke (m, optional)","base",true);
 LinearLayout utilitiesFields=fold(optionalCatalog,"Leitungen / Kabel",false);
 pipeInput=field(utilitiesFields,"Rohr-Außendurchmesser (mm, optional)","pipe",true);
 Button utilitiesButton=new Button(this);utilitiesButton.setText("Weitere Leitungen / Kabel");utilitiesButton.setAllCaps(false);utilitiesFields.addView(utilitiesButton);utilitiesButton.setOnClickListener(v->editUtilities());
 asphaltFields=page();root.addView(asphaltFields,root.indexOfChild(belagsToggle));
 asphaltInput=field(asphaltFields,"Asphaltstärke (m, optional)","asphalt",true);
 LinearLayout cutFields=fold(optionalCatalog,"Nachschnitt",false);
 leftInput=field(cutFields,"Nachschnitt links (m, optional)","left",true);
 rightInput=field(cutFields,"Nachschnitt rechts (m, optional)","right",true);
 Button save=new Button(this);save.setText("Berechnen und speichern");save.setVisibility(View.GONE);root.addView(save);
 Runnable surfaceVisibility=()->{String name=String.valueOf(surface.getSelectedItem());boolean stone=isStoneSurface(name);belagsToggle.setVisibility(stone?View.VISIBLE:View.GONE);setBelagsExpanded(stone);asphaltFields.setVisibility("Asphalt".equals(name)?View.VISIBLE:View.GONE);};
 onChoice(surface,i->surfaceVisibility.run());surfaceVisibility.run();
 root=quantityPage;
 result=new TextView(this);result.setTextSize(18);root.addView(result);shoringQuantities=new TextView(this);root.addView(shoringQuantities);detailQuantities=new TextView(this);root.addView(detailQuantities);
 root=planPage;
 sketch=new Sketch();sketch.setBackgroundColor(Color.BLACK);root.addView(sketch,new LinearLayout.LayoutParams(-1,dp(280))); LinearLayout historyBar=new LinearLayout(this);root.addView(historyBar);undoObjectsButton=new Button(this);undoObjectsButton.setText("Rückgängig");historyBar.addView(undoObjectsButton,new LinearLayout.LayoutParams(0,-2,1));redoObjectsButton=new Button(this);redoObjectsButton.setText("Wiederholen");historyBar.addView(redoObjectsButton,new LinearLayout.LayoutParams(0,-2,1));undoObjectsButton.setOnClickListener(v->restoreObjectHistory(false));redoObjectsButton.setOnClickListener(v->restoreObjectHistory(true));updateHistoryButtons();
 Button edit=new Button(this);edit.setText("Trassenmaße bearbeiten");root.addView(edit);edit.setOnClickListener(v->showPage(measurePage));
 LinearLayout serviceCatalog=fold(planPage,"Zusätzliche Leistungen / Katalog",false);
 actionButtonSingle(serviceCatalog,"Trassenobjekte – Suchschachtung, Baugruben, Hindernisse",()->showPage(objectPage));
 actionButtonSingle(serviceCatalog,"Oberflächenabschnitte",()->showPage(segmentPage));
 actionButtonSingle(serviceCatalog,"Leitungen / Kabel",()->editUtilities());
 LinearLayout earthCatalog=fold(serviceCatalog,"Erdarbeiten und Verbau",false);
 LinearLayout surfaceWorkCatalog=fold(serviceCatalog,"Oberflächenarbeiten",false);
 LinearLayout sectionCatalog=fold(serviceCatalog,"Abschnittsprofile und Mengen",false);
 actionButtonSingle(earthCatalog,"Verbauflächen berechnen",()->{
  LinearLayout fields=new LinearLayout(this);fields.setOrientation(1);
  String[] hints={"Station von (m)","Station bis (m)","Verbauhöhe links (m; 0 = kein Verbau)","Verbauhöhe rechts (m; 0 = kein Verbau)"};
  EditText[] inputs=new EditText[4];for(int i=0;i<4;i++){inputs[i]=new EditText(this);inputs[i].setHint(hints[i]);inputs[i].setInputType(8194);fields.addView(inputs[i]);}
  TextView resultText=new TextView(this);fields.addView(resultText);
  java.util.ArrayList<double[]> entries=new java.util.ArrayList<>();
  Runnable refreshVerbau=()->{
   StringBuilder report=new StringBuilder();double left=0,right=0;
   for(double[] e:entries){double a=(e[1]-e[0])*e[2],b=(e[1]-e[0])*e[3];left+=a;right+=b;report.append(String.format(java.util.Locale.GERMANY,"%.2f – %.2f m: L %.2f / R %.2f m²\n",e[0],e[1],a,b));}
   report.append(String.format(java.util.Locale.GERMANY,"\nLinks: %.2f m²\nRechts: %.2f m²\nGesamt: %.2f m²",left,right,left+right));resultText.setText(report.toString());
  };
  Runnable saveVerbau=()->{org.json.JSONArray data=new org.json.JSONArray();for(double[] e:entries){org.json.JSONArray row=new org.json.JSONArray();for(double v:e)try{row.put(v);}catch(Exception ex){throw new IllegalArgumentException(ex);}data.put(row);}getPreferences(0).edit().putString("metricVerbauSections",data.toString()).apply();refreshVerbau.run();if(sketch!=null)sketch.invalidate();refreshMainQuantities();if(livePreview!=null)livePreview.invalidate();if(profile!=null)profile.invalidate();};
  try{org.json.JSONArray data=new org.json.JSONArray(getPreferences(0).getString("metricVerbauSections","[]"));for(int i=0;i<data.length();i++){org.json.JSONArray row=data.getJSONArray(i);entries.add(new double[]{row.getDouble(0),row.getDouble(1),row.getDouble(2),row.getDouble(3)});}}catch(Exception ex){notice("Verbaudaten konnten nicht geladen werden.");return;}
  refreshVerbau.run();
  Button remove=new Button(this);remove.setText("Einzelnen Abschnitt löschen");fields.addView(remove);
  remove.setOnClickListener(button->{
   if(entries.isEmpty()){notice("Keine Verbauabschnitte vorhanden.");return;}
   String[] labels=new String[entries.size()];for(int i=0;i<entries.size();i++){double[] e=entries.get(i);labels[i]=String.format(java.util.Locale.GERMANY,"%.2f – %.2f m | L %.2f / R %.2f m",e[0],e[1],e[2],e[3]);}
   new android.app.AlertDialog.Builder(this).setTitle("Verbauabschnitt auswählen").setItems(labels,(dialog,index)->new android.app.AlertDialog.Builder(this).setMessage(labels[index]+" löschen?").setNegativeButton("Abbrechen",null).setPositiveButton("Löschen",(confirm,which)->{entries.remove(index);saveVerbau.run();}).show()).show();
  });
  final int[] editing={-1};
  Button editSection=new Button(this);editSection.setText("Abschnitt bearbeiten");fields.addView(editSection);
  Button add=new Button(this);add.setText("Abschnitt hinzufügen");
  editSection.setOnClickListener(button->{
   if(entries.isEmpty()){notice("Keine Verbauabschnitte vorhanden.");return;}
   String[] labels=new String[entries.size()];for(int i=0;i<entries.size();i++){double[] e=entries.get(i);labels[i]=String.format(java.util.Locale.GERMANY,"%.2f – %.2f m | L %.2f / R %.2f m",e[0],e[1],e[2],e[3]);}
   new android.app.AlertDialog.Builder(this).setTitle("Abschnitt bearbeiten").setItems(labels,(dialog,index)->{editing[0]=index;for(int i=0;i<4;i++)inputs[i].setText(String.valueOf(entries.get(index)[i]));add.setText("Änderungen speichern");remove.setEnabled(false);}).show();
  });
  Button cancelEdit=new Button(this);cancelEdit.setText("Bearbeitung abbrechen");fields.addView(cancelEdit);cancelEdit.setOnClickListener(button->{editing[0]=-1;add.setText("Abschnitt hinzufügen");remove.setEnabled(true);for(EditText field:inputs)field.setText("");});
  fields.addView(add);
  add.setOnClickListener(button->{
   try{
    double[] v=new double[4];for(int i=0;i<4;i++){if(inputs[i].length()==0)throw new IllegalArgumentException("Alle Maße eingeben");v[i]=number(inputs[i]);if(!Double.isFinite(v[i])||v[i]<0)throw new IllegalArgumentException("Ungültiges Maß");}
    if(v[1]<=v[0]||(l>0&&v[1]>l)||v[2]+v[3]<=0)throw new IllegalArgumentException("Stationsgrenzen oder Verbauhöhe prüfen");
    for(double[] e:entries)if((editing[0]<0||e!=entries.get(editing[0]))&&v[0]<e[1]&&v[1]>e[0]&&((v[2]>0&&e[2]>0)||(v[3]>0&&e[3]>0)))throw new IllegalArgumentException("Überlappender Verbau auf derselben Seite");
    if(editing[0]>=0)entries.set(editing[0],v);else entries.add(v);editing[0]=-1;add.setText("Abschnitt hinzufügen");remove.setEnabled(true);for(EditText field:inputs)field.setText("");saveVerbau.run();
   }catch(Exception ex){notice(ex.getMessage());}
  });
  Button reset=new Button(this);reset.setText("Berechnung zurücksetzen");fields.addView(reset);reset.setOnClickListener(button->{new android.app.AlertDialog.Builder(this).setMessage("Alle Verbauabschnitte löschen?").setNegativeButton("Abbrechen",null).setPositiveButton("Löschen",(dialog,which)->{entries.clear();editing[0]=-1;add.setText("Abschnitt hinzufügen");remove.setEnabled(true);for(EditText field:inputs)field.setText("");saveVerbau.run();}).show();});
  ScrollView scroll=new ScrollView(this);scroll.addView(fields);
  new android.app.AlertDialog.Builder(this).setTitle("Verbau – Flächenvorberechnung").setView(scroll).setPositiveButton("Schließen",null).show();
 });
 actionButtonSingle(earthCatalog,"Bodenaustausch und Entsorgung",()->{
  LinearLayout fields=new LinearLayout(this);fields.setOrientation(1);
  String[] hints={"Bodenaushub fest (m³), ohne Oberbau","Wiederverwendbarer Boden fest (m³)","Benötigte Verfüllung verdichtet (m³)","Auflockerungsfaktor Entsorgung","Lieferfaktor Ersatzmaterial"};
  EditText[] inputs=new EditText[hints.length];
  for(int i=0;i<hints.length;i++){inputs[i]=new EditText(this);inputs[i].setHint(hints[i]);inputs[i].setInputType(8194);fields.addView(inputs[i]);}
  TextView resultText=new TextView(this);fields.addView(resultText);
  Button calculate=new Button(this);calculate.setText("Berechnen");fields.addView(calculate);
  calculate.setOnClickListener(button->{
   try{
    double[] v=new double[5];for(int i=0;i<v.length;i++){v[i]=number(inputs[i]);if(!Double.isFinite(v[i])||v[i]<0)throw new IllegalArgumentException("Ungültige Eingabe: "+hints[i]);}
    if(v[1]>v[0]||v[3]<1||v[4]<1)throw new IllegalArgumentException("Wiederverwendung und Faktoren prüfen");
    double reuse=Math.min(v[1],v[2]),disposal=v[0]-reuse,replacement=v[2]-reuse;
    resultText.setText(String.format(java.util.Locale.GERMANY,"Wiedereinbau (angenommen 1:1): %.3f m³\nEntsorgung fest: %.3f m³\nEntsorgung lose: %.3f m³\nErsatzmaterial verdichtet: %.3f m³\nLiefermenge lose: %.3f m³\n\nAnnahme: Festvolumen des wiederverwendeten Bodens entspricht dem verdichteten Einbauvolumen. Faktoren projektspezifisch prüfen.",reuse,disposal,disposal*v[3],replacement,replacement*v[4]));
   }catch(Exception ex){resultText.setText("Berechnung nicht möglich: "+ex.getMessage());}
  });
  ScrollView scroll=new ScrollView(this);scroll.addView(fields);
  new android.app.AlertDialog.Builder(this).setTitle("Bodenbilanz – Vorberechnung").setView(scroll).setPositiveButton("Schließen",null).show();
 });
 actionButtonSingle(earthCatalog,"Leitungszone und Verfüllung",()->{
  try{
   org.json.JSONArray rows=new PipeLayoutStore(getPreferences(0)).sections();
   if(rows.length()==0){notice("Keine Abschnittsprofile vorhanden.");return;}
   EditText coverZone=new EditText(this);coverZone.setInputType(8194);coverZone.setHint("Leitungszone über Rohrscheitel (m)");
   android.app.AlertDialog inputDialog=new android.app.AlertDialog.Builder(this).setTitle("Leitungszone – Überdeckung").setView(coverZone).setNegativeButton("Abbrechen",null).setPositiveButton("Berechnen",null).create();
   inputDialog.setOnShowListener(open->inputDialog.getButton(-1).setOnClickListener(button->{
    try{
     double cover=number(coverZone);if(!Double.isFinite(cover)||cover<0)throw new IllegalArgumentException("Ungültige Überdeckung");
     double[] totals=new double[4];StringBuilder report=new StringBuilder();double covered=0;
     for(int i=0;i<rows.length();i++){
      org.json.JSONObject row=rows.getJSONObject(i);
      double from=row.getDouble("from"),to=row.getDouble("to"),bw=row.getDouble("width"),bd=row.getDouble("depth"),len=to-from;
      if(!Double.isFinite(from+to+bw+bd)||from<0||len<=0||bw<=0||bd<=0||(l>0&&to>l))throw new IllegalArgumentException("Ungültiger Abschnitt");
      for(int j=0;j<i;j++){org.json.JSONObject prev=rows.getJSONObject(j);if(from<prev.getDouble("to")&&to>prev.getDouble("from"))throw new IllegalArgumentException("Überlappende Abschnitte");}
      java.util.List<PipeLayout.Pipe> pipes=PipeLayoutStore.decode(row.getJSONArray("pipes").toString());
      if(pipes.isEmpty()||!PipeLayout.warnings(pipes,bw,bd,0,0).isEmpty())throw new IllegalArgumentException("Rohrlage prüfen: Station "+from);
      double bottom=bd,top=0,displacement=0;
      for(PipeLayout.Pipe pipe:pipes){bottom=Math.min(bottom,pipe.bedding);top=Math.max(top,pipe.bedding+pipe.diameter);displacement+=Math.PI*pipe.diameter*pipe.diameter/4*len;}
      double zoneTop=top+cover;if(zoneTop>bd)throw new IllegalArgumentException("Leitungszone über GOK: Station "+from);
      double bed=bottom*bw*len,zone=(zoneTop-bottom)*bw*len-displacement,fill=(bd-zoneTop)*bw*len;
      totals[0]+=bed;totals[1]+=zone;totals[2]+=fill;totals[3]+=displacement;covered+=len;
      report.append(String.format(java.util.Locale.GERMANY,"Station %.2f – %.2f m\nUntere Bettung: %.3f m³\nLeitungszone ohne untere Bettung: %.3f m³\nRestverfüllung brutto: %.3f m³\nRohrverdrängung: %.3f m³\n\n",from,to,bed,zone,fill,displacement));
     }
     report.append(String.format(java.util.Locale.GERMANY,"Summen (%.2f m)\nBettung: %.3f m³\nLeitungszone: %.3f m³\nRestverfüllung: %.3f m³\nRohrverdrängung: %.3f m³",covered,totals[0],totals[1],totals[2],totals[3]));
     report.append("\n\nRechteckiger Querschnitt. Restverfüllung einschließlich Oberflächenaufbau; Schichten noch abzuziehen. Nur gespeicherte Profile. Keine Verdichtungszuschläge.");
     TextView resultText=new TextView(this);resultText.setPadding(dp(16),dp(12),dp(16),dp(12));resultText.setText(report.toString());ScrollView scroll=new ScrollView(this);scroll.addView(resultText);
     inputDialog.dismiss();new android.app.AlertDialog.Builder(this).setTitle("Leitungszone – Mengen").setView(scroll).setPositiveButton("Schließen",null).show();
    }catch(Exception ex){coverZone.setError(ex.getMessage());}
   }));inputDialog.show();
  }catch(Exception ex){notice(ex.getMessage());}
 });
 actionButtonSingle(surfaceWorkCatalog,"Schichtvolumen berechnen",()->{
  try{
   String report=surfaceVolumeReport();
   TextView resultText=new TextView(this);resultText.setPadding(dp(16),dp(12),dp(16),dp(12));resultText.setText(report);ScrollView scroll=new ScrollView(this);scroll.addView(resultText);
   new android.app.AlertDialog.Builder(this).setTitle("Oberflächenaufbau – Schichtvolumen").setView(scroll).setPositiveButton("Schließen",null).show();
  }catch(Exception ex){notice("Schichtvolumen: "+ex.getMessage());}
 });
 actionButtonSingle(surfaceWorkCatalog,"Oberflächenmengen nach Abschnitt",()->{
  try{
   String report=surfaceAreaReport();
   TextView resultText=new TextView(this);resultText.setPadding(dp(16),dp(12),dp(16),dp(12));resultText.setText(report);ScrollView scroll=new ScrollView(this);scroll.addView(resultText);
   new android.app.AlertDialog.Builder(this).setTitle("Oberflächenmengen").setView(scroll).setPositiveButton("Schließen",null).show();
  }catch(Exception ex){notice("Oberflächenmengen: "+ex.getMessage());}
 });
 actionButtonSingle(sectionCatalog,"Erdmengen nach Abschnitt",()->{
  try{
   org.json.JSONArray rows=new PipeLayoutStore(getPreferences(0)).sections();
   if(rows.length()==0){notice("Keine Abschnittsprofile vorhanden.");return;}
   java.util.List<org.json.JSONObject> sorted=new java.util.ArrayList<>();
   for(int i=0;i<rows.length();i++)sorted.add(rows.getJSONObject(i));
   sorted.sort((a,b)->Double.compare(a.optDouble("from"),b.optDouble("from")));
   double total=0,covered=0,last=0;
   StringBuilder report=new StringBuilder("Aushub – rechteckig, brutto\n\n");
   for(org.json.JSONObject row:sorted){
    double from=row.getDouble("from"),to=row.getDouble("to"),bw=row.getDouble("width"),bd=row.getDouble("depth");
    if(!Double.isFinite(from)||!Double.isFinite(to)||!Double.isFinite(bw)||!Double.isFinite(bd)||from<last||to<=from||bw<=0||bd<=0)throw new IllegalArgumentException("Ungültige oder überlappende Abschnittsprofile");
    double length=to-from,volume=CivilGeometry.rectangularVolume(length,bw,bd);
    if(!Double.isFinite(volume)||!Double.isFinite(total+volume)||!Double.isFinite(covered+length))throw new IllegalArgumentException("Abschnittsmengen außerhalb des Zahlenbereichs");
    total+=volume;covered+=length;last=to;
    report.append(String.format(java.util.Locale.GERMANY,"Station %.2f – %.2f m\n%.2f × %.2f × %.2f = %.3f m³\n\n",from,to,length,bw,bd,volume));
   }
   report.append(String.format(java.util.Locale.GERMANY,"Summe: %.3f m³\nErfasste Länge: %.2f m",total,covered));
   if(l>0){if(last>l)report.append("\nAchtung: Profil endet außerhalb der Trasse.");else report.append(String.format(java.util.Locale.GERMANY,"\nNicht erfasste Länge: %.2f m",Math.max(0,l-covered)));}
   if(l>0&&last<=l){
    double remaining=Math.max(0,l-covered);
    if(remaining<=0.000001)report.append(String.format(java.util.Locale.GERMANY,"\nGesamtaushub: %.3f m³",total));
    else{
     try{
      double standardWidth=number(width),standardDepth=number(depth);
      if(!Double.isFinite(standardWidth)||!Double.isFinite(standardDepth)||standardWidth<=0||standardDepth<=0)throw new IllegalArgumentException();
      double remainderVolume=CivilGeometry.rectangularVolume(remaining,standardWidth,standardDepth);
      if(!Double.isFinite(remainderVolume)||!Double.isFinite(total+remainderVolume))throw new IllegalArgumentException();
      report.append(String.format(java.util.Locale.GERMANY,"\nResttrasse (allgemeines Regelprofil): %.2f × %.2f × %.2f = %.3f m³\nGesamtaushub: %.3f m³",remaining,standardWidth,standardDepth,remainderVolume,total+remainderVolume));
     }catch(Exception ex){report.append("\nGesamtaushub nicht ermittelbar: allgemeine Grabenmaße fehlen.");}
    }
   }
   report.append("\n\nBrutto, rechteckig; keine Abzüge oder Böschungen. Abschnittsbezogene Sondermaße außerhalb dieser Profile sind nicht berücksichtigt. Nicht zusätzlich zum allgemeinen Aushub addieren.");
   TextView resultText=new TextView(this);resultText.setPadding(dp(16),dp(12),dp(16),dp(12));resultText.setText(report.toString());ScrollView scroll=new ScrollView(this);scroll.addView(resultText);
   new android.app.AlertDialog.Builder(this).setTitle("Mengenermittlung – Abschnittsprofile").setView(scroll).setPositiveButton("Schließen",null).show();
  }catch(Exception ex){notice("Mengenermittlung: "+ex.getMessage());}
 });
 actionButtonSingle(sectionCatalog,"Abschnittsprofile anzeigen",()->{
  try{
   org.json.JSONArray profiles=new PipeLayoutStore(getPreferences(0)).sections();
   if(profiles.length()==0){notice("Keine Abschnittsprofile vorhanden.");return;}
   String[] labels=new String[profiles.length()];
   for(int i=0;i<profiles.length();i++){org.json.JSONObject p=profiles.getJSONObject(i);labels[i]=String.format(java.util.Locale.GERMANY,"Station %.2f – %.2f m",p.getDouble("from"),p.getDouble("to"));}
   new android.app.AlertDialog.Builder(this).setTitle("Abschnittsprofile").setItems(labels,(dialog,index)->{
    try{
     org.json.JSONObject p=profiles.getJSONObject(index);
     double bw=p.getDouble("width"),bd=p.getDouble("depth");
     java.util.List<PipeLayout.Pipe> pipes=PipeLayoutStore.decode(p.getJSONArray("pipes").toString());
     LinearLayout body=new LinearLayout(this);body.setOrientation(1);
     PipeLayoutView view=new PipeLayoutView(this);view.setLayoutData(pipes,bw,bd);
     body.addView(view,new LinearLayout.LayoutParams(-1,dp(300)));
     TextView dimensionsText=new TextView(this);dimensionsText.setText(android.text.TextUtils.join("\n",PipeLayout.dimensions(pipes,bw,bd)));body.addView(dimensionsText);
     ScrollView scroll=new ScrollView(this);scroll.addView(body);
     new android.app.AlertDialog.Builder(this).setTitle(labels[index]).setView(scroll).setPositiveButton("Schließen",null).setNeutralButton("Löschen",(preview,which)->{
      new android.app.AlertDialog.Builder(this).setTitle("Abschnittsprofil löschen?").setMessage(labels[index]).setNegativeButton("Abbrechen",null).setPositiveButton("Löschen",(confirm,choice)->{
       try{new PipeLayoutStore(getPreferences(0)).deleteSection(p.getDouble("from"),p.getDouble("to"));refreshMainQuantities();if(sketch!=null)sketch.invalidate();notice("Abschnittsprofil gelöscht.");}
       catch(Exception ex){notice(ex.getMessage());}
      }).show();
     }).setNegativeButton("Maße bearbeiten",(preview,which)->{
      LinearLayout fields=new LinearLayout(this);fields.setOrientation(1);
      EditText newWidth=new EditText(this),newDepth=new EditText(this);
      newWidth.setHint("Grabenbreite (m)");newDepth.setHint("Grabentiefe (m)");
      newWidth.setInputType(8194);newDepth.setInputType(8194);newWidth.setText(String.valueOf(bw));newDepth.setText(String.valueOf(bd));fields.addView(newWidth);fields.addView(newDepth);
      utilitySlider(fields,newWidth,"Grabenbreite (m)",0.1,Math.max(6,bw),0.01,true);
      utilitySlider(fields,newDepth,"Grabentiefe (m)",0.1,Math.max(8,bd),0.01,true);
      PipeLayoutView draftView=new PipeLayoutView(this);draftView.setLayoutData(pipes,bw,bd);fields.addView(draftView,new LinearLayout.LayoutParams(-1,dp(180)));
      TextView volumePreview=new TextView(this);fields.addView(volumePreview);
      Runnable updateDraft=()->{try{double ww=number(newWidth),dd=number(newDepth);if(!Double.isFinite(ww)||!Double.isFinite(dd)||ww<=0||dd<=0)return;draftView.setLayoutData(pipes,ww,dd);volumePreview.setText(String.format(java.util.Locale.GERMANY,"Aushub (rechteckig, brutto): %.3f m³",ww*dd*(p.optDouble("to")-p.optDouble("from"))));}catch(Exception ignored){}};
      android.text.TextWatcher draftWatcher=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){updateDraft.run();}public void afterTextChanged(android.text.Editable e){}};
      newWidth.addTextChangedListener(draftWatcher);newDepth.addTextChangedListener(draftWatcher);updateDraft.run();
      android.app.AlertDialog editor=new android.app.AlertDialog.Builder(this).setTitle(labels[index]).setView(fields).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();
      editor.setOnShowListener(open->editor.getButton(-1).setOnClickListener(saveButton->{
       try{new PipeLayoutStore(getPreferences(0)).saveSection(p.getDouble("from"),p.getDouble("to"),number(newWidth),number(newDepth),pipes);refreshMainQuantities();if(sketch!=null)sketch.invalidate();editor.dismiss();notice("Abschnittsprofil aktualisiert.");}
       catch(Exception ex){newDepth.setError(ex.getMessage());}
      }));editor.show();
     }).show();
    }catch(Exception ex){notice("Abschnittsprofil: "+ex.getMessage());}
   }).setNegativeButton("Schließen",null).show();
  }catch(Exception ex){notice("Abschnittsprofile: "+ex.getMessage());}
 });
 sheetHeading(planPage,"Stationierte Objekte");
 stationRows=new LinearLayout(this);
 stationRows.setOrientation(LinearLayout.VERTICAL);
 planPage.addView(stationRows);
 root=profilePage;
 sheetHeading(root,"Regelprofil");
 LinearLayout layers=fold(root,"Oberflächenaufbau",true);
 surfaceLayers=sheetText("",16,false);surfaceLayers.setLineSpacing(dp(6),1);layers.addView(surfaceLayers);showSurfaceLayers();
 overviewAction(layers,"Oberflächenaufbau bearbeiten","layers",()->openBelagsDetails());
 legend=new TextView(this);legend.setTextSize(14);root.addView(legend);
 root=segmentPage;
 Button editSurface=new Button(this);editSurface.setText("Belagsdetails bearbeiten");root.addView(editSurface);editSurface.setOnClickListener(v->openBelagsDetails());
 TextView segmentHeading=new TextView(this);segmentHeading.setText("Oberflächenabschnitte");segmentHeading.setTextSize(20);root.addView(segmentHeading);
 stationFrom=field(root,"Station von (m)","stationFrom",true);
 stationTo=field(root,"Station bis (m)","stationTo",true);
 try{segments=new org.json.JSONArray(getPreferences(0).getString("segments","[]"));}catch(Exception ignored){}
 Button addSegment=new Button(this);addSegment.setText("Abschnitt hinzufügen");root.addView(addSegment);
 segmentsView=new TextView(this);root.addView(segmentsView);
 Button removeSegment=new Button(this);removeSegment.setText("Letzten Abschnitt entfernen");root.addView(removeSegment);
 addSegment.setOnClickListener(v->addSegment());
 removeSegment.setOnClickListener(v->{if(segments.length()>0){segments.remove(segments.length()-1);persistSegments();}});
 showSegments();
 root=objectPage;
 TextView oh=new TextView(this);oh.setText("Trassenobjekte");oh.setTextSize(20);root.addView(oh);
 objectType=new Spinner(this);objectType.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Hausanschluss","Suchschachtung","Startgrube","Zielgrube","KVS","Hindernis","Kernbohrung","Knickpunkt"}));root.addView(objectType);
 objectStation=field(root,"Objektstation (m)","objectStation",true);
 objectSide=new Spinner(this);objectSide.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Unbekannt","links","rechts","mittig"}));root.addView(objectSide);
 objectNote=field(root,"Objektdetails / Bemerkung","objectNote",false);
 try{objects=new org.json.JSONArray(getPreferences(0).getString("objects","[]"));}catch(Exception ignored){}
  readHistory("objectUndo",undoObjects);
  readHistory("objectRedo",redoObjects);
  updateHistoryButtons();
 Button ob=new Button(this);ob.setText("Objekt hinzufügen");root.addView(ob);ob.setOnClickListener(v->addObject());
 objectList=new TextView(this);root.addView(objectList);showObjects();
 Button undo=new Button(this);undo.setText("Letztes Objekt entfernen");root.addView(undo);undo.setOnClickListener(v->{if(objects.length()>0){objects.remove(objects.length()-1);saveObjects();}});
 root=profilePage;
 profile=new Profile();root.addView(profile,new LinearLayout.LayoutParams(-1,(int)(300*getResources().getDisplayMetrics().density)));
 sectionProfilePanel=page();profilePage.addView(sectionProfilePanel,0);

 LinearLayout optionsPanel=page();
 overviewAction(optionsPanel,"Längsschnitt anzeigen","sections",()->showLongitudinalProfile());
 actionButtonSingle(optionsPanel,"Verbau – Profilstation wählen",()->{
  EditText station=new EditText(this);station.setInputType(8194);station.setText(String.valueOf(profileShoringStation));
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Profilstation (m)").setView(station).setNegativeButton("Abbrechen",null).setPositiveButton("Übernehmen",null).create();
  dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(btn->{try{double value=number(station);if(!Double.isFinite(value)||value<0||l<=0||value>l){station.setError("Station innerhalb der Trasse eingeben");return;}profileShoringStation=value;profile.invalidate();dialog.dismiss();}catch(Exception ex){station.setError("Ungültige Station");}}));dialog.show();
 });

 LinearLayout sizes=page();
 sizes.addView(sheetText("Grabenmaße – direkt bearbeiten",16,true));
 profileSlider(sizes,"Grabenbreite",width,10,600);
 profileSlider(sizes,"Grabentiefe",depth,10,800);
 profileSlider(sizes,"Nachschnitt links",leftInput,0,200);
 profileSlider(sizes,"Nachschnitt rechts",rightInput,0,200);
 LinearLayout asphaltControls=page(),stoneControls=page();
 LinearLayout buildPanel=page();overviewAction(buildPanel,"Oberfläche / Belagsdetails","layers",()->openBelagsDetails());buildPanel.addView(asphaltControls);buildPanel.addView(stoneControls);
 profileSlider(asphaltControls,"Asphaltstärke",asphaltInput,0,100);
 utilitySlider(stoneControls,stoneDepth,"Belagsstärke (cm)",0,100,1,true);
 EditText stoneExact=new EditText(this);stoneExact.setSingleLine(true);stoneExact.setInputType(8194);stoneExact.setText(stoneDepth.getText());stoneControls.addView(stoneExact);
 final boolean[] syncingStone={false};
 stoneDepth.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){if(syncingStone[0])return;syncingStone[0]=true;stoneExact.setText(s);syncingStone[0]=false;refreshProfileDraft();}public void afterTextChanged(android.text.Editable e){}});
 stoneExact.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){if(syncingStone[0])return;syncingStone[0]=true;stoneDepth.setText(s);syncingStone[0]=false;refreshProfileDraft();}public void afterTextChanged(android.text.Editable e){}});
 profileSlider(buildPanel,"Tragschicht",baseInput,0,200);
 Runnable updateSurfaceControls=()->{boolean asphaltSelected="Asphalt".equals(String.valueOf(surface.getSelectedItem()));asphaltControls.setVisibility(asphaltSelected?View.VISIBLE:View.GONE);stoneControls.setVisibility(isStoneSurface(String.valueOf(surface.getSelectedItem()))?View.VISIBLE:View.GONE);};
 buildPanel.getViewTreeObserver().addOnGlobalLayoutListener(()->updateSurfaceControls.run());
 updateSurfaceControls.run();
 LinearLayout pipeDetails=page();
 utilitySlider(pipeDetails,pipeInput,"Rohr-Außendurchmesser (mm)",1,2000,1,true);
 pipeInput.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){if(profile!=null)profile.invalidate();}public void afterTextChanged(android.text.Editable e){}});
 overviewAction(pipeDetails,"Weitere Leitungen / Kabel","pipe",()->editUtilities());
 profilePage.removeView(legend);pipeDetails.addView(legend);
 profilePage.removeView(sectionProfilePanel);
 profilePage.removeView(profile);
 LinearLayout profileWorkspace=new LinearLayout(this);profileWorkspace.setOrientation(getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
 profilePage.addView(profileWorkspace,Math.min(2,profilePage.getChildCount()));
 boolean wideProfile=profileWorkspace.getOrientation()==LinearLayout.HORIZONTAL;
 profileWorkspace.addView(profile,wideProfile?new LinearLayout.LayoutParams(0,dp(300),1):new LinearLayout.LayoutParams(-1,dp(260)));
 profileWorkspace.addView(sizes,wideProfile?new LinearLayout.LayoutParams(0,-2,1):new LinearLayout.LayoutParams(-1,-2));
 LinearLayout toolbar=new LinearLayout(this);profilePage.addView(toolbar,1);String[] names={"Aufbau","Leitungen","Maße","Abschnitte","Optionen"};String[] icons={"layers","pipe","ruler","sections","settings"};LinearLayout[] panels={buildPanel,pipeDetails,sizes,sectionProfilePanel,optionsPanel};
 for(int j=0;j<names.length;j++){final int index=j;Button tool=new Button(this);styleEditorButton(tool,false);compactToolIcon(tool,names[j],icons[j]);tool.setSingleLine(true);if(android.os.Build.VERSION.SDK_INT>=26)tool.setAutoSizeTextTypeUniformWithConfiguration(9,12,1,android.util.TypedValue.COMPLEX_UNIT_SP);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(54),1);lp.setMargins(dp(2),dp(2),dp(2),dp(2));toolbar.addView(tool,lp);tool.setOnClickListener(v->{if(index==2){sizes.requestFocus();return;}if(index==3)openSectionProfilePicker();showProfilePanel(names[index],panels[index]);});}

 LinearLayout profileFooter=new LinearLayout(this);profilePage.addView(profileFooter);
 Button standard=new Button(this),applyProfile=new Button(this);
 styleEditorButton(standard,false);styleEditorButton(applyProfile,true);
 standard.setText("Standard-Profil verwalten");applyProfile.setText("Übernehmen");
 profileFooter.addView(standard,new LinearLayout.LayoutParams(0,dp(52),1));profileFooter.addView(applyProfile,new LinearLayout.LayoutParams(0,dp(52),1));
 standard.setOnClickListener(v->manageStandardProfile());
 applyProfile.setOnClickListener(v->{if(length.length()>0){if(calculate())notice("Regelprofil übernommen.");}else{refreshProfileDraft();if(saveDraft())notice("Profilentwurf gespeichert.");}});
 save.setOnClickListener(v->{if(calculate())showPage(planPage);});if(projectChosen&&length.length()>0&&width.length()>0&&depth.length()>0)calculate();showPage(planPage);if(!projectChosen)showProjectStart();
 }
 void manageStandardProfile(){
  SharedPreferences defaults=getSharedPreferences("base_profile_defaults",MODE_PRIVATE);
  LinearLayout fields=page();
  EditText bw=projectFormField(fields,"Grabenbreite (m)"),bd=projectFormField(fields,"Grabentiefe (m)");
  for(EditText f:new EditText[]{bw,bd})f.setInputType(8194);
  bw.setText(String.format(java.util.Locale.GERMANY,"%.2f",defaults.getFloat("width",0.4f)));
  bd.setText(String.format(java.util.Locale.GERMANY,"%.2f",defaults.getFloat("depth",1.0f)));
  fields.addView(sheetText("Gilt für neue Projekte. Bestehende Profile bleiben unverändert.",13,false));
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Standard-Profil verwalten").setView(fields).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();
  dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(btn->{try{
   double ww=number(bw),dd=number(bd);
   if(!Double.isFinite(ww)||ww<0.1||ww>6){bw.setError("0,10 – 6,00 m");return;}
   if(!Double.isFinite(dd)||dd<0.1||dd>8){bd.setError("0,10 – 8,00 m");return;}
   if(!defaults.edit().putFloat("width",(float)ww).putFloat("depth",(float)dd).commit()){notice("Speichern fehlgeschlagen.");return;}
   dialog.dismiss();
  }catch(Exception e){bw.setError("Gültige Maße eingeben");}}));dialog.show();
 }
 LinearLayout sectionProfilePanel;
 void showProfilePanel(String title,LinearLayout panel){
  if(panel.getParent()!=null)return;ScrollView scroll=new ScrollView(this);scroll.addView(panel);android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Fertig",null).create();dialog.setOnDismissListener(v->{scroll.removeView(panel);refreshProfileDraft();if(profile!=null)profile.invalidate();});dialog.show();
 }
 void openSectionProfilePicker(){
  sectionProfilePanel.removeAllViews();
  try{
   org.json.JSONArray stored=new PipeLayoutStore(getPreferences(0)).sections();
   java.util.ArrayList<org.json.JSONObject> rows=new java.util.ArrayList<>();
   for(int i=0;i<stored.length();i++)rows.add(stored.getJSONObject(i));
   for(int i=0;i<segments.length();i++){
    org.json.JSONObject seg=segments.getJSONObject(i);double a=seg.getDouble("from"),b=seg.getDouble("to");boolean overlap=false;
    for(org.json.JSONObject p:rows)if(a<p.getDouble("to")&&b>p.getDouble("from")){overlap=true;break;}
    if(!overlap)rows.add(new org.json.JSONObject().put("from",a).put("to",b).put("width",seg.getDouble("width")).put("depth",d>0?d:0).put("pipes",new org.json.JSONArray()));
   }
   rows.sort((a,b)->Double.compare(a.optDouble("from"),b.optDouble("from")));
   if(rows.isEmpty()){sectionProfilePanel.addView(sheetText("Keine Abschnitte vorhanden",15,false));return;}
   LinearLayout body=page();sectionProfilePanel.addView(body);
   body.addView(sheetText("Abschnitte der Trasse",16,true));
   Spinner picker=new Spinner(this);body.addView(picker);picker.setVisibility(View.GONE);
   LinearLayout stationList=page();stationList.setBackground(editorShape(0xff202525,12));body.addView(stationList);
   for(int j=0;j<rows.size();j++){
    final int selection=j+1;org.json.JSONObject row=rows.get(j);
    LinearLayout item=new LinearLayout(this);item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(dp(12),dp(10),dp(12),dp(10));item.setMinimumHeight(dp(60));stationList.addView(item);
    Button marker=new Button(this);compactToolIcon(marker,"","sections");marker.setBackgroundColor(Color.TRANSPARENT);marker.setClickable(false);marker.setFocusable(false);marker.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(0xff58c9b0));item.addView(marker,new LinearLayout.LayoutParams(dp(36),dp(44)));
    LinearLayout labelsBox=new LinearLayout(this);labelsBox.setOrientation(1);item.addView(labelsBox,new LinearLayout.LayoutParams(0,-2,1));
    labelsBox.addView(sheetText(String.format(java.util.Locale.GERMANY,"Abschnitt %02d",selection),15,true));
    TextView range=sheetText(String.format(java.util.Locale.GERMANY,"%.2f – %.2f m",row.getDouble("from"),row.getDouble("to")),13,false);range.setTextColor(0xffaeb9b8);labelsBox.addView(range);
    TextView arrow=sheetText("›",24,false);item.addView(arrow);item.setBackground(editorShape(0xff202525,8));item.setClickable(true);item.setFocusable(true);item.setOnClickListener(v->{picker.setSelection(selection);for(int k=0;k<stationList.getChildCount();k++){View child=stationList.getChildAt(k);if(child instanceof LinearLayout)child.setBackground(editorShape(child==item?0xff28564c:0xff202525,8));}});
    if(j<rows.size()-1){View line=new View(this);line.setBackgroundColor(0xff414848);stationList.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));}
   }
   String[] labels=new String[rows.size()+1];labels[0]="Standardprofil";for(int i=0;i<rows.size();i++){org.json.JSONObject p=rows.get(i);labels[i+1]=String.format(java.util.Locale.GERMANY,"Abschnitt %02d · %.2f – %.2f m",i+1,p.getDouble("from"),p.getDouble("to"));}
   picker.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels));
   actionButtonSingle(body,"Standardprofil",()->{picker.setSelection(0);for(int k=0;k<stationList.getChildCount();k++){View child=stationList.getChildAt(k);if(child instanceof LinearLayout)child.setBackground(editorShape(0xff202525,8));}});
   LinearLayout controls=page();body.addView(controls);PipeLayoutView view=new PipeLayoutView(this);controls.addView(view,new LinearLayout.LayoutParams(-1,dp(240)));
   EditText bw=new EditText(this),bd=new EditText(this);bw.setHint("Grabenbreite (m)");bd.setHint("Grabentiefe (m)");controls.addView(bw);utilitySlider(controls,bw,"Grabenbreite (m)",0.1,6,0.01,true);controls.addView(bd);utilitySlider(controls,bd,"Grabentiefe (m)",0.1,8,0.01,true);
   EditText cutL=new EditText(this),cutR=new EditText(this),topLayer=new EditText(this),baseLayer=new EditText(this);
   EditText[] extras={cutL,cutR,topLayer,baseLayer};String[] extraLabels={"Nachschnitt links (m)","Nachschnitt rechts (m)","Deckschicht (m)","Tragschicht (m)"};
   for(int j=0;j<extras.length;j++){extras[j].setHint(extraLabels[j]);controls.addView(extras[j]);utilitySlider(controls,extras[j],extraLabels[j],0,j<2?2:1,0.01,true);}
   TextView quantity=sheetText("",14,false);controls.addView(quantity);
   final boolean[] loading={false};
   Runnable preview=()->{if(loading[0])return;try{org.json.JSONObject p=rows.get(picker.getSelectedItemPosition()-1);double ww=number(bw),dd=number(bd);if(!Double.isFinite(ww)||!Double.isFinite(dd)||ww<=0||dd<=0)return;view.setLayoutData(PipeLayoutStore.decode(p.getJSONArray("pipes").toString()),ww,dd);
    org.json.JSONObject seg=findSurfaceSection(p);String type=seg==null?covering:seg.optString("surface",covering);
    RouteSketch style=new RouteSketch(MainActivity.this,"[]");style.bindDimensionProject(activeProject);
    view.setAsphaltProfile("Asphalt".equals(type));
    view.setCivilProfile(optional(cutL),optional(cutR),optional(topLayer),optional(baseLayer),style.configuredSurfaceColor(type,style.legendSettings()));
    quantity.setText(String.format(java.util.Locale.GERMANY,"Aushub (brutto): %.3f m³",(p.getDouble("to")-p.getDouble("from"))*ww*dd));}catch(Exception ignored){}};
   android.text.TextWatcher watcher=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){preview.run();}public void afterTextChanged(android.text.Editable e){}};
   bw.addTextChangedListener(watcher);bd.addTextChangedListener(watcher);for(EditText field:extras)field.addTextChangedListener(watcher);
   java.util.function.IntConsumer load=i->{boolean standard=i==0;controls.setVisibility(standard?View.GONE:View.VISIBLE);if(standard)return;org.json.JSONObject p=rows.get(i-1);loading[0]=true;bw.setText(String.valueOf(p.optDouble("width")));bd.setText(p.optDouble("depth")>0?String.valueOf(p.optDouble("depth")):"");org.json.JSONObject seg=findSurfaceSection(p);String[] keys={"left","right","topThickness","baseThickness"};for(int j=0;j<extras.length;j++)extras[j].setText(seg==null?"":seg.optString(keys[j],""));loading[0]=false;preview.run();};
   onChoice(picker,load);load.accept(0);
   actionButtonSingle(controls,"Abschnitt speichern",()->{try{
    int i=picker.getSelectedItemPosition()-1;org.json.JSONObject p=rows.get(i);double ww=number(bw),dd=number(bd);
    double cl=optional(cutL),cr=optional(cutR),tl=optional(topLayer),bl=optional(baseLayer);
    if((Double.isFinite(tl)?tl:0)+(Double.isFinite(bl)?bl:0)>dd)throw new IllegalArgumentException("Schichtstärken überschreiten Grabentiefe");
    org.json.JSONArray nextSurfaces=new org.json.JSONArray(segments.toString());
    org.json.JSONObject seg=null;
    double from=p.getDouble("from"),to=p.getDouble("to");
    for(int j=0;j<nextSurfaces.length();j++){
     org.json.JSONObject other=nextSurfaces.getJSONObject(j);
     double a=other.getDouble("from"),b=other.getDouble("to");
     if(from==a&&to==b){seg=other;continue;}
     if(from<b&&to>a)throw new IllegalArgumentException("Oberflächenabschnitt überlappt – Grenzen zuerst angleichen");
    }
    if(seg==null){seg=new org.json.JSONObject().put("from",from).put("to",to).put("surface",covering);nextSurfaces.put(seg);}
    if(l>0&&to>l)throw new IllegalArgumentException("Abschnitt außerhalb der Trasse");
    seg.put("width",ww);String[] keys={"left","right","topThickness","baseThickness"};double[] vals={cl,cr,tl,bl};
    for(int j=0;j<keys.length;j++){if(Double.isFinite(vals[j]))seg.put(keys[j],vals[j]);else seg.remove(keys[j]);}
    if(Double.isFinite(cl)&&Double.isFinite(cr))seg.put("cutWidth",ww+cl+cr);else seg.remove("cutWidth");
    new PipeLayoutStore(getPreferences(0)).saveSectionWithSurfaces(from,to,ww,dd,PipeLayoutStore.decode(p.getJSONArray("pipes").toString()),nextSurfaces);
    segments=nextSurfaces;showSegments();refreshStationLayers();
    p.put("width",ww).put("depth",dd);if(sketch!=null)sketch.invalidate();refreshMainQuantities();refreshDetailQuantities();notice("Abschnittsprofil gespeichert.");
   }catch(Exception ex){notice("Abschnittsprofil: "+ex.getMessage());}});
   controls.addView(sheetText("Vor dem Wechsel Änderungen speichern. Standardprofil bleibt unverändert.",13,false));
   
  }catch(Exception ex){notice("Abschnittsprofile: "+ex.getMessage());}
 }
 org.json.JSONObject findSurfaceSection(org.json.JSONObject p){
  for(int j=0;j<segments.length();j++){org.json.JSONObject seg=segments.optJSONObject(j);if(seg!=null&&seg.optDouble("from")==p.optDouble("from")&&seg.optDouble("to")==p.optDouble("to"))return seg;}return null;
 }
 void actionButton(LinearLayout bar,String label,Runnable action){
  Button button=actionButtonSingle(bar,label,action);button.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
 }
 Button actionButtonSingle(LinearLayout bar,String label,Runnable action){
  Button button=new Button(this);button.setAllCaps(false);button.setText(label);
  styleEditorButton(button,label.equals("Speichern"));button.setTextSize(14);
  button.setOnClickListener(v->action.run());bar.addView(button);return button;
 }
 LinearLayout fold(LinearLayout parent,String title,boolean expanded){
  Button toggle=new Button(this);toggle.setAllCaps(false);toggle.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
  toggle.setText(title+(expanded?" ▴":" ▾"));styleEditorButton(toggle,false);toggle.setTextSize(14);parent.addView(toggle);
  LinearLayout content=page();parent.addView(content);content.setVisibility(expanded?View.VISIBLE:View.GONE);
  toggle.setOnClickListener(v->{boolean open=content.getVisibility()!=View.VISIBLE;
   content.setVisibility(open?View.VISIBLE:View.GONE);toggle.setText(title+(open?" ▴":" ▾"));});
  return content;
 }
 void utilitySlider(LinearLayout form,EditText field,String label,double min,double max,double step,boolean enabled){
  TextView title=new TextView(this);title.setText(label);form.addView(title);
  SeekBar bar=new SeekBar(this);styleProfileSlider(bar);bar.setMax((int)Math.ceil((max-min)/step));bar.setEnabled(enabled);form.addView(bar);
  field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|(min<0?android.text.InputType.TYPE_NUMBER_FLAG_SIGNED:0));
  Runnable sync=()->{try{double value=Double.parseDouble(field.getText().toString().replace(',','.'));if(Double.isFinite(value))bar.setProgress((int)Math.round((value-min)/step));}catch(Exception ignored){}};
  field.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){sync.run();}public void afterTextChanged(android.text.Editable e){}});
  bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int p,boolean user){if(user)field.setText(String.format(java.util.Locale.GERMANY,step>=1?"%.0f":step>=0.01?"%.2f":"%.3f",Math.min(max,min+p*step)));}});
  sync.run();
 }
 void styleProfileSlider(SeekBar bar){bar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xffeeeeee));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff484848));bar.setThumbTintList(android.content.res.ColorStateList.valueOf(0xff111111));bar.setMinHeight(dp(44));}
 void profileSlider(LinearLayout page,String name,EditText target,int min,int max){
 LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);page.addView(heading);TextView title=sheetText(name,16,false);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView label=sheetText("",16,true);label.setTextColor(0xff58c99a);heading.addView(label);SeekBar bar=new SeekBar(this);styleProfileSlider(bar);bar.setMax(max-min);page.addView(bar);
 EditText exact=new EditText(this);exact.setSingleLine(true);exact.setInputType(8194);exact.setText(target.getText());page.addView(exact);
 final boolean[] busy={false};
 Runnable sync=()->{if(busy[0])return;busy[0]=true;try{double val=number(target);label.setText(String.format(java.util.Locale.GERMANY,"%.2f m",val));bar.setProgress((int)Math.round(val*100)-min);exact.setText(target.getText());}catch(Exception e){label.setText("— m");}busy[0]=false;};
 target.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){sync.run();}public void afterTextChanged(android.text.Editable e){}});
 bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int progress,boolean user){if(!user)return;target.setText(String.format(java.util.Locale.GERMANY,"%.2f",(progress+min)/100.0));refreshProfileDraft();}});
 exact.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){if(busy[0])return;try{double v=number(exact);if(!Double.isFinite(v)||v<min/100.0||v>max/100.0)return;busy[0]=true;target.setText(s);label.setText(s+" m");bar.setProgress((int)Math.round(v*100)-min);busy[0]=false;refreshProfileDraft();}catch(Exception e){busy[0]=false;}}public void afterTextChanged(android.text.Editable e){}});
 sync.run();
 }
 void refreshProfileDraft(){
  if(!projectChosen)return;
  try{
   double bw=number(width),bd=number(depth);
   if(!Double.isFinite(bw)||!Double.isFinite(bd)||bw<=0||bd<=0)return;
   double nl=optional(leftInput),nr=optional(rightInput),nb=optional(baseInput);
   String cover=surface.getSelectedItem().toString();
   double na="Asphalt".equals(cover)?optional(asphaltInput):Double.NaN,nt="Asphalt".equals(cover)?na:isStoneSurface(cover)?optional(stoneDepth)/100:Double.NaN;
   if((Double.isFinite(nt)&&nt>bd)||(Double.isFinite(nb)&&nb+(Double.isFinite(nt)?nt:0)>bd))return;
   w=bw;d=bd;left=nl;right=nr;base=nb;asphalt=na;topThickness=nt;covering=cover;
   if(profile!=null)profile.invalidate();
   if(length.length()>0)calculate();
  }catch(Exception ignored){}
 }
 boolean calculate(){try{double l=number(length),w=number(width),d=number(depth);if(!Double.isFinite(l*w*d)||l<=0||w<=0||d<=0)throw new Exception();
 String covering=surface.getSelectedItem().toString();boolean stoneSelected=isStoneSurface(covering);if(stoneSelected){optional(stoneLength);optional(stoneWidth);}double stoneMetres=stoneSelected?optional(stoneDepth)/100:Double.NaN;
 double base=optional(baseInput),pipe=optional(pipeInput);if(Double.isFinite(pipe)&&(pipe<=0||pipe/1000>w||pipe/1000>d))throw new Exception();
 double asphalt="Asphalt".equals(covering)?optional(asphaltInput):Double.NaN,left=optional(leftInput),right=optional(rightInput);double topLayer=covering.equals("Asphalt")?asphalt:stoneMetres;
 if(Double.isFinite(topLayer)&&topLayer>d)throw new Exception();
 if(Double.isFinite(base)&&base+(Double.isFinite(topLayer)?topLayer:0)>d)throw new Exception();
 this.l=l; this.w=w; this.d=d; this.covering=covering;
 topThickness=topLayer; this.base=base; this.pipe=pipe; this.asphalt=asphalt; this.left=left; this.right=right;
 if(!getPreferences(0).edit().putString("stoneLength",stoneLength.getText().toString()).putString("stoneWidth",stoneWidth.getText().toString()).putString("stoneDepth",stoneDepth.getText().toString()).putString("stoneColor",stoneColor.getText().toString()).putString("pattern",pattern.getText().toString()).putString("bedding",bedding.getText().toString()).putString("underlying",underlying.getText().toString()).putInt("handling",handling.getSelectedItemPosition()).putInt("surface",surface.getSelectedItemPosition()).putString("base",baseInput.getText().toString()).putString("pipe",pipeInput.getText().toString()).putString("asphalt",asphaltInput.getText().toString()).putString("left",leftInput.getText().toString()).putString("right",rightInput.getText().toString()).putString("project",project.getText().toString()).putString("length",length.getText().toString()).putString("width",width.getText().toString()).putString("depth",depth.getText().toString()).commit())throw new Exception("Speichern fehlgeschlagen");
 projectHeader.setText(project.length()>0?project.getText().toString():"Feldaufmaß Pro");
 refreshMainQuantities();StringBuilder details=new StringBuilder("Legende\nOberfläche: "+covering);
 if(stoneSelected&&(stoneLength.length()>0||stoneWidth.length()>0))details.append("\nSteinformat: "+(stoneLength.length()>0?stoneLength.getText():"?")+" × "+(stoneWidth.length()>0?stoneWidth.getText():"?")+" cm");
 if(stoneSelected&&(stoneDepth.length()>0))details.append("\nBelagsstärke: "+stoneDepth.getText()+" cm");
 if(stoneSelected&&(stoneColor.length()>0))details.append("\nFarbe: "+stoneColor.getText());
 if(stoneSelected&&(pattern.length()>0))details.append("\nVerlegemuster: "+pattern.getText());
 if(stoneSelected&&(bedding.length()>0))details.append("\nBettung: "+bedding.getText());
 if(underlying.length()>0)details.append("\nAltbelag darunter: "+underlying.getText());
 if(handling.getSelectedItemPosition()>0)details.append("\n"+handling.getSelectedItem());
 compareSoll();showObjects();refreshStationLayers();refreshDetailQuantities();legend.setText(details.toString());showSurfaceLayers();sketch.invalidate();profile.invalidate();return true;
 }catch(Exception e){Toast.makeText(this,"Maße prüfen. Stand wurde nicht gespeichert.",Toast.LENGTH_SHORT).show();return false;}}
 void detailObject(int index){org.json.JSONObject o=objects.optJSONObject(index);if(o==null)return;LinearLayout form=page();Spinner mode=new Spinner(this);mode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Draufsicht","Querschnitt","Längsschnitt"}));form.addView(mode);if(!overlapSignature(index).isEmpty()){actionButtonSingle(form,overlapAccepted(index)?"Überlappung: Bestätigung zurücknehmen":"Überlappung beibehalten",()->confirmObjectOverlap(index));}EditText a=new EditText(this),b=new EditText(this),h=new EditText(this);EditText[] es={a,b,h};String[] keys={"detailLength","detailWidth","detailDepth"},labels={"Länge (m)","Breite (m)","Tiefe (m)"};for(int j=0;j<3;j++){es[j].setInputType(8194);es[j].setHint(labels[j]);es[j].setText(o.optString(keys[j],""));form.addView(es[j]);}View preview=new View(this){Paint p=new Paint(3);protected void onDraw(Canvas c){float x=getWidth()/2f,y=getHeight()/2f; p.setColor(0xffdce9f4);c.drawRect(x-85,y-45,x+85,y+45,p);p.setColor(0xff203849);p.setStyle(Paint.Style.STROKE);c.drawRect(x-85,y-45,x+85,y+45,p);p.setStyle(Paint.Style.FILL);p.setTextSize(18);c.drawText(mode.getSelectedItem().toString()+" – schematisch",12,24,p);c.drawText((mode.getSelectedItemPosition()==1?b:a).getText()+" m",x-35,y-55,p);c.drawText((mode.getSelectedItemPosition()==0?b:h).getText()+" m",x+90,y,p);}};form.addView(preview,new LinearLayout.LayoutParams(-1,dp(180)));for(EditText e:es)e.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){preview.invalidate();}public void afterTextChanged(android.text.Editable e){}});mode.setSelection(Math.max(0,Math.min(2,o.optInt("detailMode",0))));mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){preview.invalidate();}public void onNothingSelected(android.widget.AdapterView<?> p){}});android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Detailzeichnung – "+o.optString("type")+" / "+o.optString("station")+" m").setView(form).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(btn->{try{for(EditText e:es){if(e.length()>0){double n=number(e);if(!Double.isFinite(n)||n<=0){e.setError("Positives Maß erforderlich");return;}}}for(int j=0;j<3;j++)o.put(keys[j],es[j].getText().toString());o.put("detailMode",mode.getSelectedItemPosition());saveObjects();dialog.dismiss();}catch(Exception e){Toast.makeText(this,"Detail konnte nicht gespeichert werden",Toast.LENGTH_LONG).show();}}));dialog.show();}
 void editObject(int index){org.json.JSONObject o=objects.optJSONObject(index);if(o==null)return;LinearLayout form=page();EditText st=new EditText(this);st.setInputType(8194);st.setHint("Station (m)");st.setText(o.optString("station"));form.addView(st);String[] sides={"Unbekannt","links","rechts","mittig"};Spinner side=choices(form,sides);side.setSelection(Math.max(0,java.util.Arrays.asList(sides).indexOf(o.optString("side"))));EditText offset=new EditText(this);offset.setInputType(8194);offset.setHint("Abstand zum Grabenrand (m, optional)");offset.setText(o.optString("detailOffset",""));form.addView(offset);TextView gapHint=sheetText("Nur links / rechts: 0 = direkt angrenzend; leer = nicht gemessen.",13,false);form.addView(gapHint);EditText note=new EditText(this);note.setHint("Bemerkung");note.setText(o.optString("note"));form.addView(note);android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle(o.optString("type")+" bearbeiten").setView(form).setNeutralButton("Detailzeichnung",(v,j)->detailObject(index)).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{try{double x=number(st),total=l;if(!Double.isFinite(x)||!Double.isFinite(total)||x<0||x>total)throw new Exception();String chosenSide=side.getSelectedItem().toString();double gap=Double.NaN;if(offset.length()>0&&("links".equals(chosenSide)||"rechts".equals(chosenSide))){try{gap=number(offset);}catch(Exception ignored){}if(!Double.isFinite(gap)||gap<0){offset.setError("Abstand ab 0 m eingeben");return;}}o.put("station",x);o.put("side",chosenSide);if(Double.isFinite(gap))o.put("detailOffset",offset.getText().toString());else o.remove("detailOffset");o.put("note",note.getText().toString());saveObjects();dialog.dismiss();}catch(Exception e){st.setError("Station außerhalb der Trasse");}}));dialog.show();}
 double detailNumber(org.json.JSONObject o,String key){try{return Double.parseDouble(o.optString(key).replace(',','.'));}catch(Exception e){return Double.NaN;}}
 double detailExtent(org.json.JSONObject o){try{double v=Double.parseDouble(o.optString("detailLength","").replace(',','.'));return Double.isFinite(v)&&v>0?v:0;}catch(Exception e){return 0;}}
 static double[] lateralInterval(org.json.JSONObject o,double trenchWidth){
  double width=RouteSketch.positiveDimension(o,"detailWidth"),gap=RouteSketch.lateralGap(o);
  if(!Double.isFinite(width))return null;
  String side=o.optString("side");
  if("mittig".equals(side))return new double[]{-width/2,width/2};
  if(!Double.isFinite(gap)||!Double.isFinite(trenchWidth)||trenchWidth<=0)return null;
  double edge=trenchWidth/2+gap;
  if(!Double.isFinite(edge+width))return null;
  if("links".equals(side))return new double[]{edge,edge+width};
  if("rechts".equals(side))return new double[]{-edge-width,-edge};
  return null;
 }
 static boolean potentialObjectOverlap(org.json.JSONObject a,org.json.JSONObject b,double trenchWidth){
  double x=a.optDouble("station"),y=b.optDouble("station");
  if(!Double.isFinite(x)||!Double.isFinite(y))return false;
  double lenA=RouteSketch.positiveDimension(a,"detailLength"),lenB=RouteSketch.positiveDimension(b,"detailLength");
  double distance=Math.abs(x-y);
  if(Double.isFinite(lenA)&&Double.isFinite(lenB)){
   if(distance>=lenA/2+lenB/2)return false;
  }else{
   // A station inside the measured footprint is enough to request a field check.
   double measuredLength=Double.isFinite(lenA)?lenA:lenB;
   boolean insideMeasuredExtent=Double.isFinite(measuredLength)&&distance<=measuredLength/2;
   if(!insideMeasuredExtent&&distance>=.01)return false;
  }
  double[] u=lateralInterval(a,trenchWidth),v=lateralInterval(b,trenchWidth);
  if(u!=null&&v!=null)return Math.min(u[1],v[1])>Math.max(u[0],v[0]);
  String sideA=a.optString("side"),sideB=b.optString("side");
  if(("links".equals(sideA)&&"rechts".equals(sideB))||("rechts".equals(sideA)&&"links".equals(sideB)))return false;
  return true;
 }
 static double overlapArea(org.json.JSONObject a,org.json.JSONObject b,double trenchWidth){
  double x=a.optDouble("station"),y=b.optDouble("station");
  double la=RouteSketch.positiveDimension(a,"detailLength"),lb=RouteSketch.positiveDimension(b,"detailLength");
  double[] u=lateralInterval(a,trenchWidth),v=lateralInterval(b,trenchWidth);
  return CivilGeometry.overlapArea(x,y,la,lb,u,v);
 }
 static String overlapObjectLabel(int index,org.json.JSONObject object){
  String label=String.format(java.util.Locale.GERMANY,"Nr. %d %s (Station %.2f m)",index+1,object.optString("type"),object.optDouble("station"));
  return Double.isFinite(RouteSketch.positiveDimension(object,"detailLength"))?label:label+" [Länge fehlt]";
 }
 String overlapReport(){
  StringBuilder out=new StringBuilder();int count=0;
  for(int i=0;i<objects.length();i++){
   org.json.JSONObject a=objects.optJSONObject(i);if(a==null)continue;
   for(int j=i+1;j<objects.length();j++){
    org.json.JSONObject b=objects.optJSONObject(j);if(b==null||!potentialObjectOverlap(a,b,w))continue;
    count++;
    out.append("\n").append(overlapObjectLabel(i,a)).append(" / ").append(overlapObjectLabel(j,b)).append(" – ");
    double area=overlapArea(a,b,w);
    if(Double.isFinite(area))out.append(String.format(java.util.Locale.GERMANY,"%.2f m²",area));else out.append("Fläche offen (Maße / Abstand fehlen)");
    out.append(overlapAccepted(i)&&overlapAccepted(j)?" · bestätigt":" · prüfen");
   }
  }
  if(count==0)return "\n\nKeine möglichen Objektüberlappungen erkannt.";
  return "\n\nÜberlappungsprüfung – paarweise, entlang der Stationierung (schematisch):"+out+"\nKeine Summierung der Schnittflächen, kein Mengenabzug. An Knicken und bei unterschiedlichen Tiefen separat prüfen.";
 }
 boolean objectOutsideRoute(org.json.JSONObject o){
  double x=o.optDouble("station"),a=detailExtent(o)/2;return !Double.isFinite(x)||x-a<0||x+a>l;
 }
 String overlapSignature(int index){
  org.json.JSONObject o=objects.optJSONObject(index);if(o==null)return "";
  java.util.ArrayList<String> matches=new java.util.ArrayList<>();
  for(int j=0;j<objects.length();j++){
   org.json.JSONObject q=objects.optJSONObject(j);if(j!=index&&q!=null&&potentialObjectOverlap(o,q,w))matches.add(overlapGeometry(q));
  }
  if(matches.isEmpty())return "";
  java.util.Collections.sort(matches);
  try{return new org.json.JSONArray().put(overlapGeometry(o)).put(Double.isFinite(w)?(Object)w:org.json.JSONObject.NULL).put(new org.json.JSONArray(matches)).toString();}catch(RuntimeException e){return "";}
 }
 static String overlapGeometry(org.json.JSONObject o){
  org.json.JSONArray data=new org.json.JSONArray();
  for(String key:new String[]{"station","type","side","detailLength","detailWidth","detailDepth","detailOffset"})data.put(o.optString(key));
  return data.toString();
 }
 boolean overlapAccepted(int index){
  org.json.JSONObject o=objects.optJSONObject(index);if(o==null)return false;
  String accepted=o.optString("acceptedOverlap");return !accepted.isEmpty()&&accepted.equals(overlapSignature(index));
 }
 boolean objectWarning(int index){
  org.json.JSONObject o=objects.optJSONObject(index);if(o==null)return false;
  return objectOutsideRoute(o)||(!overlapSignature(index).isEmpty()&&!overlapAccepted(index));
 }
 String overlapStatus(int index){
  org.json.JSONObject o=objects.optJSONObject(index);if(o==null)return "";
  String status=objectOutsideRoute(o)?" | ⚠ Stationsbereich prüfen":"";
  if(!overlapSignature(index).isEmpty())status+=overlapAccepted(index)?" | Überlappung bestätigt":" | ⚠ Überlappung prüfen";
  return status;
 }
 void confirmObjectOverlap(int index){
  org.json.JSONObject o=objects.optJSONObject(index);if(o==null)return;
  String signature=overlapSignature(index);if(signature.isEmpty()){notice("Keine mögliche Überlappung gefunden.");return;}
  boolean accepted=overlapAccepted(index);
  new android.app.AlertDialog.Builder(this).setTitle(accepted?"Bestätigung zurücknehmen":"Überlappung beibehalten")
   .setMessage(accepted?"Die Überlappung wird wieder als offene Warnung angezeigt.":"Die Geometrie und alle Mengen bleiben unverändert. Die Bestätigung gilt für die aktuelle Lage und Maße dieses Objekts. LV-Regeln separat prüfen.")
   .setNegativeButton("Abbrechen",null).setPositiveButton(accepted?"Zurücknehmen":"Beibehalten",(d,b)->{
    if(objects.optJSONObject(index)!=o||!signature.equals(overlapSignature(index))){notice("Objekt geändert. Bitte erneut prüfen.");return;}
    try{if(accepted)o.remove("acceptedOverlap");else o.put("acceptedOverlap",signature);saveObjects();}
    catch(Exception e){notice("Bestätigung konnte nicht gespeichert werden.");}
   }).show();
 }
 void addObject(){try{double st=number(objectStation),total=l;if(!Double.isFinite(st)||!Double.isFinite(total)||total<=0||st<0||st>total)throw new Exception();org.json.JSONObject o=new org.json.JSONObject();o.put("station",st);o.put("type",objectType.getSelectedItem().toString());o.put("side",objectSide.getSelectedItem().toString());o.put("note",objectNote.getText().toString());objects.put(o);saveObjects();}catch(Exception e){Toast.makeText(this,"Objektstation außerhalb der Trasse oder ungültig.",Toast.LENGTH_LONG).show();}}
 void updateHistoryButtons(){if(undoObjectsButton!=null)undoObjectsButton.setEnabled(!undoObjects.isEmpty());if(redoObjectsButton!=null)redoObjectsButton.setEnabled(!redoObjects.isEmpty());}
 static final int HISTORY_LIMIT = 50;

 void readHistory(String key, java.util.ArrayDeque<String> history) {
  try {
   org.json.JSONArray entries = new org.json.JSONArray(getPreferences(0).getString(key,"[]"));
   for (int i = 0; i < Math.min(entries.length(), HISTORY_LIMIT); i++) {
    String snapshot = entries.getString(i);
    new org.json.JSONArray(snapshot);
    history.addLast(snapshot);
   }
  } catch (org.json.JSONException invalidEntry) {
   // Entries are newest first. Keep only the valid steps before the damaged one.
  }
 }

 void persistObjects() {
  getPreferences(0).edit()
    .putString("objects", objects.toString())
    .putString("objectUndo", new org.json.JSONArray(undoObjects).toString())
    .putString("objectRedo", new org.json.JSONArray(redoObjects).toString())
    .apply();
  showObjects();
  refreshStationLayers();
  refreshDetailQuantities();
  sketch.invalidate();
  updateHistoryButtons();
 }
 void saveObjects(){String old=getPreferences(0).getString("objects","[]");if(!old.equals(objects.toString())){recordProjectEvent("Objekte geändert");undoObjects.push(old);while(undoObjects.size()>HISTORY_LIMIT)undoObjects.removeLast();redoObjects.clear();}persistObjects();}
 void restoreObjectHistory(boolean redo){java.util.ArrayDeque<String> from=redo?redoObjects:undoObjects,to=redo?undoObjects:redoObjects;if(from.isEmpty())return;try{org.json.JSONArray restored=new org.json.JSONArray(from.peek());to.push(objects.toString());from.pop();objects=restored;persistObjects();}catch(org.json.JSONException e){Toast.makeText(this,"Änderung konnte nicht wiederhergestellt werden",Toast.LENGTH_SHORT).show();}}

 void refreshDetailQuantities(){if(detailQuantities==null)return;StringBuilder b=new StringBuilder("\nDetailmengen – geometrisch, nicht addiert\n");for(int i=0;i<objects.length();i++){org.json.JSONObject o=objects.optJSONObject(i);if(o==null)continue;double a=detailNumber(o,"detailLength"),w=detailNumber(o,"detailWidth"),h=detailNumber(o,"detailDepth");b.append(o.optString("type")).append(" / ").append(o.optString("station")).append(" m\n");if(Double.isFinite(a*w)&&a>0&&w>0){b.append(String.format(java.util.Locale.GERMANY,"Fläche: %.2f m²",a*w));if(Double.isFinite(a*w*h)&&h>0)b.append(String.format(java.util.Locale.GERMANY," | Körpervolumen: %.2f m³",a*w*h));else b.append(" | Tiefe fehlt");}else b.append("Detailmaße fehlen");b.append(overlapStatus(i));b.append("\n");}b.append("\nNicht zur Haupttrasse addieren: Überschneidungen und LV-Regeln prüfen.");b.append(overlapReport());detailQuantities.setText(b.toString());}
 String detailSummary(org.json.JSONObject o){StringBuilder b=new StringBuilder();String[] keys={"detailLength","detailWidth","detailDepth"},names={"L","B","T"};for(int j=0;j<3;j++){String v=o.optString(keys[j],"");if(!v.isEmpty())b.append(" | ").append(names[j]).append("=").append(v).append(" m");}return b.toString();}
 void showObjects(){refreshOverview();StringBuilder b=new StringBuilder();for(int i=0;i<objects.length();i++){org.json.JSONObject o=objects.optJSONObject(i);if(o!=null)b.append(String.format(java.util.Locale.GERMANY,"%.2f m | %s | %s | %s\n",o.optDouble("station"),o.optString("type"),o.optString("side"),o.optString("note")+detailSummary(o)+overlapStatus(i)));}objectList.setText(b.toString());}
 void saveSoll(){try{EditText[] fields={sollLength,sollWidth,sollDepth,sollAsphalt};String[] keys={"sollLength","sollWidth","sollDepth","sollAsphalt"};for(EditText e:fields){if(e.length()>0){double n=number(e);if(!Double.isFinite(n)||n<=0)throw new Exception();}}android.content.SharedPreferences.Editor ed=getPreferences(0).edit();for(int i=0;i<fields.length;i++)ed.putString(keys[i],fields[i].getText().toString());ed.putString("sollSource",sollSource.getText().toString()).apply();compareSoll();}catch(Exception e){Toast.makeText(this,"Bitte gültige positive Soll-Maße eingeben.",Toast.LENGTH_LONG).show();}}
 void compareSoll(){if(sollComparison==null||length==null||asphaltInput==null)return;EditText[] actual={length,width,depth,asphaltInput};String[] keys={"sollLength","sollWidth","sollDepth","sollAsphalt"};String[] names={"Trassenlänge","Grabenbreite","Grabentiefe","Asphaltstärke"};StringBuilder b=new StringBuilder("Soll / Ist – Abweichungen (Ist minus Soll)\n");for(int i=0;i<keys.length;i++){b.append(names[i]).append(": ");try{double p=Double.parseDouble(getPreferences(0).getString(keys[i],"").replace(',','.'));double a=number(actual[i]);if(!Double.isFinite(a)||a<=0)throw new Exception();b.append(String.format(java.util.Locale.GERMANY,"Soll %.2f m | Ist %.2f m | Δ %+.2f m",p,a,a-p));}catch(Exception e){b.append("Nicht vollständig erfasst");}b.append("\n");}sollComparison.setText(b.toString());}
 static String csvNumber(double n){return Double.isFinite(n)?String.format(java.util.Locale.GERMANY,"%.3f",n):"";}
 static String csvText(String value){
  String safe=value==null?"":value.replace("\r"," ").replace("\n"," ");
  String trimmed=safe.trim();
  if(!trimmed.isEmpty()&&"=+-@".indexOf(trimmed.charAt(0))>=0)safe="'"+safe;
  return "\""+safe.replace("\"","\"\"")+"\"";
 }
 static void csvRow(java.io.Writer out,String project,String group,String label,double from,double to,double len,double width,double depth,double quantity,String unit,String note)throws java.io.IOException{
  String[] fields={csvText(project),csvText(group),csvText(label),csvNumber(from),csvNumber(to),csvNumber(len),csvNumber(width),csvNumber(depth),csvNumber(quantity),csvText(unit),csvText(note)};
  out.write(String.join(";",fields));out.write("\r\n");
 }
 String rebAnsatzFormula(org.json.JSONObject row)throws Exception{
  String kind=row.optString("kind"),mode=row.optString("mode"),snapshot=row.optString("snapshot");
  double quantity=row.optDouble("quantity"),a,b,h;
  if(!ansatzSourceCurrent(row))throw new java.io.IOException("Mengenansatz: Quelle geändert");
  if("object".equals(kind)){
   org.json.JSONArray o=new org.json.JSONArray(snapshot);
   a=Double.parseDouble(o.optString(3).replace(',','.'));
   if("length".equals(mode))return RebExport.product(a);
   b=Double.parseDouble(o.optString(4).replace(',','.'));
   if("area".equals(mode))return RebExport.product(a,b);
   if("volume".equals(mode)){h=Double.parseDouble(o.optString(5).replace(',','.'));return RebExport.product(a,b,h);}
  }
  if("segment".equals(kind)){
   org.json.JSONObject o=new org.json.JSONObject(snapshot);
   a=o.getDouble("to")-o.getDouble("from");
   if("length".equals(mode))return RebExport.product(a);
   if("area".equals(mode))return RebExport.product(a,o.getDouble("width"));
   if("cut".equals(mode))return RebExport.product(a,o.getDouble("cutWidth"));
  }
  return RebExport.product(quantity);
 }
 String rebData()throws Exception{
  StringBuilder out=new StringBuilder(RebExport.header(projectHeader.getText().toString())).append("\r\n");
  org.json.JSONArray lv=lvPositions();int address=0;
  for(int j=0;j<lv.length();j++){
   org.json.JSONObject o=lv.optJSONObject(j);if(o==null)continue;String oz=o.optString("oz"),source=o.optString("source","manual");
   double qty=lvQuantity(o);if(!Double.isFinite(qty)){
    if("manual".equals(source)&&!o.has("quantity"))continue;
    if("ansatz".equals(source)&&(o.optJSONArray("ansatz")==null||o.optJSONArray("ansatz").length()==0))continue;
    throw new java.io.IOException("Menge fehlt oder ungültig: "+oz);
   }
   if("ansatz".equals(source)){
    org.json.JSONArray rows=o.getJSONArray("ansatz");
    for(int k=0;k<rows.length();k++){
     org.json.JSONObject row=rows.getJSONObject(k);
     if(!Double.isFinite(ansatzQuantity(row,o.optString("unit"))))throw new java.io.IOException("Einheit prüfen: "+oz);
     String formula="count".equals(row.optString("mode"))?RebExport.product(row.getDouble("quantity")):rebAnsatzFormula(row);
     if(!ansatzSourceCurrent(row))throw new java.io.IOException("Quelle geändert: "+oz);
     out.append(RebExport.record(oz,formula,address++)).append("\r\n");
    }
   }else if("area".equals(source)||"volume".equals(source)){
    for(String formula:trenchRebFormulas(l,w,d,new PipeLayoutStore(getPreferences(0)).sections(),"volume".equals(source)))
     out.append(RebExport.record(oz,formula,address++)).append("\r\n");
   }else if("cut".equals(source)){
    for(double[] part:currentCutIntervals())out.append(RebExport.record(oz,RebExport.product(part[0],part[1]),address++)).append("\r\n");
   }else{
    String formula="length".equals(source)?RebExport.product(l):RebExport.product(qty);
    out.append(RebExport.record(oz,formula,address++)).append("\r\n");
   }
  }
  if(address==0)throw new java.io.IOException("Keine abrechenbaren Mengenansätze vorhanden");
  return out.toString();
 }
 String pendingRebData;
 String pendingX31Data;
 void exportX31(){
  if(!calculate()){showPage(measurePage);return;}
  try{
   String xml=GaebX31Export.write(projectHeader.getText().toString(),rebData());
   new android.app.AlertDialog.Builder(this).setTitle("GAEB X31 exportieren – Testformat")
    .setMessage("GAEB DA XML 3.3, REB 23.003 (2009), Formel 91, OZ 00.00.0000. Keine Mengenabzüge. Keine eingebetteten Anlagen. Vor Verwendung mit GAEB-XML-Checker und Ziel-AVA prüfen.")
    .setNegativeButton("Abbrechen",null).setPositiveButton("Testdatei speichern",(d,w)->{
     pendingX31Data=xml;android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);i.addCategory(android.content.Intent.CATEGORY_OPENABLE);i.setType("application/octet-stream");i.putExtra(android.content.Intent.EXTRA_TITLE,"Feldaufmass.x31");startActivityForResult(i,421);
    }).show();
  }catch(Exception e){notice("X31 nicht exportiert: "+e.getMessage());}
 }
 void writeX31(android.net.Uri uri){
  String xml=pendingX31Data;pendingX31Data=null;if(xml==null){notice("Export erneut starten.");return;}
  try(java.io.OutputStream stream=getContentResolver().openOutputStream(uri,"wt")){
   if(stream==null)throw new java.io.IOException();
   stream.write(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));stream.flush();notice("X31-Testdatei gespeichert.");
  }catch(Exception e){notice("X31 konnte nicht gespeichert werden.");}
 }
 void exportReb(){
  if(!calculate()){showPage(measurePage);return;}
  try{
   String data=rebData();
   new android.app.AlertDialog.Builder(this).setTitle("DA11 exportieren – Testformat")
    .setMessage((data.split("\r\n").length-1)+" Rechenansätze · REB 23.003 (2009), Formel 91, OZ 00.00.0000. Positionen ohne manuelle Istmenge werden ausgelassen. Keine Mengenabzüge. Import in Ziel-AVA vor Abrechnung prüfen.")
    .setNegativeButton("Abbrechen",null).setPositiveButton("Testdatei speichern",(d,w)->{
     pendingRebData=data;android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);i.addCategory(android.content.Intent.CATEGORY_OPENABLE);i.setType("application/octet-stream");i.putExtra(android.content.Intent.EXTRA_TITLE,"Feldaufmass.d11");startActivityForResult(i,420);
    }).show();
  }catch(Exception e){notice("DA11 nicht exportiert: "+e.getMessage());}
 }
 void writeReb(android.net.Uri uri){
  String data=pendingRebData;pendingRebData=null;if(data==null){notice("Export erneut starten.");return;}
  try(java.io.OutputStream stream=getContentResolver().openOutputStream(uri,"wt")){
   if(stream==null)throw new java.io.IOException();
   stream.write(data.getBytes(java.nio.charset.StandardCharsets.US_ASCII));stream.flush();notice("DA11-Testdatei gespeichert.");
  }catch(Exception e){notice("DA11 konnte nicht gespeichert werden.");}
 }
 void exportCsv(){
  if(!calculate()){showPage(measurePage);return;}
  new android.app.AlertDialog.Builder(this).setTitle("Mengen als CSV exportieren")
   .setMessage("Excel-kompatible CSV mit Semikolon und Dezimalkomma. Haupttrasse, Belagsabschnitte und Detailmengen sind getrennte Gruppen, nicht addierbar. Keine automatische LV-Zuordnung oder Mengenabzüge.")
   .setNegativeButton("Abbrechen",null).setPositiveButton("Exportieren",(dialog,which)->{
    android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
    i.addCategory(android.content.Intent.CATEGORY_OPENABLE);i.setType("text/csv");i.putExtra(android.content.Intent.EXTRA_TITLE,"Feldaufmass_Mengen.csv");startActivityForResult(i,418);
   }).show();
 }
 void writeCsvData(java.io.Writer out)throws java.io.IOException{
  final double n=Double.NaN;String name=projectHeader.getText().toString();
  out.write('\ufeff');out.write("Projekt;Gruppe;Bezeichnung;Station von (m);Station bis (m);Länge (m);Breite (m);Tiefe (m);Menge;Einheit;Hinweis\r\n");
  double[] trench=currentTrenchQuantities();
  boolean sectional=true;try{sectional=new PipeLayoutStore(getPreferences(0)).sections().length()>0;}catch(Exception ignored){}
  String basis=Double.isFinite(trench[0])?(sectional?"Abschnittsprofile berücksichtigt; keine Abzüge":"Standardprofil; keine Abzüge"):"Abschnittsprofile prüfen – Menge offen";
  String overlap=mainOverlapNotice(new RouteSketch(this,routeData()));if(!overlap.isEmpty())basis+="; "+overlap;
  csvRow(out,name,"Haupttrasse","Aushub – geometrisch",0,l,l,sectional?n:w,sectional?n:d,trench[1],"m³",basis);
  csvRow(out,name,"Haupttrasse","Grundfläche",0,l,l,sectional?n:w,n,trench[0],"m²",basis+"; Bezugsfläche, nicht addieren");
  double cutArea=currentCutArea();
  csvRow(out,name,"Haupttrasse","Fläche mit Nachschnitt",0,l,l,n,n,cutArea,"m²",Double.isFinite(cutArea)?"Abschnittsbreiten berücksichtigt; alternative Bezugsfläche, nicht zusätzlich zur Grundfläche":"Nachschnittmaße oder Abschnittsgrenzen prüfen");
  for(int j=0;j<segments.length();j++){
   org.json.JSONObject o=segments.optJSONObject(j);if(o==null)continue;double a=o.optDouble("from"),b=o.optDouble("to"),width=o.optDouble("width"),len=b-a;
   boolean valid=Double.isFinite(a)&&Double.isFinite(b)&&a>=0&&b<=l&&len>0&&Double.isFinite(width)&&width>0;
   csvRow(out,name,"Belagsabschnitt",o.optString("surface"),a,b,len,width,n,valid?len*width:n,"m²",valid?"Grabenfläche; Teilfläche der Haupttrasse":"Stationsgrenzen / Breite prüfen");
   double cut=o.optDouble("cutWidth");if(Double.isFinite(cut)&&cut>0)csvRow(out,name,"Belagsabschnitt",o.optString("surface")+" mit Nachschnitt",a,b,len,cut,n,valid?len*cut:n,"m²","Alternative Fläche, nicht zusätzlich");
  }
  for(int j=0;j<objects.length();j++){
   org.json.JSONObject o=objects.optJSONObject(j);if(o==null)continue;double st=o.optDouble("station"),a=detailNumber(o,"detailLength"),b=detailNumber(o,"detailWidth"),h=detailNumber(o,"detailDepth");
   String label=(j+1)+" "+o.optString("type"),note="Detailmenge, nicht addieren; "+o.optString("side")+overlapStatus(j)+"; "+o.optString("note");
   boolean area=Double.isFinite(a*b)&&a>0&&b>0;
   csvRow(out,name,"Objektdetail",label+" – Fläche",st,n,a,b,n,area?a*b:n,"m²",note);
   csvRow(out,name,"Objektdetail",label+" – Körpervolumen",st,n,a,b,h,area&&Double.isFinite(a*b*h)&&h>0?a*b*h:n,"m³",note);
  }
  org.json.JSONArray lv=lvPositions();
  for(int j=0;j<lv.length();j++){org.json.JSONObject o=lv.optJSONObject(j);if(o==null)continue;
   csvRow(out,name,"LV-Zuordnung",o.optString("oz")+" "+o.optString("title"),n,n,n,n,n,lvQuantity(o),o.optString("unit"),"Quelle: "+o.optString("source")+"; Soll: "+csvNumber(o.optDouble("target"))+"; LV-Regeln prüfen; nicht zu Geometriemengen addieren");
   org.json.JSONArray rows=o.optJSONArray("ansatz");if("ansatz".equals(o.optString("source"))&&rows!=null)for(int k=0;k<rows.length();k++){org.json.JSONObject row=rows.optJSONObject(k);if(row==null)continue;csvRow(out,name,"LV-Mengenansatz",o.optString("oz")+" "+row.optString("label"),n,n,n,n,n,ansatzQuantity(row,o.optString("unit")),row.optString("unit"),"Teilmenge der LV-Summe; "+(ansatzSourceCurrent(row)?"Messwert-Schnappschuss":"Quelle geändert / prüfen"));}
  }
  out.flush();
 }
 void writeCsv(android.net.Uri uri){
  if(!calculate())return;
  try(java.io.OutputStream stream=getContentResolver().openOutputStream(uri,"wt")){
   if(stream==null)throw new java.io.IOException();
   java.io.Writer writer=new java.io.BufferedWriter(new java.io.OutputStreamWriter(stream,java.nio.charset.StandardCharsets.UTF_8));
   writeCsvData(writer);notice("CSV gespeichert.");
  }catch(Exception e){notice("CSV konnte nicht gespeichert werden.");}
 }
 void exportDxf(){
  RouteSketch route=new RouteSketch(this,routeData());
  if(route.vertices.size()<2){notice("Zuerst eine Trasse zeichnen.");return;}
  boolean metric=DxfExport.metricAvailable(route);
  new android.app.AlertDialog.Builder(this).setTitle("DXF exportieren")
   .setMessage(metric?"Die eingegebenen Abschnittslängen werden in Metern übernommen. Richtungen stammen aus der Skizze, nicht aus einer Vermessung. Lokales Koordinatensystem.":"Abschnittslängen fehlen oder sind ungültig. Export nur als nicht maßstäbliche Skizze möglich.")
   .setNegativeButton("Abbrechen",null).setPositiveButton(metric?"DXF in Metern":"Skizze als DXF",(d,w)->{
    android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
    i.addCategory(android.content.Intent.CATEGORY_OPENABLE);i.setType("application/octet-stream");i.putExtra(android.content.Intent.EXTRA_TITLE,"Feldaufmass.dxf");startActivityForResult(i,metric?416:417);
   }).show();
 }
 void writeDxf(android.net.Uri uri,boolean metric){
  RouteSketch route=new RouteSketch(this,routeData());
  if(route.vertices.size()<2||metric&&!DxfExport.metricAvailable(route)){notice("Trasse geändert oder Maße unvollständig. Export erneut starten.");return;}
  try(java.io.OutputStream stream=getContentResolver().openOutputStream(uri,"wt")){
   if(stream==null)throw new java.io.IOException();
   java.io.Writer writer=new java.io.BufferedWriter(new java.io.OutputStreamWriter(stream,java.nio.charset.StandardCharsets.US_ASCII));
   org.json.JSONArray fittings=new org.json.JSONArray(getPreferences(0).getString("montageFittings","[]"));
   org.json.JSONArray connections=new org.json.JSONArray(getPreferences(0).getString(MontageConnections.KEY,"[]"));
   new DxfExport(writer,route,metric).write(projectHeader.getText().toString(),objects,segments,fittings,connections);
   writer.flush();notice("DXF gespeichert.");
  }catch(Exception e){notice("DXF konnte nicht gespeichert werden.");}
 }
 enum PdfScope {
  DATA("Nur Projektdaten","Projektdaten"), DRAWINGS("Nur Zeichnungen","Zeichnungen"), BOTH("Projektdaten und Zeichnungen","Gesamtprojekt");
  final String label,filename;
  PdfScope(String label,String filename){this.label=label;this.filename=filename;}
 }
 PdfScope pendingPdfScope;
 String pendingPdfProject;
 int pendingPdfLayers=-1;
 void exportPdf(){
  String[] choices={PdfScope.DATA.label,PdfScope.DRAWINGS.label,PdfScope.BOTH.label};
  new android.app.AlertDialog.Builder(this).setTitle("PDF-Inhalt auswählen").setItems(choices,(dialog,index)->{
   PdfScope scope=PdfScope.values()[index];
   // A sketch or a single object can be printed before trench measurements exist.
   if(scope!=PdfScope.DRAWINGS&&length.length()>0&&width.length()>0&&depth.length()>0&&!calculate()){showPage(measurePage);return;}
   if(scope==PdfScope.DATA)choosePdfDestination(scope,-1);else choosePdfDrawingLayers(scope);
  }).setNegativeButton("Abbrechen",null).show();
 }
 void choosePdfDrawingLayers(PdfScope scope){
  final String project=activeProject;
  String[] labels={"Leitungsverlauf / Punkte","Bemaßung","Punktnamen","GPS / Standort","Fotomarker","Objekte","Oberflächen","Oberflächenbemaßung","GPS-Aufzeichnung","GPS-Abweichungen","GPX-Referenz","Schachtscheine / Fremdmedien"};
  int[] masks={RouteSketch.LAYER_ROUTE,RouteSketch.LAYER_DIMENSIONS,RouteSketch.LAYER_NAMES,RouteSketch.LAYER_GPS,RouteSketch.LAYER_PHOTOS,RouteSketch.LAYER_OBJECTS,RouteSketch.LAYER_SURFACES,RouteSketch.LAYER_SURFACE_DIMENSIONS,RouteSketch.LAYER_GPS_TRACK,RouteSketch.LAYER_GPS_DEVIATIONS,RouteSketch.LAYER_GPS_REFERENCE,RouteSketch.LAYER_SCHACHTSCHEINE};
  boolean[] checked=new boolean[labels.length];int current=drawingLayers();
  for(int j=0;j<labels.length;j++)checked[j]=(current&masks[j])!=0;
  new android.app.AlertDialog.Builder(this).setTitle("PDF-Lageplan: Ebenen")
   .setMultiChoiceItems(labels,checked,(dialog,index,on)->checked[index]=on)
   .setNegativeButton("Abbrechen",null)
   .setNeutralButton("Alle Ebenen",(dialog,which)->{if(project.equals(activeProject))choosePdfDestination(scope,RouteSketch.ALL_LAYERS);else notice("Projekt gewechselt.");})
   .setPositiveButton("Weiter",(dialog,which)->{
    if(!project.equals(activeProject)){notice("Projekt gewechselt.");return;}
    int mask=0;for(int j=0;j<checked.length;j++)if(checked[j])mask|=masks[j];
    if(mask==0){notice("Bitte mindestens eine Lageplanebene auswählen.");choosePdfDrawingLayers(scope);return;}
    choosePdfDestination(scope,mask);
   }).show();
 }
 void choosePdfDestination(PdfScope scope,int layers){
  android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
  intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);intent.setType("application/pdf");
  intent.putExtra(android.content.Intent.EXTRA_TITLE,"Feldaufmass_"+scope.filename+".pdf");
  pendingPdfScope=scope;pendingPdfProject=activeProject;pendingPdfLayers=layers;startActivityForResult(intent,412);
 }
 void drawReportView(Canvas canvas, View view, int top, int width, int height) {
  canvas.save();
  canvas.translate(36,top);
  view.layout(0,0,width,height);
  view.draw(canvas);
  canvas.restore();
 }

 int photoCount(RouteSketch route){
  int count=0;for(RouteSketch.Vertex v:route.vertices)count+=v.photos.length();return count;
 }
 void exportPhotoReport(){
  RouteSketch route=new RouteSketch(this,routeData());
  if(photoCount(route)==0){notice("Noch keine Fotos zugeordnet.");return;}
  new android.app.AlertDialog.Builder(this).setTitle("Fotobericht als PDF")
   .setMessage(photoCount(route)+" Fotos im aktuellen Projekt. Der Bericht enthält vorhandene Foto-GPS-Koordinaten und Beschreibungen.")
   .setNegativeButton("Abbrechen",null).setPositiveButton("Exportieren",(d,w)->{
    android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
    i.addCategory(android.content.Intent.CATEGORY_OPENABLE);i.setType("application/pdf");i.putExtra(android.content.Intent.EXTRA_TITLE,"Feldaufmass_Fotobericht.pdf");startActivityForResult(i,422);
   }).show();
 }
 android.graphics.Bitmap reportPhoto(String name){
  if(name==null||!name.matches("[A-Za-z0-9._-]+")||name.equals(".")||name.equals(".."))return null;
  java.io.File file=new java.io.File(getFilesDir(),"photos/"+name);if(!file.isFile())return null;
  android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;
  android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(),options);
  if(options.outWidth<=0||options.outHeight<=0)return null;
  options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>1600)options.inSampleSize*=2;
  options.inJustDecodeBounds=false;
  android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(),options);
  if(bitmap==null)return null;
  try{
   int orientation=new android.media.ExifInterface(file.getAbsolutePath()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);
   android.graphics.Matrix m=new android.graphics.Matrix();
   switch(orientation){
    case 2:m.setScale(-1,1);break;
    case 3:m.setRotate(180);break;
    case 4:m.setScale(1,-1);break;
    case 5:m.setRotate(90);m.postScale(-1,1);break;
    case 6:m.setRotate(90);break;
    case 7:m.setRotate(-90);m.postScale(-1,1);break;
    case 8:m.setRotate(-90);break;
    default:break;
   }
   if(!m.isIdentity()){
    android.graphics.Bitmap rotated=android.graphics.Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,true);
    if(rotated!=bitmap)bitmap.recycle();bitmap=rotated;
   }
  }catch(Exception ignored){}
  return bitmap;
 }
 int appendPhotoPdf(android.graphics.pdf.PdfDocument pdf,int pageNumber,RouteSketch route,String projectName){
  int photoNumber=0,total=photoCount(route);
  Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  android.text.TextPaint tp=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);tp.setTextSize(12);tp.setColor(INK);
  for(int j=0;j<route.vertices.size();j++){
   RouteSketch.Vertex vertex=route.vertices.get(j);
   for(int k=0;k<vertex.photos.length();k++){
    String filename=vertex.photos.optString(k);photoNumber++;
    org.json.JSONObject meta=vertex.photoMetadata.optJSONObject(filename);
    String info=projectName+"\nTrassenpunkt "+(j+1)+(vertex.name.isEmpty()?"":" – "+vertex.name)+"\nAktuelle Station: "+reportMeasure(photoStation(route,j))+"\n"+photoInfo(meta);
    if(meta!=null&&!meta.optString("description").isEmpty())info+="\n\nBeschreibung:\n"+meta.optString("description");
    android.text.StaticLayout layout=android.text.StaticLayout.Builder.obtain(info,0,info.length(),tp,523).build();
    android.graphics.Bitmap bitmap=reportPhoto(filename);
    int line=0;boolean first=true;
    do{
     android.graphics.pdf.PdfDocument.Page page=pdf.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,pageNumber++).create());
     int textY=first?452:76,end=line+1;
     int top=layout.getLineTop(line);
     while(end<layout.getLineCount()&&layout.getLineBottom(end)-top<=780-textY)end++;
     try{
      Canvas c=page.getCanvas();pen.setColor(INK);pen.setTextSize(18);
      c.drawText("Fotodokumentation – Foto "+photoNumber+" / "+total,36,40,pen);
      pen.setTextSize(11);c.drawText("Seite "+(pageNumber-1),500,808,pen);
      if(first){
       if(bitmap!=null){
        float factor=Math.min(523f/bitmap.getWidth(),370f/bitmap.getHeight()),bw=bitmap.getWidth()*factor,bh=bitmap.getHeight()*factor;
        float x=36+(523-bw)/2,y=63+(370-bh)/2;
        c.drawBitmap(bitmap,null,new android.graphics.RectF(x,y,x+bw,y+bh),pen);
       }else{pen.setColor(0xff982f20);c.drawText("Bilddatei fehlt oder konnte nicht gelesen werden.",36,85,pen);pen.setColor(INK);}
      }
      c.save();c.translate(36,textY);c.clipRect(0,0,523,layout.getLineTop(end)-top);c.translate(0,-top);layout.draw(c);c.restore();
     }finally{pdf.finishPage(page);}
     line=end;first=false;
    }while(line<layout.getLineCount());
   }
  }
  return pageNumber;
 }
 void writePhotoReport(android.net.Uri uri){
  RouteSketch route=new RouteSketch(this,routeData());if(photoCount(route)==0){notice("Keine Fotos vorhanden.");return;}
  android.graphics.pdf.PdfDocument pdf=new android.graphics.pdf.PdfDocument();
  try(java.io.OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
   if(out==null)throw new java.io.IOException();
   appendPhotoPdf(pdf,1,route,projectHeader.getText().toString());pdf.writeTo(out);notice("Fotobericht gespeichert.");
  }catch(Exception e){notice("Fotobericht konnte nicht gespeichert werden.");}
  finally{pdf.close();}
 }
 int appendLongitudinalPdf(android.graphics.pdf.PdfDocument pdf,int pageNumber,RouteSketch route){
  double measured=route.measuredLength(),total=Double.isFinite(measured)&&measured>0?measured:l;
  if(!Double.isFinite(total)||total<=0)return pageNumber;
  org.json.JSONArray points=elevationPoints();
  boolean absolute=hasAbsoluteElevations(points,total),relative=false;
  for(int j=0;j<points.length();j++){org.json.JSONObject o=points.optJSONObject(j);if(o!=null&&Double.isFinite(o.optDouble("depth"))&&o.optDouble("depth")>0&&o.optDouble("station")>=0&&o.optDouble("station")<=total)relative=true;}
  boolean standard=!absolute&&!relative&&Double.isFinite(d)&&d>0;
  if(standard){
   points=new org.json.JSONArray();
   try{points.put(new org.json.JSONObject().put("station",0).put("depth",d));points.put(new org.json.JSONObject().put("station",total).put("depth",d));}catch(org.json.JSONException e){throw new IllegalStateException(e);}
  }
  Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);pen.setColor(INK);
  android.graphics.pdf.PdfDocument.Page page=pdf.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,pageNumber++).create());
  try{
   Canvas c=page.getCanvas();pen.setTextSize(19);c.drawText("Längsschnitt",36,40,pen);pen.setTextSize(12);
   c.drawText("Trassenlänge: "+reportMeasure(total),36,65,pen);
   c.drawText(standard?"Relatives Standardprofil – einheitliche Grabentiefe":absolute?"Höhenprofil – gemeinsamer Höhenbezug":"Relatives Tiefenprofil – Oberfläche als Bezug",36,85,pen);
   if(absolute||relative||standard){
    c.save();c.translate(36,105);
    float density=getResources().getDisplayMetrics().density;
    ElevationProfileView view=new ElevationProfileView(points,objects,total,absolute);
    view.layout(0,0,(int)(523*density),(int)(420*density));c.scale(1/density,1/density);view.draw(c);c.restore();
    String[] labels=absolute?new String[]{"OK Gelände","Grabensohle","Rohrsohle"}:new String[]{"Oberfläche (Bezug)","Grabensohle"};
    int[] colors={0xff65717a,0xffa65e19,TEAL};
    for(int k=0;k<labels.length;k++){pen.setColor(colors[k]);pen.setStrokeWidth(3);c.drawLine(36,550+k*20,60,550+k*20,pen);pen.setColor(INK);c.drawText(labels[k],68,554+k*20,pen);}
   }else c.drawText("Keine darstellbaren Höhen- oder Tiefenmesswerte.",36,125,pen);
   pen.setTextSize(11);if(!shoringReport().isEmpty())c.drawText("Verbau: Stationsbänder L/R, keine Höhenlage.",36,630,pen);
   pen.setTextSize(11);c.drawText("Vertikal überhöht / nicht maßstäblich. Keine Extrapolation.",36,740,pen);
   c.drawText("Fehlende Höhenwerte bleiben offen. Vor Abrechnung prüfen.",36,758,pen);
   c.drawText("Seite "+(pageNumber-1),500,805,pen);
  }finally{pdf.finishPage(page);}
  String report=elevationReport();
  android.text.TextPaint tp=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);tp.setTextSize(12);tp.setColor(INK);
  android.text.StaticLayout layout=android.text.StaticLayout.Builder.obtain(report,0,report.length(),tp,523).build();
  int line=0;
  while(line<layout.getLineCount()){
   int top=layout.getLineTop(line),end=line+1;
   while(end<layout.getLineCount()&&layout.getLineBottom(end)-top<=700)end++;
   page=pdf.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,pageNumber++).create());
   try{Canvas c=page.getCanvas();pen.setTextSize(16);c.drawText("Höhenpunkte und Gefälle",36,40,pen);pen.setTextSize(11);c.drawText("Seite "+(pageNumber-1),500,805,pen);c.save();c.translate(36,64);c.clipRect(0,0,523,layout.getLineTop(end)-top);c.translate(0,-top);layout.draw(c);c.restore();}
   finally{pdf.finishPage(page);}
   line=end;
  }
  return pageNumber;
 }
 TextView shoringQuantities;
 String shoringReport(){
  StringBuilder report=new StringBuilder();double leftArea=0,rightArea=0;boolean invalid=false;
  try{
   org.json.JSONArray rows=new org.json.JSONArray(getPreferences(0).getString("metricVerbauSections","[]"));
   if(rows.length()==0)return "";
   report.append("Verbau – Flächenvorberechnung\n");
   java.util.ArrayList<double[]> accepted=new java.util.ArrayList<>();
   for(int i=0;i<rows.length();i++){
    org.json.JSONArray row=rows.getJSONArray(i);double from=row.getDouble(0),to=row.getDouble(1),lh=row.getDouble(2),rh=row.getDouble(3);
    if(!Double.isFinite(from)||!Double.isFinite(to)||!Double.isFinite(lh)||!Double.isFinite(rh)||from<0||to<=from||lh<0||rh<0||!Double.isFinite(l)||l<=0||to>l||lh+rh<=0){invalid=true;report.append("Abschnitt "+(i+1)+": Stationsgrenzen / Maße prüfen\n");continue;}
    boolean overlap=false;for(double[] prior:accepted)if(from<prior[1]&&to>prior[0]&&((lh>0&&prior[2]>0)||(rh>0&&prior[3]>0))){overlap=true;break;}
    if(overlap){invalid=true;report.append("Abschnitt "+(i+1)+": Überlappung auf derselben Seite – nicht summiert\n");continue;}
    double la=(to-from)*lh,ra=(to-from)*rh;if(!Double.isFinite(la+ra+leftArea+rightArea)){invalid=true;report.append("Abschnitt "+(i+1)+": Fläche außerhalb des Zahlenbereichs\n");continue;}
    accepted.add(new double[]{from,to,lh,rh});leftArea+=la;rightArea+=ra;
    report.append(String.format(java.util.Locale.GERMANY,"%.2f – %.2f m | Höhe L/R %.2f / %.2f m | Fläche L/R %.2f / %.2f m²\n",from,to,lh,rh,la,ra));
   }
   if(invalid)report.append("Verbau-Gesamtmenge offen – alle Abschnitte prüfen.\n\n");
   else report.append(String.format(java.util.Locale.GERMANY,"Verbau links: %.2f m² | rechts: %.2f m² | gesamt: %.2f m²\n\n",leftArea,rightArea,leftArea+rightArea));
  }catch(Exception ex){return "Verbaudaten konnten nicht vollständig ausgewertet werden.\n\n";}
  return report.toString();
 }
 static double[] mainTrenchQuantities(double l,double w,double d,org.json.JSONArray profiles)throws org.json.JSONException{
  if(!Double.isFinite(l*w*d)||l<=0||w<=0||d<=0)throw new IllegalArgumentException("Trassenmaße fehlen");
  double volume=l*w*d,area=l*w;
  for(int i=0;i<profiles.length();i++){
   org.json.JSONObject p=profiles.getJSONObject(i);double a=p.getDouble("from"),b=p.getDouble("to"),bw=p.getDouble("width"),bd=p.getDouble("depth");
   if(a<0||b<=a||b>l||bw<=0||bd<=0||!Double.isFinite(a+b+bw+bd))throw new IllegalArgumentException("Abschnitt außerhalb der Trasse oder ungültig");
   for(int j=0;j<i;j++){org.json.JSONObject q=profiles.getJSONObject(j);if(a<q.getDouble("to")&&b>q.getDouble("from"))throw new IllegalArgumentException("Abschnittsprofile überlappen");}
   volume+=(b-a)*(bw*bd-w*d);area+=(b-a)*(bw-w);
  }
  if(!Double.isFinite(volume)||!Double.isFinite(area)||volume<=0||area<=0)throw new IllegalArgumentException("Ungültige Mengen");
  return new double[]{area,volume};
 }
 static java.util.List<String> trenchRebFormulas(double l,double w,double d,org.json.JSONArray profiles,boolean volume)throws Exception{
  mainTrenchQuantities(l,w,d,profiles);
  java.util.ArrayList<org.json.JSONObject> sorted=new java.util.ArrayList<>();
  for(int j=0;j<profiles.length();j++)sorted.add(profiles.getJSONObject(j));
  sorted.sort((a,b)->Double.compare(a.optDouble("from"),b.optDouble("from")));
  java.util.List<String> formulas=new java.util.ArrayList<>();double cursor=0;
  for(org.json.JSONObject p:sorted){
   double from=p.getDouble("from"),to=p.getDouble("to"),bw=p.getDouble("width"),bd=p.getDouble("depth");
   if(from>cursor)formulas.add(volume?RebExport.product(from-cursor,w,d):RebExport.product(from-cursor,w));
   formulas.add(volume?RebExport.product(to-from,bw,bd):RebExport.product(to-from,bw));cursor=to;
  }
  if(cursor<l)formulas.add(volume?RebExport.product(l-cursor,w,d):RebExport.product(l-cursor,w));
  return formulas;
 }
 static java.util.List<double[]> cutIntervals(double l,double w,double d,double left,double right,org.json.JSONArray profiles,org.json.JSONArray surfaces)throws Exception{
  mainTrenchQuantities(l,w,d,profiles);
  java.util.TreeSet<Double> boundaries=new java.util.TreeSet<>();boundaries.add(0.0);boundaries.add(l);
  for(org.json.JSONArray rows:new org.json.JSONArray[]{profiles,surfaces}){
   for(int i=0;i<rows.length();i++){
    org.json.JSONObject row=rows.getJSONObject(i);double a=row.getDouble("from"),b=row.getDouble("to");
    if(!Double.isFinite(a+b)||a<0||b<=a||b>l)throw new IllegalArgumentException("Ungültige Abschnittsgrenzen");
    for(int j=0;j<i;j++){org.json.JSONObject q=rows.getJSONObject(j);if(a<q.getDouble("to")&&b>q.getDouble("from"))throw new IllegalArgumentException("Abschnitte überlappen");}
    boundaries.add(a);boundaries.add(b);
   }
  }
  java.util.List<double[]> parts=new java.util.ArrayList<>();Double previous=null;
  for(double end:boundaries){
   if(previous==null){previous=end;continue;}
   double start=previous,mid=start+(end-start)/2,bw=w,cut=Double.NaN;
   for(int i=0;i<profiles.length();i++){org.json.JSONObject p=profiles.getJSONObject(i);if(mid>=p.getDouble("from")&&mid<p.getDouble("to")){bw=p.getDouble("width");break;}}
   for(int i=0;i<surfaces.length();i++){
    org.json.JSONObject p=surfaces.getJSONObject(i);if(mid<p.getDouble("from")||mid>=p.getDouble("to"))continue;
    if(p.has("cutWidth")){cut=p.getDouble("cutWidth");if(!Double.isFinite(cut)||cut<bw-1e-9)throw new IllegalArgumentException("Nachschnittbreite kleiner als Grabenbreite");}
    break;
   }
   if(!Double.isFinite(cut)){
    if(!Double.isFinite(left)||!Double.isFinite(right)||left<0||right<0)throw new IllegalArgumentException("Nachschnittmaße fehlen");
    cut=bw+left+right;
   }
   if(!Double.isFinite(cut)||cut<=0)throw new IllegalArgumentException("Ungültige Nachschnittbreite");
   parts.add(new double[]{end-start,cut});previous=end;
  }
  return parts;
 }
 java.util.List<double[]> currentCutIntervals()throws Exception{
  return cutIntervals(l,w,d,left,right,new PipeLayoutStore(getPreferences(0)).sections(),segments);
 }
 double currentCutArea(){
  try{double area=0;for(double[] part:currentCutIntervals())area+=part[0]*part[1];return Double.isFinite(area)?area:Double.NaN;}
  catch(Exception e){return Double.NaN;}
 }
 double[] currentTrenchQuantities(){
  try{return mainTrenchQuantities(l,w,d,new PipeLayoutStore(getPreferences(0)).sections());}
  catch(Exception e){return new double[]{Double.NaN,Double.NaN};}
 }
 static StripOverlap.Result mainRouteOverlap(RouteSketch route,double l,double w,double d,org.json.JSONArray profiles)throws Exception{
  if(!DxfExport.metricAvailable(route)||Math.abs(route.measuredLength()-l)>0.001)throw new IllegalArgumentException("Trassenlänge oder Bemaßung prüfen");
  mainTrenchQuantities(l,w,d,profiles);
  DxfExport metric=new DxfExport(new java.io.StringWriter(),route,true);
  java.util.TreeSet<Double> cuts=new java.util.TreeSet<>();cuts.add(0.0);cuts.add(l);
  double station=0;
  for(int i=1;i<route.vertices.size();i++){station+=route.vertices.get(i).metres;if(station>0&&station<l)cuts.add(station);}
  for(int i=0;i<profiles.length();i++){org.json.JSONObject p=profiles.getJSONObject(i);cuts.add(p.getDouble("from"));cuts.add(p.getDouble("to"));}
  if(cuts.size()>301)throw new IllegalArgumentException("Detaillierte Flächenprüfung erforderlich");
  java.util.List<double[]> strips=new java.util.ArrayList<>();Double previous=null;
  for(double end:cuts){
   if(previous==null){previous=end;continue;}
   double start=previous,mid=start+(end-start)/2;previous=end;
   double bw=w;for(int j=0;j<profiles.length();j++){org.json.JSONObject p=profiles.getJSONObject(j);if(mid>=p.getDouble("from")&&mid<p.getDouble("to")){bw=p.getDouble("width");break;}}
   double[] a=metric.station(start),b=metric.station(end);
   if(a==null||b==null)throw new IllegalArgumentException("Station außerhalb der Skizze");
   if(end-start>1e-8)strips.add(new double[]{a[0],a[1],b[0],b[1],bw/2,bw/2});
  }
  return StripOverlap.calculate(strips);
 }
 String mainOverlapNotice(RouteSketch route){
  if(route.vertices.size()<2)return "";
  try{
   StripOverlap.Result r=mainRouteOverlap(route,l,w,d,new PipeLayoutStore(getPreferences(0)).sections());
   if(r.overlap<=0.01)return "";
   return String.format(java.util.Locale.GERMANY,"Überlappungskontrolle: %.2f m² im Streifenmodell der Haupttrasse (auch an Knicken). Bruttomengen unverändert; Netto / Eckergänzungen / Abzweige in Maßstäbliche Vorschau prüfen.",r.overlap);
  }catch(Exception e){return "Überlappungskontrolle Haupttrasse offen – Geometrie / Maße prüfen.";}
 }
 void refreshMainQuantities(){
  if(result==null)return;
  String basic="Trassenmaße fehlen";
  if(l>0&&w>0&&d>0){
   double[] q=currentTrenchQuantities();
   basic=Double.isFinite(q[0])?String.format(java.util.Locale.GERMANY,"Aushub: %.2f m³\nGrundfläche: %.2f m²",q[1],q[0]):"Abschnittsprofile prüfen – Mengen nicht eindeutig";
  }
  double cutArea=currentCutArea();double groundArea=currentTrenchQuantities()[0];if(Double.isFinite(cutArea)&&Double.isFinite(groundArea)&&cutArea>groundArea+1e-9)basic+=String.format(java.util.Locale.GERMANY,"\nFläche mit Nachschnitt: %.2f m² (alternativ)",cutArea);
  if(segments.length()==0&&"Asphalt".equals(covering)&&l>0)basic+="\n"+asphaltCutReport(l,w,Double.isFinite(left)&&Double.isFinite(right)?w+left+right:Double.NaN,left,right);
  RouteSketch route=new RouteSketch(this,routeData());double measured=route.measuredLength();
  String overlap=mainOverlapNotice(route);
  result.setText(basic+routeLengthWarning(measured)+(overlap.isEmpty()?"":"\n"+overlap));
  if(shoringQuantities!=null)shoringQuantities.setText(shoringReport());
 }
 RouteSketch measuredExportRoute(RouteSketch original){
  if(!DxfExport.metricAvailable(original))return null;
  RouteSketch copy=new RouteSketch(this,original.encode());
  if(!copy.rebuildMeasuredGeometry()||!Double.isFinite(copy.uniformMetresPerUnit()))return null;
  copy.setStationLayers(original.stationObjects,original.surfaceSections);copy.setTrenchWidth(w);
  return copy;
 }
 int appendScaledRoutePdf(android.graphics.pdf.PdfDocument pdf,int pageNumber,RouteSketch route){
  RouteSketch measured=measuredExportRoute(route);if(measured==null)return pageNumber;
  route=measured;
  double factor=route.uniformMetresPerUnit();
  double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
  for(RouteSketch.Vertex v:route.vertices){minX=Math.min(minX,v.point.x);minY=Math.min(minY,v.point.y);maxX=Math.max(maxX,v.point.x);maxY=Math.max(maxY,v.point.y);}
  double trenchWidth=Double.isFinite(w)&&w>0?w:0;
  double cutL=Double.isFinite(left)&&left>=0?left:0,cutR=Double.isFinite(right)&&right>=0?right:0;
  org.json.JSONArray widths;try{widths=new org.json.JSONArray(getPreferences(0).getString("metricWidthSections","[]"));}catch(Exception e){widths=new org.json.JSONArray();}
  double margin=(trenchWidth/2+Math.max(cutL,cutR))/factor;
  for(int j=0;j<widths.length();j++){org.json.JSONObject row=widths.optJSONObject(j);if(row==null)continue;double bw=row.optDouble("width"),cl=row.optDouble("left",cutL),cr=row.optDouble("right",cutR);if(Double.isFinite(bw)&&bw>0&&Double.isFinite(cl)&&cl>=0&&Double.isFinite(cr)&&cr>=0)margin=Math.max(margin,(bw/2+Math.max(cl,cr))/factor);}
for(int j=0;j<route.surfaceSections.length();j++){
   org.json.JSONObject row=route.surfaceSections.optJSONObject(j);if(row==null||!"Asphalt".equals(row.optString("surface")))continue;
   double bw=row.optDouble("width");if(!Double.isFinite(bw)||bw<=0)continue;
   double cl=row.optDouble("left",0),cr=row.optDouble("right",0);
   margin=Math.max(margin,(bw/2+Math.max(Double.isFinite(cl)?Math.max(0,cl):0,Double.isFinite(cr)?Math.max(0,cr):0))/factor);
  }
  minX-=margin;maxX+=margin;minY-=margin;maxY+=margin;
  int denominator=0,width=595,height=842,format=4;double unit=0;
  int[][] formats={{595,842},{842,1191},{1191,1684},{1684,2384},{2384,3370}};
  search:for(int candidate:new int[]{100,200,500,1000,2000,5000}){
   double u=factor*1000*72/25.4/candidate;
   for(int f=0;f<formats.length;f++)for(int orientation=0;orientation<2;orientation++){
    int pw=formats[f][orientation],ph=formats[f][1-orientation];
    if((maxX-minX)*u<=pw-80&&(maxY-minY)*u<=ph-240){denominator=candidate;unit=u;width=pw;height=ph;format=4-f;break search;}
   }
  }
  if(denominator==0)return pageNumber;
  android.graphics.pdf.PdfDocument.Page page=pdf.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(width,height,pageNumber++).create());
  try{
   Canvas c=page.getCanvas();Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(INK);p.setTextSize(16);c.drawText("Trassenachse – Maßstab 1:"+denominator,36,40,p);
   p.setTextSize(10);c.drawText("A"+format+" · Druckgröße 100 % · gemessene Längen, skizzierte Richtungen · "+(trenchWidth>0?"Grabenbreite "+reportMeasure(trenchWidth):"nur Trassenachse"),36,58,p);
   if(widths.length()>0){
    double st=0;
    for(int i=1;i<route.vertices.size();i++){
     RouteSketch.Vertex a=route.vertices.get(i-1),b=route.vertices.get(i);double len=b.metres,dx=b.point.x-a.point.x,dy=b.point.y-a.point.y,n=Math.hypot(dx,dy);
     java.util.TreeSet<Double> breaks=new java.util.TreeSet<>();breaks.add(st);breaks.add(st+len);
     for(int j=0;j<widths.length();j++){org.json.JSONObject row=widths.optJSONObject(j);if(row==null)continue;for(double v:new double[]{row.optDouble("from"),row.optDouble("to")})if(v>st&&v<st+len)breaks.add(v);}
     Double previous=null;
     for(double end:breaks){if(previous==null){previous=end;continue;}double start=previous;previous=end;double mid=start+(end-start)/2,bw=trenchWidth,cl=cutL,cr=cutR;
      for(int j=0;j<widths.length();j++){org.json.JSONObject row=widths.optJSONObject(j);if(row!=null&&mid>=row.optDouble("from")&&mid<row.optDouble("to")&&row.optDouble("width")>0){bw=row.optDouble("width");cl=row.optDouble("left",cutL);cr=row.optDouble("right",cutR);break;}}
      if(!Double.isFinite(bw)||bw<=0||n<=0)continue;
      double t1=(start-st)/len,t2=(end-st)/len,ax=40+(a.point.x+dx*t1-minX)*unit,ay=85+(a.point.y+dy*t1-minY)*unit,bx=40+(a.point.x+dx*t2-minX)*unit,by=85+(a.point.y+dy*t2-minY)*unit;
      for(int layer=0;layer<2;layer++){double L=(bw/2+(layer==0?Math.max(0,cl):0))*unit/factor,R=(bw/2+(layer==0?Math.max(0,cr):0))*unit/factor,nx=dy/n,ny=-dx/n;if(!Double.isFinite(L)||!Double.isFinite(R))continue;
       android.graphics.Path shape=new android.graphics.Path();shape.moveTo((float)(ax+nx*L),(float)(ay+ny*L));shape.lineTo((float)(bx+nx*L),(float)(by+ny*L));shape.lineTo((float)(bx-nx*R),(float)(by-ny*R));shape.lineTo((float)(ax-nx*R),(float)(ay-ny*R));shape.close();p.setColor(layer==0?0xffaeb9c2:0xffded4c3);c.drawPath(shape,p);}
     }st+=len;
    }
    p.setColor(INK);p.setTextSize(10);c.drawText("Abschnittsbreiten / Nachschnitt – ohne Eckergänzung",36,72,p);
   }
   if(widths.length()==0&&trenchWidth>0&&(cutL>0||cutR>0)){
    p.setColor(0xffaeb9c2);p.setStyle(Paint.Style.FILL);
    for(int i=1;i<route.vertices.size();i++){
     RouteSketch.Vertex a=route.vertices.get(i-1),b=route.vertices.get(i);
     double dx=b.point.x-a.point.x,dy=b.point.y-a.point.y,n=Math.hypot(dx,dy);
     if(n<=0)continue;
     double nx=dy/n,ny=-dx/n;
     double ax=40+(a.point.x-minX)*unit,ay=85+(a.point.y-minY)*unit,bx=40+(b.point.x-minX)*unit,by=85+(b.point.y-minY)*unit;
     double L=(trenchWidth/2+cutL)*unit/factor,R=(trenchWidth/2+cutR)*unit/factor;
     android.graphics.Path cut=new android.graphics.Path();cut.moveTo((float)(ax+nx*L),(float)(ay+ny*L));cut.lineTo((float)(bx+nx*L),(float)(by+ny*L));cut.lineTo((float)(bx-nx*R),(float)(by-ny*R));cut.lineTo((float)(ax-nx*R),(float)(ay-ny*R));cut.close();c.drawPath(cut,p);
    }
    p.setColor(INK);p.setTextSize(10);c.drawText("Nachschnitt L/R: "+reportMeasure(left)+" / "+reportMeasure(right)+" · ohne Eckergänzung",36,72,p);
   }
   if(widths.length()==0&&trenchWidth>0){
    p.setColor(0xffded4c3);p.setStrokeWidth((float)(trenchWidth/factor*unit));p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.BUTT);p.setStyle(Paint.Style.STROKE);
    android.graphics.Path trench=new android.graphics.Path();
    for(int i=0;i<route.vertices.size();i++){RouteSketch.Vertex v=route.vertices.get(i);float x=(float)(40+(v.point.x-minX)*unit),y=(float)(85+(v.point.y-minY)*unit);if(i==0)trench.moveTo(x,y);else trench.lineTo(x,y);}
    c.drawPath(trench,p);p.setStyle(Paint.Style.FILL);p.setStrokeCap(Paint.Cap.BUTT);
   }
   p.setColor(TEAL);p.setStrokeWidth(1.5f);
   for(int i=1;i<route.vertices.size();i++){RouteSketch.Vertex a=route.vertices.get(i-1),b=route.vertices.get(i);c.drawLine((float)(40+(a.point.x-minX)*unit),(float)(85+(a.point.y-minY)*unit),(float)(40+(b.point.x-minX)*unit),(float)(85+(b.point.y-minY)*unit),p);}
   if(route.surfaceSections.length()>0){
    double station=0;java.util.LinkedHashMap<String,Integer> legend=new java.util.LinkedHashMap<>();
    p.setStrokeWidth(3f);
    for(int i=1;i<route.vertices.size();i++){
     RouteSketch.Vertex a=route.vertices.get(i-1),b=route.vertices.get(i);double len=b.metres,dx=b.point.x-a.point.x,dy=b.point.y-a.point.y;
     if(len<=0)continue;
     for(int j=0;j<route.surfaceSections.length();j++){
      org.json.JSONObject row=route.surfaceSections.optJSONObject(j);if(row==null)continue;
      double from=Math.max(station,row.optDouble("from")),to=Math.min(station+len,row.optDouble("to"));String name=row.optString("surface").trim();
      if(name.isEmpty()||!Double.isFinite(from)||!Double.isFinite(to)||to<=from)continue;
      int color=RouteSketch.surfaceColor(name);legend.put(name,color);p.setColor(color);
      double t1=(from-station)/len,t2=(to-station)/len;
      c.drawLine((float)(40+(a.point.x+dx*t1-minX)*unit),(float)(85+(a.point.y+dy*t1-minY)*unit),(float)(40+(a.point.x+dx*t2-minX)*unit),(float)(85+(a.point.y+dy*t2-minY)*unit),p);
     }station+=len;
    }
    float lx=40,ly=height-95;p.setTextSize(9);
    for(java.util.Map.Entry<String,Integer> entry:legend.entrySet()){
     String label=entry.getKey();float space=p.measureText(label)+28;
     if(lx+space>width-36){lx=40;ly+=12;}if(ly>height-78)break;
     p.setColor(entry.getValue());c.drawRect(lx,ly-7,lx+12,ly+1,p);p.setColor(INK);c.drawText(label,lx+16,ly,p);lx+=space;
    }
   }
   if(trenchWidth>0||widths.length()>0)try{
    org.json.JSONArray rows=new org.json.JSONArray(getPreferences(0).getString("metricVerbauSections","[]"));
    double station=0;
    p.setColor(0xffa65e19);p.setStrokeWidth(.8f);
    for(int i=1;i<route.vertices.size();i++){
     RouteSketch.Vertex a=route.vertices.get(i-1),b=route.vertices.get(i);
     double len=b.metres,dx=b.point.x-a.point.x,dy=b.point.y-a.point.y,n=Math.hypot(dx,dy);
     if(n<=0||len<=0)continue;
     for(int j=0;j<rows.length();j++){
      org.json.JSONArray row=rows.optJSONArray(j);if(row==null)continue;
      double from=Math.max(station,row.optDouble(0)),to=Math.min(station+len,row.optDouble(1));
      if(!Double.isFinite(from)||!Double.isFinite(to)||to<=from)continue;
      for(int side=0;side<2;side++){
       double h=row.optDouble(side+2);if(!Double.isFinite(h)||h<=0)continue;
       java.util.TreeSet<Double> cuts=new java.util.TreeSet<>();cuts.add(from);cuts.add(to);
       for(int k=0;k<widths.length();k++){org.json.JSONObject section=widths.optJSONObject(k);if(section==null)continue;for(double boundary:new double[]{section.optDouble("from"),section.optDouble("to")})if(boundary>from&&boundary<to)cuts.add(boundary);}
       Double previous=null;
       for(double end:cuts){if(previous==null){previous=end;continue;}double start=previous;previous=end;double mid=start+(end-start)/2,bw=trenchWidth;
        for(int k=0;k<widths.length();k++){org.json.JSONObject section=widths.optJSONObject(k);if(section!=null&&mid>=section.optDouble("from")&&mid<section.optDouble("to")&&section.optDouble("width")>0){bw=section.optDouble("width");break;}}
        if(!Double.isFinite(bw)||bw<=0)continue;
        double offset=(side==0?1:-1)*bw/(2*factor),nx=dy/n*offset,ny=-dx/n*offset;
        double t1=(start-station)/len,t2=(end-station)/len;
        c.drawLine((float)(40+(a.point.x+dx*t1+nx-minX)*unit),(float)(85+(a.point.y+dy*t1+ny-minY)*unit),(float)(40+(a.point.x+dx*t2+nx-minX)*unit),(float)(85+(a.point.y+dy*t2+ny-minY)*unit),p);
       }
      }
     }
     station+=len;
    }
    if(rows.length()>0){p.setTextSize(10);c.drawText("Braun: Verbau an Grabenkante (symbolisch)",36,height-65,p);}
   }catch(Exception ignored){}
   if(trenchWidth>0||widths.length()>0){
    java.util.ArrayList<double[]> pdfDepthSections=new java.util.ArrayList<>();
    double pdfDefaultDepth=d;
    boolean pdfDepthValid=true;
    try{
     String defaultDepth=getPreferences(0).getString("metricMainExcavationDepth","").trim();
     if(!defaultDepth.isEmpty()){pdfDefaultDepth=Double.parseDouble(defaultDepth.replace(',','.'));if(!Double.isFinite(pdfDefaultDepth)||pdfDefaultDepth<=0)throw new IllegalArgumentException();}
     for(String line:getPreferences(0).getString("metricDepthSections","").split("\\n")){
      if(line.trim().isEmpty())continue;
      String[] parts=line.trim().split(";",-1);if(parts.length!=3)throw new IllegalArgumentException();
      double[] v=new double[3];for(int k=0;k<3;k++){v[k]=Double.parseDouble(parts[k].trim().replace(',','.'));if(!Double.isFinite(v[k]))throw new IllegalArgumentException();}
      if(v[0]<0||v[1]<=v[0]||v[1]>route.measuredLength()+1e-8||v[2]<=0)throw new IllegalArgumentException();
      for(double[] old:pdfDepthSections)if(v[0]<old[1]&&v[1]>old[0])throw new IllegalArgumentException();
      pdfDepthSections.add(v);
     }
    }catch(Exception e){pdfDepthValid=false;pdfDepthSections.clear();pdfDefaultDepth=Double.NaN;}
    org.json.JSONArray utilities=extraUtilities();
    int conflicts=utilityCollisionCount(utilities,route.measuredLength());
    if(conflicts>0){p.setColor(0xffb3261e);p.setTextSize(9);c.drawText("Profilprüfung: "+conflicts+" mögliche Leitungskollision(en)",36,height-137,p);}
boolean omitted=false;java.util.LinkedHashMap<String,Integer> utilityLegend=new java.util.LinkedHashMap<>();
    for(int j=0;j<utilities.length();j++){
     org.json.JSONObject row=utilities.optJSONObject(j);if(row==null)continue;
     double diameter=row.optDouble("diameter")/1000,offset=row.optDouble("offset");
     if(!Double.isFinite(diameter)||diameter<=0||!Double.isFinite(offset)||offset<diameter/2){omitted=true;continue;}
     String type=row.optString("type");p.setColor(type.equals("Gas")?0xffe9b63c:type.equals("Trinkwasser")?0xff3686bc:0xffc66d56);p.setStrokeWidth((float)(diameter*unit/factor));
     double station=0,lastStation=Double.NaN;float lastX=0,lastY=0;
     for(int i=1;i<route.vertices.size();i++){
      RouteSketch.Vertex a=route.vertices.get(i-1),b=route.vertices.get(i);double len=b.metres,dx=b.point.x-a.point.x,dy=b.point.y-a.point.y,n=Math.hypot(dx,dy);if(n<=0||len<=0)continue;
      java.util.TreeSet<Double> cuts=new java.util.TreeSet<>();cuts.add(station);cuts.add(station+len);
      for(int k=0;k<widths.length();k++){org.json.JSONObject section=widths.optJSONObject(k);if(section==null)continue;for(double v:new double[]{section.optDouble("from"),section.optDouble("to")})if(v>station&&v<station+len)cuts.add(v);}
      for(double[] section:pdfDepthSections)for(int k=0;k<2;k++)if(section[k]>station&&section[k]<station+len)cuts.add(section[k]);
      double routeFrom=row.optDouble("routeFrom",0),routeTo=row.optDouble("routeTo",Double.POSITIVE_INFINITY);
      if(routeFrom>station&&routeFrom<station+len)cuts.add(routeFrom);if(routeTo>station&&routeTo<station+len)cuts.add(routeTo);
      Double previous=null;
      for(double stop:cuts){if(previous==null){previous=stop;continue;}double begin=previous;previous=stop;double mid=(begin+stop)/2,bw=trenchWidth;if(mid<routeFrom||mid>=routeTo)continue;
       for(int k=0;k<widths.length();k++){org.json.JSONObject section=widths.optJSONObject(k);if(section!=null&&mid>=section.optDouble("from")&&mid<section.optDouble("to")&&section.optDouble("width")>0){bw=section.optDouble("width");break;}}
       double localDepth=pdfDefaultDepth;for(double[] section:pdfDepthSections)if(mid>=section[0]&&mid<section[1]){localDepth=section[2];break;}
       if(!Double.isFinite(bw)||bw<=0||utilityOutsideProfile(row,bw,localDepth)){
        omitted=true;
        int previousColor=p.getColor();float previousWidth=p.getStrokeWidth();
        p.setColor(0xffb3261e);p.setStrokeWidth(3);
        double tStart=(begin-station)/len,tEnd=(stop-station)/len;
        c.drawLine((float)(40+(a.point.x+dx*tStart-minX)*unit),(float)(85+(a.point.y+dy*tStart-minY)*unit),(float)(40+(a.point.x+dx*tEnd-minX)*unit),(float)(85+(a.point.y+dy*tEnd-minY)*unit),p);
        p.setColor(previousColor);p.setStrokeWidth(previousWidth);continue;
       }
       double shift=(bw/2-offset)/factor,nx=dy/n*shift,ny=-dx/n*shift,t1=(begin-station)/len,t2=(stop-station)/len;
       utilityLegend.put(type+" · "+String.format(java.util.Locale.GERMANY,"%.0f mm",diameter*1000),p.getColor());
       float x1=(float)(40+(a.point.x+dx*t1+nx-minX)*unit),y1=(float)(85+(a.point.y+dy*t1+ny-minY)*unit),x2=(float)(40+(a.point.x+dx*t2+nx-minX)*unit),y2=(float)(85+(a.point.y+dy*t2+ny-minY)*unit);
       if(Double.isFinite(lastStation)&&Math.abs(lastStation-begin)<1e-7)c.drawLine(lastX,lastY,x1,y1,p);
       c.drawLine(x1,y1,x2,y2,p);lastStation=stop;lastX=x2;lastY=y2;
      }station+=len;
     }
    }
    if(!utilityLegend.isEmpty()){
     p.setTextSize(9);float lx=40,ly=height-124;boolean truncated=false;
     for(java.util.Map.Entry<String,Integer> entry:utilityLegend.entrySet()){
      float space=p.measureText(entry.getKey())+26;
      if(lx+space>width-75){truncated=true;break;}
      p.setColor(entry.getValue());p.setStrokeWidth(3);c.drawLine(lx,ly-3,lx+12,ly-3,p);p.setColor(INK);c.drawText(entry.getKey(),lx+16,ly,p);lx+=space;
     }
     if(truncated){p.setColor(INK);c.drawText("…",width-55,ly,p);}
    }
    if(!pdfDepthValid){p.setColor(0xffb3261e);p.setTextSize(9);c.drawText("Tiefenabschnitte ungültig – Tiefenprüfung nicht möglich",36,height-98,p);}
    if(utilities.length()>0){p.setColor(INK);p.setTextSize(9);c.drawText(omitted?"Zusatzleitungen: ungültige Abschnitte rot markiert; fehlende Maße ggf. ausgelassen":"Zusatzleitungen: Profilversatz ab linker Kante; Verbindungen schematisch",36,height-110,p);}
   }
   // Longitudinal asphalt cuts, separate from the excavation outline.
   Paint asphaltPen=new Paint(Paint.ANTI_ALIAS_FLAG);asphaltPen.setStrokeWidth(.8f);
   boolean originalCuts=false,recutCuts=false;
   java.util.ArrayList<org.json.JSONObject> cutRows=new java.util.ArrayList<>();
   for(int j=0;j<route.surfaceSections.length();j++){org.json.JSONObject item=route.surfaceSections.optJSONObject(j);if(item!=null)cutRows.add(item);}
   cutRows.sort((aa,bb)->Double.compare(aa.optDouble("from"),bb.optDouble("from")));
   float[] lastX=new float[4],lastY=new float[4];double[] lastStation={Double.NaN,Double.NaN,Double.NaN,Double.NaN};
   double previousEnd=Double.NaN;
   for(org.json.JSONObject row:cutRows){
    if(!"Asphalt".equals(row.optString("surface"))){previousEnd=Double.NaN;continue;}
    double from=row.optDouble("from"),to=row.optDouble("to"),bw=row.optDouble("width");
    if(!Double.isFinite(from+to+bw)||from<0||to<=from||to>route.measuredLength()+1e-8||bw<=0)continue;
    double st=0;
    if(!Double.isFinite(previousEnd)||Math.abs(previousEnd-from)>1e-8)java.util.Arrays.fill(lastStation,Double.NaN);
    previousEnd=to;
    for(int side=0;side<2;side++)if(!(row.optDouble(side==0?"left":"right")>0))lastStation[side*2+1]=Double.NaN;
    for(int i=1;i<route.vertices.size();i++){
     RouteSketch.Vertex aa=route.vertices.get(i-1),bb=route.vertices.get(i);
     double len=bb.metres,dx=bb.point.x-aa.point.x,dy=bb.point.y-aa.point.y,n=Math.hypot(dx,dy);
     double lo=Math.max(st,from),hi=Math.min(st+len,to);
     if(hi>lo&&n>0&&len>0){
      for(int side=0;side<2;side++)for(int pass=0;pass<2;pass++){
       double extra=row.optDouble(side==0?"left":"right");
       if(pass==1&&(!Double.isFinite(extra)||extra<=0))continue;
       double offset=(side==0?1:-1)*(bw/2+(pass==1?extra:0))/factor,nx=dy/n*offset,ny=-dx/n*offset;
       double t1=(lo-st)/len,t2=(hi-st)/len;
       asphaltPen.setColor(pass==1?0xffb66b16:0xff465968);
       asphaltPen.setPathEffect(pass==1?new android.graphics.DashPathEffect(new float[]{4,3},0):null);
       float x1=(float)(40+(aa.point.x+dx*t1+nx-minX)*unit),y1=(float)(85+(aa.point.y+dy*t1+ny-minY)*unit);
       float x2=(float)(40+(aa.point.x+dx*t2+nx-minX)*unit),y2=(float)(85+(aa.point.y+dy*t2+ny-minY)*unit);
       int index=side*2+pass;
       if(Double.isFinite(lastStation[index])&&Math.abs(lastStation[index]-lo)<1e-8)c.drawLine(lastX[index],lastY[index],x1,y1,asphaltPen);
       c.drawLine(x1,y1,x2,y2,asphaltPen);lastX[index]=x2;lastY[index]=y2;lastStation[index]=hi;
       if(pass==1)recutCuts=true;else originalCuts=true;
      }
     }
     st+=len;
    }
   }
   if(originalCuts){
    asphaltPen.setPathEffect(null);asphaltPen.setColor(0xff465968);asphaltPen.setTextSize(9);
    c.drawLine(40,height-158,58,height-158,asphaltPen);c.drawText("Asphaltschnitt",64,height-155,asphaltPen);
    if(recutCuts){asphaltPen.setColor(0xffb66b16);asphaltPen.setPathEffect(new android.graphics.DashPathEffect(new float[]{4,3},0));c.drawLine(180,height-158,198,height-158,asphaltPen);asphaltPen.setPathEffect(null);c.drawText("Rückschnitt (zusätzlich)",204,height-155,asphaltPen);}
   }
   // Paper-space labels: never alter the measured geometry or drawing scale.
   Paint dim=new Paint(Paint.ANTI_ALIAS_FLAG);dim.setTextSize(10);dim.setColor(INK);
   java.util.ArrayList<android.graphics.RectF> occupied=new java.util.ArrayList<>();
   int hiddenDimensions=0;
   for(int i=1;i<route.vertices.size();i++){
    RouteSketch.Vertex a=route.vertices.get(i-1),b=route.vertices.get(i);
    if(!Double.isFinite(b.metres)||b.metres<=0)continue;
    float ax=(float)(40+(a.point.x-minX)*unit),ay=(float)(85+(a.point.y-minY)*unit);
    float bx=(float)(40+(b.point.x-minX)*unit),by2=(float)(85+(b.point.y-minY)*unit);
    double n=Math.hypot(bx-ax,by2-ay);if(n<1e-6)continue;
    float nx=(float)((by2-ay)/n),ny=(float)(-(bx-ax)/n),mx=(ax+bx)/2,my=(ay+by2)/2;
    String label=String.format(java.util.Locale.GERMANY,"A%02d · %.2f m",i,b.metres);
    float tw=dim.measureText(label),offset=(float)Math.max(14,margin*unit+12);
    android.graphics.RectF chosen=null;
    for(int k=0;k<8;k++){
     float delta=(k%2==0?1:-1)*(offset+18*(k/2)),cx=mx+nx*delta,cy=my+ny*delta;
     android.graphics.RectF rect=new android.graphics.RectF(cx-tw/2-4,cy-8,cx+tw/2+4,cy+8);
     if(rect.left<30||rect.right>width-30||rect.top<79||rect.bottom>height-150)continue;
     boolean collision=false;for(android.graphics.RectF other:occupied)if(android.graphics.RectF.intersects(other,rect)){collision=true;break;}
     if(!collision){chosen=rect;break;}
    }
    if(chosen==null){hiddenDimensions++;continue;}
    occupied.add(chosen);
    dim.setColor(0xff65717a);dim.setStrokeWidth(.6f);
    c.drawLine(mx,my,chosen.centerX(),chosen.centerY(),dim);
    dim.setColor(android.graphics.Color.WHITE);c.drawRect(chosen,dim);
    dim.setColor(INK);c.drawText(label,chosen.left+4,chosen.centerY()-(dim.ascent()+dim.descent())/2,dim);
   }
   if(hiddenDimensions>0){dim.setColor(INK);dim.setTextSize(9);c.drawText(hiddenDimensions+" Maßangabe(n) aus Platzgründen im Abschnittsplan nachlesen.",36,height-147,dim);}
   p.setColor(INK);p.setStrokeWidth(1);float bar=(float)(50*72/25.4);float by=height-47;c.drawLine(40,by,40+bar,by,p);c.drawLine(40,by-4,40,by+4,p);c.drawLine(40+bar,by-4,40+bar,by+4,p);p.setTextSize(10);c.drawText("50 mm Kontrollmaß",40,by+17,p);
   c.drawText("Entspricht "+reportMeasure(denominator*0.05)+" in der Örtlichkeit",40+bar+20,by+4,p);
   c.drawText("Blatt "+(pageNumber-1),width-85,height-30,p);
  }finally{pdf.finishPage(page);}
  return pageNumber;
 }
 String surfaceVolumeReport()throws Exception{
  if(segments.length()==0)return "Keine Oberflächenabschnitte vorhanden.";
   StringBuilder report=new StringBuilder("Vorberechnung – abschnittsbezogene Schichtstärken\n\n");
   java.util.TreeMap<String,Double> totals=new java.util.TreeMap<>();
   for(int i=0;i<segments.length();i++){
    org.json.JSONObject row=segments.getJSONObject(i);
    double from=row.getDouble("from"),to=row.getDouble("to"),bw=row.getDouble("width");
    if(!Double.isFinite(from+to+bw)||from<0||to<=from||bw<=0||(!Double.isFinite(l)||l<=0||to>l))throw new IllegalArgumentException("Ungültige Abschnittsmaße");
    for(int j=0;j<i;j++){org.json.JSONObject prev=segments.getJSONObject(j);if(from<prev.getDouble("to")&&to>prev.getDouble("from"))throw new IllegalArgumentException("Überlappende Abschnitte");}
    String type=row.optString("surface","Unbekannt");
    double cut=row.optDouble("cutWidth",bw);
    if(!Double.isFinite(cut)||cut<bw)throw new IllegalArgumentException("Ungültige Nachschnittbreite");
    report.append(String.format(java.util.Locale.GERMANY,"%.2f – %.2f m | %s\n",from,to,type));
    boolean asphaltLayer=type.equals("Asphalt"),stoneLayer=isStoneSurface(type);
    boolean sameMaterial=type.equals(covering);
    if(asphaltLayer||stoneLayer){
     double thickness=row.has("topThickness")?row.optDouble("topThickness"):sameMaterial?topThickness:Double.NaN;
     if(Double.isFinite(thickness)&&thickness>0){
      double volume=CivilGeometry.rectangularVolume(to-from,cut,thickness);
      totals.put(type,totals.getOrDefault(type,0.0)+volume);
      report.append(String.format(java.util.Locale.GERMANY,"Deckschicht: %.3f m³%s%s\n",volume,row.has("cutWidth")?"":" (nur Graben; Nachschnitt unbekannt)",row.has("topThickness")?"":" · Standardprofil"));
     }else report.append("Deckschichtstärke fehlt\n");
    }else report.append("Deckschicht nicht berechnet\n");
    double baseThickness=row.has("baseThickness")?row.optDouble("baseThickness"):sameMaterial?base:Double.NaN;
    if(Double.isFinite(baseThickness)&&baseThickness>0&&(asphaltLayer||stoneLayer)){
     double volume=CivilGeometry.rectangularVolume(to-from,bw,baseThickness);totals.put("Tragschicht",totals.getOrDefault("Tragschicht",0.0)+volume);
     report.append(String.format(java.util.Locale.GERMANY,"Tragschicht (Grabenbreite): %.3f m³\n",volume));
    }else report.append("Tragschicht nicht berechnet\n");
   }
   report.append("\nTeilsummen (nur erfasste Schichten)\n");
   for(java.util.Map.Entry<String,Double> entry:totals.entrySet())report.append(String.format(java.util.Locale.GERMANY,"%s: %.3f m³\n",entry.getKey(),entry.getValue()));
   report.append("\nStandardstärken nur bei gleicher Oberfläche. Fehlende Schichten bleiben offen. Bettung und Altbelag nicht berechnet. Nicht zusätzlich zum Bruttoaushub addieren.");
  return report.toString();
 }
 // Geometric longitudinal cutting estimate; actual cuts and transverse ends are not inferred.
 static String asphaltCutReport(double length,double width,double cutWidth,double left,double right){
  if(!Double.isFinite(length)||length<=0)return "";
  StringBuilder out=new StringBuilder(String.format(java.util.Locale.GERMANY,"Asphaltschnitt längs (beidseitig, rechnerisch): %.2f lfm\n",2*length));
  boolean leftKnown=Double.isFinite(left)&&left>=0,rightKnown=Double.isFinite(right)&&right>=0;
  if(Double.isFinite(width)&&Double.isFinite(cutWidth)&&Math.abs(cutWidth-width)<1e-9){
   if(!leftKnown&&!rightKnown)return out.toString();
  }
  int sides=(leftKnown&&left>0?1:0)+(rightKnown&&right>0?1:0);
  if(sides>0)out.append(String.format(java.util.Locale.GERMANY,"Zusätzlicher Asphaltschnitt (Rückschnitt%s): %.2f lfm\n",leftKnown&&rightKnown?"":"; bekannte Seiten",length*sides));
  if(!leftKnown||!rightKnown)out.append("Weitere Rückschnitt-Schnittlänge offen – Seitenmaße prüfen.\n");
  return out.toString();
 }
 String surfaceAreaReport()throws Exception{
  java.util.TreeMap<String,double[]> totals=new java.util.TreeMap<>();
   StringBuilder report=new StringBuilder();double covered=0;
   for(int i=0;i<segments.length();i++){
    org.json.JSONObject row=segments.getJSONObject(i);
    double from=row.getDouble("from"),to=row.getDouble("to"),bw=row.getDouble("width");
    if(!Double.isFinite(from+to+bw)||from<0||to<=from||bw<=0||(!Double.isFinite(l)||l<=0||to>l))throw new IllegalArgumentException("Ungültige Abschnittsmaße");
    for(int j=0;j<i;j++){org.json.JSONObject prev=segments.getJSONObject(j);if(from<prev.getDouble("to")&&to>prev.getDouble("from"))throw new IllegalArgumentException("Überlappende Oberflächenabschnitte");}
    String type=row.optString("surface","Unbekannt");double len=to-from,area=len*bw;covered+=len;
    double[] sum=totals.computeIfAbsent(type,k->new double[3]);sum[0]+=area;
    report.append(String.format(java.util.Locale.GERMANY,"%.2f – %.2f m | %s\nAufbruch (Graben): %.3f m²\n",from,to,type,area));
    if("Asphalt".equals(type))report.append(asphaltCutReport(len,bw,row.optDouble("cutWidth"),row.optDouble("left"),row.optDouble("right")));
    if(row.has("cutWidth")){
     double cut=row.getDouble("cutWidth");if(!Double.isFinite(cut)||cut<bw)throw new IllegalArgumentException("Ungültige Nachschnittbreite");
     double extra=len*(cut-bw);sum[1]+=extra;
     if(extra>1e-9)report.append(String.format(java.util.Locale.GERMANY,"Nachschnitt zusätzlich: %.3f m² | Gesamt: %.3f m²\n",extra,area+extra));
    }else{sum[2]+=len;report.append("Nachschnitt nicht erfasst\n");}
   }
   report.append("\nSummen nach Oberfläche\n");
   for(java.util.Map.Entry<String,double[]> entry:totals.entrySet()){double[] v=entry.getValue();report.append(String.format(java.util.Locale.GERMANY,"%s: %.3f m² Graben + %.3f m² Nachschnitt%s\n",entry.getKey(),v[0],v[1],v[2]>0?" (unvollständig)":""));}
   if(l>0)report.append(String.format(java.util.Locale.GERMANY,"\nNicht erfasste Trassenlänge: %.2f m",Math.max(0,l-covered)));
  return report.toString();
 }
 String surfaceReportForExport(){
  if(segments.length()==0)return "";
  StringBuilder report=new StringBuilder("\n\nOberflächenmengen – abschnittsbezogen\n");
  try{report.append(surfaceAreaReport());}catch(Exception e){report.append("Mengen offen – Abschnittsgrenzen / Maße prüfen.");}
  report.append("\n\nSchichtvolumen – Vorberechnung\n");
  try{report.append(surfaceVolumeReport());}catch(Exception e){report.append("Schichtmengen offen – Abschnittsmaße prüfen.");}
  return report.toString();
 }
 void writePdf(android.net.Uri uri){writePdf(uri,PdfScope.BOTH);}
 void writePdf(android.net.Uri uri,PdfScope scope){writePdf(uri,scope,-1);}
 void writePdf(android.net.Uri uri,PdfScope scope,int layers){
  android.graphics.pdf.PdfDocument pdf=new android.graphics.pdf.PdfDocument();
  try{
   RouteSketch route=new RouteSketch(this,routeData());route.setStationLayers(objects,segments);route.setTrenchWidth(w);
   int next=1;
   if(scope!=PdfScope.DATA){
    route.printPlan=true;route.bindDimensionProject(activeProject);route.setVisibleLayers(layers<0?drawingLayers():layers);loadUtilitySheets(route);
    route.setIndependentGpsTrack(gpsTrack());route.setImportedGpsReference(importedGpsReference());
    SharedPreferences prefs=getPreferences(0);
    if(prefs.getInt("routeUnderlayHidden",0)==0){
     route.setUnderlay(reportPhoto(prefs.getString("routeUnderlay","")));
     route.setUnderlayAlpha(prefs.getInt("routeUnderlayAlpha",150));
     org.json.JSONArray transform=new org.json.JSONArray(prefs.getString("routeUnderlayTransform","[]"));
     route.setUnderlayTransform((float)transform.optDouble(0,0),(float)transform.optDouble(1,0),(float)transform.optDouble(2,1),(float)transform.optDouble(3,0));
    }
    next=new ProjectPlanPdf(this).append(pdf,next,route);
    next=appendScaledRoutePdf(pdf,next,route);
    org.json.JSONArray fittings=new org.json.JSONArray(prefs.getString("montageFittings","[]"));
    if(fittings.length()>0&&DxfExport.metricAvailable(route)&&Math.abs(route.measuredLength()-l)<.001){
     appendMontageDrawing(pdf,next++,route,fittings,l);
    }
    if(route.vertices.size()>1||l>0)next=appendLongitudinalPdf(pdf,next,route);
   }
   if(scope!=PdfScope.DRAWINGS){
    next=appendProjectInformation(pdf,next,route);
    org.json.JSONArray daily=dailyReports();
    for(int j=0;j<daily.length();j++)next=appendDailyReport(pdf,next,daily.getJSONObject(j));
    org.json.JSONArray changes=nachtraege();
    for(int j=0;j<changes.length();j++)next=appendNachtrag(pdf,next,changes.getJSONObject(j));
    next=new MontageProtocols(this).appendPages(pdf,next);
    next=new MontageConnections(this).appendPages(pdf,next);
    appendPhotoPdf(pdf,next,route,projectHeader.getText().toString());
   }
   try(java.io.OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
    if(out==null)throw new java.io.IOException();pdf.writeTo(out);
   }
   notice("PDF gespeichert.");
  }catch(Exception e){notice("PDF konnte nicht gespeichert werden.");}finally{pdf.close();}
 }
 int appendProjectInformation(android.graphics.pdf.PdfDocument pdf,int next,RouteSketch route)throws Exception{
  String report=(route.vertices.size()>1?"Trassenskizze – Abschnittsmaße\n"+route.measurements()+"\n\n":"")+"Oberflächenaufbau\n"+surfaceLayers.getText()+"\n\n"+result.getText()+"\n"+legend.getText()+"\n"+
    ((left==0&&right==0)?"":"Nachschnitt links: "+reportMeasure(left)+" | rechts: "+reportMeasure(right)+"\n")+
    "Asphaltstärke: "+reportMeasure(asphalt)+"\nTragschicht: "+reportMeasure(base)+"\n\n"+
    "Trassenobjekte\n"+objectList.getText()+detailQuantities.getText()+"\n\n"+segmentsView.getText()+"\n\n"+shoringReport()+surfaceReportForExport();
  StringBuilder body=new StringBuilder(projectHeader.getText()).append("\n\n").append(report);
  for(RouteSketch.SingleObject o:route.singleObjects)body.append("\nEinzelobjekt · ").append(o.type).append(" · ohne Station");
  for(RouteSketch.Vertex v:route.vertices){
   if(!v.name.isEmpty()||Double.isFinite(v.latitude))body.append("\nPunkt ").append(route.vertices.indexOf(v)+1).append(" · ").append(v.name);
   if(Double.isFinite(v.latitude))body.append(" · GPS ").append(v.latitude).append(", ").append(v.longitude);
  }
  org.json.JSONArray fittings=new org.json.JSONArray(getPreferences(0).getString("montageFittings","[]"));
  for(int i=0;i<fittings.length();i++)body.append("\nFormstück · ").append(planFittingLabel(fittings.getJSONObject(i),i,null));
  String materials=projectMaterialText(getPreferences(0));if(!materials.isEmpty())body.append("\n\nMaterialliste\n").append(materials);
  org.json.JSONArray lv=lvPositions();for(int i=0;i<lv.length();i++){
   org.json.JSONObject row=lv.getJSONObject(i);body.append("\nLV · ").append(lvLabel(row)).append(" · Ist: ").append(csvNumber(lvQuantity(row))).append(" · Soll: ").append(csvNumber(row.optDouble("target"))).append(asphaltLvBreakdown(row));
  }
  org.json.JSONArray sheets=utilitySheets();for(int i=0;i<sheets.length();i++)body.append("\nSchachtschein · ").append(sheets.getJSONObject(i).optString("name"));
  for(int i=0;i<documents.length();i++)body.append("\nUnterlage · ").append(documents.getJSONObject(i).optString("name"));
  if(elevationPoints().length()>0)body.append("\n\n").append(elevationReport());
  return dailyTextPdf(pdf,next,"Projektdaten und Mengen",body.toString(),null);
 }
 String reportMeasure(double value){
  return Double.isFinite(value)?String.format(java.util.Locale.GERMANY,"%.2f m",value):"nicht erfasst";
 }
 static final String[] DOC_KEYS={"sollLength","sollWidth","sollDepth","sollAsphalt"};
 static final String[] DOC_LABELS={"Trassenlänge","Grabenbreite","Grabentiefe","Asphaltstärke"};
 static final String[] DOC_PATTERNS={"(?:trassenlänge|leitungslänge|gesamtlänge\\s+der\\s+trasse)","(?:grabenbreite|rohrgrabenbreite)","(?:grabentiefe|rohrgrabentiefe)","(?:asphaltstärke|asphaltdicke|asphalt\\s*[- ]?dicke)"};
 static final class DocMeasure{
  final int field,page;final double metres;final String evidence;
  DocMeasure(int field,int page,double metres,String evidence){this.field=field;this.page=page;this.metres=metres;this.evidence=evidence;}
 }
 static java.util.ArrayList<DocMeasure> findDocumentMeasures(String text,int page){
  java.util.ArrayList<DocMeasure> result=new java.util.ArrayList<>();
  for(int j=0;j<DOC_PATTERNS.length;j++){
   java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?iu)\\b"+DOC_PATTERNS[j]+"\\s*(?:\\(soll\\))?\\s*[:=]?\\s*(?:beträgt\\s+|ist\\s+)?(\\d{1,6}(?:[.,]\\d{1,6})?)\\s*(mm|cm|m|millimeter|zentimeter|meter)(?![\\p{L}\\p{N}²³])").matcher(text);
   while(m.find()&&result.size()<100){double value=Double.parseDouble(m.group(1).replace(',','.'));String unit=m.group(2).toLowerCase(java.util.Locale.ROOT);
    if(unit.equals("mm")||unit.equals("millimeter"))value/=1000;else if(unit.equals("cm")||unit.equals("zentimeter"))value/=100;
    if(Double.isFinite(value)&&value>0)result.add(new DocMeasure(j,page,value,m.group().replaceAll("\\s+"," ")));
   }
  }return result;
 }
 void chooseImageAnalysis(){
  RouteSketch route=new RouteSketch(this,routeData());
  java.util.ArrayList<String> files=new java.util.ArrayList<>(),labels=new java.util.ArrayList<>();
  labels.add("Bilddatei / Handskizze auswählen");
  for(int j=0;j<route.vertices.size();j++){RouteSketch.Vertex v=route.vertices.get(j);for(int k=0;k<v.photos.length();k++){files.add(v.photos.optString(k));labels.add("Punkt "+(j+1)+" · Foto "+(k+1)+(v.name.isEmpty()?"":" · "+v.name));}}
  new android.app.AlertDialog.Builder(this).setTitle("Foto / Handskizze analysieren").setItems(labels.toArray(new String[0]),(d,w)->{
   if(w==0){android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);i.addCategory(android.content.Intent.CATEGORY_OPENABLE);i.setType("image/*");startActivityForResult(i,426);}
   else analyzeImageDocument(photoUri(files.get(w-1)),labels.get(w));
  }).setNegativeButton("Abbrechen",null).show();
 }
 android.graphics.Bitmap imageForOcr(android.net.Uri uri)throws Exception{
  if(android.os.Build.VERSION.SDK_INT>=28){
   return android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(getContentResolver(),uri),(decoder,info,source)->{
    int w=info.getSize().getWidth(),h=info.getSize().getHeight();float scale=Math.min(1f,2600f/Math.max(w,h));
    decoder.setTargetSize(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)));decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
   });
  }
  android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;
  try(java.io.InputStream in=getContentResolver().openInputStream(uri)){android.graphics.BitmapFactory.decodeStream(in,null,options);}
  if(options.outWidth<=0||options.outHeight<=0)throw new java.io.IOException("Bild nicht lesbar");
  options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>2600)options.inSampleSize*=2;options.inJustDecodeBounds=false;
  try(java.io.InputStream in=getContentResolver().openInputStream(uri)){return android.graphics.BitmapFactory.decodeStream(in,null,options);}
 }
 void analyzeImageDocument(android.net.Uri uri,String name){
  String projectId=activeProject;notice("Bildtext wird lokal erkannt …");
  new Thread(()->{
   android.graphics.Bitmap bitmap=null;com.google.mlkit.vision.text.TextRecognizer recognizer=null;
   try{
    bitmap=imageForOcr(uri);if(bitmap==null)throw new java.io.IOException("Bild nicht lesbar");
    recognizer=com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS);
    String raw=com.google.android.gms.tasks.Tasks.await(recognizer.process(com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap,0))).getText();
    final String recognized=raw.length()>50000?raw.substring(0,50000):raw;
    runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()&&projectId.equals(activeProject))reviewImageText(name,recognized);});
   }catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())notice("Bildanalyse fehlgeschlagen: "+e.getMessage());});}
   finally{if(recognizer!=null)recognizer.close();if(bitmap!=null)bitmap.recycle();}
  },"image-ocr").start();
 }
 void reviewImageText(String name,String recognized){
  LinearLayout form=page();ScrollView scroll=new ScrollView(this);scroll.addView(form);
  form.addView(sheetText(name+"\nOCR – Handschrift kann falsch oder unvollständig erkannt werden. Bitte mit dem Original vergleichen.",14,true));
  EditText content=new EditText(this);content.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);content.setMinLines(5);content.setText(recognized);form.addView(content);
  form.addView(sheetText("Fehlende Bezeichnungen ergänzen, z. B. Grabenbreite 0,92 m. Einzelne unbeschriftete Zahlen werden nicht automatisch zugeordnet.",14,false));
  new android.app.AlertDialog.Builder(this).setTitle("Erkannten Bildtext prüfen").setView(scroll).setNegativeButton("Abbrechen",null).setNeutralButton("Formstücke suchen",(d,w)->reviewFittingText(name,content.getText().toString())).setPositiveButton("Maße suchen",(d,w)->{
   java.util.ArrayList<DocMeasure> matches=new java.util.ArrayList<>();
   for(DocMeasure m:findDocumentMeasures(content.getText().toString(),1))matches.add(new DocMeasure(m.field,1,m.metres,"OCR / geprüfter Bildtext: "+m.evidence));
   previewDocumentMeasures(name,"Bild / Handskizze · OCR-Text",matches);
  }).show();
 }
 void reviewFittingText(String source,String text){
  java.util.List<FittingTextParser.Candidate> matches=FittingTextParser.parse(text);
  if(matches.isEmpty()){new android.app.AlertDialog.Builder(this).setTitle("Keine eindeutige Bezeichnung gefunden").setMessage("Eine Bezeichnung je Zeile verwenden, z. B. Bogen 45° DN 80, Station 12,5 m.")
    .setPositiveButton("Text prüfen",(d,w)->reviewImageText(source,text)).setNegativeButton("Schließen",null).show();return;}
  reviewPendingFittings(source,text,matches,matches.size(),activeProject);
 }
 void reviewPendingFittings(String source,String text,java.util.List<FittingTextParser.Candidate> pending,int total,String projectId){
  if(!projectId.equals(activeProject)){notice("Projekt wurde gewechselt.");return;}
  if(pending.isEmpty()){showMontageInventory();return;}
  String[] labels=new String[pending.size()];
  for(int i=0;i<labels.length;i++){
   FittingTextParser.Candidate candidate=pending.get(i);
   labels[i]="Zeile "+candidate.line+" · "+candidate.evidence;
  }
  android.app.AlertDialog.Builder menu=new android.app.AlertDialog.Builder(this)
    .setTitle("Formstücke prüfen · "+pending.size()+" von "+total+" offen")
    .setItems(labels,(d,i)->{
     FittingTextParser.Candidate candidate=pending.get(i);
     showMontageFitting(candidate.type,null,source,candidate,saved->{
      if(saved)pending.remove(candidate);
      reviewPendingFittings(source,text,pending,total,projectId);
     });
    }).setPositiveButton("Fertig",(d,w)->showMontageInventory());
  if(pending.size()==total)menu.setNegativeButton("Text prüfen",(d,w)->reviewImageText(source,text));
  menu.show();
 }
 void choosePdfAnalysis(){
  java.util.ArrayList<org.json.JSONObject> pdfs=new java.util.ArrayList<>();java.util.ArrayList<String> labels=new java.util.ArrayList<>();
  for(int j=0;j<documents.length();j++){org.json.JSONObject o=documents.optJSONObject(j);if(o==null||o.optString("uri").isEmpty())continue;
   if(o.optString("name").toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")){pdfs.add(o);labels.add(o.optString("name"));}
  }
  labels.add("Andere PDF-Datei auswählen");
  new android.app.AlertDialog.Builder(this).setTitle("PDF-Unterlage analysieren").setItems(labels.toArray(new String[0]),(d,w)->{
   if(w<pdfs.size()){org.json.JSONObject o=pdfs.get(w);analyzePdfDocument(android.net.Uri.parse(o.optString("uri")),o.optString("name"));}
   else{android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);i.addCategory(android.content.Intent.CATEGORY_OPENABLE);i.setType("application/pdf");startActivityForResult(i,425);}
  }).show();
 }
 void analyzePdfDocument(android.net.Uri uri,String name){
  new android.app.AlertDialog.Builder(this).setTitle("PDF auswerten")
   .setItems(new String[]{"Automatisch – Text und OCR bei Bedarf","OCR – alle ausgewählten Seiten"},(d,w)->analyzePdfWithOcr(uri,name,w==1))
   .setNegativeButton("Abbrechen",null).show();
 }
 void analyzePdfWithOcr(android.net.Uri uri,String name,boolean forceOcr){
  String projectId=activeProject;notice("PDF wird lokal ausgewertet …");
  new Thread(()->{
   java.util.ArrayList<DocMeasure> results=new java.util.ArrayList<>();
   int pages=0,read=0,chars=0,ocrPages=0,ocrFailed=0;boolean limited=false;
   com.google.mlkit.vision.text.TextRecognizer recognizer=null;
   try{
    android.os.ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"r");
    if(fd==null)throw new java.io.IOException("Datei nicht lesbar");
    android.graphics.pdf.PdfRenderer renderer;
    try{renderer=new android.graphics.pdf.PdfRenderer(fd);}catch(Exception e){fd.close();throw e;}
    try(android.graphics.pdf.PdfRenderer owned=renderer){
     pages=renderer.getPageCount();int max=Math.min(pages,100);
     for(int j=0;j<max;j++){
      if(isDestroyed()||isFinishing())return;
      if(chars>=500000||results.size()>=200){limited=true;break;}
      try(android.graphics.pdf.PdfRenderer.Page page=renderer.openPage(j)){
       StringBuilder nativeText=new StringBuilder();
       if(!forceOcr&&android.os.Build.VERSION.SDK_INT>=35){
        try{for(android.graphics.pdf.content.PdfPageTextContent part:page.getTextContents()){if(nativeText.length()>=100000)break;nativeText.append(part.getText()).append("\n");}}catch(Exception ignored){}
       }
       java.util.ArrayList<DocMeasure> matches=findDocumentMeasures(nativeText.toString(),j+1);
       chars+=nativeText.length();boolean hasText=!nativeText.toString().trim().isEmpty();
       results.addAll(matches);
       if(forceOcr||matches.isEmpty()){
        if(ocrPages>=20){limited=true;}
        else{
         ocrPages++;
         android.graphics.Bitmap bitmap=null;
         try{
          if(recognizer==null)recognizer=com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS);
          float scale=Math.min(2.5f,2200f/Math.max(page.getWidth(),page.getHeight()));
          bitmap=android.graphics.Bitmap.createBitmap(Math.max(1,Math.round(page.getWidth()*scale)),Math.max(1,Math.round(page.getHeight()*scale)),android.graphics.Bitmap.Config.ARGB_8888);
          bitmap.eraseColor(android.graphics.Color.WHITE);
          page.render(bitmap,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
          com.google.mlkit.vision.text.Text recognized=com.google.android.gms.tasks.Tasks.await(recognizer.process(com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap,0)));
          String value=recognized.getText();if(value.length()>100000){value=value.substring(0,100000);limited=true;}
          chars+=value.length();hasText|=!value.trim().isEmpty();
          for(DocMeasure m:findDocumentMeasures(value,j+1))results.add(new DocMeasure(m.field,m.page,m.metres,"OCR: "+m.evidence));
         }catch(Exception e){ocrFailed++;}
         finally{if(bitmap!=null)bitmap.recycle();}
        }
       }
       if(hasText)read++;
      }
     }limited|=pages>max;
    }
    final String summary=read+" Seiten mit Text · "+pages+" Seiten insgesamt · OCR: "+ocrPages+" Seiten"+(ocrFailed>0?" ("+ocrFailed+" fehlgeschlagen)":"")+(limited?" · Analyse begrenzt":"");
    runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()&&projectId.equals(activeProject))previewDocumentMeasures(name,summary,results);});
   }catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())notice("PDF konnte nicht gelesen werden: "+e.getMessage());});}
   finally{if(recognizer!=null)recognizer.close();}
  },"pdf-analysis").start();
 }
 void previewDocumentMeasures(String name,String summary,java.util.ArrayList<DocMeasure> found){
  LinearLayout form=page();ScrollView scroll=new ScrollView(this);scroll.addView(form);
  form.addView(sheetText(name+"\n"+summary,15,true));
  form.addView(sheetText("Textsuche und OCR, keine geometrische Zeichnungsinterpretation. OCR kann Ziffern und Einheiten falsch erkennen. Nur am Original geprüfte Werte als Soll übernehmen. Istmaße bleiben unverändert.",14,false));
  Spinner[] picks=new Spinner[DOC_KEYS.length];
  java.util.ArrayList<java.util.ArrayList<DocMeasure>> choicesByField=new java.util.ArrayList<>();
  for(int j=0;j<DOC_KEYS.length;j++){
   java.util.ArrayList<DocMeasure> options=new java.util.ArrayList<>();java.util.ArrayList<String> labels=new java.util.ArrayList<>();
   labels.add("Nicht übernehmen");
   for(DocMeasure m:found)if(m.field==j){options.add(m);labels.add(csvNumber(m.metres)+" m · S. "+m.page+" · "+m.evidence);}
   choicesByField.add(options);form.addView(sheetText(DOC_LABELS[j]+" (bisher Soll: "+getPreferences(0).getString(DOC_KEYS[j],"offen")+")",14,true));
   picks[j]=choices(form,labels.toArray(new String[0]));
  }
  if(found.isEmpty())form.addView(sheetText("Keine eindeutigen Maßangaben gefunden. Scanqualität, Seitenlimit und Schreibweise prüfen.",14,false));
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Maßvorschläge prüfen").setView(scroll).setNegativeButton("Schließen",null).setPositiveButton("Als Soll speichern",null).create();
  dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{
   android.content.SharedPreferences.Editor ed=getPreferences(0).edit();StringBuilder source=new StringBuilder(name);int count=0;
   for(int j=0;j<picks.length;j++){int k=picks[j].getSelectedItemPosition()-1;if(k<0)continue;DocMeasure m=choicesByField.get(j).get(k);ed.putString(DOC_KEYS[j],java.math.BigDecimal.valueOf(m.metres).stripTrailingZeros().toPlainString());source.append("\n").append(DOC_LABELS[j]).append(": S. ").append(m.page).append(" · ").append(m.evidence);count++;}
   if(count==0){notice("Bitte mindestens einen Wert auswählen.");return;}
   ed.putString("sollSource",source.toString());if(!ed.commit()){notice("Sollwerte konnten nicht gespeichert werden.");return;}
   recordProjectEvent("Sollwerte aus Unterlage übernommen");compareSoll();dialog.dismiss();notice("Sollwerte gespeichert. Istmaße unverändert.");
  }));dialog.show();
 }
 void showSavedSollUtilities(){
  try{org.json.JSONArray rows=new org.json.JSONArray(getPreferences(0).getString("sollUtilities","[]"));StringBuilder out=new StringBuilder();for(int i=0;i<rows.length();i++){org.json.JSONObject row=rows.getJSONObject(i);out.append(row.optString("type")).append(" · DN ").append(row.optString("dn","")).append(" · ").append(row.optString("material")).append("\n");}new android.app.AlertDialog.Builder(this).setTitle("Gespeicherte Soll-Leitungen").setMessage(out.length()==0?"Keine Soll-Leitungen gespeichert.":out.toString()).setPositiveButton("Schließen",null).show();}catch(Exception e){notice("Soll-Leitungen konnten nicht gelesen werden.");}
 }
 void openPlanTextAnalysis(){
  final String sourceProject=activeProject;
  EditText input=new EditText(this);
  input.setHint("Text aus dem Ausführungsplan einfügen");
  input.setMinLines(4);
  new android.app.AlertDialog.Builder(this).setTitle("Soll-Leitungen aus Text erkennen").setView(input)
   .setNegativeButton("Abbrechen",null).setPositiveButton("Text prüfen",(d,w)->{
    java.util.List<PlanTextParser.Candidate> candidates=PlanTextParser.parse(input.getText().toString());
    if(candidates.isEmpty()){notice("Keine eindeutigen Leitungsbezeichnungen gefunden.");return;}
    LinearLayout review=new LinearLayout(this);review.setOrientation(1);
    TextView hint=new TextView(this);hint.setText("Soll-Vorschläge prüfen. Keine Übernahme in Ist-Mengen oder Abrechnung.");review.addView(hint);
    java.util.ArrayList<EditText[]> fields=new java.util.ArrayList<>();
    for(PlanTextParser.Candidate c:candidates){
     TextView source=new TextView(this);source.setText("Zeile "+c.lineNumber+": "+c.sourceLine);review.addView(source);
     EditText type=new EditText(this);type.setHint("Leitungsart");type.setText(c.type);review.addView(type);
     EditText dn=new EditText(this);dn.setHint("DN (optional)");dn.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);dn.setText(c.nominalDiameter);review.addView(dn);
     EditText material=new EditText(this);material.setHint("Material (optional)");material.setText(c.material);review.addView(material);
     fields.add(new EditText[]{type,dn,material});
    }
    android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(review);
    android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Soll-Leitungen bearbeiten").setView(scroll).setNegativeButton("Abbrechen",null).setPositiveButton("Vorschau",null).create();
    dialog.setOnShowListener(x->dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
     StringBuilder summary=new StringBuilder("Geprüfte Eingaben – nur Vorschau, noch nicht gespeichert.\n\n");
     org.json.JSONArray reviewed=new org.json.JSONArray();
     for(EditText[] row:fields){
      String type=row[0].getText().toString().trim(),dn=row[1].getText().toString().trim(),material=row[2].getText().toString().trim();
      if(type.isEmpty()){row[0].setError("Leitungsart erforderlich");return;}
      if(!dn.isEmpty()&&!dn.matches("[1-9][0-9]{0,3}")){row[1].setError("DN zwischen 1 und 9999 eingeben");return;}
      try{PlanTextParser.Candidate c=candidates.get(reviewed.length());org.json.JSONObject item=new org.json.JSONObject();item.put("type",type);item.put("dn",dn);item.put("material",material);item.put("sourceLine",c.sourceLine);item.put("lineNumber",c.lineNumber);reviewed.put(item);}catch(org.json.JSONException e){notice("Soll-Daten ungültig.");return;}
      summary.append(type).append(" · DN ").append(dn.isEmpty()?"unbekannt":dn).append(" · ").append(material.isEmpty()?"Material unbekannt":material).append("\n");
     }
     new android.app.AlertDialog.Builder(this).setTitle("Soll-Vorschau").setMessage(summary.toString()).setNegativeButton("Zurück",null).setPositiveButton("Soll-Liste ersetzen und speichern",(confirm,which)->{if(!sourceProject.equals(activeProject)){notice("Projekt wurde gewechselt.");return;}if(getPreferences(0).edit().putString("sollUtilities",reviewed.toString()).commit()){dialog.dismiss();notice("Soll-Leitungen gespeichert.");}else notice("Speichern fehlgeschlagen.");}).show();
    }));
    dialog.show();
   }).show();
 }
 void choosePlanDocument(){
  if(documents.length()==0){notice("Bitte zuerst Ausführungsunterlagen hinzufügen.");return;}
  String[] names=new String[documents.length()];for(int j=0;j<names.length;j++)names[j]=documents.optJSONObject(j).optString("name");
  new android.app.AlertDialog.Builder(this).setTitle("Ausführungsplan auswählen").setItems(names,(dialog,index)->previewPlanDocument(documents.optJSONObject(index))).setNegativeButton("Abbrechen",null).show();
 }
 void previewPlanDocument(org.json.JSONObject doc){
  android.net.Uri uri=android.net.Uri.parse(doc.optString("uri"));
  String name=doc.optString("name"),mime=getContentResolver().getType(uri);
  if(!"application/pdf".equals(mime)&&!name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")){notice("Planvorschau derzeit nur für PDF verfügbar. Andere Formate bleiben verknüpft.");return;}
  LinearLayout form=new LinearLayout(this);form.setOrientation(1);
  TextView status=new TextView(this);status.setText("Soll-Plan · Vorschau, noch keine automatische Erkennung");form.addView(status);
  android.widget.ImageView image=new android.widget.ImageView(this);image.setAdjustViewBounds(true);form.addView(image,new LinearLayout.LayoutParams(-1,dp(360)));
  Button next=new Button(this);next.setText("Nächste Seite");form.addView(next);
  Button prev=new Button(this);prev.setText("Vorherige Seite");form.addView(prev);
  final int[] page={0};
  Runnable render=()->{
   try(android.os.ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"r");android.graphics.pdf.PdfRenderer pdf=new android.graphics.pdf.PdfRenderer(fd)){
    page[0]=Math.max(0,Math.min(pdf.getPageCount()-1,page[0]));
    try(android.graphics.pdf.PdfRenderer.Page p=pdf.openPage(page[0])){
     float scale=Math.min(1600f/p.getWidth(),1600f/p.getHeight());
     android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(Math.max(1,(int)(p.getWidth()*scale)),Math.max(1,(int)(p.getHeight()*scale)),android.graphics.Bitmap.Config.ARGB_8888);
     bitmap.eraseColor(Color.WHITE);p.render(bitmap,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);image.setImageBitmap(bitmap);
     status.setText("Soll-Plan · Seite "+(page[0]+1)+" / "+pdf.getPageCount()+" · nicht ausgewertet");prev.setEnabled(page[0]>0);next.setEnabled(page[0]+1<pdf.getPageCount());
    }
   }catch(Exception e){status.setText("PDF nicht lesbar. Bitte Unterlage erneut verknüpfen.");}
  };
  next.setOnClickListener(v->{page[0]++;render.run();});prev.setOnClickListener(v->{page[0]--;render.run();});
  new android.app.AlertDialog.Builder(this).setTitle(name).setView(form).setPositiveButton("Schließen",null).show();render.run();
 }
 void showDocuments(){StringBuilder b=new StringBuilder();for(int j=0;j<documents.length();j++){org.json.JSONObject o=documents.optJSONObject(j);if(o!=null)b.append(o.optString("name")).append(" – Soll / nicht ausgewertet\n");}documentsList.setText(b.length()==0?"Keine Unterlagen ausgewählt":b.toString());}
 void attachDocument(android.net.Uri uri,int flags){if(uri==null)return;try{for(int j=0;j<documents.length();j++){org.json.JSONObject old=documents.optJSONObject(j);if(old!=null&&uri.toString().equals(old.optString("uri")))return;}getContentResolver().takePersistableUriPermission(uri,flags&android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);String name=uri.getLastPathSegment();try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}org.json.JSONObject o=new org.json.JSONObject();o.put("uri",uri.toString());o.put("name",name);documents.put(o);getPreferences(0).edit().putString("documents",documents.toString()).apply();}catch(Exception e){Toast.makeText(this,"Unterlage konnte nicht dauerhaft verknüpft werden.",Toast.LENGTH_LONG).show();}}
 String selectedPoint,pendingPoint,pendingPhoto;
 int findPoint(RouteSketch route,String id){
  for(int i=0;i<route.vertices.size();i++)if(route.vertices.get(i).id.equals(id))return i;
  return -1;
 }
 void assignGps(String id){
  if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
   requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION},702);return;
  }
  android.location.LocationManager manager=(android.location.LocationManager)getSystemService(LOCATION_SERVICE);
  if(!manager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)){notice("Bitte GPS aktivieren.");return;}
  if(android.os.Build.VERSION.SDK_INT<30){notice("GPS-Zuordnung benötigt Android 11 oder neuer.");return;}
  int[] durations={10,20,30,60};
  int saved=getPreferences(0).getInt("gpsDurationSeconds",20);
  int selected=1;for(int i=0;i<durations.length;i++)if(durations[i]==saved)selected=i;
  final int[] choice={selected};
  new android.app.AlertDialog.Builder(this).setTitle("GPS-Messdauer")
   .setSingleChoiceItems(new String[]{"10 Sekunden","20 Sekunden","30 Sekunden","60 Sekunden"},selected,(d,w)->choice[0]=w)
   .setNegativeButton("Abbrechen",null).setPositiveButton("Messung starten",(d,w)->{
    int seconds=durations[choice[0]];getPreferences(0).edit().putInt("gpsDurationSeconds",seconds).apply();collectGps(id,manager,seconds);
   }).show();
 }
 void collectGps(String id,android.location.LocationManager manager,int seconds){
  final java.util.ArrayList<android.location.Location> samples=new java.util.ArrayList<>();
  final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
  final android.app.AlertDialog progress=new android.app.AlertDialog.Builder(this).setTitle("GPS-Messung · "+seconds+" Sekunden").setMessage("Bitte am Messpunkt stehen bleiben.").setNegativeButton("Abbrechen",null).create();
  final long started=android.os.SystemClock.elapsedRealtime();
  final android.location.LocationListener listener=new android.location.LocationListener(){
   public void onLocationChanged(android.location.Location fix){
    if(!fix.hasAccuracy()||fix.getAccuracy()<=0||android.os.SystemClock.elapsedRealtimeNanos()-fix.getElapsedRealtimeNanos()>3000000000L)return;
    samples.add(new android.location.Location(fix));
    progress.setMessage(String.format(java.util.Locale.GERMANY,"%d/%d Sekunden · %d Messungen\nAktuell ± %.2f m",Math.min(seconds,(android.os.SystemClock.elapsedRealtime()-started)/1000),seconds,samples.size(),fix.getAccuracy()));
   }
   public void onProviderDisabled(String provider){progress.dismiss();}
   public void onProviderEnabled(String provider){}
   public void onStatusChanged(String provider,int status,android.os.Bundle extras){}
  };
  Runnable finish=()->{
   if(!progress.isShowing())return;progress.dismiss();
   if(isFinishing()||isDestroyed()||!hasWindowFocus()&&isChangingConfigurations())return;
   if(samples.isEmpty()){notice("Keine GPS-Messung verfügbar.");return;}
   android.location.Location best=samples.get(0);
   for(android.location.Location f:samples)if(f.getAccuracy()<best.getAccuracy())best=f;
   double sum=0,lat=0,lon=0;
   for(android.location.Location f:samples){double w=1/Math.pow(Math.max(.1,f.getAccuracy()),2);sum+=w;lat+=w*f.getLatitude();lon+=w*(((f.getLongitude()-best.getLongitude()+540)%360)-180);}
   android.location.Location mean=new android.location.Location(best);mean.setLatitude(lat/sum);mean.setLongitude(((best.getLongitude()+lon/sum+540)%360)-180);
   float conservative=best.getAccuracy();
   for(android.location.Location f:samples)conservative=Math.max(conservative,mean.distanceTo(f)+f.getAccuracy());
   mean.setAccuracy(conservative);offerGpsFix(id,mean);
  };
  progress.setOnDismissListener(d->{handler.removeCallbacks(finish);manager.removeUpdates(listener);});
  progress.show();
  try{manager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER,1000L,0f,listener);handler.postDelayed(finish,seconds*1000L);}
  catch(RuntimeException ex){progress.dismiss();notice("GPS konnte nicht gestartet werden.");}
 }
 void offerGpsFix(String id,android.location.Location fix){
   if(isFinishing()||isDestroyed())return;
   if(fix==null){notice("Keine GPS-Position verfügbar. Bitte erneut versuchen.");return;}
   RouteSketch route=new RouteSketch(this,routeData());int index=findPoint(route,id);if(index<0)return;
   RouteSketch.Vertex point=route.vertices.get(index);
   boolean precise=fix.hasAccuracy()&&RouteSketch.preciseGps(fix.getAccuracy());
   android.graphics.PointF target=precise?route.gpsPosition(fix.getLatitude(),fix.getLongitude(),index):null;
   String info=fix.hasAccuracy()?String.format(java.util.Locale.GERMANY,"GPS-Genauigkeit: ± %.2f m",fix.getAccuracy()):"GPS-Genauigkeit unbekannt";
   android.app.AlertDialog.Builder choice=new android.app.AlertDialog.Builder(this).setTitle("GPS-Punkt zuordnen")
    .setMessage(info+(target!=null?"\nPunkt kann verschoben werden.":!precise?"\nVerschieben gesperrt: Genauigkeit muss höchstens 0,50 m betragen.":"\nVerschieben nicht möglich: Kalibrierung fehlt oder Position außerhalb der Zeichnung."))
    .setNegativeButton("Abbrechen",null).setPositiveButton("Manuellen Punkt beibehalten",(d,w)->storeGps(id,fix,false));
   if(target!=null)choice.setNeutralButton("Auf GPS verschieben",(d,w)->storeGps(id,fix,true));
   choice.show();

 }
 void storeGps(String id,android.location.Location fix,boolean move){
  RouteSketch latest=new RouteSketch(this,routeData());int i=findPoint(latest,id);if(i<0)return;
  RouteSketch.Vertex v=latest.vertices.get(i);
  if(move){
   android.graphics.PointF target=fix.hasAccuracy()&&RouteSketch.preciseGps(fix.getAccuracy())?latest.gpsPosition(fix.getLatitude(),fix.getLongitude(),i):null;
   if(target==null){notice("GPS-Verschiebung nicht möglich.");return;}
   v.rememberGpsMove();v.point.set(target);
  }
  v.latitude=fix.getLatitude();v.longitude=fix.getLongitude();v.accuracy=fix.hasAccuracy()?fix.getAccuracy():Double.NaN;
  if(saveRoute(latest)){refreshRoute();notice(move?"Punkt auf GPS-Position verschoben.":"GPS-Koordinaten gespeichert.");}
 }
 @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
  super.onRequestPermissionsResult(requestCode,permissions,results);
  if(requestCode==703){java.util.function.DoubleConsumer next=pendingBleConsumer;pendingBleConsumer=null;boolean granted=results.length>0;for(int result:results)granted&=result==android.content.pm.PackageManager.PERMISSION_GRANTED;if(granted&&next!=null)openBleMeasure(next);else notice("Bluetooth-Berechtigung fehlt.");return;}
  if(requestCode==704){if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED){if(projectChosen&&!isFinishing()&&!isDestroyed())showGpsTrackControls();}else notice("Für die GPS-Aufzeichnung ist der genaue Standort erforderlich.");return;}
  if(requestCode==702){if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED){startLiveGps();assignGps(selectedPoint);}else notice("Für GPS-Punkte ist der genaue Standort erforderlich.");}
 }
 void renamePoint(String id,String proposed){
  RouteSketch route=new RouteSketch(this,routeData());int i=findPoint(route,id);if(i<0){notice("Trassenpunkt nicht gefunden.");return;}
  EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Punktname");name.setText(proposed==null?route.vertices.get(i).name:proposed);
  new android.app.AlertDialog.Builder(this).setTitle("Punkt umbenennen").setView(name).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",(d,w)->{
   RouteSketch latest=new RouteSketch(this,routeData());int j=findPoint(latest,id);if(j<0)return;
   latest.vertices.get(j).name=name.getText().toString().trim();if(saveRoute(latest))refreshRoute();
  }).show();
 }
 void undoGps(String id){
  RouteSketch initial=new RouteSketch(this,routeData());int index=findPoint(initial,id);
  if(index<0||initial.vertices.get(index).gpsUndo==null){notice("Keine GPS-Verschiebung zum Rückgängigmachen.");return;}
  new android.app.AlertDialog.Builder(this).setTitle("GPS-Verschiebung rückgängig machen?").setMessage("Die vorherige Position und GPS-Zuordnung werden wiederhergestellt. Maße, Name und Fotos bleiben erhalten.").setNegativeButton("Abbrechen",null).setPositiveButton("Wiederherstellen",(d,w)->{
   RouteSketch route=new RouteSketch(this,routeData());int i=findPoint(route,id);
   if(i>=0&&route.vertices.get(i).undoGpsMove()&&saveRoute(route)){refreshRoute();notice("Vorherige Position wiederhergestellt.");}
  }).show();
 }
 double photoStation(RouteSketch route,int index){
  double station=0;for(int j=1;j<=index;j++){double m=route.vertices.get(j).metres;if(!Double.isFinite(m)||m<=0)return Double.NaN;station+=m;}return station;
 }
 org.json.JSONObject newPhotoMetadata(java.io.File file,RouteSketch route,int index){
  org.json.JSONObject meta=new org.json.JSONObject();
  try{
   meta.put("capturedAt",System.currentTimeMillis()).put("timeSource","cameraReturn");
   double station=photoStation(route,index);if(Double.isFinite(station))meta.put("station",station);
   android.media.ExifInterface exif=new android.media.ExifInterface(file.getAbsolutePath());
   String date=exif.getAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL);
   if(date!=null&&!date.isEmpty())meta.put("exifDate",date);
   float[] coords=new float[2];
   if(exif.getLatLong(coords)&&Float.isFinite(coords[0])&&Float.isFinite(coords[1]))meta.put("latitude",(double)coords[0]).put("longitude",(double)coords[1]).put("gpsSource","EXIF");
  }catch(Exception ignored){}
  return meta;
 }
 String photoInfo(org.json.JSONObject m){
  if(m==null)return "Keine Fotometadaten erfasst.";
  StringBuilder b=new StringBuilder();
  if(m.has("exifDate"))b.append("Aufnahme (EXIF): ").append(m.optString("exifDate"));
  else if(m.has("capturedAt"))b.append("Kamera beendet: ").append(new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss",java.util.Locale.GERMANY).format(new java.util.Date(m.optLong("capturedAt"))));
  if(m.has("station"))b.append("\nStation bei Zuordnung: ").append(reportMeasure(m.optDouble("station")));
  if(m.has("latitude")&&m.has("longitude"))b.append(String.format(java.util.Locale.GERMANY,"\nFoto-GPS (%s): %.7f / %.7f",m.optString("gpsSource"),m.optDouble("latitude"),m.optDouble("longitude")));
  else b.append("\nKeine Foto-GPS-Koordinaten verfügbar.");
  if(m.has("accuracy"))b.append("\nGPS-Genauigkeit: ± ").append(reportMeasure(m.optDouble("accuracy")));
  return b.toString();
 }
 void showPhotoDetails(String id,String filename){
  RouteSketch route=new RouteSketch(this,routeData());int index=findPoint(route,id);if(index<0)return;
  org.json.JSONObject meta=route.vertices.get(index).photoMetadata.optJSONObject(filename);
  LinearLayout form=page();form.addView(sheetText(photoInfo(meta),14,false));
  EditText description=new EditText(this);description.setHint("Beschreibung / Bemerkung");description.setText(meta==null?"":meta.optString("description"));form.addView(description);
  new android.app.AlertDialog.Builder(this).setTitle("Fotodokumentation – Punkt "+(index+1)).setView(form).setNeutralButton("Foto öffnen",(d,w)->{
   android.content.Intent view=new android.content.Intent(android.content.Intent.ACTION_VIEW);view.setDataAndType(photoUri(filename),"image/jpeg");view.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
   try{startActivity(view);}catch(Exception e){notice("Keine Bildanzeige verfügbar.");}
  }).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",(d,w)->{
   RouteSketch latest=new RouteSketch(this,routeData());int j=findPoint(latest,id);if(j<0)return;RouteSketch.Vertex v=latest.vertices.get(j);boolean attached=false;
   for(int k=0;k<v.photos.length();k++)if(filename.equals(v.photos.optString(k)))attached=true;
   if(!attached){notice("Foto nicht mehr zugeordnet.");return;}
   try{org.json.JSONObject m=v.photoMetadata.optJSONObject(filename);if(m==null)m=new org.json.JSONObject();m.put("description",description.getText().toString());v.photoMetadata.put(filename,m);if(saveRoute(latest))refreshRoute();}catch(Exception e){notice("Beschreibung konnte nicht gespeichert werden.");}
  }).show();
 }
 void importNmeaPoint(String id){
  LinearLayout form=page();
  form.addView(sheetText("NMEA-Daten eines externen Empfängers einfügen (GGA und GST desselben Messzeitpunkts). WGS84-Koordinaten vorausgesetzt. Keine Live-Verbindung; Aufnahmezeit und Empfänger prüfen.",14,false));
  EditText input=new EditText(this);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);input.setMinLines(4);input.setHint("$GNGGA,...\n$GNGST,...");form.addView(input);
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("GNSS / RTK – NMEA-Import (Test)").setView(form).setNegativeButton("Abbrechen",null).setPositiveButton("Prüfen",null).create();
  dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{
   NmeaFix fix=NmeaFix.parse(input.getText().toString());
   if(fix==null){input.setError("Gültiger GGA-Satz mit Prüfsumme erforderlich");return;}
   dialog.dismiss();reviewNmeaPoint(id,fix);
  }));dialog.show();
 }
 void reviewNmeaPoint(String id,NmeaFix data){
  RouteSketch route=new RouteSketch(this,routeData());int index=findPoint(route,id);if(index<0){notice("Punkt nicht gefunden.");return;}
  android.location.Location fix=new android.location.Location("external-nmea-import");
  fix.setLatitude(data.lat);fix.setLongitude(data.lon);
  if(data.movable())fix.setAccuracy((float)data.accuracy);
  boolean move=data.movable()&&route.gpsPosition(data.lat,data.lon,index)!=null;
  String info=String.format(java.util.Locale.GERMANY,"Breite %.8f°\nLänge %.8f°\n%s\nSatelliten: %d\nUTC: %s",data.lat,data.lon,data.status(),data.satellites,data.utc);
  info+="\n"+(Double.isFinite(data.accuracy)?String.format(java.util.Locale.GERMANY,"GST-basierte horizontale Schätzung: %.3f m",data.accuracy):"Keine passende GST-Genauigkeitsangabe");
  info+="\nImportierte Messung, nicht live. Bezugssystem, Aktualität und Empfängerstatus vor Übernahme prüfen.";
  info+=move?"\nVerschieben möglich.":"\nVerschieben gesperrt: Fix-Code 4, Genauigkeit ≤ 0,50 m und gültige Plankalibrierung erforderlich.";
  android.app.AlertDialog.Builder builder=new android.app.AlertDialog.Builder(this).setTitle("Externe GNSS-Koordinaten prüfen").setMessage(info).setNegativeButton("Abbrechen",null)
   .setPositiveButton("Manuellen Punkt beibehalten",(d,w)->{storeGps(id,fix,false);});
  if(move)builder.setNeutralButton("Auf GNSS verschieben",(d,w)->storeGps(id,fix,true));
  builder.show();
 }
 void pointMenu(){
  RouteSketch route=new RouteSketch(this,routeData());int index=findPoint(route,selectedPoint);
  if(index<0||livePreview==null)return;
  RouteSketch.Vertex point=route.vertices.get(index);
  final String id=point.id;
  android.widget.PopupMenu menu=new android.widget.PopupMenu(new android.view.ContextThemeWrapper(this,android.R.style.Theme_Material),livePreview);
  menu.getMenu().add(0,0,0,point.name.isEmpty()?"Trassenpunkt "+(index+1):point.name).setEnabled(false);
  if(Double.isFinite(point.latitude)&&Double.isFinite(point.longitude)){
   menu.getMenu().add(0,10,1,String.format(java.util.Locale.GERMANY,"%.7f° / %.7f°",point.latitude,point.longitude)).setEnabled(false);
   menu.getMenu().add(0,11,2,Double.isFinite(point.accuracy)?String.format(java.util.Locale.GERMANY,"GPS ± %.2f m",point.accuracy):"GPS: Genauigkeit unbekannt").setEnabled(false);
  }else menu.getMenu().add(0,10,1,"Keine GPS-Koordinaten").setEnabled(false);
  menu.getMenu().add(0,12,3,"GPS-Aufzeichnung");
  menu.getMenu().add(0,13,4,"Trassenvergleich");
  menu.getMenu().add(0,1,4,"GPS-Punkt zuordnen");
  menu.getMenu().add(0,2,4,"Foto aufnehmen");
  menu.getMenu().add(0,3,5,"Punkt umbenennen");
  menu.getMenu().add(0,4,6,"GNSS / RTK – NMEA importieren");
  if(point.gpsUndo!=null)menu.getMenu().add(0,5,7,"GPS-Verschiebung rückgängig machen");
  for(int i=0;i<point.photos.length();i++)menu.getMenu().add(0,100+i,8+i,"Foto "+(i+1)+" · Details");
  menu.getMenu().findItem(12).setIcon(android.R.drawable.ic_menu_mylocation);
  menu.getMenu().findItem(13).setIcon(android.R.drawable.ic_menu_directions);
  menu.getMenu().findItem(1).setIcon(android.R.drawable.ic_menu_mapmode);
  menu.getMenu().findItem(2).setIcon(android.R.drawable.ic_menu_camera);
  menu.getMenu().findItem(3).setIcon(android.R.drawable.ic_menu_edit);
  menu.getMenu().findItem(4).setIcon(android.R.drawable.ic_menu_upload);
  if(menu.getMenu().findItem(5)!=null)menu.getMenu().findItem(5).setIcon(android.R.drawable.ic_menu_revert);
  if(android.os.Build.VERSION.SDK_INT>=29)menu.setForceShowIcon(true);
  menu.setOnMenuItemClickListener(item->{switch(item.getItemId()){
   case 12:showGpsTrackControls();return true;
   case 13:showGpsTrackComparison();return true;
   case 1:assignGps(id);return true;
   case 2:selectedPoint=id;capturePhoto();return true;
   case 3:renamePoint(id,null);return true;
   case 4:importNmeaPoint(id);return true;
   case 5:undoGps(id);return true;
   default:int i=item.getItemId()-100;if(i>=0&&i<point.photos.length()){showPhotoDetails(id,point.photos.optString(i));return true;}return false;
  }});menu.show();
 }
 android.net.Uri photoUri(String file){
  return androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".photos",new java.io.File(getFilesDir(),"photos/"+file));
 }
 void notice(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
 void capturePhoto(){
  RouteSketch route=new RouteSketch(this,routeData());
  if(route.vertices.isEmpty()){notice("Zuerst eine Trasse zeichnen und speichern.");return;}
  if(findPoint(route,selectedPoint)<0){
   String[] labels=new String[route.vertices.size()];
   for(int i=0;i<labels.length;i++)labels[i]="Trassenpunkt "+(i+1);
   new android.app.AlertDialog.Builder(this).setTitle("Punkt für Foto wählen").setItems(labels,(dialog,i)->{
    selectedPoint=route.vertices.get(i).id;refreshRoute();capturePhoto();
   }).show();return;
  }
  java.io.File dir=new java.io.File(getFilesDir(),"photos");
  try{
   dir.mkdirs();java.io.File file=java.io.File.createTempFile("punkt_",".jpg",dir);
   pendingPhoto=file.getName();pendingPoint=selectedPoint;android.net.Uri uri=photoUri(pendingPhoto);
   android.content.Intent camera=new android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
   camera.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,uri);
   camera.setClipData(android.content.ClipData.newRawUri("Foto",uri));
   camera.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION|android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
   startActivityForResult(camera,413);
  }catch(Exception e){finishPhoto(false);notice("Kamera konnte nicht geöffnet werden.");}
 }
 void finishPhoto(boolean success){
  if(pendingPhoto==null)return;
  java.io.File file=new java.io.File(getFilesDir(),"photos/"+pendingPhoto);
  RouteSketch route=new RouteSketch(this,routeData());int index=findPoint(route,pendingPoint);
  if(success && index>=0 && file.length()>0){
   RouteSketch.Vertex v=route.vertices.get(index);v.photos.put(pendingPhoto);
   try{v.photoMetadata.put(pendingPhoto,newPhotoMetadata(file,route,index));}catch(Exception ignored){}
   if(saveRoute(route)){refreshRoute();notice("Foto an Trassenpunkt "+(index+1)+" angehängt.");showPhotoDetails(v.id,pendingPhoto);}else notice("Foto gespeichert, aber Zuordnung fehlgeschlagen.");
  }else{file.delete();if(success)notice("Foto konnte nicht zugeordnet werden.");}
  pendingPoint=null;pendingPhoto=null;
 }
 @Override protected void onSaveInstanceState(Bundle out){
  super.onSaveInstanceState(out);out.putString("pdfProject",pendingPdfProject);out.putInt("pdfLayers",pendingPdfLayers);if(pendingPdfScope!=null)out.putString("pdfScope",pendingPdfScope.name());out.putBoolean("projectChosen",projectChosen);out.putString("selectedPoint",selectedPoint);
  out.putString("pendingPoint",pendingPoint);out.putString("pendingPhoto",pendingPhoto);out.putString("pendingMaterialProject",pendingMaterialProject);out.putString("pendingConnectionsProject",pendingConnectionsProject);out.putString("pendingPlanProject",pendingPlanProject);out.putString("pendingProtocolsProject",pendingProtocolsProject);
 }
 com.google.mlkit.nl.translate.Translator translator;
 int speechVersion;
 void offerVoiceRename(String german){
  java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?iu)^(?:benenne|nenne)\\s+(?:den\\s+)?(?:ausgewählten\\s+)?(?:trassen)?punkt(?:\\s+(\\d+))?\\s+(?:in\\s+|als\\s+)?(.+?)(?:\\s+um)?[.!]?$").matcher(german.trim());
  if(!m.matches())return;
  RouteSketch route=new RouteSketch(this,routeData());int i=findPoint(route,selectedPoint);
  if(m.group(1)!=null){try{i=Integer.parseInt(m.group(1))-1;}catch(NumberFormatException e){return;}}
  if(i<0||i>=route.vertices.size()){notice("Bitte zuerst einen Trassenpunkt auswählen.");return;}
  renamePoint(route.vertices.get(i).id,m.group(2).trim());
 }
 boolean selectVoicePoint(String text){
  java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?iu)(?:trassenpunkt|punkt|точк[ауеи]|точке)\\s+(\\d+)").matcher(text);
  RouteSketch route=new RouteSketch(this,routeData());
  if(m.find()){int i;try{i=Integer.parseInt(m.group(1))-1;}catch(NumberFormatException e){return false;}
   if(i<0||i>=route.vertices.size()){notice("Trassenpunkt nicht gefunden.");return false;}
   selectedPoint=route.vertices.get(i).id;
  }
  if(findPoint(route,selectedPoint)<0){notice("Bitte zuerst einen Trassenpunkt auswählen.");return false;}
  return true;
 }
 boolean offerVoicePointAction(String text){
  String t=text.toLowerCase(java.util.Locale.ROOT);
  boolean gps=t.matches(".*(?:gps|гпс|координат|koordinaten|standort).*")&&t.matches(".*(?:mess|zuordn|erfass|speicher|aufzeichn|привяж|измер|запиш|сохрани).*");
  boolean undo=t.matches(".*(?:gps|гпс).*")&&t.matches(".*(?:rückgängig|zurücksetzen|отмени|отменить).*");
  if(!gps&&!undo)return false;
  if(selectVoicePoint(text)){if(undo)undoGps(selectedPoint);else assignGps(selectedPoint);}
  return true;
 }
 boolean offerVoiceSegment(String text){
  java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?iu)^(?:измени\\s+|установи\\s+|ändere\\s+|setze\\s+)?(?:длин[ау]\\s+участка|(?:länge\\s+(?:von\\s+)?(?:abschnitt|des\\s+abschnitts))|abschnitt|участок)\\s+(\\d+)\\s*(?:на\\s+|auf\\s+|ist\\s+|[:=]\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(м|метр[а-я]*|m|meter|см|сантиметр[а-я]*|cm|zentimeter)[.!]?$").matcher(text.trim());
  if(!m.matches())return false;
  int i;double value;
  try{i=Integer.parseInt(m.group(1));value=Double.parseDouble(m.group(2).replace(',','.'));}catch(NumberFormatException e){notice("Ungültiges Maß.");return true;}
  String unit=m.group(3).toLowerCase(java.util.Locale.ROOT);
  if(unit.equals("cm")||unit.equals("см")||unit.startsWith("сантиметр")||unit.equals("zentimeter"))value/=100;
  RouteSketch route=new RouteSketch(this,routeData());
  if(i<1||i>=route.vertices.size()||!Double.isFinite(value)||value<=0){notice("Abschnitt oder Maß ungültig.");return true;}
  final double metres=value;final String from=route.vertices.get(i-1).id,to=route.vertices.get(i).id;
  new android.app.AlertDialog.Builder(this).setTitle("Abschnitt "+i+" bemaßen")
   .setMessage(String.format(java.util.Locale.GERMANY,"Neue Länge: %.3f m",metres))
   .setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",(d,w)->{
    RouteSketch latest=new RouteSketch(this,routeData());int j=findPoint(latest,to);
    if(j<1||!from.equals(latest.vertices.get(j-1).id)){notice("Abschnitt wurde geändert. Bitte erneut wählen.");return;}
    latest.vertices.get(j).metres=metres;if(saveRoute(latest)){refreshRoute();notice("Abschnitt gespeichert.");}
   }).show();return true;
 }
 boolean offerVoiceObject(String text){
  java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?iu)^(?:добавь|добавить|erfasse|füge)\\s+(домовое подключение|препятствие|поисковый шурф|стартовый котлован|приёмный котлован|приемный котлован|hausanschluss|hindernis|suchschachtung|startgrube|zielgrube|kvs|квс|knickpunkt|точку поворота)\\s+(?:на\\s+станции|bei\\s+station|an\\s+station|station)\\s+(\\d+(?:[.,]\\d+)?)\\s*(?:метр[а-я]*|meter|м|m)?(?:\\s+(слева|справа|по центру|links|rechts|mittig))?(?:\\s+hinzu)?[.!]?$").matcher(text.trim());
  if(!m.matches())return false;
  String raw=m.group(1).toLowerCase(java.util.Locale.ROOT),type;
  switch(raw){
   case "домовое подключение":case "hausanschluss":type="Hausanschluss";break;
   case "препятствие":case "hindernis":type="Hindernis";break;
   case "поисковый шурф":case "suchschachtung":type="Suchschachtung";break;
   case "стартовый котлован":case "startgrube":type="Startgrube";break;
   case "приёмный котлован":case "приемный котлован":case "zielgrube":type="Zielgrube";break;
   case "kvs":case "квс":type="KVS";break;
   default:type="Knickpunkt";
  }
  double station;try{station=Double.parseDouble(m.group(2).replace(',','.'));}catch(NumberFormatException e){notice("Ungültige Station.");return true;}
  if(!Double.isFinite(station)||!Double.isFinite(l)||l<=0||station<0||station>l){notice("Objektstation außerhalb der Trasse.");return true;}
  String side=m.group(3)==null?"":m.group(3).toLowerCase(java.util.Locale.ROOT);
  String initial=side.equals("слева")||side.equals("links")?"links":side.equals("справа")||side.equals("rechts")?"rechts":side.equals("по центру")||side.equals("mittig")?"mittig":"Unbekannt";
  LinearLayout form=page();form.addView(sheetText(String.format(java.util.Locale.GERMANY,"%s · Station %.2f m",type,station),16,true));
  String[] sides={"Unbekannt","links","rechts","mittig"};Spinner choose=choices(form,sides);choose.setSelection(java.util.Arrays.asList(sides).indexOf(initial));
  EditText note=new EditText(this);note.setHint("Bemerkung");form.addView(note);
  new android.app.AlertDialog.Builder(this).setTitle("Objekt hinzufügen").setView(form).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",(d,w)->{
   if(!Double.isFinite(l)||l<=0||station>l){notice("Objektstation außerhalb der Trasse.");return;}
   try{org.json.JSONObject o=new org.json.JSONObject();o.put("station",station);o.put("type",type);o.put("side",choose.getSelectedItem().toString());o.put("note",note.getText().toString());objects.put(o);saveObjects();notice("Objekt gespeichert.");}catch(Exception e){notice("Objekt konnte nicht gespeichert werden.");}
  }).show();return true;
 }
 boolean offerVoiceObjectDimensions(String text){
  java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?iu)^(?:размеры объекта|maße objekt|objektmaße)\\s+(\\d+)\\s*[:,]?\\s*(.+)$").matcher(text.trim());
  if(!m.matches())return false;
  int i;try{i=Integer.parseInt(m.group(1))-1;}catch(NumberFormatException e){notice("Ungültige Objektnummer.");return true;}
  org.json.JSONObject o=objects.optJSONObject(i);if(o==null){notice("Objekt nicht gefunden.");return true;}
  String[] keys={"detailLength","detailWidth","detailDepth"},names={"Länge","Breite","Tiefe"},patterns={"(?:длина|länge)","(?:ширина|breite)","(?:глубина|tiefe)"};
  String[] values=new String[3];StringBuilder preview=new StringBuilder(o.optString("type")+" · Station "+o.optString("station")+" m\n");
  for(int j=0;j<3;j++){
   java.util.regex.Matcher v=java.util.regex.Pattern.compile("(?iu)"+patterns[j]+"\\s*[:=]?\\s*(\\d+(?:[.,]\\d+)?)\\s*(zentimeter|cm|сантиметр[а-я]*|см|meter|метр[а-я]*|m|м)(?![\\p{L}])").matcher(m.group(2));
   if(v.find()){double n=Double.parseDouble(v.group(1).replace(',','.'));String u=v.group(2).toLowerCase(java.util.Locale.ROOT);if(u.equals("cm")||u.equals("см")||u.startsWith("сантиметр")||u.equals("zentimeter"))n/=100;
    if(!Double.isFinite(n)||n<=0){notice("Positive Maße erforderlich.");return true;}values[j]=Double.toString(n);preview.append(names[j]).append(": ").append(String.format(java.util.Locale.GERMANY,"%.3f m",n)).append("\n");}
  }
  if(values[0]==null&&values[1]==null&&values[2]==null){notice("Keine Maße mit Einheit erkannt.");return true;}
  new android.app.AlertDialog.Builder(this).setTitle("Detailmaße prüfen").setMessage(preview.toString()).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",(d,w)->{
   if(objects.optJSONObject(i)!=o){notice("Objekt wurde geändert. Bitte erneut wählen.");return;}
   try{for(int j=0;j<3;j++)if(values[j]!=null)o.put(keys[j],values[j]);saveObjects();notice("Detailmaße gespeichert.");}catch(Exception e){notice("Detailmaße konnten nicht gespeichert werden.");}
  }).show();return true;
 }
 boolean offerVoiceSurface(String text){
  String[] patterns={"(?:толщина\\s+асфальта|асфальт|asphaltstärke|asphalt)","(?:толщина\\s+брусчатки|брусчатка|pflasterstärke|pflaster)","(?:щебень|несущий слой|tragschichtstärke|schottertragschicht)","(?:песчаная подушка|песок|sandbettung)"};
  String[] labels={"Asphaltstärke","Belagsstärke","Tragschichtstärke","Sandbettung"};
  EditText[] fields={asphaltInput,stoneDepth,baseInput,bedding};
  String[] values=new String[4];StringBuilder preview=new StringBuilder();
  for(int j=0;j<patterns.length;j++){
   java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?iu)"+patterns[j]+"\\s*[:=]?\\s*(\\d+(?:[.,]\\d+)?)\\s*(zentimeter|cm|сантиметр[а-я]*|см|meter|метр[а-я]*|m|м)(?![\\p{L}])").matcher(text);
   if(m.find()){double n=Double.parseDouble(m.group(1).replace(',','.'));String u=m.group(2).toLowerCase(java.util.Locale.ROOT);if(u.equals("cm")||u.equals("см")||u.startsWith("сантиметр")||u.equals("zentimeter"))n/=100;
    if(!Double.isFinite(n)||n<=0){notice("Positive Schichtstärke erforderlich.");return true;}
    values[j]=j==3?String.format(java.util.Locale.GERMANY,"Sand, %.3f m",n):String.format(java.util.Locale.GERMANY,"%.3f",j==1?n*100:n);
    preview.append(labels[j]).append(": ").append(String.format(java.util.Locale.GERMANY,"%.3f m",n)).append("\n");
   }
  }
  if(preview.length()==0)return false;
  new android.app.AlertDialog.Builder(this).setTitle("Oberflächenaufbau prüfen").setMessage(preview.toString()).setNegativeButton("Abbrechen",null).setPositiveButton("Übernehmen",(d,w)->{
   for(int j=0;j<4;j++)if(values[j]!=null)fields[j].setText(values[j]);
   android.content.SharedPreferences.Editor ed=getPreferences(0).edit();String[] keys={"asphalt","stoneDepth","base","bedding"};for(int j=0;j<4;j++)if(values[j]!=null)ed.putString(keys[j],values[j]);if(ed.commit())notice("Schichtangaben gespeichert. Belagsart im Regelprofil prüfen.");else notice("Speichern fehlgeschlagen.");
  }).show();return true;
 }
 boolean offerVoiceNewPoint(String text){
  java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?iu)^(?:добавь точку|новая точка|neuer punkt|punkt hinzufügen)\\s+(\\d+(?:[.,]\\d+)?)\\s*(метр[а-я]*|м|meter|m)\\s+(вправо|влево|вверх|вниз|nach rechts|nach links|nach oben|nach unten)[.!]?$").matcher(text.trim());
  if(!m.matches())return false;
  double metres=Double.parseDouble(m.group(1).replace(',','.'));
  if(!Double.isFinite(metres)||metres<=0){notice("Positive Länge erforderlich.");return true;}
  String direction=m.group(3).toLowerCase(java.util.Locale.ROOT);
  int dx=direction.equals("вправо")||direction.equals("nach rechts")?1:direction.equals("влево")||direction.equals("nach links")?-1:0;
  int dy=direction.equals("вниз")||direction.equals("nach unten")?1:direction.equals("вверх")||direction.equals("nach oben")?-1:0;
  RouteSketch route=new RouteSketch(this,routeData());
  if(route.vertices.isEmpty()){notice("Bitte zuerst einen Startpunkt zeichnen.");return true;}
  RouteSketch.Vertex last=route.vertices.get(route.vertices.size()-1);
  float x=last.point.x+dx*.12f,y=last.point.y+dy*.12f;
  if(x<0||x>1||y<0||y>1){notice("Zeichnungsrand erreicht. Bitte Punkt manuell setzen.");return true;}
  String label=dx>0?"rechts":dx<0?"links":dy>0?"unten":"oben";
  String original=route.encode();
  new android.app.AlertDialog.Builder(this).setTitle("Trassenpunkt hinzufügen").setMessage(String.format(java.util.Locale.GERMANY,"Abschnitt %.2f m · nach %s\nRichtung in der Skizze, nicht geografisch.",metres,label)).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",(d,w)->{
   RouteSketch latest=new RouteSketch(this,routeData());if(!original.equals(latest.encode())){notice("Trasse wurde geändert. Bitte erneut versuchen.");return;}
   RouteSketch.Vertex v=new RouteSketch.Vertex(new android.graphics.PointF(x,y),metres);latest.vertices.add(v);
   if(saveRoute(latest)){selectedPoint=v.id;refreshRoute();notice("Trassenpunkt gespeichert.");}
  }).show();return true;
 }
 boolean offerVoiceSave(String text){
  if(!text.trim().matches("(?iu)^(?:сохрани(?:\\s+(?:проект|всё|все|данные))?|(?:projekt|alles|daten)\\s+speichern|speichere(?:\\s+(?:das\\s+)?projekt|\\s+alles)?)[.!]?$"))return false;
  new android.app.AlertDialog.Builder(this).setTitle("Projekt speichern").setMessage("Aktuelle Profilmaße prüfen und speichern? Trasse, Objekte und Fotos bleiben erhalten.").setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",(d,w)->{
   if(calculate()){notice("Projekt gespeichert.");}else{notice("Projekt nicht gespeichert. Bitte Maße prüfen.");}
  }).show();return true;
 }
 boolean offerVoiceDailyReport(String text,int version){
  java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?isu)^(?:(?:добавь|запиши|внеси)\\s+(?:в\\s+)?(?:строительный\\s+журнал|журнал|отч[её]т)|(?:bau)?tagesbericht|(?:добавь\\s+в\\s+)?bautagesbericht)\\s*[:;,–—-]?\\s*(.*)$").matcher(text.trim());
  if(!m.matches())return false;
  String payload=m.group(1).trim();
  if(payload.isEmpty()){showDailyReports();return true;}
  final String projectId=activeProject;
  if(!java.util.regex.Pattern.compile("[\\p{IsCyrillic}]").matcher(payload).find()){confirmVoiceDailyReport(payload);return true;}
  if(translator==null)translator=com.google.mlkit.nl.translate.Translation.getClient(
   new com.google.mlkit.nl.translate.TranslatorOptions.Builder().setSourceLanguage("ru").setTargetLanguage("de").build());
  notice("Bautagesbericht wird ins Deutsche übersetzt …");
  translator.downloadModelIfNeeded().onSuccessTask(ignored->translator.translate(payload))
   .addOnSuccessListener(this,german->{if(version==speechVersion&&projectId.equals(activeProject)&&!isFinishing()&&!isDestroyed())confirmVoiceDailyReport(german);})
   .addOnFailureListener(this,error->{if(version==speechVersion)notice("Übersetzung fehlgeschlagen. Kein Bericht geändert.");});
  return true;
 }
 void confirmVoiceDailyReport(String german){
  if(german==null||german.trim().isEmpty()){notice("Kein Text erkannt.");return;}
  LinearLayout form=page();ScrollView scroll=new ScrollView(this);scroll.addView(form);
  form.addView(sheetText("Datum",14,true));EditText date=new EditText(this);date.setSingleLine(true);date.setText(java.time.LocalDate.now().toString());form.addView(date);
  String[] labels=java.util.Arrays.copyOfRange(DAILY_LABELS,1,DAILY_LABELS.length);
  form.addView(sheetText("Abschnitt des Bautagesberichts",14,true));Spinner section=choices(form,labels);section.setSelection(3);
  form.addView(sheetText("Deutschen Text vor dem Speichern prüfen, insbesondere Zahlen und Maße.",14,false));
  EditText content=new EditText(this);content.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);content.setMinLines(4);content.setText(german);form.addView(content);
  form.addView(sheetText("Text wird an den vorhandenen Abschnitt angehängt. Fehlende Tagesberichte werden neu erstellt.",13,false));
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Bautagesbericht – Diktat").setView(scroll).setNegativeButton("Abbrechen",null).setPositiveButton("Übernehmen",null).create();
  dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{
   String day=date.getText().toString().trim(),value=content.getText().toString().trim();
   try{if(!day.matches("\\d{4}-\\d{2}-\\d{2}")||!java.time.LocalDate.parse(day).toString().equals(day))throw new IllegalArgumentException();}
   catch(Exception e){date.setError("Gültiges Datum: JJJJ-MM-TT");return;}
   if(value.isEmpty()){content.setError("Text erforderlich");return;}
   try{
    org.json.JSONArray rows=dailyReports();java.util.ArrayList<org.json.JSONObject> sorted=new java.util.ArrayList<>();org.json.JSONObject selected=null;
    for(int j=0;j<rows.length();j++){org.json.JSONObject row=rows.getJSONObject(j);sorted.add(row);if(day.equals(row.optString("date")))selected=row;}
    if(selected==null){selected=new org.json.JSONObject().put("id",java.util.UUID.randomUUID().toString()).put("date",day);sorted.add(selected);}
    String key=DAILY_KEYS[section.getSelectedItemPosition()+1],before=selected.optString(key,"");
    selected.put(key,before.isEmpty()?value:before+"\n"+value).put("modifiedAt",System.currentTimeMillis());
    sorted.sort((a,c)->a.optString("date").compareTo(c.optString("date")));
    if(saveDailyReports(new org.json.JSONArray(sorted))){dialog.dismiss();notice("Diktat im Bautagesbericht gespeichert.");showDailyReports();}
   }catch(Exception e){notice("Diktat konnte nicht gespeichert werden.");}
  }));dialog.show();
 }
 void handleSpeech(String text){
  int version=++speechVersion;
  if(offerVoiceDailyReport(text,version))return;
  if(offerVoiceSave(text)||offerAssistantAnswer(text))return;
  String normalized=text.toLowerCase(java.util.Locale.ROOT);
  if(normalized.matches(".*(?:сделай|сделать|сними|прикрепи|прицепи|mach|nimm|nehme|aufnehmen|erstelle|hänge|füge).*?(?:фото|снимок|foto).*") ||
     normalized.matches(".*(?:сфотографируй|fotografier|foto aufnehmen|foto machen|foto erstellen|foto anhängen|foto hinzufügen|foto aufnehmen).*")){
   if(selectVoicePoint(text))capturePhoto();return;
  }
  if(offerVoiceNewPoint(text)||offerVoiceObjectDimensions(text)||offerVoiceSurface(text)||offerVoiceObject(text)||offerVoiceSegment(text)||offerVoicePointAction(text))return;
  if(offerProfileBatch(text,false))return;
  showPage(voicePage);
  if(voiceLanguage.getSelectedItemPosition()==0){voiceText.setText(text);offerVoiceRename(text);return;}
  if(translator==null)translator=com.google.mlkit.nl.translate.Translation.getClient(
   new com.google.mlkit.nl.translate.TranslatorOptions.Builder().setSourceLanguage("ru").setTargetLanguage("de").build());
  voiceText.setText("");notice("Diktat wird ins Deutsche übersetzt …");
  translator.downloadModelIfNeeded().onSuccessTask(ignored->translator.translate(text))
   .addOnSuccessListener(this,german->{if(version==speechVersion){voiceText.setText(german);if(!offerVoiceSave(german)&&!offerVoiceNewPoint(german)&&!offerVoiceObjectDimensions(german)&&!offerVoiceSurface(german)&&!offerVoiceObject(german)&&!offerVoiceSegment(german))offerVoiceRename(german);}})
   .addOnFailureListener(this,error->{if(version==speechVersion)notice("Übersetzung fehlgeschlagen. Für den ersten Modellabruf wird Internet benötigt.");});
 }
 @Override protected void onDestroy(){pendingEditorSpeech=null;if(bleMeasure!=null)bleMeasure.close();pendingBleConsumer=null;if(translator!=null)translator.close();super.onDestroy();}
 void startDictation(){

 android.content.Intent i=new android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
 i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
 i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE,voiceLanguage.getSelectedItemPosition()==0?"de-DE":"ru-RU");
 i.putExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
 try{startActivityForResult(i,410);}catch(android.content.ActivityNotFoundException e){Toast.makeText(this,"Keine Spracherkennung verfügbar.",Toast.LENGTH_LONG).show();}
 }
 @Override protected void onActivityResult(int request,int result,android.content.Intent data){super.onActivityResult(request,result,data);
 if(request==454){if(result==RESULT_OK&&data!=null&&data.getData()!=null)importUtilitySheet(data.getData());return;}
 if(request==453){if(result==RESULT_OK&&data!=null&&data.getData()!=null)readGpsImport(data.getData());return;}
 if(request==451||request==452){String snapshot=pendingGpsExport;pendingGpsExport=null;if(result==RESULT_OK&&data!=null&&data.getData()!=null&&snapshot!=null)writeGpsExport(data.getData(),request==451,snapshot);return;}
 if(request==432){
  String projectId=pendingProtocolsProject;pendingProtocolsProject=null;
  if(result==RESULT_OK&&data!=null&&data.getData()!=null){
   if(activeProject.equals(projectId))new MontageProtocols(this).writePdf(data.getData());else notice("Projekt gewechselt. Export erneut starten.");
  }
  return;
 }
 if(request==431){
  String projectId=pendingPlanProject;pendingPlanProject=null;
  if(result==RESULT_OK&&data!=null&&data.getData()!=null){
   if(activeProject.equals(projectId))writeMontagePlan(data.getData());else notice("Projekt gewechselt. Export erneut starten.");
  }
  return;
 }
 if(request==430){
  String projectId=pendingConnectionsProject;pendingConnectionsProject=null;
  if(result==RESULT_OK&&data!=null&&data.getData()!=null){
   if(activeProject.equals(projectId))new MontageConnections(this).writePdf(data.getData());else notice("Projekt gewechselt. Export erneut starten.");
  }
  return;
 }
 if(request==429){String id=pendingMaterialProject;pendingMaterialProject=null;if(result==RESULT_OK&&data!=null&&data.getData()!=null)writeMaterialPdf(data.getData(),id);return;}
 if(request==428){java.util.function.Consumer<String> accept=pendingEditorSpeech;pendingEditorSpeech=null;if(accept!=null&&result==RESULT_OK&&data!=null){java.util.ArrayList<String> values=data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);if(values!=null&&!values.isEmpty())accept.accept(values.get(0));}return;}
 if(request==427&&result==RESULT_OK&&data!=null&&data.getData()!=null){importRouteUnderlay(data.getData());return;}
 if(request==426&&result==RESULT_OK&&data!=null&&data.getData()!=null){android.net.Uri uri=data.getData();String name=uri.getLastPathSegment();try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}catch(Exception ignored){}analyzeImageDocument(uri,name==null?"Bild":name);return;}
 if(request==425&&result==RESULT_OK&&data!=null&&data.getData()!=null){android.net.Uri uri=data.getData();String name=uri.getLastPathSegment();try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}catch(Exception ignored){}analyzePdfDocument(uri,name==null?"PDF":name);return;}
 if(request==424){if(result==RESULT_OK&&data!=null&&data.getData()!=null)writeNachtragPdf(data.getData());else pendingNachtragPdfId=null;return;}
 if(request==423){if(result==RESULT_OK&&data!=null&&data.getData()!=null)writeDailyPdf(data.getData());else pendingDailyPdfId=null;return;}
 if(request==422&&result==RESULT_OK&&data!=null&&data.getData()!=null){writePhotoReport(data.getData());return;}
 if(request==421){if(result==RESULT_OK&&data!=null&&data.getData()!=null)writeX31(data.getData());else pendingX31Data=null;return;}
 if(request==420){if(result==RESULT_OK&&data!=null&&data.getData()!=null)writeReb(data.getData());else pendingRebData=null;return;}
 if(request==419&&result==RESULT_OK&&data!=null&&data.getData()!=null){importGaeb(data.getData());return;}
 if(request==418&&result==RESULT_OK&&data!=null&&data.getData()!=null){writeCsv(data.getData());return;}
 if((request==416||request==417)&&result==RESULT_OK&&data!=null&&data.getData()!=null){writeDxf(data.getData(),request==416);return;}
 if(request==415&&result==RESULT_OK&&data!=null&&data.getData()!=null){importProjectBackup(data.getData());return;}
 if(request==414&&result==RESULT_OK&&data!=null&&data.getData()!=null){exportProjectBackup(data.getData());return;}
 if(request==413){finishPhoto(result==RESULT_OK);return;}
 if(request==412){
  PdfScope scope=pendingPdfScope;String owner=pendingPdfProject;int layers=pendingPdfLayers;pendingPdfScope=null;pendingPdfProject=null;pendingPdfLayers=-1;
  if(result==RESULT_OK&&data!=null&&data.getData()!=null){
   if(scope!=null&&activeProject.equals(owner))writePdf(data.getData(),scope,layers);else notice("Projekt gewechselt. Export erneut starten.");
  }
  return;
 }
 if(request==411&&result==RESULT_OK&&data!=null){if(data.getClipData()!=null){for(int j=0;j<data.getClipData().getItemCount();j++)attachDocument(data.getClipData().getItemAt(j).getUri(),data.getFlags());}else attachDocument(data.getData(),data.getFlags());showDocuments();return;}
 if(request==410&&result==RESULT_OK&&data!=null){java.util.ArrayList<String> values=data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);if(values!=null&&!values.isEmpty())handleSpeech(values.get(0));}}
 int pendingAssistantField=-1;
 double assistantNumber(EditText field){try{return optional(field);}catch(Exception e){return Double.NaN;}}
 boolean offerAssistantAnswer(String text){
  if(pendingAssistantField<0)return false;
  if(text.trim().matches("(?iu)^(не знаю|уточню позже|weiß nicht|später)[.!]?$")){pendingAssistantField=-1;notice("In Ordnung, das Feld bleibt offen.");return true;}
  if(!text.trim().matches("(?iu)^\\d+(?:[.,]\\d+)?\\s*(?:м|метр[а-я]*|см|сантиметр[а-я]*|мм|m|meter|cm|zentimeter|mm|millimeter)[.!]?$"))return false;
  String[] prefixes={"длина трассы ","ширина траншеи ","глубина траншеи ","толщина асфальта ","nachschnitt слева ","nachschnitt справа "};
  return offerProfileBatch(prefixes[pendingAssistantField]+text,false);
 }
 void showMissingAssistantData(){
  StringBuilder b=new StringBuilder();
  pendingAssistantField=-1;
  EditText[] fields={length,width,depth};String[] questions={"Wie lang ist die gesamte Trasse?","Wie breit ist der Graben?","Wie tief ist der Graben?","Wie dick ist der Asphalt?","Wie breit ist der Nachschnitt links?","Wie breit ist der Nachschnitt rechts?"};
  for(int i=0;i<fields.length;i++){double n=assistantNumber(fields[i]);if(!Double.isFinite(n)||n<=0){if(pendingAssistantField<0)pendingAssistantField=i;b.append("• ").append(questions[i]).append("\n");}}
  String covering=surface.getSelectedItem().toString();
  if(covering.equals("Unbekannt"))b.append("• Welche Oberflächenart liegt vor?\n");
  if(covering.equals("Asphalt")){
   if(!Double.isFinite(assistantNumber(asphaltInput))||assistantNumber(asphaltInput)<=0){if(pendingAssistantField<0)pendingAssistantField=3;b.append("• ").append(questions[3]).append("\n");}
   if(!Double.isFinite(assistantNumber(leftInput))){if(pendingAssistantField<0)pendingAssistantField=4;b.append("• ").append(questions[4]).append("\n");}
   if(!Double.isFinite(assistantNumber(rightInput))){if(pendingAssistantField<0)pendingAssistantField=5;b.append("• ").append(questions[5]).append("\n");}
  }
  for(int i=0;i<objects.length();i++){org.json.JSONObject o=objects.optJSONObject(i);if(o==null||!o.optString("type").equals("Hindernis"))continue;
   String[] keys={"detailLength","detailWidth","detailDepth"},labels={"Länge","Breite","Tiefe"};StringBuilder missing=new StringBuilder();
   for(int j=0;j<keys.length;j++){double n=detailNumber(o,keys[j]);if(!Double.isFinite(n)||n<=0){if(missing.length()>0)missing.append(", ");missing.append(labels[j]);}}
   if(missing.length()>0)b.append("• Hindernis, Station ").append(o.optString("station")).append(" m: bitte ergänzen: ").append(missing).append(".\n");
  }
  new android.app.AlertDialog.Builder(this).setTitle("Assistent – fehlende Angaben").setMessage(b.length()==0?"Die geprüften Grundmaße sind vollständig.":b.toString()).setNegativeButton("Später ergänzen",(d,w)->pendingAssistantField=-1).setPositiveButton("Antworten",(d,w)->{showPage(voicePage);voiceText.setText("");voiceText.setHint(pendingAssistantField>=0?questions[pendingAssistantField]:"Ergänzung eingeben");voiceText.requestFocus();}).show();
 }
 void applyDictation(){String text=voiceText.getText().toString();if(!offerAssistantAnswer(text))offerProfileBatch(text,true);}
 boolean offerProfileBatch(String message,boolean warn){
  String t=message.toLowerCase(java.util.Locale.ROOT);
  String[] names={"(?:grabenbreite|ширин[аой]\\s+траншеи|траншея\\s+шириной)","(?:grabentiefe|глубин[аой]\\s+траншеи|траншея\\s+глубиной)","(?:trassenlänge|длина\\s+трассы)","(?:asphaltstärke|asphaltdicke|толщина\\s+асфальта|асфальт)","(?:nachschnitt\\s+links|nachschnitt\\s+слева|подрезка\\s+слева)","(?:nachschnitt\\s+rechts|nachschnitt\\s+справа|подрезка\\s+справа)"};
  EditText[] targets={width,depth,length,asphaltInput,leftInput,rightInput};
  String[] keys={"width","depth","length","asphalt","left","right"};
  String[] labels={"Grabenbreite","Grabentiefe","Trassenlänge","Asphaltstärke","Nachschnitt links","Nachschnitt rechts"};
  String[] proposed=new String[targets.length];StringBuilder preview=new StringBuilder();boolean conflict=false;
  for(int j=0;j<names.length;j++){
   java.util.regex.Matcher m=java.util.regex.Pattern.compile(names[j]+"\\s*[:=]?\\s*(?:ist\\s+)?(\\d+(?:[.,]\\d+)?)\\s*(zentimeter|cm|сантиметр[а-я]*|см|millimeter|mm|мм|meter|метр[а-я]*|m|м)(?![\\p{L}])").matcher(t);
   while(m.find()){
    double v=Double.parseDouble(m.group(1).replace(',','.'));String unit=m.group(2);
    if(unit.equals("cm")||unit.equals("см")||unit.startsWith("сантиметр")||unit.equals("zentimeter"))v/=100;
    else if(unit.equals("mm")||unit.equals("мм")||unit.equals("millimeter"))v/=1000;
    if(!Double.isFinite(v)||v<0||(j<4&&v==0))continue;
    String next=java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString().replace('.',',');
    if(proposed[j]!=null&&!proposed[j].equals(next))conflict=true;
    proposed[j]=next;
   }
   if(proposed[j]!=null){preview.append(labels[j]).append(": ").append(proposed[j]).append(" m");
    if(targets[j].length()>0)preview.append(" (bisher: ").append(targets[j].getText()).append(" m)");
    preview.append("\n");}
  }
  if(conflict){notice("Widersprüchliche Maße erkannt. Bitte Werte prüfen.");return true;}
  if(preview.length()==0){if(warn)notice("Keine eindeutigen Graben- oder Belagsmaße erkannt.");return false;}
  new android.app.AlertDialog.Builder(this).setTitle("Erkannte Maße prüfen").setMessage(preview+"\nWeitere Angaben aus der Nachricht wurden noch nicht verarbeitet.")
   .setNegativeButton("Abbrechen",null).setPositiveButton("Übernehmen",(d,w)->{
    android.content.SharedPreferences.Editor ed=getPreferences(0).edit();
    for(int j=0;j<targets.length;j++)if(proposed[j]!=null)ed.putString(keys[j],proposed[j]);
    if(!ed.commit()){notice("Speichern fehlgeschlagen.");return;}
    for(int j=0;j<targets.length;j++)if(proposed[j]!=null)targets[j].setText(proposed[j]);
    if(length.length()>0&&width.length()>0&&depth.length()>0)calculate();
    showMissingAssistantData();
   }).show();return true;
 }
 void addSegment(){try{
 double start=number(stationFrom),end=number(stationTo),total=l,gw=number(width);
 if(!Double.isFinite(start+end+total+gw)||start<0||end<=start||end>total||gw<=0)throw new Exception();
 double a=optional(leftInput),b=optional(rightInput);
 for(int i=0;i<segments.length();i++){org.json.JSONObject old=segments.getJSONObject(i);if(start<old.getDouble("to")&&end>old.getDouble("from")){Toast.makeText(this,"Abschnitte dürfen sich nicht überschneiden.",Toast.LENGTH_LONG).show();return;}}
 org.json.JSONObject item=new org.json.JSONObject();item.put("from",start);item.put("to",end);item.put("surface",surface.getSelectedItem().toString());item.put("width",gw);
 if(Double.isFinite(a))item.put("left",a);if(Double.isFinite(b))item.put("right",b);
 if(Double.isFinite(a)&&Double.isFinite(b))item.put("cutWidth",gw+a+b);
 segments.put(item);persistSegments();
 }catch(Exception e){Toast.makeText(this,"Bitte gültige Stationsgrenzen und Breite eingeben.",Toast.LENGTH_LONG).show();}}
 void persistSegments(){getPreferences(0).edit().putString("segments",segments.toString()).apply();showSegments();refreshStationLayers();}
 void showSegments(){StringBuilder out=new StringBuilder();java.util.TreeMap<String,Double> totals=new java.util.TreeMap<>();
 for(int i=0;i<segments.length();i++){org.json.JSONObject o=segments.optJSONObject(i);if(o==null)continue;double a=o.optDouble("from"),b=o.optDouble("to"),area=(b-a)*o.optDouble("width");String type=o.optString("surface");
 out.append(String.format(java.util.Locale.GERMANY,"RP %02d | %.2f–%.2f m | %s | %.2f m² (Graben)\n",i+1,a,b,type,area));
 if(o.optDouble("cutWidth")>o.optDouble("width")+1e-9)out.append(String.format(java.util.Locale.GERMANY,"Mit Nachschnitt: %.2f m²\n",(b-a)*o.optDouble("cutWidth")));
 if("Asphalt".equals(type))out.append(asphaltCutReport(b-a,o.optDouble("width"),o.optDouble("cutWidth"),o.optDouble("left"),o.optDouble("right")));
 totals.put(type,totals.getOrDefault(type,0.0)+area);}
 for(java.util.Map.Entry<String,Double> e:totals.entrySet())out.append(String.format(java.util.Locale.GERMANY,"%s gesamt: %.2f m² (Graben)\n",e.getKey(),e.getValue()));
 segmentsView.setText(out.toString());}
 double optional(EditText e)throws Exception{if(e.length()==0)return Double.NaN;double v=number(e);if(!Double.isFinite(v)||v<0)throw new Exception();return v;}
 org.json.JSONArray extraUtilities(){
  try{return new org.json.JSONArray(getPreferences(0).getString("extraUtilities","[]"));}catch(Exception e){return new org.json.JSONArray();}
 }
 void editUtilities(){
  org.json.JSONArray rows=extraUtilities();
  java.util.ArrayList<String> labels=new java.util.ArrayList<>();
  for(int i=0;i<rows.length();i++){org.json.JSONObject o=rows.optJSONObject(i);labels.add(o.optString("type")+" · "+o.optDouble("diameter")+" mm");}
  labels.add("+ Leitung / Kabel hinzufügen");
  new android.app.AlertDialog.Builder(this).setTitle("Mehrfachbelegung der Trasse").setItems(labels.toArray(new String[0]),(dialog,index)->{
   if(index<rows.length()){utilityMenu(index);return;}
   LinearLayout form=new LinearLayout(this);form.setOrientation(1);
   Spinner type=new Spinner(this);type.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Trinkwasser","Gas","Abwasser","Fernwärme","Stromkabel","Glasfaser","Kabelschutzrohr","Sonstige Leitung"}));form.addView(type);
   EditText diameter=new EditText(this);diameter.setHint("Außendurchmesser (mm)");diameter.setInputType(8194);form.addView(diameter);utilitySlider(form,diameter,"Außendurchmesser (mm)",1,2000,1,true);
   android.app.AlertDialog add=new android.app.AlertDialog.Builder(this).setTitle("Leitung hinzufügen").setView(form).setNegativeButton("Abbrechen",null).setPositiveButton("Hinzufügen",null).create();
   add.setOnShowListener(x->add.getButton(-1).setOnClickListener(y->{try{double mm=Double.parseDouble(diameter.getText().toString().replace(',','.'));if(!Double.isFinite(mm)||mm<=0)throw new Exception();org.json.JSONObject o=new org.json.JSONObject();o.put("id",java.util.UUID.randomUUID().toString()).put("type",type.getSelectedItem().toString());o.put("diameter",mm);rows.put(o);if(saveUtilities(rows))add.dismiss();}catch(Exception e){diameter.setError("Positiven Durchmesser eingeben");}}));add.show();
  }).setNegativeButton("Schließen",null).show();
 }
 void editUtilityPosition(int index,double station,Runnable refresh){
  org.json.JSONArray rows=extraUtilities();org.json.JSONObject source=rows.optJSONObject(index);if(source==null)return;
  final String[] savedPosition={rows.toString()};String projectId=activeProject;
  final int[] selected={index};
  final org.json.JSONObject[] current={source};
  LinearLayout form=new LinearLayout(this);form.setOrientation(1);
  EditText offset=new EditText(this),depth=new EditText(this);
  EditText[] fields={offset,depth};String[] keys={"offset","axisDepth"},labels={"Abstand vom linken Grabenrand (m)","Achstiefe (m)"};
  for(int i=0;i<2;i++){fields[i].setInputType(8194);fields[i].setHint(labels[i]);fields[i].setText(source.optString(keys[i],""));form.addView(fields[i]);double existing=source.optDouble(keys[i],0);utilitySlider(form,fields[i],labels[i],0,Math.max(10,Double.isFinite(existing)?existing:0),0.01,true);}
  final java.util.Map<Integer,java.util.ArrayDeque<String[]>> undoByUtility=new java.util.HashMap<>(),redoByUtility=new java.util.HashMap<>();
  final java.util.ArrayDeque<String[]> positionHistory=new java.util.ArrayDeque<>();
  final java.util.ArrayDeque<String[]> redoHistory=new java.util.ArrayDeque<>();
  Button redoPosition=new Button(this);redoPosition.setText("Wiederholen");redoPosition.setEnabled(false);
    Button undoPosition=new Button(this);undoPosition.setText("Verschieben rückgängig");undoPosition.setEnabled(false);
  undoPosition.setOnClickListener(v->{if(positionHistory.isEmpty())return;redoHistory.push(new String[]{offset.getText().toString(),depth.getText().toString()});redoPosition.setEnabled(true);String[] previous=positionHistory.pop();offset.setText(previous[0]);depth.setText(previous[1]);undoPosition.setEnabled(!positionHistory.isEmpty());});
  redoPosition.setOnClickListener(v->{if(redoHistory.isEmpty())return;positionHistory.push(new String[]{offset.getText().toString(),depth.getText().toString()});String[] next=redoHistory.pop();offset.setText(next[0]);depth.setText(next[1]);undoPosition.setEnabled(true);redoPosition.setEnabled(!redoHistory.isEmpty());});
  final java.util.ArrayDeque<String> groupUndo=new java.util.ArrayDeque<>(),groupRedo=new java.util.ArrayDeque<>();
  final java.util.Set<Integer> group=new java.util.HashSet<>();
  final boolean[] groupMode={false};
  Button groupButton=new Button(this);groupButton.setText("Gruppe");
  final boolean[] showDimensions={false};
  final double[] zoomReset={0};
  Button resetProfileZoom=new Button(this);resetProfileZoom.setText("100%");resetProfileZoom.setContentDescription("Maßstab zurücksetzen");
  View preview=new View(this){
   final Paint pen=new Paint(3);
   double read(EditText field){try{return Double.parseDouble(field.getText().toString().trim().replace(',','.'));}catch(Exception ex){return Double.NaN;}}
   double dragScale,dragLeft,dragTop,grabX,grabY;boolean dragging; int dragPointer=-1;
   double profileZoom=1, pinchSpan, pinchZoom, panX, panY, pinchWorldX, pinchWorldY, pinchBaseScale, pinchWidth; boolean pinching;
   double span(android.view.MotionEvent e){return Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1));}
   @Override public boolean onTouchEvent(android.view.MotionEvent e){
    if(dragScale<=0)return false;
    int action=e.getActionMasked();
    if(action==android.view.MotionEvent.ACTION_POINTER_DOWN&&e.getPointerCount()==2){pinching=true;dragging=false;dragPointer=-1;pinchSpan=span(e);pinchZoom=profileZoom;pinchBaseScale=dragScale/profileZoom;pinchWidth=(getWidth()-2*(dragLeft-panX))/dragScale;pinchWorldX=((e.getX(0)+e.getX(1))/2-dragLeft)/dragScale;pinchWorldY=((e.getY(0)+e.getY(1))/2-dragTop)/dragScale;getParent().requestDisallowInterceptTouchEvent(true);return true;}
    if(pinching){if(action==android.view.MotionEvent.ACTION_MOVE&&e.getPointerCount()==2&&pinchSpan>0){double next=Math.max(1,Math.min(5,pinchZoom*span(e)/pinchSpan));double nextScale=pinchBaseScale*next;panX=(e.getX(0)+e.getX(1))/2-pinchWorldX*nextScale-(getWidth()-pinchWidth*nextScale)/2;panY=(e.getY(0)+e.getY(1))/2-pinchWorldY*nextScale-dp(12);profileZoom=next;resetProfileZoom.setText(Math.round(next*100)+"%");if(next==1){panX=0;panY=0;}invalidate();}if(action==android.view.MotionEvent.ACTION_UP||action==android.view.MotionEvent.ACTION_CANCEL){pinching=false;getParent().requestDisallowInterceptTouchEvent(false);}return true;}
    double x=read(offset),y=read(depth);
    switch(e.getActionMasked()){
     case android.view.MotionEvent.ACTION_DOWN:
      double radius=current[0].optDouble("diameter")/2000;
      if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(radius)||Math.hypot(e.getX()-dragLeft-x*dragScale,e.getY()-dragTop-y*dragScale)>Math.max(dp(20),radius*dragScale)){
       int hit=-1;double best=Double.POSITIVE_INFINITY;
       for(int k=0;k<rows.length();k++){if(k==selected[0])continue;org.json.JSONObject n=rows.optJSONObject(k);if(n==null||station<n.optDouble("routeFrom",0)||station>=n.optDouble("routeTo",Double.POSITIVE_INFINITY))continue;
        double nx=n.optDouble("offset"),ny=n.optDouble("axisDepth"),nr=n.optDouble("diameter")/2000;
        double distance=Math.hypot(e.getX()-dragLeft-nx*dragScale,e.getY()-dragTop-ny*dragScale);
        if(Double.isFinite(nr)&&nr>0&&distance<=Math.max(dp(20),nr*dragScale)&&distance<best){hit=k;best=distance;}
       }
       if(hit<0)return false;
       if(groupMode[0]){if(!group.add(hit))group.remove(hit);invalidate();return true;}
       if(!Double.isFinite(x)||!Double.isFinite(y)||x<0||y<0){notice("Zuerst gültige Maße eingeben");return true;}
       try{current[0].put("offset",x);current[0].put("axisDepth",y);}catch(Exception ex){return true;}
       undoByUtility.put(selected[0],new java.util.ArrayDeque<>(positionHistory));redoByUtility.put(selected[0],new java.util.ArrayDeque<>(redoHistory));
       selected[0]=hit;current[0]=rows.optJSONObject(hit);offset.setText(current[0].optString("offset",""));depth.setText(current[0].optString("axisDepth",""));
       positionHistory.clear();redoHistory.clear();if(undoByUtility.containsKey(hit))positionHistory.addAll(undoByUtility.get(hit));if(redoByUtility.containsKey(hit))redoHistory.addAll(redoByUtility.get(hit));undoPosition.setEnabled(!positionHistory.isEmpty());redoPosition.setEnabled(!redoHistory.isEmpty());
       notice(current[0].optString("name",current[0].optString("type")));invalidate();x=read(offset);y=read(depth);if(!Double.isFinite(x)||!Double.isFinite(y))return true;
      }
      if(groupMode[0]&&group.size()>1){try{current[0].put("offset",x);current[0].put("axisDepth",y);groupUndo.push(rows.toString());if(groupUndo.size()>50)groupUndo.removeLast();groupRedo.clear();}catch(Exception ex){return true;}positionHistory.clear();redoHistory.clear();undoPosition.setEnabled(false);redoPosition.setEnabled(false);}else{redoHistory.clear();redoPosition.setEnabled(false);positionHistory.push(new String[]{offset.getText().toString(),depth.getText().toString()});if(positionHistory.size()>50)positionHistory.removeLast();undoPosition.setEnabled(true);}
      grabX=e.getX()-dragLeft-x*dragScale;grabY=e.getY()-dragTop-y*dragScale;dragPointer=e.getPointerId(0);dragging=true;getParent().requestDisallowInterceptTouchEvent(true);return true;
     case android.view.MotionEvent.ACTION_MOVE:
      if(!dragging)return false;
      int pointerIndex=e.findPointerIndex(dragPointer);if(pointerIndex<0)return true;double targetX=Math.max(0,(e.getX(pointerIndex)-dragLeft-grabX)/dragScale),targetY=Math.max(0,(e.getY(pointerIndex)-dragTop-grabY)/dragScale);
      if(groupMode[0]&&group.size()>1){
       double dx=targetX-x,dy=targetY-y;
       for(int k:group){if(k==selected[0])continue;org.json.JSONObject n=rows.optJSONObject(k);if(n==null)continue;double nx=n.optDouble("offset"),ny=n.optDouble("axisDepth");if(!Double.isFinite(nx)||!Double.isFinite(ny))return true;dx=Math.max(dx,-nx);dy=Math.max(dy,-ny);}
       dx=Math.round(dx*100)/100.0;dy=Math.round(dy*100)/100.0;
       for(int k:group){if(k==selected[0])continue;org.json.JSONObject n=rows.optJSONObject(k);if(n==null)continue;try{n.put("offset",Math.max(0,n.optDouble("offset")+dx));n.put("axisDepth",Math.max(0,n.optDouble("axisDepth")+dy));}catch(Exception ex){return true;}}
       targetX=x+dx;targetY=y+dy;
      }
      offset.setText(String.format(java.util.Locale.US,"%.2f",targetX));
      depth.setText(String.format(java.util.Locale.US,"%.2f",targetY));return true;
     case android.view.MotionEvent.ACTION_POINTER_UP:
      if(e.getPointerId(e.getActionIndex())!=dragPointer)return dragging;
      dragging=false;dragPointer=-1;getParent().requestDisallowInterceptTouchEvent(false);return true;
     case android.view.MotionEvent.ACTION_UP:
     case android.view.MotionEvent.ACTION_CANCEL:
      boolean handled=dragging;dragging=false;dragPointer=-1;getParent().requestDisallowInterceptTouchEvent(false);if(handled&&e.getActionMasked()==android.view.MotionEvent.ACTION_UP)performClick();return handled;
     default:return dragging;
    }
   }
   @Override public boolean performClick(){super.performClick();return true;}

   final java.util.List<android.graphics.RectF> dimensionLabels=new java.util.ArrayList<>();
   int hiddenDimensions;
   void dimension(Canvas c,float ax,float ay,float bx,float by,double value){
    pen.setColor(value<0?0xffff7777:0xffeeeeee);pen.setStrokeWidth(dp(1));pen.setTextSize(dp(10));
    c.drawLine(ax,ay,bx,by,pen);float dx=bx-ax,dy=by-ay;double len=Math.hypot(dx,dy);
    if(len>0){float tx=(float)(-dy/len*dp(3)),ty=(float)(dx/len*dp(3));c.drawLine(ax-tx,ay-ty,ax+tx,ay+ty,pen);c.drawLine(bx-tx,by-ty,bx+tx,by+ty,pen);}
    String label=String.format(java.util.Locale.GERMANY,"%.2f m",value);float lx=Math.max(dp(2),Math.min(getWidth()-pen.measureText(label)-dp(2),(ax+bx)/2-pen.measureText(label)/2)),ly=Math.max(dp(12),(ay+by)/2-dp(4));float tw=pen.measureText(label),th=dp(12);android.graphics.RectF bounds=null;
    for(int attempt=0;attempt<25;attempt++){int shift=attempt==0?0:((attempt+1)/2)*(attempt%2==1?-1:1);float yy=ly+shift*dp(14);android.graphics.RectF candidate=new android.graphics.RectF(lx-dp(2),yy-th,lx+tw+dp(2),yy+dp(3));if(candidate.left<0||candidate.right>getWidth()||candidate.top<dp(28)||candidate.bottom>getHeight()-dp(16))continue;boolean occupied=false;for(android.graphics.RectF other:dimensionLabels)if(android.graphics.RectF.intersects(candidate,other)){occupied=true;break;}if(!occupied){bounds=candidate;ly=yy;break;}}
    if(bounds!=null){dimensionLabels.add(bounds);int color=pen.getColor();pen.setColor(Color.BLACK);c.drawRect(bounds,pen);pen.setColor(color);c.drawText(label,lx,ly,pen);if(Math.abs(ly-((ay+by)/2-dp(4)))>dp(14)||Math.abs(lx+tw/2-(ax+bx)/2)>dp(14)){pen.setColor(0xffaaaaaa);c.drawLine((ax+bx)/2,(ay+by)/2,lx+tw/2,ly+dp(3),pen);}}else hiddenDimensions++;
   }
   @Override protected void onDraw(Canvas c){
    super.onDraw(c);
    double width=w, height=d, x=read(offset), y=read(depth),radius=current[0].optDouble("diameter")/2000;
    android.content.SharedPreferences prefs=getPreferences(0);
    try{org.json.JSONArray sections=new org.json.JSONArray(prefs.getString("metricWidthSections","[]"));for(int k=0;k<sections.length();k++){org.json.JSONObject section=sections.getJSONObject(k);if(station>=section.optDouble("from")&&station<section.optDouble("to")){width=section.optDouble("width");break;}}}catch(Exception ignored){}
    try{String raw=prefs.getString("metricMainExcavationDepth","").trim();if(!raw.isEmpty())height=Double.parseDouble(raw.replace(',','.'));for(String line:prefs.getString("metricDepthSections","").split("\\n")){if(line.trim().isEmpty())continue;String[] parts=line.split(";",-1);if(parts.length!=3)throw new IllegalArgumentException();double from=Double.parseDouble(parts[0].trim().replace(',','.')),to=Double.parseDouble(parts[1].trim().replace(',','.'));if(station>=from&&station<to){height=Double.parseDouble(parts[2].trim().replace(',','.'));break;}}}catch(Exception ex){height=Double.NaN;}
    double gap=0,cover=0;
    try{double value=Double.parseDouble(prefs.getString("metricPipeMinGap","0").replace(',','.'));if(Double.isFinite(value)&&value>=0)gap=value;}catch(Exception ignored){}
    try{double value=Double.parseDouble(prefs.getString("metricPipeMinCover","0").replace(',','.'));if(Double.isFinite(value)&&value>=0)cover=value;}catch(Exception ignored){}

    if(zoomReset[0]!=0){profileZoom=1;panX=0;panY=0;zoomReset[0]=0;}
    dragScale=0;c.drawColor(Color.BLACK);
    if(!Double.isFinite(width)||!Double.isFinite(height)||width<=0||height<=0)return;
    double scale=Math.min((getWidth()-dp(32))/width,(getHeight()-dp(32))/height)*profileZoom;if(scale<=0)return;
    float left=(float)((getWidth()-width*scale)/2+panX),top=(float)(dp(12)+panY);
    dragScale=scale;dragLeft=left;dragTop=top;
    pen.setStyle(Paint.Style.FILL);pen.setColor(0xff303030);c.drawRect(left,top,(float)(left+width*scale),(float)(top+height*scale),pen);
    pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(dp(1));pen.setColor(0xffaaaaaa);c.drawRect(left,top,(float)(left+width*scale),(float)(top+height*scale),pen);pen.setStyle(Paint.Style.FILL);
    if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(radius)||radius<=0)return;
    boolean outside=x-radius<0||x+radius>width||y-radius<0||y+radius>height;
    boolean collision=false,clearance=false,coverWarning=y-radius<cover;
    for(int k=0;k<rows.length();k++){
     if(k==selected[0])continue;org.json.JSONObject neighbor=rows.optJSONObject(k);if(neighbor==null)continue;
     double from=Math.max(current[0].optDouble("routeFrom",0),neighbor.optDouble("routeFrom",0)),to=Math.min(current[0].optDouble("routeTo",Double.POSITIVE_INFINITY),neighbor.optDouble("routeTo",Double.POSITIVE_INFINITY));
     if(to<=from||station<from||station>=to)continue;
     double nx=neighbor.optDouble("offset"),ny=neighbor.optDouble("axisDepth"),nr=neighbor.optDouble("diameter")/2000;
     if(!Double.isFinite(nx)||!Double.isFinite(ny)||!Double.isFinite(nr)||nr<=0)continue;
     double distance=Math.hypot(x-nx,y-ny)-radius-nr;boolean overlap=distance<0;collision|=overlap;clearance|=distance<gap;
     pen.setColor(overlap?0xffef5350:distance<gap?0xffffb74d:group.contains(k)?0xff42a5f5:0xff9e9e9e);
     c.drawCircle((float)(left+nx*scale),(float)(top+ny*scale),(float)(nr*scale),pen);
    }
    c.save();c.clipRect(0,0,getWidth(),getHeight());pen.setColor(outside||collision?0xffef5350:clearance||coverWarning?0xffffb74d:0xff42a5f5);c.drawCircle((float)(left+x*scale),(float)(top+y*scale),(float)(radius*scale),pen);c.restore();
    dimensionLabels.clear();hiddenDimensions=0;
    if(showDimensions[0]){
     float cx=(float)(left+x*scale),cy=(float)(top+y*scale),rr=(float)(radius*scale),right=(float)(left+width*scale);
     dimension(c,left,cy,cx-rr,cy,x-radius);
     dimension(c,cx+rr,cy,right,cy,width-x-radius);
     dimension(c,cx,top,cx,cy-rr,y-radius);
     dimension(c,left,top,right,top,width);
     float bottom=(float)(top+height*scale);
     dimension(c,right,top,right,bottom,height);
     dimension(c,left,top,left,cy,y);
     dimension(c,cx,cy-rr,cx,cy+rr,2*radius);
     dimension(c,cx,cy+rr,cx,bottom,height-y-radius);
     for(int k=0;k<rows.length();k++){if(k==selected[0])continue;org.json.JSONObject n=rows.optJSONObject(k);if(n==null||station<n.optDouble("routeFrom",0)||station>=n.optDouble("routeTo",Double.POSITIVE_INFINITY))continue;
      double nx=n.optDouble("offset"),ny=n.optDouble("axisDepth"),nr=n.optDouble("diameter")/2000,dist=Math.hypot(nx-x,ny-y);
      if(!Double.isFinite(dist)||!Double.isFinite(nr)||nr<=0||dist<=0)continue;
      double ux=(nx-x)/dist,uy=(ny-y)/dist;
      dimension(c,(float)(left+(x+ux*radius)*scale),(float)(top+(y+uy*radius)*scale),(float)(left+(nx-ux*nr)*scale),(float)(top+(ny-uy*nr)*scale),dist-radius-nr);
     }
    }
    if(hiddenDimensions>0){pen.setColor(0xffffb74d);pen.setTextSize(dp(10));c.drawText(hiddenDimensions+" Maßangaben ausgeblendet – Ansicht vergrößern",dp(8),dp(24),pen);}
    if(clearance||coverWarning){pen.setColor(0xffffb74d);pen.setTextSize(dp(11));c.drawText(clearance&&coverWarning?"Mindestabstand / Überdeckung":clearance?"Mindestabstand unterschritten":"Mindestüberdeckung unterschritten",dp(8),dp(11),pen);}
    if(outside||collision){pen.setColor(0xffff7777);pen.setTextSize(dp(11));c.drawText(outside&&collision?"Profilgrenze und Leitungskollision":outside?"Außerhalb des Grabenprofils":"Leitungskollision",dp(8),getHeight()-dp(4),pen);}
   }
  };
  Button groupBack=new Button(this);groupBack.setText("Gruppe ↶");groupBack.setEnabled(false);
  Button groupForward=new Button(this);groupForward.setText("Gruppe ↷");groupForward.setEnabled(false);
  Runnable restoreGroup=()->{preview.invalidate();groupBack.setEnabled(!groupUndo.isEmpty());groupForward.setEnabled(!groupRedo.isEmpty());};
  groupBack.setOnClickListener(v->{if(groupUndo.isEmpty())return;try{current[0].put("offset",Double.parseDouble(offset.getText().toString().replace(',','.')));current[0].put("axisDepth",Double.parseDouble(depth.getText().toString().replace(',','.')));org.json.JSONArray previous=new org.json.JSONArray(groupUndo.peek());groupRedo.push(rows.toString());groupUndo.pop();for(int k=0;k<rows.length();k++)rows.put(k,previous.getJSONObject(k));current[0]=rows.optJSONObject(selected[0]);offset.setText(current[0].optString("offset"));depth.setText(current[0].optString("axisDepth"));restoreGroup.run();}catch(Exception ex){notice("Ungültige Maße");}});
  groupForward.setOnClickListener(v->{if(groupRedo.isEmpty())return;try{current[0].put("offset",Double.parseDouble(offset.getText().toString().replace(',','.')));current[0].put("axisDepth",Double.parseDouble(depth.getText().toString().replace(',','.')));org.json.JSONArray next=new org.json.JSONArray(groupRedo.peek());groupUndo.push(rows.toString());groupRedo.pop();for(int k=0;k<rows.length();k++)rows.put(k,next.getJSONObject(k));current[0]=rows.optJSONObject(selected[0]);offset.setText(current[0].optString("offset"));depth.setText(current[0].optString("axisDepth"));restoreGroup.run();}catch(Exception ex){notice("Ungültige Maße");}});
    groupButton.setOnClickListener(v->{groupMode[0]=!groupMode[0];group.clear();if(groupMode[0])group.add(selected[0]);groupButton.setText(groupMode[0]?"Gruppe ✓":"Gruppe");preview.invalidate();});
  Button alignGroup=new Button(this);alignGroup.setText("Gruppe ausrichten");
  alignGroup.setOnClickListener(v->{
   if(!groupMode[0]||group.size()<2){notice("Mindestens zwei Leitungen auswählen");return;}
   new android.app.AlertDialog.Builder(this).setTitle("Gruppe ausrichten").setItems(new String[]{"Gleiche Achstiefe","Gleicher Seitenabstand"},(dd,choice)->{
    try{
     double x=Double.parseDouble(offset.getText().toString().replace(',','.')),y=Double.parseDouble(depth.getText().toString().replace(',','.'));
     if(!Double.isFinite(x)||!Double.isFinite(y)||x<0||y<0)throw new IllegalArgumentException();
     current[0].put("offset",x);current[0].put("axisDepth",y);
     groupUndo.push(rows.toString());if(groupUndo.size()>50)groupUndo.removeLast();groupRedo.clear();
     for(int k:group){org.json.JSONObject n=rows.optJSONObject(k);if(n!=null)n.put(choice==0?"axisDepth":"offset",choice==0?y:x);}
     restoreGroup.run();
    }catch(Exception ex){notice("Gültige Maße eingeben");}
   }).setNegativeButton("Abbrechen",null).show();
  });
  Button distribute=new Button(this);distribute.setText("Gruppe mit Mindestabstand verteilen");
  distribute.setOnClickListener(v->{
   if(!groupMode[0]||group.size()<2){notice("Mindestens zwei Leitungen auswählen");return;}
   try{
    double x=Double.parseDouble(offset.getText().toString().replace(',','.')),y=Double.parseDouble(depth.getText().toString().replace(',','.'));
    double gap=Double.parseDouble(getPreferences(0).getString("metricPipeMinGap","0").replace(',','.'));
    if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(gap)||x<0||y<0||gap<0)throw new IllegalArgumentException();
    current[0].put("offset",x);current[0].put("axisDepth",y);
    java.util.List<Integer> ordered=new java.util.ArrayList<>(group);
    ordered.sort((aa,bb)->Double.compare(rows.optJSONObject(aa).optDouble("offset"),rows.optJSONObject(bb).optDouble("offset")));
    double margin=Double.parseDouble(getPreferences(0).getString("metricUtilitySideClearance","0").replace(',','.'));
    if(!Double.isFinite(margin)||margin<0)throw new IllegalArgumentException();
    double edge=margin;double[] centers=new double[ordered.size()];
    for(int k=0;k<ordered.size();k++){org.json.JSONObject n=rows.optJSONObject(ordered.get(k));double radius=n.optDouble("diameter")/2000;if(!Double.isFinite(radius)||radius<=0)throw new IllegalArgumentException();centers[k]=edge+radius;edge+=2*radius+gap;}
    double required=edge-gap+margin;
    double available=w;
    try{org.json.JSONArray sections=new org.json.JSONArray(getPreferences(0).getString("metricWidthSections","[]"));for(int k=0;k<sections.length();k++){org.json.JSONObject section=sections.getJSONObject(k);if(station>=section.optDouble("from")&&station<section.optDouble("to")){available=section.optDouble("width");break;}}}catch(Exception ignored){}
    if(!Double.isFinite(available)||available<=0||required>available+1e-8){notice(String.format(java.util.Locale.GERMANY,"Graben zu schmal: benötigt %.3f m, vorhanden %.3f m",required,available));return;}
    groupUndo.push(rows.toString());if(groupUndo.size()>50)groupUndo.removeLast();groupRedo.clear();
    for(int k=0;k<ordered.size();k++){org.json.JSONObject n=rows.optJSONObject(ordered.get(k));n.put("offset",centers[k]);n.put("axisDepth",y);}
    offset.setText(current[0].optString("offset"));depth.setText(current[0].optString("axisDepth"));restoreGroup.run();
    notice(String.format(java.util.Locale.GERMANY,"Benötigte Breite mit Randabstand: %.3f m",required));
   }catch(Exception ex){notice("Gültige Durchmesser und Maße erforderlich");}
  });
  Button sideClearance=new Button(this);sideClearance.setText("Seitlichen Randabstand einstellen");
  sideClearance.setOnClickListener(v->{EditText input=new EditText(this);input.setInputType(8194);input.setText(getPreferences(0).getString("metricUtilitySideClearance","0"));android.app.AlertDialog dd=new android.app.AlertDialog.Builder(this).setTitle("Randabstand je Seite (m)").setView(input).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();dd.setOnShowListener(q->dd.getButton(-1).setOnClickListener(z->{try{double m=Double.parseDouble(input.getText().toString().replace(',','.'));if(!Double.isFinite(m)||m<0)throw new IllegalArgumentException();getPreferences(0).edit().putString("metricUtilitySideClearance",String.valueOf(m)).apply();dd.dismiss();}catch(Exception ex){input.setError("Gültiges Maß eingeben");}}));dd.show();});
  form.addView(sideClearance);
  form.addView(distribute);
  Button dimensions=new Button(this);dimensions.setText("Bemaßung anzeigen");dimensions.setOnClickListener(v->{showDimensions[0]=!showDimensions[0];dimensions.setText(showDimensions[0]?"Bemaßung ausblenden":"Bemaßung anzeigen");preview.invalidate();});form.addView(dimensions);
  form.addView(alignGroup);
  form.addView(groupBack);form.addView(groupForward);
  form.addView(preview,0,new LinearLayout.LayoutParams(-1,dp(160)));
  Button expandPreview=new Button(this);expandPreview.setText("Querschnitt vergrößern");form.addView(expandPreview,1);
  resetProfileZoom.setOnClickListener(v->{zoomReset[0]=1;resetProfileZoom.setText("100%");preview.invalidate();});

  for(EditText field:fields)field.addTextChangedListener(new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){preview.invalidate();groupBack.setEnabled(!groupUndo.isEmpty());groupForward.setEnabled(!groupRedo.isEmpty());}
   public void afterTextChanged(android.text.Editable e){}
  });
  ScrollView positionScroll=new ScrollView(this);positionScroll.setFillViewport(false);positionScroll.addView(form);
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Leitungslage – Querschnitt").setView(positionScroll).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();
  Button fullscreen=new Button(this);fullscreen.setText("Vollbild");form.addView(fullscreen,2);
  Button toggleControls=new Button(this);toggleControls.setText("Maße −");form.addView(toggleControls,3);form.addView(undoPosition,4);form.addView(redoPosition,5);
  toggleControls.setOnClickListener(v->{
   boolean hide=toggleControls.getText().toString().equals("Maße −");
   for(int k=2;k<form.getChildCount();k++)form.getChildAt(k).setVisibility(hide?View.GONE:View.VISIBLE);
   toggleControls.setText(hide?"Maße +":"Maße −");
  });
  LinearLayout toolbar=new LinearLayout(this);toolbar.setOrientation(0);
  for(Button b:new Button[]{undoPosition,redoPosition,fullscreen,toggleControls}){form.removeView(b);toolbar.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setMinWidth(0);b.setMinimumWidth(0);b.setTextSize(11);}
  form.removeView(preview);
  android.widget.FrameLayout workspace=new android.widget.FrameLayout(this);
  workspace.addView(preview,new android.widget.FrameLayout.LayoutParams(-1,-1));
  form.addView(workspace,0,new LinearLayout.LayoutParams(-1,dp(210)));
  preview.setContentDescription("Leitungsquerschnitt. Ausgewählte Leitung zum Verschieben ziehen.");

  android.widget.FrameLayout.LayoutParams barParams=new android.widget.FrameLayout.LayoutParams(Math.min(dp(280),Math.max(dp(160),getResources().getDisplayMetrics().widthPixels-dp(96))),-2,android.view.Gravity.TOP|android.view.Gravity.LEFT);
  workspace.addView(toolbar,barParams);android.widget.FrameLayout.LayoutParams zoomParams=new android.widget.FrameLayout.LayoutParams(dp(72),-2,android.view.Gravity.BOTTOM|android.view.Gravity.RIGHT);workspace.addView(resetProfileZoom,zoomParams);Button saveProfile=new Button(this);saveProfile.setText("Speichern");android.widget.FrameLayout.LayoutParams saveParams=new android.widget.FrameLayout.LayoutParams(-2,-2,android.view.Gravity.BOTTOM|android.view.Gravity.LEFT);workspace.addView(saveProfile,saveParams);resetProfileZoom.setTextSize(11);resetProfileZoom.setMinWidth(0);resetProfileZoom.setMinimumWidth(0);toolbar.addView(groupButton,new LinearLayout.LayoutParams(0,-2,1));groupButton.setMinWidth(0);groupButton.setMinimumWidth(0);groupButton.setPadding(0,0,0,0);groupButton.setTextSize(10);toolbar.setBackgroundColor(0xff303030);
  for(Button tool:new Button[]{undoPosition,redoPosition,fullscreen,toggleControls,groupButton,resetProfileZoom,saveProfile}){styleEditorButton(tool,tool==saveProfile);tool.setMinHeight(dp(44));tool.setMinimumHeight(dp(44));}
  toolbar.setBackground(editorShape(0xff202827,12));
  fullscreen.setTextSize(10);toggleControls.setTextSize(10);
  fullscreen.setPadding(dp(2),0,dp(2),0);toggleControls.setPadding(dp(2),0,dp(2),0);
  undoPosition.setPadding(0,0,0,0);redoPosition.setPadding(0,0,0,0);

  workspace.addOnLayoutChangeListener((v,l,t,rr,b,ol,ot,orr,ob)->{toolbar.setTranslationX(Math.max(0,Math.min(workspace.getWidth()-toolbar.getWidth(),toolbar.getTranslationX())));toolbar.setTranslationY(Math.max(0,Math.min(workspace.getHeight()-toolbar.getHeight(),toolbar.getTranslationY())));});
  TextView handle=new TextView(this);handle.setText("⠿");handle.setTextSize(22);handle.setPadding(dp(8),dp(6),dp(8),dp(6));handle.setContentDescription("Werkzeugleiste verschieben");toolbar.addView(handle,0);
  handle.setOnTouchListener(new View.OnTouchListener(){
   float startY,baseY,startX,baseX;
   public boolean onTouch(View v,android.view.MotionEvent e){
    switch(e.getActionMasked()){
     case android.view.MotionEvent.ACTION_DOWN:startY=e.getRawY();baseY=toolbar.getTranslationY();startX=e.getRawX();baseX=toolbar.getTranslationX();v.getParent().requestDisallowInterceptTouchEvent(true);return true;
     case android.view.MotionEvent.ACTION_MOVE:toolbar.setTranslationX(Math.max(0,Math.min(workspace.getWidth()-toolbar.getWidth(),baseX+e.getRawX()-startX)));toolbar.setTranslationY(Math.max(0,Math.min(workspace.getHeight()-toolbar.getHeight(),baseY+e.getRawY()-startY)));return true;
     case android.view.MotionEvent.ACTION_UP:case android.view.MotionEvent.ACTION_CANCEL:v.getParent().requestDisallowInterceptTouchEvent(false);return true;
     default:return true;
    }
   }
  });
  undoPosition.setText("↶");undoPosition.setContentDescription("Verschieben rückgängig");
  redoPosition.setText("↷");redoPosition.setContentDescription("Wiederholen");
  expandPreview.setOnClickListener(v->{
   android.view.ViewGroup.LayoutParams lp=workspace.getLayoutParams();
   boolean expanded=lp.height>dp(210);
   lp.height=expanded?dp(210):Math.max(dp(240),getResources().getDisplayMetrics().heightPixels-dp(280));
   workspace.setLayoutParams(lp);toolbar.setTranslationY(0);toolbar.setTranslationX(0);expandPreview.setText(expanded?"Querschnitt vergrößern":"Querschnitt verkleinern");
  });
  fullscreen.setOnClickListener(v->{
   android.view.Window window=dialog.getWindow();if(window==null)return;
   boolean full=fullscreen.getText().toString().equals("Vollbild");
   window.setLayout(full?-1:-2,full?-1:-2);
   window.getDecorView().setSystemUiVisibility(full?5894:0);
   android.view.ViewGroup.LayoutParams lp=workspace.getLayoutParams();
   lp.height=full?Math.max(dp(160),getResources().getDisplayMetrics().heightPixels-dp(200)):dp(210);
   workspace.setLayoutParams(lp);toolbar.setTranslationY(0);toolbar.setTranslationX(0);fullscreen.setText(full?"Normal":"Vollbild");
   expandPreview.setText(full?"Querschnitt verkleinern":"Querschnitt vergrößern");
  });
  java.util.function.Consumer<Boolean> persistPosition=close->{
   if(!projectId.equals(activeProject)||!savedPosition[0].equals(extraUtilities().toString())){notice("Projektdaten geändert. Leitung erneut öffnen.");return;}
   try{
    org.json.JSONObject updated=new org.json.JSONObject(current[0].toString());
    for(int i=0;i<2;i++){double value;try{value=Double.parseDouble(fields[i].getText().toString().trim().replace(',','.'));if(!Double.isFinite(value)||value<0)throw new NumberFormatException();}catch(Exception ex){fields[i].setError("Gültiges Maß eingeben");return;}updated.put(keys[i],value);}
    org.json.JSONArray next=new org.json.JSONArray(rows.toString());next.put(selected[0],updated);
    if(saveUtilities(next)){rows.put(selected[0],updated);current[0]=updated;savedPosition[0]=extraUtilities().toString();recordProjectEvent("Leitungslage geändert");if(close){dialog.dismiss();if(refresh!=null)refresh.run();}else{notice("Leitungslage gespeichert");preview.invalidate();if(refresh!=null)refresh.run();}}
   }catch(Exception ex){notice("Leitungslage konnte nicht gespeichert werden.");}
  };saveProfile.setOnClickListener(v->persistPosition.accept(false));dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(y->persistPosition.accept(true)));dialog.show();
 }
 void utilityMenu(int index){
  org.json.JSONArray rows=extraUtilities();org.json.JSONObject o=rows.optJSONObject(index);if(o==null)return;
  new android.app.AlertDialog.Builder(this).setTitle(o.optString("name",o.optString("type"))).setItems(new String[]{"Eigenschaften bearbeiten","Leitung entfernen","Montagedaten und Einbaulänge"},(dialog,choice)->{
   if(choice==2){editInstalledUtility(rows,index);return;}
   if(choice==1){new android.app.AlertDialog.Builder(this).setMessage("Leitung entfernen?").setNegativeButton("Abbrechen",null).setPositiveButton("Entfernen",(x,y)->{rows.remove(index);saveUtilities(rows);}).show();return;}
   LinearLayout form=new LinearLayout(this);form.setOrientation(1);
   String[] keys={"name","type","material","dn","diameter"},titles={"Bezeichnung","Leitungsart","Material","DN / Kabeltyp","Außendurchmesser (mm)"};
   EditText[] fields=new EditText[keys.length];for(int j=0;j<keys.length;j++){fields[j]=new EditText(this);fields[j].setHint(titles[j]);fields[j].setText(o.optString(keys[j],""));if(j==4)fields[j].setInputType(8194);form.addView(fields[j]);}
   utilitySlider(form,fields[4],"Außendurchmesser (mm)",1,2000,1,true);
   android.app.AlertDialog edit=new android.app.AlertDialog.Builder(this).setTitle("Leitung bearbeiten").setView(form).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();
   edit.setOnShowListener(x->edit.getButton(-1).setOnClickListener(y->{try{double mm=Double.parseDouble(fields[4].getText().toString().replace(',','.'));if(!Double.isFinite(mm)||mm<=0)throw new Exception();if(fields[1].length()==0){fields[1].setError("Leitungsart eingeben");return;}for(int j=0;j<4;j++)o.put(keys[j],fields[j].getText().toString().trim());o.put("diameter",mm);if(saveUtilities(rows))edit.dismiss();}catch(Exception e){fields[4].setError("Positiven Durchmesser eingeben");}}));edit.show();
  }).show();
 }
 void editInstalledUtility(org.json.JSONArray rows,int index){
  org.json.JSONObject source=rows.optJSONObject(index);if(source==null)return;
  String before=rows.toString(),projectId=activeProject;
  LinearLayout form=page();ScrollView scroll=new ScrollView(this);scroll.addView(form);
  form.addView(sheetText(source.optString("type")+" · "+source.optDouble("diameter")+" mm",16,true));
  String[] keys={"sdr","pn","installedLength"},labels={"SDR (optional)","PN (optional)","Gemessene Einbaulänge (m)"};
  EditText[] fields=new EditText[keys.length];
  for(int i=0;i<keys.length;i++){
   form.addView(sheetText(labels[i],14,true));fields[i]=new EditText(this);fields[i].setSingleLine(true);
   fields[i].setInputType(8194);fields[i].setText(source.optString(keys[i],""));form.addView(fields[i]);
  }
  EditText stationFrom=new EditText(this),stationTo=new EditText(this);
  stationFrom.setInputType(8194);stationTo.setInputType(8194);
  stationFrom.setHint("Station von (m, optional)");stationTo.setHint("Station bis (m, optional)");
  stationFrom.setText(source.optString("routeFrom",""));stationTo.setText(source.optString("routeTo",""));form.addView(stationFrom);form.addView(stationTo);
  TextView stationLength=sheetText("",14,false);form.addView(stationLength);
  Runnable updateStationLength=()->{try{double from=Double.parseDouble(stationFrom.getText().toString().trim().replace(',','.')),to=Double.parseDouble(stationTo.getText().toString().trim().replace(',','.'));if(!Double.isFinite(from)||!Double.isFinite(to)||from<0||to<=from||(Double.isFinite(l)&&l>0&&to>l))throw new IllegalArgumentException();stationLength.setText(String.format(java.util.Locale.GERMANY,"Stationslänge: %.2f m (nicht gemessene Einbaulänge)",to-from));}catch(Exception ex){stationLength.setText("Stationslänge: –");}};
  for(EditText stationField:new EditText[]{stationFrom,stationTo})stationField.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){updateStationLength.run();}public void afterTextChanged(android.text.Editable e){}});
  updateStationLength.run();
  CheckBox installed=new CheckBox(this);installed.setText("Einbau bestätigen und Länge in Materialliste übernehmen");
  installed.setChecked(source.optBoolean("installed",false));form.addView(installed);
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Leitungsmontage").setView(scroll)
    .setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();
  dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(button->{
   if(!projectId.equals(activeProject)||!before.equals(extraUtilities().toString())){notice("Projektdaten geändert. Leitung erneut öffnen.");return;}
   try{
    org.json.JSONObject updated=new org.json.JSONObject(source.toString());
    for(int i=0;i<fields.length;i++){
     String raw=fields[i].getText().toString().trim();
     if(raw.isEmpty()){updated.remove(keys[i]);continue;}
     double value;
     try{value=Double.parseDouble(raw.replace(',','.'));if(!Double.isFinite(value)||value<=0)throw new NumberFormatException();}
     catch(NumberFormatException invalid){fields[i].setError("Positiven Wert eingeben");return;}
     updated.put(keys[i],value);
    }
    if(installed.isChecked()&&!updated.has("installedLength")){fields[2].setError("Gemessene Einbaulänge erforderlich");return;}
    String fromRaw=stationFrom.getText().toString().trim(),toRaw=stationTo.getText().toString().trim();
    if(fromRaw.isEmpty()&&toRaw.isEmpty()){updated.remove("routeFrom");updated.remove("routeTo");}
    else{
     double from,to;try{from=Double.parseDouble(fromRaw.replace(',','.'));to=Double.parseDouble(toRaw.replace(',','.'));}catch(Exception invalid){stationTo.setError("Beide Stationen eingeben");return;}
     if(!Double.isFinite(from)||!Double.isFinite(to)||from<0||to<=from||(Double.isFinite(l)&&l>0&&to>l)){stationTo.setError("Ungültiger Stationsbereich");return;}
     updated.put("routeFrom",from);updated.put("routeTo",to);
    }
    updated.put("installed",installed.isChecked());
    if(!updated.has("id"))updated.put("id",java.util.UUID.randomUUID().toString());
    org.json.JSONArray next=new org.json.JSONArray(before);next.put(index,updated);
    if(saveUtilities(next)){dialog.dismiss();recordProjectEvent("Leitungsmontage geändert");editUtilities();}
   }catch(Exception e){notice("Montagedaten konnten nicht gespeichert werden.");}
  }));dialog.show();
 }
 boolean utilityOutsideProfile(org.json.JSONObject u,double width,double depth){
  if(u==null)return false;
  double radius=u.optDouble("diameter")/2000,ox=u.optDouble("offset"),oy=u.optDouble("axisDepth");
  if(!Double.isFinite(radius)||radius<=0)return false;
  return (Double.isFinite(width)&&width>0&&(radius*2>width+1e-8||(Double.isFinite(ox)&&(ox-radius< -1e-8||ox+radius>width+1e-8))))||(Double.isFinite(depth)&&depth>0&&(radius*2>depth+1e-8||(Double.isFinite(oy)&&(oy-radius< -1e-8||oy+radius>depth+1e-8))));
 }
 int utilityCollisionCount(org.json.JSONArray rows,double length){
  int conflicts=0;
  for(int j=0;j<rows.length();j++)for(int k=j+1;k<rows.length();k++){
   org.json.JSONObject u=rows.optJSONObject(j),v=rows.optJSONObject(k);if(u==null||v==null)continue;
   double ud=u.optDouble("diameter")/1000,vd=v.optDouble("diameter")/1000,ux=u.optDouble("offset"),vx=v.optDouble("offset"),uy=u.optDouble("axisDepth"),vy=v.optDouble("axisDepth");
   double from=Math.max(0,Math.max(u.optDouble("routeFrom",0),v.optDouble("routeFrom",0))),to=Math.min(length,Math.min(u.optDouble("routeTo",Double.POSITIVE_INFINITY),v.optDouble("routeTo",Double.POSITIVE_INFINITY)));
   if(to>from&&Double.isFinite(ud)&&Double.isFinite(vd)&&ud>0&&vd>0&&Double.isFinite(ux)&&Double.isFinite(vx)&&Double.isFinite(uy)&&Double.isFinite(vy)&&Math.hypot(ux-vx,uy-vy)<(ud+vd)/2)conflicts++;
  }
  return conflicts;
 }
 boolean saveUtilities(org.json.JSONArray rows){
  boolean ok=getPreferences(0).edit().putString("extraUtilities",rows.toString()).commit();
  if(ok){if(profile!=null)profile.invalidate();if(sketch!=null)sketch.invalidate();if(livePreview!=null)livePreview.invalidate();}else notice("Leitungen konnten nicht gespeichert werden.");return ok;
 }
 double profileShoringStation=0;
 double[] shoringAtStation(double station){
  double[] heights={0,0};
  try{org.json.JSONArray rows=new org.json.JSONArray(getPreferences(0).getString("metricVerbauSections","[]"));for(int i=0;i<rows.length();i++){org.json.JSONArray row=rows.getJSONArray(i);if(station>=row.getDouble(0)&&(station<row.getDouble(1)||(station==l&&station==row.getDouble(1)))){for(int side=0;side<2;side++){double h=row.getDouble(side+2);if(Double.isFinite(h)&&h>0)heights[side]=Math.max(heights[side],h);}}}}catch(Exception ignored){}
  return heights;
 }
 class Profile extends View{
 Paint p=new Paint(3);
 final java.util.ArrayList<android.graphics.RectF> utilityHits=new java.util.ArrayList<>();
 Profile(){super(MainActivity.this);setClickable(true);}
 @Override public boolean performClick(){super.performClick();return true;}
 int dragUtility=-1;float downX,downY,profileLeft,profileTop,profileScale;boolean utilityMoved;org.json.JSONArray dragRows;
 @Override public boolean onTouchEvent(android.view.MotionEvent e){
  float xx=e.getX()*360f/getWidth(),yy=e.getY()*300f/getHeight();
  switch(e.getActionMasked()){
   case android.view.MotionEvent.ACTION_DOWN:
    dragUtility=-1;utilityMoved=false;downX=xx;downY=yy;
    for(int i=utilityHits.size()-1;i>=0;i--){android.graphics.RectF hit=utilityHits.get(i);if(hit!=null&&hit.contains(xx,yy)){dragUtility=i;dragRows=extraUtilities();getParent().requestDisallowInterceptTouchEvent(true);break;}}
    return true;
   case android.view.MotionEvent.ACTION_MOVE:
    if(dragUtility>=0&&profileScale>0){
     if(Math.hypot(xx-downX,yy-downY)>3)utilityMoved=true;
     if(utilityMoved){org.json.JSONObject o=dragRows.optJSONObject(dragUtility);double radius=o.optDouble("diameter")/2000;
      if(radius*2<=w&&radius*2<=d)try{o.put("offset",Math.max(radius,Math.min(w-radius,(xx-profileLeft)/profileScale)));o.put("axisDepth",Math.max(radius,Math.min(d-radius,(yy-profileTop)/profileScale)));invalidate();}catch(Exception ignored){}
     }
    }return true;
   case android.view.MotionEvent.ACTION_UP:
    int selected=dragUtility;dragUtility=-1;getParent().requestDisallowInterceptTouchEvent(false);
    if(selected>=0){if(utilityMoved){saveUtilities(dragRows);}else{performClick();utilityMenu(selected);}}dragRows=null;invalidate();return true;
   case android.view.MotionEvent.ACTION_CANCEL:
    dragUtility=-1;dragRows=null;getParent().requestDisallowInterceptTouchEvent(false);invalidate();return true;
  }return true;
 }
 String m(double x){return String.format(java.util.Locale.GERMANY,"%.2f m",x);}
 void label(Canvas c,String s,float x,float y){p.setColor(0xffcbd6d5);p.setTextSize(12);c.drawText(s,x,y,p);}
 void soil(Canvas c,float a,float t,float b,float end,int fill,int grain){
 if(b<=a||end<=t)return; p.setStyle(Paint.Style.FILL);p.setColor(fill);c.drawRect(a,t,b,end,p);c.save();c.clipRect(a,t,b,end);p.setColor(grain);p.setStrokeWidth(0.65f);for(int row=0;row<(end-t)/7+1;row++){for(float xx=a+3+(row%2)*4;xx<b;xx+=9){float yy=t+3+row*7;c.drawLine(xx,yy,xx+2,yy-1,p);}}c.restore();
 }
 void pipeSymbol(Canvas c,float cx,float cy,float radius){int color=p.getColor();p.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy,radius,p);p.setColor(0xff101719);c.drawCircle(cx,cy,radius*0.70f,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(0.8f);p.setColor(color);c.drawCircle(cx,cy,radius,p);p.setStyle(Paint.Style.FILL);}
 protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(Color.BLACK);utilityHits.clear();if(!Double.isFinite(w)||!Double.isFinite(d)||w<=0||d<=0)return;
 c.save();c.scale(getWidth()/360f,getHeight()/300f);
 double nl=Double.isFinite(left)?left:0,nr=Double.isFinite(right)?right:0;
 float scale=(float)Math.min(190/(w+nl+nr),155/d);
 float x=180-(float)(w*scale/2),z=180+(float)(w*scale/2),top=55,bottom=top+(float)(d*scale);
 profileLeft=x;profileTop=top;profileScale=scale;
 soil(c,20,top,x,bottom,0xff302922,0xff78634d);soil(c,z,top,340,bottom,0xff302922,0xff78634d);
 soil(c,x,top,z,bottom,0xff303b35,0xff64766a);
 float layerTop=top;
 if(Double.isFinite(topThickness)){
 RouteSketch style=new RouteSketch(MainActivity.this,"[]");style.bindDimensionProject(activeProject);
 org.json.JSONObject settings=style.legendSettings();
 int material=style.configuredSurfaceColor(covering,settings);p.setColor(android.graphics.Color.rgb((android.graphics.Color.red(material)+100)/2,(android.graphics.Color.green(material)+110)/2,(android.graphics.Color.blue(material)+115)/2));layerTop=top+(float)(topThickness*scale);c.drawRect(20,top,340,layerTop,p);
 int hatch=style.configuredHatch(covering,settings);
 if(hatch>=0&&layerTop>top){c.save();c.clipRect(20,top,340,layerTop);float band=12*getResources().getDisplayMetrics().density;for(float yy=top+band/2;yy<layerTop+band/2;yy+=band){c.save();c.translate(20,yy);style.drawHatchBand(c,320,band/12,hatch);c.restore();}c.restore();}
 }
 if(Double.isFinite(base)){soil(c,20,layerTop,340,Math.min(bottom,layerTop+(float)(base*scale)),0xff666052,0xffa39a83);}
 if(!covering.equals("Asphalt")&&!covering.equals("Unbekannt")&&!covering.equals("Unbefestigt")&&!covering.equals("Sonstiges")){p.setColor(0xff777777);c.drawLine(20,top,340,top,p);for(int i=20;i<340;i+=covering.contains("platten")||covering.contains("Platten")?28:12)c.drawLine(i,top-4,i,top,p);}
 if("Asphalt".equals(covering)){
  // Original cut edges and additional recut edges are distinct.
  float cutBottom=Math.max(top+9,layerTop);
  p.setStrokeWidth(1.5f);p.setColor(0xffd5d9dc);
  c.drawLine(x,top-5,x,cutBottom,p);c.drawLine(z,top-5,z,cutBottom,p);
  p.setColor(0xffffb267);
  android.graphics.PathEffect prior=p.getPathEffect();
  p.setPathEffect(new android.graphics.DashPathEffect(new float[]{4,2},0));
  if(nl>0){float edge=x-(float)(nl*scale);c.drawLine(edge,top-5,edge,cutBottom,p);}
  if(nr>0){float edge=z+(float)(nr*scale);c.drawLine(edge,top-5,edge,cutBottom,p);}
  p.setPathEffect(prior);
 }
 double previewPipe=pipe;try{previewPipe=optional(pipeInput);}catch(Exception ignored){}
 org.json.JSONArray utilities=dragRows==null?extraUtilities():dragRows;
 int conflicts=utilityCollisionCount(utilities,Double.isFinite(l)&&l>0?l:Double.POSITIVE_INFINITY);
 if(conflicts>0){p.setColor(0xffff927d);p.setTextSize(11);c.drawText("Achtung: "+conflicts+" mögliche Leitungskollision(en)",20,40,p);}
 int outside=0;
 for(int i=0;i<utilities.length();i++)if(utilityOutsideProfile(utilities.optJSONObject(i),w,d))outside++;
 if(outside>0){p.setColor(0xffff927d);p.setTextSize(11);c.drawText("Profilgrenzen verletzt: "+outside+" Leitung(en)",20,52,p);}

 if(utilities.length()==0){if(Double.isFinite(previewPipe)){float radius=(float)(previewPipe/2000*scale);p.setColor(0xff3686bc);pipeSymbol(c,180,bottom-radius,radius);}}
 else{
  double sum=Double.isFinite(previewPipe)?previewPipe/1000:0;for(int i=0;i<utilities.length();i++)sum+=utilities.optJSONObject(i).optDouble("diameter",0)/1000;
  int count=utilities.length()+(Double.isFinite(previewPipe)?1:0);
  float gap=(float)((w-sum)*scale/(count+1)),cursor=x+gap;
  if(gap<0){gap=0;cursor=x;label(c,"Leitungsanordnung prüfen",20,224);}
  {
   if(Double.isFinite(previewPipe)){float radius=(float)(previewPipe/2000*scale);p.setColor(0xff3686bc);pipeSymbol(c,cursor+radius,bottom-radius,radius);cursor+=2*radius+gap;}
   for(int i=0;i<utilities.length();i++){org.json.JSONObject o=utilities.optJSONObject(i);float radius=(float)(o.optDouble("diameter",0)/2000*scale);String type=o.optString("type");p.setColor(type.equals("Gas")?0xffe9b63c:type.equals("Trinkwasser")?0xff3686bc:0xffc66d56);if(radius*2<=bottom-top&&radius*2<=z-x){float cx=(float)(x+o.optDouble("offset",(cursor+radius-x)/scale)*scale),cy=(float)(top+o.optDouble("axisDepth",(bottom-radius-top)/scale)*scale);cx=Math.max(x+radius,Math.min(z-radius,cx));cy=Math.max(top+radius,Math.min(bottom-radius,cy));pipeSymbol(c,cx,cy,radius);utilityHits.add(new android.graphics.RectF(cx-radius,cy-radius,cx+radius,cy+radius));}else{utilityHits.add(null);label(c,"Leitungstiefe prüfen",20,224);}cursor+=2*radius+gap;}
  }
 }

 p.setColor(0xffd0dedb);p.setStrokeWidth(1.2f);c.drawLine(x,top,x,bottom,p);c.drawLine(z,top,z,bottom,p);c.drawLine(x,bottom,z,bottom,p);
 p.setColor(0xff28d5c7);c.drawLine(x,bottom+14,z,bottom+14,p);c.drawLine(x,bottom+8,x,bottom+20,p);c.drawLine(z,bottom+8,z,bottom+20,p);
 p.setTextSize(13);String widthLabel=m(w);c.drawText(widthLabel,(x+z-p.measureText(widthLabel))/2,bottom+29,p);
 c.drawLine(z+12,top,z+12,bottom,p);c.drawLine(z+7,top,z+17,top,p);c.drawLine(z+7,bottom,z+17,bottom,p);c.drawText(m(d),z+18,(top+bottom)/2,p);
 if(Double.isFinite(left)&&Double.isFinite(right)&&(nl+nr)>0){float a=x-(float)(nl*scale),b=z+(float)(nr*scale);c.drawLine(a,35,b,35,p);c.drawLine(a,30,a,45,p);c.drawLine(b,30,b,45,p);p.setTextSize(13);String cutLabel=m(w+nl+nr);c.drawText(cutLabel,(a+b-p.measureText(cutLabel))/2,27,p);}
 else if(!Double.isFinite(left)||!Double.isFinite(right))label(c,"Nachschnitt: nicht vollständig erfasst",20,27);
 label(c,"Oberfläche: "+covering,20,238);
 label(c,Double.isFinite(topThickness)?"Belagsstärke: "+m(topThickness):"Belagsstärke: nicht erfasst",20,252);
 label(c,utilities.length()>0?"Leitungen: "+(utilities.length()+(Double.isFinite(previewPipe)?1:0))+" (Lage symbolisch)":Double.isFinite(previewPipe)?"Rohr Ø "+previewPipe+" mm (Lage symbolisch)":"Rohrmaße: unbekannt",20,266);
 double station=Math.max(0,Math.min(l,profileShoringStation));double[] shoring=shoringAtStation(station);
 if(shoring[0]>0||shoring[1]>0){
  p.setColor(0xffa65e19);p.setStrokeWidth(3);
  if(shoring[0]>0)c.drawLine(x-3,top,x-3,top+(float)(Math.min(d,shoring[0])*scale),p);
  if(shoring[1]>0)c.drawLine(z+3,top,z+3,top+(float)(Math.min(d,shoring[1])*scale),p);
  label(c,"Verbau @ "+m(station)+": L "+m(shoring[0])+" / R "+m(shoring[1]),20,295);
 }
 label(c,"Schematisch – keine Ausführungsplanung",20,282);c.restore();
 }}
 class Sketch extends View{Paint p=new Paint(3);Sketch(){super(MainActivity.this);setClickable(true);}
 @Override public boolean onTouchEvent(android.view.MotionEvent e){if(e.getAction()==android.view.MotionEvent.ACTION_DOWN)return true;if(e.getAction()==android.view.MotionEvent.ACTION_UP){performClick();if(l<=0)return true;float a=35,b=getWidth()-35;java.util.ArrayList<Integer> hits=new java.util.ArrayList<>();for(int i=0;i<objects.length();i++){org.json.JSONObject o=objects.optJSONObject(i);if(o==null)continue;double st=o.optDouble("station");if(st<0||st>l)continue;float x=a+(b-a)*(float)(st/l);if(Math.abs(e.getX()-x)<dp(22)&&Math.abs(e.getY()-95)<dp(24))hits.add(i);}if(hits.size()==1)editObject(hits.get(0));else if(hits.size()>1){String[] names=new String[hits.size()];for(int j=0;j<names.length;j++){org.json.JSONObject o=objects.optJSONObject(hits.get(j));names[j]=o.optString("type")+" – "+o.optString("station")+" m";}new android.app.AlertDialog.Builder(MainActivity.this).setTitle("Objekt auswählen").setItems(names,(v,j)->editObject(hits.get(j))).show();}else if(e.getY()>=dp(8)&&e.getY()<=dp(30)){try{double station=(e.getX()-a)/(b-a)*l;org.json.JSONArray rows=new PipeLayoutStore(getPreferences(0)).sections();for(int j=0;j<rows.length();j++){org.json.JSONObject row=rows.getJSONObject(j);if(station>=row.getDouble("from")&&station<row.getDouble("to")){PipeLayoutView view=new PipeLayoutView(MainActivity.this);view.setLayoutData(PipeLayoutStore.decode(row.getJSONArray("pipes").toString()),row.getDouble("width"),row.getDouble("depth"));new android.app.AlertDialog.Builder(MainActivity.this).setTitle("Regelprofil – "+row.getDouble("from")+" bis "+row.getDouble("to")+" m").setView(view).setPositiveButton("Schließen",null).show();break;}}}catch(Exception ex){notice(ex.getMessage());}}else if(e.getY()>=65&&e.getY()<=125)showPage(measurePage);return true;}return super.onTouchEvent(e);}
 @Override public boolean performClick(){super.performClick();return true;}
 protected void onDraw(Canvas c){super.onDraw(c);float a=35,b=getWidth()-35;p.setColor(covering.equals("Asphalt")?0xff656970:covering.equals("Unbefestigt")?0xffa98d6c:0xffd9c4a5);c.drawRect(a,65,b,125,p);
 if(!covering.equals("Asphalt")&&!covering.equals("Unbefestigt")&&!covering.equals("Unbekannt")&&!covering.equals("Sonstiges")){
 p.setColor(0xff81796b);p.setStrokeWidth(1);int step=covering.toLowerCase(java.util.Locale.ROOT).contains("platten")?32:16;
 c.save();c.clipRect(a,65,b,125);
 for(int row=0;row<3;row++){float y=65+row*20;c.drawLine(a,y,b,y,p);for(float x=a-(row%2)*step/2;x<b;x+=step)c.drawLine(x,y,x,y+20,p);}
 c.restore();}
p.setColor(covering.equals("Asphalt")?Color.WHITE:Color.BLACK);p.setStrokeWidth(2);c.drawLine(a,95,b,95,p);if(l>0){for(int i=0;i<objects.length();i++){org.json.JSONObject o=objects.optJSONObject(i);if(o==null)continue;double st=o.optDouble("station");if(st<0||st>l)continue;float x=a+(b-a)*(float)(st/l);p.setColor(objectWarning(i)?0xffc62828:0xff246eaa);double dl=detailNumber(o,"detailLength"),dw=detailNumber(o,"detailWidth");if(Double.isFinite(dl)&&dl>0&&Double.isFinite(dw)&&dw>0){float half=Math.max(4,(float)(dl/l*(b-a)/2));float hh=Math.min(60,Math.max(5,(float)(dw*15)));c.drawRect(x-half,95-hh,x+half,95+hh,p);}else c.drawCircle(x,95,8,p);}}p.setColor(UI_TEXT);c.drawLine(a,160,b,160,p);c.drawLine(a,150,a,170,p);c.drawLine(b,150,b,170,p);p.setTextSize(22);c.drawText("0,00 m",a,45,p);c.drawText(String.format(java.util.Locale.GERMANY,"%.2f m",l),Math.max(a,b-110),45,p);c.drawText("Breite: "+w+" m",a,200,p);c.drawText("Schematisch – nicht maßstäblich",a,245,p);if(l>0&&b>a){try{org.json.JSONArray shoring=new org.json.JSONArray(getPreferences(0).getString("metricVerbauSections","[]"));p.setColor(0xffa65e19);p.setStrokeWidth(5);for(int j=0;j<shoring.length();j++){org.json.JSONArray row=shoring.getJSONArray(j);double from=Math.max(0,row.getDouble(0)),to=Math.min(l,row.getDouble(1));if(!Double.isFinite(from)||!Double.isFinite(to)||to<=from)continue;float x1=a+(b-a)*(float)(from/l),x2=a+(b-a)*(float)(to/l);if(row.getDouble(2)>0)c.drawLine(x1,65,x2,65,p);if(row.getDouble(3)>0)c.drawLine(x1,125,x2,125,p);}if(shoring.length()>0){p.setColor(UI_TEXT);p.setTextSize(18);c.drawText("Verbau (braun): links oben / rechts unten",a,270,p);}p.setStrokeWidth(2);}catch(Exception ignored){p.setStrokeWidth(2);}}if(l>0){try{org.json.JSONArray rows=new PipeLayoutStore(getPreferences(0)).sections();int[] colors={0xff397db5,0xff398b75,0xffb58b45};for(int j=0;j<rows.length();j++){org.json.JSONObject row=rows.getJSONObject(j);float x1=a+(b-a)*(float)(Math.min(l,row.getDouble("from"))/l),x2=a+(b-a)*(float)(Math.min(l,row.getDouble("to"))/l);p.setColor(colors[j%colors.length]);c.drawRect(x1,dp(8),x2,dp(30),p);}}catch(Exception ignored){}}}
 }
}
