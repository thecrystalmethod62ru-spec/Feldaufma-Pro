package de.feldaufmass.pro;

import java.util.Locale;

/** NMEA 0183 GGA and matching GST epoch. No HDOP-to-metres assumption. */
final class NmeaFix {
 final double lat,lon,accuracy;final int quality,satellites;final String utc;
 NmeaFix(double lat,double lon,double accuracy,int quality,int satellites,String utc){this.lat=lat;this.lon=lon;this.accuracy=accuracy;this.quality=quality;this.satellites=satellites;this.utc=utc;}
 boolean movable(){return quality==4&&Double.isFinite(accuracy)&&accuracy>0&&accuracy<=0.5;}
 String status(){return quality==4?"Fix-Code 4 (RTK Fixed / Empfängerstatus)":quality==5?"Fix-Code 5 (Float / Empfängerstatus)":"Fix-Code "+quality;}
 static String[] fields(String line){
  line=line.trim();int star=line.indexOf('*');if(!line.startsWith("$")||star<1||star+3!=line.length()||line.length()>512)return null;
  int xor=0;for(int j=1;j<star;j++){char c=line.charAt(j);if(c<32||c>126)return null;xor^=c;}
  try{if(xor!=Integer.parseInt(line.substring(star+1),16))return null;}catch(Exception e){return null;}
  return line.substring(1,star).split(",",-1);
 }
 static double coordinate(String raw,String hemisphere,boolean latitude){
  if(!raw.matches(latitude?"\\d{4}(?:\\.\\d+)?":"\\d{5}(?:\\.\\d+)?"))throw new IllegalArgumentException("Koordinatenformat");
  double n=Double.parseDouble(raw),degree=Math.floor(n/100),minutes=n-degree*100;
  if(minutes>=60||degree+minutes/60>(latitude?90:180))throw new IllegalArgumentException("Koordinatenbereich");
  if(hemisphere.equals(latitude?"S":"W"))return -(degree+minutes/60);
  if(!hemisphere.equals(latitude?"N":"E"))throw new IllegalArgumentException("Himmelsrichtung");
  return degree+minutes/60;
 }
 static double epoch(String s){
  if(!s.matches("\\d{6}(?:\\.\\d+)?"))return Double.NaN;
  int h=Integer.parseInt(s.substring(0,2)),m=Integer.parseInt(s.substring(2,4));double sec=Double.parseDouble(s.substring(4));
  return h<24&&m<60&&sec<60?h*3600+m*60+sec:Double.NaN;
 }
 static NmeaFix parse(String text){
  if(text==null||text.length()>32768)return null;
  String[] gga=null;java.util.ArrayList<String[]> gst=new java.util.ArrayList<>();
  for(String line:text.split("\\r?\\n")){String[] f=fields(line);if(f==null||f.length<9)continue;if(f[0].matches("[A-Z]{2}GGA"))gga=f;else if(f[0].matches("[A-Z]{2}GST"))gst.add(f);}
  if(gga==null)return null;
  try{
   int quality=Integer.parseInt(gga[6]),satellites=Integer.parseInt(gga[7]);double time=epoch(gga[1]);
   if(quality<1||quality>5||satellites<1||!Double.isFinite(time))return null;
   double lat=coordinate(gga[2],gga[3],true),lon=coordinate(gga[4],gga[5],false),accuracy=Double.NaN;
   for(String[] f:gst)if(Math.abs(epoch(f[1])-time)<0.001){
    try{double a=Double.parseDouble(f[6]),b=Double.parseDouble(f[7]);if(a>0&&b>0&&Double.isFinite(a)&&Double.isFinite(b)){double estimate=2.45*Math.max(a,b);accuracy=Double.isFinite(accuracy)?Math.max(accuracy,estimate):estimate;}}catch(Exception ignored){}
   }
   return new NmeaFix(lat,lon,accuracy,quality,satellites,gga[1]);
  }catch(Exception e){return null;}
 }
}
