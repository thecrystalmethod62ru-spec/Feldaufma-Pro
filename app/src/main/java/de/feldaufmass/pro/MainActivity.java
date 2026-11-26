package de.feldaufmass.pro;
import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import android.view.*;
import android.graphics.*;
import android.content.SharedPreferences;

public class MainActivity extends Activity {
 EditText project,length,width,depth; TextView result; Sketch sketch;
 double l,w,d;
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
 Button save=new Button(this);save.setText("Berechnen und speichern");root.addView(save);
 result=new TextView(this);result.setTextSize(18);root.addView(result);
 sketch=new Sketch();root.addView(sketch,new LinearLayout.LayoutParams(-1,300));
 save.setOnClickListener(v->calculate());if(length.length()>0&&width.length()>0&&depth.length()>0)calculate();
 }
 void calculate(){try{l=number(length);w=number(width);d=number(depth);if(!Double.isFinite(l*w*d)||l<=0||w<=0||d<=0)throw new Exception();
 getPreferences(0).edit().putString("project",project.getText().toString()).putString("length",length.getText().toString()).putString("width",width.getText().toString()).putString("depth",depth.getText().toString()).apply();
 result.setText(String.format(java.util.Locale.GERMANY,"Aushub: %.2f m³\nGrundfläche: %.2f m²",l*w*d,l*w));sketch.invalidate();
 }catch(Exception e){result.setText("Bitte gültige positive Maße eingeben.");}}
 class Sketch extends View{Paint p=new Paint(3);Sketch(){super(MainActivity.this);}
 protected void onDraw(Canvas c){super.onDraw(c);float a=35,b=getWidth()-35;p.setColor(0xffd9c4a5);c.drawRect(a,65,b,125,p);p.setColor(Color.BLACK);p.setStrokeWidth(2);c.drawLine(a,95,b,95,p);c.drawLine(a,160,b,160,p);c.drawLine(a,150,a,170,p);c.drawLine(b,150,b,170,p);p.setTextSize(22);c.drawText("0,00 m",a,45,p);c.drawText(String.format(java.util.Locale.GERMANY,"%.2f m",l),Math.max(a,b-110),45,p);c.drawText("Breite: "+w+" m",a,200,p);c.drawText("Schematisch – nicht maßstäblich",a,245,p);}
 }
}
