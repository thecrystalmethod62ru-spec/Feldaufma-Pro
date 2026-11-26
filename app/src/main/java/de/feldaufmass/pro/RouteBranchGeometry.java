package de.feldaufmass.pro;

import java.util.ArrayList;
import java.util.List;

/** Branch coordinates in the same metre coordinate system as the main route. */
public final class RouteBranchGeometry {
    private RouteBranchGeometry() {}

    /** Main vertices must already be scaled to measured lengths.
     * Directions are absolute degrees counterclockwise from positive X.
     */
    public static List<double[]> build(List<double[]> main,double station,double[] lengths,double[] directions) {
        if(main==null||main.size()<2||!Double.isFinite(station)||station<0||
           lengths==null||directions==null||lengths.length==0||lengths.length!=directions.length)
            throw new IllegalArgumentException("Ungültige Abzweigung");
        double remaining=station;
        double[] anchor=null;
        for(int i=1;i<main.size();i++){
            double[] a=main.get(i-1),b=main.get(i);
            if(a==null||b==null||a.length<2||b.length<2)throw new IllegalArgumentException("Ungültige Haupttrasse");
            double n=Math.hypot(b[0]-a[0],b[1]-a[1]);
            if(!Double.isFinite(n)||n<=0)throw new IllegalArgumentException("Ungültige Haupttrasse");
            if(anchor==null){
                if(remaining<=n+1e-8){
                    double t=Math.min(1,remaining/n);
                    anchor=new double[]{a[0]+t*(b[0]-a[0]),a[1]+t*(b[1]-a[1])};
                }else remaining-=n;
            }
        }
        if(anchor==null)throw new IllegalArgumentException("Station außerhalb der Haupttrasse");
        List<double[]> vertices=new ArrayList<>();
        vertices.add(anchor);
        double x=anchor[0],y=anchor[1];
        for(int i=0;i<lengths.length;i++){
            if(!Double.isFinite(lengths[i])||lengths[i]<=0||!Double.isFinite(directions[i]))
                throw new IllegalArgumentException("Ungültiges Abzweigmaß");
            double angle=Math.toRadians(directions[i]%360);
            x+=lengths[i]*Math.cos(angle);y+=lengths[i]*Math.sin(angle);
            if(!Double.isFinite(x)||!Double.isFinite(y))throw new IllegalArgumentException("Koordinaten außerhalb des Wertebereichs");
            vertices.add(new double[]{x,y});
        }
        return vertices;
    }
}
