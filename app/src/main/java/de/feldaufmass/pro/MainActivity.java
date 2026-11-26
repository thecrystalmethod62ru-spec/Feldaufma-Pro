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
  java.util.Set<String> strings=new java.util.HashSet<>(java.util.Arrays.asList("project","length","width","depth","base","pipe","asphalt","left","right","stoneLength","stoneWidth","stoneDepth","stoneColor","pattern","bedding","underlying","stationFrom","stationTo","objectStation","objectNote","sollLength","sollWidth","sollDepth","sollAsphalt","sollSource","routeSketch","objects","segments","documents","sollUtilities","montageFittings","metricWidthSections","objectUndo","objectRedo","elevationPoints","lvPositions","dailyReports","nachtraege","routeUnderlay","sketchCalibration"));
  java.util.Set<String> ints=new java.util.HashSet<>(java.util.Arrays.asList("surface","handling","dictationLanguage","gpsDurationSeconds"));
  SharedPreferences.Editor ed=projectStorage(id).edit();
  java.util.Iterator<String> keys=fields.keys();String name="Importiertes Projekt";java.util.Set<String> used=new java.util.HashSet<>();
  while(keys.hasNext()){
   String key=keys.next();org.json.JSONObject field=fields.getJSONObject(key);String type=field.getString("type");Object value=field.get("value");
   if(strings.contains(key)&&type.equals("String")&&value instanceof String){
    String v=(String)value;
    if(key.equals("routeSketch")){
     org.json.JSONArray rows=new org.json.JSONArray(v);
     for(int j=0;j<rows.length();j++){
      org.json.JSONArray row=rows.getJSONArray(j);double x=row.getDouble(0),y=row.getDouble(1);
      if(!Double.isFinite(x)||!Double.isFinite(y)||x<0||x>1||y<0||y>1)throw new java.io.IOException("Ungültiger Trassenpunkt");
      org.json.JSONArray items=row.optJSONArray(4);if(items==null)continue;
      for(int k=0;k<items.length();k++){String old=items.getString(k),replacement=photos.get(old);if(replacement==null)throw new java.io.IOException("Foto fehlt");used.add(old);items.put(k,replacement);org.json.JSONObject meta=row.optJSONObject(10);if(meta!=null&&meta.has(old)){Object photoMeta=meta.remove(old);meta.put(replacement,photoMeta);}}
     }v=rows.toString();
    }else if(key.equals("routeUnderlay")){if(!v.isEmpty()){String replacement=photos.get(v);if(replacement==null)throw new java.io.IOException("Zeichenvorlage fehlt");used.add(v);v=replacement;}}
    else if(key.equals("documents")){
     org.json.JSONArray docs=new org.json.JSONArray(v);for(int j=0;j<docs.length();j++){org.json.JSONObject doc=docs.getJSONObject(j);doc.remove("uri");doc.put("name",doc.optString("name")+" (neu verknüpfen)");}v=docs.toString();
    }else if(key.equals("dailyReports")||key.equals("nachtraege")){org.json.JSONArray rows=new org.json.JSONArray(v);for(int j=0;j<rows.length();j++){org.json.JSONArray items=dailyPhotos(rows.getJSONObject(j));for(int k=0;k<items.length();k++){org.json.JSONObject photo=items.getJSONObject(k);String old=photo.getString("file"),replacement=photos.get(old);if(replacement==null)throw new java.io.IOException("Berichtsfoto fehlt");used.add(old);photo.put("file",replacement);}}v=rows.toString();
    }else if(key.equals("objects")||key.equals("segments")||key.equals("elevationPoints")||key.equals("lvPositions")||key.equals("sollUtilities")||key.equals("montageFittings")||key.equals("metricWidthSections")){org.json.JSONArray rows=new org.json.JSONArray(v);for(int j=0;j<rows.length();j++){org.json.JSONObject o=rows.getJSONObject(j);if(key.equals("metricWidthSections")){double from=o.getDouble("from"),to=o.getDouble("to"),width=o.getDouble("width");for(String side:new String[]{"left","right"})if(o.has(side)){double cut=o.getDouble(side);if(!Double.isFinite(cut)||cut<0)throw new org.json.JSONException("Ungültiger Nachschnitt");}if(!Double.isFinite(from)||!Double.isFinite(to)||!Double.isFinite(width)||from<0||to<=from||width<=0)throw new org.json.JSONException("Ungültiger Breitenabschnitt");for(int k=0;k<j;k++){org.json.JSONObject previous=rows.getJSONObject(k);if(from<previous.getDouble("to")&&to>previous.getDouble("from"))throw new org.json.JSONException("Überlappende Breitenabschnitte");}}}}
    else if(key.equals("objectUndo")||key.equals("objectRedo")){org.json.JSONArray rows=new org.json.JSONArray(v);for(int j=0;j<rows.length();j++)new org.json.JSONArray(rows.getString(j));}
    if(key.equals("project")&&!v.trim().isEmpty())name=v.trim();
    ed.putString(key,v);
   }else if(ints.contains(key)&&type.equals("Integer")&&value instanceof Number){
    int n=field.getInt("value");if(key.equals("dictationLanguage")&&(n<0||n>1))throw new java.io.IOException("Ungültige Sprache");ed.putInt(key,n);
   }else if(key.equals("lastAutosave")&&type.equals("Long")&&value instanceof Number){ed.putLong(key,field.getLong("value"));}
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
   org.json.JSONArray items=rows.getJSONArray(j).optJSONArray(4);if(items==null)continue;
   for(int k=0;k<items.length();k++){
    String name=items.getString(k);if(!name.matches("[A-Za-z0-9._-]+")||name.equals(".")||name.equals(".."))throw new java.io.IOException("Ungültiger Fotodateiname");
    photos.add(name);
   }
  }
  Object template=snapshot.get("routeUnderlay");if(template instanceof String&&!((String)template).isEmpty()){String filename=(String)template;if(!filename.matches("[A-Za-z0-9._-]+")||filename.equals(".")||filename.equals(".."))throw new java.io.IOException("Ungültige Vorlage");photos.add(filename);}
  for(String reportKey:new String[]{"dailyReports","nachtraege"}){
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
 void showProjects(){
  java.util.ArrayList<String> ids=new java.util.ArrayList<>(projectRegistry().getStringSet("ids",java.util.Collections.emptySet()));
  java.util.Collections.sort(ids);ids.remove(LEGACY_PROJECT);ids.add(0,LEGACY_PROJECT);
  String[] labels=new String[ids.size()];
  for(int j=0;j<ids.size();j++){String id=ids.get(j);String name=projectStorage(id).getString("project","");labels[j]=(id.equals(activeProject)?"● ":"")+ (name.trim().isEmpty()?"Bestandsprojekt":name);}
  new android.app.AlertDialog.Builder(this).setTitle("Projekte").setItems(labels,(d,w)->{String id=ids.get(w);if(!id.equals(activeProject))confirmProjectSwitch(id);}).setNeutralButton("Neues Projekt",(d,w)->newProjectDialog()).setNegativeButton("Schließen",null).show();
 }
 void newProjectDialog(){
  EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Projektname");
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Neues Projekt").setMessage("Aktuelle Eingaben werden automatisch als Entwurf gespeichert.").setView(name).setNegativeButton("Abbrechen",null).setPositiveButton("Erstellen",null).create();
  dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{
   String title=name.getText().toString().trim();if(title.isEmpty()){name.setError("Projektname erforderlich");return;}
   String id=java.util.UUID.randomUUID().toString();
   if(!projectStorage(id).edit().putString("project",title).putInt("dictationLanguage",getPreferences(0).getInt("dictationLanguage",1)).commit()){notice("Projekt konnte nicht erstellt werden.");return;}
   java.util.Set<String> ids=new java.util.HashSet<>(projectRegistry().getStringSet("ids",java.util.Collections.emptySet()));ids.add(id);
   if(!projectRegistry().edit().putStringSet("ids",ids).putString("active",id).commit()){notice("Projekt konnte nicht aktiviert werden.");return;}
   dialog.dismiss();restartForProject();
  }));dialog.show();
 }
 void confirmProjectSwitch(String id){
  new android.app.AlertDialog.Builder(this).setTitle("Projekt wechseln").setMessage("Aktuelle Eingaben werden automatisch als Entwurf gespeichert.").setNegativeButton("Abbrechen",null).setPositiveButton("Wechseln",(d,w)->{
   if(projectRegistry().edit().putString("active",id).commit())restartForProject();else notice("Projektwechsel fehlgeschlagen.");
  }).show();
 }
 void restartForProject(){
  saveDraft();
  selectedPoint=null;pendingPoint=null;pendingPhoto=null;speechVersion++;
  recreate();
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
   optional(stoneLength);optional(stoneWidth);
   double stone=optional(stoneDepth)/100,baseValue=optional(baseInput),pipeValue=optional(pipeInput);
   double asphaltValue=optional(asphaltInput);optional(leftInput);optional(rightInput);
   double top="Asphalt".equals(surface.getSelectedItem().toString())?asphaltValue:stone;
   return (!Double.isFinite(pipeValue)||(pipeValue>0&&pipeValue/1000<=wid&&pipeValue/1000<=dep))
    &&(!Double.isFinite(top)||top<=dep)&&(!Double.isFinite(baseValue)||baseValue+(Double.isFinite(top)?top:0)<=dep);
  }catch(Exception e){return false;}
 }
 boolean saveDraft(){
  if(project==null||surface==null||handling==null)return false;
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

 RouteSketch livePreview;
 android.location.Location liveFix;
 android.location.LocationManager liveManager;
 final android.location.LocationListener liveListener=new android.location.LocationListener(){
  public void onLocationChanged(android.location.Location fix){liveFix=fix;if(livePreview!=null)livePreview.setLiveFix(fix);}
  public void onProviderDisabled(String provider){liveFix=null;if(livePreview!=null)livePreview.setLiveFix(null);}
  public void onProviderEnabled(String provider){}
  public void onStatusChanged(String provider,int status,android.os.Bundle extras){}
 };
 void startLiveGps(){
  if(liveManager!=null||checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
  try{android.location.LocationManager m=(android.location.LocationManager)getSystemService(LOCATION_SERVICE);
   m.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER,1000L,0f,liveListener);liveManager=m;
  }catch(RuntimeException ignored){}
 }
 @Override protected void onResume(){super.onResume();startLiveGps();autosaveHandler.removeCallbacks(autosaveTick);autosaveHandler.postDelayed(autosaveTick,AUTOSAVE_INTERVAL_MS);}
 @Override protected void onPause(){
  autosaveHandler.removeCallbacks(autosaveTick);saveDraft();
  if(liveManager!=null){liveManager.removeUpdates(liveListener);liveManager=null;}
  liveFix=null;if(livePreview!=null)livePreview.setLiveFix(null);super.onPause();
 }
 int drawingLayers(){
  android.content.SharedPreferences prefs=getSharedPreferences("drawing_view",MODE_PRIVATE);
  int mask=prefs.getInt(activeProject,RouteSketch.ALL_LAYERS)&RouteSketch.ALL_LAYERS;
  if(!prefs.getBoolean("surfaceDimensionsMigrated:"+activeProject,false)){
   if((mask&RouteSketch.LAYER_DIMENSIONS)!=0)mask|=RouteSketch.LAYER_SURFACE_DIMENSIONS;
   else mask&=~RouteSketch.LAYER_SURFACE_DIMENSIONS;
   prefs.edit().putInt(activeProject,mask).putBoolean("surfaceDimensionsMigrated:"+activeProject,true).apply();
  }
  return mask;
 }
 void showDrawingLayers(){
  String[] labels={"Leitungsverlauf / Punkte","Bemaßung","Punktnamen","GPS / Standort","Fotodokumentation","Objekte","Oberflächen","Oberflächenbemaßung"};
  int[] masks={RouteSketch.LAYER_ROUTE,RouteSketch.LAYER_DIMENSIONS,RouteSketch.LAYER_NAMES,RouteSketch.LAYER_GPS,RouteSketch.LAYER_PHOTOS,RouteSketch.LAYER_OBJECTS,RouteSketch.LAYER_SURFACES,RouteSketch.LAYER_SURFACE_DIMENSIONS};
  boolean[] checked=new boolean[labels.length];int current=drawingLayers();
  for(int j=0;j<labels.length;j++)checked[j]=(current&masks[j])!=0;
  new android.app.AlertDialog.Builder(this).setTitle("Zeichnungsebenen")
   .setMultiChoiceItems(labels,checked,(d,j,on)->checked[j]=on)
   .setNegativeButton("Abbrechen",null).setNeutralButton("Alle anzeigen",(d,w)->applyDrawingLayers(RouteSketch.ALL_LAYERS))
   .setPositiveButton("Übernehmen",(d,w)->{int mask=0;for(int j=0;j<checked.length;j++)if(checked[j])mask|=masks[j];applyDrawingLayers(mask);}).show();
 }
 void applyDrawingLayers(int mask){
  if(!getSharedPreferences("drawing_view",MODE_PRIVATE).edit().putInt(activeProject,mask).commit()){notice("Ebenen konnten nicht gespeichert werden.");return;}
  if(livePreview!=null)livePreview.setVisibleLayers(mask);
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
   if(omitted>0)label+=" · "+omitted+" Stationseinträge ohne Planposition";
   if(obj.length()>0)label+="\n* Ohne Abstand: schematisch angrenzend. Skizze nicht maßstabsgetreu." ;
   routeSummary.setText(label);
  }else if(livePreview.vertices.size()>=2){
   double total=livePreview.measuredLength();routeSummary.setText((livePreview.vertices.size()-1)+" Abschnitte · "+(Double.isFinite(total)?"Bemaßte Länge: "+reportMeasure(total):"Bemaßung unvollständig"));
  }
 }
 void refreshRoute(){
  routePreviewBox.removeAllViews();
  RouteSketch preview=new RouteSketch(this,routeData());preview.bindDimensionProject(activeProject);
  livePreview=preview;preview.setVisibleLayers(drawingLayers());preview.setLiveFix(liveFix);
  preview.pointSelected=i->{selectedPoint=preview.vertices.get(i).id;refreshRoute();pointMenu();};
  preview.selected=i->measureSegment(preview,i);
  preview.changed=()->{saveRoute(preview);refreshRoute();};
  preview.highlight=findPoint(preview,selectedPoint);
  if(!preview.vertices.isEmpty() && !routeData().equals(preview.encode()))getPreferences(0).edit().putString("routeSketch",preview.encode()).remove("routeHistoryHead").remove("routeUndo").remove("routeRedo").commit();
  routePreviewBox.addView(preview,new LinearLayout.LayoutParams(-1,dp(300)));
  double total=preview.measuredLength();
  if(preview.vertices.size()<2){routeSummary.setText("Trasse mit dem Finger zeichnen");refreshStationLayers();return;}
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
 boolean persistRouteHistory(RouteEditHistory history){
  try{
   String before=routeData();
   SharedPreferences.Editor ed=getPreferences(0).edit().putString("routeSketch",history.current)
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
 void chooseRouteUnderlay(){
  new android.app.AlertDialog.Builder(this).setTitle("Handskizze als Zeichenvorlage")
   .setMessage("Bild wird als Hintergrund im Trasseneditor gespeichert. Vorhandene Punkte und Maße bleiben unverändert. Keine automatische Maßstabsberechnung.")
   .setNegativeButton("Abbrechen",null).setPositiveButton("Bild auswählen",(d,w)->{
    android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);i.addCategory(android.content.Intent.CATEGORY_OPENABLE);i.setType("image/*");startActivityForResult(i,427);
   }).show();
 }
 void importRouteUnderlay(android.net.Uri uri){
  String projectId=activeProject;String original=getPreferences(0).getString("routeUnderlay","");
  new Thread(()->{
   java.io.File file=null;android.graphics.Bitmap bitmap=null;
   try{
    bitmap=imageForOcr(uri);if(bitmap==null)throw new java.io.IOException("Bild nicht lesbar");
    java.io.File dir=new java.io.File(getFilesDir(),"photos");if(!dir.isDirectory()&&!dir.mkdirs())throw new java.io.IOException("Bildordner fehlt");
    file=java.io.File.createTempFile("vorlage_",".png",dir);
    try(java.io.OutputStream out=new java.io.FileOutputStream(file)){if(!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out))throw new java.io.IOException("Bild konnte nicht gespeichert werden");}
    final java.io.File saved=file;
    runOnUiThread(()->{
     if(isDestroyed()||isFinishing()||!projectId.equals(activeProject)||!original.equals(getPreferences(0).getString("routeUnderlay",""))){saved.delete();return;}
     if(!getPreferences(0).edit().putString("routeUnderlay",saved.getName()).commit()){saved.delete();notice("Vorlage konnte nicht gespeichert werden.");return;}
     recordProjectEvent("Zeichenvorlage geändert");editRoute(false);
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
  new android.app.AlertDialog.Builder(this).setTitle(o.optString("type")+" · "+csvNumber(o.optDouble("station"))+" m")
   .setItems(new String[]{"Bearbeiten / verschieben","Objekt entfernen"},(d,w)->{
    if(w==0)editEditorObject(route,draft,refresh,index,Double.NaN);
    else new android.app.AlertDialog.Builder(this).setTitle("Objekt entfernen?").setMessage("Die Trasse und andere Objekte bleiben unverändert.").setNegativeButton("Abbrechen",null).setPositiveButton("Entfernen",(x,y)->{if(draft.optJSONObject(index)==o){draft.remove(index);refresh.run();}}).show();
   }).setNegativeButton("Schließen",null).show();
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
  LinearLayout form=page();ScrollView scroll=new ScrollView(this);scroll.addView(form);
  java.util.ArrayList<String> types=new java.util.ArrayList<>(java.util.Arrays.asList("Hausanschluss","Suchschachtung","Hindernis","Startgrube","Zielgrube","KVS","Knickpunkt"));if(old!=null&&!types.contains(old.optString("type")))types.add(old.optString("type"));Spinner type=choices(form,types.toArray(new String[0]));if(old!=null)type.setSelection(types.indexOf(old.optString("type")));
  EditText station=new EditText(this);station.setInputType(8194);station.setHint("Station in m (0 – "+csvNumber(total)+")");if(Double.isFinite(suggestedStation)){station.setText(java.math.BigDecimal.valueOf(suggestedStation).stripTrailingZeros().toPlainString().replace('.',','));}if(old!=null)station.setText(old.optString("station"));form.addView(station);
  String[] sides={"Unbekannt","links","rechts","mittig"};Spinner side=choices(form,sides);if(old!=null)side.setSelection(Math.max(0,java.util.Arrays.asList(sides).indexOf(old.optString("side"))));
  String[] keys={"detailLength","detailWidth","detailDepth","detailOffset"},labels={"Länge (m)","Breite (m)","Tiefe (m)","Abstand zum Grabenrand (m)"};
  EditText[] fields=new EditText[keys.length];for(int j=0;j<fields.length;j++){fields[j]=new EditText(this);fields[j].setInputType(8194);fields[j].setHint(labels[j]+" – optional");if(old!=null)fields[j].setText(old.optString(keys[j],""));form.addView(fields[j]);}
  EditText note=new EditText(this);note.setHint("Bemerkung");if(old!=null)note.setText(old.optString("note"));form.addView(note);
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle(old==null?"Objekt an Station hinzufügen":"Objekt bearbeiten / verschieben").setView(scroll).setNegativeButton("Abbrechen",null).setPositiveButton("Übernehmen",null).create();
  dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{
   try{
    double st=number(station),limit=route.measuredLength();if(!Double.isFinite(st)||!Double.isFinite(limit)||st<0||st>limit){station.setError("Station außerhalb der bemaßten Trasse");return;}
    String chosen=side.getSelectedItem().toString();
    org.json.JSONObject o=(old==null?new org.json.JSONObject():new org.json.JSONObject(old.toString())).put("station",st).put("type",type.getSelectedItem().toString()).put("side",chosen).put("note",note.getText().toString());
    for(int j=0;j<fields.length;j++){if(fields[j].length()==0){o.remove(keys[j]);continue;}double value=number(fields[j]);if(!Double.isFinite(value)||value<0||(j<3&&value==0)||(j==3&&!chosen.equals("links")&&!chosen.equals("rechts"))){fields[j].setError("Maß / Seite prüfen");return;}o.put(keys[j],fields[j].getText().toString());}
    if(old==null)draft.put(o);else if(draft.optJSONObject(index)==old){if(!overlapGeometry(old).equals(overlapGeometry(o)))o.remove("acceptedOverlap");draft.put(index,o);}else{notice("Objekt wurde geändert.");return;}refresh.run();dialog.dismiss();
   }catch(Exception e){notice("Station und Maße prüfen.");}
  }));dialog.show();
 }
 void showFullscreenRoute(android.app.AlertDialog editor,LinearLayout form,RouteSketch route,Spinner mode,Spinner angles,LinearLayout historyBar,Runnable openObjects){
  // Move the existing views, not a second sketch: unsaved data and undo history stay shared.
  View[] views={route,mode,angles,historyBar};
  int[] positions=new int[views.length];android.view.ViewGroup.LayoutParams[] params=new android.view.ViewGroup.LayoutParams[views.length];
  for(int j=0;j<views.length;j++){if(views[j].getParent()!=form)return;positions[j]=form.indexOfChild(views[j]);params[j]=views[j].getLayoutParams();}
  android.app.Dialog full=new android.app.Dialog(this,android.R.style.Theme_Material_Light_NoActionBar){
   @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused&&getWindow()!=null){androidx.core.view.WindowInsetsControllerCompat c=androidx.core.view.WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView());c.setSystemBarsBehavior(androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());}}
  };
  full.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
  final int previousOrientation=getRequestedOrientation();
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.WHITE);
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header);
  Button back=new Button(this),save=new Button(this);back.setAllCaps(false);save.setAllCaps(false);back.setText("Vollbild beenden");save.setText("Speichern");
  header.addView(back,new LinearLayout.LayoutParams(0,-2,1));header.addView(save,new LinearLayout.LayoutParams(0,-2,1));
  Spinner orientation=new Spinner(this);
  orientation.setAdapter(new android.widget.ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Automatisch drehen","Querformat","Hochformat"}));
  header.addView(orientation,new LinearLayout.LayoutParams(0,-2,1));
  orientation.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
    if(!full.isShowing())return;
    setRequestedOrientation(position==1?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:position==2?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
   }
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
  });
  for(View view:views)form.removeView(view);
  LinearLayout controls=new LinearLayout(this);root.addView(controls);
  controls.addView(mode,new LinearLayout.LayoutParams(0,-2,1));controls.addView(angles,new LinearLayout.LayoutParams(0,-2,1));
  root.addView(route,new LinearLayout.LayoutParams(-1,0,1));
  root.addView(historyBar,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout tools=new LinearLayout(this);root.addView(tools);
  Button mic=new Button(this),measureList=new Button(this),resetZoom=new Button(this);
  mic.setAllCaps(false);mic.setText("🎙 Assistent");measureList.setAllCaps(false);measureList.setText("Abschnitte");resetZoom.setAllCaps(false);resetZoom.setText("Ansicht");
  tools.addView(mic,new LinearLayout.LayoutParams(0,-2,1));tools.addView(measureList,new LinearLayout.LayoutParams(0,-2,1));tools.addView(resetZoom,new LinearLayout.LayoutParams(0,-2,1));
  mic.setOnClickListener(v->startEditorDictation(speech->applyEditorSpeech(speech,route,mode,historyBar,full)));
  measureList.setOnClickListener(v->showFullscreenMeasures(route));
  Button legendEdit=new Button(this);legendEdit.setAllCaps(false);legendEdit.setText("Legende");tools.addView(legendEdit,new LinearLayout.LayoutParams(0,-2,1));legendEdit.setOnClickListener(v->route.editLegend());
  resetZoom.setOnClickListener(v->{route.zoom=1;route.centerX=.5f;route.centerY=.5f;route.invalidate();});
  Button objectTools=new Button(this);objectTools.setAllCaps(false);objectTools.setText("Objekte");tools.addView(objectTools,new LinearLayout.LayoutParams(0,-2,1));objectTools.setOnClickListener(v->openObjects.run());
  full.setContentView(root);
  full.setOnDismissListener(d->{
   pendingEditorSpeech=null;
   setRequestedOrientation(previousOrientation);
   for(View view:views){android.view.ViewParent parent=view.getParent();if(parent instanceof android.view.ViewGroup)((android.view.ViewGroup)parent).removeView(view);}
   // Original positions are ascending; restore before other controls.
   for(int j=0;j<views.length;j++)form.addView(views[j],Math.min(positions[j],form.getChildCount()),params[j]);
   route.invalidate();
  });
  back.setOnClickListener(v->full.dismiss());
  save.setOnClickListener(v->{full.dismiss();if(editor.isShowing())editor.getButton(-1).performClick();});
  android.view.Window window=full.getWindow();
  if(window!=null){
   androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false);
   androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root,(view,insets)->{
    androidx.core.graphics.Insets safe=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout()|androidx.core.view.WindowInsetsCompat.Type.systemBars()|androidx.core.view.WindowInsetsCompat.Type.ime());
    view.setPadding(safe.left,safe.top,safe.right,safe.bottom);return insets;
   });
  }
  full.show();
  setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
  if(window!=null){window.setLayout(-1,-1);androidx.core.view.WindowInsetsControllerCompat c=androidx.core.view.WindowCompat.getInsetsController(window,root);c.setSystemBarsBehavior(androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());androidx.core.view.ViewCompat.requestApplyInsets(root);}
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
  RouteSketch route=new RouteSketch(this,routeData());route.editable=true;route.bindDimensionProject(activeProject);
  TextView hint=sheetText("Trasse mit dem Finger zeichnen. Weitere Striche verlängern das Ende.",15,false);
  form.addView(hint);
  Button fullscreen=new Button(this);fullscreen.setAllCaps(false);fullscreen.setText("⛶ Vollbild");form.addView(fullscreen);
  form.addView(route,new LinearLayout.LayoutParams(-1,dp(260)));
  String template=getPreferences(0).getString("routeUnderlay","");
  if(!template.isEmpty()){
   android.graphics.Bitmap image=reportPhoto(template);
   if(image!=null){
    route.setUnderlay(image);
    android.widget.CheckBox showTemplate=new android.widget.CheckBox(this);showTemplate.setText("Zeichenvorlage anzeigen");showTemplate.setChecked(true);form.addView(showTemplate);
    showTemplate.setOnCheckedChangeListener((b,checked)->route.setUnderlayVisible(checked));
    form.addView(sheetText("Deckkraft der Vorlage",13,false));
    android.widget.SeekBar opacity=new android.widget.SeekBar(this);opacity.setMax(255);opacity.setProgress(150);form.addView(opacity);
    opacity.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(android.widget.SeekBar bar,int progress,boolean user){route.setUnderlayAlpha(progress);}public void onStartTrackingTouch(android.widget.SeekBar bar){}public void onStopTrackingTouch(android.widget.SeekBar bar){}});
    Button removeTemplate=new Button(this);removeTemplate.setAllCaps(false);removeTemplate.setText("Vorlage aus Projekt entfernen");form.addView(removeTemplate);
    removeTemplate.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("Zeichenvorlage entfernen?").setMessage("Die gezeichnete Trasse und Maße bleiben erhalten.").setNegativeButton("Abbrechen",null).setPositiveButton("Entfernen",(d,w)->{
     if(template.equals(getPreferences(0).getString("routeUnderlay",""))&&getPreferences(0).edit().remove("routeUnderlay").commit()){route.setUnderlay(null);showTemplate.setEnabled(false);opacity.setEnabled(false);removeTemplate.setEnabled(false);recordProjectEvent("Zeichenvorlage entfernt");}else notice("Vorlage konnte nicht entfernt werden.");
    }).show());
   }else form.addView(sheetText("Zeichenvorlage fehlt oder ist nicht lesbar.",13,false));
  }

  Spinner mode=choices(form,new String[]{"Zeichnen","Maß eintragen","Punkte verschieben","Objekt platzieren","Objekt bearbeiten"});
  Spinner angles=choices(form,new String[]{"Freier Winkel","0°","15°","30°","45°","60°","75°","90°"});
  angles.setVisibility(View.GONE);
  onChoice(angles,index->route.angle=index==0?-1:(index-1)*15);
  onChoice(mode,index->{
   route.dimensioning=index==1;route.moving=index==2;route.placingObject=index==3;route.editingObjects=index==4;
   angles.setVisibility(route.moving?View.VISIBLE:View.GONE);
   hint.setText(new String[]{"Mit einem Finger zeichnen, mit zwei Fingern zoomen oder verschieben.","Abschnitt zum Bemaßen antippen.","Punkt ziehen. Der Winkel gilt zur Waagerechten am benachbarten Punkt.","Trasse antippen, um ein Objekt an einer Station zu platzieren.","Objekt auf dem Plan antippen."}[index]);
  });
  mode.setSelection(measure?1:0);
  final String initialSaved=routeData();
  final String initialObjects=objects.toString();final org.json.JSONArray draftObjects=copyEditorObjects(initialObjects);
  Runnable refreshObjects=()->{route.setTrenchWidth(w);route.setStationLayers(draftObjects,segments);route.invalidate();};
  Runnable openObjects=()->showEditorObjects(route,draftObjects,refreshObjects);
  route.stationSelected=hits->pickEditorObjectStation(route,draftObjects,refreshObjects,hits);
  route.objectSelected=hits->pickEditorObject(route,draftObjects,refreshObjects,hits);
  Button objectTools=new Button(this);objectTools.setAllCaps(false);objectTools.setText("Objekte hinzufügen");form.addView(objectTools);objectTools.setOnClickListener(v->openObjects.run());refreshObjects.run();
  RouteEditHistory history=loadRouteHistory(route.encode());
  LinearLayout historyBar=new LinearLayout(this);form.addView(historyBar);
  Button undo=new Button(this),redo=new Button(this);undo.setAllCaps(false);redo.setAllCaps(false);undo.setText("Rückgängig");redo.setText("Wiederholen");
  historyBar.addView(undo,new LinearLayout.LayoutParams(0,-2,1));historyBar.addView(redo,new LinearLayout.LayoutParams(0,-2,1));
  Button legendEdit=new Button(this);legendEdit.setAllCaps(false);legendEdit.setText("Legende bearbeiten");form.addView(legendEdit);legendEdit.setOnClickListener(v->route.editLegend());
  Button resetDimensions=new Button(this);resetDimensions.setAllCaps(false);resetDimensions.setText("Maßpositionen zurücksetzen");historyBar.addView(resetDimensions,new LinearLayout.LayoutParams(0,-2,1));resetDimensions.setOnClickListener(v->route.resetDimensionLayout());
  Button deletePoint=new Button(this);deletePoint.setAllCaps(false);deletePoint.setText("Punkt löschen");historyBar.addView(deletePoint,new LinearLayout.LayoutParams(0,-2,1));deletePoint.setOnClickListener(v->deleteRoutePoint(route));
  Button remove=new Button(this);remove.setText("Letzten Abschnitt entfernen");form.addView(remove);
  final org.json.JSONObject[] calibration={savedSketchCalibration()};
  TextView scaleStatus=sheetText("",13,false);form.addView(scaleStatus);
  Button calibrate=new Button(this);calibrate.setAllCaps(false);calibrate.setText("Maßstab kalibrieren");form.addView(calibrate);
  Button clearScale=new Button(this);clearScale.setAllCaps(false);clearScale.setText("Kalibrierung entfernen");form.addView(clearScale);
  LinearLayout list=page();form.addView(list);
  route.selected=i->measureSegment(route,i);
  route.strokeAdded=first->offerNewRouteMeasurements(route,first);
  route.pointSelected=i->{
   if(i<0||i>=route.vertices.size())return;
   route.highlight=i;route.invalidate();
   RouteSketch.Vertex p=route.vertices.get(i);
   android.app.AlertDialog.Builder menu=new android.app.AlertDialog.Builder(this).setTitle("Punkt "+(i+1)+(p.name.isEmpty()?"":" · "+p.name)).setNegativeButton("Schließen",null);
   if(i>0&&i<route.vertices.size()-1)menu.setItems(new String[]{"Punkt löschen und verbinden"},(d,w)->confirmDeleteRoutePoint(route,i));
   else menu.setMessage("Start- oder Endpunkt. Kein Verbinden zweier Nachbarpunkte möglich.");
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
  calibrate.setOnClickListener(v->calibrateSketch(route,data->{calibration[0]=data;refreshEditor.run();}));
  clearScale.setOnClickListener(v->{calibration[0]=new org.json.JSONObject();refreshEditor.run();});
  route.changed=()->{history.record(route.encode());refreshEditor.run();};
  java.util.function.Consumer<Boolean> restore=forward->{
   String snapshot=history.step(forward);RouteSketch parsed=new RouteSketch(this,snapshot);
   route.vertices.clear();route.vertices.addAll(parsed.vertices);route.highlight=-1;route.dragged=-1;route.invalidate();refreshEditor.run();
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
  editor.setOnShowListener(ignored->editor.getButton(-1).setOnClickListener(v->{
   if(!initialSaved.equals(routeData())){notice("Trasse wurde zwischenzeitlich geändert. Editor neu öffnen.");return;}
   history.record(route.encode());
   if(!initialObjects.equals(draftObjects.toString())){double total=route.measuredLength();if(!initialObjects.equals(objects.toString())){notice("Objekte zwischenzeitlich geändert. Editor neu öffnen.");return;}for(int j=0;j<draftObjects.length();j++){org.json.JSONObject entry=draftObjects.optJSONObject(j);if(unchangedEditorObject(initialObjects,entry))continue;double st=entry.optDouble("station");if(!Double.isFinite(total)||!Double.isFinite(st)||st<0||st>total){notice("Objektstation außerhalb der bemaßten Trasse. Bitte korrigieren.");return;}}}
   if(persistEditorRoute(history,draftObjects)){boolean saved=getPreferences(0).edit().putString("sketchCalibration",calibration[0].toString()).commit();refreshRoute();editor.dismiss();notice(saved?"Trasse gespeichert.":"Trasse gespeichert, Kalibrierung nicht gespeichert.");}
  }));
  fullscreen.setOnClickListener(v->showFullscreenRoute(editor,form,route,mode,angles,historyBar,openObjects));
  editor.show();
  if(startFullscreen)fullscreen.performClick();
 }

 static org.json.JSONArray copyEditorObjects(String source){try{return new org.json.JSONArray(source);}catch(Exception e){throw new IllegalStateException(e);}}
 static boolean unchangedEditorObject(String initial,org.json.JSONObject object){
  if(object==null)return false;org.json.JSONArray original=copyEditorObjects(initial);
  for(int j=0;j<original.length();j++){org.json.JSONObject old=original.optJSONObject(j);if(old!=null&&old.toString().equals(object.toString()))return true;}return false;
 }
 boolean persistEditorRoute(RouteEditHistory history,org.json.JSONArray draft){
  if(draft.toString().equals(objects.toString()))return persistRouteHistory(history);
  try{
   String before=routeData(),old=objects.toString();org.json.JSONArray merged=new org.json.JSONArray(draft.toString());
   java.util.ArrayDeque<String> nextUndo=new java.util.ArrayDeque<>(undoObjects);nextUndo.push(old);while(nextUndo.size()>HISTORY_LIMIT)nextUndo.removeLast();
   if(!getPreferences(0).edit().putString("routeSketch",history.current).putString("routeHistoryHead",history.current).putString("routeUndo",new org.json.JSONArray(history.undo).toString()).putString("routeRedo",new org.json.JSONArray(history.redo).toString()).putString("objects",merged.toString()).putString("objectUndo",new org.json.JSONArray(nextUndo).toString()).putString("objectRedo","[]").commit()){notice("Trasse und Objekte konnten nicht gespeichert werden.");return false;}
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
 LinearLayout belagsFields;
 Button belagsToggle;

 void setBelagsExpanded(boolean expanded){
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
  String thickness=covering.equals("Asphalt")?data.getString("asphalt",""):data.getString("stoneDepth","");
  String unit=covering.equals("Asphalt")?" m":" cm";
  StringBuilder layers=new StringBuilder("Deckschicht · ").append(material).append(" · ")
   .append(thickness.isEmpty()?"Stärke nicht erfasst":thickness+unit);
  String bed=data.getString("bedding",""),old=data.getString("underlying",""),baseValue=data.getString("base","");
  layers.append("\nBettung · ").append(bed.isEmpty()?"nicht erfasst":bed);
  if(!old.isEmpty())layers.append("\nUnterliegender Altbelag · ").append(old);
  layers.append("\nTragschicht · ").append(baseValue.isEmpty()?"Stärke nicht erfasst":baseValue+" m");
  surfaceLayers.setText(layers.toString());
 }
 static final int INK=0xff193747, TEAL=0xff12665f;
 TextView[] overviewValues=new TextView[4];
 LinearLayout stationRows;
 final java.util.LinkedHashMap<LinearLayout,Button> sheetTabs=new java.util.LinkedHashMap<>();
 Button toolsMenu;

 TextView sheetText(String text,int size,boolean bold) {
  TextView label=new TextView(this);
  label.setText(text);
  label.setTextSize(size);
  label.setTextColor(INK);
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
    metric.setBackgroundColor(Color.WHITE);
    LinearLayout.LayoutParams box=new LinearLayout.LayoutParams(0,-2,1);
    box.setMargins(0,0,dp(4),dp(4));
    pair.addView(metric,box);
    overviewValues[i]=sheetText("–",21,true);
    overviewValues[i].setTextColor(TEAL);
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
   entry.setAllCaps(false);
   entry.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
   entry.setTextColor(objectWarning(index)?0xff982f20:INK);
   entry.setText(String.format(java.util.Locale.GERMANY,"%.2f m   ·   %s",object.optDouble("station"),object.optString("type")));
   entry.setOnClickListener(v->detailObject(index));
   if(!overlapSignature(index).isEmpty()){
    entry.setOnLongClickListener(v->{confirmObjectOverlap(index);return true;});
    entry.setText(entry.getText()+(overlapAccepted(index)?" · bestätigt":" · ⚠ Überlappung"));
   }
   stationRows.addView(entry);
  }
 }

 void addSheetTab(LinearLayout bar,String name,LinearLayout sheet){
  Button tab=new Button(this);
  tab.setAllCaps(false);
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
 static double sumAnsatz(org.json.JSONObject position){
  org.json.JSONArray rows=position.optJSONArray("ansatz");
  if(rows==null||rows.length()==0)return Double.NaN;
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
    stored.put(row);persistAnsatz(original,index,stored);
   }).show();
  }).setNegativeButton("Abbrechen",null).show();
 }
 void persistAnsatz(org.json.JSONArray positions,int index,org.json.JSONArray rows){
  try{org.json.JSONObject position=positions.getJSONObject(index);position.put("ansatz",rows);position.put("source","ansatz");if(saveLv(positions))showLvAnsatz(index);}catch(Exception e){notice("Mengenansatz konnte nicht gespeichert werden.");}
 }
 double lvQuantity(org.json.JSONObject o){
  String source=o.optString("source","manual");
  double value=Double.NaN;
  if("ansatz".equals(source))value=sumAnsatz(o);
  else if("manual".equals(source))value=o.optDouble("quantity");
  else if(l>0&&w>0&&d>0){
   if("length".equals(source)&&"m".equals(o.optString("unit")))value=l;
   if("area".equals(source)&&"m²".equals(o.optString("unit")))value=l*w;
   if("volume".equals(source)&&"m³".equals(o.optString("unit")))value=l*w*d;
   if("cut".equals(source)&&"m²".equals(o.optString("unit"))&&Double.isFinite(left)&&Double.isFinite(right))value=l*(w+left+right);
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
   Button button=new Button(this);button.setAllCaps(false);button.setText(lvLabel(o)+"\nIst: "+(Double.isFinite(qty)?csvNumber(qty):"offen")+(Double.isFinite(target)?" · Soll: "+csvNumber(target):""));
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
  String[] sources={"manual","length","area","volume","cut","ansatz"};String[] sourceLabels={"Manuelle Istmenge","Trassenlänge (m)","Grundfläche (m²)","Aushub – geometrisch (m³)","Fläche mit Nachschnitt (m²)","Mengenansätze"};
  form.addView(sheetText("Mengenquelle",14,false));Spinner source=choices(form,sourceLabels);source.setSelection(Math.max(0,java.util.Arrays.asList(sources).indexOf(old==null?"manual":old.optString("source"))));
  form.addView(sheetText("Automatische Quellen verwenden die gespeicherten Haupttrassenmaße, keine LV-spezifischen Abzugsregeln.",13,false));
  android.app.AlertDialog.Builder builder=new android.app.AlertDialog.Builder(this).setTitle("LV-Position").setView(scroll).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null);
  if(old!=null&&!old.optString("gaebLongText").isEmpty())form.addView(sheetText("GAEB-Langtext\n"+old.optString("gaebLongText"),13,false));
  if(old!=null)builder.setNeutralButton("Löschen",(a,b)->new android.app.AlertDialog.Builder(this).setTitle("LV-Position löschen?").setNegativeButton("Abbrechen",null).setPositiveButton("Löschen",(x,y)->{org.json.JSONArray next=lvPositions();if(!next.toString().equals(original.toString())){notice("LV geändert. Neu öffnen.");return;}next.remove(index);if(saveLv(next))showLv();}).show());
  android.app.AlertDialog dialog=builder.create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{
   try{
    String oz=fields[0].getText().toString().trim(),title=fields[1].getText().toString().trim(),unit=fields[2].getText().toString().trim();
    if(oz.isEmpty()||title.isEmpty()||unit.isEmpty()){notice("Ordnungszahl, Kurztext und Einheit erforderlich.");return;}
    String chosen=sources[source.getSelectedItemPosition()];
    String expected="length".equals(chosen)?"m":"volume".equals(chosen)?"m³":("manual".equals(chosen)||"ansatz".equals(chosen))?unit:"m²";
    if(!expected.equals(unit)){fields[2].setError("Einheit "+expected+" erforderlich");return;}
    org.json.JSONObject item=old==null?new org.json.JSONObject():new org.json.JSONObject(old.toString());item.remove("target");item.remove("quantity");item.put("oz",oz).put("title",title).put("unit",unit).put("source",chosen);
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
  new android.app.AlertDialog.Builder(this).setTitle(changeOrder?"Fotos zum Nachtrag":"Fotos zum Bautagesbericht").setMultiChoiceItems(labels,checked,(d,j,c)->checked[j]=c)
   .setNegativeButton("Abbrechen",null).setPositiveButton("Übernehmen",(d,w)->{
    if(!(changeOrder?nachtraege():dailyReports()).toString().equals(original.toString())){notice("Bericht geändert. Neu öffnen.");return;}
    org.json.JSONArray photos=new org.json.JSONArray();for(int j=0;j<list.size();j++)if(checked[j])photos.put(list.get(j));
    try{report.put("photos",photos).put("modifiedAt",System.currentTimeMillis());if(changeOrder){if(saveNachtraege(original))showNachtraege();}else if(saveDailyReports(original))showDailyReports();}catch(Exception e){notice("Fotos konnten nicht zugeordnet werden.");}
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
   String projectName=projectHeader.getText().toString();StringBuilder text=new StringBuilder("Projekt: "+projectName+"\nDatum: "+report.optString("date")+"\n");
   for(int j=1;j<DAILY_KEYS.length;j++){String value=report.optString(DAILY_KEYS[j]);text.append("\n").append(DAILY_LABELS[j]).append("\n").append(value.isEmpty()?"Nicht erfasst":value).append("\n");}
   org.json.JSONArray photos=dailyPhotos(report);text.append("\nZugeordnete Fotos: ").append(photos.length());
   int page=dailyTextPdf(pdf,1,"Bautagesbericht",text.toString(),null);
   for(int j=0;j<photos.length();j++){org.json.JSONObject o=photos.optJSONObject(j);if(o==null)continue;android.graphics.Bitmap bitmap=reportPhoto(o.optString("file"));String info=projectName+"\nBerichtsdatum: "+report.optString("date")+"\n"+o.optString("info");if(bitmap==null)info+="\nBilddatei fehlt oder ist nicht lesbar.";
    page=dailyTextPdf(pdf,page,"Foto "+(j+1)+" / "+photos.length(),info,bitmap);
   }
   pdf.writeTo(out);notice("Bautagesbericht gespeichert.");
  }catch(Exception e){notice("PDF konnte nicht gespeichert werden.");}finally{pdf.close();}
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
   String projectName=projectHeader.getText().toString();StringBuilder text=new StringBuilder("Projekt: "+projectName+"\nDatum: "+row.optString("date")+"\nBearbeitungsstatus: "+row.optString("status")+"\n");
   String[] keys={"title","reason","order","evidence"},labels={"Bezeichnung / LV-Bezug","Sachverhalt / Begründung","Anordnung / Auftraggeber","Nachweise / Bezug"};
   for(int j=0;j<keys.length;j++){String value=row.optString(keys[j]);text.append("\n").append(labels[j]).append("\n").append(value.isEmpty()?"Nicht erfasst":value).append("\n");}
   double q=row.optDouble("quantity");text.append("\nZusätzliche Menge\n").append(Double.isFinite(q)?csvNumber(q)+" "+row.optString("unit"):"Nicht erfasst");
   org.json.JSONArray photos=dailyPhotos(row);text.append("\n\nZugeordnete Fotos: ").append(photos.length());
   text.append("\n\nDokumentation – kein automatisch ermittelter Vergütungsanspruch. Vertragsgrundlage, Mengen und Freigabe gesondert prüfen.");
   int page=dailyTextPdf(pdf,1,"Nachtragsdokumentation",text.toString(),null);
   for(int j=0;j<photos.length();j++){org.json.JSONObject o=photos.optJSONObject(j);if(o==null)continue;android.graphics.Bitmap bitmap=reportPhoto(o.optString("file"));
    String info=projectName+"\nNachtrag: "+row.optString("title")+"\n"+o.optString("info");if(bitmap==null)info+="\nBilddatei fehlt oder ist nicht lesbar.";
    page=dailyTextPdf(pdf,page,"Nachweisfoto "+(j+1)+" / "+photos.length(),info,bitmap);
   }
   pdf.writeTo(out);notice("Nachtragsdokumentation gespeichert.");
  }catch(Exception e){notice("PDF konnte nicht gespeichert werden.");}finally{pdf.close();}
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
   .setItems(new String[]{"Formstückkatalog","Projekt-Formstücke","Gesamtplan – Stationierung"},(d,w)->{if(w==0)showMontageCatalog();else if(w==1)showMontageInventory();else showMontageStationPlan();})
   .setNegativeButton("Schließen",null).show();
 }
 void showMontageStationPlan(){
  try{
   double total=Double.parseDouble(getPreferences(0).getString("length","").trim().replace(',','.'));
   if(!Double.isFinite(total)||total<=0){notice("Gültige Tiefbau-Trassenlänge erforderlich.");return;}
   org.json.JSONArray rows=new org.json.JSONArray(getPreferences(0).getString("montageFittings","[]"));
   java.util.ArrayList<org.json.JSONObject> linked=new java.util.ArrayList<>();int missing=0;
   for(int i=0;i<rows.length();i++){org.json.JSONObject o=rows.getJSONObject(i);double st=o.optDouble("station",Double.NaN);if(!Double.isFinite(st)||st<0||st>total)missing++;else linked.add(o);}
   linked.sort((x,y)->Double.compare(x.optDouble("station"),y.optDouble("station")));
   LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(12),dp(8),dp(12),dp(8));
   TextView hint=new TextView(this);hint.setText("Lineare Stationsübersicht – keine maßstäbliche Montagegeometrie. Ohne gültige Station: "+missing);panel.addView(hint);
   android.view.View axis=new android.view.View(this){
    final android.graphics.Paint paint=new android.graphics.Paint(3);
    @Override protected void onDraw(android.graphics.Canvas c){
     super.onDraw(c);float left=dp(18),right=getWidth()-dp(18),y=dp(42);if(right<=left)return;
     paint.setColor(0xff9b876c);paint.setStrokeWidth(dp(18));c.drawLine(left,y,right,y,paint);
     paint.setColor(0xff398bda);paint.setStrokeWidth(dp(2));c.drawLine(left,y,right,y,paint);
     for(org.json.JSONObject o:linked){float x=left+(right-left)*(float)(o.optDouble("station")/total);paint.setColor(0xffe6a23c);c.drawCircle(x,y,dp(5),paint);}
    }
   };panel.addView(axis,new LinearLayout.LayoutParams(-1,dp(70)));
   TextView range=new TextView(this);range.setText("0,00 m  —  "+total+" m");panel.addView(range);
   RouteSketch geometry=new RouteSketch(this,routeData());
   double measured=geometry.measuredLength();
   if(Double.isFinite(measured)&&measured>0&&Math.abs(measured-total)<0.001){
    TextView title=new TextView(this);title.setText("Gemeinsame Trassenskizze – Stationspunkte (ohne seitlichen Versatz)");panel.addView(title);
    android.view.View map=new android.view.View(this){
     final android.graphics.Paint pen=new android.graphics.Paint(3);
     @Override protected void onDraw(android.graphics.Canvas c){
      super.onDraw(c);float size=Math.min(getWidth(),getHeight())-dp(32);if(size<=0)return;
      float ox=(getWidth()-size)/2,oy=(getHeight()-size)/2;
      pen.setColor(0xff9b876c);pen.setStrokeWidth(dp(14));pen.setStrokeCap(android.graphics.Paint.Cap.ROUND);
      for(int i=1;i<geometry.vertices.size();i++){android.graphics.PointF a=geometry.vertices.get(i-1).point,b=geometry.vertices.get(i).point;c.drawLine(ox+a.x*size,oy+a.y*size,ox+b.x*size,oy+b.y*size,pen);}
      pen.setColor(0xff398bda);pen.setStrokeWidth(dp(2));
      for(int i=1;i<geometry.vertices.size();i++){android.graphics.PointF a=geometry.vertices.get(i-1).point,b=geometry.vertices.get(i).point;c.drawLine(ox+a.x*size,oy+a.y*size,ox+b.x*size,oy+b.y*size,pen);}
      pen.setColor(0xffe6a23c);
      for(org.json.JSONObject o:linked){android.graphics.PointF p=geometry.stationPoint(o.optDouble("station"));if(p!=null)c.drawCircle(ox+p.x*size,oy+p.y*size,dp(5),pen);}
     }
    };panel.addView(map,new LinearLayout.LayoutParams(-1,dp(240)));
   }else{TextView warning=new TextView(this);warning.setText("Geometrische Zuordnung offen: Skizzenmaße fehlen oder stimmen nicht mit der Trassenlänge überein.");panel.addView(warning);}

   for(org.json.JSONObject o:linked){TextView row=new TextView(this);row.setPadding(0,dp(6),0,dp(6));row.setText(o.optDouble("station")+" m · "+MontageCatalog.Fitting.valueOf(o.getString("type")).label+" · DN "+(o.optString("dn").isEmpty()?"unbekannt":o.optString("dn")));panel.addView(row);}
   android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(panel);
   new android.app.AlertDialog.Builder(this).setTitle("Gesamtplan – Stationierung").setView(scroll).setPositiveButton("Zurück",(d,w)->showMontage()).show();
  }catch(Exception e){notice("Stationsübersicht nicht verfügbar. Trassenlänge und Projektdaten prüfen.");}
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
   tile.setContentDescription(fitting.label);tile.setFocusable(true);tile.setOnClickListener(v->{dialog.dismiss();showMontageFitting(fitting);});
  }
  dialog.show();
 }
 void showMontageFitting(MontageCatalog.Fitting fitting){
  final String projectId=activeProject;
  LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);
  panel.addView(new MontageSymbolView(this,fitting),new LinearLayout.LayoutParams(-1,dp(100)));
  EditText dn=new EditText(this);dn.setHint("DN (optional)");dn.setInputType(2);panel.addView(dn);
  EditText material=new EditText(this);material.setHint("Werkstoff (optional)");panel.addView(material);
  EditText rotation=new EditText(this);rotation.setHint("Drehwinkel (°)");rotation.setInputType(8194|4096);rotation.setText("0");panel.addView(rotation);
  android.widget.CheckBox mirror=new android.widget.CheckBox(this);mirror.setText("Spiegeln");panel.addView(mirror);
  EditText station=new EditText(this);station.setHint("Station Tiefbau (m, optional)");station.setInputType(8194);panel.addView(station);
  EditText offset=new EditText(this);offset.setHint("Seitlicher Versatz (m, + rechts / − links)");offset.setInputType(8194|4096);panel.addView(offset);
  EditText cover=new EditText(this);cover.setHint("Überdeckung bis Rohroberkante (m, optional)");cover.setInputType(8194);panel.addView(cover);
  android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(panel);
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle(fitting.label).setView(scroll).setNegativeButton("Zurück",(d,w)->showMontageCatalog()).setPositiveButton("Zum Projekt hinzufügen",null).create();
  dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{
   if(!projectId.equals(activeProject)){notice("Projekt wurde gewechselt.");return;}
   String diameter=dn.getText().toString().trim();
   if(!diameter.isEmpty()&&!diameter.matches("[1-9][0-9]{0,3}")){dn.setError("Ungültige Nennweite");return;}
   double angle;try{angle=MontageCatalog.normalizeRotation(Double.parseDouble(rotation.getText().toString().trim().replace(',','.')));}catch(Exception e){rotation.setError("Ungültiger Winkel");return;}
   try{
    org.json.JSONArray rows=new org.json.JSONArray(getPreferences(0).getString("montageFittings","[]"));
    org.json.JSONObject item=new org.json.JSONObject().put("id",java.util.UUID.randomUUID().toString()).put("type",fitting.name()).put("dn",diameter).put("material",material.getText().toString().trim()).put("rotation",angle).put("mirrored",mirror.isChecked()).put("placed",false);
    EditText[] coordinates={station,offset,cover};String[] keys={"station","offset","cover"};
    if(station.length()==0&&(offset.length()>0||cover.length()>0)){station.setError("Station für die Zuordnung erforderlich");return;}
    for(int i=0;i<coordinates.length;i++){String raw=coordinates[i].getText().toString().trim();if(raw.isEmpty())continue;double value;
     try{value=Double.parseDouble(raw.replace(',','.'));if(!Double.isFinite(value)||(i!=1&&value<0))throw new IllegalArgumentException();}catch(Exception e){coordinates[i].setError("Ungültiges Maß");return;}
     if(i==0){String routeLength=getPreferences(0).getString("length","").trim();try{double max=Double.parseDouble(routeLength.replace(',','.'));if(!Double.isFinite(max)||max<=0||value>max)throw new IllegalArgumentException();}catch(Exception e){station.setError("Station innerhalb einer gültigen Tiefbau-Trassenlänge erforderlich");return;}}
     item.put(keys[i],value);
    }
    item.put("stationLinked",item.has("station"));rows.put(item);
    if(!getPreferences(0).edit().putString("montageFittings",rows.toString()).commit()){notice("Speichern fehlgeschlagen.");return;}
    dialog.dismiss();notice("Formstück gespeichert – noch nicht platziert.");showMontageCatalog();
   }catch(Exception e){notice("Formstück konnte nicht gespeichert werden.");}
  }));dialog.show();
 }
 void showMontageInventory(){
  try{org.json.JSONArray rows=new org.json.JSONArray(getPreferences(0).getString("montageFittings","[]"));StringBuilder out=new StringBuilder();for(int i=0;i<rows.length();i++){org.json.JSONObject row=rows.getJSONObject(i);out.append(MontageCatalog.Fitting.valueOf(row.getString("type")).label).append(" · DN ").append(row.optString("dn").isEmpty()?"unbekannt":row.optString("dn")).append(" · ").append(row.optString("material")).append(row.has("station")?" · Station "+row.optDouble("station")+" m":" · ohne Station").append(" · nicht platziert\n");}new android.app.AlertDialog.Builder(this).setTitle("Projekt-Formstücke").setMessage(out.length()==0?"Keine Formstücke gespeichert.":out.toString()).setPositiveButton("Zurück",(d,w)->showMontage()).show();}catch(Exception e){notice("Formstücke konnten nicht gelesen werden.");}
 }
 void showMetricRoute(){
  RouteSketch route=new RouteSketch(this,routeData());
  if(!DxfExport.metricAvailable(route)){notice("Zuerst alle Trassenabschnitte bemaßen und gültige Richtungen zeichnen.");return;}
  DxfExport model=new DxfExport(new java.io.StringWriter(),route,true);
  final java.util.ArrayList<double[]> montagePoints=new java.util.ArrayList<>();
  try{
   org.json.JSONArray items=new org.json.JSONArray(getPreferences(0).getString("montageFittings","[]"));
   int n=model.vertices.size();double[] xs=new double[n],ys=new double[n],ls=new double[n-1];
   for(int i=0;i<n;i++){xs[i]=model.vertices.get(i)[0];ys[i]=-model.vertices.get(i)[1];if(i>0)ls[i-1]=route.vertices.get(i).metres;}
   for(int i=0;i<items.length();i++){org.json.JSONObject o=items.optJSONObject(i);if(o==null)continue;try{
    MontageRouteGeometry.Position pos=MontageRouteGeometry.locate(xs,ys,ls,o.optDouble("station",Double.NaN),o.optDouble("offset",0));
    if(pos.atCorner&&o.optDouble("offset",0)!=0)continue;
    montagePoints.add(new double[]{pos.x,-pos.y});
   }catch(IllegalArgumentException ignored){}}
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
   geometryStatus.setText(crossings==0&&missingWidths==0?"Geometrie: keine Achsen-Selbstkreuzung erkannt; Flächenprüfung noch offen.":"Prüfung: "+crossings+" Achsen-Selbstkreuzung(en), "+missingWidths+" Abschnitt(e) ohne Grabenbreite. Keine automatische Abrechnung.");
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
     entry.setOnShowListener(d->entry.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{
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
    double part=(to-from)*w;area+=part;
    double cutLeft=widths[1],cutRight=widths[2];for(int j=0;j<widthSections.length();j++){org.json.JSONObject o=widthSections.optJSONObject(j);if(o!=null&&mid>=o.optDouble("from",Double.NaN)&&mid<o.optDouble("to",Double.NaN)){if(o.has("left"))cutLeft=o.optDouble("left",Double.NaN);if(o.has("right"))cutRight=o.optDouble("right",Double.NaN);}}
    if(Double.isFinite(cutLeft)&&cutLeft>=0&&Double.isFinite(cutRight)&&cutRight>=0){nachschnittLeft+=(to-from)*cutLeft;nachschnittRight+=(to-from)*cutRight;nachschnittArea+=(to-from)*(cutLeft+cutRight);}else missingCuts=true;
    String surface=null;int matches=0;
    for(int j=0;j<surfaces.length();j++){org.json.JSONObject o=surfaces.optJSONObject(j);if(o==null)continue;
     double lo=o.optDouble("from",Double.NaN),hi=o.optDouble("to",Double.NaN);String name=o.optString("surface","").trim();
     if(Double.isFinite(lo)&&Double.isFinite(hi)&&lo>=0&&hi<=total&&lo<=mid&&mid<hi&&!name.isEmpty()){surface=name;matches++;}
    }
    if(matches==1){bySurface.put(surface,bySurface.containsKey(surface)?bySurface.get(surface)+part:part);if(Double.isFinite(cutLeft)&&cutLeft>=0&&Double.isFinite(cutRight)&&cutRight>=0){double extra=(to-from)*(cutLeft+cutRight);cutsBySurface.put(surface,cutsBySurface.containsKey(surface)?cutsBySurface.get(surface)+extra:extra);}}
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
       StripOverlap.Result total=StripOverlap.calculate(combined);
       result.append(String.format(java.util.Locale.GERMANY,"\nGesamttrasse inkl. Abzweigungen (gerade Streifen):\nAbzweiglänge: %.2f m\nAbzweigfläche nominal: %.2f m²\nBrutto gesamt: %.2f m²\nÜberlappung gesamt: %.2f m²\nNetto gesamt: %.2f m²",branchLength,branchArea,total.gross,total.overlap,total.net));
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


       if(combinedMode==1||combinedMode==2)result.append(String.format(java.util.Locale.GERMANY,"\nKontrollwert Gesamttrasse: %.2f m²",combinedMode==1?total.gross:total.net));
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
      new android.app.AlertDialog.Builder(MainActivity.this).setTitle(String.format(java.util.Locale.GERMANY,"Station %.3f m",station)).setItems(new String[]{"Abzweigung hinzufügen"},(dialog,which)->{requestedBranchStation[0]=station;branchEditor.performClick();}).setNegativeButton("Abbrechen",null).show();
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
  menu.getMenu().add(0,116,Menu.NONE,"Handskizze als Zeichenvorlage");
  menu.getMenu().add(0,117,Menu.NONE,"Bluetooth-Messgerät (Test)");
  menu.getMenu().add(0,118,Menu.NONE,"Rohrleitungsbau – Montage");
  menu.getMenu().add(0,119,Menu.NONE,"Tiefbau – Maßstäbliche Vorschau");
  menu.setOnMenuItemClickListener(item->{if(item.getItemId()==119)showMetricRoute();else if(item.getItemId()==118)showMontage();else if(item.getItemId()==117)testBleMeasure();else if(item.getItemId()==116)chooseRouteUnderlay();else if(item.getItemId()==115)chooseImageAnalysis();else if(item.getItemId()==114)choosePdfAnalysis();else if(item.getItemId()==113)showNachtraege();else if(item.getItemId()==112)showDailyReports();else if(item.getItemId()==111)exportPhotoReport();else if(item.getItemId()==110)exportX31();else if(item.getItemId()==109)exportReb();else if(item.getItemId()==108)showLv();else if(item.getItemId()==107)exportCsv();else if(item.getItemId()==106)exportDxf();else if(item.getItemId()==105)showLongitudinalProfile();else if(item.getItemId()==104)showAutomaticBackups();else if(item.getItemId()==103)showProjectHistory();else if(item.getItemId()==102)requestProjectRestore();else if(item.getItemId()==101)requestProjectBackup();else if(item.getItemId()==100)showProjects();else showPage(pages[item.getItemId()]);return true;});
  menu.show();
 }
 LinearLayout page(){LinearLayout p=new LinearLayout(this);p.setOrientation(1);p.setPadding(dp(12),dp(8),dp(12),dp(8));return p;}
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 void showPage(LinearLayout p){
  for(java.util.Map.Entry<LinearLayout,Button> entry:sheetTabs.entrySet()){
   boolean selected=entry.getKey()==p;
   Button tab=entry.getValue();
   tab.setSelected(selected);
   tab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selected?TEAL:Color.WHITE));
   tab.setTextColor(selected?Color.WHITE:INK);
  }
  if(p==documentsPage)compareSoll();if(p==quantityPage)refreshDetailQuantities();body.removeAllViews();ScrollView sc=new ScrollView(this);sc.setFillViewport(true);if(p.getParent()!=null)((android.view.ViewGroup)p.getParent()).removeView(p);sc.addView(p);body.addView(sc,new LinearLayout.LayoutParams(-1,-1));}
 private void fitSystemPanels(LinearLayout root){
  Window window=getWindow();
  androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false);
  window.setStatusBarColor(Color.TRANSPARENT);
  window.setNavigationBarColor(Color.TRANSPARENT);
  if(android.os.Build.VERSION.SDK_INT>=29)window.setNavigationBarContrastEnforced(false);
  window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
  androidx.core.view.WindowInsetsControllerCompat controller=
    androidx.core.view.WindowCompat.getInsetsController(window,root);
  controller.setAppearanceLightStatusBars(true);
  controller.setAppearanceLightNavigationBars(true);
  androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root,(view,insets)->{
   int types=androidx.core.view.WindowInsetsCompat.Type.systemBars()
      |androidx.core.view.WindowInsetsCompat.Type.displayCutout()
      |androidx.core.view.WindowInsetsCompat.Type.ime();
   androidx.core.graphics.Insets safe=insets.getInsets(types);
   view.setPadding(safe.left,safe.top,safe.right,safe.bottom);
   return insets;
  });
  androidx.core.view.ViewCompat.requestApplyInsets(root);
 }

 public void onCreate(Bundle b){super.onCreate(b);activeProject=projectRegistry().getString("active",LEGACY_PROJECT);
 if(b!=null){selectedPoint=b.getString("selectedPoint");pendingPoint=b.getString("pendingPoint");pendingPhoto=b.getString("pendingPhoto");}
 shell=page();shell.setPadding(0,0,0,0);shell.setBackgroundColor(0xfff4f6f8);setContentView(shell);fitSystemPanels(shell);
 planPage=page();measurePage=page();voicePage=page();profilePage=page();segmentPage=page();objectPage=page();quantityPage=page();
 LinearLayout header=new LinearLayout(this);
 header.setGravity(Gravity.CENTER_VERTICAL);
 header.setPadding(dp(16),dp(4),dp(8),dp(4));
 header.setBackgroundColor(Color.WHITE);
 shell.addView(header);
 projectHeader=sheetText(getPreferences(0).getString("project","Feldaufmaß Pro"),22,true);
 projectHeader.setTextColor(INK);
 projectHeader.setMaxLines(2);
 projectHeader.setEllipsize(android.text.TextUtils.TruncateAt.END);
 header.addView(projectHeader,new LinearLayout.LayoutParams(0,-2,1));
 toolsMenu=new Button(this);
 toolsMenu.setAllCaps(false);
 toolsMenu.setText("Werkzeuge");
 header.addView(toolsMenu);
 toolsMenu.setOnClickListener(v->showTools());
 LinearLayout tabs=new LinearLayout(this);shell.addView(tabs);
 addSheetTab(tabs,"Trasse",planPage);
 addSheetTab(tabs,"Profile",profilePage);
 addSheetTab(tabs,"Mengen",quantityPage);
 body=new LinearLayout(this);shell.addView(body,new LinearLayout.LayoutParams(-1,0,1));
 LinearLayout dock=new LinearLayout(this);dock.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
 dock.setBackgroundColor(Color.WHITE);shell.addView(dock);
 ImageButton assistant=new ImageButton(this);assistant.setImageResource(android.R.drawable.ic_btn_speak_now);
 assistant.setContentDescription("Sprachassistent starten");dock.addView(assistant,new LinearLayout.LayoutParams(dp(52),dp(48)));
 assistant.setOnClickListener(v->startDictation());
 actionButtonSingle(dock,"Assistent",()->showPage(voicePage));
 actionButtonSingle(planPage,"Zeichnungsebenen",()->showDrawingLayers());
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
 project=field(root,"Projekt / Baustelle","project",false);
 length=field(root,"Trassenlänge (m)","length",true);
 Button adopt=new Button(this);adopt.setText("Bemaßte Skizzenlänge übernehmen");root.addView(adopt);
 adopt.setOnClickListener(v->{
  double total=new RouteSketch(this,routeData()).measuredLength();
  if(Double.isFinite(total))length.setText(java.math.BigDecimal.valueOf(total).stripTrailingZeros().toPlainString());
  else Toast.makeText(this,"Zuerst alle Skizzenabschnitte bemaßen.",Toast.LENGTH_LONG).show();
 });
 width=field(root,"Grabenbreite (m)","width",true);
 depth=field(root,"Grabentiefe (m)","depth",true);
 TextView surfaceLabel=new TextView(this);surfaceLabel.setText("Oberfläche");root.addView(surfaceLabel);
 surface=new Spinner(this);String[] types={"Unbekannt","Asphalt","Pflaster","Gehwegplatten","Unbefestigt","Betonpflaster – Rechteck","Betonpflaster – Quadrat","Doppel-T-Pflaster","Wellenverbundpflaster","Sechseckpflaster","Achteckpflaster","Verbundpflaster – sonstige","Betonpflaster – Mehrformat","Ökopflaster / Sickerpflaster","Rasengittersteine","Rasenfugenpflaster","Natursteinpflaster – Großpflaster","Natursteinpflaster – Kleinpflaster","Natursteinpflaster – Mosaikpflaster","Natursteinpflaster – sonstige","Klinkerpflaster","Ziegelpflaster","Beton-Gehwegplatten","Natursteinplatten","Waschbetonplatten","Taktilplatten – Rippen","Taktilplatten – Noppen","Rinnenpflaster","Schlackenpflaster","Holzpflaster","Sonstiges","Beton","Betonpflaster","Großpflaster","Kleinpflaster","Gehwegplatten 1 × 1 m","Verbundpflaster","Schotter","Kies","Sand","Unbefestigte Fläche","Rasen","Grünfläche","Wassergebundene Decke","Sonstige Oberfläche"};
 java.util.ArrayList<String> surfaceTypes=new java.util.ArrayList<>(java.util.Arrays.asList(types));
 android.content.SharedPreferences catalog=getSharedPreferences("surface_catalog",MODE_PRIVATE);
 try{org.json.JSONArray custom=new org.json.JSONArray(catalog.getString("custom","[]"));for(int i=0;i<custom.length();i++){String name=custom.optString(i).trim();if(!name.isEmpty()&&!surfaceTypes.contains(name))surfaceTypes.add(name);}}catch(Exception ignored){}
 ArrayAdapter<String> surfaceAdapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,surfaceTypes);
 surface.setAdapter(surfaceAdapter);root.addView(surface);
 surface.setSelection(Math.min(surfaceTypes.size()-1,Math.max(0,getPreferences(0).getInt("surface",0))));
 Button addSurface=new Button(this);addSurface.setAllCaps(false);addSurface.setText("Eigene Oberfläche hinzufügen");root.addView(addSurface);
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
 belagsToggle.setAllCaps(false);
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
 underlying=field(belagsFields,"Unterliegender Altbelag (z. B. Großpflaster)","underlying",false);
 TextView handlingLabel=new TextView(this);handlingLabel.setText("Umgang mit Altbelag");root.addView(handlingLabel);
 handling=new Spinner(this);String[] options={"Nicht erfasst","Aufnahme","Aufnahme und Entsorgung","Aufnahme und Wiederverwendung","Wiederherstellung"};
 handling.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,options));root.addView(handling);handling.setSelection(Math.min(4,Math.max(0,getPreferences(0).getInt("handling",0))));
 baseInput=field(root,"Tragschichtstärke (m, optional)","base",true);
 pipeInput=field(root,"Rohr-Außendurchmesser (mm, optional)","pipe",true);
 Button utilitiesButton=new Button(this);utilitiesButton.setText("Weitere Leitungen / Kabel");utilitiesButton.setAllCaps(false);root.addView(utilitiesButton);utilitiesButton.setOnClickListener(v->editUtilities());
 asphaltInput=field(root,"Asphaltstärke (m, optional)","asphalt",true);
 leftInput=field(root,"Nachschnitt links (m, optional)","left",true);
 rightInput=field(root,"Nachschnitt rechts (m, optional)","right",true);
 Button save=new Button(this);save.setText("Berechnen und speichern");root.addView(save);
 root=quantityPage;
 result=new TextView(this);result.setTextSize(18);root.addView(result);detailQuantities=new TextView(this);root.addView(detailQuantities);
 root=planPage;
 sketch=new Sketch();sketch.setBackgroundColor(Color.WHITE);root.addView(sketch,new LinearLayout.LayoutParams(-1,dp(280))); LinearLayout historyBar=new LinearLayout(this);root.addView(historyBar);undoObjectsButton=new Button(this);undoObjectsButton.setText("Rückgängig");historyBar.addView(undoObjectsButton,new LinearLayout.LayoutParams(0,-2,1));redoObjectsButton=new Button(this);redoObjectsButton.setText("Wiederholen");historyBar.addView(redoObjectsButton,new LinearLayout.LayoutParams(0,-2,1));undoObjectsButton.setOnClickListener(v->restoreObjectHistory(false));redoObjectsButton.setOnClickListener(v->restoreObjectHistory(true));updateHistoryButtons();
 Button edit=new Button(this);edit.setText("Trassenmaße bearbeiten");root.addView(edit);edit.setOnClickListener(v->showPage(measurePage));
 sheetHeading(planPage,"Stationierte Objekte");
 stationRows=new LinearLayout(this);
 stationRows.setOrientation(LinearLayout.VERTICAL);
 planPage.addView(stationRows);
 root=profilePage;
 actionButtonSingle(root,"Längsschnitt anzeigen",()->showLongitudinalProfile());
 TextView heading=new TextView(this);heading.setText("Regelprofil – Grabenquerschnitt");heading.setTextSize(20);root.addView(heading);
 LinearLayout layers=fold(root,"Oberflächenaufbau",true);
 surfaceLayers=sheetText("",16,false);surfaceLayers.setLineSpacing(dp(6),1);layers.addView(surfaceLayers);showSurfaceLayers();
 Button editLayers=new Button(this);editLayers.setText("Oberflächenaufbau bearbeiten");layers.addView(editLayers);
 editLayers.setOnClickListener(v->openBelagsDetails());
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
 profile=new Profile();root.addView(profile,1,new LinearLayout.LayoutParams(-1,(int)(300*getResources().getDisplayMetrics().density)));
 LinearLayout sizes=fold(profilePage,"Grabenmaße",false);
 profileSlider(sizes,"Grabenbreite",width,10,600);
 profileSlider(sizes,"Grabentiefe",depth,10,800);
 profileSlider(sizes,"Nachschnitt links",leftInput,0,200);
 profileSlider(sizes,"Nachschnitt rechts",rightInput,0,200);
 LinearLayout pipeDetails=fold(profilePage,"Rohr und Bettung",false);
 profilePage.removeView(legend);pipeDetails.addView(legend);
 actionButtonSingle(profilePage,"Speichern",()->calculate());
 save.setOnClickListener(v->{if(calculate())showPage(planPage);});if(length.length()>0&&width.length()>0&&depth.length()>0)calculate();showPage(planPage);
 }
 void actionButton(LinearLayout bar,String label,Runnable action){
  Button button=actionButtonSingle(bar,label,action);button.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
 }
 Button actionButtonSingle(LinearLayout bar,String label,Runnable action){
  Button button=new Button(this);button.setAllCaps(false);button.setText(label);
  button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(TEAL));button.setTextColor(Color.WHITE);
  button.setOnClickListener(v->action.run());bar.addView(button);return button;
 }
 LinearLayout fold(LinearLayout parent,String title,boolean expanded){
  Button toggle=new Button(this);toggle.setAllCaps(false);toggle.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
  toggle.setText(title+(expanded?" ▴":" ▾"));parent.addView(toggle);
  LinearLayout content=page();parent.addView(content);content.setVisibility(expanded?View.VISIBLE:View.GONE);
  toggle.setOnClickListener(v->{boolean open=content.getVisibility()!=View.VISIBLE;
   content.setVisibility(open?View.VISIBLE:View.GONE);toggle.setText(title+(open?" ▴":" ▾"));});
  return content;
 }
 void profileSlider(LinearLayout page,String name,EditText target,int min,int max){
 TextView label=new TextView(this);page.addView(label);SeekBar bar=new SeekBar(this);bar.setMax(max-min);page.addView(bar);
 EditText exact=new EditText(this);exact.setSingleLine(true);exact.setInputType(8194);exact.setText(target.getText());page.addView(exact);
 final boolean[] busy={false};
 Runnable sync=()->{if(busy[0])return;busy[0]=true;try{double val=number(target);label.setText(name+" · "+String.format(java.util.Locale.GERMANY,"%.2f m",val));bar.setProgress((int)Math.round(val*100)-min);exact.setText(target.getText());}catch(Exception e){label.setText(name+" (m)");}busy[0]=false;};
 target.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){sync.run();}public void afterTextChanged(android.text.Editable e){}});
 bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int progress,boolean user){if(!user)return;target.setText(String.format(java.util.Locale.GERMANY,"%.2f",(progress+min)/100.0));calculate();}});
 exact.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){if(busy[0])return;try{double v=number(exact);if(!Double.isFinite(v)||v<min/100.0||v>max/100.0)return;busy[0]=true;target.setText(s);label.setText(name+" · "+s+" m");bar.setProgress((int)Math.round(v*100)-min);busy[0]=false;calculate();}catch(Exception e){busy[0]=false;}}public void afterTextChanged(android.text.Editable e){}});
 sync.run();
 }
 boolean calculate(){try{double l=number(length),w=number(width),d=number(depth);if(!Double.isFinite(l*w*d)||l<=0||w<=0||d<=0)throw new Exception();
 optional(stoneLength);optional(stoneWidth);double stoneMetres=optional(stoneDepth)/100;
 String covering=surface.getSelectedItem().toString();double base=optional(baseInput),pipe=optional(pipeInput);if(Double.isFinite(pipe)&&(pipe<=0||pipe/1000>w||pipe/1000>d))throw new Exception();
 double asphalt=optional(asphaltInput),left=optional(leftInput),right=optional(rightInput);double topLayer=covering.equals("Asphalt")?asphalt:stoneMetres;
 if(Double.isFinite(topLayer)&&topLayer>d)throw new Exception();
 if(Double.isFinite(base)&&base+(Double.isFinite(topLayer)?topLayer:0)>d)throw new Exception();
 this.l=l; this.w=w; this.d=d; this.covering=covering;
 topThickness=topLayer; this.base=base; this.pipe=pipe; this.asphalt=asphalt; this.left=left; this.right=right;
 if(!getPreferences(0).edit().putString("stoneLength",stoneLength.getText().toString()).putString("stoneWidth",stoneWidth.getText().toString()).putString("stoneDepth",stoneDepth.getText().toString()).putString("stoneColor",stoneColor.getText().toString()).putString("pattern",pattern.getText().toString()).putString("bedding",bedding.getText().toString()).putString("underlying",underlying.getText().toString()).putInt("handling",handling.getSelectedItemPosition()).putInt("surface",surface.getSelectedItemPosition()).putString("base",baseInput.getText().toString()).putString("pipe",pipeInput.getText().toString()).putString("asphalt",asphaltInput.getText().toString()).putString("left",leftInput.getText().toString()).putString("right",rightInput.getText().toString()).putString("project",project.getText().toString()).putString("length",length.getText().toString()).putString("width",width.getText().toString()).putString("depth",depth.getText().toString()).commit())throw new Exception("Speichern fehlgeschlagen");
 projectHeader.setText(project.length()>0?project.getText().toString():"Feldaufmaß Pro");
 result.setText(String.format(java.util.Locale.GERMANY,"Aushub: %.2f m³\nGrundfläche: %.2f m²",l*w*d,l*w));StringBuilder details=new StringBuilder("Legende\nOberfläche: "+covering);
 if(stoneLength.length()>0||stoneWidth.length()>0)details.append("\nSteinformat: "+(stoneLength.length()>0?stoneLength.getText():"?")+" × "+(stoneWidth.length()>0?stoneWidth.getText():"?")+" cm");
 if(stoneDepth.length()>0)details.append("\nBelagsstärke: "+stoneDepth.getText()+" cm");
 if(stoneColor.length()>0)details.append("\nFarbe: "+stoneColor.getText());
 if(pattern.length()>0)details.append("\nVerlegemuster: "+pattern.getText());
 if(bedding.length()>0)details.append("\nBettung: "+bedding.getText());
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
  if(Double.isFinite(lenA)&&Double.isFinite(lenB)){
   if(Math.abs(x-y)>=(lenA+lenB)/2)return false;
  }else if(Math.abs(x-y)>=.01)return false;
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
  if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(la)||!Double.isFinite(lb)||u==null||v==null)return Double.NaN;
  double along=Math.max(0,Math.min(x+la/2,y+lb/2)-Math.max(x-la/2,y-lb/2));
  double across=Math.max(0,Math.min(u[1],v[1])-Math.max(u[0],v[0]));
  double area=along*across;return Double.isFinite(area)?area:Double.NaN;
 }
 String overlapReport(){
  StringBuilder out=new StringBuilder();int count=0;
  for(int i=0;i<objects.length();i++){
   org.json.JSONObject a=objects.optJSONObject(i);if(a==null)continue;
   for(int j=i+1;j<objects.length();j++){
    org.json.JSONObject b=objects.optJSONObject(j);if(b==null||!potentialObjectOverlap(a,b,w))continue;
    count++;
    out.append("\n").append(i+1).append(" / ").append(j+1).append(": ").append(a.optString("type")).append(" / ").append(b.optString("type")).append(" – ");
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
  String signature=overlapSignature(index);return !signature.isEmpty()&&signature.equals(o.optString("acceptedOverlap"));
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
   }else{
    String formula="length".equals(source)?RebExport.product(l):"area".equals(source)?RebExport.product(l,w):"volume".equals(source)?RebExport.product(l,w,d):"cut".equals(source)?RebExport.product(l,w+left+right):RebExport.product(qty);
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
  csvRow(out,name,"Haupttrasse","Aushub – geometrisch",0,l,l,w,d,l*w*d,"m³","Gesamtquader; keine Abzüge");
  csvRow(out,name,"Haupttrasse","Grundfläche",0,l,l,w,n,l*w,"m²","Bezugsfläche, nicht zu anderen Flächen addieren");
  if(Double.isFinite(left)&&Double.isFinite(right))csvRow(out,name,"Haupttrasse","Fläche mit Nachschnitt",0,l,l,w+left+right,n,l*(w+left+right),"m²","Alternative Bezugsfläche, nicht zusätzlich zur Grundfläche");
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
   new DxfExport(writer,route,metric).write(projectHeader.getText().toString(),objects,segments);
   writer.flush();notice("DXF gespeichert.");
  }catch(Exception e){notice("DXF konnte nicht gespeichert werden.");}
 }
 void exportPdf(){if(!calculate()){showPage(measurePage);return;}android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);i.addCategory(android.content.Intent.CATEGORY_OPENABLE);i.setType("application/pdf");i.putExtra(android.content.Intent.EXTRA_TITLE,"Feldaufmass.pdf");startActivityForResult(i,412);}
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
 void writePdf(android.net.Uri uri) {
  android.graphics.pdf.PdfDocument pdf=new android.graphics.pdf.PdfDocument();
  try(java.io.OutputStream out=getContentResolver().openOutputStream(uri,"wt")) {
   if(out==null)throw new java.io.IOException();
   Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
   android.graphics.pdf.PdfDocument.Page page=pdf.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,1).create());
   try {
    Canvas canvas=page.getCanvas();
    pen.setTextSize(19);
    canvas.drawText("Feldaufmaß Pro – Entwurf",36,40,pen);
    pen.setTextSize(13);
    canvas.drawText(projectHeader.getText().toString(),36,64,pen);
    canvas.drawText("Zeichnung",36,96,pen);
    drawReportView(canvas,new Sketch(),100,523,260);
    canvas.drawText("Regelprofil",36,390,pen);
    drawReportView(canvas,new Profile(),408,360,300);
    canvas.drawText("Vorläufige Maße – vor Abrechnung prüfen.",36,786,pen);
   } finally {pdf.finishPage(page);}
   RouteSketch route=new RouteSketch(this,routeData());
   String report=(route.vertices.size()>1?"Trassenskizze – Abschnittsmaße\n"+route.measurements()+"\n\n":"")+"Oberflächenaufbau\n"+surfaceLayers.getText()+"\n\n"+result.getText()+"\n"+legend.getText()+"\n"+
    "Nachschnitt links: "+reportMeasure(left)+" | rechts: "+reportMeasure(right)+"\n"+
    "Asphaltstärke: "+reportMeasure(asphalt)+"\nTragschicht: "+reportMeasure(base)+"\n\n"+
    "Trassenobjekte\n"+objectList.getText()+detailQuantities.getText()+"\n\n"+segmentsView.getText();
   android.text.TextPaint textPaint=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
   textPaint.setTextSize(12);
   android.text.StaticLayout layout=android.text.StaticLayout.Builder.obtain(report,0,report.length(),textPaint,523).build();
   int line=0, pageNumber=2;
   while(line<layout.getLineCount()){
    int top=layout.getLineTop(line), end=line+1;
    while(end<layout.getLineCount() && layout.getLineBottom(end)-top<=720)end++;
    page=pdf.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,pageNumber++).create());
    try {
     Canvas canvas=page.getCanvas();
     canvas.drawText("Mengen und Details – Seite "+(pageNumber-1),36,40,pen);
     canvas.translate(36,64);
     canvas.clipRect(0,0,523,layout.getLineTop(end)-top);
     canvas.translate(0,-top);
     layout.draw(canvas);
    } finally {pdf.finishPage(page);}
    line=end;
   }
   if(route.vertices.size()>1){
    page=pdf.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,pageNumber++).create());
    try{
     page.getCanvas().drawText("Trassenskizze – schematisch",36,44,pen);
     drawReportView(page.getCanvas(),route,80,523,523);
    }finally{pdf.finishPage(page);}
   }
   pageNumber=appendLongitudinalPdf(pdf,pageNumber,route);
   appendPhotoPdf(pdf,pageNumber,route,projectHeader.getText().toString());
   pdf.writeTo(out);
  } catch(Exception e) {
   Toast.makeText(this,"PDF konnte nicht gespeichert werden.",Toast.LENGTH_LONG).show();
   return;
  } finally {pdf.close();}
  Toast.makeText(this,"PDF gespeichert",Toast.LENGTH_SHORT).show();
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
  new android.app.AlertDialog.Builder(this).setTitle("Erkannten Bildtext prüfen").setView(scroll).setNegativeButton("Abbrechen",null).setPositiveButton("Maße suchen",(d,w)->{
   java.util.ArrayList<DocMeasure> matches=new java.util.ArrayList<>();
   for(DocMeasure m:findDocumentMeasures(content.getText().toString(),1))matches.add(new DocMeasure(m.field,1,m.metres,"OCR / geprüfter Bildtext: "+m.evidence));
   previewDocumentMeasures(name,"Bild / Handskizze · OCR-Text",matches);
  }).show();
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
  if(index<0)return;
  org.json.JSONArray photos=route.vertices.get(index).photos;
  boolean canUndo=route.vertices.get(index).gpsUndo!=null;int photoStart=canUndo?5:4;
  String[] labels=new String[photoStart+photos.length()];labels[0]="Foto aufnehmen";labels[1]="GPS-Punkt zuordnen";labels[2]="Punkt umbenennen";
  labels[3]="GNSS / RTK – NMEA importieren";if(canUndo)labels[4]="GPS-Verschiebung rückgängig machen";
  for(int i=0;i<photos.length();i++)labels[i+photoStart]="Foto "+(i+1)+" · Details";
  new android.app.AlertDialog.Builder(this).setTitle(route.vertices.get(index).name.isEmpty()?"Trassenpunkt "+(index+1):route.vertices.get(index).name).setItems(labels,(dialog,i)->{
   if(i==0)capturePhoto();else if(i==1)assignGps(route.vertices.get(index).id);else if(i==2)renamePoint(route.vertices.get(index).id,null);else if(i==3)importNmeaPoint(route.vertices.get(index).id);else if(canUndo&&i==4)undoGps(route.vertices.get(index).id);else {
    showPhotoDetails(route.vertices.get(index).id,photos.optString(i-photoStart));
   }
  }).show();
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
  super.onSaveInstanceState(out);out.putString("selectedPoint",selectedPoint);
  out.putString("pendingPoint",pendingPoint);out.putString("pendingPhoto",pendingPhoto);
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
 if(request==412&&result==RESULT_OK&&data!=null&&data.getData()!=null){writePdf(data.getData());return;}
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
 if(Double.isFinite(a)&&Double.isFinite(b))item.put("cutWidth",gw+a+b);
 segments.put(item);persistSegments();
 }catch(Exception e){Toast.makeText(this,"Bitte gültige Stationsgrenzen und Breite eingeben.",Toast.LENGTH_LONG).show();}}
 void persistSegments(){getPreferences(0).edit().putString("segments",segments.toString()).apply();showSegments();refreshStationLayers();}
 void showSegments(){StringBuilder out=new StringBuilder();java.util.TreeMap<String,Double> totals=new java.util.TreeMap<>();
 for(int i=0;i<segments.length();i++){org.json.JSONObject o=segments.optJSONObject(i);if(o==null)continue;double a=o.optDouble("from"),b=o.optDouble("to"),area=(b-a)*o.optDouble("width");String type=o.optString("surface");
 out.append(String.format(java.util.Locale.GERMANY,"RP %02d | %.2f–%.2f m | %s | %.2f m² (Graben)\n",i+1,a,b,type,area));
 if(o.has("cutWidth"))out.append(String.format(java.util.Locale.GERMANY,"Mit Nachschnitt: %.2f m²\n",(b-a)*o.optDouble("cutWidth")));
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
   EditText diameter=new EditText(this);diameter.setHint("Außendurchmesser (mm)");diameter.setInputType(8194);form.addView(diameter);
   android.app.AlertDialog add=new android.app.AlertDialog.Builder(this).setTitle("Leitung hinzufügen").setView(form).setNegativeButton("Abbrechen",null).setPositiveButton("Hinzufügen",null).create();
   add.setOnShowListener(x->add.getButton(-1).setOnClickListener(y->{try{double mm=Double.parseDouble(diameter.getText().toString().replace(',','.'));if(!Double.isFinite(mm)||mm<=0)throw new Exception();org.json.JSONObject o=new org.json.JSONObject();o.put("type",type.getSelectedItem().toString());o.put("diameter",mm);rows.put(o);if(saveUtilities(rows))add.dismiss();}catch(Exception e){diameter.setError("Positiven Durchmesser eingeben");}}));add.show();
  }).setNegativeButton("Schließen",null).show();
 }
 void utilityMenu(int index){
  org.json.JSONArray rows=extraUtilities();org.json.JSONObject o=rows.optJSONObject(index);if(o==null)return;
  new android.app.AlertDialog.Builder(this).setTitle(o.optString("name",o.optString("type"))).setItems(new String[]{"Eigenschaften bearbeiten","Leitung entfernen"},(dialog,choice)->{
   if(choice==1){new android.app.AlertDialog.Builder(this).setMessage("Leitung entfernen?").setNegativeButton("Abbrechen",null).setPositiveButton("Entfernen",(x,y)->{rows.remove(index);saveUtilities(rows);}).show();return;}
   LinearLayout form=new LinearLayout(this);form.setOrientation(1);
   String[] keys={"name","type","material","dn","diameter"},titles={"Bezeichnung","Leitungsart","Material","DN / Kabeltyp","Außendurchmesser (mm)"};
   EditText[] fields=new EditText[keys.length];for(int j=0;j<keys.length;j++){fields[j]=new EditText(this);fields[j].setHint(titles[j]);fields[j].setText(o.optString(keys[j],""));if(j==4)fields[j].setInputType(8194);form.addView(fields[j]);}
   android.app.AlertDialog edit=new android.app.AlertDialog.Builder(this).setTitle("Leitung bearbeiten").setView(form).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();
   edit.setOnShowListener(x->edit.getButton(-1).setOnClickListener(y->{try{double mm=Double.parseDouble(fields[4].getText().toString().replace(',','.'));if(!Double.isFinite(mm)||mm<=0)throw new Exception();if(fields[1].length()==0){fields[1].setError("Leitungsart eingeben");return;}for(int j=0;j<4;j++)o.put(keys[j],fields[j].getText().toString().trim());o.put("diameter",mm);if(saveUtilities(rows))edit.dismiss();}catch(Exception e){fields[4].setError("Positiven Durchmesser eingeben");}}));edit.show();
  }).show();
 }
 boolean saveUtilities(org.json.JSONArray rows){
  boolean ok=getPreferences(0).edit().putString("extraUtilities",rows.toString()).commit();
  if(ok){if(profile!=null)profile.invalidate();}else notice("Leitungen konnten nicht gespeichert werden.");return ok;
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
 void label(Canvas c,String s,float x,float y){p.setColor(Color.BLACK);p.setTextSize(12);c.drawText(s,x,y,p);}
 protected void onDraw(Canvas c){super.onDraw(c);utilityHits.clear();if(w<=0||d<=0)return;
 c.save();c.scale(getWidth()/360f,getHeight()/300f);
 double nl=Double.isFinite(left)?left:0,nr=Double.isFinite(right)?right:0;
 float scale=(float)Math.min(190/(w+nl+nr),155/d);
 float x=180-(float)(w*scale/2),z=180+(float)(w*scale/2),top=55,bottom=top+(float)(d*scale);
 profileLeft=x;profileTop=top;profileScale=scale;
 p.setColor(0xffb7a48a);c.drawRect(20,top,340,bottom,p);
 p.setColor(0xffeee2cc);c.drawRect(x,top,z,bottom,p);
 float layerTop=top;
 if(Double.isFinite(topThickness)){
 RouteSketch style=new RouteSketch(MainActivity.this,"[]");style.bindDimensionProject(activeProject);
 org.json.JSONObject settings=style.legendSettings();
 p.setColor(style.configuredSurfaceColor(covering,settings));layerTop=top+(float)(topThickness*scale);c.drawRect(20,top,340,layerTop,p);
 int hatch=style.configuredHatch(covering,settings);
 if(hatch>=0&&layerTop>top){c.save();c.clipRect(20,top,340,layerTop);float band=12*getResources().getDisplayMetrics().density;for(float yy=top+band/2;yy<layerTop+band/2;yy+=band){c.save();c.translate(20,yy);style.drawHatchBand(c,320,band/12,hatch);c.restore();}c.restore();}
 }
 if(Double.isFinite(base)){p.setColor(0xffa6a6a6);c.drawRect(20,layerTop,340,layerTop+(float)(base*scale),p);}
 if(!covering.equals("Asphalt")&&!covering.equals("Unbekannt")&&!covering.equals("Unbefestigt")&&!covering.equals("Sonstiges")){p.setColor(0xff777777);c.drawLine(20,top,340,top,p);for(int i=20;i<340;i+=covering.contains("platten")||covering.contains("Platten")?28:12)c.drawLine(i,top-4,i,top,p);}
 org.json.JSONArray utilities=dragRows==null?extraUtilities():dragRows;
 if(utilities.length()==0){if(Double.isFinite(pipe)){float radius=(float)(pipe/2000*scale);p.setColor(0xff3686bc);c.drawCircle(180,bottom-radius,radius,p);}}
 else{
  double sum=Double.isFinite(pipe)?pipe/1000:0;for(int i=0;i<utilities.length();i++)sum+=utilities.optJSONObject(i).optDouble("diameter",0)/1000;
  int count=utilities.length()+(Double.isFinite(pipe)?1:0);
  float gap=(float)((w-sum)*scale/(count+1)),cursor=x+gap;
  if(gap<0){gap=0;cursor=x;label(c,"Leitungsanordnung prüfen",20,224);}
  {
   if(Double.isFinite(pipe)){float radius=(float)(pipe/2000*scale);p.setColor(0xff3686bc);c.drawCircle(cursor+radius,bottom-radius,radius,p);cursor+=2*radius+gap;}
   for(int i=0;i<utilities.length();i++){org.json.JSONObject o=utilities.optJSONObject(i);float radius=(float)(o.optDouble("diameter",0)/2000*scale);String type=o.optString("type");p.setColor(type.equals("Gas")?0xffe9b63c:type.equals("Trinkwasser")?0xff3686bc:0xffc66d56);if(radius*2<=bottom-top&&radius*2<=z-x){float cx=(float)(x+o.optDouble("offset",(cursor+radius-x)/scale)*scale),cy=(float)(top+o.optDouble("axisDepth",(bottom-radius-top)/scale)*scale);cx=Math.max(x+radius,Math.min(z-radius,cx));cy=Math.max(top+radius,Math.min(bottom-radius,cy));c.drawCircle(cx,cy,radius,p);utilityHits.add(new android.graphics.RectF(cx-radius,cy-radius,cx+radius,cy+radius));}else{utilityHits.add(null);label(c,"Leitungstiefe prüfen",20,224);}cursor+=2*radius+gap;}
  }
 }

 p.setColor(Color.BLACK);p.setStrokeWidth(1);c.drawLine(x,top,x,bottom,p);c.drawLine(z,top,z,bottom,p);c.drawLine(x,bottom,z,bottom,p);
 c.drawLine(x,bottom+14,z,bottom+14,p);c.drawLine(x,bottom+8,x,bottom+20,p);c.drawLine(z,bottom+8,z,bottom+20,p);
 label(c,m(w),150,bottom+29);
 c.drawLine(z+12,top,z+12,bottom,p);label(c,m(d),z+16,(top+bottom)/2);
 if(Double.isFinite(left)&&Double.isFinite(right)){float a=x-(float)(nl*scale),b=z+(float)(nr*scale);c.drawLine(a,35,b,35,p);c.drawLine(a,30,a,45,p);c.drawLine(b,30,b,45,p);label(c,m(w+nl+nr),145,27);}
 else label(c,"Nachschnitt: nicht vollständig erfasst",20,27);
 label(c,"Oberfläche: "+covering,20,238);
 label(c,Double.isFinite(topThickness)?"Belagsstärke: "+m(topThickness):"Belagsstärke: nicht erfasst",20,252);
 label(c,utilities.length()>0?"Leitungen: "+(utilities.length()+(Double.isFinite(pipe)?1:0))+" (Lage symbolisch)":Double.isFinite(pipe)?"Rohr Ø "+pipe+" mm (Lage symbolisch)":"Rohrmaße: unbekannt",20,266);
 label(c,"Schematisch – keine Ausführungsplanung",20,282);c.restore();
 }}
 class Sketch extends View{Paint p=new Paint(3);Sketch(){super(MainActivity.this);setClickable(true);}
 @Override public boolean onTouchEvent(android.view.MotionEvent e){if(e.getAction()==android.view.MotionEvent.ACTION_DOWN)return true;if(e.getAction()==android.view.MotionEvent.ACTION_UP){performClick();if(l<=0)return true;float a=35,b=getWidth()-35;java.util.ArrayList<Integer> hits=new java.util.ArrayList<>();for(int i=0;i<objects.length();i++){org.json.JSONObject o=objects.optJSONObject(i);if(o==null)continue;double st=o.optDouble("station");if(st<0||st>l)continue;float x=a+(b-a)*(float)(st/l);if(Math.abs(e.getX()-x)<dp(22)&&Math.abs(e.getY()-95)<dp(24))hits.add(i);}if(hits.size()==1)editObject(hits.get(0));else if(hits.size()>1){String[] names=new String[hits.size()];for(int j=0;j<names.length;j++){org.json.JSONObject o=objects.optJSONObject(hits.get(j));names[j]=o.optString("type")+" – "+o.optString("station")+" m";}new android.app.AlertDialog.Builder(MainActivity.this).setTitle("Objekt auswählen").setItems(names,(v,j)->editObject(hits.get(j))).show();}else if(e.getY()>=65&&e.getY()<=125)showPage(measurePage);return true;}return super.onTouchEvent(e);}
 @Override public boolean performClick(){super.performClick();return true;}
 protected void onDraw(Canvas c){super.onDraw(c);float a=35,b=getWidth()-35;p.setColor(covering.equals("Asphalt")?0xff656970:covering.equals("Unbefestigt")?0xffa98d6c:0xffd9c4a5);c.drawRect(a,65,b,125,p);
 if(!covering.equals("Asphalt")&&!covering.equals("Unbefestigt")&&!covering.equals("Unbekannt")&&!covering.equals("Sonstiges")){
 p.setColor(0xff81796b);p.setStrokeWidth(1);int step=covering.toLowerCase(java.util.Locale.ROOT).contains("platten")?32:16;
 c.save();c.clipRect(a,65,b,125);
 for(int row=0;row<3;row++){float y=65+row*20;c.drawLine(a,y,b,y,p);for(float x=a-(row%2)*step/2;x<b;x+=step)c.drawLine(x,y,x,y+20,p);}
 c.restore();}
p.setColor(Color.BLACK);p.setStrokeWidth(2);c.drawLine(a,95,b,95,p);if(l>0){for(int i=0;i<objects.length();i++){org.json.JSONObject o=objects.optJSONObject(i);if(o==null)continue;double st=o.optDouble("station");if(st<0||st>l)continue;float x=a+(b-a)*(float)(st/l);p.setColor(objectWarning(i)?0xffc62828:0xff246eaa);double dl=detailNumber(o,"detailLength"),dw=detailNumber(o,"detailWidth");if(Double.isFinite(dl)&&dl>0&&Double.isFinite(dw)&&dw>0){float half=Math.max(4,(float)(dl/l*(b-a)/2));float hh=Math.min(60,Math.max(5,(float)(dw*15)));c.drawRect(x-half,95-hh,x+half,95+hh,p);}else c.drawCircle(x,95,8,p);p.setColor(Color.BLACK);}}c.drawLine(a,160,b,160,p);c.drawLine(a,150,a,170,p);c.drawLine(b,150,b,170,p);p.setTextSize(22);c.drawText("0,00 m",a,45,p);c.drawText(String.format(java.util.Locale.GERMANY,"%.2f m",l),Math.max(a,b-110),45,p);c.drawText("Breite: "+w+" m",a,200,p);c.drawText("Schematisch – nicht maßstäblich",a,245,p);}
 }
}
