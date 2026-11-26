package de.feldaufmass.pro;

import android.graphics.Path;
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
    /** Bevel filler on the outside of a connected bend, in metre coordinates.
     * Returns null for straight, disconnected or reversing segments.
     * Does not change the original strip calculation or billing selection.
     */
    public static double[][] cornerFiller(double[] a,double[] b) {
        if(a==null||b==null||a.length!=6||b.length!=6) throw new IllegalArgumentException("Ungültiger Abschnitt");
        for(double[] s:new double[][]{a,b}) {
            for(double v:s) if(!Double.isFinite(v)) throw new IllegalArgumentException("Fehlende Maße");
            if(s[4]<0||s[5]<0) throw new IllegalArgumentException("Ungültige Breite");
        }
        if(Math.hypot(a[2]-b[0],a[3]-b[1])>1e-7)return null;
        double ax=a[2]-a[0],ay=a[3]-a[1],bx=b[2]-b[0],by=b[3]-b[1];
        double al=Math.hypot(ax,ay),bl=Math.hypot(bx,by);
        if(al<=0||bl<=0)throw new IllegalArgumentException("Ungültige Länge");
        ax/=al;ay/=al;bx/=bl;by/=bl;
        double cross=ax*by-ay*bx;
        if(Math.abs(cross)<1e-9)return null;
        double wa=cross>0?-a[5]:a[4],wb=cross>0?-b[5]:b[4];
        if(wa==0||wb==0)return null;
        double x=a[2],y=a[3];
        return new double[][]{{x,y},{x-ay*wa,y+ax*wa},{x-by*wb,y+bx*wb}};
    }
    /** Miter join with a bounded extension; falls back to bevel at sharp bends. */
    public static double[][] miterCorner(double[] a,double[] b) {
        double[][] q=cornerFiller(a,b);
        if(q==null)return null;
        double ax=a[2]-a[0],ay=a[3]-a[1],bx=b[2]-b[0],by=b[3]-b[1];
        double al=Math.hypot(ax,ay),bl=Math.hypot(bx,by);
        ax/=al;ay/=al;bx/=bl;by/=bl;
        double den=ax*by-ay*bx;
        double dx=q[2][0]-q[1][0],dy=q[2][1]-q[1][1];
        double t=(dx*by-dy*bx)/den;
        double u=(dx*ay-dy*ax)/den;
        double x=q[1][0]+t*ax,y=q[1][1]+t*ay;
        double reach=Math.max(Math.hypot(q[1][0]-q[0][0],q[1][1]-q[0][1]),Math.hypot(q[2][0]-q[0][0],q[2][1]-q[0][1]));
        if(t<0||u>0||!Double.isFinite(x)||!Double.isFinite(y)||Math.hypot(x-q[0][0],y-q[0][1])>4*reach)return q;
        return new double[][]{q[0],q[1],{x,y},q[2]};
    }
    /** Each strip: x1,y1,x2,y2,leftExtent,rightExtent, in metres. */
    public static Result calculate(List<double[]> strips) {
        return calculateInternal(strips,0);
    }
    /** Ordered connected route, including bevel corner fillers. */
    public static Result calculateWithCorners(List<double[]> strips) {
        return calculateInternal(strips,1);
    }
    public static Result calculateWithMiter(List<double[]> strips) {
        return calculateInternal(strips,2);
    }
    private static Result calculateInternal(List<double[]> strips,int corners) {
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
        if(corners!=0) for(int i=1;i<strips.size();i++){
            double[][] q=corners==2?miterCorner(strips.get(i-1),strips.get(i)):cornerFiller(strips.get(i-1),strips.get(i));
            if(q==null)continue;
            Path p=new Path();
            p.moveTo((float)(q[0][0]-ox),(float)(q[0][1]-oy));
            for(int j=1;j<q.length;j++)p.lineTo((float)(q[j][0]-ox),(float)(q[j][1]-oy));p.close();
            if(!union.op(p,Path.Op.UNION))throw new IllegalStateException("Eckergänzung fehlgeschlagen");
            double twice=0;
            for(int j=1;j<q.length-1;j++)twice+=(q[j][0]-q[0][0])*(q[j+1][1]-q[0][1])-(q[j][1]-q[0][1])*(q[j+1][0]-q[0][0]);
            gross+=Math.abs(twice)/2;
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
