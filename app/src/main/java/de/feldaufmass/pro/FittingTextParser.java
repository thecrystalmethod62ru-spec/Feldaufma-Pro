package de.feldaufmass.pro;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FittingTextParser {
    private static final int FLAGS=Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE;
    private static final Pattern NAME=Pattern.compile("\\b(Bogen|T[- ]?Stück|Kreuzstück|Reduzierung|Muffe|Flansch|Endkappe|Schieber)\\b",FLAGS);
    private static final Pattern ANGLE=Pattern.compile("^\\s*[:=]?\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:°|Grad\\b)",FLAGS);
    private static final Pattern DN=Pattern.compile("\\bDN\\s*([1-9]\\d{0,3})(?![\\d/]|[.,]\\d)",FLAGS);
    private static final Pattern STATION_LABEL=Pattern.compile("\\b(?:Station\\b|Stat\\.)",FLAGS);
    private static final Pattern STATION_VALUE=Pattern.compile(
            "\\s*[:=]?\\s*(?:(\\d+)\\s*\\+\\s*(\\d{3}(?:[.,]\\d+)?)(?:\\s*m\\b)?|(\\d+(?:[.,]\\d+)?)\\s*m\\b)(?![\\w+]|[.,]\\d)",FLAGS);

    static final class Candidate {
        final MontageCatalog.Fitting type;
        final String dn,station,evidence;
        final int line;
        Candidate(MontageCatalog.Fitting type,String dn,String station,String evidence,int line){
            this.type=type;this.dn=dn;this.station=station;this.evidence=evidence;this.line=line;
        }
    }

    static List<Candidate> parse(String text) {
        List<Candidate> result=new ArrayList<>();
        String[] lines=text.split("\\r?\\n");
        for(int i=0;i<lines.length;i++){
            String line=lines[i].trim();
            Matcher name=NAME.matcher(line);
            if(!name.find())continue;
            String label=name.group(1);int end=name.end();
            if(name.find())continue;
            MontageCatalog.Fitting type=null;
            if(label.equalsIgnoreCase("Bogen")){
                Matcher angle=ANGLE.matcher(line.substring(end));
                if(!angle.find())continue;
                double degrees=Double.parseDouble(angle.group(1).replace(',','.'));
                for(MontageCatalog.Fitting candidate:MontageCatalog.Fitting.values()){
                    if(candidate.bendAngle>0&&candidate.bendAngle==degrees)type=candidate;
                }
            }else{
                for(MontageCatalog.Fitting candidate:MontageCatalog.Fitting.values()){
                    if(normalize(candidate.label).equalsIgnoreCase(normalize(label)))type=candidate;
                }
            }
            if(type!=null)result.add(new Candidate(type,unique(DN,line),station(line),line,i+1));
        }
        return result;
    }

    private static String station(String line){
        Matcher label=STATION_LABEL.matcher(line);
        if(!label.find())return "";
        int start=label.end();
        if(label.find())return "";
        Matcher value=STATION_VALUE.matcher(line).region(start,line.length());
        if(!value.lookingAt())return "";
        if(value.group(3)!=null)return value.group(3);
        // Three metre digits follow the kilometre prefix, including leading zeros.
        String metres=value.group(1)+value.group(2);
        return metres.replaceFirst("^0+(?=[0-9])","");
    }

    private static String normalize(String label){return label.replace("-","").replace(" ","");}
    private static String unique(Pattern pattern,String line){
        Matcher m=pattern.matcher(line);
        if(!m.find())return "";
        String value=m.group(1);
        return m.find()?"":value;
    }
}
