package ru.ilya.alitahome;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import android.view.View;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

public class InstallerActivity extends Activity {
    private LinearLayout page,list;
    private TextView status;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private UploadServer server;
    private static final long LIMIT=200L*1024*1024;
    private int dp(float n){return Ui.dp(this,n);}
    private File folder(){File f=new File(getFilesDir(),"inbox");f.mkdirs();return f;}
    @Override public void onCreate(Bundle b){super.onCreate(b);Ui.full(this);
        FrameLayout frame=VladDesign.background(this,true);setContentView(frame);LinearLayout layout=VladDesign.column(this);layout.setPadding(dp(60),dp(35),dp(60),dp(32));LinearLayout head=new LinearLayout(this);head.setGravity(android.view.Gravity.CENTER_VERTICAL);head.addView(VladDesign.display(this,"Установка APK",29,400),new LinearLayout.LayoutParams(0,-2,1));head.addView(VladDesign.button(this,"Назад","back",()->finish()),new LinearLayout.LayoutParams(dp(100),dp(36)));layout.addView(head);VladDesign.gap(this,layout,18);
        LinearLayout split=new LinearLayout(this);split.setGravity(android.view.Gravity.CENTER_VERTICAL);ImageView illustration=new ImageView(this);illustration.setImageDrawable(VladDesign.resource(this,"illus_apk_install"));illustration.setScaleType(ImageView.ScaleType.FIT_CENTER);split.addView(illustration,new LinearLayout.LayoutParams(dp(270),dp(310)));
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.setClipToPadding(false);page=VladDesign.column(this);page.setPadding(dp(30),dp(10),dp(10),dp(20));scroll.addView(page);split.addView(scroll,new LinearLayout.LayoutParams(0,-1,1));layout.addView(split,new LinearLayout.LayoutParams(-1,0,1));frame.addView(layout,new FrameLayout.LayoutParams(-1,-1));VladDesign.hints(this,frame,true);
        page.addView(Ui.text(this,getIntent().getBooleanExtra("inbox",false)?"Загрузки":"Выбери способ установки",22,Ui.INK));TextView hint=Ui.text(this,"С телефона, флешки или из магазина",13,Ui.MUTED);hint.setPadding(0,dp(10),0,dp(16));hint.setTag("focus_hint");page.addView(hint);
        LinearLayout row=new LinearLayout(this);add(row,"С телефона",()->receive());add(row,"С флешки",()->chooseFile());add(row,"RuStore",()->openStore());page.addView(row);
        status=Ui.text(this,"",13,Ui.GREEN);status.setPadding(0,dp(14),0,dp(14));page.addView(status);        Button clear=Ui.button(this,"Удалить полученные APK",()->new Modal.Builder(this).setTitle("Удалить файлы установщиков?")
            .setMessage("Установленные приложения останутся. Активную передачу закроем.").setPositiveButton("Удалить",(d,w)->{
                stopServer();worker.execute(()->{File[] files=folder().listFiles();if(files!=null)for(File f:files)f.delete();runOnUiThread(()->refresh());});
            }).setNegativeButton("Отмена",null).show());page.addView(clear);
        list=Ui.col(this);page.addView(list);page.addView(Ui.button(this,"← Вернуться во VladOS",()->finish()));refresh();
        row.getChildAt(0).requestFocus();
    }
    private void add(LinearLayout row,String s,Runnable action){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(56),1);lp.setMargins(0,dp(10),dp(9),dp(10));row.addView(Ui.button(this,s,action),lp);}
    private void openStore(){Intent i=getPackageManager().getLeanbackLaunchIntentForPackage("ru.vk.store.tv");if(i==null)i=getPackageManager().getLaunchIntentForPackage("ru.vk.store.tv");if(i!=null)startActivity(i);else download("https://static.rustore.ru/release/RuStoreTV.apk","ru.vk.store.tv");}
    private void chooseFile(){
        try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),41);}
        catch(Exception e){Ui.message(this,"Нет системного выбора файла","В этой прошивке не найден выбор документов. Используй «С телефона по Wi-Fi» или открой APK штатным файловым менеджером.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==42){status.setText(result==RESULT_OK?"Приложение установлено":"Установка не завершена");refresh();}
        if(request==41 && result==RESULT_OK && data!=null && data.getData()!=null){Uri uri=data.getData();status.setText("Копируем APK…");
            worker.execute(()->{File dest=new File(folder(),"import_"+System.nanoTime()+".apk");try(InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new IOException("Файл не открылся");copy(in,dest);runOnUiThread(()->ready(dest,null));
            }catch(Exception e){dest.delete();error("Не удалось прочитать APK",e);}});
        }
    }
    private static void copy(InputStream in,File dest)throws IOException{
        try(OutputStream out=new FileOutputStream(dest)){byte[] buf=new byte[32768];long size=0;int n;
            while((n=in.read(buf))!=-1){size+=n;if(size>LIMIT)throw new IOException("Файл больше 200 МБ");out.write(buf,0,n);}}
    }
    private void download(String address,String expected){
        status.setText("Скачиваем официальный APK…");worker.execute(()->{
            File dest=new File(folder(),"download_"+System.nanoTime()+".apk");
            try{HttpURLConnection conn=open(address);try(InputStream in=conn.getInputStream()){copy(in,dest);}finally{conn.disconnect();}
                runOnUiThread(()->ready(dest,expected));
            }catch(Exception e){dest.delete();error("Загрузка не удалась. Проверь сеть или передай APK с телефона.",e);}
        });
    }
    static HttpURLConnection open(String address)throws IOException{
        URL url=new URL(address);
        for(int n=0;n<8;n++){
            if(!"https".equals(url.getProtocol()))throw new IOException("Разрешён только HTTPS");
            HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","VladOS/0.2 AndroidTV");int status=c.getResponseCode();
            if(status>=300&&status<400){String next=c.getHeaderField("Location");c.disconnect();if(next==null)throw new IOException("Нет адреса загрузки");url=new URL(url,next);continue;}
            if(status!=200){c.disconnect();throw new IOException("HTTP "+status);}return c;
        }throw new IOException("Слишком много перенаправлений");
    }
    private String validate(File file,String expected)throws Exception{
        PackageInfo p=getPackageManager().getPackageArchiveInfo(file.getPath(),0);
        if(p==null)throw new IOException("Не удалось прочитать APK. Нужен один .apk, а не XAPK/APKM или набор split APK.");
        if(expected!=null && !expected.equals(p.packageName))throw new IOException("Неожиданный пакет: "+p.packageName+". Установка отменена.");
        if(Build.VERSION.SDK_INT>=24&&p.applicationInfo.minSdkVersion>Build.VERSION.SDK_INT)throw new IOException("Приложению нужен Android "+p.applicationInfo.minSdkVersion+" (API), а у ТВ API "+Build.VERSION.SDK_INT);
        Set<String> nativeAbis=new HashSet<>();try(ZipFile zip=new ZipFile(file)){
            Enumeration<? extends ZipEntry> items=zip.entries();while(items.hasMoreElements()){
                String[] parts=items.nextElement().getName().split("/");if(parts.length>=3&&"lib".equals(parts[0]))nativeAbis.add(parts[1]);
            }}
        if(!nativeAbis.isEmpty()&&Collections.disjoint(nativeAbis,Arrays.asList(Build.SUPPORTED_ABIS)))throw new IOException("APK для другой архитектуры: "+nativeAbis+". Нужен ARMv7 / universal.");
        return p.packageName+"\nВерсия "+p.versionName;
    }
    private void ready(File file,String expected){
        if(isFinishing()||isDestroyed())return;
        try{String info=validate(file,expected);status.setText("APK готов к установке");refresh();
            new Modal.Builder(this).setTitle("Установить приложение?").setMessage(info+"\n\n"+Math.max(1,file.length()/1048576)+" МБ")
                .setPositiveButton("Установить",(d,w)->install(file)).setNegativeButton("Позже",null).show();
        }catch(Exception e){file.delete();refresh();Ui.message(this,"APK не подходит",e.getMessage());}
    }
    private void install(File file){
        if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){
            Ui.message(this,"Нужно разрешение на установку","Открой «Доступ к установке». Если настройки Xiaomi вылетают, один раз выполни на компьютере:\n\nadb shell appops set ru.ilya.alitahome REQUEST_INSTALL_PACKAGES allow\n\nЗатем вернись и выбери полученный APK снова.");return;
        }
        try{Uri uri=Uri.parse("content://ru.ilya.alitahome.apks/"+file.getName());
            Intent i=new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.putExtra(Intent.EXTRA_RETURN_RESULT,true);startActivityForResult(i,42);
        }catch(Exception e){Ui.message(this,"Установщик недоступен",e.getClass().getSimpleName()+"\nМожно установить этот APK через ADB.");}
    }
    private void permission(){
        if(Build.VERSION.SDK_INT<26||getPackageManager().canRequestPackageInstalls()){Ui.message(this,"Установка разрешена","Можно выбирать APK. Подтверждение каждой установки останется системным.");return;}
        try{startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}
        catch(Exception e){Ui.message(this,"Настройки недоступны","Выполни один раз:\nadb shell appops set ru.ilya.alitahome REQUEST_INSTALL_PACKAGES allow");}
    }
    private void refresh(){if(list==null)return;list.removeAllViews();File[] files=folder().listFiles((d,n)->n.endsWith(".apk"));
        if(files==null||files.length==0){list.addView(Ui.text(this,"",14,Ui.MUTED));return;}
        Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        for(File f:files){PackageInfo p=getPackageManager().getPackageArchiveInfo(f.getPath(),0);String label=p==null?f.getName():p.packageName+" · "+p.versionName;
            TextView item=VladDesign.button(this,label,"apk",()->ready(f,null));LinearLayout.LayoutParams itemParams=new LinearLayout.LayoutParams(-1,dp(52));itemParams.topMargin=dp(8);list.addView(item,itemParams);}
    }
    private void receive(){
        stopServer();String ip=lanIp();if(ip==null){Ui.message(this,"Нет адреса домашней сети","Подключи телевизор к Wi-Fi или Ethernet.");return;}
        try{server=new UploadServer(ip,folder(),f->runOnUiThread(()->ready(f,null)));
            Ui.message(this,"Передача APK с iPhone / Android","Открой на телефоне в браузере:\n\nhttp://"+ip+":"+server.port()+"\n\nКод: "+server.code+"\n\nТелефон и ТВ должны быть в одной Wi-Fi сети. Выбери APK в браузере телефона.");
        }catch(Exception e){Ui.message(this,"Не удалось запустить приём",e.getMessage());}
    }
    static String lanIp(){try{Enumeration<NetworkInterface> nets=NetworkInterface.getNetworkInterfaces();while(nets.hasMoreElements()){
        NetworkInterface n=nets.nextElement();if(!n.isUp()||n.isLoopback()||n.getName().startsWith("tun"))continue;
        Enumeration<InetAddress> ips=n.getInetAddresses();while(ips.hasMoreElements()){InetAddress a=ips.nextElement();if(a instanceof Inet4Address && a.isSiteLocalAddress())return a.getHostAddress();}
    }}catch(Exception ignored){}return null;}
    private void error(String title,Exception e){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){status.setText(title);Ui.message(this,title,e.getMessage());}});}
    private void stopServer(){if(server!=null){server.close();server=null;}}
    @Override protected void onStop(){stopServer();super.onStop();}
    @Override protected void onDestroy(){stopServer();worker.shutdownNow();super.onDestroy();}
}
