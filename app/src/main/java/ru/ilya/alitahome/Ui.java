package ru.ilya.alitahome;
import android.app.*;
import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
public final class Ui {
    public static final int INK=0xfff2f1f8,MUTED=0xffa19db8,GREEN=0xff8b4dff,BASE=0xff0b0a13,CARD=0xb82c2648;
    public static int dp(Context c,float n){return Math.round(c.getResources().getDisplayMetrics().density*n);}
    public static LinearLayout col(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(1);return v;}
    public static TextView text(Context c,String s,int size,int color){return VladDesign.text(c,s,size,color);}
    public static GradientDrawable bg(Context c,int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,14));return d;}
    public static void hint(Context c,String label){if(c instanceof Activity){View decor=((Activity)c).getWindow().getDecorView();View v=decor.findViewWithTag("drawer_hint");if(v==null)v=decor.findViewWithTag("focus_hint");if(v instanceof TextView)((TextView)v).setText(label);}}
    public static void focus(Context c,View v,int color){v.setBackground(VladDesign.shape(c,color,11));VladDesign.focus(c,v,2);}
    public static Button button(Context c,String label,Runnable action){
        Button b=new Button(c);b.setText(label);b.setTextSize(12);b.setTextColor(INK);b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));b.setStateListAnimator(null);b.setBackgroundTintList(null);b.setPadding(dp(c,12),dp(c,8),dp(c,12),dp(c,8));b.setIncludeFontPadding(false);b.setAllCaps(false);VladDesign.fonts(c);b.setTypeface(VladDesign.body);b.setGravity(Gravity.CENTER);
        android.graphics.drawable.Drawable icon=c.getDrawable(c.getResources().getIdentifier("action_"+Glyph.infer(label),"drawable",c.getPackageName()));icon.setBounds(0,0,dp(c,24),dp(c,24));b.setCompoundDrawablePadding(dp(c,8));b.setCompoundDrawables(icon,null,null,null);
        b.setContentDescription(label);if(android.os.Build.VERSION.SDK_INT>=26)b.setTooltipText(label);
        b.setBackground(VladDesign.shape(c,CARD,24));VladDesign.focus(c,b,0);b.setOnClickListener(v->action.run());return b;
    }
    public static void message(Context c,String title,String message){new Modal.Builder(c).setTitle(title).setMessage(message).setPositiveButton("Понятно",null).show();}
    public static void full(Activity a){a.getWindow().getDecorView().setSystemUiVisibility(5894);float brightness=a.getSharedPreferences("home",0).getFloat("brightness",-1f);WindowManager.LayoutParams p=a.getWindow().getAttributes();p.screenBrightness=brightness;a.getWindow().setAttributes(p);}
}
