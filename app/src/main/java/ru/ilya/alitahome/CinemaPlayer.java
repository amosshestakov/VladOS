package ru.ilya.alitahome;

import android.app.Activity;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.io.*;
import java.net.*;

/** Television player with VladOS controls; no Android MediaController or system dialogs. */
final class CinemaPlayer extends FrameLayout {
    interface Events {void progress(int time,int duration);void closed();void ended();}
    private final Activity owner;
    private final Events events;
    private final VideoView video;
    private final LinearLayout panel;
    private final TextView status,position;
    private final TextView subtitle;
    private final ExecutorService downloads=Executors.newSingleThreadExecutor();
    private volatile List<Cue> cues=Collections.emptyList();
    private static final class Cue {long from,to;String text;Cue(long f,long t,String s){from=f;to=t;text=s;}}
    private final Handler handler=new Handler();
    private final JSONObject data;
    private final TextView pause;
    private boolean released,prepared;
    private int resume;
    private final Runnable hide=()->hideControls();
    private final Runnable ticker=new Runnable(){public void run(){if(released)return;if(prepared){int time=video.getCurrentPosition(),duration=video.getDuration();position.setText(clock(time)+"  /  "+clock(duration));events.progress(time,duration);String line="";for(Cue cue:cues)if(time>=cue.from&&time<=cue.to){line=cue.text;break;}subtitle.setText(line);subtitle.setVisibility(line.isEmpty()?GONE:VISIBLE);}handler.postDelayed(this,500);}};
    CinemaPlayer(Activity activity,JSONObject payload,Events callback){
        super(activity);owner=activity;events=callback;data=payload;setBackgroundColor(Color.BLACK);setFocusable(true);setFocusableInTouchMode(true);
        video=new VideoView(activity);FrameLayout.LayoutParams vp=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER);addView(video,vp);
        subtitle=Ui.text(activity,"",20,Color.WHITE);subtitle.setGravity(Gravity.CENTER);subtitle.setShadowLayer(4,1,2,Color.BLACK);FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);sp.setMargins(dp(60),0,dp(60),dp(50));addView(subtitle,sp);
        status=Ui.text(activity,"Загружаем видео…",19,Ui.INK);status.setGravity(Gravity.CENTER);addView(status,new FrameLayout.LayoutParams(-1,-1));
        panel=Ui.col(activity);panel.setPadding(dp(30),dp(22),dp(30),dp(22));panel.setBackgroundColor(0xee080a0d);
        TextView title=Ui.text(activity,payload.optString("title","Кино"),20,Ui.INK);title.setMaxLines(1);panel.addView(title);
        position=Ui.text(activity,"",13,Ui.MUTED);position.setPadding(0,dp(10),0,dp(14));panel.addView(position);
        LinearLayout actions=new LinearLayout(activity);
        actions.addView(button("−10 сек",()->seek(-10000)),params());
        pause=button("Пауза",()->toggle());actions.addView(pause,params());
        actions.addView(button("+10 сек",()->seek(10000)),params());
        actions.addView(button("Качество",()->quality()),params());
        actions.addView(button("Субтитры",()->subtitles()),params());
        actions.addView(button("Закрыть",()->close()),params());panel.addView(actions);
        addView(panel,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        video.setOnPreparedListener(mp->{prepared=true;status.setVisibility(GONE);if(resume>0)video.seekTo(resume);video.start();pause.setText("Пауза");scheduleHide();});
        video.setOnErrorListener((mp,what,extra)->{status.setText("Не удалось запустить видео\nМожно выбрать другое качество или вернуться к фильму");status.setVisibility(VISIBLE);panel.setVisibility(VISIBLE);return true;});
        video.setOnCompletionListener(mp->{events.progress(video.getDuration(),video.getDuration());events.ended();release();});
        resume=payload.optInt("position",0)*1000;
        open(payload.optString("url"));requestFocus();handler.post(ticker);
    }
    private int dp(int value){return Ui.dp(owner,value);}
    private LinearLayout.LayoutParams params(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.rightMargin=dp(10);return p;}
    private TextView button(String name,Runnable action){TextView t=Ui.text(owner,name,14,Ui.INK);t.setGravity(Gravity.CENTER);t.setPadding(dp(12),0,dp(12),0);Ui.focus(owner,t,Ui.CARD);t.setOnClickListener(v->{action.run();scheduleHide();});return t;}
    private String clock(int ms){int sec=Math.max(0,ms/1000);return String.format(Locale.ROOT,"%d:%02d:%02d",sec/3600,sec/60%60,sec%60);}
    private void open(String url){
        Uri uri=Uri.parse(url);if(!"https".equals(uri.getScheme())&&!LocalTorrServer.stream(url)){status.setText("Источник должен использовать HTTPS или локальный TorrServer");return;}
        prepared=false;status.setText("Загружаем видео…");status.setVisibility(VISIBLE);video.setVideoURI(uri);video.start();
    }
    private void quality(){
        JSONObject qualities=data.optJSONObject("quality");if(qualities==null||qualities.length()==0){status.setText("Источник предлагает одно качество");status.setVisibility(VISIBLE);handler.postDelayed(()->{if(prepared)status.setVisibility(GONE);},2000);return;}
        handler.removeCallbacks(hide);ArrayList<String> names=new ArrayList<>();Iterator<String> keys=qualities.keys();while(keys.hasNext())names.add(keys.next());
        new Modal.Builder(owner).setTitle("Качество").setItems(names.toArray(new String[0]),(d,which)->{resume=video.getCurrentPosition();open(qualities.optString(names.get(which)));}).setNegativeButton("Назад",null).show();
    }
    private long stamp(String value){String[] parts=value.replace(',','.').split(":");double seconds=0;for(String part:parts)seconds=seconds*60+Double.parseDouble(part);return (long)(seconds*1000);}
    private void subtitles(){
        JSONArray list=data.optJSONArray("subtitles");ArrayList<String> names=new ArrayList<>();names.add("Выключены");if(list!=null)for(int i=0;i<list.length();i++)names.add(list.optJSONObject(i).optString("label","Дорожка "+(i+1)));
        handler.removeCallbacks(hide);new Modal.Builder(owner).setTitle("Субтитры").setItems(names.toArray(new String[0]),(d,which)->{
            cues=Collections.emptyList();subtitle.setText("");if(which==0||list==null)return;String url=list.optJSONObject(which-1).optString("url");
            downloads.execute(()->{HttpURLConnection connection=null;try{
                URL address=new URL(url);if(!address.getProtocol().equals("https"))return;
                connection=(HttpURLConnection)address.openConnection();connection.setConnectTimeout(10000);connection.setReadTimeout(10000);
                String text;try(InputStream in=connection.getInputStream()){text=Catalog.read(in,1024*1024).replace("\r","");}
                ArrayList<Cue> parsed=new ArrayList<>();Pattern timing=Pattern.compile("((?:\\d+:)?\\d{2}:\\d{2}[.,]\\d+)\\s*-->\\s*((?:\\d+:)?\\d{2}:\\d{2}[.,]\\d+)");
                for(String block:text.split("\\n\\s*\\n")){Matcher match=timing.matcher(block);if(match.find()){int end=block.indexOf('\n',match.end());if(end>0){String line=android.text.Html.fromHtml(block.substring(end+1).replace("\n","<br>")).toString();parsed.add(new Cue(stamp(match.group(1)),stamp(match.group(2)),line));}}if(parsed.size()>5000)break;}
                cues=parsed;
            }catch(Exception e){owner.runOnUiThread(()->{if(!released)new Modal.Builder(owner).setTitle("Субтитры недоступны").setMessage("Не удалось загрузить дорожку. Можно продолжить без неё.").setPositiveButton("Продолжить",null).show();});}finally{if(connection!=null)connection.disconnect();}});
        }).setNegativeButton("Назад",null).show();
    }
    void pauseForBackground(){if(prepared){events.progress(video.getCurrentPosition(),video.getDuration());video.pause();pause.setText("Продолжить");}panel.setVisibility(VISIBLE);}
    private void toggle(){if(!prepared)return;if(video.isPlaying()){video.pause();pause.setText("Продолжить");}else{video.start();pause.setText("Пауза");}panel.setVisibility(VISIBLE);scheduleHide();}
    private void seek(int delta){if(!prepared)return;video.seekTo(Math.max(0,Math.min(video.getDuration(),video.getCurrentPosition()+delta)));panel.setVisibility(VISIBLE);scheduleHide();}
    private void scheduleHide(){handler.removeCallbacks(hide);if(prepared&&video.isPlaying())handler.postDelayed(hide,4500);}
    private void hideControls(){panel.setVisibility(GONE);requestFocus();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(event.getAction()!=KeyEvent.ACTION_DOWN)return super.dispatchKeyEvent(event);
        int key=event.getKeyCode();
        if(key==KeyEvent.KEYCODE_BACK){close();return true;}
        if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||key==KeyEvent.KEYCODE_MEDIA_PAUSE||key==KeyEvent.KEYCODE_MEDIA_PLAY){toggle();return true;}
        if(panel.getVisibility()!=VISIBLE||isFocused()){
            if(key==KeyEvent.KEYCODE_DPAD_LEFT){seek(-10000);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_RIGHT){seek(10000);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){toggle();return true;}
        }
        if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_MENU){panel.setVisibility(VISIBLE);pause.requestFocus();scheduleHide();return true;}
        return super.dispatchKeyEvent(event);
    }
    void close(){if(released)return;if(prepared)events.progress(video.getCurrentPosition(),video.getDuration());events.closed();release();}
    void release(){if(released)return;released=true;downloads.shutdownNow();handler.removeCallbacksAndMessages(null);video.stopPlayback();if(getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)getParent()).removeView(this);}
}
