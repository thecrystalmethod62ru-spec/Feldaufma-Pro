package de.feldaufmass.pro;
import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import android.view.*;
import android.graphics.*;
import android.content.SharedPreferences;

public class MainActivity extends Activity {
 EditText project,length,width,depth; TextView result; Sketch sketch;
 EditText objectStation,objectNote; Spinner objectType,objectSide; TextView objectList; org.json.JSONArray objects=new org.json.JSONArray(); EditText voiceText; Spinner voiceLanguage;
 double l,w,d,asphalt,left,right; EditText asphaltInput,leftInput,rightInput; Profile profile; Spinner surface; EditText stoneLength,stoneWidth,stoneDepth,stoneColor,pattern,bedding,underlying; Spinner handling; TextView legend; EditText stationFrom,stationTo; TextView segmentsView; org.json.JSONArray segments=new org.json.JSONArray(); EditText baseInput,pipeInput; double base=Double.NaN,pipe=Double.NaN; String covering="Unbekannt";
 EditText field(LinearLayout root,String label,String key,boolean numeric){
 TextView t=new TextView(this);t.setText(label);root.addView(t);
 EditText e=new EditText(this);e.setSingleLine(true);
 if(numeric)e.setInputType(8194);
 e.setText(getPreferences(0).getString(key,""));root.addView(e);return e;
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
 @Override protected void onResume(){super.onResume();startLiveGps();}
 @Override protected void onPause(){
  if(liveManager!=null){liveManager.removeUpdates(liveListener);liveManager=null;}
  liveFix=null;if(livePreview!=null)livePreview.setLiveFix(null);super.onPause();
 }
 void refreshRoute(){
  routePreviewBox.removeAllViews();
  RouteSketch preview=new RouteSketch(this,routeData());
  livePreview=preview;preview.setLiveFix(liveFix);
  preview.pointSelected=i->{selectedPoint=preview.vertices.get(i).id;refreshRoute();pointMenu();};
  preview.selected=i->measureSegment(preview,i);
  preview.changed=()->{saveRoute(preview);refreshRoute();};
  preview.highlight=findPoint(preview,selectedPoint);
  if(!preview.vertices.isEmpty() && !routeData().equals(preview.encode()))saveRoute(preview);
  routePreviewBox.addView(preview,new LinearLayout.LayoutParams(-1,dp(300)));
  double total=preview.measuredLength();
  if(preview.vertices.size()<2){routeSummary.setText("Trasse mit dem Finger zeichnen");return;}
  routeSummary.setText((preview.vertices.size()-1)+" Abschnitte · "+
    (Double.isFinite(total)?"Bemaßte Länge: "+reportMeasure(total):"Bemaßung unvollständig"));
 }

 void measureSegment(RouteSketch route,int i){
  EditText metres=new EditText(this);
  metres.setSingleLine(true);metres.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,."));
  metres.setHint("Länge in m");
  double old=route.vertices.get(i).metres;
  if(Double.isFinite(old))metres.setText(String.valueOf(old).replace('.',','));
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
    .setTitle("Abschnitt "+i+" bemaßen").setView(metres)
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

 void editRoute(boolean measure){
  LinearLayout form=page();
  ScrollView scroll=new ScrollView(this);scroll.addView(form);
  RouteSketch route=new RouteSketch(this,routeData());route.editable=true;
  TextView hint=sheetText("Trasse mit dem Finger zeichnen. Weitere Striche verlängern das Ende.",15,false);
  form.addView(hint);
  form.addView(route,new LinearLayout.LayoutParams(-1,dp(260)));
  Spinner mode=choices(form,new String[]{"Zeichnen","Maß eintragen","Punkte verschieben"});
  Spinner angles=choices(form,new String[]{"Freier Winkel","0°","15°","30°","45°","60°","75°","90°"});
  angles.setVisibility(View.GONE);
  onChoice(angles,index->route.angle=index==0?-1:(index-1)*15);
  onChoice(mode,index->{
   route.dimensioning=index==1;route.moving=index==2;
   angles.setVisibility(route.moving?View.VISIBLE:View.GONE);
   hint.setText(new String[]{"Mit einem Finger zeichnen, mit zwei Fingern zoomen oder verschieben.","Abschnitt zum Bemaßen antippen.","Punkt ziehen. Der Winkel gilt zur Waagerechten am benachbarten Punkt."}[index]);
  });
  mode.setSelection(measure?1:0);
  Button remove=new Button(this);remove.setText("Letzten Abschnitt entfernen");form.addView(remove);
  LinearLayout list=page();form.addView(list);
  route.selected=i->measureSegment(route,i);
  route.changed=()->{
   remove.setEnabled(route.vertices.size()>1);
   list.removeAllViews();
   String[] lines=route.measurements().split("\n");
   for(int i=1;i<route.vertices.size();i++){
    int index=i;
    Button measureButton=new Button(this);measureButton.setAllCaps(false);measureButton.setText(lines[i-1]);
    measureButton.setOnClickListener(v->measureSegment(route,index));list.addView(measureButton);
   }
  };
  remove.setOnClickListener(v->{
   route.vertices.remove(route.vertices.size()-1);
   if(route.vertices.size()==1)route.vertices.clear();
   route.changed.run();route.invalidate();
  });
  route.changed.run();
  android.app.AlertDialog editor=new android.app.AlertDialog.Builder(this).setTitle("Trasse skizzieren").setView(scroll)
   .setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();
  editor.setOnShowListener(ignored->editor.getButton(-1).setOnClickListener(v->{
   if(saveRoute(route)){refreshRoute();editor.dismiss();notice("Trasse gespeichert.");}
  }));
  editor.show();
 }

 boolean saveRoute(RouteSketch route){try{if(getPreferences(0).edit().putString("routeSketch",route.encode()).commit())return true;}catch(RuntimeException ignored){}notice("Trasse konnte nicht gespeichert werden. Bitte erneut versuchen.");return false;}
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

 void showTools(){
  String[] names={"Maße","Beläge","Objekte","Unterlagen / PDF","Sprachassistent"};
  LinearLayout[] pages={measurePage,segmentPage,objectPage,documentsPage,voicePage};
  PopupMenu menu=new PopupMenu(this,toolsMenu);
  for(int i=0;i<names.length;i++)menu.getMenu().add(0,i,Menu.NONE,names[i]);
  menu.setOnMenuItemClickListener(item->{showPage(pages[item.getItemId()]);return true;});
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

 public void onCreate(Bundle b){super.onCreate(b);
 if(b!=null){selectedPoint=b.getString("selectedPoint");pendingPoint=b.getString("pendingPoint");pendingPhoto=b.getString("pendingPhoto");}
 shell=page();shell.setPadding(0,0,0,0);shell.setBackgroundColor(0xfff4f6f8);setContentView(shell);fitSystemPanels(shell);
 planPage=page();measurePage=page();voicePage=page();profilePage=page();segmentPage=page();objectPage=page();quantityPage=page();
 LinearLayout header=new LinearLayout(this);
 header.setGravity(Gravity.CENTER_VERTICAL);
 header.setPadding(dp(16),dp(4),dp(8),dp(4));
 header.setBackgroundColor(Color.WHITE);
 shell.addView(header);
 projectHeader=sheetText("Feldaufmaß Pro",22,true);
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
 routePreviewBox=page();planPage.addView(routePreviewBox);
 routeSummary=sheetText("",15,false);planPage.addView(routeSummary);
 LinearLayout actions=new LinearLayout(this);planPage.addView(actions);
 actionButton(actions,"Maß eintragen",()->editRoute(true));actionButton(actions,"Trasse zeichnen",()->editRoute(false));
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
 surface=new Spinner(this);String[] types={"Unbekannt","Asphalt","Pflaster","Gehwegplatten","Unbefestigt","Betonpflaster – Rechteck","Betonpflaster – Quadrat","Doppel-T-Pflaster","Wellenverbundpflaster","Sechseckpflaster","Achteckpflaster","Verbundpflaster – sonstige","Betonpflaster – Mehrformat","Ökopflaster / Sickerpflaster","Rasengittersteine","Rasenfugenpflaster","Natursteinpflaster – Großpflaster","Natursteinpflaster – Kleinpflaster","Natursteinpflaster – Mosaikpflaster","Natursteinpflaster – sonstige","Klinkerpflaster","Ziegelpflaster","Beton-Gehwegplatten","Natursteinplatten","Waschbetonplatten","Taktilplatten – Rippen","Taktilplatten – Noppen","Rinnenpflaster","Schlackenpflaster","Holzpflaster","Sonstiges"};
 surface.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,types));root.addView(surface);
 surface.setSelection(Math.min(types.length-1,Math.max(0,getPreferences(0).getInt("surface",0))));
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
 getPreferences(0).edit().putString("stoneLength",stoneLength.getText().toString()).putString("stoneWidth",stoneWidth.getText().toString()).putString("stoneDepth",stoneDepth.getText().toString()).putString("stoneColor",stoneColor.getText().toString()).putString("pattern",pattern.getText().toString()).putString("bedding",bedding.getText().toString()).putString("underlying",underlying.getText().toString()).putInt("handling",handling.getSelectedItemPosition()).putInt("surface",surface.getSelectedItemPosition()).putString("base",baseInput.getText().toString()).putString("pipe",pipeInput.getText().toString()).putString("asphalt",asphaltInput.getText().toString()).putString("left",leftInput.getText().toString()).putString("right",rightInput.getText().toString()).putString("project",project.getText().toString()).putString("length",length.getText().toString()).putString("width",width.getText().toString()).putString("depth",depth.getText().toString()).apply();
 projectHeader.setText(project.length()>0?project.getText().toString():"Feldaufmaß Pro");
 result.setText(String.format(java.util.Locale.GERMANY,"Aushub: %.2f m³\nGrundfläche: %.2f m²",l*w*d,l*w));StringBuilder details=new StringBuilder("Legende\nOberfläche: "+covering);
 if(stoneLength.length()>0||stoneWidth.length()>0)details.append("\nSteinformat: "+(stoneLength.length()>0?stoneLength.getText():"?")+" × "+(stoneWidth.length()>0?stoneWidth.getText():"?")+" cm");
 if(stoneDepth.length()>0)details.append("\nBelagsstärke: "+stoneDepth.getText()+" cm");
 if(stoneColor.length()>0)details.append("\nFarbe: "+stoneColor.getText());
 if(pattern.length()>0)details.append("\nVerlegemuster: "+pattern.getText());
 if(bedding.length()>0)details.append("\nBettung: "+bedding.getText());
 if(underlying.length()>0)details.append("\nAltbelag darunter: "+underlying.getText());
 if(handling.getSelectedItemPosition()>0)details.append("\n"+handling.getSelectedItem());
 compareSoll();showObjects();refreshDetailQuantities();legend.setText(details.toString());showSurfaceLayers();sketch.invalidate();profile.invalidate();return true;
 }catch(Exception e){Toast.makeText(this,"Maße prüfen. Stand wurde nicht gespeichert.",Toast.LENGTH_SHORT).show();return false;}}
 void detailObject(int index){org.json.JSONObject o=objects.optJSONObject(index);if(o==null)return;LinearLayout form=page();Spinner mode=new Spinner(this);mode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Draufsicht","Querschnitt","Längsschnitt"}));form.addView(mode);EditText a=new EditText(this),b=new EditText(this),h=new EditText(this);EditText[] es={a,b,h};String[] keys={"detailLength","detailWidth","detailDepth"},labels={"Länge (m)","Breite (m)","Tiefe (m)"};for(int j=0;j<3;j++){es[j].setInputType(8194);es[j].setHint(labels[j]);es[j].setText(o.optString(keys[j],""));form.addView(es[j]);}View preview=new View(this){Paint p=new Paint(3);protected void onDraw(Canvas c){float x=getWidth()/2f,y=getHeight()/2f; p.setColor(0xffdce9f4);c.drawRect(x-85,y-45,x+85,y+45,p);p.setColor(0xff203849);p.setStyle(Paint.Style.STROKE);c.drawRect(x-85,y-45,x+85,y+45,p);p.setStyle(Paint.Style.FILL);p.setTextSize(18);c.drawText(mode.getSelectedItem().toString()+" – schematisch",12,24,p);c.drawText((mode.getSelectedItemPosition()==1?b:a).getText()+" m",x-35,y-55,p);c.drawText((mode.getSelectedItemPosition()==0?b:h).getText()+" m",x+90,y,p);}};form.addView(preview,new LinearLayout.LayoutParams(-1,dp(180)));for(EditText e:es)e.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){preview.invalidate();}public void afterTextChanged(android.text.Editable e){}});mode.setSelection(Math.max(0,Math.min(2,o.optInt("detailMode",0))));mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){preview.invalidate();}public void onNothingSelected(android.widget.AdapterView<?> p){}});android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Detailzeichnung – "+o.optString("type")+" / "+o.optString("station")+" m").setView(form).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(btn->{try{for(EditText e:es){if(e.length()>0){double n=number(e);if(!Double.isFinite(n)||n<=0){e.setError("Positives Maß erforderlich");return;}}}for(int j=0;j<3;j++)o.put(keys[j],es[j].getText().toString());o.put("detailMode",mode.getSelectedItemPosition());saveObjects();dialog.dismiss();}catch(Exception e){Toast.makeText(this,"Detail konnte nicht gespeichert werden",Toast.LENGTH_LONG).show();}}));dialog.show();}
 void editObject(int index){org.json.JSONObject o=objects.optJSONObject(index);if(o==null)return;LinearLayout form=page();EditText st=new EditText(this);st.setInputType(8194);st.setHint("Station (m)");st.setText(o.optString("station"));form.addView(st);EditText note=new EditText(this);note.setHint("Bemerkung");note.setText(o.optString("note"));form.addView(note);android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle(o.optString("type")+" bearbeiten").setView(form).setNeutralButton("Detailzeichnung",(v,j)->detailObject(index)).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{try{double x=number(st),total=l;if(!Double.isFinite(x)||!Double.isFinite(total)||x<0||x>total)throw new Exception();o.put("station",x);o.put("note",note.getText().toString());saveObjects();dialog.dismiss();}catch(Exception e){st.setError("Station außerhalb der Trasse");}}));dialog.show();}
 double detailNumber(org.json.JSONObject o,String key){try{return Double.parseDouble(o.optString(key).replace(',','.'));}catch(Exception e){return Double.NaN;}}
 double detailExtent(org.json.JSONObject o){try{double v=Double.parseDouble(o.optString("detailLength","").replace(',','.'));return Double.isFinite(v)&&v>0?v:0;}catch(Exception e){return 0;}}
 boolean objectWarning(int index){org.json.JSONObject o=objects.optJSONObject(index);if(o==null)return false;double x=o.optDouble("station"),a=detailExtent(o)/2;if(!Double.isFinite(x)||x-a<0||x+a>l)return true;for(int j=0;j<objects.length();j++){org.json.JSONObject q=objects.optJSONObject(j);if(j==index||q==null)continue;double y=q.optDouble("station"),b=detailExtent(q)/2;if(!Double.isFinite(y))continue;if(o.optString("side").equals(q.optString("side"))&&(Math.abs(x-y)<0.01||Math.abs(x-y)<a+b))return true;}return false;}
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
  refreshDetailQuantities();
  sketch.invalidate();
  updateHistoryButtons();
 }
 void saveObjects(){String old=getPreferences(0).getString("objects","[]");if(!old.equals(objects.toString())){undoObjects.push(old);while(undoObjects.size()>HISTORY_LIMIT)undoObjects.removeLast();redoObjects.clear();}persistObjects();}
 void restoreObjectHistory(boolean redo){java.util.ArrayDeque<String> from=redo?redoObjects:undoObjects,to=redo?undoObjects:redoObjects;if(from.isEmpty())return;try{org.json.JSONArray restored=new org.json.JSONArray(from.peek());to.push(objects.toString());from.pop();objects=restored;persistObjects();}catch(org.json.JSONException e){Toast.makeText(this,"Änderung konnte nicht wiederhergestellt werden",Toast.LENGTH_SHORT).show();}}

 void refreshDetailQuantities(){if(detailQuantities==null)return;StringBuilder b=new StringBuilder("\nDetailmengen – geometrisch, nicht addiert\n");for(int i=0;i<objects.length();i++){org.json.JSONObject o=objects.optJSONObject(i);if(o==null)continue;double a=detailNumber(o,"detailLength"),w=detailNumber(o,"detailWidth"),h=detailNumber(o,"detailDepth");b.append(o.optString("type")).append(" / ").append(o.optString("station")).append(" m\n");if(Double.isFinite(a*w)&&a>0&&w>0){b.append(String.format(java.util.Locale.GERMANY,"Fläche: %.2f m²",a*w));if(Double.isFinite(a*w*h)&&h>0)b.append(String.format(java.util.Locale.GERMANY," | Körpervolumen: %.2f m³",a*w*h));else b.append(" | Tiefe fehlt");}else b.append("Detailmaße fehlen");if(objectWarning(i))b.append(" | ⚠ Lage prüfen");b.append("\n");}b.append("\nNicht zur Haupttrasse addieren: Überschneidungen und LV-Regeln prüfen.");detailQuantities.setText(b.toString());}
 String detailSummary(org.json.JSONObject o){StringBuilder b=new StringBuilder();String[] keys={"detailLength","detailWidth","detailDepth"},names={"L","B","T"};for(int j=0;j<3;j++){String v=o.optString(keys[j],"");if(!v.isEmpty())b.append(" | ").append(names[j]).append("=").append(v).append(" m");}return b.toString();}
 void showObjects(){refreshOverview();StringBuilder b=new StringBuilder();for(int i=0;i<objects.length();i++){org.json.JSONObject o=objects.optJSONObject(i);if(o!=null)b.append(String.format(java.util.Locale.GERMANY,"%.2f m | %s | %s | %s\n",o.optDouble("station"),o.optString("type"),o.optString("side"),o.optString("note")+detailSummary(o)+(objectWarning(i)?" | ⚠ Station prüfen":"")));}objectList.setText(b.toString());}
 void saveSoll(){try{EditText[] fields={sollLength,sollWidth,sollDepth,sollAsphalt};String[] keys={"sollLength","sollWidth","sollDepth","sollAsphalt"};for(EditText e:fields){if(e.length()>0){double n=number(e);if(!Double.isFinite(n)||n<=0)throw new Exception();}}android.content.SharedPreferences.Editor ed=getPreferences(0).edit();for(int i=0;i<fields.length;i++)ed.putString(keys[i],fields[i].getText().toString());ed.putString("sollSource",sollSource.getText().toString()).apply();compareSoll();}catch(Exception e){Toast.makeText(this,"Bitte gültige positive Soll-Maße eingeben.",Toast.LENGTH_LONG).show();}}
 void compareSoll(){if(sollComparison==null||length==null||asphaltInput==null)return;EditText[] actual={length,width,depth,asphaltInput};String[] keys={"sollLength","sollWidth","sollDepth","sollAsphalt"};String[] names={"Trassenlänge","Grabenbreite","Grabentiefe","Asphaltstärke"};StringBuilder b=new StringBuilder("Soll / Ist – Abweichungen (Ist minus Soll)\n");for(int i=0;i<keys.length;i++){b.append(names[i]).append(": ");try{double p=Double.parseDouble(getPreferences(0).getString(keys[i],"").replace(',','.'));double a=number(actual[i]);if(!Double.isFinite(a)||a<=0)throw new Exception();b.append(String.format(java.util.Locale.GERMANY,"Soll %.2f m | Ist %.2f m | Δ %+.2f m",p,a,a-p));}catch(Exception e){b.append("Nicht vollständig erfasst");}b.append("\n");}sollComparison.setText(b.toString());}
 void exportPdf(){if(!calculate()){showPage(measurePage);return;}android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);i.addCategory(android.content.Intent.CATEGORY_OPENABLE);i.setType("application/pdf");i.putExtra(android.content.Intent.EXTRA_TITLE,"Feldaufmass.pdf");startActivityForResult(i,412);}
 void drawReportView(Canvas canvas, View view, int top, int width, int height) {
  canvas.save();
  canvas.translate(36,top);
  view.layout(0,0,width,height);
  view.draw(canvas);
  canvas.restore();
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
    page=pdf.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,pageNumber).create());
    try{
     page.getCanvas().drawText("Trassenskizze – schematisch",36,44,pen);
     drawReportView(page.getCanvas(),route,80,523,523);
    }finally{pdf.finishPage(page);}
   }
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
 void pointMenu(){
  RouteSketch route=new RouteSketch(this,routeData());int index=findPoint(route,selectedPoint);
  if(index<0)return;
  org.json.JSONArray photos=route.vertices.get(index).photos;
  boolean canUndo=route.vertices.get(index).gpsUndo!=null;int photoStart=canUndo?4:3;
  String[] labels=new String[photoStart+photos.length()];labels[0]="Foto aufnehmen";labels[1]="GPS-Punkt zuordnen";labels[2]="Punkt umbenennen";
  if(canUndo)labels[3]="GPS-Verschiebung rückgängig machen";
  for(int i=0;i<photos.length();i++)labels[i+photoStart]="Foto "+(i+1)+" öffnen";
  new android.app.AlertDialog.Builder(this).setTitle(route.vertices.get(index).name.isEmpty()?"Trassenpunkt "+(index+1):route.vertices.get(index).name).setItems(labels,(dialog,i)->{
   if(i==0)capturePhoto();else if(i==1)assignGps(route.vertices.get(index).id);else if(i==2)renamePoint(route.vertices.get(index).id,null);else if(canUndo&&i==3)undoGps(route.vertices.get(index).id);else {
    android.content.Intent view=new android.content.Intent(android.content.Intent.ACTION_VIEW);
    view.setDataAndType(photoUri(photos.optString(i-photoStart)),"image/jpeg");
    view.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
    try{startActivity(view);}catch(android.content.ActivityNotFoundException e){notice("Keine Bildanzeige verfügbar.");}
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
   route.vertices.get(index).photos.put(pendingPhoto);saveRoute(route);refreshRoute();notice("Foto an Trassenpunkt "+(index+1)+" angehängt.");
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
 void handleSpeech(String text){
  int version=++speechVersion;
  String normalized=text.toLowerCase(java.util.Locale.ROOT);
  if(normalized.matches(".*(?:сделай|сделать|сними|прикрепи|прицепи|mach|nimm|nehme|aufnehmen|erstelle|hänge|füge).*?(?:фото|снимок|foto).*") ||
     normalized.matches(".*(?:сфотографируй|fotografier|foto aufnehmen|foto machen|foto erstellen|foto anhängen|foto hinzufügen|foto aufnehmen).*")){
   if(selectVoicePoint(text))capturePhoto();return;
  }
  if(offerVoiceObject(text)||offerVoiceSegment(text)||offerVoicePointAction(text))return;
  showPage(voicePage);
  if(voiceLanguage.getSelectedItemPosition()==0){voiceText.setText(text);offerVoiceRename(text);return;}
  if(translator==null)translator=com.google.mlkit.nl.translate.Translation.getClient(
   new com.google.mlkit.nl.translate.TranslatorOptions.Builder().setSourceLanguage("ru").setTargetLanguage("de").build());
  voiceText.setText("");notice("Diktat wird ins Deutsche übersetzt …");
  translator.downloadModelIfNeeded().onSuccessTask(ignored->translator.translate(text))
   .addOnSuccessListener(this,german->{if(version==speechVersion){voiceText.setText(german);if(!offerVoiceObject(german)&&!offerVoiceSegment(german))offerVoiceRename(german);}})
   .addOnFailureListener(this,error->{if(version==speechVersion)notice("Übersetzung fehlgeschlagen. Für den ersten Modellabruf wird Internet benötigt.");});
 }
 @Override protected void onDestroy(){if(translator!=null)translator.close();super.onDestroy();}
 void startDictation(){
 android.content.Intent i=new android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
 i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
 i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE,voiceLanguage.getSelectedItemPosition()==0?"de-DE":"ru-RU");
 i.putExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
 try{startActivityForResult(i,410);}catch(android.content.ActivityNotFoundException e){Toast.makeText(this,"Keine Spracherkennung verfügbar.",Toast.LENGTH_LONG).show();}
 }
 @Override protected void onActivityResult(int request,int result,android.content.Intent data){super.onActivityResult(request,result,data);
 if(request==413){finishPhoto(result==RESULT_OK);return;}
 if(request==412&&result==RESULT_OK&&data!=null&&data.getData()!=null){writePdf(data.getData());return;}
 if(request==411&&result==RESULT_OK&&data!=null){if(data.getClipData()!=null){for(int j=0;j<data.getClipData().getItemCount();j++)attachDocument(data.getClipData().getItemAt(j).getUri(),data.getFlags());}else attachDocument(data.getData(),data.getFlags());showDocuments();return;}
 if(request==410&&result==RESULT_OK&&data!=null){java.util.ArrayList<String> values=data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);if(values!=null&&!values.isEmpty())handleSpeech(values.get(0));}}
 void applyDictation(){
 String t=voiceText.getText().toString().toLowerCase(java.util.Locale.ROOT);
 String[] names={"(?:grabenbreite|breite|ширина(?: траншеи)?)","(?:grabentiefe|tiefe|глубина(?: траншеи)?)","(?:trassenlänge|länge|длина(?: трассы)?)"};
 EditText[] targets={width,depth,length};String[] proposed=new String[3];StringBuilder preview=new StringBuilder();
 for(int j=0;j<names.length;j++){java.util.regex.Matcher m=java.util.regex.Pattern.compile(names[j]+"\\s*[:=]?\\s*(?:ist\\s+)?(\\d+(?:[.,]\\d+)?)\\s*(zentimeter|cm|сантиметр[а-я]*|см|millimeter|mm|мм|meter|метр[а-я]*|m|м)(?![\\p{L}])").matcher(t);
 if(m.find()){double v=Double.parseDouble(m.group(1).replace(',','.'));String unit=m.group(2);if(unit.startsWith("zentimeter")||unit.equals("cm")||unit.startsWith("сантиметр")||unit.equals("см"))v/=100;else if(unit.equals("mm")||unit.equals("мм")||unit.equals("millimeter"))v/=1000;if(v>0&&Double.isFinite(v)){proposed[j]=String.format(java.util.Locale.GERMANY,"%.3f",v);preview.append(new String[]{"Breite","Tiefe","Länge"}[j]+": "+proposed[j]+" m\n");}}}
 if(preview.length()==0){Toast.makeText(this,"Keine eindeutigen Maße mit Einheit erkannt.",Toast.LENGTH_LONG).show();return;}
 new android.app.AlertDialog.Builder(this).setTitle("Erkannte Maße prüfen").setMessage(preview.toString()).setNegativeButton("Abbrechen",null).setPositiveButton("Übernehmen",(dialog,which)->{for(int j=0;j<3;j++)if(proposed[j]!=null)targets[j].setText(proposed[j]);}).show();
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
 void persistSegments(){getPreferences(0).edit().putString("segments",segments.toString()).apply();showSegments();}
 void showSegments(){StringBuilder out=new StringBuilder();java.util.TreeMap<String,Double> totals=new java.util.TreeMap<>();
 for(int i=0;i<segments.length();i++){org.json.JSONObject o=segments.optJSONObject(i);if(o==null)continue;double a=o.optDouble("from"),b=o.optDouble("to"),area=(b-a)*o.optDouble("width");String type=o.optString("surface");
 out.append(String.format(java.util.Locale.GERMANY,"RP %02d | %.2f–%.2f m | %s | %.2f m² (Graben)\n",i+1,a,b,type,area));
 if(o.has("cutWidth"))out.append(String.format(java.util.Locale.GERMANY,"Mit Nachschnitt: %.2f m²\n",(b-a)*o.optDouble("cutWidth")));
 totals.put(type,totals.getOrDefault(type,0.0)+area);}
 for(java.util.Map.Entry<String,Double> e:totals.entrySet())out.append(String.format(java.util.Locale.GERMANY,"%s gesamt: %.2f m² (Graben)\n",e.getKey(),e.getValue()));
 segmentsView.setText(out.toString());}
 double optional(EditText e)throws Exception{if(e.length()==0)return Double.NaN;double v=number(e);if(!Double.isFinite(v)||v<0)throw new Exception();return v;}
 class Profile extends View{
 Paint p=new Paint(3);Profile(){super(MainActivity.this);}
 String m(double x){return String.format(java.util.Locale.GERMANY,"%.2f m",x);}
 void label(Canvas c,String s,float x,float y){p.setColor(Color.BLACK);p.setTextSize(12);c.drawText(s,x,y,p);}
 protected void onDraw(Canvas c){super.onDraw(c);if(w<=0||d<=0)return;
 c.save();c.scale(getWidth()/360f,getHeight()/300f);
 double nl=Double.isFinite(left)?left:0,nr=Double.isFinite(right)?right:0;
 float scale=(float)Math.min(190/(w+nl+nr),155/d);
 float x=180-(float)(w*scale/2),z=180+(float)(w*scale/2),top=55,bottom=top+(float)(d*scale);
 p.setColor(0xffb7a48a);c.drawRect(20,top,340,bottom,p);
 p.setColor(0xffeee2cc);c.drawRect(x,top,z,bottom,p);
 float layerTop=top;
 if(Double.isFinite(topThickness)){p.setColor(covering.equals("Asphalt")?0xff454950:0xff85796d);layerTop=top+(float)(topThickness*scale);c.drawRect(20,top,340,layerTop,p);}
 if(Double.isFinite(base)){p.setColor(0xffa6a6a6);c.drawRect(20,layerTop,340,layerTop+(float)(base*scale),p);}
 if(!covering.equals("Asphalt")&&!covering.equals("Unbekannt")&&!covering.equals("Unbefestigt")&&!covering.equals("Sonstiges")){p.setColor(0xff777777);c.drawLine(20,top,340,top,p);for(int i=20;i<340;i+=covering.contains("platten")||covering.contains("Platten")?28:12)c.drawLine(i,top-4,i,top,p);}
 if(Double.isFinite(pipe)){float radius=(float)(pipe/2000*scale);p.setColor(0xff3686bc);c.drawCircle(180,bottom-radius,radius,p);}

 p.setColor(Color.BLACK);p.setStrokeWidth(1);c.drawLine(x,top,x,bottom,p);c.drawLine(z,top,z,bottom,p);c.drawLine(x,bottom,z,bottom,p);
 c.drawLine(x,bottom+14,z,bottom+14,p);c.drawLine(x,bottom+8,x,bottom+20,p);c.drawLine(z,bottom+8,z,bottom+20,p);
 label(c,m(w),150,bottom+29);
 c.drawLine(z+12,top,z+12,bottom,p);label(c,m(d),z+16,(top+bottom)/2);
 if(Double.isFinite(left)&&Double.isFinite(right)){float a=x-(float)(nl*scale),b=z+(float)(nr*scale);c.drawLine(a,35,b,35,p);c.drawLine(a,30,a,45,p);c.drawLine(b,30,b,45,p);label(c,m(w+nl+nr),145,27);}
 else label(c,"Nachschnitt: nicht vollständig erfasst",20,27);
 label(c,"Oberfläche: "+covering,20,238);
 label(c,Double.isFinite(topThickness)?"Belagsstärke: "+m(topThickness):"Belagsstärke: nicht erfasst",20,252);
 label(c,Double.isFinite(pipe)?"Rohr Ø "+pipe+" mm (Lage symbolisch)":"Rohrmaße: unbekannt",20,266);
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
