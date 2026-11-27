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
  // Use the same material renderer as the actual trench cross-section.
  int[] fills={0xff383c3f,0xff666052,0xff503a29,0xff292520};
  int index=Math.max(0,Math.min(pattern,fills.length-1));
  ProfileMaterialRenderer.draw(c,ink,getWidth(),getHeight(),d,0,0,w,h,fills[index],0xffb7c1c1,index==1);
  ink.setStyle(Paint.Style.STROKE);ink.setColor(0xffb7c1c1);ink.setStrokeWidth(Math.max(.7f,d*.65f));
  c.drawRect(0,0,w-1,h-1,ink);ink.setStyle(Paint.Style.FILL);
 }
}
