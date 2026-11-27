package de.feldaufmass.pro;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import java.util.List;

final class UtilityCatalogDialog {
 interface Selection { void selected(String type,String name,String material,double diameter); }
 private static final int BG=0xff0b1115,FG=0xffe6f0f1,ACCENT=0xff27c8c0;
 private final Context context;
 private final Selection selection;
 private final PipeCatalog pipes;
 private final CableCatalog cables;
 UtilityCatalogDialog(Context context,Selection selection){
  this.context=context;this.selection=selection;
  pipes=new PipeCatalog(context);cables=new CableCatalog(context);
 }
 private int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
 private TextView text(String value,int size,int color){
  TextView t=new TextView(context);t.setText(value);t.setTextSize(size);t.setTextColor(color);
  t.setGravity(android.view.Gravity.CENTER_VERTICAL);t.setPadding(dp(12),dp(8),dp(12),dp(8));return t;
 }
 private LinearLayout panel(){LinearLayout l=new LinearLayout(context);l.setOrientation(1);l.setBackgroundColor(BG);l.setPadding(dp(12),dp(8),dp(12),dp(8));return l;}
 private void show(AlertDialog dialog){dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(BG));dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ACCENT);}
 void open(){
  LinearLayout body=panel();body.addView(text("Leitungen",20,FG));
  AlertDialog d=new AlertDialog.Builder(context).setView(body).setNegativeButton("Schließen",null).create();
  category(body,"Rohre",false,()->{d.dismiss();entries(false,false);});
  category(body,"Kabel",true,()->{d.dismiss();entries(true,false);});
  show(d);
 }
 private void category(LinearLayout body,String title,boolean cable,Runnable action){
  LinearLayout row=new LinearLayout(context);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
  row.addView(new Symbol(context,cable),new LinearLayout.LayoutParams(dp(42),dp(42)));
  row.addView(text(title,17,ACCENT),new LinearLayout.LayoutParams(0,dp(52),1));
  row.setContentDescription(title);row.setOnClickListener(v->action.run());body.addView(row);
 }
 private void entries(boolean cable,boolean onlyFavorites){
  LinearLayout body=panel();body.addView(text(cable?"Kabel":"Rohre",20,FG));
  TextView filter=text(onlyFavorites?"Alle anzeigen":"☆ Favoriten",15,ACCENT);body.addView(filter);
  ScrollView scroll=new ScrollView(context);LinearLayout rows=panel();scroll.addView(rows);
  body.addView(scroll,new LinearLayout.LayoutParams(-1,dp(340)));
  AlertDialog d=new AlertDialog.Builder(context).setView(body).setNegativeButton("Zurück",null).create();
  filter.setOnClickListener(v->{d.dismiss();entries(cable,!onlyFavorites);});
  if(cable){
   List<CableCatalog.Entry> list=onlyFavorites?cables.favorites():CableCatalog.entries();
   for(CableCatalog.Entry e:list)addRow(rows,e.label(),cables.isFavorite(e),()->{d.dismiss();cableDiameter(e);},()->cables.toggleFavorite(e),onlyFavorites,()->{d.dismiss();entries(true,true);});
   if(list.isEmpty())rows.addView(text("Keine Favoriten",15,FG));
  }else{
   List<PipeCatalog.Entry> list=onlyFavorites?pipes.favorites():PipeCatalog.entries();
   for(PipeCatalog.Entry e:list)addRow(rows,e.label(),pipes.isFavorite(e),()->{d.dismiss();selection.selected(e.medium,e.label(),e.material,e.diameter);},()->pipes.toggleFavorite(e),onlyFavorites,()->{d.dismiss();entries(false,true);});
   if(list.isEmpty())rows.addView(text("Keine Favoriten",15,FG));
  }
  show(d);d.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{d.dismiss();open();});
 }
 private void addRow(LinearLayout rows,String label,boolean favorite,Runnable choose,Runnable toggle,boolean filtered,Runnable refresh){
  LinearLayout row=new LinearLayout(context);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
  TextView value=text(label,14,FG);row.addView(value,new LinearLayout.LayoutParams(0,-2,1));value.setOnClickListener(v->choose.run());
  TextView star=text(favorite?"★":"☆",23,ACCENT);star.setContentDescription("Favorit: "+label);
  row.addView(star,new LinearLayout.LayoutParams(dp(52),dp(48)));
  final boolean[] state={favorite};
  star.setOnClickListener(v->{toggle.run();state[0]=!state[0];star.setText(state[0]?"★":"☆");if(filtered)refresh.run();});
  rows.addView(row);View line=new View(context);line.setBackgroundColor(0xff263c40);rows.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
 }
 private void cableDiameter(CableCatalog.Entry e){
  LinearLayout body=panel();body.addView(text(e.label(),18,FG));body.addView(text("Außendurchmesser laut Hersteller (mm)",14,FG));
  EditText input=new EditText(context);input.setTextColor(FG);input.setHintTextColor(0xff8c9fa1);input.setHint("mm");input.setInputType(8194);body.addView(input);
  AlertDialog d=new AlertDialog.Builder(context).setView(body).setNegativeButton("Zurück",null).setPositiveButton("Übernehmen",null).create();
  show(d);d.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{d.dismiss();entries(true,false);});
  d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ACCENT);
  d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{
   double mm=Double.parseDouble(input.getText().toString().trim().replace(',','.'));
   if(!Double.isFinite(mm)||mm<=0)throw new IllegalArgumentException();
   selection.selected(e.medium,e.designation,"",mm);d.dismiss();
  }catch(Exception ex){input.setError("Gültigen Außendurchmesser eingeben");}});
 }
 private static final class Symbol extends View{
  final boolean cable;final Paint p=new Paint(3);
  Symbol(Context context,boolean cable){super(context);this.cable=cable;}
  @Override protected void onDraw(Canvas c){
   super.onDraw(c);float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(x,y)*.72f;
   p.setColor(ACCENT);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1.5f,r*.10f));
   c.drawCircle(x,y,r,p);
   if(cable){for(int i=0;i<4;i++){double a=Math.PI/4+i*Math.PI/2;c.drawCircle(x+(float)Math.cos(a)*r*.43f,y+(float)Math.sin(a)*r*.43f,r*.24f,p);}}
   else c.drawCircle(x,y,r*.76f,p);
  }
 }
}
