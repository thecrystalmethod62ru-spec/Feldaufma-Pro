package de.feldaufmass.pro;

import android.util.Xml;
import org.xmlpull.v1.XmlSerializer;
import java.io.StringWriter;
import java.io.IOException;
import java.util.TreeMap;
import java.util.ArrayList;

/** Experimental X31 3.3 writer for the same restricted OZ mask as the DA11 writer. */
final class GaebX31Export {
 static void tag(XmlSerializer x,String name,String value)throws IOException{
  x.startTag(null,name);x.text(value);x.endTag(null,name);
 }
 static void breakdown(XmlSerializer x,String type,int length)throws IOException{
  x.startTag(null,"BoQBkdn");tag(x,"Type",type);tag(x,"Length",Integer.toString(length));tag(x,"Num","Yes");x.endTag(null,"BoQBkdn");
 }
 static String write(String project,String da11)throws IOException{
  TreeMap<String,TreeMap<String,TreeMap<String,ArrayList<String>>>> tree=new TreeMap<>();
  int count=0;
  for(String row:da11.split("\\r?\\n")){
   if(row.startsWith("00")||row.isEmpty())continue;
   if(row.length()!=80||!row.matches("11[0-9]{8} .{69}"))throw new IOException("Ungueltige DA11-Zeile");
   String a=row.substring(2,4),b=row.substring(4,6),c=row.substring(6,10);
   String takeoff=RebExport.pad("",11)+row.substring(11);
   tree.computeIfAbsent(a,k->new TreeMap<>()).computeIfAbsent(b,k->new TreeMap<>()).computeIfAbsent(c,k->new ArrayList<>()).add(takeoff);count++;
  }
  if(count==0)throw new IOException("Keine Rechenansaetze");
  StringWriter out=new StringWriter();XmlSerializer x=Xml.newSerializer();x.setOutput(out);
  x.startDocument("UTF-8",null);
  x.startTag(null,"GAEB");x.attribute(null,"xmlns","http://www.gaeb.de/GAEB_DA_XML/DA31/3.3");
  x.startTag(null,"GAEBInfo");tag(x,"Version","3.3");tag(x,"VersDate","2023-01");
  tag(x,"Date",new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT).format(new java.util.Date()));
  tag(x,"Time",new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.ROOT).format(new java.util.Date()));
  tag(x,"ProgName","Feldaufmass Pro");x.endTag(null,"GAEBInfo");
  x.startTag(null,"QtyDeterm");
  x.startTag(null,"PrjInfo");tag(x,"RefPrjName",project);x.endTag(null,"PrjInfo");
  x.startTag(null,"QtyDetermInfo");tag(x,"MethodDescription","REB23003-2009");tag(x,"ProjDescr",project);x.endTag(null,"QtyDetermInfo");
  tag(x,"DP","31");
  x.startTag(null,"BoQ");x.attribute(null,"ID","BOQ1");tag(x,"RefBoQName",project);
  breakdown(x,"BoQLevel",2);breakdown(x,"BoQLevel",2);breakdown(x,"Item",4);
  x.startTag(null,"BoQBody");
  for(String a:tree.keySet()){
   x.startTag(null,"BoQCtgy");x.attribute(null,"ID","G"+a);x.attribute(null,"RNoPart",a);x.startTag(null,"BoQBody");
   for(String b:tree.get(a).keySet()){
    x.startTag(null,"BoQCtgy");x.attribute(null,"ID","G"+a+b);x.attribute(null,"RNoPart",b);x.startTag(null,"BoQBody");x.startTag(null,"Itemlist");
    for(String c:tree.get(a).get(b).keySet()){
     x.startTag(null,"Item");x.attribute(null,"ID","P"+a+b+c);x.attribute(null,"RNoPart",c);x.startTag(null,"QtyDeterm");
     for(String row:tree.get(a).get(b).get(c)){
      x.startTag(null,"QDetermItem");x.startTag(null,"QTakeoff");x.attribute(null,"Row",row);x.endTag(null,"QTakeoff");x.endTag(null,"QDetermItem");
     }
     x.endTag(null,"QtyDeterm");x.endTag(null,"Item");
    }
    x.endTag(null,"Itemlist");x.endTag(null,"BoQBody");x.endTag(null,"BoQCtgy");
   }
   x.endTag(null,"BoQBody");x.endTag(null,"BoQCtgy");
  }
  x.endTag(null,"BoQBody");x.endTag(null,"BoQ");x.endTag(null,"QtyDeterm");x.endTag(null,"GAEB");x.endDocument();x.flush();return out.toString();
 }
}
