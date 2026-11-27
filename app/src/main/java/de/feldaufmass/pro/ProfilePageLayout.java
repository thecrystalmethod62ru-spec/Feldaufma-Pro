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
 final LinearLayout profilePage,profileWork,profileInfo,profileCatalog,profileCenter;
 final TextView profilePageTitle,profileDimensions,profileMaterial;
 final PipeLayoutView fullCross;
 final ScrollView profileInfoScroll;
 Button profileEdit,editSectionProfile,baseSettings,applyProfile;
 Button[] fullButtons;
 private Button standardCard,sectionCard;
 private final Context context;
 ProfilePageLayout(Context context){
 this.context=context;
 profilePage=new LinearLayout(context);profilePage.setOrientation(1);profilePage.setBackgroundColor(Color.BLACK);profilePage.setVisibility(View.GONE);
  profilePageTitle=sheetText("Regelprofil",16,true);profilePageTitle.setTextColor(Color.WHITE);profilePageTitle.setPadding(dp(8),dp(6),dp(56),dp(6));profilePage.addView(profilePageTitle);
  profileWork=new LinearLayout(context);profileWork.setOrientation(0);profilePage.addView(profileWork,new LinearLayout.LayoutParams(-1,0,1));profileWork.setPadding(dp(6),dp(4),dp(6),dp(4));
  profileCatalog=new LinearLayout(context);profileCatalog.setOrientation(LinearLayout.VERTICAL);profileCatalog.setPadding(dp(6),dp(6),dp(6),dp(6));profileCatalog.setBackground(panelFrame());profileCatalog.setVisibility(View.GONE);profileWork.addView(profileCatalog,new LinearLayout.LayoutParams(dp(140),-1));
  profileCenter=new LinearLayout(context);profileCenter.setOrientation(LinearLayout.VERTICAL);profileWork.addView(profileCenter,new LinearLayout.LayoutParams(-1,0,3));
  fullCross=new PipeLayoutView(context);profileCenter.addView(fullCross,new LinearLayout.LayoutParams(-1,0,1));profileWork.setOrientation(LinearLayout.VERTICAL);
  profileInfo=new LinearLayout(context);profileInfo.setOrientation(1);profileInfo.setPadding(dp(8),dp(8),dp(8),dp(8));android.graphics.drawable.GradientDrawable parameterFrame=editorShape(0xff041012,8);parameterFrame.setStroke(dp(1),0xff008b93);profileInfo.setBackground(parameterFrame);
  profileInfoScroll=new ScrollView(context);profileInfoScroll.addView(profileInfo);profileInfoScroll.setFillViewport(true);profileWork.addView(profileInfoScroll,new LinearLayout.LayoutParams(-1,0,2));
  profileDimensions=sheetText("",14,true);profileDimensions.setTextColor(0xff28d5c7);TextView parameterTitle=sheetText("Parameter",14,true);parameterTitle.setTextColor(0xff28d5c7);profileInfo.addView(parameterTitle);profileInfo.addView(profileDimensions);profileDimensions.setPadding(dp(8),dp(10),dp(8),dp(10));android.graphics.drawable.GradientDrawable dimensionFrame=editorShape(Color.BLACK,4);dimensionFrame.setStroke(dp(1),0xff008b93);profileDimensions.setBackground(dimensionFrame);
  profileMaterial=sheetText("",12,false);profileMaterial.setTextColor(0xffcbd6d5);profileInfo.addView(profileMaterial);profileMaterial.setPadding(dp(8),dp(10),dp(8),dp(10));
  final LinearLayout portraitPanels=new LinearLayout(context);
  portraitPanels.setOrientation(LinearLayout.HORIZONTAL);
  final ScrollView catalogScroll=new ScrollView(context);
  catalogScroll.setFillViewport(true);
  final boolean[] lastWide={false}, initialized={false};
  profileWork.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob)->{
   int width=r-l-profileWork.getPaddingLeft()-profileWork.getPaddingRight();
   int height=b-t-profileWork.getPaddingTop()-profileWork.getPaddingBottom();
   if(width<=0||height<=0)return;
   boolean wide=getResourcesLandscape();
   int panelHeight=Math.min(dp(200),Math.max(dp(105),Math.round(height*.34f)));
   if(initialized[0]&&lastWide[0]==wide){
    if(!wide&&portraitPanels.getLayoutParams().height!=panelHeight)portraitPanels.setLayoutParams(new LinearLayout.LayoutParams(-1,panelHeight));
    return;
   }
   initialized[0]=true;lastWide[0]=wide;
   detach(profileCenter);detach(profileInfoScroll);detach(profileCatalog);detach(catalogScroll);detach(portraitPanels);
   profileWork.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
   profileCatalog.setVisibility(View.VISIBLE);
   catalogScroll.addView(profileCatalog,new ScrollView.LayoutParams(-1,-2));
   if(wide){
    profileWork.addView(catalogScroll,new LinearLayout.LayoutParams(Math.round(width*.19f),-1));
    profileWork.addView(profileCenter,new LinearLayout.LayoutParams(0,-1,1));
    profileWork.addView(profileInfoScroll,new LinearLayout.LayoutParams(Math.round(width*.30f),-1));
   }else{
    profileWork.addView(profileCenter,new LinearLayout.LayoutParams(-1,0,1));
    profileWork.addView(portraitPanels,new LinearLayout.LayoutParams(-1,panelHeight));
    LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-1,1);left.setMargins(0,0,dp(3),0);
    portraitPanels.addView(profileInfoScroll,left);
    portraitPanels.addView(catalogScroll,new LinearLayout.LayoutParams(0,-1,1));
   }
  });
 }
 void buildActions(java.util.function.BiConsumer<Button,Boolean> style, java.util.function.BiConsumer<Button,String> settingsIcon){
  profileCenter.addView(ProfileLegend.createCompact(context),new LinearLayout.LayoutParams(-1,-2));
  profileEdit=new Button(context);profileEdit.setText("Maße bearbeiten");style.accept(profileEdit,false);configureAction(profileEdit);
  profileDimensions.setOnClickListener(v->profileEdit.performClick());profileDimensions.setContentDescription("Grabenbreite und Grabentiefe bearbeiten");
  editSectionProfile=new Button(context);style.accept(editSectionProfile,false);settingsIcon.accept(editSectionProfile,"Abschnitt bearbeiten");
  baseSettings=new Button(context);style.accept(baseSettings,false);settingsIcon.accept(baseSettings,"Standard-Profil verwalten");
  applyProfile=new Button(context);style.accept(applyProfile,true);applyProfile.setText("Übernehmen");configureAction(applyProfile);
  LinearLayout fullTabs=new LinearLayout(context);fullTabs.setPadding(dp(4),0,dp(4),dp(4));profileCenter.addView(fullTabs,0,new LinearLayout.LayoutParams(-1,dp(44)));
  String[] names={"Aufbau","Leitungen","Material","Berechnung"};fullButtons=new Button[names.length];
  for(int i=0;i<names.length;i++){Button b=new Button(context);fullButtons[i]=b;style.accept(b,i==0);b.setText(names[i]);b.setAllCaps(false);b.setTextSize(11);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(0,0,0,0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(dp(2),0,dp(2),0);fullTabs.addView(b,lp);}
  LinearLayout profileActions=new LinearLayout(context);profilePage.addView(profileActions,new LinearLayout.LayoutParams(-1,dp(40)));
  for(Button b:new Button[]{editSectionProfile,baseSettings}){android.graphics.drawable.Drawable icon=b.getCompoundDrawables()[1];b.setCompoundDrawables(icon,null,null,null);b.setMinHeight(0);b.setMinimumHeight(0);b.setTextSize(12);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(6),0,dp(6),0);}
  profileInfo.addView(editSectionProfile,new LinearLayout.LayoutParams(-1,dp(44)));
  TextView catalogTitle=sheetText("Profilkatalog",14,true);catalogTitle.setTextColor(0xff28d5c7);profileCatalog.addView(catalogTitle);
  Button standard=new Button(context);standardCard=standard;style.accept(standard,true);standard.setText("Standardprofil");configureAction(standard);standard.setOnClickListener(v->baseSettings.performClick());profileCatalog.addView(standard,new LinearLayout.LayoutParams(-1,dp(58)));
  Button section=new Button(context);sectionCard=section;style.accept(section,false);section.setText("Abschnitt");configureAction(section);section.setOnClickListener(v->editSectionProfile.performClick());profileCatalog.addView(section,new LinearLayout.LayoutParams(-1,dp(58)));
  for(Button b:new Button[]{baseSettings,profileEdit,applyProfile})profileActions.addView(b,new LinearLayout.LayoutParams(0,-1,1));
 }
 void updateCatalog(String standard,String section,boolean sectionSelected){
  if(standardCard==null||sectionCard==null)return;
  standardCard.setText("Standardprofil\n"+standard);
  sectionCard.setText(section==null?"Abschnitt auswählen":section);
  standardCard.setSelected(!sectionSelected);sectionCard.setSelected(sectionSelected);
  standardCard.setBackground(panelFrame());sectionCard.setBackground(panelFrame());
  standardCard.setTextColor(sectionSelected?Color.WHITE:0xff28d5c7);
  sectionCard.setTextColor(sectionSelected?0xff28d5c7:Color.WHITE);
 }
 private static void detach(View view){if(view.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)view.getParent()).removeView(view);}
 private boolean getResourcesLandscape(){return context.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;}
 private GradientDrawable panelFrame(){GradientDrawable d=editorShape(0xff041012,8);d.setStroke(dp(1),0xff008b93);return d;}
 private void configureAction(Button b){b.setAllCaps(false);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(4),0,dp(4),0);b.setTextSize(12);}
 private int dp(int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
 private TextView sheetText(String text,int size,boolean bold){TextView v=new TextView(context);v.setText(text);v.setTextSize(size);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
 private GradientDrawable editorShape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
}
