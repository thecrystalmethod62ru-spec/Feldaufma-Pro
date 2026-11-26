package de.feldaufmass.pro;

import java.util.ArrayList;
import java.util.List;

public final class PipeLayout {
    public static final class Pipe {
        public final String name;
        public final double offset, diameter, wall, bedding;
        public Pipe(String name,double offset,double diameter,double wall,double bedding){
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
            if(Math.abs(a.offset)+a.diameter/2>width/2)result.add(a.name+": außerhalb der Grabenbreite");
            if(!a.section(0,depth).fits())result.add(a.name+": oberhalb GOK");
            else if(depth-a.bedding-a.diameter<minCover)result.add(a.name+": Mindestüberdeckung unterschritten");
            for(int j=0;j<i;j++){
                Pipe b=pipes.get(j);
                double gap=Math.hypot(a.offset-b.offset,a.centerHeight()-b.centerHeight())-(a.diameter+b.diameter)/2;
                if(gap<0)result.add(a.name+" / "+b.name+": Rohrüberschneidung");
                else if(gap<clearance)result.add(a.name+" / "+b.name+": Mindestabstand unterschritten");
            }
        }
        return result;
    }
}
