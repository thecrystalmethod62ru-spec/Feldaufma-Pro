package de.feldaufmass.pro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Locale;

/** Coordinates describe a schematic only. Metres are entered in the field. */
final class RouteSketch extends View {
 static final class Vertex {
  final PointF point;
  double metres; // Length of the incoming segment, NaN if unmeasured.
  Vertex(PointF point,double metres){this.point=point;this.metres=metres;}
 }
 final ArrayList<Vertex> vertices=new ArrayList<>();
 private final ArrayList<PointF> stroke=new ArrayList<>();
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 boolean editable,dimensioning;
 Runnable changed;
 interface SegmentListener {void select(int endpoint);}
 SegmentListener selected;

 RouteSketch(Context context,String data){
  super(context);
  try{
   JSONArray rows=new JSONArray(data);
   for(int i=0;i<rows.length();i++){
    JSONArray row=rows.getJSONArray(i);
    vertices.add(new Vertex(new PointF((float)row.getDouble(0),(float)row.getDouble(1)),row.optDouble(2,Double.NaN)));
   }
  }catch(JSONException e){vertices.clear();}
  setContentDescription("Trassenskizze");
 }
 String encode(){
  JSONArray rows=new JSONArray();
  try{
   for(Vertex v:vertices){
    JSONArray row=new JSONArray().put((double)v.point.x).put((double)v.point.y);
    if(Double.isFinite(v.metres))row.put(v.metres);
    rows.put(row);
   }
  }catch(JSONException e){throw new IllegalStateException(e);}
  return rows.toString();
 }
 double measuredLength(){
  if(vertices.size()<2)return Double.NaN;
  double total=0;
  for(int i=1;i<vertices.size();i++)total+=vertices.get(i).metres;
  return total;
 }
 String measurements(){
  StringBuilder text=new StringBuilder();
  for(int i=1;i<vertices.size();i++){
   double metres=vertices.get(i).metres;
   if(i>1)text.append("\n");
   text.append("Abschnitt ").append(i).append(" · ").append(Double.isFinite(metres)?
      String.format(Locale.GERMANY,"%.2f m",metres):"Maß offen");
  }
  return text.toString();
 }
 private float scale(){return Math.max(1,Math.min(getWidth(),getHeight())*.88f);}
 private float left(){return (getWidth()-scale())/2;}
 private float top(){return (getHeight()-scale())/2;}
 private static float clamp(float n){return Math.max(0,Math.min(1,n));}
 private PointF point(float x,float y){return new PointF(clamp((x-left())/scale()),clamp((y-top())/scale()));}
 private static double distance(PointF a,PointF b){return Math.hypot(a.x-b.x,a.y-b.y);}
 private static double distanceToLine(PointF p,PointF a,PointF b){
  double dx=b.x-a.x,dy=b.y-a.y,den=dx*dx+dy*dy;
  double t=den==0?0:Math.max(0,Math.min(1,((p.x-a.x)*dx+(p.y-a.y)*dy)/den));
  return Math.hypot(p.x-a.x-t*dx,p.y-a.y-t*dy);
 }
 private void line(Canvas c,PointF a,PointF b){c.drawLine(left()+scale()*a.x,top()+scale()*a.y,left()+scale()*b.x,top()+scale()*b.y,paint);}
 @Override protected void onDraw(Canvas c){
  c.drawColor(Color.WHITE);
  paint.setStrokeWidth(1);paint.setColor(0xffe5eceb);
  for(int i=0;i<=10;i++){
   float f=i/10f;line(c,new PointF(f,0),new PointF(f,1));line(c,new PointF(0,f),new PointF(1,f));
  }
  paint.setTextSize(Math.max(12,scale()*.05f));
  for(int i=1;i<vertices.size();i++){
   PointF a=vertices.get(i-1).point,b=vertices.get(i).point;
   paint.setColor(0xff13665f);paint.setStrokeWidth(4);line(c,a,b);
   c.drawCircle(left()+b.x*scale(),top()+b.y*scale(),4,paint);
   c.drawText(String.valueOf(i),left()+(a.x+b.x)*scale()/2,top()+(a.y+b.y)*scale()/2-5,paint);
  }
  paint.setColor(0xff42799e);paint.setStrokeWidth(3);
  for(int i=1;i<stroke.size();i++)line(c,stroke.get(i-1),stroke.get(i));
 }
 private void finishStroke(){
  if(stroke.size()<2)return;
  boolean[] keep=new boolean[stroke.size()];
  int last=stroke.size()-1;
  keep[0]=keep[last]=true;
  ArrayDeque<int[]> pending=new ArrayDeque<>();
  pending.push(new int[]{0,last});
  while(!pending.isEmpty()){
   int[] span=pending.pop();
   double largest=.023;int split=-1;
   for(int i=span[0]+1;i<span[1];i++){
    double d=distanceToLine(stroke.get(i),stroke.get(span[0]),stroke.get(span[1]));
    if(d>largest){largest=d;split=i;}
   }
   if(split>=0){keep[split]=true;pending.push(new int[]{span[0],split});pending.push(new int[]{split,span[1]});}
  }
  if(last==1 && distance(stroke.get(0),stroke.get(last))<.02)return;
  for(int i=vertices.isEmpty()?0:1;i<=last;i++)
   if(keep[i])vertices.add(new Vertex(stroke.get(i),Double.NaN));
 }
 @Override public boolean onTouchEvent(MotionEvent event){
  if(!editable)return false;
  PointF p=point(event.getX(),event.getY());
  switch(event.getActionMasked()){
   case MotionEvent.ACTION_DOWN:
    getParent().requestDisallowInterceptTouchEvent(true);
    stroke.clear();
    if(!dimensioning)stroke.add(vertices.isEmpty()?p:vertices.get(vertices.size()-1).point);
    return true;
   case MotionEvent.ACTION_MOVE:
    if(!dimensioning && !stroke.isEmpty() && distance(stroke.get(stroke.size()-1),p)>.008){stroke.add(p);invalidate();}
    return true;
   case MotionEvent.ACTION_UP:
    getParent().requestDisallowInterceptTouchEvent(false);
    if(dimensioning){
     int nearest=-1;double best=.085;
     for(int i=1;i<vertices.size();i++){
      double d=distanceToLine(p,vertices.get(i-1).point,vertices.get(i).point);
      if(d<best){best=d;nearest=i;}
     }
     if(nearest>=0 && selected!=null)selected.select(nearest);
    }else if(!stroke.isEmpty()){
     stroke.add(p);finishStroke();stroke.clear();if(changed!=null)changed.run();
    }
    performClick();invalidate();return true;
   case MotionEvent.ACTION_CANCEL:
    getParent().requestDisallowInterceptTouchEvent(false);stroke.clear();invalidate();return true;
   default:return true;
  }
 }
 @Override public boolean performClick(){super.performClick();return true;}
}
