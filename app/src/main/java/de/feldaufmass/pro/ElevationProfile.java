package de.feldaufmass.pro;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

/** Surveyed elevations in metres, station measured along the route. */
public final class ElevationProfile {
    private final List<double[]> points = new ArrayList<>();
    public ElevationProfile(List<double[]> input, double length) {
        if (!Double.isFinite(length)||length<=0) throw new IllegalArgumentException("Ungültige Trassenlänge");
        for(double[] p:input){
            if(p==null||p.length!=2||!Double.isFinite(p[0])||!Double.isFinite(p[1])||p[0]<0||p[0]>length)
                throw new IllegalArgumentException("Ungültiger Höhenpunkt");
            points.add(p.clone());
        }
        points.sort(Comparator.comparingDouble(p->p[0]));
        if(points.size()<2) throw new IllegalArgumentException("Mindestens zwei Höhenpunkte erforderlich");
        for(int i=1;i<points.size();i++) if(points.get(i)[0]<=points.get(i-1)[0])
            throw new IllegalArgumentException("Doppelte Station");
    }
    /** No extrapolation beyond surveyed stations. */
    public double elevation(double station){
        if(!Double.isFinite(station)||station<points.get(0)[0]||station>points.get(points.size()-1)[0])return Double.NaN;
        for(int i=1;i<points.size();i++){
            double[] a=points.get(i-1),b=points.get(i);
            if(station<=b[0])return a[1]+(b[1]-a[1])*(station-a[0])/(b[0]-a[0]);
        }
        return Double.NaN;
    }
    /** Positive values indicate a fall in the direction of increasing station. */
    public double fallPercent(double from,double to){
        if(!Double.isFinite(from)||!Double.isFinite(to)||to<=from)throw new IllegalArgumentException("Ungültiger Stationsbereich");
        return 100*(elevation(from)-elevation(to))/(to-from);
    }
    public List<double[]> stations(){
        List<double[]> copy=new ArrayList<>();
        for(double[] p:points)copy.add(p.clone());
        return copy;
    }
}
