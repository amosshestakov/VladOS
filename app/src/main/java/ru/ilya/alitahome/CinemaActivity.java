package ru.ilya.alitahome;

import android.app.*;
import android.os.*;
import android.webkit.*;
import android.webkit.CookieManager;
import android.view.*;
import android.widget.*;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Locally bundled cinema. Its renderer and network workers never run in the Home process. */
public final class CinemaActivity extends Activity {
    private static final String ORIGIN="https://cinema.vlados.local/";
    private WebView web;
    private CinemaPlayer player;
    private PhoneInputServer phoneInput;
    private FrameLayout root;
    private LinearLayout cover;
    private View fullscreen;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private final ExecutorService network=Executors.newFixedThreadPool(3);
    private volatile boolean destroyed;
    private final Set<String> providers=new HashSet<>(Arrays.asList("rezka.fi","rezka.ag","hdrezka.ag","hdrezka.me","hdrezka-home.tv"));
    private final Set<String> onlineHosts=new HashSet<>(Arrays.asList("filmixapp.cyou","filmix.gg","kodik-api.com","kodik.info","kodik.cc","kodikdb.com","plapi.cdnvideohub.com","api.anilibria.app","api.anilibria.tv","api.apbugall.org","api.srvkp.com","kinopoiskapiunofficial.tech"));

