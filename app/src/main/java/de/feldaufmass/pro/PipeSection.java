package de.feldaufmass.pro;

/** Circular pipe on bedding, all dimensions in metres. */
public final class PipeSection {
    public final double ground, trenchBottom, outerBottom, outerTop, invert, cover;
    public PipeSection(double ground,double depth,double bedding,double outerDiameter,double wallThickness) {
        double[] values={ground,depth,bedding,outerDiameter,wallThickness};
        for(double v:values)if(!Double.isFinite(v))throw new IllegalArgumentException("Ungültiges Rohrmaß");
        if(depth<=0||bedding<0||outerDiameter<=0||wallThickness<=0||wallThickness>=outerDiameter/2)
            throw new IllegalArgumentException("Ungültige Rohrabmessungen");
        this.ground=ground;
        trenchBottom=ground-depth;
        outerBottom=trenchBottom+bedding;
        outerTop=outerBottom+outerDiameter;
        invert=outerBottom+wallThickness;
        cover=ground-outerTop;
    }
    public boolean fits(){return cover>=0;}
    public boolean meetsCover(double minimum){
        if(!Double.isFinite(minimum)||minimum<0)throw new IllegalArgumentException("Ungültige Mindestüberdeckung");
        return cover>=minimum;
    }
}
