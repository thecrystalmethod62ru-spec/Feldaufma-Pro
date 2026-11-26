package de.feldaufmass.pro;

import android.graphics.Path;
import android.graphics.PathMeasure;
import java.util.List;

/** Geometric control values for flat-ended strips; excludes corner fillers. */
public final class StripOverlap {
    private StripOverlap() {}
    public static final class Result {
        public final double gross, net, overlap;
        Result(double gross, double net) {
            this.gross = gross;
            this.net = net;
            this.overlap = Math.max(0, gross - net);
        }
    }
    /** Each strip: x1,y1,x2,y2,leftExtent,rightExtent, in metres. */
    public static Result calculate(List<double[]> strips) {
        if (strips == null || strips.isEmpty()) throw new IllegalArgumentException("Keine Abschnitte");
        Path union = new Path();
        double gross = 0;
        double ox = strips.get(0)[0], oy = strips.get(0)[1];
        for (double[] s : strips) {
            if (s.length != 6) throw new IllegalArgumentException("Ungültiger Abschnitt");
            for (double v : s) if (!Double.isFinite(v)) throw new IllegalArgumentException("Fehlende Maße");
            double dx = s[2]-s[0], dy = s[3]-s[1], len = Math.hypot(dx,dy);
            if (len <= 0 || s[4]<0 || s[5]<0 || s[4]+s[5]<=0) throw new IllegalArgumentException("Ungültige Breite oder Länge");
            double nx=-dy/len, ny=dx/len;
            double x=s[0]-ox,y=s[1]-oy,u=s[2]-ox,v=s[3]-oy;
            Path p=new Path();
            p.moveTo((float)(x+nx*s[4]),(float)(y+ny*s[4]));
            p.lineTo((float)(u+nx*s[4]),(float)(v+ny*s[4]));
            p.lineTo((float)(u-nx*s[5]),(float)(v-ny*s[5]));
            p.lineTo((float)(x-nx*s[5]),(float)(y-ny*s[5]));
            p.close();
            if (!union.op(p,Path.Op.UNION)) throw new IllegalStateException("Flächenvereinigung fehlgeschlagen");
            gross+=len*(s[4]+s[5]);
        }
        // Path.op on polygonal input produces polygonal contours. Approximate
        // samples include vertices; signed areas account for interior holes.
        float[] points=union.approximate(0.001f);
        double signed=0;
        for(int i=3;i<points.length;i+=3) {
            if(points[i]==points[i-3]) continue; // contour jump
            signed+=(double)points[i-2]*points[i+2]-(double)points[i+1]*points[i-1];
        }
        double net=Math.abs(signed)/2;
        if(!Double.isFinite(net)||net>gross+Math.max(0.01,gross*0.0001))
            throw new IllegalStateException("Flächenprüfung fehlgeschlagen");
        return new Result(gross,Math.min(gross,net));
    }
}
