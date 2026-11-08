package ru.ilya.alitahome;
import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class SettingsActivity extends Activity {
    private LinearLayout content,btList,side;
    private final Map<String,TextView> categoryItems=new LinkedHashMap<>();
    private TextView btStatus;
    private BluetoothAdapter bt;
    private final LinkedHashMap<String,BluetoothDevice> discovered=new LinkedHashMap<>();
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private String screen="sound";
    private boolean registered;
    private int dp(float n){return Ui.dp(this,n);}
    private SharedPreferences prefs(){return getSharedPreferences("home",0);}
    private final BroadcastReceiver events=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        if(BluetoothDevice.ACTION_FOUND.equals(i.getAction())){BluetoothDevice d=i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);if(d!=null)discovered.put(d.getAddress(),d);}
        if(screen.equals("bluetooth"))renderBluetoothList();
    }};
    @Override public void onCreate(Bundle b){super.onCreate(b);Ui.full(this);
        buildRedesign();
        bt=BluetoothAdapter.getDefaultAdapter();IntentFilter filter=new IntentFilter();filter.addAction(BluetoothDevice.ACTION_FOUND);filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);registerReceiver(events,filter);registered=true;
        String initial=b==null?getIntent().getStringExtra("section"):b.getString("section");if("about".equals(initial))initial="system";if(initial==null||!categoryItems.containsKey(initial))initial="overview";show(initial);categoryItems.get(initial).requestFocus();
    }
    @Override protected void onResume(){super.onResume();Ui.full(this);if(content!=null){View focused=getCurrentFocus();int id=focused==null?View.NO_ID:focused.getId();boolean restore=focused!=null&&!categoryItems.containsValue(focused);show(screen);if(restore){View target=id==View.NO_ID?null:content.findViewById(id);if(target==null)target=firstControl(content);if(target!=null)target.requestFocus();}}}
    private void buildRedesign(){
        FrameLayout root=VladDesign.background(this,true);setContentView(root);LinearLayout layout=VladDesign.column(this);layout.setPadding(dp(60),dp(35),dp(60),dp(34));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(VladDesign.display(this,"Настройки",29,400),new LinearLayout.LayoutParams(0,-2,1));header.addView(VladDesign.button(this,"Назад","back",()->finish()),new LinearLayout.LayoutParams(dp(100),dp(36)));layout.addView(header);VladDesign.gap(this,layout,24);
        LinearLayout columns=new LinearLayout(this);side=VladDesign.column(this);String[][] categories={{"overview","Центр настроек","gear"},{"network","Сеть","wifi"},{"bluetooth","Пульты и устройства","user"},{"picture","Экран","tv"},{"sound","Звук","speaker"},{"apps","Приложения","grid"},{"storage","Хранилище","space"},{"system","Система","gear"}};
        for(String[] category:categories){TextView item=VladDesign.button(this,category[1],category[2],()->show(category[0]));item.setOnFocusChangeListener((view,on)->{item.setTextColor(on?VladDesign.INK:VladDesign.TEXT);item.setBackground(VladDesign.shape(this,on?VladDesign.TEXT:screen.equals(category[0])?VladDesign.GLASS_HI:android.graphics.Color.TRANSPARENT,24));android.graphics.drawable.Drawable icon=VladDesign.icon(this,category[2],on);icon.setBounds(0,0,dp(17),dp(17));item.setCompoundDrawables(icon,null,null,null);if(on)show(category[0]);});item.setTag(category[0]);categoryItems.put(category[0],item);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(40));p.bottomMargin=dp(4);side.addView(item,p);}columns.addView(side,new LinearLayout.LayoutParams(dp(220),-1));
        ScrollView scroll=new ScrollView(this);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);content=VladDesign.column(this);content.setPadding(dp(28),dp(4),dp(8),dp(20));scroll.addView(content);columns.addView(scroll,new LinearLayout.LayoutParams(0,-1,1));layout.addView(columns,new LinearLayout.LayoutParams(-1,0,1));root.addView(layout,new FrameLayout.LayoutParams(-1,-1));VladDesign.hints(this,root,true);
    }
    private View firstControl(View v){if(v.isFocusable())return v;if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int n=0;n<g.getChildCount();n++){View found=firstControl(g.getChildAt(n));if(found!=null)return found;}}return null;}
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event){if(event.getAction()==android.view.KeyEvent.ACTION_DOWN){View focused=getCurrentFocus();if(event.getKeyCode()==android.view.KeyEvent.KEYCODE_DPAD_RIGHT&&focused!=null&&categoryItems.containsValue(focused)){View first=firstControl(content);if(first!=null){first.requestFocus();return true;}}if(event.getKeyCode()==android.view.KeyEvent.KEYCODE_DPAD_LEFT&&focused!=null&&!categoryItems.containsValue(focused)&&!(focused instanceof android.widget.EditText)&&!(focused instanceof android.widget.SeekBar)){TextView category=categoryItems.get(screen);if(category==null)category=categoryItems.get(screen.equals("sleep")?"picture":"system");category.requestFocus();return true;}}return super.dispatchKeyEvent(event);}
    @Override public void onBackPressed(){if(screen.equals("sleep")){categoryItems.get("picture").requestFocus();return;}if(screen.equals("quick")){categoryItems.get("system").requestFocus();return;}finish();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("section",screen);super.onSaveInstanceState(out);}
    private void overview(){
        title("Центр настроек","Всё для вашего телевизора — в одном месте");
        android.net.ConnectivityManager cm=(android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        android.net.NetworkInfo net=cm.getActiveNetworkInfo();StatFs disk=new StatFs(getFilesDir().getPath());
        content.addView(Ui.text(this,(net!=null&&net.isConnected()?"Сеть подключена":"Нет подключения к сети")+"  ·  Свободно "+size(disk.getAvailableBytes()),16,Ui.INK));
        LinearLayout actions=Ui.col(this);action(actions,"Сеть и подключение",()->selectCategory("network"));action(actions,"Пульты, наушники и устройства",()->selectCategory("bluetooth"));action(actions,"Экран и таймер сна",()->selectCategory("picture"));action(actions,"Звук и аудиовыход",()->selectCategory("sound"));action(actions,"Оболочка и системные настройки",()->selectCategory("system"));content.addView(actions);
    }
    private void selectCategory(String key){categoryItems.get(key).requestFocus();show(key);View first=firstControl(content);if(first!=null)first.requestFocus();}
    private String version(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "";}}
    private boolean isHome(){ResolveInfo r=getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),PackageManager.MATCH_DEFAULT_ONLY);return r!=null&&r.activityInfo!=null&&getPackageName().equals(r.activityInfo.packageName);}
    private void system(){
        title("Система","VladOS "+version()+"  ·  "+Build.MANUFACTURER+" "+Build.MODEL);
        content.addView(Ui.text(this,"Android "+Build.VERSION.RELEASE+"  ·  "+(isHome()?"VladOS — главный экран":"Для Home используется служба возврата"),15,Ui.MUTED));
        content.addView(Ui.text(this,"Служба управления: "+(RemoteService.live==null?"не подключена":"подключена"),13,Ui.MUTED));LinearLayout actions=Ui.col(this);action(actions,"Обновление VladOS",()->startActivity(new Intent(this,UpdateActivity.class)));action(actions,"Возврат по Home: "+(prefs().getBoolean("home_return",true)?"включён":"выключен"),()->{prefs().edit().putBoolean("home_return",!prefs().getBoolean("home_return",true)).apply();show("system");});action(actions,"Главный экран по умолчанию",()->openSystem("android.settings.HOME_SETTINGS"));action(actions,"Быстрый доступ и кнопка Menu",()->show("quick"));action(actions,"Служба управления и таймер сна",()->serviceHelp());action(actions,"Все настройки телевизора",()->openSystem("android.settings.SETTINGS"));action(actions,"Открыть стандартную оболочку Xiaomi",()->openXiaomi());content.addView(actions);
        content.addView(Ui.text(this,"Кнопка Xiaomi открывает штатный главный экран. Выбор оболочки по умолчанию сохраняется. Автовозврат приостановлен до следующего открытия VladOS.",13,Ui.MUTED));
    }
    private void openSystem(String action){String tvAction="android.settings.WIFI_SETTINGS".equals(action)?"com.xiaomi.mitv.settings.WIFI_SETTINGS":("android.settings.SOUND_SETTINGS".equals(action)||"android.settings.DISPLAY_SETTINGS".equals(action))?"com.xiaomi.mitv.settings.DISPLAYSOUND_SETTINGS":action;try{startActivity(new Intent(tvAction).setPackage("com.xiaomi.mitv.settings"));return;}catch(ActivityNotFoundException|SecurityException ignored){}if(!"android.settings.ACCESSIBILITY_SETTINGS".equals(action)){Intent tv=launch("com.xiaomi.mitv.settings");if(tv!=null)try{startActivity(tv);return;}catch(ActivityNotFoundException|SecurityException ignored){}}try{startActivity(new Intent(action));return;}catch(ActivityNotFoundException|SecurityException ignored){}if(!"android.settings.SETTINGS".equals(action))try{startActivity(new Intent("android.settings.SETTINGS"));return;}catch(ActivityNotFoundException|SecurityException ignored){}Ui.message(this,"Раздел недоступен","Телевизор не предоставил экран этих настроек.");}
    private void openXiaomi(){RemoteService.xiaomiSession=true;
        // Use a concrete component: a generic HOME intent would return to VladOS.
        try{startActivity(new Intent().setComponent(new ComponentName("com.xiaomi.eetv.home","com.xiaomi.eetv.home.Launcher")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return;}catch(ActivityNotFoundException|SecurityException ignored){}
        ArrayList<ResolveInfo> candidates=new ArrayList<>();for(ResolveInfo r:getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0))if(r.activityInfo!=null&&!getPackageName().equals(r.activityInfo.packageName)&&r.activityInfo.enabled&&r.activityInfo.exported&&(r.activityInfo.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0&&!r.activityInfo.name.contains("FallbackHome"))candidates.add(r);
        if(candidates.size()==1){launchHome(candidates.get(0));return;}if(candidates.isEmpty()){Ui.message(this,"Оболочка Xiaomi недоступна","Штатный главный экран не найден или отключён.");return;}
        String[] names=new String[candidates.size()];for(int i=0;i<names.length;i++)names[i]=candidates.get(i).loadLabel(getPackageManager()).toString();new Modal.Builder(this).setTitle("Стандартный главный экран").setItems(names,(d,n)->launchHome(candidates.get(n))).setNegativeButton("Назад",null).show();
    }
    private void launchHome(ResolveInfo r){try{startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(new ComponentName(r.activityInfo.packageName,r.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(ActivityNotFoundException|SecurityException e){Ui.message(this,"Главный экран недоступен","Не удалось открыть выбранную оболочку.");}}
    private void network(){
        title("Сеть",InstallerActivity.lanIp()==null?"Нет подключения":"IP-адрес: "+InstallerActivity.lanIp());android.net.ConnectivityManager manager=(android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);android.net.NetworkInfo active=manager.getActiveNetworkInfo();content.addView(Ui.text(this,active!=null&&active.isConnected()?"Подключение активно":"Интернет недоступен",17,active!=null&&active.isConnected()?VladDesign.OK:VladDesign.BAD));
        LinearLayout actions=Ui.col(this);action(actions,"Найти сети Wi-Fi",()->scanWifi());action(actions,"Параметры сети Android",()->openSystem("android.settings.WIFI_SETTINGS"));content.addView(actions);
        content.addView(Ui.text(this,"Для трансляции телефон и телевизор должны находиться в одной домашней сети.",12,Ui.MUTED));
    }
    private void scanWifi(){if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},31);return;}android.net.wifi.WifiManager wifi=(android.net.wifi.WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);try{if(!wifi.isWifiEnabled()){new Modal.Builder(this).setTitle("Включить Wi-Fi?").setPositiveButton("Включить",(d,w)->{wifi.setWifiEnabled(true);show("network");}).setNegativeButton("Назад",null).show();return;}wifi.startScan();List<android.net.wifi.ScanResult> results=wifi.getScanResults();if(results.isEmpty()){Ui.message(this,"Сети Wi-Fi","Список пока пуст. Проверь, что определение местоположения разрешено, и повтори поиск.");return;}Collections.sort(results,(x,y)->Integer.compare(y.level,x.level));Set<String> seen=new HashSet<>();for(android.net.wifi.ScanResult result:results){if(result.SSID.isEmpty()||!seen.add(result.SSID))continue;TextView row=Ui.text(this,result.SSID+"  ·  "+android.net.wifi.WifiManager.calculateSignalLevel(result.level,4)+"/3",14,Ui.INK);row.setPadding(dp(16),dp(13),dp(16),dp(13));Ui.focus(this,row,VladDesign.GLASS);row.setContentDescription(result.SSID);row.setOnClickListener(v->connectWifi(result));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.topMargin=dp(7);content.addView(row,p);}}catch(Exception e){Ui.message(this,"Wi-Fi","Не удалось получить список сетей.");}}
    private void connectWifi(android.net.wifi.ScanResult result){if(result.capabilities.contains("EAP")||result.capabilities.contains("SAE")||result.capabilities.contains("WEP")){Ui.message(this,"Тип сети не поддерживается","Подключись к этой сети через системные настройки телевизора.");return;}Dialog dialog=new Dialog(this);dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);LinearLayout form=Ui.col(this);form.setPadding(dp(25),dp(25),dp(25),dp(25));form.setBackground(VladDesign.shape(this,VladDesign.INK,11));form.addView(Ui.text(this,result.SSID,22,Ui.INK));android.widget.EditText password=new android.widget.EditText(this);password.setTextColor(Ui.INK);password.setTextSize(15);password.setTypeface(VladDesign.body);password.setSingleLine(true);password.setHint("Пароль сети");password.setHintTextColor(Ui.MUTED);password.setInputType(129);password.setBackground(VladDesign.shape(this,VladDesign.GLASS_HI,11));password.setPadding(dp(14),dp(12),dp(14),dp(12));LinearLayout.LayoutParams input=new LinearLayout.LayoutParams(-1,dp(48));input.topMargin=dp(18);form.addView(password,input);LinearLayout row=Ui.col(this);action(row,"Подключить",()->{android.net.wifi.WifiConfiguration cfg=new android.net.wifi.WifiConfiguration();cfg.SSID="\""+result.SSID.replace("\"","\\\"")+"\"";if(result.capabilities.contains("WPA"))cfg.preSharedKey="\""+password.getText().toString().replace("\"","\\\"")+"\"";else cfg.allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.NONE);android.net.wifi.WifiManager wifi=(android.net.wifi.WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);int id=wifi.addNetwork(cfg);if(id<0){Ui.message(this,"Подключение не началось","Проверь пароль или настройки сети.");return;}wifi.enableNetwork(id,true);wifi.reconnect();dialog.dismiss();show("network");});action(row,"Назад",()->dialog.dismiss());form.addView(row);dialog.setContentView(form);dialog.show();dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));dialog.getWindow().setLayout(dp(500),-2);Ui.full(this);}
    private void storage(){StatFs stat=new StatFs(getFilesDir().getPath());title("Хранилище","Свободно "+size(stat.getAvailableBytes())+" из "+size(stat.getTotalBytes()));int percent=(int)((stat.getTotalBytes()-stat.getAvailableBytes())*100/Math.max(1,stat.getTotalBytes()));VladSeekBar bar=new VladSeekBar(this);bar.setMax(100);bar.setProgress(percent);bar.setEnabled(false);content.addView(bar,new LinearLayout.LayoutParams(-1,dp(42)));content.addView(Ui.text(this,"Занято "+percent+"%",15,Ui.INK));LinearLayout actions=Ui.col(this);action(actions,"Очистить кэш VladOS",()->new Modal.Builder(this).setTitle("Очистить кэш?").setMessage("Избранное и аккаунты останутся.").setPositiveButton("Очистить",(d,w)->work.execute(()->{erase(getCacheDir());runOnUiThread(()->show("storage"));})).setNegativeButton("Назад",null).show());action(actions,"Полученные APK",()->startActivity(new Intent(this,InstallerActivity.class).putExtra("inbox",true)));content.addView(actions);}
    private void about(){title("Об устройстве",android.os.Build.MANUFACTURER+" · "+android.os.Build.MODEL);content.addView(Ui.text(this,"VladOS "+version()+"\nAndroid "+android.os.Build.VERSION.RELEASE,17,Ui.INK));LinearLayout actions=Ui.col(this);action(actions,"Быстрый доступ и Happ",()->show("quick"));action(actions,"Приложение для ТВ",()->chooseTv());action(actions,"Открыть Лампу",()->startActivity(new Intent(this,CinemaActivity.class)));content.addView(actions);}
    private void chooseTv(){ArrayList<String> packages=new ArrayList<>(),names=new ArrayList<>();packages.add("__builtin");names.add("Лампа · встроенная");for(ApplicationInfo app:getPackageManager().getInstalledApplications(0))if(!app.packageName.equals(getPackageName())&&launch(app.packageName)!=null){packages.add(app.packageName);names.add(app.loadLabel(getPackageManager()).toString());}new Modal.Builder(this).setTitle("Приложение для ТВ").setItems(names.toArray(new String[0]),(d,which)->{prefs().edit().putString("tv_app_package",packages.get(which)).apply();show("apps");}).setNegativeButton("Назад",null).show();}
    private void action(LinearLayout row,String label,Runnable action){row.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(44));lp.setMargins(0,dp(8),0,dp(5));View button=VladDesign.button(this,label,null,action);button.setId(0x01000000|(label.hashCode()&0x00ffffff));row.addView(button,lp);}
    private void title(String title,String subtitle){TextView t=Ui.text(this,title,22,Ui.INK);t.setPadding(0,0,0,dp(9));content.addView(t);if(!subtitle.isEmpty()){TextView s=Ui.text(this,subtitle,13,Ui.MUTED);s.setPadding(0,0,0,dp(14));content.addView(s);}}
    private void show(String name){if(!screen.equals(name)&&bt!=null)try{bt.cancelDiscovery();}catch(Exception ignored){}screen=name;content.removeAllViews();for(Map.Entry<String,TextView> entry:categoryItems.entrySet()){if(!entry.getValue().hasFocus())entry.getValue().setBackground(VladDesign.shape(this,entry.getKey().equals(name)?VladDesign.GLASS_HI:android.graphics.Color.TRANSPARENT,24));}
        switch(name){case "overview":overview();break;case "system":system();break;case "network":network();break;case "storage":storage();break;case "about":about();break;case "bluetooth":bluetooth();break;case "apps":apps();break;case "picture":picture();break;case "sleep":sleep();break;case "quick":quick();break;default:sound();}
    }
    private void sound(){title("Звук","Громкость и аудиоустройства телевизора");LinearLayout advanced=Ui.col(this);action(advanced,"Выход звука и дополнительные параметры",()->openSystem("android.settings.SOUND_SETTINGS"));content.addView(advanced);
        AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        if(audio.isVolumeFixed()){content.addView(Ui.text(this,"Система сообщает о фиксированной громкости. Используй пульт внешнего ресивера / саундбара.",14,Ui.MUTED));return;}
        final int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);TextView value=Ui.text(this,"",19,Ui.INK);content.addView(value);
        SeekBar bar=new VladSeekBar(this);bar.setMax(max);bar.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));value.setText("Громкость: "+bar.getProgress()+" / "+max);content.addView(bar,new LinearLayout.LayoutParams(-1,dp(50)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}
            public void onProgressChanged(SeekBar s,int n,boolean user){if(user)try{audio.setStreamVolume(AudioManager.STREAM_MUSIC,n,0);value.setText("Громкость: "+audio.getStreamVolume(AudioManager.STREAM_MUSIC)+" / "+max);}catch(Exception e){Ui.message(SettingsActivity.this,"Громкость не изменена",e.getMessage());}}});
        LinearLayout row=new LinearLayout(this);action(row,"Звук: отключить / включить",()->{audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_TOGGLE_MUTE,0);show("sound");});content.addView(row);
        content.addView(Ui.text(this,"Звуковые выходы, видимые Android",17,Ui.INK));
        for(AudioDeviceInfo device:audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS))content.addView(Ui.text(this,device.getProductName()+" · "+audioType(device.getType()),13,Ui.MUTED));
        content.addView(Ui.text(this,"Выбор HDMI ARC/SPDIF и аппаратные эффекты требуют интерфейсов Xiaomi; здесь они пока не переключаются.",12,Ui.MUTED));
    }
    private String audioType(int type){switch(type){case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:return "Bluetooth A2DP";case AudioDeviceInfo.TYPE_HDMI:return "HDMI";case AudioDeviceInfo.TYPE_HDMI_ARC:return "HDMI ARC";case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:return "Динамики";default:return "Выход "+type;}}
    private void bluetooth(){title("Пульты и устройства","Bluetooth-устройства телевизора. Оставь Bluetooth включённым при использовании беспроводного пульта.");LinearLayout pair=Ui.col(this);action(pair,"Системное подключение устройств",()->openSystem("android.settings.BLUETOOTH_SETTINGS"));content.addView(pair);
        if(bt==null){content.addView(Ui.text(this,"Адаптер не найден",16,Ui.MUTED));return;}
        btStatus=Ui.text(this,"",14,Ui.GREEN);content.addView(btStatus);LinearLayout row=new LinearLayout(this);
        action(row,"Bluetooth: включить",()->{try{if(!bt.enable())Ui.message(this,"Bluetooth","Система отклонила включение адаптера.");}catch(Exception e){Ui.message(this,"Bluetooth",e.getMessage());}});
        action(row,"Bluetooth: поиск устройств",()->scan());action(row,"Bluetooth: остановить поиск",()->{bt.cancelDiscovery();renderBluetoothList();});content.addView(row);
        btList=Ui.col(this);content.addView(btList);renderBluetoothList();
    }
    private void scan(){
        if(!bt.isEnabled()){Ui.message(this,"Bluetooth выключен","Сначала включи адаптер.");return;}
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},11);return;}
        try{bt.cancelDiscovery();discovered.clear();boolean ok=bt.startDiscovery();renderBluetoothList();if(!ok)Ui.message(this,"Поиск не запущен","Android отклонил поиск. Возможно, требуется включённое определение местоположения.\n\nРазреши доступ к местоположению в настройках приложения VladOS.");}
        catch(Exception e){Ui.message(this,"Поиск недоступен",e.getMessage());}
    }
    @Override public void onRequestPermissionsResult(int r,String[] permissions,int[] grants){super.onRequestPermissionsResult(r,permissions,grants);if(r==11&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)scan();if(r==31&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED){show("network");scanWifi();}}
    private void renderBluetoothList(){if(btList==null||btStatus==null||!screen.equals("bluetooth"))return;btList.removeAllViews();
        try{btStatus.setText(bt.isEnabled()?(bt.isDiscovering()?"Ищем устройства…":"Bluetooth включён"):"Bluetooth выключен");
            btList.addView(Ui.text(this,"Сопряжённые устройства",17,Ui.INK));Set<BluetoothDevice> paired=bt.getBondedDevices();
            if(paired.isEmpty())btList.addView(Ui.text(this,"Пока нет",13,Ui.MUTED));for(BluetoothDevice device:paired)deviceRow(device,true);
            btList.addView(Ui.text(this,"Найденные устройства",17,Ui.INK));for(BluetoothDevice d:discovered.values())if(!paired.contains(d))deviceRow(d,false);
        }catch(Exception e){btStatus.setText("Нет доступа: "+e.getClass().getSimpleName());}
    }
    private void deviceRow(BluetoothDevice device,boolean paired){String name=device.getName()==null?device.getAddress():device.getName();
        TextView row=Ui.text(this,name+"\n"+device.getAddress()+(paired?" · сопряжено":" · нажми OK для сопряжения"),15,Ui.INK);
        row.setPadding(dp(16),dp(12),dp(16),dp(12));row.setContentDescription(name);Ui.focus(this,row,Ui.CARD);
        row.setOnClickListener(v->{if(paired){Ui.message(this,name,"Устройство сопряжено. Подключение аудиопрофиля и HID-пульта контролирует Android. Принудительное переключение этих профилей через публичный API Android 9 недоступно; статус «сопряжено» не означает «подключено».");return;}
            try{bt.cancelDiscovery();if(!device.createBond())Ui.message(this,"Сопряжение не началось","Проверь режим подключения на устройстве.");else Toast.makeText(this,"Подтверди системный запрос сопряжения, если он появится",Toast.LENGTH_LONG).show();}catch(Exception e){Ui.message(this,"Сопряжение",e.getMessage());}});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));btList.addView(row,lp);
    }
    private void apps(){content.addView(VladDesign.button(this,"Приложение для ТВ","tv",()->chooseTv()),new LinearLayout.LayoutParams(-1,dp(44)));StatFs stat=new StatFs(getFilesDir().getPath());title("Приложения и память","Свободно "+size(stat.getAvailableBytes())+" из "+size(stat.getTotalBytes())+". Системные приложения скрыты от удаления.");
        LinearLayout row=new LinearLayout(this);action(row,"Удалить кэш VladOS",()->new Modal.Builder(this).setTitle("Удалить кэш VladOS?").setMessage("Избранное, настройки и приложения останутся.")
            .setPositiveButton("Очистить",(d,w)->{work.execute(()->{erase(getCacheDir());runOnUiThread(()->{if(!isDestroyed()){Toast.makeText(this,"Кэш VladOS очищен",Toast.LENGTH_SHORT).show();show("apps");}});});}).setNegativeButton("Отмена",null).show());
        action(row,"Удалить полученные APK",()->new Modal.Builder(this).setTitle("Удалить установочные APK?").setMessage("Установленные приложения останутся.").setPositiveButton("Удалить",(d,w)->{work.execute(()->{erase(new File(getFilesDir(),"inbox"));runOnUiThread(()->{if(!isDestroyed())show("apps");});});}).setNegativeButton("Отмена",null).show());content.addView(row);
        List<ApplicationInfo> list=getPackageManager().getInstalledApplications(0);Collections.sort(list,(a,b)->a.loadLabel(getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));
        for(ApplicationInfo app:list){if((app.flags&ApplicationInfo.FLAG_SYSTEM)!=0&&!app.packageName.equals(getPackageName()))continue;
            TextView item=Ui.text(this,app.loadLabel(getPackageManager()).toString(),16,Ui.INK);item.setPadding(dp(15),dp(12),dp(15),dp(12));item.setContentDescription(item.getText());Ui.focus(this,item,Ui.CARD);item.setOnClickListener(v->details(app));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(6));content.addView(item,lp);}
    }
    private void details(ApplicationInfo app){
        String label=app.loadLabel(getPackageManager()).toString();StringBuilder info=new StringBuilder(app.packageName);try{PackageInfo p=getPackageManager().getPackageInfo(app.packageName,PackageManager.GET_PERMISSIONS);
            long bytes=new File(app.sourceDir).length();if(app.splitSourceDirs!=null)for(String path:app.splitSourceDirs)bytes+=new File(path).length();info.append("\nВерсия: ").append(p.versionName).append("\nФайлы APK: ").append(size(bytes));
            if(p.requestedPermissions!=null){info.append("\n\nРазрешения:\n");for(int n=0;n<p.requestedPermissions.length;n++){
                boolean granted=p.requestedPermissionsFlags!=null&&(p.requestedPermissionsFlags[n]&PackageInfo.REQUESTED_PERMISSION_GRANTED)!=0;
                info.append(granted?"✓ ":"— ").append(p.requestedPermissions[n].replace("android.permission.","")).append('\n');}}
        }catch(Exception e){info.append("\n").append(e.getClass().getSimpleName());}
        info.append("\nAndroid не разрешает VladOS менять разрешения и удалять кэш другого приложения без дополнительных прав.");
        Modal.Builder dialog=new Modal.Builder(this).setTitle(label).setMessage(info.toString()).setPositiveButton("Закрыть",null);
        if((app.flags&ApplicationInfo.FLAG_SYSTEM)==0&&!app.packageName.equals(getPackageName()))dialog.setNegativeButton("Удалить приложение",(d,w)->{try{startActivity(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+app.packageName)));}catch(Exception e){Ui.message(this,"Удаление",e.getMessage());}});dialog.show();
    }
    private static void erase(File file){File[] children=file.listFiles();if(children!=null)for(File c:children){if(c.isDirectory())erase(c);c.delete();}}
    private static String size(long bytes){return String.format(Locale.ROOT,bytes>=1073741824?"%.1f ГБ":"%.0f МБ",bytes/(bytes>=1073741824?1073741824d:1048576d));}
    private void picture(){content.addView(VladDesign.button(this,"Таймер сна","bolt",()->show("sleep")),new LinearLayout.LayoutParams(-1,dp(44)));title("Экран","Оформление VladOS и параметры телевизора");LinearLayout panel=Ui.col(this);action(panel,"Настройки экрана телевизора",()->openSystem("android.settings.DISPLAY_SETTINGS"));action(panel,"Анимации VladOS: "+(prefs().getBoolean("motion",true)?"включены":"выключены"),()->{prefs().edit().putBoolean("motion",!prefs().getBoolean("motion",true)).apply();show("picture");});content.addView(panel);
        TextView label=Ui.text(this,"Яркость окна VladOS",17,Ui.INK);content.addView(label);SeekBar brightness=new VladSeekBar(this);brightness.setMax(90);
        float saved=prefs().getFloat("brightness",-1f);brightness.setProgress(saved<0?90:Math.round(saved*100)-10);content.addView(brightness,new LinearLayout.LayoutParams(-1,dp(50)));
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int n,boolean user){if(user){prefs().edit().putFloat("brightness",(n+10)/100f).apply();Ui.full(SettingsActivity.this);label.setText("Яркость окна VladOS: "+(n+10)+"%");}}});
        LinearLayout row=new LinearLayout(this);action(row,"Изображение: системная яркость окна",()->{prefs().edit().putFloat("brightness",-1f).apply();Ui.full(this);show("picture");});content.addView(row);
        content.addView(Ui.text(this,"Яркость интерфейса зависит от поддержки телевизора. Подсветка и цвет находятся в настройках экрана телевизора.",13,Ui.MUTED));
    }
    private void sleep(){long due=prefs().getLong("sleep_at",0);title("Таймер сна",due>System.currentTimeMillis()?"Осталось около "+((due-System.currentTimeMillis())/60000+1)+" мин":"Таймер выключен");
        content.addView(Ui.text(this,"Выключение экрана выполняет служба управления VladOS через системную команду блокировки. Сначала включи её, затем проверь сон на 1 минуту и пробуждение пультом. Полное отключение питания не выполняется.",14,Ui.MUTED));
        content.addView(Ui.text(this,"Служба: "+(RemoteService.live==null?"не подключена":"подключена"),15,Ui.GREEN));
        LinearLayout row=new LinearLayout(this);for(int min:new int[]{1,15,30,60,90}){
            TextView item=Ui.text(this,min+" мин",16,Ui.INK);item.setGravity(Gravity.CENTER);item.setContentDescription("Сон через "+min+" минут");Ui.focus(this,item,Ui.CARD);item.setOnClickListener(v->{if(RemoteService.live==null){serviceHelp();return;}SleepReceiver.set(this,min);show("sleep");});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(90),dp(50));lp.setMargins(0,dp(16),dp(10),0);row.addView(item,lp);}content.addView(row);
        LinearLayout actions=new LinearLayout(this);action(actions,"Таймер сна: отменить",()->{SleepReceiver.cancel(this);show("sleep");});action(actions,"Настройки службы управления",()->serviceHelp());content.addView(actions);
        String last=prefs().getString("sleep_result","");if(!last.isEmpty())content.addView(Ui.text(this,last,13,Ui.MUTED));
    }
    private void serviceHelp(){openSystem("android.settings.ACCESSIBILITY_SETTINGS");} private void unusedServiceHelp(){Ui.message(this,"Служба управления VladOS","Включи «VladOS — кнопка меню» через специальные возможности либо запусти Enable-Remote.ps1 из архива.\n\nВ разделе быстрого доступа можно отключить перехват Menu и оставить службу только для таймера. Содержимое экрана не читается.");}
    private void quick(){title("Быстрый доступ","Выбор приёмника сохраняется. Кнопка трансляции открывает его напрямую.");
        ArrayList<ApplicationInfo> launchable=new ArrayList<>();for(ApplicationInfo app:getPackageManager().getInstalledApplications(0))if(launch(app.packageName)!=null&&!app.packageName.equals(getPackageName()))launchable.add(app);
        Collections.sort(launchable,(a,b)->a.loadLabel(getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));
        LinearLayout row=new LinearLayout(this);action(row,"Трансляция: выбрать приёмник",()->{String[] names=new String[launchable.size()];for(int n=0;n<names.length;n++)names[n]=launchable.get(n).loadLabel(getPackageManager()).toString();new Modal.Builder(this).setTitle("Приложение для трансляции").setItems(names,(d,w)->{prefs().edit().putString("receiver",launchable.get(w).packageName).apply();show("quick");}).show();});
        action(row,"Happ: открыть клиент",()->{Intent i=launch("su.happ.proxyutility");if(i==null)Ui.message(this,"Happ","Клиент не найден");else startActivity(i);});action(row,"Настройки Menu вне VladOS",()->{prefs().edit().putBoolean("global_menu",!prefs().getBoolean("global_menu",true)).apply();show("quick");});content.addView(row);
        content.addView(Ui.text(this,"Приёмник: "+prefs().getString("receiver","автовыбор")+"\nПерехват Menu: "+(prefs().getBoolean("global_menu",true)?"включён, если служба подключена":"выключен"),14,Ui.MUTED));
        content.addView(Ui.text(this,"VPN использует приложение Happ. Сервер, подписка и разрешение на подключение настраиваются в Happ.",13,Ui.MUTED));
    }
    private Intent launch(String pkg){Intent i=getPackageManager().getLeanbackLaunchIntentForPackage(pkg);return i==null?getPackageManager().getLaunchIntentForPackage(pkg):i;}
    @Override protected void onStop(){try{if(bt!=null)bt.cancelDiscovery();}catch(Exception ignored){}super.onStop();}
    @Override protected void onDestroy(){if(registered)unregisterReceiver(events);work.shutdownNow();super.onDestroy();}
}
