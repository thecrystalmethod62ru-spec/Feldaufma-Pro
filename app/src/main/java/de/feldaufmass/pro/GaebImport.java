package de.feldaufmass.pro;

import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import android.util.Xml;

/** Limited GAEB DA XML LV reader; does not certify contractual or schema compliance. */
final class GaebImport {
 static JSONArray read(InputStream input)throws Exception{
  XmlPullParser x=Xml.newPullParser();
  x.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);
  x.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false);
  x.setInput(new java.io.FilterInputStream(input){
   long count;
   void check(int n)throws IOException{if(n>0&&(count+=n)>32L*1024*1024)throw new IOException("Datei zu gross");}
   public int read()throws IOException{int v=in.read();check(v<0?0:1);return v;}
   public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,len);check(n);return n;}
  },null);
  JSONArray result=new JSONArray();ArrayList<String> groups=new ArrayList<>();HashSet<String> seen=new HashSet<>();
  String phase=null,ns=null;boolean root=false;int itemDepth=-1,boqDepth=-1,boqs=0;
  String oz=null,id=null,unit="",qty="";StringBuilder shortText=new StringBuilder(),longText=new StringBuilder();
  int shortDepth=-1,longDepth=-1,qtyDepth=-1,unitDepth=-1;
  for(int event=x.getEventType();event!=XmlPullParser.END_DOCUMENT;event=x.nextToken()){
   if(event==XmlPullParser.DOCDECL||event==XmlPullParser.ENTITY_REF&&x.getText()==null)throw new IOException("DTD / unbekannte Entitaet nicht erlaubt");
   if(x.getDepth()>100)throw new IOException("XML zu tief");
   if(event==XmlPullParser.START_TAG){
    String tag=x.getName();int depth=x.getDepth();
    if(!root){root=true;ns=x.getNamespace();if(!"GAEB".equals(tag)||ns==null||!ns.matches("https?://www\\.gaeb\\.de/GAEB_DA_XML/DA(81|82|83|86)/3\\.[123]/?"))throw new IOException("Nur GAEB DA XML X81/X82/X83/X86 (3.1–3.3)");phase=ns.replaceAll(".*?/DA(\\d+)/.*","$1");}
    if(!ns.equals(x.getNamespace()))continue;
    if("BoQ".equals(tag)){if(++boqs>1)throw new IOException("Mehrere LV in einer Datei nicht unterstuetzt");boqDepth=depth;}
    if(boqDepth<0)continue;
    if("BoQCtgy".equals(tag)){String part=x.getAttributeValue(null,"RNoPart");if(part==null||part.trim().isEmpty())throw new IOException("Titel ohne OZ");groups.add(part.trim());}
    if("Item".equals(tag)){
     if(itemDepth>=0)throw new IOException("Verschachtelte Position");itemDepth=depth;String part=x.getAttributeValue(null,"RNoPart");if(part==null||part.trim().isEmpty())throw new IOException("Position ohne OZ");
     oz=(groups.isEmpty()?"":String.join(".",groups)+".")+part.trim();id=x.getAttributeValue(null,"ID");unit="";qty="";shortText.setLength(0);longText.setLength(0);
    }
    if(itemDepth>=0){
     if(depth==itemDepth+1&&"Qty".equals(tag))qtyDepth=depth;
     if(depth==itemDepth+1&&"QU".equals(tag))unitDepth=depth;
     if("TextOutlTxt".equals(tag))shortDepth=depth;
     if("DetailTxt".equals(tag))longDepth=depth;
     if("p".equals(tag)||"br".equals(tag)){if(shortDepth>=0)shortText.append(' ');if(longDepth>=0)longText.append(' ');}
    }
   }else if(event==XmlPullParser.TEXT||event==XmlPullParser.CDSECT||event==XmlPullParser.ENTITY_REF){
    String value=x.getText();if(value==null)continue;
    if(qtyDepth>=0)qty+=value;if(unitDepth>=0)unit+=value;
    if(shortDepth>=0)shortText.append(value);if(longDepth>=0)longText.append(value);
    if(shortText.length()+longText.length()>200000||qty.length()>100||unit.length()>100)throw new IOException("Position zu gross");
   }else if(event==XmlPullParser.END_TAG&&ns!=null&&ns.equals(x.getNamespace())){
    int depth=x.getDepth();String tag=x.getName();
    if(depth==qtyDepth)qtyDepth=-1;if(depth==unitDepth)unitDepth=-1;if(depth==shortDepth)shortDepth=-1;if(depth==longDepth)longDepth=-1;
    if(depth==itemDepth&&"Item".equals(tag)){
     if(!seen.add(oz))throw new IOException("Doppelte OZ: "+oz);
     String title=shortText.toString().trim().replaceAll("\\s+"," "),detail=longText.toString().trim().replaceAll("\\s+"," ");
     if(title.isEmpty())title=detail.isEmpty()?"Position "+oz:detail.substring(0,Math.min(160,detail.length()));
     String u=unit.trim();if("m2".equalsIgnoreCase(u))u="m²";if("m3".equalsIgnoreCase(u))u="m³";
     JSONObject o=new JSONObject().put("oz",oz).put("title",title).put("unit",u).put("source","manual").put("gaebPhase",phase).put("gaebId",id==null?"":id).put("gaebLongText",detail);
     if(!qty.trim().isEmpty()){double q=Double.parseDouble(qty.trim());if(!Double.isFinite(q)||q<0)throw new IOException("Ungueltige Menge: "+oz);o.put("target",q);}
     result.put(o);if(result.length()>10000)throw new IOException("Zu viele Positionen");itemDepth=-1;
    }
    if("BoQCtgy".equals(tag)&&!groups.isEmpty())groups.remove(groups.size()-1);
    if(depth==boqDepth&&"BoQ".equals(tag))boqDepth=-1;
   }
  }
  if(result.length()==0)throw new IOException("Keine LV-Positionen gefunden");
  return result;
 }
}
