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
 static final int LAYER_ROUTE=1,LAYER_DIMENSIONS=2,LAYER_NAMES=4,LAYER_GPS=8,LAYER_PHOTOS=16,LAYER_OBJECTS=32,LAYER_SURFACES=64,LAYER_SURFACE_DIMENSIONS=128,LAYER_GPS_TRACK=256,LAYER_GPS_DEVIATIONS=512,LAYER_GPS_REFERENCE=1024,ALL_LAYERS=2047;
 int visibleLayers=ALL_LAYERS;
 boolean layerVisible(int layer){return (visibleLayers&layer)!=0;}
 void setVisibleLayers(int layers){visibleLayers=layers&ALL_LAYERS;invalidate();}
 static final long POINT_CONTEXT_HOLD_MS=2000L;
 static final double GPS_MOVE_ACCURACY_METRES=0.5;
 static boolean preciseGps(double accuracy){return Double.isFinite(accuracy)&&accuracy>0&&accuracy<=GPS_MOVE_ACCURACY_METRES;}
 static final class Vertex {
  final PointF point;
  boolean hasStoredId;
  String id=java.util.UUID.randomUUID().toString();
  String name="";
  JSONArray gpsUndo;
  void rememberGpsMove(){gpsUndo=new JSONArray();for(double n:new double[]{point.x,point.y,latitude,longitude,accuracy})gpsUndo.put(Double.isFinite(n)?(Object)n:org.json.JSONObject.NULL);}
  boolean undoGpsMove(){if(gpsUndo==null||gpsUndo.length()!=5)return false;double x=gpsUndo.optDouble(0,Double.NaN),y=gpsUndo.optDouble(1,Double.NaN);if(!Double.isFinite(x)||!Double.isFinite(y))return false;point.set((float)x,(float)y);latitude=gpsUndo.optDouble(2,Double.NaN);longitude=gpsUndo.optDouble(3,Double.NaN);accuracy=gpsUndo.optDouble(4,Double.NaN);gpsUndo=null;return true;}
  JSONArray photos=new JSONArray();
  org.json.JSONObject photoMetadata=new org.json.JSONObject();
  double latitude=Double.NaN,longitude=Double.NaN,accuracy=Double.NaN;
  double metres; // Length of the incoming segment, NaN if unmeasured.
  Vertex(PointF point,double metres){this.point=point;this.metres=metres;}
 }
 final ArrayList<Vertex> vertices=new ArrayList<>();
 private final ArrayList<PointF> stroke=new ArrayList<>();
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 boolean editable,dimensioning;
 Runnable changed;
 SegmentListener strokeAdded;
 interface SegmentListener {void select(int endpoint);}
 SegmentListener selected;

 RouteSketch(Context context,String data){
  super(context);
  try{
   JSONArray rows=new JSONArray(data);
   for(int i=0;i<rows.length();i++){
    JSONArray row=rows.getJSONArray(i);
    Vertex v=new Vertex(new PointF((float)row.getDouble(0),(float)row.getDouble(1)),row.optDouble(2,Double.NaN));
    v.hasStoredId=row.opt(3) instanceof String && !row.optString(3).trim().isEmpty();
    v.id=row.optString(3,v.id);
    if(row.optJSONArray(4)!=null)v.photos=row.optJSONArray(4);
    v.latitude=row.optDouble(5,Double.NaN);v.longitude=row.optDouble(6,Double.NaN);v.accuracy=row.optDouble(7,Double.NaN);
    v.name=row.optString(8,"");v.gpsUndo=row.optJSONArray(9);if(row.optJSONObject(10)!=null)v.photoMetadata=row.optJSONObject(10);
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
    row.put(v.name);row.put(v.gpsUndo==null?org.json.JSONObject.NULL:v.gpsUndo);if(v.photoMetadata.length()>0)row.put(v.photoMetadata);
    rows.put(row);
   }
  }catch(JSONException e){throw new IllegalStateException(e);}
  return rows.toString();
 }
 /** Similarity transform from two independent GPS anchors, local tangent plane.
  * Returns null when calibration is insufficient or the target is outside the sketch. */
 private static final class GpsTransform{
  final double lat,lon,x,y,cos,real,imag;
  GpsTransform(double lat,double lon,double x,double y,double cos,double real,double imag){this.lat=lat;this.lon=lon;this.x=x;this.y=y;this.cos=cos;this.real=real;this.imag=imag;}
  PointF project(double latitude,double longitude){
   if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||Math.abs(latitude)>85||Math.abs(longitude)>180)return null;
   double tx=longitudeDelta(longitude,lon)*cos*111195,ty=-(latitude-lat)*111195;
   double px=x+real*tx-imag*ty,py=y+imag*tx+real*ty;
   if(!Double.isFinite(px)||!Double.isFinite(py)||px<0||px>1||py<0||py>1)return null;
   return new PointF((float)px,(float)py);
  }
 }
 PointF gpsPosition(double lat,double lon,int excluded){
  GpsTransform transform=gpsTransform(excluded);return transform==null?null:transform.project(lat,lon);
 }
 private GpsTransform gpsTransform(int excluded){
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
  double sx=b.point.x-a.point.x,sy=b.point.y-a.point.y;
  return new GpsTransform(a.latitude,a.longitude,a.point.x,a.point.y,cos,(sx*ex+sy*ey)/norm,(sy*ex-sx*ey)/norm);
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
 double uniformMetresPerUnit(){
  if(vertices.size()<2)return Double.NaN;
  double factor=Double.NaN;
  for(int i=1;i<vertices.size();i++){
   Vertex v=vertices.get(i);double n=distance(vertices.get(i-1).point,v.point);
   if(!Double.isFinite(v.metres)||v.metres<=0||n<1e-8)return Double.NaN;
   double ratio=v.metres/n;if(!Double.isFinite(ratio))return Double.NaN;
   if(i==1)factor=ratio;else if(Math.abs(ratio/factor-1)>.001)return Double.NaN;
  }
  return factor;
 }
 boolean rebuildMeasuredGeometry(){
  if(vertices.size()<2)return false;
  double[] xs=new double[vertices.size()],ys=new double[vertices.size()];
  double minX=0,maxX=0,minY=0,maxY=0;
  for(int i=1;i<vertices.size();i++){
   Vertex a=vertices.get(i-1),b=vertices.get(i);
   double dx=b.point.x-a.point.x,dy=b.point.y-a.point.y,n=Math.hypot(dx,dy);
   if(!Double.isFinite(b.metres)||b.metres<=0||!Double.isFinite(n)||n<1e-8)return false;
   xs[i]=xs[i-1]+dx/n*b.metres;ys[i]=ys[i-1]+dy/n*b.metres;
   if(!Double.isFinite(xs[i])||!Double.isFinite(ys[i]))return false;
   minX=Math.min(minX,xs[i]);maxX=Math.max(maxX,xs[i]);minY=Math.min(minY,ys[i]);maxY=Math.max(maxY,ys[i]);
  }
  double span=Math.max(maxX-minX,maxY-minY);if(!Double.isFinite(span)||span<=0)return false;
  for(int i=0;i<vertices.size();i++)vertices.get(i).point.set((float)(.5+.8*((xs[i]-minX)-(maxX-minX)/2)/span),(float)(.5+.8*((ys[i]-minY)-(maxY-minY)/2)/span));
  zoom=1;centerX=.5f;centerY=.5f;if(changed!=null)changed.run();invalidate();return true;
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
 JSONArray stationObjects=new JSONArray(),surfaceSections=new JSONArray();
 void setStationLayers(JSONArray objects,JSONArray sections){stationObjects=objects;surfaceSections=sections;invalidate();}
 int draggedObject=-1; String objectBefore; float objectGrabX,objectGrabY;
 void cancelObjectDrag(){if(draggedObject>=0&&objectBefore!=null)try{stationObjects.put(draggedObject,new org.json.JSONObject(objectBefore));}catch(Exception ignored){}draggedObject=-1;resizeAxis=0;objectBefore=null;invalidate();}
 void moveStationObject(float x,float y){
  org.json.JSONObject o=stationObjects.optJSONObject(draggedObject);if(o==null)return;
  java.util.ArrayList<StationHit> hits=stationCandidates(x,y,Double.POSITIVE_INFINITY);if(hits.isEmpty())return;
  StationHit h=hits.get(0);Vertex a=vertices.get(h.segment-1),b=vertices.get(h.segment);
  double dx=b.point.x-a.point.x,dy=b.point.y-a.point.y,n=Math.hypot(dx,dy);
  double wid=positiveDimension(o,"detailWidth");
  double widthAcrossRoute=projectedWidth(positiveDimension(o,"detailLength"),wid,objectRotation(o));
  if(n<=0||!Double.isFinite(b.metres)||b.metres<=0)return;
  PointF anchor=stationPoint(h.station);if(anchor==null)return;
  double lateral=((x-anchor.x)*dy-(y-anchor.y)*dx)/n*b.metres/n;
  try{o.put("station",Math.round(h.station*100)/100.0);
   if(Double.isFinite(widthAcrossRoute)&&Double.isFinite(trenchWidth)&&trenchWidth>0){
    double half=(widthAcrossRoute+trenchWidth)/2;
    o.put("side",Math.abs(lateral)<half/2?"mittig":lateral>0?"links":"rechts");
    o.put("detailOffset",Math.round(Math.max(0,Math.abs(lateral)-half)*100)/100.0);
   }
   invalidate();
  }catch(Exception ignored){}
 }
 boolean editingObjects;
 java.util.function.Consumer<java.util.ArrayList<Integer>> objectSelected;
 java.util.ArrayList<Integer> objectCandidates(double x,double y,double tolerance){
  java.util.ArrayList<Integer> hits=new java.util.ArrayList<>();
  if(!layerVisible(LAYER_OBJECTS))return hits;
  for(int j=0;j<stationObjects.length();j++){
   org.json.JSONObject o=stationObjects.optJSONObject(j);if(o==null)continue;
   ObjectFootprint f=objectFootprint(o);if(f==null)continue;
   double dx=x-f.center.x,dy=y-f.center.y,angle=Math.toRadians(f.angle);
   double u=dx*Math.cos(angle)+dy*Math.sin(angle),v=-dx*Math.sin(angle)+dy*Math.cos(angle);
   double distance=f.measured?Math.hypot(Math.max(0,Math.abs(u)-f.length/2),Math.max(0,Math.abs(v)-f.width/2)):Math.hypot(dx,dy);
   if(distance<=tolerance)hits.add(j);
  }return hits;
 }
 boolean placingObject;
 java.util.function.Consumer<java.util.ArrayList<StationHit>> stationSelected;
 static final class StationHit{
  final int segment;final double station,distance;
  StationHit(int segment,double station,double distance){this.segment=segment;this.station=station;this.distance=distance;}
 }
 java.util.ArrayList<StationHit> stationCandidates(double x,double y,double tolerance){
  java.util.ArrayList<StationHit> hits=new java.util.ArrayList<>();
  double total=measuredLength();if(!Double.isFinite(total)||total<=0)return hits;
  double start=0;
  for(int i=1;i<vertices.size();i++){
   Vertex a=vertices.get(i-1),b=vertices.get(i);
   double dx=b.point.x-a.point.x,dy=b.point.y-a.point.y,den=dx*dx+dy*dy;
   if(den>1e-12){
    double f=Math.max(0,Math.min(1,((x-a.point.x)*dx+(y-a.point.y)*dy)/den));
    double distance=Math.hypot(x-a.point.x-f*dx,y-a.point.y-f*dy);
    if(distance<=tolerance)hits.add(new StationHit(i,start+f*b.metres,distance));
   }
   start+=b.metres;
  }
  hits.sort((a,b)->Double.compare(a.distance,b.distance));
  return hits;
 }
 PointF stationPoint(double station){
  double total=measuredLength();
  if(!Double.isFinite(total)||total<=0||!Double.isFinite(station)||station<0||station>total+1e-8)return null;
  double start=0;
  for(int i=1;i<vertices.size();i++){
   Vertex v=vertices.get(i);if(!Double.isFinite(v.metres)||v.metres<=0)return null;
  }
  for(int i=1;i<vertices.size();i++){
   Vertex a=vertices.get(i-1),b=vertices.get(i);double end=start+b.metres;
   if(station<=end+1e-8){float f=(float)Math.max(0,Math.min(1,(station-start)/b.metres));return new PointF(a.point.x+(b.point.x-a.point.x)*f,a.point.y+(b.point.y-a.point.y)*f);}
   start=end;
  }
  return null;
 }
 static int surfaceColor(String name){
  String n=name.toLowerCase(Locale.ROOT);
  if(n.contains("asphalt"))return 0xff646B73;
  if(n.contains("1 × 1")||n.contains("1 x 1"))return 0xff76B5E5;
  if(n.contains("großpflaster"))return 0xff9670AC;
  if(n.contains("kleinpflaster"))return 0xffC5A3D9;
  if(n.contains("platten"))return 0xffE7D5B0;
  if(n.contains("verbund")||n.contains("doppel-t"))return 0xffC66D56;
  if(n.contains("pflaster"))return 0xffD39B60;
  if(n.contains("beton"))return 0xffBFC5CB;
  if(n.contains("schotter"))return 0xffA49B89;
  if(n.contains("kies"))return 0xffE3C96A;
  if(n.contains("sand"))return 0xffF5E9A8;
  if(n.contains("unbefestigt"))return 0xff916B49;
  if(n.contains("rasen"))return 0xff79BC65;
  if(n.contains("grünfläche"))return 0xff367D46;
  if(n.contains("wassergebunden"))return 0xffC7B49B;
  return 0xffD58AB4;
 }
 static int surfacePattern(String name){
  int color=surfaceColor(name);
  int[] colors={0xff646B73,0xffBFC5CB,0xffD39B60,0xff9670AC,0xffC5A3D9,0xffE7D5B0,0xff76B5E5,0xffC66D56,0xffA49B89,0xffE3C96A,0xffF5E9A8,0xff916B49,0xff79BC65,0xff367D46,0xffC7B49B,0xffD58AB4};
  for(int i=0;i<colors.length;i++)if(colors[i]==color)return i;
  return 15;
 }
 private void surfaceHatch(Canvas c,PointF a,PointF b,float density,int pattern){
  float ax=left()+scale()*a.x,ay=top()+scale()*a.y,bx=left()+scale()*b.x,by=top()+scale()*b.y;
  float length=(float)Math.hypot(bx-ax,by-ay);if(length<1)return;
  c.save();c.translate(ax,ay);c.rotate((float)Math.toDegrees(Math.atan2(by-ay,bx-ax)));c.clipRect(0,-6*density,length,6*density);
  drawHatchBand(c,length,density,pattern);
  c.restore();
 }
 void drawHatchBand(Canvas c,float length,float density,int pattern){
  Paint hatch=new Paint(Paint.ANTI_ALIAS_FLAG);hatch.setColor(0xAA202020);hatch.setStrokeWidth(.8f*density);
  float step=(5+pattern%4*3)*density;
  for(float x=-12*density;x<length+12*density;x+=step){
   switch(pattern/4){
    case 0:c.drawLine(x,-6*density,x+6*density,6*density,hatch);break;
    case 1:c.drawLine(x,-6*density,x,6*density,hatch);c.drawLine(x,0,x+step,0,hatch);break;
    case 2:c.drawCircle(x,-3*density,density,hatch);c.drawCircle(x+step/2,3*density,density,hatch);break;
    default:c.drawLine(x,6*density,x+6*density,-6*density,hatch);c.drawLine(x,-6*density,x+6*density,6*density,hatch);break;
   }
  }
 }
 private static class DimensionHit {
  String key; android.graphics.RectF bounds;float nx,ny,offset;
  DimensionHit(String k,android.graphics.RectF b,float x,float y,float o){key=k;bounds=b;nx=x;ny=y;offset=o;}
 }
 private final java.util.ArrayList<DimensionHit> dimensionHits=new java.util.ArrayList<>();
 private final java.util.HashMap<String,Float> dimensionOffsets=new java.util.HashMap<>();
 private String dimensionProject;
 void bindDimensionProject(String project){
  dimensionProject=project;dimensionOffsets.clear();
  try{
   org.json.JSONObject data=new org.json.JSONObject(getContext().getSharedPreferences("dimension_layout",0).getString(project,"{}"));
   java.util.Iterator<String> keys=data.keys();while(keys.hasNext()){String k=keys.next();double v=data.optDouble(k);if(Double.isFinite(v)&&(k.startsWith("route:")?Math.abs(v)<=1000:v>=15&&v<=260))dimensionOffsets.put(k,(float)v);}
  }catch(Exception ignored){}
  invalidate();
 }
 private boolean saveDimensionLayout(){
  if(dimensionProject==null)return false;
  org.json.JSONObject data=new org.json.JSONObject();
  try{for(java.util.Map.Entry<String,Float> e:dimensionOffsets.entrySet())data.put(e.getKey(),e.getValue().doubleValue());}catch(Exception e){return false;}
  boolean ok=getContext().getSharedPreferences("dimension_layout",0).edit().putString(dimensionProject,data.toString()).commit();
  if(!ok)android.widget.Toast.makeText(getContext(),"Maßpositionen konnten nicht gespeichert werden.",0).show();
  return ok;
 }
 void resetDimensionLayout(){
  new android.app.AlertDialog.Builder(getContext()).setTitle("Maßpositionen zurücksetzen?").setMessage("Alle Maßbeschriftungen werden wieder automatisch angeordnet. Messwerte bleiben unverändert.").setNegativeButton("Abbrechen",null).setPositiveButton("Zurücksetzen",(dialog,which)->{java.util.HashMap<String,Float> old=new java.util.HashMap<>(dimensionOffsets);dimensionOffsets.clear();if(!saveDimensionLayout())dimensionOffsets.putAll(old);invalidate();}).show();
 }
 private DimensionHit activeDimension;
 private float dimensionStartX,dimensionStartY;
 private long dimensionDownTime; private boolean dimensionMoved;
 private Float previousDimensionOffset,previousRouteX,previousRouteY;
 private void restoreDimensionDrag(){String k=activeDimension.key;if(k.startsWith("route:")){if(previousRouteX==null)dimensionOffsets.remove(k+":x");else dimensionOffsets.put(k+":x",previousRouteX);if(previousRouteY==null)dimensionOffsets.remove(k+":y");else dimensionOffsets.put(k+":y",previousRouteY);}else if(previousDimensionOffset==null)dimensionOffsets.remove(k);else dimensionOffsets.put(k,previousDimensionOffset);}
 private void showDimensionRotation(String key){
  String k=key+":angle";Float old=dimensionOffsets.get(k);
  android.widget.LinearLayout panel=new android.widget.LinearLayout(getContext());panel.setOrientation(1);int d=(int)(12*getResources().getDisplayMetrics().density);panel.setPadding(d,d,d,d);
  android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(0xff202827);bg.setCornerRadius(d);panel.setBackground(bg);
  android.widget.TextView label=new android.widget.TextView(getContext());label.setTextColor(0xffe3f0ed);panel.addView(label);
  android.widget.SeekBar slider=new android.widget.SeekBar(getContext());slider.setMax(12);slider.setProgress(Math.max(0,Math.min(12,Math.round(((old==null?0:old)+90)/15))));label.setText("Beschriftungswinkel: "+(slider.getProgress()*15-90)+"°");panel.addView(slider);
  android.widget.PopupWindow popup=new android.widget.PopupWindow(panel,Math.min(getWidth(),d*25),android.view.ViewGroup.LayoutParams.WRAP_CONTENT,true);popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));popup.setOutsideTouchable(true);
  slider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
   public void onProgressChanged(android.widget.SeekBar bar,int progress,boolean user){int angle=progress*15-90;label.setText("Beschriftungswinkel: "+angle+"°");dimensionOffsets.put(k,(float)angle);invalidate();}
   public void onStartTrackingTouch(android.widget.SeekBar bar){}
   public void onStopTrackingTouch(android.widget.SeekBar bar){}
  });
  final boolean[] confirmed={false};
  android.widget.LinearLayout actions=new android.widget.LinearLayout(getContext());panel.addView(actions);
  android.widget.Button cancel=new android.widget.Button(getContext());cancel.setText("Abbrechen");cancel.setAllCaps(false);actions.addView(cancel,new android.widget.LinearLayout.LayoutParams(0,-2,1));cancel.setOnClickListener(v->popup.dismiss());
  android.widget.Button reset=new android.widget.Button(getContext());reset.setText("0°");reset.setAllCaps(false);actions.addView(reset,new android.widget.LinearLayout.LayoutParams(0,-2,1));reset.setOnClickListener(v->slider.setProgress(6));
  android.widget.Button done=new android.widget.Button(getContext());done.setText("Übernehmen");done.setAllCaps(false);actions.addView(done,new android.widget.LinearLayout.LayoutParams(0,-2,1));done.setOnClickListener(v->{confirmed[0]=true;popup.dismiss();});
  popup.setOnDismissListener(()->{if(!confirmed[0]||!saveDimensionLayout()){if(old==null)dimensionOffsets.remove(k);else dimensionOffsets.put(k,old);}invalidate();});
  popup.showAtLocation(this,android.view.Gravity.BOTTOM|android.view.Gravity.CENTER_HORIZONTAL,0,d);
 }
 private void drawSurfaceDimensions(Canvas c,float d){
  if(!layerVisible(LAYER_SURFACES)||!layerVisible(LAYER_SURFACE_DIMENSIONS))return;
  java.util.TreeSet<Double> boundaries=new java.util.TreeSet<>();
  for(int j=0;j<surfaceSections.length();j++){
   org.json.JSONObject o=surfaceSections.optJSONObject(j);if(o==null)continue;
   double from=o.optDouble("from"),to=o.optDouble("to");
   if(Double.isFinite(from)&&Double.isFinite(to)&&from<to&&stationPoint(from)!=null&&stationPoint(to)!=null){boundaries.add(from);boundaries.add(to);}
  }
  if(boundaries.size()<2)return;
  // Split dimension geometry at route bends, without modifying actual station data.
  double cumulative=0;
  for(int i=1;i<vertices.size();i++){
   double metres=vertices.get(i).metres;if(!Double.isFinite(metres)||metres<=0)break;
   cumulative+=metres;if(cumulative>boundaries.first()&&cumulative<boundaries.last())boundaries.add(cumulative);
  }
  Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(10*d);p.setStrokeWidth(d);
  java.util.ArrayList<android.graphics.RectF> occupied=new java.util.ArrayList<>();
  Double previous=null;
  for(Double station:boundaries){
   if(previous!=null){
    double mid=(previous+station)/2;boolean covered=false;
    for(int j=0;j<surfaceSections.length();j++){org.json.JSONObject o=surfaceSections.optJSONObject(j);if(o!=null&&o.optDouble("from")<=mid&&o.optDouble("to")>=mid){covered=true;break;}}
    PointF a=stationPoint(previous),b=stationPoint(station);
    if(covered&&a!=null&&b!=null){
     float ax=left()+scale()*a.x,ay=top()+scale()*a.y,bx=left()+scale()*b.x,by=top()+scale()*b.y;
     float len=(float)Math.hypot(bx-ax,by-ay);
     if(len>.1f){
      float ux=(bx-ax)/len,uy=(by-ay)/len,nx=-uy,ny=ux;
      if(ny<0||(Math.abs(ny)<.001f&&nx<0)){nx=-nx;ny=-ny;}
      String label=String.format(Locale.GERMANY,"%.2f m",station-previous);
      float w=p.measureText(label);
      float offset=25*d;
      float halfW=Math.abs(ux)*(w/2+3*d)+Math.abs(uy)*12*d;
      float halfH=Math.abs(uy)*(w/2+3*d)+Math.abs(ux)*12*d;
      android.graphics.RectF bounds=new android.graphics.RectF();
      for(int lane=0;lane<12;lane++){
       offset=(25+lane*19)*d;
       float cx=(ax+bx)/2+nx*offset,cy=(ay+by)/2+ny*offset;
       bounds.set(cx-halfW,cy-halfH,cx+halfW,cy+halfH);
       boolean collision=false;
       for(android.graphics.RectF other:occupied)if(android.graphics.RectF.intersects(bounds,other)){collision=true;break;}
       if(!collision)break;
      }
      String dimensionKey=Double.toString(previous)+":"+Double.toString(station);
      Float manual=dimensionOffsets.get(dimensionKey);
      if(manual!=null){
       offset=manual*d;
       float cx=(ax+bx)/2+nx*offset,cy=(ay+by)/2+ny*offset;
       bounds.set(cx-halfW,cy-halfH,cx+halfW,cy+halfH);
      }
      dimensionHits.add(new DimensionHit(dimensionKey,new android.graphics.RectF(bounds),nx,ny,offset/d));
      occupied.add(new android.graphics.RectF(bounds));
      float x1=ax+nx*offset,y1=ay+ny*offset,x2=bx+nx*offset,y2=by+ny*offset;
      p.setColor(darkPalette()?0xffcbdad7:0xff263238);
      c.drawLine(ax+nx*7*d,ay+ny*7*d,x1+nx*4*d,y1+ny*4*d,p);
      c.drawLine(bx+nx*7*d,by+ny*7*d,x2+nx*4*d,y2+ny*4*d,p);
      c.drawLine(x1,y1,x2,y2,p);
      for(int k=0;k<2;k++){float x=k==0?x1:x2,y=k==0?y1:y2;c.drawLine(x-(ux+nx)*3*d,y-(uy+ny)*3*d,x+(ux+nx)*3*d,y+(uy+ny)*3*d,p);}
      float tx=(x1+x2)/2,ty=(y1+y2)/2;
      float angle=(float)Math.toDegrees(Math.atan2(uy,ux));if(angle>90)angle-=180;if(angle<-90)angle+=180;
      c.save();c.translate(tx,ty);c.rotate(angle);
      p.setColor(darkPalette()?0xff202827:Color.WHITE);c.drawRect(-w/2-2*d,-11*d,w/2+2*d,3*d,p);
      p.setColor(darkPalette()?0xffcbdad7:0xff263238);c.drawText(label,-w/2,0,p);c.restore();
     }
    }
   }
   previous=station;
  }
 }
 int configuredHatch(String name,org.json.JSONObject settings){
  org.json.JSONObject style=settings.optJSONObject(name.trim());int hatch=style==null?0:style.optInt("hatch",0);
  return hatch==17?-1:hatch>=1&&hatch<=16?hatch-1:surfacePattern(name);
 }
 int configuredSurfaceColor(String name,org.json.JSONObject settings){
  org.json.JSONObject item=settings.optJSONObject(name.trim());
  if(item!=null)try{return Color.parseColor(item.optString("color"));}catch(Exception ignored){}
  return surfaceColor(name);
 }
 void drawShoring(Canvas c,float density){
  if(!layerVisible(LAYER_ROUTE))return;
  try{
   JSONArray rows=new JSONArray(((android.app.Activity)getContext()).getPreferences(0).getString("metricVerbauSections","[]"));
   int color=darkPalette()?0xffe6a45e:0xffa65e19;org.json.JSONObject style=legendSettings().optJSONObject("Verbau");if(style!=null)try{color=Color.parseColor(style.optString("color"));}catch(Exception ignored){}paint.setColor(color);paint.setStrokeWidth(3*density);
   for(int j=0;j<rows.length();j++){
    JSONArray row=rows.optJSONArray(j);if(row==null)continue;
    double from=row.optDouble(0),to=row.optDouble(1);
    if(!Double.isFinite(from)||!Double.isFinite(to)||from>=to||stationPoint(from)==null||stationPoint(to)==null)continue;
    double start=0;
    for(int i=1;i<vertices.size();i++){
     double end=start+vertices.get(i).metres,lo=Math.max(from,start),hi=Math.min(to,end);
     if(hi>lo){PointF a=stationPoint(lo),b=stationPoint(hi);if(a!=null&&b!=null){
      float dx=b.x-a.x,dy=b.y-a.y;double norm=Math.hypot(dx,dy);
      if(norm>0){float offset=9*density,ox=(float)(dy/norm)*offset,oy=(float)(-dx/norm)*offset;
       float ax=left()+scale()*a.x,ay=top()+scale()*a.y,bx=left()+scale()*b.x,by=top()+scale()*b.y;
       if(row.optDouble(2,0)>0)c.drawLine(ax+ox,ay+oy,bx+ox,by+oy,paint);
       if(row.optDouble(3,0)>0)c.drawLine(ax-ox,ay-oy,bx-ox,by-oy,paint);
      }
     }}start=end;
    }
   }
  }catch(Exception ignored){}
 }
 void drawSurfaceSections(Canvas c,float density){
  if(!layerVisible(LAYER_SURFACES))return;
  org.json.JSONObject settings=legendSettings();
  for(int j=0;j<surfaceSections.length();j++){
   org.json.JSONObject section=surfaceSections.optJSONObject(j);if(section==null)continue;
   double from=section.optDouble("from"),to=section.optDouble("to");
   if(from>=to||stationPoint(from)==null||stationPoint(to)==null)continue;
   paint.setColor(configuredSurfaceColor(section.optString("surface"),settings));paint.setStrokeWidth(12*density);
   double start=0;
   for(int i=1;i<vertices.size();i++){
    double end=start+vertices.get(i).metres,lo=Math.max(from,start),hi=Math.min(to,end);
    if(hi>lo){PointF a=stationPoint(lo),b=stationPoint(hi);if(a!=null&&b!=null){line(c,a,b);int hatch=configuredHatch(section.optString("surface"),settings);if(hatch>=0)surfaceHatch(c,a,b,density,hatch);}}
    start=end;
   }
  }
 }
 double trenchWidth=Double.NaN;
 void setTrenchWidth(double width){trenchWidth=width;invalidate();}
 static double positiveDimension(org.json.JSONObject o,String key){
  try{double v=Double.parseDouble(o.optString(key).replace(',','.'));return Double.isFinite(v)&&v>0?v:Double.NaN;}catch(Exception e){return Double.NaN;}
 }
 static double lateralGap(org.json.JSONObject o){
  try{double v=Double.parseDouble(o.optString("detailOffset").replace(',','.'));return Double.isFinite(v)&&v>=0?v:Double.NaN;}catch(Exception e){return Double.NaN;}
 }
 static final class ObjectFootprint{
  final PointF anchor,center;final float angle,length,width;final boolean measured,sideLocated;
  ObjectFootprint(PointF anchor,PointF center,float angle,float length,float width,boolean measured,boolean sideLocated){
   this.anchor=anchor;this.center=center;this.angle=angle;this.length=length;this.width=width;this.measured=measured;this.sideLocated=sideLocated;
  }
 }
 private static double objectRotation(org.json.JSONObject object){
  double angle=object.optDouble("detailRotation",0);
  return Double.isFinite(angle)?angle:0;
 }
 private static double projectedWidth(double length,double width,double rotation){
  if(!Double.isFinite(length))return width;
  double radians=Math.toRadians(rotation);
  return Math.abs(length*Math.sin(radians))+Math.abs(width*Math.cos(radians));
 }
 ObjectFootprint objectFootprint(org.json.JSONObject o){
  double station=o.optDouble("station");PointF anchor=stationPoint(station);if(anchor==null)return null;
  double start=0;
  for(int i=1;i<vertices.size();i++){
   Vertex a=vertices.get(i-1),b=vertices.get(i);double end=start+b.metres;
   if(station<=end+1e-8){
    double dx=b.point.x-a.point.x,dy=b.point.y-a.point.y,n=Math.hypot(dx,dy);
    if(n<=1e-8)return new ObjectFootprint(anchor,anchor,0,0,0,false,false);
    double unit=n/b.metres,len=positiveDimension(o,"detailLength"),wid=positiveDimension(o,"detailWidth");
    String side=o.optString("side");int sign="links".equals(side)?1:"rechts".equals(side)?-1:0;
    boolean measured=Double.isFinite(len)&&Double.isFinite(wid)&&Double.isFinite(unit*len)&&Double.isFinite(unit*wid)&&unit*len<=Float.MAX_VALUE&&unit*wid<=Float.MAX_VALUE;
    boolean located=sign==0?"mittig".equals(side):measured&&Double.isFinite(trenchWidth)&&trenchWidth>0;
    double rotation=objectRotation(o);
    double projectedWidth=projectedWidth(len,wid,rotation);
    double gap=lateralGap(o);
    double shift=located&&sign!=0?sign*((trenchWidth+projectedWidth)/2+(Double.isFinite(gap)?gap:0))*unit:0;
    double cx=anchor.x+dy/n*shift,cy=anchor.y-dx/n*shift;
    if(!Double.isFinite(cx)||!Double.isFinite(cy)||Math.abs(cx)>Float.MAX_VALUE||Math.abs(cy)>Float.MAX_VALUE){cx=anchor.x;cy=anchor.y;located=false;}
    return new ObjectFootprint(anchor,new PointF((float)cx,(float)cy),(float)(Math.toDegrees(Math.atan2(dy,dx))+rotation),measured?(float)(len*unit):0,measured?(float)(wid*unit):0,measured,located);
   }
   start=end;
  }
  return null;
 }
 int selectedResizeObject=-1,resizeAxis=0;
 void resizeStationObject(float x,float y){
  org.json.JSONObject o=stationObjects.optJSONObject(draggedObject);if(o==null)return;
  try{org.json.JSONObject original=new org.json.JSONObject(objectBefore);ObjectFootprint f=objectFootprint(original);if(f==null||!f.measured)return;
   double angle=Math.toRadians(f.angle),dx=x-f.center.x,dy=y-f.center.y;
   if(resizeAxis==3){double base=f.angle-original.optDouble("detailRotation",0);double target=Math.toDegrees(Math.atan2(dy,dx))+90-base;target=((target+180)%360+360)%360-180;o.put("detailRotation",Math.round(target));o.remove("acceptedOverlap");invalidate();return;}
   double span=resizeAxis==1?f.length:f.width,metres=positiveDimension(original,resizeAxis==1?"detailLength":"detailWidth");
   if(span<=0||!Double.isFinite(metres))return;
   double projected=resizeAxis==1?dx*Math.cos(angle)+dy*Math.sin(angle):-dx*Math.sin(angle)+dy*Math.cos(angle);
   double value=Math.max(.01,Math.round(2*Math.abs(projected)*metres/span*100)/100.0);
   o.put(resizeAxis==1?"detailLength":"detailWidth",value);o.remove("acceptedOverlap");invalidate();
  }catch(Exception ignored){}
 }
 int resizeHandle(ObjectFootprint f,float x,float y){
  if(f==null||!f.measured)return 0;double angle=Math.toRadians(f.angle),dx=x-f.center.x,dy=y-f.center.y;
  double u=dx*Math.cos(angle)+dy*Math.sin(angle),v=-dx*Math.sin(angle)+dy*Math.cos(angle),tol=12*getResources().getDisplayMetrics().density/scale();
  if(Math.hypot(u,v+f.width/2+24*getResources().getDisplayMetrics().density/scale())<tol)return 3;
  if(Math.hypot(Math.abs(u)-f.length/2,v)<tol)return 1;
  if(Math.hypot(u,Math.abs(v)-f.width/2)<tol)return 2;return 0;
 }
 void drawStationObjects(Canvas c,float density){
  if(!layerVisible(LAYER_OBJECTS))return;
  paint.setTextSize(11*density);
  for(int j=0;j<stationObjects.length();j++){
   org.json.JSONObject o=stationObjects.optJSONObject(j);if(o==null)continue;
   ObjectFootprint f=objectFootprint(o);if(f==null)continue;
   float x=left()+scale()*f.center.x,y=top()+scale()*f.center.y;
   float ax=left()+scale()*f.anchor.x,ay=top()+scale()*f.anchor.y;
   paint.setColor(0xffa65e19);paint.setStrokeWidth(density);c.drawLine(ax,ay,x,y,paint);
   if(f.measured){
    float hw=f.length*scale()/2,hh=f.width*scale()/2;
    if(Float.isFinite(hw)&&Float.isFinite(hh)){
     c.save();c.translate(x,y);c.rotate(f.angle);
     paint.setColor(0x66d38a35);c.drawRect(-hw,-hh,hw,hh,paint);
     paint.setColor(0xffa65e19);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(density);c.drawRect(-hw,-hh,hw,hh,paint);paint.setStyle(Paint.Style.FILL);if(j==selectedResizeObject&&(moving||editingObjects)){paint.setColor(0xff69cdbb);for(int sign:new int[]{-1,1}){c.drawCircle(sign*hw,0,4*density,paint);c.drawCircle(0,sign*hh,4*density,paint);}paint.setStrokeWidth(density);c.drawLine(0,-hh,0,-hh-24*density,paint);c.drawCircle(0,-hh-24*density,5*density,paint);}c.restore();
    }
   }
   paint.setColor(0xffa65e19);c.drawCircle(x,y,3*density,paint);
   String label=o.optString("type","Objekt")+" · "+String.format(Locale.GERMANY,"%.2f m",o.optDouble("station"));
   String side=o.optString("side");if("links".equals(side)||"rechts".equals(side))label+=" · "+side+(f.sideLocated?(Double.isFinite(lateralGap(o))?String.format(Locale.GERMANY," · Abstand %.2f m",lateralGap(o)):"*"):" (Lage offen)");
   if(f.measured)label+=String.format(Locale.GERMANY," · %.2f × %.2f m",positiveDimension(o,"detailLength"),positiveDimension(o,"detailWidth"));
   float tx=x+6*density,ty=y-13*density;
   paint.setColor(darkPalette()?0xff202827:Color.WHITE);c.drawRect(tx-2*density,ty-11*density,tx+paint.measureText(label)+2*density,ty+3*density,paint);
   paint.setColor(darkPalette()?0xfff0b575:0xff7c4514);c.drawText(label,tx,ty,paint);
  }
  paint.setTextSize(13*density);
 }
 private android.location.Location liveFix;
 JSONArray independentGpsTrack=new JSONArray();
 String gpsTrackComparison(){
  if(vertices.size()<2||independentGpsTrack.length()<2)return "Mindestens zwei Punkte je Trasse erforderlich.";
  java.util.ArrayList<PointF> gps=new java.util.ArrayList<>();
  double gpsLength=0,planLength=0;
  org.json.JSONObject last=null;
  GpsTransform transform=gpsTransform(-1);
  if(transform==null)return "GPS-Kalibrierung fehlt.";
  for(int i=0;i<independentGpsTrack.length();i++){
   org.json.JSONObject row=independentGpsTrack.optJSONObject(i);
   if(row==null)return "Ungültige GPS-Spur.";
   PointF p=transform.project(row.optDouble("lat"),row.optDouble("lon"));
   if(p==null)return "GPS-Spur nicht vollständig kalibriert oder außerhalb des Plans.";
   if(last!=null){float[] d=new float[1];android.location.Location.distanceBetween(last.optDouble("lat"),last.optDouble("lon"),row.optDouble("lat"),row.optDouble("lon"),d);gpsLength+=d[0];planLength+=distance(gps.get(gps.size()-1),p);}
   gps.add(p);last=row;
  }
  if(planLength<=0||gpsLength<=0)return "GPS-Spur zu kurz.";
  double factor=gpsLength/planLength;
  java.util.ArrayList<PointF> manual=new java.util.ArrayList<>();for(Vertex v:vertices)manual.add(v.point);
  double[] forward=sampleDeviation(gps,manual,factor),reverse=sampleDeviation(manual,gps,factor);
  return String.format(Locale.GERMANY,"GPS → manuell: Ø %.2f m · Max. %.2f m\nManuell → GPS: Ø %.2f m · Max. %.2f m\nGPS-Länge: %.2f m\nStichproben alle ca. 1 m (max. 10000 je Richtung). Näherungswerte, kein Vermessungsnachweis.",forward[0],forward[1],reverse[0],reverse[1],gpsLength);
 }
 private double[] sampleDeviation(java.util.List<PointF> from,java.util.List<PointF> to,double factor){
  double length=0;for(int i=1;i<from.size();i++)length+=distance(from.get(i-1),from.get(i));
  double step=Math.max(1/factor,length/9999);double sum=0,max=0;int count=0;double next=0,offset=0;
  for(int i=1;i<from.size();i++){
   PointF a=from.get(i-1),b=from.get(i);double len=distance(a,b);if(len<=0)continue;
   while(next<=offset+len&&count<10000){
    double f=Math.max(0,Math.min(1,(next-offset)/len));PointF p=new PointF((float)(a.x+f*(b.x-a.x)),(float)(a.y+f*(b.y-a.y)));
    double best=Double.POSITIVE_INFINITY;for(int j=1;j<to.size();j++)best=Math.min(best,distanceToLine(p,to.get(j-1),to.get(j)));
    best*=factor;sum+=best;max=Math.max(max,best);count++;next+=step;
   }offset+=len;
  }
  return new double[]{count==0?0:sum/count,max};
 }
 void setIndependentGpsTrack(JSONArray rows){independentGpsTrack=rows==null?new JSONArray():rows;deviationKey=null;projectedGpsKey=null;invalidate();}
 private static final class GpsDeviationLine{
  final PointF a,b;final int color;final boolean reverse;
  GpsDeviationLine(PointF a,PointF b,int color,boolean reverse){this.a=a;this.b=b;this.color=color;this.reverse=reverse;}
 }
 private String deviationKey="";
 private final java.util.ArrayList<GpsDeviationLine> deviationCache=new java.util.ArrayList<>();
 private String gpsGeometryKey(){
  StringBuilder key=new StringBuilder();
  for(Vertex v:vertices)key.append(v.point.x).append(',').append(v.point.y).append(',').append(v.latitude).append(',').append(v.longitude).append(',').append(v.accuracy).append(';');
  return key.toString();
 }
 void drawGpsDeviations(Canvas c,float density){
  if(!layerVisible(LAYER_GPS_DEVIATIONS))return;
  String key=gpsGeometryKey();
  if(!key.equals(deviationKey)){deviationCache.clear();rebuildGpsDeviations();deviationKey=key;}
  paint.setStrokeWidth(1.5f*density);
  android.graphics.DashPathEffect dash=new android.graphics.DashPathEffect(new float[]{4*density,3*density},0);
  try{for(GpsDeviationLine item:deviationCache){paint.setPathEffect(item.reverse?dash:null);paint.setColor(item.color);line(c,item.a,item.b);}}finally{paint.setPathEffect(null);}
 }
 private void rebuildGpsDeviations(){
  if(!layerVisible(LAYER_GPS_DEVIATIONS)||vertices.size()<2||independentGpsTrack.length()<2)return;
  java.util.ArrayList<PointF> points=new java.util.ArrayList<>();
  double metres=0,span=0;org.json.JSONObject last=null;
  GpsTransform transform=gpsTransform(-1);
  if(transform==null)return;
  for(int i=0;i<independentGpsTrack.length();i++){
   org.json.JSONObject row=independentGpsTrack.optJSONObject(i);if(row==null)return;
   PointF p=transform.project(row.optDouble("lat"),row.optDouble("lon"));if(p==null)return;
   if(last!=null){float[] d=new float[1];android.location.Location.distanceBetween(last.optDouble("lat"),last.optDouble("lon"),row.optDouble("lat"),row.optDouble("lon"),d);metres+=d[0];span+=distance(points.get(points.size()-1),p);}
   points.add(p);last=row;
  }
  if(span<=0||metres<=0)return;
  double factor=metres/span;
  java.util.ArrayList<PointF> manual=new java.util.ArrayList<>();
  for(Vertex v:vertices)manual.add(v.point);
  cacheDeviationDirection(points,manual,factor,false);
  cacheDeviationDirection(manual,points,factor,true);
 }
 private void cacheDeviationDirection(java.util.List<PointF> from,java.util.List<PointF> to,double factor,boolean reverse){
  double length=0;for(int i=1;i<from.size();i++)length+=distance(from.get(i-1),from.get(i));
  if(length<=0)return;
  double step=Math.max(1/factor,length/99),next=0,offset=0;int count=0;

   for(int i=1;i<from.size();i++){
    PointF start=from.get(i-1),end=from.get(i);double len=distance(start,end);if(len<=0)continue;
    while(next<=offset+len&&count<100){
     double f=Math.max(0,Math.min(1,(next-offset)/len));
     PointF p=new PointF((float)(start.x+f*(end.x-start.x)),(float)(start.y+f*(end.y-start.y)));
     PointF nearest=null;double best=Double.POSITIVE_INFINITY;
     for(int j=1;j<to.size();j++){
      PointF a=to.get(j-1),b=to.get(j);double dx=b.x-a.x,dy=b.y-a.y,den=dx*dx+dy*dy;
      double t=den==0?0:Math.max(0,Math.min(1,((p.x-a.x)*dx+(p.y-a.y)*dy)/den));
      double qx=a.x+t*dx,qy=a.y+t*dy;double d=Math.hypot(p.x-qx,p.y-qy);
      if(d<best){best=d;nearest=new PointF((float)qx,(float)qy);}
     }
     if(nearest!=null){double m=best*factor;deviationCache.add(new GpsDeviationLine(p,nearest,m<=1?0xff69cdbb:m<=3?0xffe9b63c:0xffef5350,reverse));}
     next+=step;count++;
    }
    offset+=len;
   }
 }
 private String projectedGpsKey=null;
 private final java.util.ArrayList<PointF> projectedGpsCache=new java.util.ArrayList<>();
 private JSONArray importedGpsReference=new JSONArray();
 private String referenceGpsKey=null;
 private final java.util.ArrayList<PointF> referenceGpsCache=new java.util.ArrayList<>();
 void setImportedGpsReference(JSONArray rows){importedGpsReference=rows==null?new JSONArray():rows;referenceGpsKey=null;invalidate();}
 void drawImportedGpsReference(Canvas c,float density){
  if(!layerVisible(LAYER_GPS_REFERENCE)||importedGpsReference.length()==0)return;
  String key=gpsGeometryKey();
  if(!key.equals(referenceGpsKey)){
   referenceGpsCache.clear();GpsTransform transform=gpsTransform(-1);
   if(transform!=null)for(int i=0;i<importedGpsReference.length();i++){org.json.JSONObject row=importedGpsReference.optJSONObject(i);referenceGpsCache.add(row==null?null:transform.project(row.optDouble("lat"),row.optDouble("lon")));}
   referenceGpsKey=key;
  }
  PointF previous=null;paint.setColor(0xffeab54d);paint.setStrokeWidth(2.5f*density);
  android.graphics.PathEffect old=paint.getPathEffect();paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{7*density,4*density},0));
  try{for(PointF p:referenceGpsCache){if(p!=null&&previous!=null)line(c,previous,p);previous=p;}}finally{paint.setPathEffect(old);}
 }
 void drawIndependentGpsTrack(Canvas c,float density){
  if(!layerVisible(LAYER_GPS_TRACK)||independentGpsTrack.length()==0)return;
  String key=gpsGeometryKey();
  if(!key.equals(projectedGpsKey)){
   projectedGpsCache.clear();
   GpsTransform transform=gpsTransform(-1);
   if(transform!=null)for(int i=0;i<independentGpsTrack.length();i++){
    org.json.JSONObject row=independentGpsTrack.optJSONObject(i);
    projectedGpsCache.add(row==null?null:transform.project(row.optDouble("lat"),row.optDouble("lon")));
   }
   projectedGpsKey=key;
  }
  PointF previous=null;paint.setColor(0xff35b9ff);paint.setStrokeWidth(3*density);
  for(PointF p:projectedGpsCache){
   if(p!=null){if(previous!=null)line(c,previous,p);c.drawCircle(left()+scale()*p.x,top()+scale()*p.y,3*density,paint);}
   previous=p;
  }
 }
 void setLiveFix(android.location.Location fix){liveFix=fix;invalidate();}
 float zoom=1,centerX=.5f,centerY=.5f;
 boolean moving,gesture;
 private int tappedPoint=-1;
 private float touchStartX,touchStartY,lastTouchX,lastTouchY;
 private long touchStartTime;
 private boolean touchMoved;
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
 private float scale(){return Math.max(1,(darkEditor?Math.max(getWidth(),getHeight()):Math.min(getWidth(),getHeight())*.88f))*zoom;}
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
 static double calibrationFactor(Vertex a,Vertex b,double known){
  if(a==null||b==null||!Double.isFinite(known)||known<=0)return Double.NaN;
  double span=Math.hypot(a.point.x-b.point.x,a.point.y-b.point.y);
  return span>=.01&&Double.isFinite(known/span)?known/span:Double.NaN;
 }
 double estimatedSegment(int endpoint,double factor){
  if(endpoint<1||endpoint>=vertices.size()||!Double.isFinite(factor)||factor<=0)return Double.NaN;
  Vertex a=vertices.get(endpoint-1),b=vertices.get(endpoint);
  double v=Math.hypot(a.point.x-b.point.x,a.point.y-b.point.y)*factor;
  return Double.isFinite(v)&&v>0?v:Double.NaN;
 }
 private android.graphics.Bitmap underlay;
 private int underlayAlpha=150;
 private float underlayX=0,underlayY=0,underlayScale=1,underlayRotation=0;
 void setUnderlayTransform(float x,float y,float zoom,float rotation){
  underlayX=Float.isFinite(x)?Math.max(-2f,Math.min(2f,x)):0f;
  underlayY=Float.isFinite(y)?Math.max(-2f,Math.min(2f,y)):0f;
  underlayScale=Float.isFinite(zoom)?Math.max(.1f,Math.min(5f,zoom)):1f;
  underlayRotation=Float.isFinite(rotation)?Math.max(-180f,Math.min(180f,rotation)):0f;
  invalidate();
 }
 private boolean underlayVisible=true;
 private final Paint underlayPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
 void setUnderlay(android.graphics.Bitmap bitmap){underlay=bitmap;invalidate();}
 void setUnderlayAlpha(int alpha){underlayAlpha=Math.max(0,Math.min(255,alpha));invalidate();}
 void setUnderlayVisible(boolean visible){underlayVisible=visible;invalidate();}
 void drawUnderlay(Canvas c){
  if(underlay==null||underlay.isRecycled()||!underlayVisible)return;
  // Use the same normalized world coordinates as route vertices in every viewport.
  float side=scale(),factor=side/Math.max(underlay.getWidth(),underlay.getHeight());
  float w=underlay.getWidth()*factor,h=underlay.getHeight()*factor;
  float x=left()+(side-w)/2,y=top()+(side-h)/2;
  underlayPaint.setAlpha(underlayAlpha);
  c.save();
  c.translate(underlayX*side,underlayY*side);
  c.rotate(underlayRotation,x+w/2,y+h/2);
  c.scale(underlayScale,underlayScale,x+w/2,y+h/2);
  c.drawBitmap(underlay,null,new android.graphics.RectF(x,y,x+w,y+h),underlayPaint);
  c.restore();
 }
 java.util.LinkedHashMap<String,Integer> legendEntries(){
  java.util.LinkedHashMap<String,Integer> entries=new java.util.LinkedHashMap<>();
  if(layerVisible(LAYER_ROUTE)&&vertices.size()>1)entries.put("Trasse",0xff13665f);
  if(layerVisible(LAYER_ROUTE))try{JSONArray rows=new JSONArray(((android.app.Activity)getContext()).getPreferences(0).getString("metricVerbauSections","[]"));for(int j=0;j<rows.length();j++){JSONArray row=rows.optJSONArray(j);if(row!=null&&row.optDouble(0)<row.optDouble(1)&&stationPoint(row.optDouble(0))!=null&&stationPoint(row.optDouble(1))!=null&&(row.optDouble(2,0)>0||row.optDouble(3,0)>0)){entries.put("Verbau",darkPalette()?0xffe6a45e:0xffa65e19);break;}}}catch(Exception ignored){}
  if(layerVisible(LAYER_SURFACES))for(int j=0;j<surfaceSections.length();j++){
   org.json.JSONObject o=surfaceSections.optJSONObject(j);if(o==null)continue;
   String name=o.optString("surface").trim();if(!name.isEmpty())entries.put(name,surfaceColor(name));
  }
  if(layerVisible(LAYER_OBJECTS))for(int j=0;j<stationObjects.length();j++){
   org.json.JSONObject o=stationObjects.optJSONObject(j);if(o!=null)entries.put(o.optString("type","Objekt"),0xffa65e19);
  }
  for(Vertex v:vertices){
   if(layerVisible(LAYER_GPS)&&Double.isFinite(v.latitude)&&Double.isFinite(v.longitude))entries.put("GPS-Punkt",0xff4285ef);
   if(layerVisible(LAYER_PHOTOS)&&v.photos.length()>0)entries.put("Foto",0xff536c80);
  }
  return entries;
 }
 org.json.JSONObject legendSettings(){
  try{return new org.json.JSONObject(getContext().getSharedPreferences("project_legend",0).getString(dimensionProject==null?"default":dimensionProject,"{}"));}catch(Exception e){return new org.json.JSONObject();}
 }
 void editLegend(){
  java.util.LinkedHashMap<String,Integer> entries=legendEntries();
  if(entries.isEmpty())return;
  String[] keys=entries.keySet().toArray(new String[0]);
  new android.app.AlertDialog.Builder(getContext()).setTitle("Legende bearbeiten").setItems(keys,(dialog,index)->{
   String key=keys[index];org.json.JSONObject settings=legendSettings(),old=settings.optJSONObject(key);
   android.widget.LinearLayout form=new android.widget.LinearLayout(getContext());form.setOrientation(1);form.setPadding(24,12,24,12);
   android.widget.EditText name=new android.widget.EditText(getContext());name.setSingleLine(true);name.setHint("Bezeichnung");name.setText(old==null?key:old.optString("name",key));form.addView(name);
   android.widget.EditText color=new android.widget.EditText(getContext());color.setSingleLine(true);color.setHint("#RRGGBB");color.setText(old==null?String.format(Locale.ROOT,"#%06X",entries.get(key)&0xffffff):old.optString("color",String.format(Locale.ROOT,"#%06X",entries.get(key)&0xffffff)));form.addView(color);
   android.widget.LinearLayout palette=new android.widget.LinearLayout(getContext());palette.setOrientation(1);form.addView(palette);
   int[] choices={0xff646B73,0xffBFC5CB,0xffD39B60,0xff9670AC,0xffC5A3D9,0xffE7D5B0,0xff76B5E5,0xffC66D56,0xffA49B89,0xffE3C96A,0xffF5E9A8,0xff916B49,0xff79BC65,0xff367D46,0xffC7B49B,0xffD58AB4};
   for(int row=0;row<2;row++){
    android.widget.LinearLayout strip=new android.widget.LinearLayout(getContext());palette.addView(strip);
    for(int col=0;col<8;col++){
     int choice=choices[row*8+col];String hex=String.format(Locale.ROOT,"#%06X",choice&0xffffff);
     android.widget.Button swatch=new android.widget.Button(getContext());swatch.setBackgroundColor(choice);swatch.setContentDescription(hex);
     android.widget.LinearLayout.LayoutParams lp=new android.widget.LinearLayout.LayoutParams(0,(int)(36*getResources().getDisplayMetrics().density),1);lp.setMargins(2,2,2,2);strip.addView(swatch,lp);swatch.setOnClickListener(v->color.setText(hex));
    }
   }
   android.widget.Spinner hatchChoice=new android.widget.Spinner(getContext());
   String[] hatchNames={"Automatisch","Diagonal – eng","Diagonal – mittel","Diagonal – weit","Diagonal – sehr weit","Raster – eng","Raster – mittel","Raster – weit","Raster – sehr weit","Punkte – eng","Punkte – mittel","Punkte – weit","Punkte – sehr weit","Kreuz – eng","Kreuz – mittel","Kreuz – weit","Kreuz – sehr weit","Ohne Schraffur"};
   android.widget.TextView hatchLabel=new android.widget.TextView(getContext());hatchLabel.setText("Schraffur");form.addView(hatchLabel);
   hatchChoice.setAdapter(new android.widget.ArrayAdapter<String>(getContext(),android.R.layout.simple_spinner_dropdown_item,hatchNames));
   hatchChoice.setSelection(Math.max(0,Math.min(17,old==null?0:old.optInt("hatch",0))));form.addView(hatchChoice);
   android.widget.EditText order=new android.widget.EditText(getContext());order.setInputType(2);order.setHint("Reihenfolge");order.setText(String.valueOf(old==null?index:old.optInt("order",index)));form.addView(order);
   android.widget.CheckBox visible=new android.widget.CheckBox(getContext());visible.setText("In Legende anzeigen");visible.setChecked(old==null||old.optBoolean("visible",true));form.addView(visible);
   android.app.AlertDialog edit=new android.app.AlertDialog.Builder(getContext()).setTitle(key).setView(form).setNegativeButton("Abbrechen",null).setNeutralButton("Standard",(x,y)->{settings.remove(key);saveLegend(settings);}).setPositiveButton("Übernehmen",null).create();
   edit.setOnShowListener(x->edit.getButton(-1).setOnClickListener(v->{
    try{String label=name.getText().toString().trim(),hex=color.getText().toString().trim();if(label.isEmpty()){name.setError("Bezeichnung eingeben");return;}if(!hex.matches("#[0-9a-fA-F]{6}")){color.setError("#RRGGBB eingeben");return;}
     org.json.JSONObject item=new org.json.JSONObject();item.put("name",label);item.put("color",hex);item.put("hatch",hatchChoice.getSelectedItemPosition());item.put("order",Integer.parseInt(order.getText().toString()));item.put("visible",visible.isChecked());settings.put(key,item);if(saveLegend(settings))edit.dismiss();
    }catch(Exception e){order.setError("Gültige Reihenfolge eingeben");}
   }));edit.show();
  }).setNegativeButton("Schließen",null).show();
 }
 private boolean saveLegend(org.json.JSONObject settings){
  boolean ok=getContext().getSharedPreferences("project_legend",0).edit().putString(dimensionProject==null?"default":dimensionProject,settings.toString()).commit();
  if(ok)invalidate();else android.widget.Toast.makeText(getContext(),"Legende konnte nicht gespeichert werden.",0).show();return ok;
 }
 private void drawAutomaticLegend(Canvas c,float d){
  java.util.LinkedHashMap<String,Integer> raw=legendEntries(),entries=new java.util.LinkedHashMap<>();
  java.util.HashMap<String,Integer> legendHatches=new java.util.HashMap<>();
  java.util.HashSet<String> surfaceNames=new java.util.HashSet<>();
  for(int j=0;j<surfaceSections.length();j++){org.json.JSONObject o=surfaceSections.optJSONObject(j);if(o!=null)surfaceNames.add(o.optString("surface").trim());}
  org.json.JSONObject settings=legendSettings();
  java.util.ArrayList<String> keys=new java.util.ArrayList<>(raw.keySet());
  keys.sort((a,b)->Integer.compare(settings.optJSONObject(a)==null?1000:settings.optJSONObject(a).optInt("order",1000),settings.optJSONObject(b)==null?1000:settings.optJSONObject(b).optInt("order",1000)));
  for(String key:keys){org.json.JSONObject item=settings.optJSONObject(key);if(item!=null&&!item.optBoolean("visible",true))continue;int color=raw.get(key);if(item!=null)try{color=Color.parseColor(item.optString("color"));}catch(Exception ignored){}String label=item==null?key:item.optString("name",key);if(entries.containsKey(label))label+=" ("+key+")";entries.put(label,color);if(surfaceNames.contains(key))legendHatches.put(label,configuredHatch(key,settings));}
  if(entries.isEmpty())return;
  Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(10*d);
  float row=15*d, width=0;for(String name:entries.keySet())width=Math.max(width,p.measureText(name)+30*d);
  width=Math.min(width,getWidth()-12*d);
  int maxRows=Math.max(1,(int)((getHeight()*.42f-24*d)/row));
  int shown=Math.min(entries.size(),maxRows);
  float x=6*d,y=getHeight()-6*d-(shown+1)*row-8*d;
  p.setColor(darkPalette()?0xF5202827:0xF5FFFFFF);c.drawRoundRect(x,y,x+width,y+(shown+1)*row+8*d,4*d,4*d,p);
  p.setColor(darkPalette()?0xffe3f0ed:0xff173c42);c.drawText("Legende",x+5*d,y+12*d,p);
  int i=0;
  for(java.util.Map.Entry<String,Integer> e:entries.entrySet()){
   if(i>=shown)break;
   float yy=y+(i+2)*row;
   p.setColor(e.getValue());c.drawRect(x+5*d,yy-9*d,x+17*d,yy,p);
   Integer hatch=legendHatches.get(e.getKey());if(hatch!=null&&hatch>=0){c.save();c.clipRect(x+5*d,yy-9*d,x+17*d,yy);c.translate(x+5*d,yy-4.5f*d);drawHatchBand(c,12*d,d*.75f,hatch);c.restore();}
   p.setColor(darkPalette()?0xffe3f0ed:0xff173c42);
   String label=i==shown-1&&entries.size()>shown?"+"+(entries.size()-shown+1)+" weitere":e.getKey();
   c.save();c.clipRect(x+21*d,yy-row,x+width-3*d,yy+3*d);c.drawText(label,x+21*d,yy,p);c.restore();i++;
  }
 }
 boolean darkEditor=false;
 boolean darkPreview=false;
 private boolean darkPalette(){return darkEditor||darkPreview;}
 @Override protected void onDraw(Canvas c){
  c.drawColor(darkPalette()?Color.BLACK:Color.WHITE);
  drawUnderlay(c);
  float density=getResources().getDisplayMetrics().density;
  paint.setTextSize(13*density);
  dimensionHits.clear();
  drawSurfaceSections(c,density);
  drawShoring(c,density);
  for(int i=1;i<vertices.size();i++){
   PointF a=vertices.get(i-1).point,b=vertices.get(i).point;
   if(layerVisible(LAYER_ROUTE)){paint.setColor(darkPalette()?0xff55c7b5:0xff13665f);paint.setStrokeWidth(4);line(c,a,b);}
   if(!layerVisible(LAYER_DIMENSIONS))continue;
   double metres=vertices.get(i).metres;
   String label=Double.isFinite(metres)?String.format(Locale.GERMANY,"%.2f m",metres):"Maß fehlt";
   float tx=left()+(a.x+b.x)*scale()/2,ty=top()+(a.y+b.y)*scale()/2-6*density;
   String key="route:"+i;tx+=density*dimensionOffsets.getOrDefault(key+":x",0f);ty+=density*dimensionOffsets.getOrDefault(key+":y",0f);
   float angle=dimensionOffsets.getOrDefault(key+":angle",0f);
   android.graphics.RectF bounds=new android.graphics.RectF(-4*density,-16*density,paint.measureText(label)+4*density,5*density);
   android.graphics.Matrix transform=new android.graphics.Matrix();transform.setRotate(angle);transform.postTranslate(tx,ty);transform.mapRect(bounds);
   dimensionHits.add(new DimensionHit(key,bounds,0,0,0));
   c.save();c.translate(tx,ty);c.rotate(angle);
   paint.setColor(Double.isFinite(metres)?(darkPalette()?0xff202827:Color.WHITE):0xffffcf73);
   c.drawRoundRect(-2*density,-14*density,paint.measureText(label)+2*density,3*density,4,4,paint);
   paint.setColor(!Double.isFinite(metres)?0xff173c42:darkPalette()?0xffe3f0ed:0xff173c42);c.drawText(label,0,0,paint);c.restore();
  }
  for(int i=0;i<vertices.size();i++){
   PointF p=vertices.get(i).point;
   if(layerVisible(LAYER_ROUTE)){
   paint.setColor(i==highlight?0xffdf8c14:darkPalette()?0xff55c7b5:0xff13665f);
   c.drawCircle(left()+scale()*p.x,top()+scale()*p.y,5*density,paint);
   paint.setColor(Color.WHITE);c.drawCircle(left()+scale()*p.x,top()+scale()*p.y,2*density,paint);
   }
   Vertex vertex=vertices.get(i);float px=left()+scale()*p.x,py=top()+scale()*p.y;
   if(layerVisible(LAYER_GPS)&&Double.isFinite(vertex.latitude)&&Double.isFinite(vertex.longitude)){
    paint.setColor(0xff4285ef);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*density);c.drawCircle(px,py,8*density,paint);paint.setStyle(Paint.Style.FILL);
   }
   if(layerVisible(LAYER_PHOTOS)&&vertex.photos.length()>0){
    float x=px+8*density,y=py-15*density;paint.setColor(0xff536c80);c.drawRoundRect(x,y,x+22*density,y+12*density,3*density,3*density,paint);paint.setColor(Color.WHITE);paint.setTextSize(10*density);c.drawText("F"+vertex.photos.length(),x+3*density,y+10*density,paint);paint.setTextSize(13*density);
   }
   String name=vertex.name;if(layerVisible(LAYER_NAMES)&&!name.isEmpty()){paint.setColor(darkPalette()?0xffe3f0ed:0xff173c42);c.drawText(name,left()+scale()*p.x+8*density,top()+scale()*p.y+16*density,paint);}
  }
  drawSurfaceDimensions(c,density);
  drawStationObjects(c,density);
  drawIndependentGpsTrack(c,density);
  drawImportedGpsReference(c,density);
  drawGpsDeviations(c,density);
  if(layerVisible(LAYER_GPS)&&liveFix!=null){
   PointF gps=gpsPosition(liveFix.getLatitude(),liveFix.getLongitude(),-1);
   if(gps!=null){
    float x=left()+scale()*gps.x,y=top()+scale()*gps.y;
    paint.setColor(0x334285ef);c.drawCircle(x,y,15*density,paint);
    paint.setColor(Color.WHITE);c.drawCircle(x,y,8*density,paint);
    paint.setColor(0xff4285ef);c.drawCircle(x,y,6*density,paint);
   }
   String status=liveFix.hasAccuracy()?String.format(Locale.GERMANY,"GPS ± %.2f m",liveFix.getAccuracy()):"GPS: Genauigkeit unbekannt";
   if(gps==null)status+=" · keine Planposition";
   paint.setColor(darkPalette()?0xff101010:Color.WHITE);c.drawRect(0,0,getWidth(),25*density,paint);
   paint.setColor(darkPalette()?0xffe3f0ed:0xff173c42);c.drawText(status,8*density,18*density,paint);
  }
  paint.setColor(0xff42799e);paint.setStrokeWidth(3);
  for(int i=1;i<stroke.size();i++)line(c,stroke.get(i-1),stroke.get(i));
  drawAutomaticLegend(c,density);
 }
 private ArrayList<PointF> simplifiedStroke(ArrayList<PointF> points,double tolerance){
  boolean[] keep=new boolean[points.size()];
  int last=points.size()-1;
  keep[0]=keep[last]=true;
  ArrayDeque<int[]> pending=new ArrayDeque<>();
  pending.push(new int[]{0,last});
  while(!pending.isEmpty()){
   int[] span=pending.pop();
   double largest=tolerance;int split=-1;
   for(int i=span[0]+1;i<span[1];i++){
    double d=distanceToLine(points.get(i),points.get(span[0]),points.get(span[1]));
    if(d>largest){largest=d;split=i;}
   }
   if(split>=0){keep[split]=true;pending.push(new int[]{span[0],split});pending.push(new int[]{split,span[1]});}
  }
  ArrayList<PointF> result=new ArrayList<>();
  for(int i=0;i<=last;i++)if(keep[i])result.add(new PointF(points.get(i).x,points.get(i).y));
  return result;
 }
 private void appendStroke(ArrayList<PointF> points){
  int before=vertices.size();
  for(int i=vertices.isEmpty()?0:1;i<points.size();i++){
   PointF p=points.get(i);
   if(!vertices.isEmpty()&&distance(vertices.get(vertices.size()-1).point,p)<.00001)continue;
   vertices.add(new Vertex(new PointF(p.x,p.y),Double.NaN));
  }
  stroke.clear();if(vertices.size()>Math.max(1,before)){moving=true;dimensioning=false;}if(changed!=null)changed.run();invalidate();
  if(vertices.size()>Math.max(1,before)&&strokeAdded!=null){int first=Math.max(1,before);post(()->strokeAdded.select(first));}
 }
 int smartDrawingMode=1;
 private void finishStroke(){
  if(stroke.size()<2)return;
  ArrayList<PointF> original=new ArrayList<>();
  for(PointF p:stroke)original.add(new PointF(p.x,p.y));
  if(original.size()==2&&distance(original.get(0),original.get(1))<.02){stroke.clear();return;}
  ArrayList<PointF> straight=simplifiedStroke(original,editable?Math.max(.023,Math.min(.08,18*getResources().getDisplayMetrics().density/scale())):.023);
  if(editable&&smartDrawingMode==0){appendStroke(original);return;}
  if(editable&&smartDrawingMode==2){for(int j=1;j<straight.size();j++){PointF anchor=straight.get(j-1),target=straight.get(j);double dx=target.x-anchor.x,dy=target.y-anchor.y;double rad=Math.round(Math.atan2(dy,dx)/Math.toRadians(15))*Math.toRadians(15);double len=Math.hypot(dx,dy),ux=Math.cos(rad),uy=Math.sin(rad);if(Math.abs(ux)>1e-8)len=Math.min(len,(ux>0?1-anchor.x:-anchor.x)/ux);if(Math.abs(uy)>1e-8)len=Math.min(len,(uy>0?1-anchor.y:-anchor.y)/uy);target.set((float)(anchor.x+len*ux),(float)(anchor.y+len*uy));}appendStroke(straight);return;}
  if(straight.size()==original.size()){appendStroke(original);return;}
  if(editable){appendStroke(straight);return;}
  new android.app.AlertDialog.Builder(getContext())
   .setTitle("Freihandlinie begradigen?")
   .setMessage("Die Skizze kann vereinfacht werden. Vorhandene Punkte und Aufmaßwerte bleiben unverändert.")
   .setPositiveButton("Begradigen",(d,w)->appendStroke(straight))
   .setNegativeButton("Original behalten",(d,w)->appendStroke(original))
   .setNeutralButton("Verwerfen",(d,w)->{stroke.clear();invalidate();})
   .setOnCancelListener(d->{stroke.clear();invalidate();})
   .show();
 }
 @Override public boolean onTouchEvent(MotionEvent event){
  if(!editable && pointSelected==null)return false;
  int action=event.getActionMasked();
  if(event.getPointerCount()>=2&&activeDimension!=null){restoreDimensionDrag();activeDimension=null;}
  if(editable&&moving&&!placingObject&&!editingObjects&&action==MotionEvent.ACTION_DOWN&&(!layerVisible(LAYER_ROUTE)||nearestPoint(event.getX(),event.getY())<0)){
   for(int i=dimensionHits.size()-1;i>=0;i--){
    DimensionHit hit=dimensionHits.get(i);
    if(hit.bounds.contains(event.getX(),event.getY())){
     activeDimension=hit;previousDimensionOffset=dimensionOffsets.get(hit.key);previousRouteX=dimensionOffsets.get(hit.key+":x");previousRouteY=dimensionOffsets.get(hit.key+":y");dimensionStartX=event.getX();dimensionStartY=event.getY();dimensionDownTime=event.getEventTime();dimensionMoved=false;getParent().requestDisallowInterceptTouchEvent(true);return true;
    }
   }
  }
  if(activeDimension!=null){
   if(action==MotionEvent.ACTION_MOVE){
    if(Math.hypot(event.getX()-dimensionStartX,event.getY()-dimensionStartY)>android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop())dimensionMoved=true;
    if(!dimensionMoved)return true;
    float d=getResources().getDisplayMetrics().density;
    if(activeDimension.key.startsWith("route:")){dimensionOffsets.put(activeDimension.key+":x",Math.max(-1000,Math.min(1000,(previousRouteX==null?0:previousRouteX)+(event.getX()-dimensionStartX)/d)));dimensionOffsets.put(activeDimension.key+":y",Math.max(-1000,Math.min(1000,(previousRouteY==null?0:previousRouteY)+(event.getY()-dimensionStartY)/d)));invalidate();return true;}
    float delta=((event.getX()-dimensionStartX)*activeDimension.nx+(event.getY()-dimensionStartY)*activeDimension.ny)/d;
    dimensionOffsets.put(activeDimension.key,Math.max(15,Math.min(260,activeDimension.offset+delta)));invalidate();return true;
   }
   if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
    if(action==MotionEvent.ACTION_CANCEL){restoreDimensionDrag();}
    if(action==MotionEvent.ACTION_UP&&dimensionMoved&&!saveDimensionLayout()){restoreDimensionDrag();}
    String key=activeDimension.key;boolean rotate=action==MotionEvent.ACTION_UP&&!dimensionMoved&&key.startsWith("route:")&&event.getEventTime()-dimensionDownTime>=android.view.ViewConfiguration.getLongPressTimeout();
    activeDimension=null;getParent().requestDisallowInterceptTouchEvent(false);invalidate();if(rotate)showDimensionRotation(key);return true;
   }
   return true;
  }
  if(event.getPointerCount()>=2){
   cancelObjectDrag();
   float x=(event.getX(0)+event.getX(1))/2,y=(event.getY(0)+event.getY(1))/2;
   float next=(float)Math.hypot(event.getX(0)-event.getX(1),event.getY(0)-event.getY(1));
   if(gesture && action==MotionEvent.ACTION_MOVE && span>0)zoomAt(next/span,focusX,focusY,x,y);
   gesture=true;span=next;focusX=x;focusY=y;stroke.clear();dragged=-1;invalidate();return true;
  }
  if(action==MotionEvent.ACTION_DOWN){gesture=false;touchMoved=false;touchStartX=event.getX();touchStartY=event.getY();lastTouchX=touchStartX;lastTouchY=touchStartY;touchStartTime=event.getEventTime();tappedPoint=editable&&pointSelected!=null&&layerVisible(LAYER_ROUTE)?nearestPoint(touchStartX,touchStartY):-1;}
  if(gesture){
   if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL)getParent().requestDisallowInterceptTouchEvent(false);
   return true;
  }
  PointF p=point(event.getX(),event.getY());
  switch(event.getActionMasked()){
   case MotionEvent.ACTION_DOWN:
    getParent().requestDisallowInterceptTouchEvent(true);
    stroke.clear();
    if(moving&&!placingObject&&!editingObjects&&tappedPoint>=0){dragged=tappedPoint;highlight=dragged;invalidate();return true;}
    resizeAxis=0;if((editingObjects||moving)&&selectedResizeObject>=0&&layerVisible(LAYER_OBJECTS)){org.json.JSONObject o=stationObjects.optJSONObject(selectedResizeObject);if(o!=null){resizeAxis=resizeHandle(objectFootprint(o),(event.getX()-left())/scale(),(event.getY()-top())/scale());if(resizeAxis!=0){draggedObject=selectedResizeObject;objectBefore=o.toString();dragged=-1;return true;}}}
    if(editingObjects||moving){java.util.ArrayList<Integer> hits=objectCandidates((event.getX()-left())/scale(),(event.getY()-top())/scale(),12*getResources().getDisplayMetrics().density/scale());if(hits.size()==1){draggedObject=hits.get(0);org.json.JSONObject o=stationObjects.optJSONObject(draggedObject);ObjectFootprint f=objectFootprint(o);objectBefore=o.toString();objectGrabX=(event.getX()-left())/scale()-f.center.x;objectGrabY=(event.getY()-top())/scale()-f.center.y;dragged=-1;return true;}}
    if(placingObject||editingObjects){dragged=-1;return true;}
    if(moving || !editable)dragged=layerVisible(LAYER_ROUTE)?nearestPoint(event.getX(),event.getY()):-1;
    else if(!dimensioning)stroke.add(vertices.isEmpty()?p:vertices.get(vertices.size()-1).point);
    return true;
   case MotionEvent.ACTION_MOVE:
    if(Math.hypot(event.getX()-touchStartX,event.getY()-touchStartY)>android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop())touchMoved=true;
    if(draggedObject>=0&&touchMoved){if(resizeAxis!=0){resizeStationObject((event.getX()-left())/scale(),(event.getY()-top())/scale());return true;}moveStationObject((event.getX()-left())/scale()-objectGrabX,(event.getY()-top())/scale()-objectGrabY);return true;}
    if(touchMoved&&(placingObject||editingObjects||(moving&&dragged<0)||dimensioning||!editable)){centerX=clamp(centerX-(event.getX()-lastTouchX)/scale());centerY=clamp(centerY-(event.getY()-lastTouchY)/scale());invalidate();}
    lastTouchX=event.getX();lastTouchY=event.getY();
    if(placingObject||editingObjects)return true;
    if(moving && dragged>=0&&touchMoved)movePoint(dragged,p);
    if(!dimensioning && !stroke.isEmpty() && distance(stroke.get(stroke.size()-1),p)>.008){stroke.add(p);invalidate();}
    return true;
   case MotionEvent.ACTION_UP:
    getParent().requestDisallowInterceptTouchEvent(false);
    if(draggedObject>=0){int index=draggedObject;selectedResizeObject=index;resizeAxis=0;draggedObject=-1;objectBefore=null;if(!touchMoved&&objectSelected!=null&&event.getEventTime()-touchStartTime>=android.view.ViewConfiguration.getLongPressTimeout()){java.util.ArrayList<Integer> hits=new java.util.ArrayList<>();hits.add(index);objectSelected.accept(hits);}invalidate();return true;}
    if(editingObjects){if(!touchMoved&&objectSelected!=null&&event.getEventTime()-touchStartTime>=android.view.ViewConfiguration.getLongPressTimeout()){double tolerance=20*getResources().getDisplayMetrics().density/scale();objectSelected.accept(objectCandidates((event.getX()-left())/scale(),(event.getY()-top())/scale(),tolerance));}performClick();invalidate();return true;}
    if(placingObject){if(!touchMoved&&stationSelected!=null){double tolerance=24*getResources().getDisplayMetrics().density/scale();stationSelected.accept(stationCandidates((event.getX()-left())/scale(),(event.getY()-top())/scale(),tolerance));}performClick();invalidate();return true;}
    if(editable&&tappedPoint>=0&&!touchMoved&&pointSelected!=null){
     stroke.clear();dragged=-1;highlight=tappedPoint;if(event.getEventTime()-touchStartTime>=POINT_CONTEXT_HOLD_MS)pointSelected.select(tappedPoint);tappedPoint=-1;performClick();invalidate();return true;
    }
    if(moving || !editable){
     if(dragged>=0){
      if(moving&&touchMoved){movePoint(dragged,p);if(changed!=null)changed.run();}
      if(!editable&&!touchMoved&&pointSelected!=null&&event.getEventTime()-touchStartTime>=POINT_CONTEXT_HOLD_MS)pointSelected.select(dragged);
     }else if(!touchMoved&&selected!=null&&layerVisible(LAYER_ROUTE)&&event.getEventTime()-touchStartTime>=POINT_CONTEXT_HOLD_MS)selectSegment(p);
     dragged=-1;
    }else if(dimensioning){
     if(!touchMoved&&event.getEventTime()-touchStartTime>=POINT_CONTEXT_HOLD_MS)selectSegment(p);
    }else if(!stroke.isEmpty()){
     stroke.add(p);finishStroke();
    }
    performClick();invalidate();return true;
   case MotionEvent.ACTION_CANCEL:
    cancelObjectDrag();getParent().requestDisallowInterceptTouchEvent(false);stroke.clear();invalidate();return true;
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
  vertices.get(index).gpsUndo=null;
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
