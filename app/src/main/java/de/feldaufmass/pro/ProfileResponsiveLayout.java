package de.feldaufmass.pro;
import android.widget.LinearLayout;
import android.view.View;

final class ProfileResponsiveLayout {
 private ProfileResponsiveLayout(){}
 static void bind(LinearLayout profileWork,View fullCross,View profileInfoScroll){
 profileWork.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
   if(right<=left||bottom<=top)return;
   // Use the window orientation rather than the remaining work area: toolbars can make a portrait work area wider than tall.
   boolean landscape=profileWork.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
   int availableWidth=right-left-profileWork.getPaddingLeft()-profileWork.getPaddingRight();
   int availableHeight=bottom-top-profileWork.getPaddingTop()-profileWork.getPaddingBottom();
   if(availableWidth<=0||availableHeight<=0)return;
   boolean wide=availableWidth>=dp(profileWork,560)||(landscape&&availableWidth>=dp(profileWork,320));
   int profileOrientation=wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL;
   int panelSize=wide?Math.min(dp(profileWork,280),Math.round(availableWidth*.40f)):Math.min(dp(profileWork,220),Math.round(availableHeight*.40f));
   LinearLayout.LayoutParams info=(LinearLayout.LayoutParams)profileInfoScroll.getLayoutParams();
   if(profileWork.getOrientation()==profileOrientation&&info.weight==0&&(wide?info.width:info.height)==panelSize)return;
   profileWork.setOrientation(profileOrientation);
   fullCross.setLayoutParams(wide?new LinearLayout.LayoutParams(0,-1,1):new LinearLayout.LayoutParams(-1,0,1));
   profileInfoScroll.setLayoutParams(wide?new LinearLayout.LayoutParams(panelSize,-1):new LinearLayout.LayoutParams(-1,panelSize));
  });
 }
 private static int dp(View view,int value){return Math.round(value*view.getResources().getDisplayMetrics().density);}
}
