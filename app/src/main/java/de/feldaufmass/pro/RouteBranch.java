package de.feldaufmass.pro;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.List;

public final class RouteBranch {
    public final String id,name,surface;
    public final double station,width,depth;
    private final double[] lengths,directions;
    public RouteBranch(String id,String name,double station,double width,double depth,String surface,double[] lengths,double[] directions){
        if(id==null||id.trim().isEmpty()||name==null||surface==null||
           !Double.isFinite(station)||station<0||!Double.isFinite(width)||width<=0||
           !Double.isFinite(depth)||depth<=0||lengths==null||directions==null||
           lengths.length==0||lengths.length!=directions.length)throw new IllegalArgumentException("Ungültige Abzweigung");
        for(int i=0;i<lengths.length;i++)if(!Double.isFinite(lengths[i])||lengths[i]<=0||!Double.isFinite(directions[i]))throw new IllegalArgumentException("Ungültige Abschnittsmaße");
        this.id=id;this.name=name;this.station=station;this.width=width;this.depth=depth;this.surface=surface;
        this.lengths=lengths.clone();this.directions=directions.clone();
    }
    public List<double[]> vertices(List<double[]> main){return RouteBranchGeometry.build(main,station,lengths,directions);}
    public double length(){double sum=0;for(double l:lengths)sum+=l;return sum;}
    public double nominalArea(){return length()*width;}
    public double nominalVolume(){return nominalArea()*depth;}
    public JSONObject toJson() throws JSONException {
        JSONObject o=new JSONObject();o.put("id",id);o.put("name",name);o.put("station",station);o.put("width",width);o.put("depth",depth);o.put("surface",surface);
        JSONArray l=new JSONArray(),d=new JSONArray();for(int i=0;i<lengths.length;i++){l.put(lengths[i]);d.put(directions[i]);}
        o.put("lengths",l);o.put("directions",d);return o;
    }
    public static RouteBranch fromJson(JSONObject o) throws JSONException {
        JSONArray l=o.getJSONArray("lengths"),d=o.getJSONArray("directions");
        if(l.length()!=d.length())throw new JSONException("Abweichende Abschnittszahl");
        double[] lengths=new double[l.length()],directions=new double[d.length()];
        for(int i=0;i<l.length();i++){lengths[i]=l.getDouble(i);directions[i]=d.getDouble(i);}
        return new RouteBranch(o.getString("id"),o.getString("name"),o.getDouble("station"),o.getDouble("width"),o.getDouble("depth"),o.getString("surface"),lengths,directions);
    }
}
