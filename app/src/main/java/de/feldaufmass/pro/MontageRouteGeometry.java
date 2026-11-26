package de.feldaufmass.pro;

/** Interpolates on a route with explicitly supplied metric segment lengths.
 * Coordinates use screen convention (x right, y down), in metres.
 * Does not infer measured lengths from a schematic drawing.
 */
public final class MontageRouteGeometry {
 private MontageRouteGeometry(){}
 public static final class Position {
  public final double x,y,heading;
  public final int segment;
  public final boolean atCorner;
  Position(double x,double y,double heading,int segment,boolean atCorner){
   this.x=x;this.y=y;this.heading=heading;this.segment=segment;this.atCorner=atCorner;
  }
 }
 public static Position locate(double[] x,double[] y,double[] lengths,double station,double offset){
  if(x==null||y==null||lengths==null||x.length<2||y.length!=x.length||lengths.length!=x.length-1||!Double.isFinite(station)||station<0||!Double.isFinite(offset))throw new IllegalArgumentException("Ungültige Trasse oder Station");
  double total=0;
  for(int i=0;i<x.length;i++){
   if(!Double.isFinite(x[i])||!Double.isFinite(y[i]))throw new IllegalArgumentException("Ungültiger Punkt");
   if(i<lengths.length){
    double span=Math.hypot(x[i+1]-x[i],y[i+1]-y[i]);
    if(!Double.isFinite(span)||span<=0||!Double.isFinite(lengths[i])||lengths[i]<=0)throw new IllegalArgumentException("Ungültiger Abschnitt");
    total+=lengths[i];
   }
  }
  if(!Double.isFinite(total)||station>total)throw new IllegalArgumentException("Station außerhalb der Trasse");
  double start=0;
  for(int i=0;i<lengths.length;i++){
   double end=start+lengths[i];
   if(station<=end||i==lengths.length-1){
    double t=Math.max(0,Math.min(1,(station-start)/lengths[i]));
    double dx=x[i+1]-x[i],dy=y[i+1]-y[i],span=Math.hypot(dx,dy);
    boolean corner=(i>0&&t==0)||(i<lengths.length-1&&t==1);
    double px=x[i]+t*dx-offset*dy/span,py=y[i]+t*dy+offset*dx/span;
    if(!Double.isFinite(px)||!Double.isFinite(py))throw new IllegalArgumentException("Ungültige Position");
    return new Position(px,py,MontageCatalog.normalizeRotation(Math.toDegrees(Math.atan2(dy,dx))),i,corner);
   }
   start=end;
  }
  throw new IllegalArgumentException("Station nicht gefunden");
 }
}
