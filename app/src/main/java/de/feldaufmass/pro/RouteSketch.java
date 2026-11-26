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
 static final double GPS_MOVE_ACCURACY_METRES=0.5;
 static boolean preciseGps(double accuracy){return Double.isFinite(accuracy)&&accuracy>0&&accuracy<=GPS_MOVE_ACCURACY_METRES;}
 static final class Vertex {
  final PointF point;
  String id=java.util.UUID.randomUUID().toString();
  JSONArray photos=new JSONArray();
  double latitude=Double.NaN,longitude=Double.NaN,accuracy=Double.NaN;
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
    Vertex v=new Vertex(new PointF((float)row.getDouble(0),(float)row.getDouble(1)),row.optDouble(2,Double.NaN));
    v.id=row.optString(3,v.id);
    if(row.optJSONArray(4)!=null)v.photos=row.optJSONArray(4);
    v.latitude=row.optDouble(5,Double.NaN);v.longitude=row.optDouble(6,Double.NaN);v.accuracy=row.optDouble(7,Double.NaN);
    vertices.add(v);
   }
  }catch(JSONException e){vertices.clear();}
  setContentDescription("Trassenskizze");
 }
 String encode(){
  JSONArray rows=new JSONArray();
  try{
   for(Vertex v:vertices){
    JSONArray row=new JSONArray().put((double)v.point.x).put((double)v.point.y);
    row.put(Double.isFinite(v.metres)?(Object)v.metres:org.json.JSONObject.NULL);
    row.put(v.id).put(v.photos);
    for(double n:new double[]{v.latitude,v.longitude,v.accuracy})row.put(Double.isFinite(n)?(Object)n:org.json.JSONObject.NULL);
    rows.put(row);
   }
  }catch(JSONException e){throw new IllegalStateException(e);}
  return rows.toString();
 }
 /** Similarity transform from two independent GPS anchors, local tangent plane.
  * Returns null when calibration is insufficient or the target is outside the sketch. */
 PointF gpsPosition(double lat,double lon,int excluded){
  if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>85||Math.abs(lon)>180)return null;
  Vertex a=null,b=null;double best=0;
  for(int i=0;i<vertices.size();i++){
   Vertex u=vertices.get(i);
   if(i==excluded||!gpsAnchor(u))continue;
   for(int j=i+1;j<vertices.size();j++){
    Vertex v=vertices.get(j);if(j==excluded||!gpsAnchor(v))continue;
    double d=distance(u.point,v.point);
    if(d>best){best=d;a=u;b=v;}
   }
  }
  if(a==null||best<.03)return null;
  double cos=Math.cos(Math.toRadians(a.latitude));
  double ex=longitudeDelta(b.longitude,a.longitude)*cos*111195,ey=-(b.latitude-a.latitude)*111195;
  double norm=ex*ex+ey*ey;
  if(norm<Math.pow(Math.max(10,3*Math.hypot(a.accuracy,b.accuracy)),2))return null;
  double tx=longitudeDelta(lon,a.longitude)*cos*111195,ty=-(lat-a.latitude)*111195;
  double sx=b.point.x-a.point.x,sy=b.point.y-a.point.y;
  double real=(sx*ex+sy*ey)/norm,imag=(sy*ex-sx*ey)/norm;
  double x=a.point.x+real*tx-imag*ty,y=a.point.y+imag*tx+real*ty;
  if(!Double.isFinite(x)||!Double.isFinite(y)||x<0||x>1||y<0||y>1)return null;
  return new PointF((float)x,(float)y);
 }
 private static double longitudeDelta(double lon,double origin){return ((lon-origin+540)%360)-180;}
 private static boolean gpsAnchor(Vertex v){
  return Double.isFinite(v.latitude)&&Math.abs(v.latitude)<=85&&Double.isFinite(v.longitude)&&Math.abs(v.longitude)<=180&&preciseGps(v.accuracy);
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
 float zoom=1,centerX=.5f,centerY=.5f;
 boolean moving,gesture;
 int dragged=-1,angle=-1,highlight=-1;
 float span,focusX,focusY;
 SegmentListener pointSelected;
 void zoomAt(float factor,float oldX,float oldY,float newX,float newY){
  float x=(oldX-left())/scale(),y=(oldY-top())/scale();
  zoom=Math.max(1,Math.min(8,zoom*factor));
  centerX=clamp(x-(newX-getWidth()/2f)/scale());
  centerY=clamp(y-(newY-getHeight()/2f)/scale());
  if(zoom==1){centerX=.5f;centerY=.5f;}
  invalidate();
 }
 private float scale(){return Math.max(1,Math.min(getWidth(),getHeight())*.88f)*zoom;}
 private float left(){return getWidth()/2f-centerX*scale();}
 private float top(){return getHeight()/2f-centerY*scale();}
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
  float density=getResources().getDisplayMetrics().density;
  paint.setTextSize(13*density);
  for(int i=1;i<vertices.size();i++){
   PointF a=vertices.get(i-1).point,b=vertices.get(i).point;
   paint.setColor(0xff13665f);paint.setStrokeWidth(4);line(c,a,b);
   double metres=vertices.get(i).metres;
   String label=Double.isFinite(metres)?String.format(Locale.GERMANY,"%.2f m",metres):"Maß fehlt";
   float tx=left()+(a.x+b.x)*scale()/2,ty=top()+(a.y+b.y)*scale()/2-6*density;
   paint.setColor(Double.isFinite(metres)?Color.WHITE:0xffffcf73);
   c.drawRoundRect(tx-2*density,ty-14*density,tx+paint.measureText(label)+2*density,ty+3*density,4,4,paint);
   paint.setColor(0xff173c42);c.drawText(label,tx,ty,paint);
  }
  for(int i=0;i<vertices.size();i++){
   PointF p=vertices.get(i).point;
   paint.setColor(i==highlight?0xffdf8c14:0xff13665f);
   c.drawCircle(left()+scale()*p.x,top()+scale()*p.y,5*density,paint);
   paint.setColor(Color.WHITE);c.drawCircle(left()+scale()*p.x,top()+scale()*p.y,2*density,paint);
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
  if(!editable && pointSelected==null)return false;
  int action=event.getActionMasked();
  if(event.getPointerCount()>=2){
   float x=(event.getX(0)+event.getX(1))/2,y=(event.getY(0)+event.getY(1))/2;
   float next=(float)Math.hypot(event.getX(0)-event.getX(1),event.getY(0)-event.getY(1));
   if(gesture && action==MotionEvent.ACTION_MOVE && span>0)zoomAt(next/span,focusX,focusY,x,y);
   gesture=true;span=next;focusX=x;focusY=y;stroke.clear();dragged=-1;invalidate();return true;
  }
  if(action==MotionEvent.ACTION_DOWN)gesture=false;
  if(gesture){
   if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL)getParent().requestDisallowInterceptTouchEvent(false);
   return true;
  }
  PointF p=point(event.getX(),event.getY());
  switch(event.getActionMasked()){
   case MotionEvent.ACTION_DOWN:
    getParent().requestDisallowInterceptTouchEvent(true);
    stroke.clear();
    if(moving || !editable)dragged=nearestPoint(event.getX(),event.getY());
    else if(!dimensioning)stroke.add(vertices.isEmpty()?p:vertices.get(vertices.size()-1).point);
    return true;
   case MotionEvent.ACTION_MOVE:
    if(moving && dragged>=0)movePoint(dragged,p);
    if(!dimensioning && !stroke.isEmpty() && distance(stroke.get(stroke.size()-1),p)>.008){stroke.add(p);invalidate();}
    return true;
   case MotionEvent.ACTION_UP:
    getParent().requestDisallowInterceptTouchEvent(false);
    if(moving || !editable){
     if(dragged>=0){
      if(moving){movePoint(dragged,p);if(changed!=null)changed.run();}
      if(pointSelected!=null)pointSelected.select(dragged);
     }else if(!editable && selected!=null)selectSegment(p);
     dragged=-1;
    }else if(dimensioning){
     selectSegment(p);
    }else if(!stroke.isEmpty()){
     stroke.add(p);finishStroke();stroke.clear();if(changed!=null)changed.run();
    }
    performClick();invalidate();return true;
   case MotionEvent.ACTION_CANCEL:
    getParent().requestDisallowInterceptTouchEvent(false);stroke.clear();invalidate();return true;
   default:return true;
  }
 }
 private void selectSegment(PointF p){
     int nearest=-1;double best=.085/zoom;
     for(int i=1;i<vertices.size();i++){
      double d=distanceToLine(p,vertices.get(i-1).point,vertices.get(i).point);
      if(d<best){best=d;nearest=i;}
     }
     if(nearest>=0 && selected!=null)selected.select(nearest);
 }
 private int nearestPoint(float x,float y){
  double best=20*getResources().getDisplayMetrics().density;int nearest=-1;
  for(int i=0;i<vertices.size();i++){
   PointF p=vertices.get(i).point;double d=Math.hypot(x-left()-p.x*scale(),y-top()-p.y*scale());
   if(d<best){nearest=i;best=d;}
  }
  return nearest;
 }
 void movePoint(int index,PointF target){
  PointF point=vertices.get(index).point;
  if(angle<0 || vertices.size()<2){point.set(target);invalidate();return;}
  PointF anchor=vertices.get(index==0?1:index-1).point;
  double rad=Math.toRadians(angle);
  double dx=Math.copySign(angle==90?0:Math.cos(rad),target.x-anchor.x);
  double dy=Math.copySign(angle==0?0:Math.sin(rad),target.y-anchor.y);
  double length=distance(anchor,target);
  if(dx!=0)length=Math.min(length,(dx>0?1-anchor.x:-anchor.x)/dx);
  if(dy!=0)length=Math.min(length,(dy>0?1-anchor.y:-anchor.y)/dy);
  point.set((float)(anchor.x+length*dx),(float)(anchor.y+length*dy));invalidate();
 }
 @Override public boolean performClick(){super.performClick();return true;}
}
