package ru.ilya.alitahome;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private ModernHome modern;
    private FrameLayout root,drawer;
    private LinearLayout cinemaHistory;
    private LinearLayout page,body;
    private TextView clock,network,heroTitle,heroGenre,heroSummary;
    private final Handler handler=new Handler();
    private final ExecutorService images=Executors.newFixedThreadPool(2);
    private SharedPreferences prefs;
    private final ArrayList<App> apps=new ArrayList<>();
    private List<Catalog.Show> shows=new ArrayList<>();
    private String currentPage="home",lastPackages="";
    private Catalog.Show selected;
    private View menuButton,lastFocus;
    private boolean vpn;
    static class App {String pkg,label;Intent intent;App(String p,String l,Intent i){pkg=p;label=l;intent=i;}}
    private static class Service {String name,pkg,url;int color;Service(String n,String p,String u,int c){name=n;pkg=p;url=u;color=c;}}
    private final Service[] services={
        new Service("RuStore","ru.vk.store.tv","https://www.rustore.ru/tv",0xff3486eb),
        new Service("YouTube","com.google.android.youtube.tv","https://play.google.com/store/apps/details?id=com.google.android.youtube.tv",0xffe5525a),
        new Service("RUTUBE","ru.rutube.app","https://www.rustore.ru/catalog/app/ru.rutube.app",0xff384460),
        new Service("VK Видео","com.vk.tv","https://www.rustore.ru/catalog/app/com.vk.tv",0xff4b8ee7),
        new Service("Okko","tv.okko.androidtv","https://www.rustore.ru/catalog/app/tv.okko.androidtv",0xff9262d6),
        new Service("Кинопоиск","ru.kinopoisk.tv","https://www.rustore.ru/catalog/app/ru.kinopoisk.tv",0xffeb9358)};
    private int dp(float n){return Ui.dp(this,n);}
    private final Runnable ticker=new Runnable(){public void run(){status();handler.postDelayed(this,5000);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);prefs=getSharedPreferences("home",MODE_PRIVATE);Ui.full(this);

        if(getIntent().getBooleanExtra("check_assets",false))AssetCheck.run(this);discover();if(BuildConfig.NEW_UI){modern=new ModernHome(this);modern.create();return;}build();if(getIntent().getBooleanExtra("drawer",false))handler.post(()->toggleDrawer());
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(modern!=null&&Intent.ACTION_MAIN.equals(intent.getAction())&&!intent.getBooleanExtra("drawer",false)){modern.resetHome();return;}if(intent.getBooleanExtra("drawer",false)){if(modern!=null)modern.menu();else toggleDrawer();}}
    @Override protected void onResume(){super.onResume();RemoteService.xiaomiSession=false;Ui.full(this);discover();if(modern!=null){modern.resume();return;}String stamp;
        // Only rebuild after package changes, preserving normal focus on resume.
        StringBuilder keys=new StringBuilder();for(App a:apps)keys.append(a.pkg).append(';');stamp=keys.toString();
        if(!stamp.equals(lastPackages)){lastPackages=stamp;showPage(currentPage);}
        refreshCinemaHistory();handler.removeCallbacks(ticker);ticker.run();}
    @Override protected void onPause(){if(modern!=null)modern.pause();handler.removeCallbacks(ticker);super.onPause();}
    @Override protected void onDestroy(){if(modern!=null)modern.destroy();images.shutdownNow();super.onDestroy();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(modern!=null&&modern.dispatch(event))return true;
        if(event.getKeyCode()==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_UP)toggleDrawer();return true;}
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed(){if(modern!=null){modern.back();return;}if(drawer!=null){closeDrawer();return;}if(!currentPage.equals("home")){showPage("home");return;}menuButton.requestFocus();}
    private TextView text(String s,int size,int color){return Ui.text(this,s,size,color);}
    private void gap(LinearLayout parent,int size){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(size)));}
    private void build(){
        root=new FrameLayout(this);root.setBackgroundColor(Ui.BASE);setContentView(root);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);page=Ui.col(this);page.setPadding(dp(30),dp(20),dp(30),dp(24));scroll.addView(page);root.addView(scroll);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);logo.setImageDrawable(getResources().getDrawable(getResources().getIdentifier("vlados_logo","drawable",getPackageName()),getTheme()));
        LinearLayout.LayoutParams logoLp=new LinearLayout.LayoutParams(dp(42),dp(42));logoLp.setMargins(0,0,dp(12),0);header.addView(logo,logoLp);
        LinearLayout brand=Ui.col(this);TextView name=text("VladOS",25,Ui.INK);name.setTypeface(null,Typeface.BOLD);brand.addView(name);brand.addView(text("",9,Ui.MUTED));header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout right=Ui.col(this);clock=text("",22,Ui.INK);clock.setGravity(Gravity.RIGHT);network=text("",10,Ui.MUTED);network.setGravity(Gravity.RIGHT);right.addView(clock);right.addView(network);header.addView(right);
        page.addView(header);gap(page,22);
        LinearLayout nav=new LinearLayout(this);
        tab(nav,"Главная",0,()->showPage("home"));tab(nav,"Кино",0,()->startActivity(new Intent(this,CinemaActivity.class)));tab(nav,"Приложения",0,()->showPage("apps"));
        tab(nav,"Установить APK",0,()->startActivity(new Intent(this,InstallerActivity.class)));
        tab(nav,"Трансляция",0,()->receiver());tab(nav,"Happ — переключить VPN",0,()->toggleHapp());
        tab(nav,"Настройки",0,()->startActivity(new Intent(this,SettingsActivity.class)));
        Button menu=Ui.button(this,"Меню",()->toggleDrawer());Glyph g=new Glyph("menu",Ui.INK);g.setBounds(0,0,dp(24),dp(24));menu.setCompoundDrawables(null,g,null,null);
        nav.addView(menu,new LinearLayout.LayoutParams(dp(55),dp(48)));menuButton=menu;
        page.addView(nav);gap(page,8);TextView hint=text("Любимые приложения — всегда рядом",12,Ui.GREEN);hint.setTag("focus_hint");page.addView(hint);gap(page,22);
        body=Ui.col(this);page.addView(body);showPage("home");
        handler.post(()->{View first=body.findViewWithTag("first_app");if(first!=null)first.requestFocus();else menu.requestFocus();});
    }
    private void tab(LinearLayout row,String title,int color,Runnable action){
        Button b=Ui.button(this,title,action);b.setText("");b.setCompoundDrawables(null,null,null,null);android.graphics.drawable.Drawable navIcon=getDrawable(getResources().getIdentifier("action_"+Glyph.infer(title),"drawable",getPackageName()));navIcon.setBounds(0,0,dp(24),dp(24));b.setCompoundDrawables(null,navIcon,null,null);Ui.focus(this,b,Ui.CARD);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(46),1);lp.setMargins(0,0,dp(10),0);row.addView(b,lp);
    }
    private void showPage(String which){if(body==null)return;currentPage=which;body.removeAllViews();
        if(which.equals("apps")){heading(body,"Все приложения","Удерживай OK, чтобы добавить в избранное.");grid(body,apps);return;}
        if(which.equals("home")){
            Set<String> favorites=prefs.getStringSet("favorites",Collections.emptySet());ArrayList<App> pinned=new ArrayList<>();for(App app:apps)if(favorites.contains(app.pkg))pinned.add(app);
            heading(body,"Избранное",pinned.isEmpty()?"Удерживай OK на приложении, чтобы закрепить его здесь.":"");gap(body,12);
            if(pinned.isEmpty())serviceRow(body);else{grid(body,pinned);gap(body,24);heading(body,"Сервисы","");gap(body,10);serviceRow(body);}
            cinemaHistory=Ui.col(this);body.addView(cinemaHistory);refreshCinemaHistory();
            gap(body,25);TextView helper=text("",11,Ui.MUTED);body.addView(helper);return;
        }
        if(!shows.isEmpty())renderCinema();else heading(body,"Афиши недоступны","Проверь сеть или выбери другой источник в настройках.");
        gap(body,12);LinearLayout foot=new LinearLayout(this);
        foot.addView(Ui.button(this,"Об афишах",()->sources()),new LinearLayout.LayoutParams(dp(60),dp(42)));
        foot.addView(Ui.button(this,"Обновить афиши",()->refreshPosters()),new LinearLayout.LayoutParams(dp(60),dp(42)));body.addView(foot);
    }

    private void refreshCinemaHistory(){
        if(cinemaHistory==null)return;cinemaHistory.removeAllViews();
        try(java.io.InputStream in=new java.io.FileInputStream(new java.io.File(getFilesDir(),"cinema-history.json"))){
            org.json.JSONArray items=new org.json.JSONArray(Catalog.read(in,256*1024));
            if(items.length()==0)return;gap(cinemaHistory,24);heading(cinemaHistory,"Продолжить просмотр","");gap(cinemaHistory,12);
            HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);
            for(int n=0;n<items.length();n++){
                org.json.JSONObject item=items.getJSONObject(n),card=item.getJSONObject("card");double duration=item.optDouble("duration",0),time=item.optDouble("time",0);if(duration>0&&time/duration>.95)continue;
                LinearLayout tile=Ui.col(this);tile.setPadding(dp(18),dp(18),dp(18),dp(18));Ui.focus(this,tile,Ui.CARD);
                TextView title=text(item.optString("title",card.optString("title",card.optString("name"))),15,Ui.INK);title.setMaxLines(2);tile.addView(title);
                TextView position=text(((int)time/60)+" мин · продолжить",11,Ui.MUTED);position.setPadding(0,dp(10),0,0);tile.addView(position);
                tile.setContentDescription(title.getText());String value=card.toString();tile.setOnClickListener(v->startActivity(new Intent(this,CinemaActivity.class).putExtra("card",value)));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(255),dp(108));lp.rightMargin=dp(12);row.addView(tile,lp);
            }scroll.addView(row);cinemaHistory.addView(scroll);
        }catch(Exception ignored){}
    }

    List<App> designApps(){return new ArrayList<>(apps);}
    SharedPreferences designPrefs(){return prefs;}
    void designOpen(App app){safe(app.intent);}
    void designCast(){receiver();}
    void designHapp(){toggleHapp();} boolean designToggleVpn(){return toggleHapp();}
    void designInstaller(boolean inbox){startActivity(new Intent(this,InstallerActivity.class).putExtra("inbox",inbox));}
    void designFiles(){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),71);}catch(Exception e){for(App app:apps){String name=(app.label+app.pkg).toLowerCase(Locale.ROOT);if(name.contains("file")||name.contains("файл")||name.contains("commander")){safe(app.intent);return;}}Ui.message(this,"Файлы","Системный выбор файлов недоступен. APK можно передать с телефона через установщик.");}}
    void designUninstall(String pkg){safe(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+pkg)));}
    String designTvLabel(){String pkg=prefs.getString("tv_app_package","__builtin");String name="Лампа";if(!pkg.equals("__builtin"))try{name=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception ignored){}return "Каналы, фильмы и сериалы · "+name;}
    void designTV(){String pkg=prefs.getString("tv_app_package","__builtin");if(pkg.equals("__builtin")){startActivity(new Intent(this,CinemaActivity.class));return;}Intent intent=getPackageManager().getLeanbackLaunchIntentForPackage(pkg);if(intent==null)intent=getPackageManager().getLaunchIntentForPackage(pkg);if(intent!=null)safe(intent);else new Modal.Builder(this).setTitle("Приложение для ТВ не установлено").setMessage("Установи APK или выбери встроенную Лампу в настройках приложений.").setPositiveButton("Установить APK",(d,w)->designInstaller(false)).setNegativeButton("Назад",null).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==71&&result==RESULT_OK&&data!=null&&data.getData()!=null){Intent open=new Intent(Intent.ACTION_VIEW).setData(data.getData()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);safe(open);}}
    private void heading(LinearLayout parent,String title,String sub){TextView h=text(title,21,Ui.INK);h.setTypeface(null,Typeface.BOLD);parent.addView(h);if(!sub.isEmpty()){TextView d=text(sub,11,Ui.MUTED);d.setPadding(0,dp(3),0,dp(8));parent.addView(d);}}
    private void renderCinema(){
        LinearLayout cinema=new LinearLayout(this);cinema.setBaselineAligned(false);
        LinearLayout hero=Ui.col(this);hero.setPadding(0,dp(11),dp(19),0);
        hero.addView(text("ВЫБЕРИ НАСТРОЕНИЕ",10,Ui.GREEN));
        heroTitle=text("",25,Ui.INK);heroTitle.setTypeface(null,Typeface.BOLD);heroTitle.setMaxLines(2);hero.addView(heroTitle);
        heroGenre=text("",11,Ui.GREEN);heroGenre.setPadding(0,dp(8),0,dp(5));hero.addView(heroGenre);
        heroSummary=text("",11,Ui.MUTED);heroSummary.setMaxLines(5);hero.addView(heroSummary);
        gap(hero,8);hero.addView(Ui.button(this,"Открыть видеосервис →",()->watch()));
        cinema.addView(hero,new LinearLayout.LayoutParams(dp(205),dp(273)));
        LinearLayout posters=Ui.col(this);heading(posters,"Свежие серии",prefs.getString("catalog_date","").isEmpty()?"Офлайн-подборка TVmaze · обновление ожидается":"Выход серий: "+prefs.getString("catalog_date","")+" · TVmaze");
        HorizontalScrollView horizontal=new HorizontalScrollView(this);horizontal.setClipToPadding(false);horizontal.setHorizontalScrollBarEnabled(false);
        LinearLayout cards=new LinearLayout(this);cards.setPadding(dp(3),dp(3),dp(7),dp(3));horizontal.addView(cards);
        for(Catalog.Show show:shows){
            LinearLayout card=Ui.col(this);card.setPadding(dp(4),dp(4),dp(4),dp(5));Ui.focus(this,card,Ui.CARD);
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setClipToOutline(true);image.setBackground(Ui.bg(this,Ui.CARD));
            card.addView(image,new LinearLayout.LayoutParams(-1,dp(167)));loadPoster(image,show);
            TextView label=text(show.title,11,Ui.INK);label.setMaxLines(2);label.setEllipsize(TextUtils.TruncateAt.END);label.setPadding(dp(3),dp(5),dp(3),0);card.addView(label);
            card.setContentDescription(show.title);card.setOnClickListener(v->{select(show);watch();});
            card.setOnFocusChangeListener((v,focus)->{GradientDrawable bg=Ui.bg(this,focus?0xff263a40:Ui.CARD);if(focus){bg.setStroke(dp(2),Ui.GREEN);select(show);Ui.hint(this,show.title);}v.setBackground(bg);});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(117),dp(213));lp.setMargins(0,0,dp(11),0);cards.addView(card,lp);
        }
        posters.addView(horizontal);cinema.addView(posters,new LinearLayout.LayoutParams(0,-2,1));body.addView(cinema);
        if(selected==null||!shows.contains(selected))selected=shows.get(0);select(selected);
    }
    private void select(Catalog.Show show){selected=show;if(heroTitle==null)return;heroTitle.setText(show.title);heroGenre.setText(show.genre);
        String plain=Html.fromHtml(show.summary).toString();heroSummary.setText(plain.length()>220?plain.substring(0,217)+"…":plain);}
    private void loadPoster(ImageView view,Catalog.Show show){images.execute(()->{try{Bitmap bitmap=Catalog.image(this,show);runOnUiThread(()->{if(!isDestroyed())view.setImageBitmap(bitmap);});}catch(Exception ignored){}});}
    private void serviceRow(LinearLayout parent){LinearLayout row=new LinearLayout(this);int index=0;for(Service service:services){
        LinearLayout tile=Ui.col(this);tile.setGravity(Gravity.CENTER);tile.setPadding(dp(14),dp(14),dp(14),dp(14));tile.setContentDescription(service.name+(find(service.pkg)!=null?" — открыть":" — установить"));Ui.focus(this,tile,Ui.CARD);
        ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try{App installed=find(service.pkg);if(installed!=null)icon.setImageDrawable(getPackageManager().getApplicationIcon(installed.pkg));
            else try(java.io.InputStream in=getAssets().open("icons/"+service.pkg+".png")){icon.setImageBitmap(BitmapFactory.decodeStream(in));}}
        catch(Exception ignored){icon.setImageDrawable(new Glyph("apps",service.color));}
        tile.addView(icon,new LinearLayout.LayoutParams(dp(54),dp(54)));TextView serviceName=text(service.name,12,Ui.INK);serviceName.setPadding(0,dp(8),0,0);tile.addView(serviceName);tile.setOnClickListener(v->openService(service));
        if(index++==0)tile.setTag("first_app");
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(115),1);lp.setMargins(0,0,dp(12),0);row.addView(tile,lp);}parent.addView(row);}
    private App find(String pkg){for(App a:apps)if(a.pkg.equals(pkg))return a;return null;}
    private void openService(Service s){App installed=find(s.pkg);if(installed!=null){safe(installed.intent);return;}
        new Modal.Builder(this).setTitle(s.name+" ещё не установлен").setItems(new String[]{"Открыть официальный магазин / страницу","Установщик APK"},(d,w)->{
            if(w==1){startActivity(new Intent(this,InstallerActivity.class));return;}
            Intent store=new Intent(Intent.ACTION_VIEW,Uri.parse(s.url));
            if(!s.name.equals("YouTube")&&find("ru.vk.store.tv")!=null){
                Intent deep=new Intent(Intent.ACTION_VIEW,Uri.parse("rustore://apps.rustore.ru/app/"+s.pkg)).setPackage("ru.vk.store.tv");
                if(deep.resolveActivity(getPackageManager())!=null){safe(deep);return;}
                store.setPackage("ru.vk.store.tv");if(store.resolveActivity(getPackageManager())!=null){safe(store);return;}store.setPackage(null);}
            if(store.resolveActivity(getPackageManager())!=null)safe(store);else Ui.message(this,"Открой магазин на телевизоре",s.url+"\n\nВ этой прошивке нет приложения для открытия ссылки. Установи RuStore TV через установщик. Для YouTube нужен совместимый официальный APK или Google Play, если он доступен.");
        }).setNegativeButton("Отмена",null).show();
    }
    private void grid(LinearLayout parent,List<App> entries){LinearLayout row=null;int columns=6;
        for(int n=0;n<entries.size();n++){if(n%columns==0){row=new LinearLayout(this);parent.addView(row);}App app=entries.get(n);
            LinearLayout card=Ui.col(this);card.setPadding(dp(8),dp(9),dp(8),dp(8));card.setGravity(Gravity.CENTER);card.setContentDescription(app.label);if(!entries.isEmpty()&&app==entries.get(0))card.setTag("first_app");Ui.focus(this,card,Ui.CARD);
            ImageView icon=new ImageView(this);try{icon.setImageDrawable(getPackageManager().getApplicationIcon(app.pkg));}catch(Exception ignored){}card.addView(icon,new LinearLayout.LayoutParams(dp(50),dp(50)));
            TextView appName=text(app.label,12,Ui.INK);appName.setGravity(Gravity.CENTER);appName.setMaxLines(2);appName.setEllipsize(TextUtils.TruncateAt.END);appName.setPadding(0,dp(7),0,0);card.addView(appName);card.setOnClickListener(v->safe(app.intent));
            card.setOnLongClickListener(v->{Set<String> favorites=new HashSet<>(prefs.getStringSet("favorites",Collections.emptySet()));boolean removed=favorites.remove(app.pkg);if(!removed)favorites.add(app.pkg);prefs.edit().putStringSet("favorites",favorites).apply();Toast.makeText(this,removed?"Убрано из избранного":"Добавлено в избранное",Toast.LENGTH_SHORT).show();return true;});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(115),1);lp.setMargins(0,0,dp(9),dp(9));row.addView(card,lp);}
        if(row!=null&&entries.size()%columns!=0)for(int n=entries.size()%columns;n<columns;n++){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(115),1);lp.setMargins(0,0,dp(9),0);row.addView(new View(this),lp);}
    }
    private void discover(){apps.clear();PackageManager pm=getPackageManager();Set<String> seen=new HashSet<>();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER})for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)){
            String pkg=r.activityInfo.packageName;if(pkg.equals(getPackageName())||!seen.add(pkg)||!r.activityInfo.enabled)continue;
            Intent launch=new Intent(Intent.ACTION_MAIN).addCategory(category).setComponent(new ComponentName(pkg,r.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            apps.add(new App(pkg,r.loadLabel(pm).toString(),launch));}
        Collections.sort(apps,(a,b)->a.label.compareToIgnoreCase(b.label));
    }
    private void toggleDrawer(){if(drawer!=null){closeDrawer();return;}lastFocus=getCurrentFocus();drawer=new FrameLayout(this);drawer.setBackgroundColor(0x770d302b);drawer.setOnClickListener(v->closeDrawer());root.addView(drawer,new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xff10151c);LinearLayout panel=Ui.col(this);panel.setPadding(dp(20),dp(25),dp(20),dp(20));panel.setOnClickListener(v->{});scroll.addView(panel);
        drawer.addView(scroll,new FrameLayout.LayoutParams(dp(290),-1,Gravity.LEFT));panel.addView(text("VladOS",22,Ui.INK));TextView drawerHint=text("Быстрый доступ",13,Ui.GREEN);drawerHint.setTag("drawer_hint");panel.addView(drawerHint);gap(panel,17);
        Button happ=Ui.button(this,"Happ  ·  "+(vpn?"VPN активен":"Открыть VPN"),()->{closeDrawer();toggleHapp();});panel.addView(happ);
        panel.addView(Ui.button(this,"Трансляция с телефона",()->{closeDrawer();receiver();}));
        panel.addView(Ui.button(this,"Установить приложение",()->{closeDrawer();startActivity(new Intent(this,InstallerActivity.class));}));
        panel.addView(Ui.button(this,"Настройки",()->{closeDrawer();startActivity(new Intent(this,SettingsActivity.class));}));
        panel.addView(Ui.button(this,"Все приложения",()->{closeDrawer();showPage("apps");}));
        panel.addView(Ui.button(this,"Wi-Fi / сеть",()->{closeDrawer();safe(new Intent(Settings.ACTION_WIFI_SETTINGS));}));
        panel.addView(Ui.button(this,"Штатный главный экран",()->{closeDrawer();safe(new Intent().setComponent(new ComponentName("com.xiaomi.eetv.home","com.xiaomi.eetv.home.Launcher")));}));
        panel.addView(Ui.button(this,"Кнопка Menu вне VladOS",()->Ui.message(this,"Быстрое меню пультом","Внутри VladOS нажми Menu (☰). Для работы в других приложениях включи службу «VladOS — кнопка меню» в специальных возможностях либо запусти Enable-Remote.ps1 из архива.\n\nОна перехватывает Menu вместо меню других приложений; содержимое экрана не читает. Если пульт не выдаёт Menu, нужна настройка другой кнопки.")));
        panel.addView(Ui.button(this,"О VladOS",()->Ui.message(this,"VladOS 0.3.1","Оболочка для alita / Android 9.\n\nУстановщик: флешка, телефон и официальные APK. VPN подключается в Happ. Трансляция требует совместимого приёмника.\n\nОбновления и исходники сохраняют ключ первой версии.")));
        panel.addView(Ui.button(this,"Закрыть меню",()->closeDrawer()));happ.requestFocus();
    }
    private void closeDrawer(){if(drawer!=null){root.removeView(drawer);drawer=null;if(lastFocus!=null)lastFocus.requestFocus();}}
    private boolean toggleHapp(){
        Intent toggle=new Intent(Intent.ACTION_VIEW,Uri.parse("happ://toggle_without_ui")).setPackage("su.happ.proxyutility");
        if(toggle.resolveActivity(getPackageManager())==null){Ui.message(this,"Happ не поддерживает команду","Для переключения без интерфейса нужен Happ с поддержкой happ://toggle_without_ui. Обнови клиент и проверь команду. Открыть клиент можно из настроек VladOS.");return false;}
        try{startActivity(toggle);Toast.makeText(this,"Команда переключения VPN отправлена",Toast.LENGTH_SHORT).show();handler.postDelayed(()->status(),2000);return true;}catch(Exception e){Ui.message(this,"VPN не переключён",e.getMessage());return false;}
    }
    private void receiver(){String saved=prefs.getString("receiver","");App configured=find(saved);if(configured!=null){safe(configured.intent);return;}for(String pkg:new String[]{"com.ionitech.airscreen","com.xiaomi.mitv.smartshare","com.duokan.airkan.tvbox","com.xiaomi.wfdsinkhelperservice","com.xiaomi.miplay"}){App app=find(pkg);if(app!=null){prefs.edit().putString("receiver",app.pkg).apply();safe(app.intent);return;}}
        ArrayList<App> choices=new ArrayList<>();for(App app:apps){String s=(app.label+app.pkg).toLowerCase(Locale.ROOT);if(s.contains("cast")||s.contains("mirror")||s.contains("airplay")||s.contains("投屏"))choices.add(app);}
        if(choices.isEmpty()){Ui.message(this,"Приёмник не найден","У системных служб Xiaomi не найден доступный экран запуска. Установи совместимый приёмник (например AirScreen) либо открой штатный раздел трансляции вручную. Поддержку протоколов нужно проверить на ТВ.");return;}
        String[] labels=new String[choices.size()];for(int n=0;n<labels.length;n++)labels[n]=choices.get(n).label;
        new Modal.Builder(this).setTitle("Выбери приёмник").setItems(labels,(d,w)->{prefs.edit().putString("receiver",choices.get(w).pkg).apply();safe(choices.get(w).intent);}).show();
    }
    private void watch(){String title=selected==null?"":selected.title;
        new Modal.Builder(this).setTitle(title).setMessage((selected==null?"":Html.fromHtml(selected.summary).toString())+"\n\nВыбери сервис просмотра. Название нужно найти в его поиске; наличие и подписка не проверялись.")
            .setPositiveButton("Кинопоиск",(d,w)->openService(services[5])).setNeutralButton("Okko",(d,w)->openService(services[4])).setNegativeButton("Закрыть",null).show();
    }
    private void sources(){new Modal.Builder(this).setTitle("Источник афиш")
        .setMessage("Сейчас: лента выхода новых серий на стримингах TVmaze, данные по CC BY-SA. Обновляется автоматически раз в 6 часов при запуске VladOS, изображения загружаются по сети. При отсутствии сети остаётся сохранённая подборка с датой источника.\n\nДля полноценной подборки фильмов предлагаю TMDB: нужен API-ключ. Публичный API LazyMedia Deluxe для экспорта афиш не подтверждён; интеграция пока не реализована.\n\nКаталог афиш не предоставляет видео и не подтверждает наличие названия в онлайн-кинотеатре.")
        .setPositiveButton("TVmaze и лицензия",(d,w)->safe(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.tvmaze.com/api#licensing"))))
        .setNegativeButton("Закрыть",null).show();}
    private void refreshPosters(){Toast.makeText(this,"Загружаем текущую ленту TVmaze…",Toast.LENGTH_SHORT).show();images.execute(()->{
        try{List<Catalog.Show> fresh=Catalog.current(this,true);runOnUiThread(()->{if(isDestroyed())return;shows=fresh;showPage(currentPage);Toast.makeText(this,"Лента обновлена",Toast.LENGTH_SHORT).show();});}
        catch(Exception e){runOnUiThread(()->{if(!isDestroyed())Ui.message(this,"Лента не обновлена","Оставили сохранённые афиши. Проверь сеть и доступ к TVmaze.\n"+e.getMessage());});}
    });}
    private void safe(Intent intent){try{startActivity(intent);}catch(Exception e){Ui.message(this,"Не удалось открыть",e.getClass().getSimpleName()+"\nРаздел может отсутствовать в прошивке.");}}
    private void status(){if(modern!=null){modern.status();return;}clock.setText(new SimpleDateFormat("HH:mm   EEE, d MMM",new Locale("ru")).format(new Date()));try{
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);vpn=false;for(Network n:cm.getAllNetworks()){NetworkCapabilities c=cm.getNetworkCapabilities(n);if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_VPN))vpn=true;}
        String ip=InstallerActivity.lanIp();network.setText((ip==null?"Нет локальной сети":ip)+"  ·  "+(vpn?"VPN включён":"VPN выключен"));
    }catch(Exception e){network.setText("Статус сети недоступен");}}
}
