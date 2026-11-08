package ru.ilya.alitahome;
import android.accessibilityservice.AccessibilityService;
import android.app.*;
import android.content.*;
import android.os.Build;
public class SleepReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        long due=c.getSharedPreferences("home",0).getLong("sleep_at",0);
        if(due==0 || System.currentTimeMillis()+1000<due)return;
        boolean done=false;try{if(Build.VERSION.SDK_INT>=28&&RemoteService.live!=null)done=RemoteService.live.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);}catch(Exception ignored){}
        c.getSharedPreferences("home",0).edit().putLong("sleep_at",0).putString("sleep_result",done?"Команда блокировки экрана отправлена":"Сон не выполнен: служба управления недоступна").apply();
    }
    private static PendingIntent intent(Context c){return PendingIntent.getBroadcast(c,501,new Intent(c,SleepReceiver.class),PendingIntent.FLAG_UPDATE_CURRENT);}
    public static void set(Context c,int minutes){long when=System.currentTimeMillis()+minutes*60000L;
        c.getSharedPreferences("home",0).edit().putLong("sleep_at",when).putString("sleep_result","").apply();
        ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,intent(c));
    }
    public static void cancel(Context c){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(intent(c));c.getSharedPreferences("home",0).edit().putLong("sleep_at",0).apply();}
}
