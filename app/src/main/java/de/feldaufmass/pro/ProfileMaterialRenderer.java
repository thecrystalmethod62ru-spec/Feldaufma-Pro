package de.feldaufmass.pro;

import android.graphics.Canvas;
import android.graphics.Paint;

/** Material rendering independent of pipe geometry and touch handling. */
final class ProfileMaterialRenderer {
    enum Pattern { ASPHALT, GRAVEL, BACKFILL, NATIVE_SOIL }
    private ProfileMaterialRenderer(){}
    static void draw(Canvas c,Paint p,int viewWidth,int viewHeight,float d,float l,float t,float r,float b,Pattern pattern){
        l=Math.max(0,l);t=Math.max(0,t);r=Math.min(viewWidth,r);b=Math.min(viewHeight,b);
        if(r<=l||b<=t)return;
        p.setStyle(Paint.Style.FILL);p.setColor(pattern==Pattern.ASPHALT?0xff171c20:pattern==Pattern.GRAVEL?0xff101619:0xff080c0d);c.drawRect(l,t,r,b,p);
        c.save();c.clipRect(l,t,r,b);
        p.setColor(pattern==Pattern.NATIVE_SOIL?0xff879391:pattern==Pattern.BACKFILL?0xffa3afb0:0xffc1ccce);p.setStrokeWidth(Math.max(0.7f,d*.65f));
        boolean asphalt=pattern==Pattern.ASPHALT;
        boolean nativeSoil=pattern==Pattern.NATIVE_SOIL;
        boolean gravel=pattern==Pattern.GRAVEL;
        if(asphalt||nativeSoil){
            float step=(nativeSoil?12:4)*d;
            if(nativeSoil){
                for(int row=0; row*step<b-t+step;row++){
                    float y=t+row*step;
                    for(float x=l-step+(row%2)*step/2;x<r;x+=step){
                        for(int k=0;k<3;k++)c.drawLine(x+k*2.2f*d,y,x+(k*2.2f+4)*d,y-4*d,p);
                    }
                }
            }else for(float x=l-(b-t);x<r;x+=step)c.drawLine(x,b,x+b-t,t,p);
        }else{
            float step=(gravel?8:4.5f)*d;
            for(int row=0; t+row*step<b;row++)for(int col=0;l+col*step<r;col++){
                float x=l+(col+.25f+((row*7+col*3)%5)*.1f)*step;
                float y=t+(row+.25f+((row*3+col*7)%5)*.1f)*step;
                if(gravel){p.setStyle(Paint.Style.STROKE);android.graphics.Path stone=new android.graphics.Path();stone.moveTo(x,y+step*.20f);stone.lineTo(x+step*.18f,y);stone.lineTo(x+step*.47f,y+step*.08f);stone.lineTo(x+step*.55f,y+step*.30f);stone.lineTo(x+step*.24f,y+step*.43f);stone.close();c.drawPath(stone,p);}
                else{p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,Math.max(.5f,d*.42f),p);}
            }
        }
        p.setStyle(Paint.Style.FILL);c.restore();
    }
}
