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
   boolean wide=right-left>=dp(profileWork,560)||(landscape&&right-left>=dp(profileWork,320));
   int profileOrientation=wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL;
   if(profileWork.getOrientation()==profileOrientation)return;
   profileWork.setOrientation(profileOrientation);
   fullCross.setLayoutParams(wide?new LinearLayout.LayoutParams(0,-1,3):new LinearLayout.LayoutParams(-1,0,3));
   profileInfoScroll.setLayoutParams(wide?new LinearLayout.LayoutParams(0,-1,2):new LinearLayout.LayoutParams(-1,0,2));
  });
 }
 private static int dp(View view,int value){return Math.round(value*view.getResources().getDisplayMetrics().density);}
}
