package de.feldaufmass.pro;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Vertical excavation prisms sharing a common reference elevation. */
public final class StripVolume {
    private StripVolume() {}
    public static final class Result {
        public final double gross, net, overlap;
        Result(double gross, double net) {
            this.gross = gross;
            this.net = net;
            this.overlap = Math.max(0, gross-net);
        }
    }
    /** Each strip: x1,y1,x2,y2,leftExtent,rightExtent,depth (metres). */
    public static Result calculate(List<double[]> strips) {
        TreeSet<Double> levels = new TreeSet<>();
        double gross = 0, net = 0, previous = 0;
        for (double[] s : strips) {
            if (s == null || s.length != 7) throw new IllegalArgumentException("Ungültiger Aushubstreifen");
            for (double v : s) if (!Double.isFinite(v)) throw new IllegalArgumentException("Ungültiges Maß");
            if (s[4]<0 || s[5]<0 || s[6]<0) throw new IllegalArgumentException("Negative Breite oder Tiefe");
            gross += Math.hypot(s[2]-s[0],s[3]-s[1])*(s[4]+s[5])*s[6];
            if (s[6]>0) levels.add(s[6]);
        }
        for (double level : levels) {
            List<double[]> active = new ArrayList<>();
            for (double[] s : strips) if (s[6]>=level && s[4]+s[5]>0 && Math.hypot(s[2]-s[0],s[3]-s[1])>0)
                active.add(java.util.Arrays.copyOf(s,6));
            if (!active.isEmpty()) net += StripOverlap.calculate(active).net*(level-previous);
            previous=level;
        }
        if (!Double.isFinite(gross)||!Double.isFinite(net)) throw new IllegalArgumentException("Volumen außerhalb des Wertebereichs");
        return new Result(gross,Math.min(gross,Math.max(0,net)));
    }
}
