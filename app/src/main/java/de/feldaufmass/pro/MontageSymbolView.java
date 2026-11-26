package de.feldaufmass.pro;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Schematic previews, not certified DIN symbols. */
public final class MontageSymbolView extends View {
 private final MontageCatalog.Fitting fitting;
 private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
 public MontageSymbolView(Context c,MontageCatalog.Fitting f){super(c);fitting=f;setContentDescription(f.label);}
 @Override protected void onDraw(Canvas c){
  super.onDraw(c);float scale=Math.min(getWidth(),getHeight())/100f;
  c.save();c.translate((getWidth()-100*scale)/2,(getHeight()-100*scale)/2);c.scale(scale,scale);
  android.util.TypedValue value=new android.util.TypedValue();
  getContext().getTheme().resolveAttribute(android.R.attr.textColorPrimary,value,true);
  p.setColor(value.resourceId!=0?getResources().getColorStateList(value.resourceId,getContext().getTheme()).getDefaultColor():value.data);
  p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);p.setStrokeJoin(Paint.Join.ROUND);
  if(fitting.bendAngle>0){
   double a=Math.toRadians(fitting.bendAngle);
   line(c,10,30,50,30);line(c,50,30,50+40*(float)Math.cos(a),30+40*(float)Math.sin(a));
  }else switch(fitting){
   case TEE:line(c,10,50,90,50);line(c,50,50,50,90);break;
   case CROSS:line(c,10,50,90,50);line(c,50,10,50,90);break;
   case REDUCER:Path r=new Path();r.moveTo(10,32);r.lineTo(35,32);r.lineTo(65,42);r.lineTo(90,42);r.lineTo(90,58);r.lineTo(65,58);r.lineTo(35,68);r.lineTo(10,68);r.close();c.drawPath(r,p);break;
   case COUPLING:line(c,10,50,90,50);c.drawRect(35,35,65,65,p);break;
   case FLANGE:line(c,10,50,90,50);line(c,46,25,46,75);line(c,54,25,54,75);break;
   case CAP:line(c,10,35,50,35);line(c,10,65,50,65);c.drawArc(35,35,65,65,-90,180,false,p);break;
   case GATE_VALVE:line(c,10,50,30,50);line(c,70,50,90,50);Path v=new Path();v.moveTo(30,35);v.lineTo(70,65);v.lineTo(70,35);v.lineTo(30,65);v.close();c.drawPath(v,p);line(c,50,50,50,20);line(c,40,20,60,20);break;
   default:break;
  }c.restore();
 }
 private void line(Canvas c,float x,float y,float a,float b){c.drawLine(x,y,a,b,p);}
}
