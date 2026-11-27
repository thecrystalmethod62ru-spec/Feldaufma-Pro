package de.feldaufmass.pro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Engineering hatch sample matching the cross-section renderer. */
final class ProfileHatchView extends View {
 private final int pattern;
 private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
 ProfileHatchView(Context context,int pattern){super(context);this.pattern=pattern;}
 @Override protected void onDraw(Canvas c){
  super.onDraw(c);
  float w=getWidth(),h=getHeight(),d=getResources().getDisplayMetrics().density;
  ink.setStyle(Paint.Style.FILL);ink.setColor(0xff080c0d);c.drawRect(0,0,w,h,ink);
  ink.setColor(0xffb7c1c1);ink.setStrokeWidth(Math.max(.7f,d*.65f));
  c.save();c.clipRect(0,0,w,h);
  if(pattern==0){
   for(float x=-h;x<w;x+=6*d)c.drawLine(x,h,x+h,0,ink);
  }else if(pattern==3){
   for(float y=7*d;y<h;y+=14*d)for(float x=7*d;x<w;x+=14*d)
    for(int k=0;k<3;k++)c.drawLine(x+k*2*d,y,x+(k*2+4)*d,y-4*d,ink);
  }else{
   float step=(pattern==1?7:3.5f)*d;
   for(int row=0;row*step<h;row++)for(int col=0;col*step<w;col++){
    float x=(col+.25f+((row*7+col*3)%5)*.1f)*step;
    float y=(row+.25f+((row*3+col*7)%5)*.1f)*step;
    if(pattern==1){ink.setStyle(Paint.Style.STROKE);c.drawOval(x,y,x+step*.48f,y+step*.36f,ink);}
    else{ink.setStyle(Paint.Style.FILL);c.drawCircle(x,y,Math.max(.5f,d*.42f),ink);}
   }
  }
  c.restore();ink.setStyle(Paint.Style.STROKE);c.drawRect(0,0,w-1,h-1,ink);
 }
}
