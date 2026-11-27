package de.feldaufmass.pro;

import android.content.Context;
import android.view.View;

/** Engineering hatch sample used by the profile legend. */
final class ProfileHatchView extends View {
 private final int pattern;
 ProfileHatchView(Context context,int pattern){super(context);this.pattern=pattern;}
 final android.graphics.Paint ink=new android.graphics.Paint(3);@Override protected void onDraw(android.graphics.Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),d=getResources().getDisplayMetrics().density;ink.setColor(0xffb7c1c1);ink.setStrokeWidth(d*.65f);c.save();c.clipRect(0,0,w,h);for(float y=3*d;y<h;y+=6*d)for(float x=3*d;x<w;x+=7*d){ink.setStyle(android.graphics.Paint.Style.FILL);if(pattern==0)c.drawLine(x-3*d,y+3*d,x+3*d,y-3*d,ink);else if(pattern==1){ink.setStyle(android.graphics.Paint.Style.STROKE);c.drawOval(x,y,x+3*d,y+2*d,ink);}else if(pattern==2)c.drawCircle(x,y,d*.6f,ink);else for(int k=0;k<3;k++)c.drawLine(x+k*d,y,x+(k+2)*d,y-2*d,ink);}c.restore();ink.setStyle(android.graphics.Paint.Style.STROKE);c.drawRect(0,0,w-1,h-1,ink);}
}