    @Override public void onCreate(Bundle state){
        super.onCreate(state);Ui.full(this);
        root=new FrameLayout(this);root.setBackgroundColor(Ui.BASE);setContentView(root);
        try {
            web=new WebView(this);web.setBackgroundColor(Ui.BASE);
            WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
            s.setCacheMode(WebSettings.LOAD_NO_CACHE);
            s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMediaPlaybackRequiresUserGesture(false);
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSupportMultipleWindows(false);
            s.setJavaScriptCanOpenWindowsAutomatically(false);s.setBuiltInZoomControls(false);
            web.addJavascriptInterface(new Bridge(),"VladCinema");
            web.setWebViewClient(new WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){return !local(req.getUrl());}
                @Override public boolean shouldOverrideUrlLoading(WebView view,String url){return !local(Uri.parse(url));}
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest req){
                    if(!local(req.getUrl())){String path=req.getUrl().getPath();if(path!=null&&(path.endsWith(".js")||path.startsWith("/plugin/")))return response("text/plain",404,new byte[0]);return null;}
                    String name=req.getUrl().getPath();
                    if(name==null||name.equals("/"))name="/index.html";
                    if(name.contains("..")||name.contains("\\"))return response("text/plain",404,new byte[0]);
                    try {
                        if(name.equals("/fonts/vlados-onest.ttf"))return new WebResourceResponse("font/ttf",null,getAssets().open("fonts/onest.ttf"));String mime=URLConnection.guessContentTypeFromName(name);
                        if(name.endsWith(".js"))mime="application/javascript";
                        if(name.endsWith(".css"))mime="text/css";
                        if(name.endsWith(".woff2"))mime="font/woff2";
                        if(mime==null)mime="application/octet-stream";
                        return new WebResourceResponse(mime,"UTF-8",getAssets().open("cinema"+name));
                    } catch(IOException e){return response("text/plain",404,new byte[0]);}
                }
                @Override public void onReceivedError(WebView view,WebResourceRequest req,WebResourceError err){if(req.isForMainFrame())showCover("Не удалось открыть кино","Повтори попытку. Главный экран доступен кнопкой «Назад».",true);}
                @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){
                    root.removeView(view);view.destroy();web=null;
                    showCover("Кино остановлено","Закрой раздел и открой его снова.",false);return true;
                }
            });
            web.setWebChromeClient(new WebChromeClient(){
                @Override public boolean onConsoleMessage(ConsoleMessage m){if(m.messageLevel()==ConsoleMessage.MessageLevel.ERROR||m.message().startsWith("Request")||m.message().startsWith("REZKA"))android.util.Log.i("VladCinema","Script line "+m.lineNumber()+": "+m.message().replaceAll("https?://[^ ]+","[URL]"));return true;}
                @Override public void onShowCustomView(View v,CustomViewCallback cb){
                    if(fullscreen!=null){cb.onCustomViewHidden();return;}
                    fullscreen=v;fullscreenCallback=cb;root.addView(v,new FrameLayout.LayoutParams(-1,-1));Ui.full(CinemaActivity.this);
                }
                @Override public void onHideCustomView(){hideFullscreen();}
                @Override public boolean onJsAlert(WebView v,String u,String m,JsResult result){result.cancel();return true;}
                @Override public boolean onJsConfirm(WebView v,String u,String m,JsResult result){result.cancel();return true;}
            });
            root.addView(web,new FrameLayout.LayoutParams(-1,-1));
            showCover("Кино","Подготавливаем каталог…",false);
            web.loadUrl(ORIGIN);
            new Handler().postDelayed(()->{if(cover!=null&&web!=null&&!destroyed)showCover("Каталог загружается","Можно подождать или открыть заново.",true);},25000);
        } catch(Throwable e){showCover("Нужен Android System WebView","Обнови компонент WebView на телевизоре и открой кино снова.",false);}
    }
    private boolean local(Uri u){return "https".equals(u.getScheme())&&"cinema.vlados.local".equals(u.getHost());}
    private static WebResourceResponse response(String mime,int status,byte[] bytes){return new WebResourceResponse(mime,"UTF-8",status,status==200?"OK":"Not Found",Collections.emptyMap(),new ByteArrayInputStream(bytes));}
    private void showCover(String title,String message,boolean retry){
        if(destroyed)return;
        if(cover!=null)root.removeView(cover);
        cover=Ui.col(this);cover.setGravity(Gravity.CENTER);cover.setPadding(70,40,70,40);cover.setBackgroundColor(Ui.BASE);
        TextView brand=Ui.text(this,"VladOS  /  Кино",14,Ui.GREEN);brand.setPadding(0,0,0,Ui.dp(this,28));cover.addView(brand);
        cover.addView(Ui.text(this,title,30,Ui.INK));TextView text=Ui.text(this,message,14,Ui.MUTED);text.setPadding(0,Ui.dp(this,14),0,Ui.dp(this,30));cover.addView(text);
        if(retry){Button b=Ui.button(this,"Повторить",()->{if(web!=null){root.removeView(cover);cover=null;web.reload();}});cover.addView(b);}
        cover.addView(Ui.button(this,"На главную",()->finish()));root.addView(cover,new FrameLayout.LayoutParams(-1,-1));
    }
    private void js(String script){runOnUiThread(()->{if(!destroyed&&web!=null)web.evaluateJavascript(script,null);});}
    private void hideFullscreen(){if(fullscreen!=null){root.removeView(fullscreen);fullscreen=null;if(fullscreenCallback!=null)fullscreenCallback.onCustomViewHidden();fullscreenCallback=null;}Ui.full(this);}
    @Override public void onBackPressed(){
        if(player!=null){player.close();return;}
        if(fullscreen!=null){hideFullscreen();return;}
        if(cover!=null||web==null){finish();return;}
        js("window.VladOSCinema ? VladOSCinema.back() : VladCinema.exit()");
    }
    @Override protected void onPause(){stopPhoneInput();if(player!=null)player.pauseForBackground();if(web!=null){js("window.VladOSCinema && VladOSCinema.pause()");web.onPause();}super.onPause();}
    @Override protected void onResume(){super.onResume();Ui.full(this);if(web!=null){web.onResume();web.requestFocus();}}
    @Override protected void onDestroy(){destroyed=true;stopPhoneInput();if(player!=null)player.release();network.shutdownNow();if(web!=null){web.removeJavascriptInterface("VladCinema");web.stopLoading();root.removeView(web);web.destroy();web=null;}super.onDestroy();}
    private void stopPhoneInput(){if(phoneInput!=null){phoneInput.close();phoneInput=null;}}

    public final class Bridge {
        @JavascriptInterface public void stopInput(){runOnUiThread(()->stopPhoneInput());}
        @JavascriptInterface public void phoneInput(){runOnUiThread(()->{
            stopPhoneInput();String ip=InstallerActivity.lanIp();if(ip==null){js("Lampa.Noty.show('Подключи ТВ к домашней сети')");return;}
            try{
                byte[] script;try(InputStream in=getAssets().open("cinema/crypto-js.js")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);script=out.toByteArray();}
                phoneInput=new PhoneInputServer(ip,script,value->{try{JSONObject fields=new JSONObject(value);String login=fields.optString("login"),password=fields.optString("password");if(login.length()>256||password.length()>512)return;js("VladOSCinema.fillCredentials("+JSONObject.quote(login)+","+JSONObject.quote(password)+")");}catch(JSONException ignored){}});
                js("VladOSCinema.showPhoneInput("+JSONObject.quote(phoneInput.url())+")");
            }catch(IOException e){js("Lampa.Noty.show('Не удалось открыть ввод с телефона')");}
        });}
        @JavascriptInterface public void play(String payload){
            if(payload.length()>100000)return;
            try{JSONObject data=new JSONObject(payload);if(!"https".equals(Uri.parse(data.optString("url")).getScheme()))return;
                runOnUiThread(()->{if(destroyed)return;if(player!=null)player.release();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    player=new CinemaPlayer(CinemaActivity.this,data,new CinemaPlayer.Events(){
                        public void progress(int time,int duration){js("window.VladOSCinema && VladOSCinema.nativeProgress("+(time/1000.0)+","+(duration/1000.0)+")");}
                        public void closed(){player=null;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);js("window.VladOSCinema && VladOSCinema.nativeClosed(false)");if(web!=null)web.requestFocus();}
                        public void ended(){player=null;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);js("window.VladOSCinema && VladOSCinema.nativeClosed(true)");if(web!=null)web.requestFocus();}
                    });root.addView(player,new FrameLayout.LayoutParams(-1,-1));player.requestFocus();
                });
            }catch(JSONException ignored){}
        }
        @JavascriptInterface public String launchCard(){String card=getIntent().getStringExtra("card");return card==null?"":card;}
        @JavascriptInterface public void history(String value){
            if(value.length()>20000)return;
            try{
                JSONObject item=new JSONObject(value);JSONObject card=item.getJSONObject("card");if(card.optInt("id",0)<=0)return;
                android.util.AtomicFile file=new android.util.AtomicFile(new File(getFilesDir(),"cinema-history.json"));
                JSONArray items=new JSONArray();items.put(item);
                try(InputStream in=file.openRead()) {JSONArray old=new JSONArray(Catalog.read(in,256*1024));for(int i=0;i<old.length()&&items.length()<8;i++)if(old.getJSONObject(i).getJSONObject("card").optInt("id")!=card.optInt("id"))items.put(old.getJSONObject(i));}catch(Exception ignored){}
                FileOutputStream out=file.startWrite();try{out.write(items.toString().getBytes("UTF-8"));file.finishWrite(out);}catch(Exception e){file.failWrite(out);}
            }catch(Exception ignored){}
        }
        @JavascriptInterface public void report(String message){if(message.startsWith("STATE "))android.util.Log.i("VladCinema",message.substring(0,Math.min(message.length(),400)));}
        @JavascriptInterface public void ready(){runOnUiThread(()->{if(cover!=null){root.removeView(cover);cover=null;}if(web!=null)web.requestFocus();});}
        @JavascriptInterface public void openProvider(String url){if(ProviderActivity.allowed(url))runOnUiThread(()->startActivity(new android.content.Intent(CinemaActivity.this,ProviderActivity.class).putExtra("url",url)));}
        @JavascriptInterface public void exit(){runOnUiThread(()->finish());}
        @JavascriptInterface public void request(String id,String url,String method,String body,String headers){
            if(destroyed)return;
            try{network.execute(()->fetch(id,url,method,body,headers));}catch(RejectedExecutionException ignored){}
        }
        @JavascriptInterface public void clearSession(){
            CookieManager cm=CookieManager.getInstance();
            for(String host:providers){String cookie=cm.getCookie("https://"+host);if(cookie!=null)for(String part:cookie.split(";")){String key=part.split("=",2)[0].trim();if(key.startsWith("dle_")||key.equals("PHPSESSID"))cm.setCookie("https://"+host,key+"=; Max-Age=0; Path=/; Secure");}}cm.flush();
        }
    }
    private boolean allowed(URL url){
        if(LocalTorrServer.request(url))return true;
        return "https".equals(url.getProtocol())&&(url.getPort()==-1||url.getPort()==443)&&url.getUserInfo()==null&&
            (providers.contains(url.getHost().toLowerCase(Locale.ROOT))||onlineHosts.contains(url.getHost().toLowerCase(Locale.ROOT))||url.getHost().equals("api.themoviedb.org")||url.getHost().equals("apitmdb.cub.best"));
    }
    private void fetch(String id,String target,String method,String body,String headerJson){
        JSONObject result=new JSONObject();HttpURLConnection c=null;
        try {
            if(!method.equals("GET")&&!method.equals("POST"))throw new IOException("Неподдерживаемый запрос");
            if(body.length()>65536)throw new IOException("Слишком большой запрос");
            URL url=new URL(target);
            for(int redirects=0;redirects<5;redirects++){
                if(!allowed(url))throw new IOException("Источник не подключён к VladOS");
                c=(HttpURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(12000);c.setReadTimeout(15000);
                c.setRequestMethod(method);c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 9; VladOS) AppleWebKit/537.36 Chrome/109.0.0.0 Safari/537.36");
                c.setRequestProperty("Accept-Language","ru,en;q=0.8");
                if(onlineHosts.contains(url.getHost())&&headerJson.length()<8192){JSONObject headers=new JSONObject(headerJson);for(String key:Arrays.asList("User-Agent","Referer","Origin","X-Requested-With"))if(headers.has(key))c.setRequestProperty(key,headers.getString(key));if("kinopoiskapiunofficial.tech".equals(url.getHost()))for(java.util.Iterator<String> keys=headers.keys();keys.hasNext();){String key=keys.next();if("X-API-KEY".equalsIgnoreCase(key))c.setRequestProperty("X-API-KEY",headers.getString(key));}}
                boolean provider=providers.contains(url.getHost());
                if(provider){c.setRequestProperty("Referer","https://"+url.getHost()+"/");c.setRequestProperty("X-Requested-With","XMLHttpRequest");String cookie=CookieManager.getInstance().getCookie(url.toString());if(cookie!=null)c.setRequestProperty("Cookie",cookie);}
                if(method.equals("POST")){c.setDoOutput(true);c.setRequestProperty("Content-Type",LocalTorrServer.request(url)?"application/json; charset=UTF-8":"application/x-www-form-urlencoded; charset=UTF-8");try(OutputStream out=c.getOutputStream()){out.write(body.getBytes("UTF-8"));}}
                int status=c.getResponseCode();
                android.util.Log.i("VladCinema","HTTP "+url.getHost()+url.getPath()+" "+status);
                if(provider)for(Map.Entry<String,List<String>> h:c.getHeaderFields().entrySet())if(h.getKey()!=null&&h.getKey().equalsIgnoreCase("Set-Cookie"))for(String cookie:h.getValue())CookieManager.getInstance().setCookie(url.toString(),cookie);
                if(status==301||status==302||status==303||status==307||status==308){String location=c.getHeaderField("Location");URL next=new URL(url,location);if(!next.getHost().equals(url.getHost())&&method.equals("POST"))throw new IOException("Источник сменил адрес. Обнови домен в настройках кино.");if(status==303||((status==301||status==302)&&method.equals("POST"))){method="GET";body="";}c.disconnect();c=null;url=next;continue;}
                InputStream stream=status>=400?c.getErrorStream():c.getInputStream();
                String text=stream==null?"":Catalog.read(stream,8*1024*1024);if(stream!=null)stream.close();
                if(provider&&(text.contains("Проверяем, что вы не бот")||text.contains("Just a moment")))throw new IOException("Источник просит проверку в браузере. Каталог доступен, просмотр пока недоступен.");
                result.put("status",status);result.put("text",text);result.put("type",c.getContentType()==null?"text/plain":c.getContentType());
                if(provider){String cookie=CookieManager.getInstance().getCookie(url.toString());result.put("hasSession",cookie!=null&&cookie.matches("(?s).*dle_user_id=[1-9][0-9]*.*")&&cookie.contains("dle_password="));}
                CookieManager.getInstance().flush();break;
            }
            if(!result.has("status"))throw new IOException("Слишком много перенаправлений");
        } catch(Exception e){android.util.Log.w("VladCinema","Network "+e.getClass().getSimpleName());try{result.put("status",0);result.put("error",e instanceof java.net.SocketTimeoutException?"Источник не ответил вовремя":e.getMessage()==null?"Ошибка сети":e.getMessage());}catch(JSONException ignored){}}
        finally{if(c!=null)c.disconnect();}
        js("window.VladOSCinema && VladOSCinema.response("+JSONObject.quote(id)+","+result.toString()+")");
    }
}
