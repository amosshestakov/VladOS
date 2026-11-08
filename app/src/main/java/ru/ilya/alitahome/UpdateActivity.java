package ru.ilya.alitahome;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public final class UpdateActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile HttpURLConnection connection;
    private TextView status,notes,download,check;
    private ProgressBar progress;
    private JSONObject release;
    private File downloaded;
    private boolean busy;
    private PackageInfo installed;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);Ui.full(this);
        try{installed=getPackageManager().getPackageInfo(getPackageName(),PackageManager.GET_SIGNATURES);}catch(Exception e){finish();return;}
        FrameLayout root=VladDesign.background(this,true);setContentView(root);
        LinearLayout layout=VladDesign.column(this);layout.setPadding(dp(60),dp(35),dp(60),dp(40));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(VladDesign.display(this,"Обновление VladOS",27,400),new LinearLayout.LayoutParams(0,-2,1));header.addView(VladDesign.button(this,"Назад","back",()->finish()),new LinearLayout.LayoutParams(dp(100),dp(36)));layout.addView(header);
        VladDesign.gap(this,layout,25);layout.addView(Ui.text(this,"Установлена версия "+installed.versionName,18,Ui.INK));VladDesign.gap(this,layout,12);
        status=Ui.text(this,"",16,Ui.INK);layout.addView(status);progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);layout.addView(progress,new LinearLayout.LayoutParams(-1,dp(24)));
        check=VladDesign.button(this,"Проверить обновления",null,()->check());LinearLayout.LayoutParams button=new LinearLayout.LayoutParams(-1,dp(45));button.topMargin=dp(14);layout.addView(check,button);
        download=VladDesign.button(this,"Скачать и установить",null,()->download());LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,dp(45));dl.topMargin=dp(12);layout.addView(download,dl);download.setVisibility(android.view.View.GONE);
        ScrollView scroll=new ScrollView(this);notes=Ui.text(this,"",14,Ui.MUTED);notes.setPadding(0,dp(16),0,dp(16));scroll.addView(notes);layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        layout.addView(Ui.text(this,"Канал: GitHub · "+UpdatePolicy.REPOSITORY+"\nУстановка сохраняет настройки и требует подтверждения Android.",12,Ui.MUTED));root.addView(layout,new FrameLayout.LayoutParams(-1,-1));check.requestFocus();check();
    }
    private int dp(int n){return Ui.dp(this,n);}
    private void ui(Runnable action){runOnUiThread(()->{if(!isDestroyed()&&!isFinishing())action.run();});}
    private void busy(boolean value){busy=value;check.setEnabled(!value);download.setEnabled(!value);progress.setIndeterminate(value);progress.setVisibility(value?android.view.View.VISIBLE:android.view.View.GONE);}
    private void failed(Exception e){ui(()->{busy(false);status.setText(e.getMessage()==null?"Не удалось проверить обновление. Повтори попытку.":e.getMessage());check.requestFocus();});}
    private HttpURLConnection open(String address)throws Exception{
        for(int i=0;i<6;i++){
            if(!UpdatePolicy.transport(address))throw new IOException("Неожиданный адрес сервера обновлений");
            HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();connection=c;c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setRequestProperty("User-Agent","VladOS-Updater");c.setRequestProperty("Accept","application/vnd.github+json");
            int code=c.getResponseCode();if(code==301||code==302||code==303||code==307||code==308){String next=c.getHeaderField("Location");if(next==null){c.disconnect();throw new IOException("Не указан адрес загрузки");}address=new URL(new URL(address),next).toString();c.disconnect();continue;}
            if(code!=200){c.disconnect();throw new IOException(code==404?"Публичный выпуск ещё не опубликован. Проверь позже.":code==403||code==429?"GitHub временно ограничил запросы. Проверь позже.":"Сервер обновлений недоступен ("+code+")");}return c;
        }throw new IOException("Слишком много перенаправлений сервера");
    }
    private String json(String url)throws Exception{
        HttpURLConnection c=open(url);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] data=new byte[8192];int n;while((n=in.read(data))!=-1){if(out.size()+n>256*1024)throw new IOException("Некорректные данные выпуска");out.write(data,0,n);}return out.toString("UTF-8");
        }finally{c.disconnect();connection=null;}
    }
    private JSONObject metadata()throws Exception{
        // Raw GitHub CDN remains reachable on TV networks that block release pages.
        try{return new JSONObject(json(UpdatePolicy.RAW_MANIFEST));}catch(Exception unavailable){}
        JSONObject latest=new JSONObject(json(UpdatePolicy.API));
        if(latest.optBoolean("draft")||latest.optBoolean("prerelease"))throw new IOException("Стабильный выпуск пока не опубликован");
        JSONArray assets=latest.getJSONArray("assets");String metadata=null;Set<String> urls=new HashSet<>();
        for(int i=0;i<assets.length();i++){JSONObject a=assets.getJSONObject(i);String url=a.optString("browser_download_url");if(UpdatePolicy.releaseAsset(url)){urls.add(url);if("update.json".equals(a.optString("name")))metadata=url;}}
        if(metadata==null)throw new IOException("В выпуске нет данных для обновления телевизора");
        JSONObject info=new JSONObject(json(metadata));if(!urls.contains(info.optString("apkUrl")))throw new IOException("APK отсутствует в опубликованном выпуске");
        if(!info.has("notes"))info.put("notes",latest.optString("body",""));return info;
    }
    private void check(){if(busy)return;release=null;downloaded=null;download.setVisibility(android.view.View.GONE);notes.setText("");status.setText("Проверяем новые версии…");busy(true);
        worker.execute(()->{try{
            final JSONObject info=metadata();
            if(!getPackageName().equals(info.optString("packageName"))||!UpdatePolicy.artifact(info.optString("apkUrl"))||!info.optString("sha256").matches("[a-fA-F0-9]{64}")||info.optLong("size",0)<=0||info.optLong("size")>UpdatePolicy.LIMIT||info.optInt("versionCode",0)<=0)throw new IOException("Данные обновления не прошли проверку");
            ui(()->{busy(false);release=info;notes.setText(info.optString("notes",""));if(info.optInt("versionCode")<=installed.versionCode){status.setText("У вас актуальная версия VladOS");check.requestFocus();}else{status.setText("Доступна версия "+info.optString("versionName")+" · "+String.format(Locale.ROOT,"%.1f МБ",info.optLong("size")/1048576d));download.setVisibility(android.view.View.VISIBLE);download.requestFocus();}});
        }catch(Exception e){failed(e);}});
    }
    private void download(){if(busy||release==null)return;if(downloaded!=null){install();return;}final JSONObject info=release;busy(true);status.setText("Скачиваем обновление…");
        worker.execute(()->{File dest=new File(getFilesDir(),"inbox/vlados_update.apk");File part=new File(getFilesDir(),"inbox/vlados_update.part");part.getParentFile().mkdirs();
            try{HttpURLConnection c=open(info.getString("apkUrl"));MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0,expected=info.getLong("size");int shown=-1;
                try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(part)){
                    byte[] data=new byte[32768];int n;while((n=in.read(data))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Загрузка отменена");total+=n;if(total>UpdatePolicy.LIMIT||total>expected)throw new IOException("Размер обновления отличается от опубликованного");out.write(data,0,n);digest.update(data,0,n);int percent=(int)(total*100/expected);if(percent!=shown){shown=percent;ui(()->{progress.setIndeterminate(false);progress.setProgress(percent);status.setText("Скачиваем обновление: "+percent+"%");});}}
                }finally{c.disconnect();connection=null;}
                if(total!=expected||!UpdatePolicy.hashMatches(info.getString("sha256"),digest.digest()))throw new IOException("Файл повреждён. Повтори загрузку обновления.");
                validate(part,info.getInt("versionCode"));if(dest.exists()&&!dest.delete())throw new IOException("Не удалось заменить старый установщик");if(!part.renameTo(dest))throw new IOException("Не удалось сохранить обновление");
                ui(()->{busy(false);downloaded=dest;download.setText("Установить обновление");status.setText("Обновление проверено. Подтверди установку Android.");install();});
            }catch(Exception e){part.delete();failed(e);}
        });
    }
    private void validate(File file,int expected)throws Exception{
        PackageInfo apk=getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNATURES);
        if(apk==null||!getPackageName().equals(apk.packageName)||apk.versionCode!=expected||apk.versionCode<=installed.versionCode)throw new IOException("APK не соответствует новой версии VladOS");
        if(Build.VERSION.SDK_INT>=24&&apk.applicationInfo.minSdkVersion>Build.VERSION.SDK_INT)throw new IOException("Обновлению нужна более новая версия Android");
        if(installed.signatures==null||apk.signatures==null||installed.signatures.length==0||!new HashSet<>(Arrays.asList(installed.signatures)).equals(new HashSet<>(Arrays.asList(apk.signatures))))throw new IOException("Подпись обновления отличается от установленного VladOS");
    }
    private void install(){
        if(downloaded==null)return;
        if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){
            new Modal.Builder(this).setTitle("Разрешить обновление VladOS").setMessage("Разреши установку приложений из VladOS в следующем системном окне. Затем вернись сюда.").setPositiveButton("Открыть разрешение",(d,w)->{try{startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())),51);}catch(Exception e){status.setText("Системный экран разрешения недоступен. Обновление сохранено.");}}).setNegativeButton("Позже",null).show();return;
        }
        try{Uri uri=Uri.parse("content://"+getPackageName()+".apks/"+downloaded.getName());Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT,true);startActivityForResult(intent,52);}catch(Exception e){status.setText("Системный установщик недоступен. Обновление сохранено.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==51){if(Build.VERSION.SDK_INT<26||getPackageManager().canRequestPackageInstalls())install();else status.setText("Разрешение не выдано. Можно вернуться к установке позже.");}if(request==52)status.setText(result==RESULT_OK?"Обновление установлено":"Установка не завершена. Нажми «Установить обновление», чтобы повторить.");}
    @Override protected void onDestroy(){worker.shutdownNow();HttpURLConnection c=connection;if(c!=null)c.disconnect();super.onDestroy();}
}
