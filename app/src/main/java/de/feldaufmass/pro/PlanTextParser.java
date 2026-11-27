package de.feldaufmass.pro;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/** Extracts unverified candidates from supplied plan text, never measured quantities. */
public final class PlanTextParser {
    private PlanTextParser() {}
    private static final String[] TYPES = {"Trinkwasser","Gas","Strom","Fernwärme","Telekommunikation","Schmutzwasser","Regenwasser"};
    private static final String[] ALIASES = {"Trinkwasser|Wasserleitung","Gasleitung|Gas","Stromkabel|Strom","Fernwärme","Telekommunikation|Glasfaser|LWL","Schmutzwasser","Regenwasser"};
    private static final Pattern DN = Pattern.compile("\\bDN\\s*([0-9]{1,4})\\b", Pattern.CASE_INSENSITIVE);
    static final String MATERIAL_NAME = "PE\\s*100(?:-RC)?|PVC(?:-U)?|PP|Stahl|Guss|Beton";
    private static final Pattern MATERIAL = Pattern.compile("\\b("+MATERIAL_NAME+")\\b", Pattern.CASE_INSENSITIVE);
    public static final class Candidate {
        public final String type, nominalDiameter, material, sourceLine;
        public final int lineNumber;
        public final boolean requiresReview = true;
        Candidate(String type, String dn, String material, String source, int line) {
            this.type=type; this.nominalDiameter=dn; this.material=material;
            this.sourceLine=source; this.lineNumber=line;
        }
    }
    private static String unique(Pattern pattern, String line) {
        Matcher m=pattern.matcher(line);
        if (!m.find()) return "";
        String value=m.group(1);
        while(m.find()) if(!value.equalsIgnoreCase(m.group(1))) return "";
        return value;
    }
    public static List<Candidate> parse(String text) {
        List<Candidate> result=new ArrayList<>();
        if(text==null) return result;
        String[] lines=text.split("\\r?\\n");
        for(int i=0;i<lines.length;i++) {
            String line=lines[i].trim();
            List<String> types=new ArrayList<>();
            for(int j=0;j<TYPES.length;j++) {
                if(Pattern.compile("\\b(?:"+ALIASES[j]+")\\b", Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(line).find()) types.add(TYPES[j]);
            }
            for(String type:types) result.add(new Candidate(type,types.size()==1?unique(DN,line):"",types.size()==1?unique(MATERIAL,line):"",line,i+1));
        }
        return result;
    }
}
