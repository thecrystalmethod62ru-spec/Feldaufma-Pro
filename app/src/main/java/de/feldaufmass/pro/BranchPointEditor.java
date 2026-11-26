package de.feldaufmass.pro;

import java.util.ArrayList;
import java.util.List;

/** Pure geometry operations; the connection point at index zero stays fixed. */
public final class BranchPointEditor {
    private BranchPointEditor(){}
    public static double snapDirection(double degrees){
        if(!Double.isFinite(degrees))throw new IllegalArgumentException("Ungültiger Winkel");
        return ((Math.round((degrees%360)/15.0)*15)%360+360)%360;
    }
    public static RouteBranch remove(RouteBranch branch,List<double[]> main,int index){
        List<double[]> points=new ArrayList<>(branch.vertices(main));
        if(index<=0||index>=points.size()||points.size()<=2)throw new IllegalArgumentException("Punkt kann nicht gelöscht werden");
        points.remove(index);
        return rebuild(branch,points);
    }
    public static RouteBranch insert(RouteBranch branch,List<double[]> main,int after,double x,double y){
        List<double[]> points=new ArrayList<>(branch.vertices(main));
        if(after<0||after>=points.size()||!Double.isFinite(x)||!Double.isFinite(y))throw new IllegalArgumentException("Ungültiger Punkt");
        points.add(after+1,new double[]{x,y});
        return rebuild(branch,points);
    }
    /** Move a vertex, optionally snapping the incoming segment to a 15 degree grid. */
    public static RouteBranch move(RouteBranch branch,List<double[]> main,int index,double x,double y,boolean snap){
        List<double[]> points=new ArrayList<>(branch.vertices(main));
        if(index<=0||index>=points.size()||!Double.isFinite(x)||!Double.isFinite(y))
            throw new IllegalArgumentException("Ungültiger Punkt");
        if(snap){
            double[] previous=points.get(index-1);
            double dx=x-previous[0],dy=y-previous[1],length=Math.hypot(dx,dy);
            double angle=Math.toRadians(snapDirection(Math.toDegrees(Math.atan2(dy,dx))));
            x=previous[0]+length*Math.cos(angle);y=previous[1]+length*Math.sin(angle);
        }
        points.set(index,new double[]{x,y});
        return rebuild(branch,points);
    }
    private static RouteBranch rebuild(RouteBranch branch,List<double[]> points){
        double[] lengths=new double[points.size()-1],directions=new double[lengths.length];
        for(int i=0;i<lengths.length;i++){
            double dx=points.get(i+1)[0]-points.get(i)[0],dy=points.get(i+1)[1]-points.get(i)[1];
            lengths[i]=Math.hypot(dx,dy);
            if(lengths[i]<1e-8)throw new IllegalArgumentException("Punkte liegen übereinander");
            directions[i]=Math.toDegrees(Math.atan2(dy,dx));
        }
        return new RouteBranch(branch.id,branch.name,branch.station,branch.width,branch.depth,branch.surface,lengths,directions);
    }
}
