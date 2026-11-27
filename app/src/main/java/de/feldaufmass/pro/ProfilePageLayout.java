package de.feldaufmass.pro;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

final class ProfilePageLayout {
 final LinearLayout profilePage,profileWork,profileInfo;
 final TextView profilePageTitle,profileDimensions,profileMaterial;
 final PipeLayoutView fullCross;
 final ScrollView profileInfoScroll;
 Button profileEdit,editSectionProfile,baseSettings,applyProfile;
 Button[] fullButtons;
 private final Context context;
 ProfilePageLayout(Context context){
 this.context=context;
 profilePage=new LinearLayout(context);profilePage.setOrientation(1);profilePage.setBackgroundColor(Color.BLACK);profilePage.setVisibility(View.GONE);
  profilePageTitle=sheetText("Regelprofil",16,true);profilePageTitle.setTextColor(Color.WHITE);profilePageTitle.setPadding(dp(8),dp(6),dp(8),dp(6));profilePage.addView(profilePageTitle);
  profileWork=new LinearLayout(context);profileWork.setOrientation(0);profilePage.addView(profileWork,new LinearLayout.LayoutParams(-1,0,1));profileWork.setPadding(dp(6),dp(4),dp(6),dp(4));
  fullCross=new PipeLayoutView(context);profileWork.addView(fullCross,new LinearLayout.LayoutParams(-1,0,3));profileWork.setOrientation(LinearLayout.VERTICAL);
  profileInfo=new LinearLayout(context);profileInfo.setOrientation(1);profileInfo.setPadding(dp(8),dp(8),dp(8),dp(8));android.graphics.drawable.GradientDrawable parameterFrame=editorShape(0xff041012,8);parameterFrame.setStroke(dp(1),0xff008b93);profileInfo.setBackground(parameterFrame);
  profileInfoScroll=new ScrollView(context);profileInfoScroll.addView(profileInfo);profileInfoScroll.setFillViewport(true);profileWork.addView(profileInfoScroll,new LinearLayout.LayoutParams(-1,0,2));
  profileDimensions=sheetText("",14,true);profileDimensions.setTextColor(0xff28d5c7);TextView parameterTitle=sheetText("PARAMETER",12,true);parameterTitle.setTextColor(0xff28d5c7);profileInfo.addView(parameterTitle);profileInfo.addView(profileDimensions);profileDimensions.setPadding(dp(8),dp(10),dp(8),dp(10));android.graphics.drawable.GradientDrawable dimensionFrame=editorShape(Color.BLACK,4);dimensionFrame.setStroke(dp(1),0xff008b93);profileDimensions.setBackground(dimensionFrame);
  profileMaterial=sheetText("",12,false);profileMaterial.setTextColor(0xffcbd6d5);profileInfo.addView(profileMaterial);profileMaterial.setPadding(dp(8),dp(10),dp(8),dp(10));
  ProfileResponsiveLayout.bind(profileWork,fullCross,profileInfoScroll);
 }
 void buildActions(java.util.function.BiConsumer<Button,Boolean> style, java.util.function.BiConsumer<Button,String> settingsIcon){
  profileInfo.addView(ProfileLegend.create(context));
  profileEdit=new Button(context);profileEdit.setText("Maße bearbeiten");style.accept(profileEdit,false);configureAction(profileEdit);
  profileDimensions.setOnClickListener(v->profileEdit.performClick());profileDimensions.setContentDescription("Grabenbreite und Grabentiefe bearbeiten");
  editSectionProfile=new Button(context);style.accept(editSectionProfile,false);settingsIcon.accept(editSectionProfile,"Abschnitt bearbeiten");
  baseSettings=new Button(context);style.accept(baseSettings,false);settingsIcon.accept(baseSettings,"Standard-Profil verwalten");
  applyProfile=new Button(context);style.accept(applyProfile,true);applyProfile.setText("Übernehmen");configureAction(applyProfile);
  LinearLayout fullTabs=new LinearLayout(context);fullTabs.setPadding(dp(4),0,dp(4),dp(4));profilePage.addView(fullTabs,1,new LinearLayout.LayoutParams(-1,dp(44)));
  String[] names={"Aufbau","Leitungen","Material","Berechnung"};fullButtons=new Button[names.length];
  for(int i=0;i<names.length;i++){Button b=new Button(context);fullButtons[i]=b;style.accept(b,i==0);b.setText(names[i]);b.setAllCaps(false);b.setTextSize(11);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(0,0,0,0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(dp(2),0,dp(2),0);fullTabs.addView(b,lp);}
  LinearLayout profileActions=new LinearLayout(context);profilePage.addView(profileActions,new LinearLayout.LayoutParams(-1,dp(40)));
  for(Button b:new Button[]{editSectionProfile,baseSettings}){android.graphics.drawable.Drawable icon=b.getCompoundDrawables()[1];b.setCompoundDrawables(icon,null,null,null);b.setMinHeight(0);b.setMinimumHeight(0);b.setTextSize(12);b.setPadding(dp(6),0,dp(6),0);}
  for(Button b:new Button[]{baseSettings,profileEdit,applyProfile})profileActions.addView(b,new LinearLayout.LayoutParams(0,-1,1));
 }
 private void configureAction(Button b){b.setAllCaps(false);b.setMinHeight(0);b.setMinimumHeight(0);b.setTextSize(12);}
 private int dp(int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
 private TextView sheetText(String text,int size,boolean bold){TextView v=new TextView(context);v.setText(text);v.setTextSize(size);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
 private GradientDrawable editorShape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
}
