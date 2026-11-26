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
 LinearLayout page(){LinearLayout p=new LinearLayout(this);p.setOrientation(1);p.setPadding(16,12,16,12);return p;}
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 void showPage(LinearLayout p){if(p==documentsPage)compareSoll();if(p==quantityPage)refreshDetailQuantities();body.removeAllViews();ScrollView sc=new ScrollView(this);sc.setFillViewport(true);if(p.getParent()!=null)((android.view.ViewGroup)p.getParent()).removeView(p);sc.addView(p);body.addView(sc,new LinearLayout.LayoutParams(-1,-1));}
 void nav(LinearLayout bar,String name,Runnable action){Button b=new Button(this);b.setText(name);b.setTextSize(12);b.setAllCaps(false);bar.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->action.run());}
 public void onCreate(Bundle b){super.onCreate(b);
 shell=page();shell.setPadding(0,dp(28),0,dp(20));shell.setBackgroundColor(0xfff4f6f8);setContentView(shell);
 projectHeader=new TextView(this);projectHeader.setText("Feldaufmaß Pro");projectHeader.setTextColor(Color.WHITE);projectHeader.setTextSize(22);projectHeader.setPadding(dp(16),dp(12),dp(16),dp(12));projectHeader.setBackgroundColor(0xff203849);shell.addView(projectHeader);
 LinearLayout tabs=new LinearLayout(this);shell.addView(tabs);nav(tabs,"Zeichnung",()->showPage(planPage));nav(tabs,"Regelprofil",()->showPage(profilePage));nav(tabs,"Mengen",()->showPage(quantityPage));
 body=new LinearLayout(this);shell.addView(body,new LinearLayout.LayoutParams(-1,0,1));
 LinearLayout tools=new LinearLayout(this);shell.addView(tools);nav(tools,"Maße",()->showPage(measurePage));nav(tools,"Beläge",()->showPage(segmentPage));nav(tools,"Objekte",()->showPage(objectPage));nav(tools,"Unterlagen",()->showPage(documentsPage));
 Button voiceButton=new Button(this);voiceButton.setText("Sprachassistent");voiceButton.setAllCaps(false);shell.addView(voiceButton);voiceButton.setOnClickListener(v->showPage(voicePage));
 planPage=page();measurePage=page();voicePage=page();profilePage=page();segmentPage=page();objectPage=page();quantityPage=page();
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
 voiceLanguage=new Spinner(this);voiceLanguage.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Deutsch","Русский"}));root.addView(voiceLanguage);
 Button mic=new Button(this);mic.setText("Spracheingabe starten");root.addView(mic);
 voiceText=new EditText(this);voiceText.setHint("Diktat prüfen oder bearbeiten");root.addView(voiceText);
 Button applyVoice=new Button(this);applyVoice.setText("Maße aus Diktat übernehmen");root.addView(applyVoice);
 mic.setOnClickListener(v->startDictation());applyVoice.setOnClickListener(v->applyDictation());
 root=measurePage;
 project=field(root,"Projekt / Baustelle","project",false);
 length=field(root,"Trassenlänge (m)","length",true);
 width=field(root,"Grabenbreite (m)","width",true);
 depth=field(root,"Grabentiefe (m)","depth",true);
 TextView surfaceLabel=new TextView(this);surfaceLabel.setText("Oberfläche");root.addView(surfaceLabel);
 surface=new Spinner(this);String[] types={"Unbekannt","Asphalt","Pflaster","Gehwegplatten","Unbefestigt","Betonpflaster – Rechteck","Betonpflaster – Quadrat","Doppel-T-Pflaster","Wellenverbundpflaster","Sechseckpflaster","Achteckpflaster","Verbundpflaster – sonstige","Betonpflaster – Mehrformat","Ökopflaster / Sickerpflaster","Rasengittersteine","Rasenfugenpflaster","Natursteinpflaster – Großpflaster","Natursteinpflaster – Kleinpflaster","Natursteinpflaster – Mosaikpflaster","Natursteinpflaster – sonstige","Klinkerpflaster","Ziegelpflaster","Beton-Gehwegplatten","Natursteinplatten","Waschbetonplatten","Taktilplatten – Rippen","Taktilplatten – Noppen","Rinnenpflaster","Schlackenpflaster","Holzpflaster","Sonstiges"};
 surface.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,types));root.addView(surface);
 surface.setSelection(Math.min(types.length-1,Math.max(0,getPreferences(0).getInt("surface",0))));
 stoneLength=field(root,"Steinlänge (cm, optional)","stoneLength",true);
 stoneWidth=field(root,"Steinbreite (cm, optional)","stoneWidth",true);
 stoneDepth=field(root,"Belagsstärke (cm, optional)","stoneDepth",true);
 stoneColor=field(root,"Farbe (optional)","stoneColor",false);
 pattern=field(root,"Verlegemuster (optional)","pattern",false);
 bedding=field(root,"Bettung – Material / Stärke (optional)","bedding",false);
 underlying=field(root,"Unterliegender Altbelag (z. B. Großpflaster)","underlying",false);
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
 sketch=new Sketch();root.addView(sketch,new LinearLayout.LayoutParams(-1,dp(280))); LinearLayout historyBar=new LinearLayout(this);root.addView(historyBar);undoObjectsButton=new Button(this);undoObjectsButton.setText("Rückgängig");historyBar.addView(undoObjectsButton,new LinearLayout.LayoutParams(0,-2,1));redoObjectsButton=new Button(this);redoObjectsButton.setText("Wiederholen");historyBar.addView(redoObjectsButton,new LinearLayout.LayoutParams(0,-2,1));undoObjectsButton.setOnClickListener(v->restoreObjectHistory(false));redoObjectsButton.setOnClickListener(v->restoreObjectHistory(true));updateHistoryButtons();
 Button edit=new Button(this);edit.setText("Trassenmaße bearbeiten");root.addView(edit);edit.setOnClickListener(v->showPage(measurePage));
 root=profilePage;
 TextView heading=new TextView(this);heading.setText("Regelprofil – Grabenquerschnitt");heading.setTextSize(20);root.addView(heading);
 legend=new TextView(this);legend.setTextSize(14);root.addView(legend);
 root=segmentPage;
 Button editSurface=new Button(this);editSurface.setText("Belagsdetails bearbeiten");root.addView(editSurface);editSurface.setOnClickListener(v->showPage(measurePage));
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
 Button ob=new Button(this);ob.setText("Objekt hinzufügen");root.addView(ob);ob.setOnClickListener(v->addObject());
 objectList=new TextView(this);root.addView(objectList);showObjects();
 Button undo=new Button(this);undo.setText("Letztes Objekt entfernen");root.addView(undo);undo.setOnClickListener(v->{if(objects.length()>0){objects.remove(objects.length()-1);saveObjects();}});
 root=profilePage;
 profile=new Profile();root.addView(profile,new LinearLayout.LayoutParams(-1,(int)(300*getResources().getDisplayMetrics().density)));
 profileSlider(profilePage,"Grabenbreite",width,10,600);
 profileSlider(profilePage,"Grabentiefe",depth,10,800);
 profileSlider(profilePage,"Nachschnitt links",leftInput,0,200);
 profileSlider(profilePage,"Nachschnitt rechts",rightInput,0,200);
 save.setOnClickListener(v->{if(calculate())showPage(planPage);});if(length.length()>0&&width.length()>0&&depth.length()>0)calculate();showPage(planPage);
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
 optional(stoneLength);optional(stoneWidth);optional(stoneDepth);
 String covering=surface.getSelectedItem().toString();double base=optional(baseInput),pipe=optional(pipeInput);if(Double.isFinite(pipe)&&(pipe<=0||pipe/1000>w||pipe/1000>d))throw new Exception();
 double asphalt=optional(asphaltInput),left=optional(leftInput),right=optional(rightInput);if(Double.isFinite(asphalt)&&asphalt>d)throw new Exception();
 if(Double.isFinite(base)&&base+(Double.isFinite(asphalt)?asphalt:0)>d)throw new Exception();
 this.l=l; this.w=w; this.d=d; this.covering=covering;
 this.base=base; this.pipe=pipe; this.asphalt=asphalt; this.left=left; this.right=right;
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
 compareSoll();showObjects();refreshDetailQuantities();legend.setText(details.toString());sketch.invalidate();profile.invalidate();return true;
 }catch(Exception e){Toast.makeText(this,"Maße prüfen. Stand wurde nicht gespeichert.",Toast.LENGTH_SHORT).show();return false;}}
 void detailObject(int index){org.json.JSONObject o=objects.optJSONObject(index);if(o==null)return;LinearLayout form=page();Spinner mode=new Spinner(this);mode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Draufsicht","Querschnitt","Längsschnitt"}));form.addView(mode);EditText a=new EditText(this),b=new EditText(this),h=new EditText(this);EditText[] es={a,b,h};String[] keys={"detailLength","detailWidth","detailDepth"},labels={"Länge (m)","Breite (m)","Tiefe (m)"};for(int j=0;j<3;j++){es[j].setInputType(8194);es[j].setHint(labels[j]);es[j].setText(o.optString(keys[j],""));form.addView(es[j]);}View preview=new View(this){Paint p=new Paint(3);protected void onDraw(Canvas c){float x=getWidth()/2f,y=getHeight()/2f; p.setColor(0xffdce9f4);c.drawRect(x-85,y-45,x+85,y+45,p);p.setColor(0xff203849);p.setStyle(Paint.Style.STROKE);c.drawRect(x-85,y-45,x+85,y+45,p);p.setStyle(Paint.Style.FILL);p.setTextSize(18);c.drawText(mode.getSelectedItem().toString()+" – schematisch",12,24,p);c.drawText((mode.getSelectedItemPosition()==1?b:a).getText()+" m",x-35,y-55,p);c.drawText((mode.getSelectedItemPosition()==0?b:h).getText()+" m",x+90,y,p);}};form.addView(preview,new LinearLayout.LayoutParams(-1,dp(180)));for(EditText e:es)e.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){preview.invalidate();}public void afterTextChanged(android.text.Editable e){}});mode.setSelection(Math.max(0,Math.min(2,o.optInt("detailMode",0))));mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){preview.invalidate();}public void onNothingSelected(android.widget.AdapterView<?> p){}});android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Detailzeichnung – "+o.optString("type")+" / "+o.optString("station")+" m").setView(form).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(btn->{try{for(EditText e:es){if(e.length()>0){double n=number(e);if(!Double.isFinite(n)||n<=0){e.setError("Positives Maß erforderlich");return;}}}for(int j=0;j<3;j++)o.put(keys[j],es[j].getText().toString());o.put("detailMode",mode.getSelectedItemPosition());saveObjects();dialog.dismiss();}catch(Exception e){Toast.makeText(this,"Detail konnte nicht gespeichert werden",Toast.LENGTH_LONG).show();}}));dialog.show();}
 void editObject(int index){org.json.JSONObject o=objects.optJSONObject(index);if(o==null)return;LinearLayout form=page();EditText st=new EditText(this);st.setInputType(8194);st.setHint("Station (m)");st.setText(o.optString("station"));form.addView(st);EditText note=new EditText(this);note.setHint("Bemerkung");note.setText(o.optString("note"));form.addView(note);android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle(o.optString("type")+" bearbeiten").setView(form).setNeutralButton("Detailzeichnung",(v,j)->detailObject(index)).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{try{double x=number(st),total=l;if(!Double.isFinite(x)||!Double.isFinite(total)||x<0||x>total)throw new Exception();o.put("station",x);o.put("note",note.getText().toString());saveObjects();dialog.dismiss();}catch(Exception e){st.setError("Station außerhalb der Trasse");}}));dialog.show();}
 double detailNumber(org.json.JSONObject o,String key){try{return Double.parseDouble(o.optString(key).replace(',','.'));}catch(Exception e){return Double.NaN;}}
 double detailExtent(org.json.JSONObject o){try{double v=Double.parseDouble(o.optString("detailLength","").replace(',','.'));return Double.isFinite(v)&&v>0?v:0;}catch(Exception e){return 0;}}
 boolean objectWarning(int index){org.json.JSONObject o=objects.optJSONObject(index);if(o==null)return false;double x=o.optDouble("station"),a=detailExtent(o)/2;if(!Double.isFinite(x)||x-a<0||x+a>l)return true;for(int j=0;j<objects.length();j++){org.json.JSONObject q=objects.optJSONObject(j);if(j==index||q==null)continue;double y=q.optDouble("station"),b=detailExtent(q)/2;if(!Double.isFinite(y))continue;if(o.optString("side").equals(q.optString("side"))&&(Math.abs(x-y)<0.01||Math.abs(x-y)<a+b))return true;}return false;}
 void addObject(){try{double st=number(objectStation),total=l;if(!Double.isFinite(st)||!Double.isFinite(total)||total<=0||st<0||st>total)throw new Exception();org.json.JSONObject o=new org.json.JSONObject();o.put("station",st);o.put("type",objectType.getSelectedItem().toString());o.put("side",objectSide.getSelectedItem().toString());o.put("note",objectNote.getText().toString());objects.put(o);saveObjects();}catch(Exception e){Toast.makeText(this,"Objektstation außerhalb der Trasse oder ungültig.",Toast.LENGTH_LONG).show();}}
 void updateHistoryButtons(){if(undoObjectsButton!=null)undoObjectsButton.setEnabled(!undoObjects.isEmpty());if(redoObjectsButton!=null)redoObjectsButton.setEnabled(!redoObjects.isEmpty());}
 void persistObjects(){getPreferences(0).edit().putString("objects",objects.toString()).apply();showObjects();refreshDetailQuantities();sketch.invalidate();updateHistoryButtons();}
 void saveObjects(){String old=getPreferences(0).getString("objects","[]");if(!old.equals(objects.toString())){undoObjects.push(old);while(undoObjects.size()>50)undoObjects.removeLast();redoObjects.clear();}persistObjects();}
 void restoreObjectHistory(boolean redo){java.util.ArrayDeque<String> from=redo?redoObjects:undoObjects,to=redo?undoObjects:redoObjects;if(from.isEmpty())return;try{org.json.JSONArray restored=new org.json.JSONArray(from.peek());to.push(objects.toString());from.pop();objects=restored;persistObjects();}catch(org.json.JSONException e){Toast.makeText(this,"Änderung konnte nicht wiederhergestellt werden",Toast.LENGTH_SHORT).show();}}

 void refreshDetailQuantities(){if(detailQuantities==null)return;StringBuilder b=new StringBuilder("\nDetailmengen – geometrisch, nicht addiert\n");for(int i=0;i<objects.length();i++){org.json.JSONObject o=objects.optJSONObject(i);if(o==null)continue;double a=detailNumber(o,"detailLength"),w=detailNumber(o,"detailWidth"),h=detailNumber(o,"detailDepth");b.append(o.optString("type")).append(" / ").append(o.optString("station")).append(" m\n");if(Double.isFinite(a*w)&&a>0&&w>0){b.append(String.format(java.util.Locale.GERMANY,"Fläche: %.2f m²",a*w));if(Double.isFinite(a*w*h)&&h>0)b.append(String.format(java.util.Locale.GERMANY," | Körpervolumen: %.2f m³",a*w*h));else b.append(" | Tiefe fehlt");}else b.append("Detailmaße fehlen");if(objectWarning(i))b.append(" | ⚠ Lage prüfen");b.append("\n");}b.append("\nNicht zur Haupttrasse addieren: Überschneidungen und LV-Regeln prüfen.");detailQuantities.setText(b.toString());}
 String detailSummary(org.json.JSONObject o){StringBuilder b=new StringBuilder();String[] keys={"detailLength","detailWidth","detailDepth"},names={"L","B","T"};for(int j=0;j<3;j++){String v=o.optString(keys[j],"");if(!v.isEmpty())b.append(" | ").append(names[j]).append("=").append(v).append(" m");}return b.toString();}
 void showObjects(){StringBuilder b=new StringBuilder();for(int i=0;i<objects.length();i++){org.json.JSONObject o=objects.optJSONObject(i);if(o!=null)b.append(String.format(java.util.Locale.GERMANY,"%.2f m | %s | %s | %s\n",o.optDouble("station"),o.optString("type"),o.optString("side"),o.optString("note")+detailSummary(o)+(objectWarning(i)?" | ⚠ Station prüfen":"")));}objectList.setText(b.toString());}
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
   String report=result.getText()+"\n"+legend.getText()+"\n"+
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
 void startDictation(){
 android.content.Intent i=new android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
 i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
 i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE,voiceLanguage.getSelectedItemPosition()==0?"de-DE":"ru-RU");
 i.putExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
 try{startActivityForResult(i,410);}catch(android.content.ActivityNotFoundException e){Toast.makeText(this,"Keine Spracherkennung verfügbar.",Toast.LENGTH_LONG).show();}
 }
 @Override protected void onActivityResult(int request,int result,android.content.Intent data){super.onActivityResult(request,result,data);
 if(request==412&&result==RESULT_OK&&data!=null&&data.getData()!=null){writePdf(data.getData());return;}
 if(request==411&&result==RESULT_OK&&data!=null){if(data.getClipData()!=null){for(int j=0;j<data.getClipData().getItemCount();j++)attachDocument(data.getClipData().getItemAt(j).getUri(),data.getFlags());}else attachDocument(data.getData(),data.getFlags());showDocuments();return;}
 if(request==410&&result==RESULT_OK&&data!=null){java.util.ArrayList<String> values=data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);if(values!=null&&!values.isEmpty())voiceText.setText(values.get(0));}}
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
 if(Double.isFinite(asphalt)&&covering.equals("Asphalt")){p.setColor(0xff454950);layerTop=top+(float)(asphalt*scale);c.drawRect(20,top,340,layerTop,p);}
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
 label(c,covering.equals("Asphalt")?(Double.isFinite(asphalt)?"Asphalt: "+m(asphalt):"Asphaltstärke: unbekannt"):"Belagsstärke: nicht erfasst",20,252);
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
