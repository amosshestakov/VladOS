package ru.ilya.alitahome;

import android.app.Activity;
import android.os.Bundle;
import android.net.Uri;
import android.graphics.Color;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.util.*;

/** Provider-owned player, isolated from the local cinema's JavaScript bridge. */
public final class ProviderActivity extends Activity {
    private WebView web;
    private FrameLayout root;
    private View full;
    private WebChromeClient.CustomViewCallback fullCallback;
    private TextView status;
    static boolean allowed(String value){
        if(value==null||value.length()>4096)return false;
        Uri u=Uri.parse(value);
        return "https".equals(u.getScheme())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&Arrays.asList("rezka.fi","rezka.ag","hdrezka.ag","hdrezka.me","hdrezka-home.tv").contains(u.getHost());
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);Ui.full(this);
        String url=getIntent().getStringExtra("url");if(!allowed(url)){finish();return;}
        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
        LinearLayout layout=Ui.col(this);root.addView(layout,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(this);header.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,16),Ui.dp(this,8));header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(VladDesign.button(this,"К фильму","back",()->finish()),new LinearLayout.LayoutParams(Ui.dp(this,145),Ui.dp(this,40)));
        status=Ui.text(this,"HDRezka · загружаем плеер…",14,Ui.INK);status.setPadding(Ui.dp(this,20),0,0,0);header.addView(status,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(VladDesign.button(this,"Повторить",null,()->web.reload()),new LinearLayout.LayoutParams(Ui.dp(this,110),Ui.dp(this,40)));layout.addView(header);
        web=new WebView(this);web.setBackgroundColor(Color.BLACK);layout.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setMediaPlaybackRequiresUserGesture(false);s.setSupportMultipleWindows(false);s.setJavaScriptCanOpenWindowsAutomatically(false);
        // No addJavascriptInterface here: remote scripts cannot call VladOS APIs.
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return !allowed(r.getUrl().toString());}
            @Override public boolean shouldOverrideUrlLoading(WebView v,String u){return !allowed(u);}
            @Override public void onPageFinished(WebView v,String u){status.setText("HDRezka · выбери озвучку и нажми ▶");web.requestFocus();}
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())status.setText("Страница недоступна · можно повторить загрузку");}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onShowCustomView(View v,CustomViewCallback callback){if(full!=null){callback.onCustomViewHidden();return;}full=v;fullCallback=callback;root.addView(v,new FrameLayout.LayoutParams(-1,-1));Ui.full(ProviderActivity.this);}
            @Override public void onHideCustomView(){hideFull();}
            @Override public boolean onJsAlert(WebView v,String u,String m,JsResult r){r.cancel();return true;}
        });
        web.loadUrl(url);
    }
    private void hideFull(){if(full!=null){root.removeView(full);full=null;WebChromeClient.CustomViewCallback c=fullCallback;fullCallback=null;if(c!=null)c.onCustomViewHidden();}}
    @Override public void onBackPressed(){if(full!=null){hideFull();return;}finish();}
    @Override protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();Ui.full(this);}
    @Override protected void onDestroy(){hideFull();if(web!=null){web.stopLoading();web.destroy();web=null;}super.onDestroy();}
}
