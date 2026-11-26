package de.feldaufmass.pro;

import java.io.Writer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** ASCII DXF R12 entities; metric reconstruction uses entered lengths and schematic bearings. */
final class DxfExport {
 static final String[] LAYERS={"TRASSE","BEMASSUNG","PUNKTE","OBJEKTE","OBERFLAECHEN","GPS","FOTOS","HINWEISE","MONTAGE","VERBINDUNGEN"};
 static final int[] COLORS={3,7,4,30,8,5,6,7,2,1};
 final Writer out;
 final ArrayList<double[]> vertices=new ArrayList<>();
 final RouteSketch route;
 final boolean metric;
 private final double[] screenX,screenY,sectionLengths;
 DxfExport(Writer out,RouteSketch route,boolean metric){
  this.out=out;this.route=route;this.metric=metric;
  int count=metric?route.vertices.size():0;
  screenX=new double[count];screenY=new double[count];sectionLengths=new double[Math.max(0,count-1)];
  double x=0,y=0;
  if(route.vertices.isEmpty())return;
  RouteSketch.Vertex first=route.vertices.get(0);
  vertices.add(new double[]{0,0});
  for(int i=1;i<route.vertices.size();i++){
   RouteSketch.Vertex a=route.vertices.get(i-1),b=route.vertices.get(i);
   double dx=b.point.x-a.point.x,dy=a.point.y-b.point.y,n=Math.hypot(dx,dy);
   if(metric){
    if(n<=1e-8||!Double.isFinite(b.metres)||b.metres<=0)throw new IllegalArgumentException("Unvollstaendige Geometrie");
    x+=dx/n*b.metres;y+=dy/n*b.metres;
    screenX[i]=x;screenY[i]=-y;sectionLengths[i-1]=b.metres;
   }else{x=(b.point.x-first.point.x)*100;y=(first.point.y-b.point.y)*100;}
   if(!Double.isFinite(x)||!Double.isFinite(y))throw new IllegalArgumentException("Ungueltige Geometrie");
   vertices.add(new double[]{x,y});
  }
 }
 static boolean metricAvailable(RouteSketch route){
  if(route.vertices.size()<2)return false;
  double total=0;
  for(int i=1;i<route.vertices.size();i++){
   RouteSketch.Vertex a=route.vertices.get(i-1),b=route.vertices.get(i);
   if(!Double.isFinite(b.metres)||b.metres<=0||Math.hypot(b.point.x-a.point.x,b.point.y-a.point.y)<=1e-8)return false;
   total+=b.metres;
  }
  return Double.isFinite(total)&&total>0;
 }
 void pair(int code,Object value)throws IOException{out.write(Integer.toString(code));out.write("\n");out.write(String.valueOf(value));out.write("\n");}
 void line(String layer,double[] a,double[] b)throws IOException{
  pair(0,"LINE");pair(8,layer);pair(10,a[0]);pair(20,a[1]);pair(30,0);pair(11,b[0]);pair(21,b[1]);pair(31,0);
 }
 static String ascii(String s){
  return s.replace("ä","ae").replace("ö","oe").replace("ü","ue").replace("Ä","Ae").replace("Ö","Oe").replace("Ü","Ue").replace("ß","ss").replaceAll("[^\\x20-\\x7E]","?");
 }
 void text(String layer,double x,double y,double height,String value)throws IOException{
  pair(0,"TEXT");pair(8,layer);pair(10,x);pair(20,y);pair(30,0);pair(40,height);pair(1,ascii(value));
 }
 void circle(String layer,double[] a,double radius)throws IOException{
  pair(0,"CIRCLE");pair(8,layer);pair(10,a[0]);pair(20,a[1]);pair(30,0);pair(40,radius);
 }
 double[] station(double at){
  if(!metricAvailable(route)||!Double.isFinite(at)||at<0)return null;
  double start=0;
  for(int i=1;i<vertices.size();i++){
   double end=start+route.vertices.get(i).metres;
   if(at<=end+1e-8){double f=Math.max(0,Math.min(1,(at-start)/(end-start)));double[] a=vertices.get(i-1),b=vertices.get(i);return new double[]{a[0]+(b[0]-a[0])*f,a[1]+(b[1]-a[1])*f};}
   start=end;
  }return null;
 }
 /** Coordinates in CAD convention. Positive offset is to the right looking along the route. */
 double[] mountingPoint(double station,double offset){
  if(!metric)return null;
  try{
   MontageRouteGeometry.Position pos=MontageRouteGeometry.locate(screenX,screenY,sectionLengths,station,offset);
   return pos.atCorner&&offset!=0?null:new double[]{pos.x,-pos.y};
  }catch(IllegalArgumentException invalid){return null;}
 }
 void write(String project,JSONArray objects,JSONArray surfaces)throws IOException{
  write(project,objects,surfaces,new JSONArray(),new JSONArray());
 }
 void write(String project,JSONArray objects,JSONArray surfaces,JSONArray fittings,JSONArray connections)throws IOException{
  pair(0,"SECTION");pair(2,"HEADER");pair(9,"$ACADVER");pair(1,"AC1009");pair(0,"ENDSEC");
  pair(0,"SECTION");pair(2,"TABLES");pair(0,"TABLE");pair(2,"LAYER");pair(70,LAYERS.length+1);
  pair(0,"LAYER");pair(2,"0");pair(70,0);pair(62,7);pair(6,"CONTINUOUS");
  for(int i=0;i<LAYERS.length;i++){pair(0,"LAYER");pair(2,LAYERS[i]);pair(70,0);pair(62,COLORS[i]);pair(6,"CONTINUOUS");}
  pair(0,"ENDTAB");pair(0,"ENDSEC");pair(0,"SECTION");pair(2,"BLOCKS");pair(0,"ENDSEC");
  pair(0,"SECTION");pair(2,"ENTITIES");
  double extent=metric?route.measuredLength():100, h=Math.max(.15,extent/100);
  double top=0;for(double[] v:vertices)top=Math.max(top,v[1]);
  text("HINWEISE",0,top+h*5,h,project);
  text("HINWEISE",0,top+h*3,h,metric?"Einheit m; gemessene Laengen, skizzierte Richtungen; lokaler Ursprung":"Schematische Einheiten; nicht massstaeblich");
  for(int i=0;i<vertices.size();i++){
   double[] a=vertices.get(i);RouteSketch.Vertex v=route.vertices.get(i);
   circle("PUNKTE",a,h*.2);text("PUNKTE",a[0]+h*.3,a[1]-h,h*.7,(i+1)+" "+v.name);
   if(i>0){double[] b=vertices.get(i-1);line("TRASSE",b,a);text("BEMASSUNG",(a[0]+b[0])/2,(a[1]+b[1])/2+h*.3,h*.75,Double.isFinite(v.metres)?String.format(Locale.ROOT,"%.3f m",v.metres):"Mass offen");}
   if(Double.isFinite(v.latitude)&&Double.isFinite(v.longitude))text("GPS",a[0]+h*.3,a[1]+h,h*.65,String.format(Locale.ROOT,"GPS %.8f %.8f",v.latitude,v.longitude));
   if(v.photos.length()>0)text("FOTOS",a[0]+h*.3,a[1]-h*2,h*.65,"Fotos: "+v.photos.length());
  }
  for(int i=0;i<objects.length();i++){JSONObject o=objects.optJSONObject(i);if(o==null)continue;double st=o.optDouble("station");double[] a=station(st);if(a==null)continue;circle("OBJEKTE",a,h*.4);text("OBJEKTE",a[0]+h*.5,a[1]+h*.5,h*.75,o.optString("type")+" "+String.format(Locale.ROOT,"%.3f m",st)+" "+o.optString("side"));}
  for(int j=0;j<surfaces.length();j++){JSONObject o=surfaces.optJSONObject(j);if(o==null)continue;double from=o.optDouble("from"),to=o.optDouble("to");if(from>=to||station(from)==null||station(to)==null)continue;
   double start=0;for(int i=1;i<vertices.size();i++){double end=start+route.vertices.get(i).metres;double lo=Math.max(start,from),hi=Math.min(end,to);if(hi>lo)line("OBERFLAECHEN",station(lo),station(hi));start=end;}
   double[] p=station((from+to)/2);text("OBERFLAECHEN",p[0],p[1]-h*2,h*.75,o.optString("surface"));
  }
  if(!metricAvailable(route))text("HINWEISE",0,top+h*1.5,h*.75,"Stationsobjekte/Belaege nicht positionierbar: Masse fehlen.");
  int unresolved=0;
  for(int group=0;group<2;group++){
   JSONArray rows=group==0?fittings:connections;
   String layer=group==0?"MONTAGE":"VERBINDUNGEN";
   for(int i=0;i<rows.length();i++){
    JSONObject row=rows.optJSONObject(i);
    if(row==null){unresolved++;continue;}
    double st=row.optDouble("station",Double.NaN);
    double[] point=mountingPoint(st,row.optDouble("offset",0));
    if(point==null){unresolved++;continue;}
    String label=row.optString("number")+" "+row.optString("type");
    if(group==0){
     try{label=MontageCatalog.Fitting.valueOf(row.optString("type")).label;}
     catch(IllegalArgumentException unknown){label=row.optString("type","Formstueck");}
     if(!row.optString("dn").isEmpty())label+=" DN "+row.optString("dn");
    }
    circle(layer,point,h*.3);
    text(layer,point[0]+h*.4,point[1]+(group==0?h:-h),h*.7,label+String.format(Locale.ROOT," - Station %.3f m",st));
   }
  }
  if(unresolved>0)text("HINWEISE",0,top+h*7,h*.75,"Montageobjekte ohne darstellbare Position: "+unresolved);
  pair(0,"ENDSEC");pair(0,"EOF");out.flush();
 }
}
