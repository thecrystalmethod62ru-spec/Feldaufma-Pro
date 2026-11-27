package de.feldaufmass.pro;

import android.graphics.Canvas;
import android.graphics.Paint;

/** Material rendering independent of pipe geometry and touch handling. */
final class ProfileMaterialRenderer {
    private ProfileMaterialRenderer(){}
    static void draw(Canvas c,Paint p,int viewWidth,int viewHeight,float d,float l,float t,float r,float b,int fill,int grain,boolean gravel){
        l=Math.max(0,l);t=Math.max(0,t);r=Math.min(viewWidth,r);b=Math.min(viewHeight,b);
        if(r<=l||b<=t)return;
        p.setStyle(Paint.Style.FILL);p.setColor(0xff080c0d);c.drawRect(l,t,r,b,p);
        c.save();c.clipRect(l,t,r,b);
        p.setColor(0xffb7c1c1);p.setStrokeWidth(Math.max(0.7f,d*.65f));
        boolean asphalt=fill==0xff383c3f;
        boolean nativeSoil=fill==0xff292520||fill==0xff302922;
        if(asphalt||nativeSoil){
            float step=(nativeSoil?14:6)*d;
            if(nativeSoil){
                for(float y=t+step/2;y<b;y+=step)for(float x=l+step/2;x<r;x+=step){
                    for(int k=0;k<3;k++)c.drawLine(x+k*2*d,y,x+(k*2+4)*d,y-4*d,p);
                }
            }else for(float x=l-(b-t);x<r;x+=step)c.drawLine(x,b,x+b-t,t,p);
        }else{
            float step=(gravel?7:3.5f)*d;
            for(int row=0; t+row*step<b;row++)for(int col=0;l+col*step<r;col++){
                float x=l+(col+.25f+((row*7+col*3)%5)*.1f)*step;
                float y=t+(row+.25f+((row*3+col*7)%5)*.1f)*step;
                if(gravel){p.setStyle(Paint.Style.STROKE);c.drawOval(x,y,x+step*.48f,y+step*.36f,p);}
                else{p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,Math.max(.5f,d*.42f),p);}
            }
        }
        p.setStyle(Paint.Style.FILL);c.restore();
    }
}
