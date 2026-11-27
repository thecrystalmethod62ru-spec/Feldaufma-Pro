package de.feldaufmass.pro;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** Landscape plan sheet using the A3 layout (section sidebar and project drawing). */
final class ProjectPlanPdf {
    private final MainActivity activity;
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final int INK=0xff19344c, BLUE=0xff174dcc;
    private static final int ROWS=6;

    ProjectPlanPdf(MainActivity activity){this.activity=activity;}

    private void label(Canvas c,String value,float x,float y,float size,int width,boolean bold){
        pen.setColor(bold?INK:0xff465968);
        pen.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);
        pen.setTextSize(size);
        String text=value.replace('\n',' ');
        if(pen.measureText(text)>width){
            int n=pen.breakText(text,true,width-pen.measureText("…"),null);
            text=text.substring(0,n)+"…";
        }
        c.drawText(text,x,y,pen);
    }

    private void view(Canvas c,View v,int x,int y,int width,int height){
        // Render at device density for consistent paper text sizes across phones.
        float density=activity.getResources().getDisplayMetrics().density;
        c.save();c.clipRect(x,y,x+width,y+height);c.translate(x,y);
        int vw=Math.round(width*density),vh=Math.round(height*density);
        c.scale((float)width/vw,(float)height/vh);v.layout(0,0,vw,vh);v.draw(c);c.restore();
    }

    private boolean profileAvailable(){return activity.w>0&&activity.d>0;}
    private String metres(double m){return Double.isFinite(m)?activity.reportMeasure(m):"offen";}

    int append(PdfDocument pdf,int next,RouteSketch route)throws Exception{
        int sections=Math.max(0,route.vertices.size()-1);
        JSONArray profiles=new PipeLayoutStore(activity.getPreferences(0)).sections();
        double totalVolume=activity.currentTrenchQuantities()[1];
        boolean stationVolumes=Double.isFinite(totalVolume)&&Math.abs(route.measuredLength()-activity.l)<.001;
        double station=0;
        for(int first=0;first<Math.max(1,sections);first+=ROWS){
            PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(842,595,next++).create());
            try{
                Canvas c=page.getCanvas();
                label(c,activity.projectHeader.getText().toString(),26,39,25,600,true);
                label(c,"Abschnittsplan · Lage und Aufmaß",26,61,13,550,false);
                label(c,"Feldaufmaß Pro",667,43,16,155,true);
                pen.setColor(INK);c.drawLine(26,76,816,76,pen);
                pen.setColor(0xffedf2f5);c.drawRect(26,91,214,535,pen);
                label(c,"Abschnitte",39,121,18,168,true);
                if(sections==0)label(c,"Keine Trassenabschnitte",39,150,12,164,false);
                for(int i=first;i<Math.min(sections,first+ROWS);i++){
                    double length=route.vertices.get(i+1).metres;
                    int y=155+(i-first)*59;
                    label(c,String.format(Locale.GERMANY,"A%02d",i+1),39,y,15,162,true);
                    label(c,metres(station)+" – "+metres(station+length),39,y+17,11,164,false);
                    String detail=metres(length);
                    if(stationVolumes){
                        double area=activity.w*activity.d,volume=length*area;
                        for(int j=0;j<profiles.length();j++){
                            JSONObject row=profiles.getJSONObject(j);
                            double span=Math.max(0,Math.min(station+length,row.getDouble("to"))-Math.max(station,row.getDouble("from")));
                            volume+=span*(row.getDouble("width")*row.getDouble("depth")-area);
                        }
                        detail+=String.format(Locale.GERMANY,"  /  %.2f m³",volume);
                    }
                    label(c,detail,39,y+33,11,164,false);
                    station+=length;
                }
                if(Double.isFinite(totalVolume))label(c,String.format(Locale.GERMANY,"Aushub   %.2f m³",totalVolume),39,521,13,164,true);
                label(c,sections>0?"Gesamttrasse":"Lageplan",228,116,16,580,true);
                if(route.vertices.isEmpty()&&route.singleObjects.isEmpty()&&activity.l>0)
                    view(c,activity.new Sketch(),228,128,581,271);
                else view(c,route,228,125,581,303);
                if(profileAvailable()){
                    label(c,"Regelprofil",228,451,14,140,true);
                    view(c,activity.new Profile(),228,461,90,75);
                    label(c,"Breite  "+metres(activity.w),328,480,12,185,false);
                    label(c,"Tiefe    "+metres(activity.d),328,500,12,185,false);
                }
                if(!route.singleObjects.isEmpty()){
                    label(c,"Einzelobjekte · "+route.singleObjects.size(),525,451,14,275,true);
                    label(c,route.singleObjects.get(0).type+(route.singleObjects.size()>1?" u. a.":""),525,477,13,275,false);
                }
                pen.setColor(BLUE);c.drawLine(26,552,816,552,pen);
                label(c,"Lageplan · schematisch, ohne festen Druckmaßstab",26,575,11,600,false);
                label(c,"Blatt "+(next-1),738,575,11,78,true);
            }finally{pdf.finishPage(page);}
        }
        if(profileAvailable())next=profilePage(pdf,next,"Regelprofil · Haupttrasse",activity.new Profile());
        for(int i=0;i<profiles.length();i++){
            JSONObject row=profiles.getJSONObject(i);
            PipeLayoutView profile=new PipeLayoutView(activity);
            profile.setLayoutData(PipeLayoutStore.decode(row.getJSONArray("pipes").toString()),row.getDouble("width"),row.getDouble("depth"));
            next=profilePage(pdf,next,"Regelprofil · "+metres(row.getDouble("from"))+" – "+metres(row.getDouble("to")),profile);
        }
        return next;
    }

    private int profilePage(PdfDocument pdf,int next,String title,View profile){
        PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,next++).create());
        try{
            Canvas c=page.getCanvas();label(c,title,36,48,20,523,true);
            label(c,activity.projectHeader.getText().toString(),36,75,13,523,false);
            view(c,profile,36,110,523,436);
            label(c,"Schematische Darstellung · Blatt "+(next-1),36,800,11,523,false);
        }finally{pdf.finishPage(page);}
        return next;
    }
}
