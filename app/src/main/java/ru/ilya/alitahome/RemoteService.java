package ru.ilya.alitahome;
import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
public class RemoteService extends AccessibilityService {
    public static volatile RemoteService live; public static volatile boolean xiaomiSession; private long lastHome;
    @Override protected void onServiceConnected(){live=this;if(!xiaomiSession&&getSharedPreferences("home",0).getBoolean("home_return",true))startActivity(new Intent(this,MainActivity.class).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
    @Override public void onDestroy(){if(live==this)live=null;super.onDestroy();}
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(xiaomiSession||!getSharedPreferences("home",0).getBoolean("home_return",true)||event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return;
        if(!"com.xiaomi.eetv.home".contentEquals(event.getPackageName()==null?"":event.getPackageName()))return;
        String name=String.valueOf(event.getClassName());if(!name.equals("com.xiaomi.eetv.home.Launcher"))return;
        long now=android.os.SystemClock.elapsedRealtime();if(now-lastHome<1500)return;lastHome=now;
        startActivity(new Intent(this,MainActivity.class).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
    @Override public void onInterrupt() {}
    @Override protected boolean onKeyEvent(KeyEvent event) {
        if(!getSharedPreferences("home",0).getBoolean("global_menu",true))return false;
        if(event.getKeyCode()!=KeyEvent.KEYCODE_MENU) return false;
        if(event.getAction()==KeyEvent.ACTION_UP) startActivity(new Intent(this,MainActivity.class)
            .putExtra("drawer",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));
        return true;
    }
}
