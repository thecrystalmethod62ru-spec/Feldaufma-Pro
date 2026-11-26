package de.feldaufmass.pro;

import java.util.ArrayList;
import java.util.List;

public final class PipeLayout {
    // Numerical tolerance in metres; prevents false warnings at exact boundaries.
    private static final double EPS = 1e-9;
    public static final class Pipe {
        public final String name;
        public final double offset, diameter, wall, bedding, minGap, minCover;
        public Pipe(String name,double offset,double diameter,double wall,double bedding){
            this(name,offset,diameter,wall,bedding,0,0);
        }
        public Pipe(String name,double offset,double diameter,double wall,double bedding,double minGap,double minCover){
            if(!Double.isFinite(minGap)||minGap<0||!Double.isFinite(minCover)||minCover<0)throw new IllegalArgumentException("Ungültige Rohrgrenzwerte");
            this.minGap=minGap;this.minCover=minCover;
            if(name==null||name.trim().isEmpty()||!Double.isFinite(offset))
                throw new IllegalArgumentException("Ungültige Rohrbezeichnung oder Lage");
            new PipeSection(0,1,bedding,diameter,wall);
            this.name=name;this.offset=offset;this.diameter=diameter;this.wall=wall;this.bedding=bedding;
        }
        public double centerHeight(){return bedding+diameter/2;}
        public PipeSection section(double ground,double depth){return new PipeSection(ground,depth,bedding,diameter,wall);}
    }
    /** Signed clear distances in metres; negative values indicate overlap. */
    public static List<String> dimensions(List<Pipe> pipes,double width,double depth){
        warnings(pipes,width,depth,0);
        List<String> result=new ArrayList<>();
        for(int i=0;i<pipes.size();i++){
            Pipe a=pipes.get(i);
            double radius=a.diameter/2;
            result.add(a.name+": Wand links "+metres(width/2+a.offset-radius)
                +" | rechts "+metres(width/2-a.offset-radius)
                +" | Überdeckung "+metres(depth-a.bedding-a.diameter));
            for(int j=0;j<i;j++){
                Pipe b=pipes.get(j);
                double gap=Math.hypot(a.offset-b.offset,a.centerHeight()-b.centerHeight())-(a.diameter+b.diameter)/2;
                result.add(b.name+" / "+a.name+": lichter Abstand "+metres(gap));
            }
        }
        return result;
    }
    private static String metres(double value){
        return String.format(java.util.Locale.GERMANY,"%.3f m",value);
    }
    public static List<String> warnings(List<Pipe> pipes,double width,double depth,double clearance){
        return warnings(pipes,width,depth,clearance,0);
    }
    public static List<String> warnings(List<Pipe> pipes,double width,double depth,double clearance,double minCover){
        if(!Double.isFinite(minCover)||minCover<0)throw new IllegalArgumentException("Ungültige Mindestüberdeckung");
        if(!Double.isFinite(width)||width<=0||!Double.isFinite(depth)||depth<=0||!Double.isFinite(clearance)||clearance<0)
            throw new IllegalArgumentException("Ungültige Grabenmaße");
        List<String> result=new ArrayList<>();
        for(int i=0;i<pipes.size();i++){
            Pipe a=pipes.get(i);
            if(Math.abs(a.offset)+a.diameter/2>width/2+EPS)result.add(a.name+": außerhalb der Grabenbreite");
            if(a.bedding+a.diameter>depth+EPS)result.add(a.name+": oberhalb GOK");
            else if(depth-a.bedding-a.diameter+EPS<Math.max(minCover,a.minCover))result.add(a.name+": Mindestüberdeckung unterschritten");
            for(int j=0;j<i;j++){
                Pipe b=pipes.get(j);
                double gap=Math.hypot(a.offset-b.offset,a.centerHeight()-b.centerHeight())-(a.diameter+b.diameter)/2;
                if(gap < -EPS)result.add(a.name+" / "+b.name+": Rohrüberschneidung");
                else if(gap+EPS<Math.max(clearance,Math.max(a.minGap,b.minGap)))result.add(a.name+" / "+b.name+": Mindestabstand unterschritten");
            }
        }
        return result;
    }

    /** Proposes a centered single-row layout, preserving each pipe's bedding.
     * Returns an empty list if this conservative arrangement cannot satisfy the limits.
     * Does not modify the input list.
     */
    public static List<Pipe> proposeRow(List<Pipe> pipes,double width,double depth,double clearance,double minCover){
        warnings(pipes,width,depth,clearance,minCover);
        List<Pipe> result=new ArrayList<>();
        double total=0;
        for(int i=0;i<pipes.size();i++){
            Pipe a=pipes.get(i);
            total+=a.diameter;
            if(i>0)total+=Math.max(clearance,Math.max(a.minGap,pipes.get(i-1).minGap));
        }
        if(total>width+EPS)return result;
        double cursor=-total/2;
        for(int i=0;i<pipes.size();i++){
            Pipe a=pipes.get(i);
            if(i>0)cursor+=Math.max(clearance,Math.max(a.minGap,pipes.get(i-1).minGap));
            result.add(new Pipe(a.name,cursor+a.diameter/2,a.diameter,a.wall,a.bedding,a.minGap,a.minCover));
            cursor+=a.diameter;
        }
        if(!warnings(result,width,depth,clearance,minCover).isEmpty())return new ArrayList<>();
        return result;
    }
}
