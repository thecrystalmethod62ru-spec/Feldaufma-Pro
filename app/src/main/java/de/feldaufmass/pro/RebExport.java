package de.feldaufmass.pro;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Locale;

/** Limited DA11 2009 export, fixed 2.2.4 OZ mask without index. */
final class RebExport {
 static String pad(String s,int n)throws IOException{
  if(s.length()>n)throw new IOException("Feld zu lang: "+s);
  StringBuilder b=new StringBuilder(s);while(b.length()<n)b.append(' ');return b.toString();
 }
 static String oz(String value)throws IOException{
  if(!value.matches("[0-9]{2}\\.[0-9]{2}\\.[0-9]{4}"))throw new IOException("OZ muss dem Schema 00.00.0000 entsprechen: "+value);
  return value.replace(".","")+" ";
 }
 static String number(double n)throws IOException{
  if(!Double.isFinite(n)||n<0)throw new IOException("Ungueltige Menge");
  return BigDecimal.valueOf(n).stripTrailingZeros().toPlainString().replace('.',',');
 }
 static String header(String project)throws IOException{
  String name=DxfExport.ascii(project);if(name.length()>51)name=name.substring(0,51);
  return "00"+pad("",8)+"23.0032009"+pad(name,51)+"1122PPPPI";
 }
 static String record(String ozValue,String formula,int index)throws IOException{
  if(index<0||index>=9999*260)throw new IOException("Zu viele Ansätze");
  if(!formula.matches("[0-9,]+(?:\\*[0-9,]+)*="))throw new IOException("Ungueltige Formel");
  String address=String.format(Locale.ROOT,"%04d%c%d",index/260+1,'A'+(index%260)/10,index%10);
  return "11"+oz(ozValue)+pad("",18)+"91"+pad(formula,38)+address+pad("",5);
 }
 static String product(double... factors)throws IOException{
  StringBuilder b=new StringBuilder();
  for(double factor:factors){if(b.length()>0)b.append('*');b.append(number(factor));}
  return b.append('=').toString();
 }
}
