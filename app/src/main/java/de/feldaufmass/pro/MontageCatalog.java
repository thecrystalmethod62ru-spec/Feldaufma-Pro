package de.feldaufmass.pro;

/** Standard fitting types. Drawing symbols require separate normative verification. */
public final class MontageCatalog {
    private MontageCatalog() {}
    public enum Fitting {
        BEND_11_25("Bogen 11,25°",2,11.25),
        BEND_22_5("Bogen 22,5°",2,22.5),
        BEND_30("Bogen 30°",2,30),
        BEND_45("Bogen 45°",2,45),
        BEND_90("Bogen 90°",2,90),
        TEE("T-Stück",3,0),
        CROSS("Kreuzstück",4,0),
        REDUCER("Reduzierung",2,0),
        COUPLING("Muffe",2,0),
        FLANGE("Flansch",2,0),
        CAP("Endkappe",1,0),
        GATE_VALVE("Schieber",2,0);
        public final String label;
        public final int ports;
        public final double bendAngle;
        Fitting(String label,int ports,double bendAngle){
            this.label=label;this.ports=ports;this.bendAngle=bendAngle;
        }
    }
    public static boolean validDn(int dn){return dn>0 && dn<=9999;}
    public static double normalizeRotation(double degrees){
        if(!Double.isFinite(degrees))throw new IllegalArgumentException("Ungültiger Winkel");
        return ((degrees%360)+360)%360;
    }
    public static String[] labels(){
        Fitting[] values=Fitting.values();
        String[] result=new String[values.length];
        for(int i=0;i<values.length;i++)result[i]=values[i].label;
        return result;
    }
}
