package de.feldaufmass.pro;

import android.graphics.Canvas;
import android.graphics.Paint;

/** Material rendering independent of pipe geometry and touch handling. */
final class ProfileMaterialRenderer {
    enum Pattern { ASPHALT, GRAVEL, BACKFILL, NATIVE_SOIL, SAND }
    private ProfileMaterialRenderer(){}
    static void draw(Canvas c,Paint p,int viewWidth,int viewHeight,float d,float l,float t,float r,float b,Pattern pattern){
        l=Math.max(0,l);t=Math.max(0,t);r=Math.min(viewWidth,r);b=Math.min(viewHeight,b);
        if(r<=l||b<=t)return;
        p.setStyle(Paint.Style.FILL);p.setColor(pattern==Pattern.ASPHALT?0xff171c20:pattern==Pattern.GRAVEL?0xff101619:0xff080c0d);c.drawRect(l,t,r,b,p);
        c.save();c.clipRect(l,t,r,b);
        p.setColor(pattern==Pattern.NATIVE_SOIL?0xffb0b0b0:pattern==Pattern.BACKFILL?0xff929292:0xffd0d0d0);p.setStrokeWidth(Math.max(0.7f,d*.65f));
        boolean asphalt=pattern==Pattern.ASPHALT;
        boolean nativeSoil=pattern==Pattern.NATIVE_SOIL;
        boolean gravel=pattern==Pattern.GRAVEL;
        if(asphalt||nativeSoil){
            float step=(nativeSoil?6:3.5f)*d;
            for(float k=(float)Math.floor((l+t)/step)*step;k<r+b;k+=step)c.drawLine(k-b,b,k-t,t,p);
            if(asphalt)for(float k=(float)Math.floor((l-b)/step)*step;k<r-t;k+=step)c.drawLine(k+b,b,k+t,t,p);
        }else{
            if(pattern==Pattern.BACKFILL){
                p.setColor(0xff565656);
                float hatch=8*d;for(float k=(float)Math.floor((l+t)/hatch)*hatch;k<r+b;k+=hatch)c.drawLine(k-b,b,k-t,t,p);
                p.setColor(0xffa3a3a3);
            }
            float step=(gravel?6.5f:pattern==Pattern.SAND?2.6f:4.5f)*d;
            for(int row=(int)Math.floor(t/step); row*step<b;row++)for(int col=(int)Math.floor(l/step);col*step<r;col++){
                float x=(col+.20f+((row*7+col*3)%5)*.11f)*step;
                float y=(row+.20f+((row*3+col*7)%5)*.11f)*step;
                if(gravel){p.setStyle(Paint.Style.STROKE);android.graphics.Path stone=new android.graphics.Path();stone.moveTo(x,y+step*.20f);stone.lineTo(x+step*.18f,y);stone.lineTo(x+step*.47f,y+step*.08f);stone.lineTo(x+step*.55f,y+step*.30f);stone.lineTo(x+step*.24f,y+step*.43f);stone.close();c.drawPath(stone,p);}
                else{p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,Math.max(.5f,d*.42f),p);}
            }
        }
        p.setStyle(Paint.Style.FILL);c.restore();
    }
}
