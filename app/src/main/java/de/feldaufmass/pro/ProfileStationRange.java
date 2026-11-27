package de.feldaufmass.pro;
import java.util.function.IntToDoubleFunction;
/** Computes station bounds independently of Android views. */
final class ProfileStationRange {
 private ProfileStationRange(){}
 static double[] resolve(int section,IntToDoubleFunction length){
 if(section<0)return null;
 double start=0;
 for(int j=0;j<section;j++){double len=length.applyAsDouble(j+1);if(!Double.isFinite(len)||len<=0)return null;start+=len;}
 double segmentLength=length.applyAsDouble(section+1);
 if(!Double.isFinite(segmentLength)||segmentLength<=0)return null;
 double end=start+segmentLength;
 return !Double.isFinite(start)||!Double.isFinite(end)||end<=start?null:new double[]{start,end};
 }
}
