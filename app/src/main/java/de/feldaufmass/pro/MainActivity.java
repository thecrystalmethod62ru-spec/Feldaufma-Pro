package de.feldaufmass.pro;
import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import android.view.*;
import android.graphics.*;
import android.content.SharedPreferences;

public class MainActivity extends Activity {
 EditText project,length,width,depth; TextView result; Sketch sketch;
 double l,w,d,asphalt,left,right; EditText asphaltInput,leftInput,rightInput; Profile profile; Spinner surface; EditText stoneLength,stoneWidth,stoneDepth,stoneColor,pattern,bedding,underlying; Spinner handling; TextView legend; EditText baseInput,pipeInput; double base=Double.NaN,pipe=Double.NaN; String covering="Unbekannt";
 EditText field(LinearLayout root,String label,String key,boolean numeric){
 TextView t=new TextView(this);t.setText(label);root.addView(t);
 EditText e=new EditText(this);e.setSingleLine(true);
 if(numeric)e.setInputType(8194);
 e.setText(getPreferences(0).getString(key,""));root.addView(e);return e;
 }
 double number(EditText e){return Double.parseDouble(e.getText().toString().replace(',','.'));}
 public void onCreate(Bundle b){super.onCreate(b);
 ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(24,32,24,24);scroll.addView(root);setContentView(scroll);
 TextView title=new TextView(this);title.setText("Feldaufmaß Pro");title.setTextSize(28);root.addView(title);
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
 result=new TextView(this);result.setTextSize(18);root.addView(result);
 sketch=new Sketch();root.addView(sketch,new LinearLayout.LayoutParams(-1,300));
 TextView heading=new TextView(this);heading.setText("Regelprofil – Grabenquerschnitt");heading.setTextSize(20);root.addView(heading);
 legend=new TextView(this);legend.setTextSize(14);root.addView(legend);
 profile=new Profile();root.addView(profile,new LinearLayout.LayoutParams(-1,(int)(300*getResources().getDisplayMetrics().density)));
 save.setOnClickListener(v->calculate());if(length.length()>0&&width.length()>0&&depth.length()>0)calculate();
 }
 void calculate(){try{l=number(length);w=number(width);d=number(depth);if(!Double.isFinite(l*w*d)||l<=0||w<=0||d<=0)throw new Exception();
 optional(stoneLength);optional(stoneWidth);optional(stoneDepth);
 covering=surface.getSelectedItem().toString();base=optional(baseInput);pipe=optional(pipeInput);if(Double.isFinite(pipe)&&(pipe<=0||pipe/1000>w||pipe/1000>d))throw new Exception();
 asphalt=optional(asphaltInput);left=optional(leftInput);right=optional(rightInput);if(Double.isFinite(asphalt)&&asphalt>d)throw new Exception();
 if(Double.isFinite(base)&&base+(Double.isFinite(asphalt)?asphalt:0)>d)throw new Exception();
 getPreferences(0).edit().putString("stoneLength",stoneLength.getText().toString()).putString("stoneWidth",stoneWidth.getText().toString()).putString("stoneDepth",stoneDepth.getText().toString()).putString("stoneColor",stoneColor.getText().toString()).putString("pattern",pattern.getText().toString()).putString("bedding",bedding.getText().toString()).putString("underlying",underlying.getText().toString()).putInt("handling",handling.getSelectedItemPosition()).putInt("surface",surface.getSelectedItemPosition()).putString("base",baseInput.getText().toString()).putString("pipe",pipeInput.getText().toString()).putString("asphalt",asphaltInput.getText().toString()).putString("left",leftInput.getText().toString()).putString("right",rightInput.getText().toString()).putString("project",project.getText().toString()).putString("length",length.getText().toString()).putString("width",width.getText().toString()).putString("depth",depth.getText().toString()).apply();
 result.setText(String.format(java.util.Locale.GERMANY,"Aushub: %.2f m³\nGrundfläche: %.2f m²",l*w*d,l*w));StringBuilder details=new StringBuilder("Legende\nOberfläche: "+covering);
 if(stoneLength.length()>0||stoneWidth.length()>0)details.append("\nSteinformat: "+(stoneLength.length()>0?stoneLength.getText():"?")+" × "+(stoneWidth.length()>0?stoneWidth.getText():"?")+" cm");
 if(stoneDepth.length()>0)details.append("\nBelagsstärke: "+stoneDepth.getText()+" cm");
 if(stoneColor.length()>0)details.append("\nFarbe: "+stoneColor.getText());
 if(pattern.length()>0)details.append("\nVerlegemuster: "+pattern.getText());
 if(bedding.length()>0)details.append("\nBettung: "+bedding.getText());
 if(underlying.length()>0)details.append("\nAltbelag darunter: "+underlying.getText());
 if(handling.getSelectedItemPosition()>0)details.append("\n"+handling.getSelectedItem());
 legend.setText(details.toString());sketch.invalidate();profile.invalidate();
 }catch(Exception e){result.setText("Bitte gültige positive Maße eingeben.");}}
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
 class Sketch extends View{Paint p=new Paint(3);Sketch(){super(MainActivity.this);}
 protected void onDraw(Canvas c){super.onDraw(c);float a=35,b=getWidth()-35;p.setColor(covering.equals("Asphalt")?0xff656970:covering.equals("Unbefestigt")?0xffa98d6c:0xffd9c4a5);c.drawRect(a,65,b,125,p);
 if(!covering.equals("Asphalt")&&!covering.equals("Unbefestigt")&&!covering.equals("Unbekannt")&&!covering.equals("Sonstiges")){
 p.setColor(0xff81796b);p.setStrokeWidth(1);int step=covering.toLowerCase(java.util.Locale.ROOT).contains("platten")?32:16;
 c.save();c.clipRect(a,65,b,125);
 for(int row=0;row<3;row++){float y=65+row*20;c.drawLine(a,y,b,y,p);for(float x=a-(row%2)*step/2;x<b;x+=step)c.drawLine(x,y,x,y+20,p);}
 c.restore();}
p.setColor(Color.BLACK);p.setStrokeWidth(2);c.drawLine(a,95,b,95,p);c.drawLine(a,160,b,160,p);c.drawLine(a,150,a,170,p);c.drawLine(b,150,b,170,p);p.setTextSize(22);c.drawText("0,00 m",a,45,p);c.drawText(String.format(java.util.Locale.GERMANY,"%.2f m",l),Math.max(a,b-110),45,p);c.drawText("Breite: "+w+" m",a,200,p);c.drawText("Schematisch – nicht maßstäblich",a,245,p);}
 }
}
