package de.feldaufmass.pro;
import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import android.view.*;
import android.graphics.*;
import android.content.SharedPreferences;

public class MainActivity extends Activity {
 EditText project,length,width,depth; TextView result; Sketch sketch;
 double l,w,d,asphalt,left,right; EditText asphaltInput,leftInput,rightInput; Profile profile;
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
 asphaltInput=field(root,"Asphaltstärke (m, optional)","asphalt",true);
 leftInput=field(root,"Nachschnitt links (m, optional)","left",true);
 rightInput=field(root,"Nachschnitt rechts (m, optional)","right",true);
 Button save=new Button(this);save.setText("Berechnen und speichern");root.addView(save);
 result=new TextView(this);result.setTextSize(18);root.addView(result);
 sketch=new Sketch();root.addView(sketch,new LinearLayout.LayoutParams(-1,300));
 TextView heading=new TextView(this);heading.setText("Regelprofil – Grabenquerschnitt");heading.setTextSize(20);root.addView(heading);
 profile=new Profile();root.addView(profile,new LinearLayout.LayoutParams(-1,(int)(300*getResources().getDisplayMetrics().density)));
 save.setOnClickListener(v->calculate());if(length.length()>0&&width.length()>0&&depth.length()>0)calculate();
 }
 void calculate(){try{l=number(length);w=number(width);d=number(depth);if(!Double.isFinite(l*w*d)||l<=0||w<=0||d<=0)throw new Exception();
 asphalt=optional(asphaltInput);left=optional(leftInput);right=optional(rightInput);if(Double.isFinite(asphalt)&&asphalt>d)throw new Exception();
 getPreferences(0).edit().putString("asphalt",asphaltInput.getText().toString()).putString("left",leftInput.getText().toString()).putString("right",rightInput.getText().toString()).putString("project",project.getText().toString()).putString("length",length.getText().toString()).putString("width",width.getText().toString()).putString("depth",depth.getText().toString()).apply();
 result.setText(String.format(java.util.Locale.GERMANY,"Aushub: %.2f m³\nGrundfläche: %.2f m²",l*w*d,l*w));sketch.invalidate();profile.invalidate();
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
 if(Double.isFinite(asphalt)){p.setColor(0xff454950);c.drawRect(20,top,340,top+(float)(asphalt*scale),p);}
 p.setColor(Color.BLACK);p.setStrokeWidth(1);c.drawLine(x,top,x,bottom,p);c.drawLine(z,top,z,bottom,p);c.drawLine(x,bottom,z,bottom,p);
 c.drawLine(x,bottom+14,z,bottom+14,p);c.drawLine(x,bottom+8,x,bottom+20,p);c.drawLine(z,bottom+8,z,bottom+20,p);
 label(c,m(w),150,bottom+29);
 c.drawLine(z+12,top,z+12,bottom,p);label(c,m(d),z+16,(top+bottom)/2);
 if(Double.isFinite(left)&&Double.isFinite(right)){float a=x-(float)(nl*scale),b=z+(float)(nr*scale);c.drawLine(a,35,b,35,p);c.drawLine(a,30,a,45,p);c.drawLine(b,30,b,45,p);label(c,m(w+nl+nr),145,27);}
 else label(c,"Nachschnitt: nicht vollständig erfasst",20,27);
 label(c,Double.isFinite(asphalt)?"Asphalt: "+m(asphalt):"Asphaltstärke: unbekannt",20,260);
 label(c,"Schematisch – keine Ausführungsplanung",20,282);c.restore();
 }}
 class Sketch extends View{Paint p=new Paint(3);Sketch(){super(MainActivity.this);}
 protected void onDraw(Canvas c){super.onDraw(c);float a=35,b=getWidth()-35;p.setColor(0xffd9c4a5);c.drawRect(a,65,b,125,p);p.setColor(Color.BLACK);p.setStrokeWidth(2);c.drawLine(a,95,b,95,p);c.drawLine(a,160,b,160,p);c.drawLine(a,150,a,170,p);c.drawLine(b,150,b,170,p);p.setTextSize(22);c.drawText("0,00 m",a,45,p);c.drawText(String.format(java.util.Locale.GERMANY,"%.2f m",l),Math.max(a,b-110),45,p);c.drawText("Breite: "+w+" m",a,200,p);c.drawText("Schematisch – nicht maßstäblich",a,245,p);}
 }
}
