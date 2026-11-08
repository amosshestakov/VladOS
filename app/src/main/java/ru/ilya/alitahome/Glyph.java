package ru.ilya.alitahome;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Original line icons drawn in a consistent 24-unit coordinate system. */
public final class Glyph extends Drawable {
    private final String type;private final int color;
    public Glyph(String type,int color){this.type=type;this.color=color;}
    @Override public void draw(Canvas raw){Rect b=getBounds();raw.save();raw.translate(b.left,b.top);raw.scale(b.width()/24f,b.height()/24f);
        Paint p=new Paint(3);p.setColor(color);p.setStrokeWidth(1.7f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        switch(type){
        case "menu":line(raw,p,4,6,20,6);line(raw,p,4,12,20,12);line(raw,p,4,18,20,18);break;
        case "home":path(raw,p,3,11,12,3,21,11);path(raw,p,5,10,5,21,10,21,10,15,14,15,14,21,19,21,19,10);break;
        case "apps":for(int x:new int[]{4,14})for(int y:new int[]{4,14})raw.drawRoundRect(x,y,x+6,y+6,1,1,p);break;
        case "film":raw.drawRoundRect(3,4,21,20,2,2,p);line(raw,p,7,4,7,20);line(raw,p,17,4,17,20);for(int y:new int[]{8,12,16}){line(raw,p,3,y,7,y);line(raw,p,17,y,21,y);}break;
        case "cast":path(raw,p,3,9,3,4,21,4,21,19,16,19);raw.drawArc(-6,9,15,30,-90,90,false,p);raw.drawArc(-3,13,10,26,-90,90,false,p);raw.drawCircle(3,21,1,p);break;
        case "install":path(raw,p,12,3,12,15,7,10);line(raw,p,12,15,17,10);path(raw,p,4,15,4,21,20,21,20,15);break;
        case "vpn":path(raw,p,12,2,20,6,19,15,12,22,5,15,4,6,12,2);path(raw,p,8,12,11,15,16,9);break;
        case "settings":raw.drawCircle(12,12,4,p);for(int a=0;a<8;a++){double n=a*Math.PI/4;line(raw,p,(float)(12+7*Math.cos(n)),(float)(12+7*Math.sin(n)),(float)(12+10*Math.cos(n)),(float)(12+10*Math.sin(n)));}raw.drawCircle(12,12,8,p);break;
        case "bluetooth":path(raw,p,12,2,18,8,6,18);path(raw,p,12,22,18,16,6,6);line(raw,p,12,2,12,22);break;
        case "sound":path(raw,p,3,9,7,9,12,5,12,19,7,15,3,15,3,9);raw.drawArc(10,5,22,19,-60,120,false,p);break;
        case "screen":raw.drawRoundRect(2,3,22,18,2,2,p);line(raw,p,8,22,16,22);line(raw,p,12,18,12,22);break;
        case "sleep":raw.drawArc(5,3,23,21,25,265,false,p);path(raw,p,10,3,9,10,15,15,22,14);break;
        case "star":path(raw,p,12,2,15,9,22,9,17,14,19,22,12,18,5,22,7,14,2,9,9,9,12,2);break;
        case "search":raw.drawCircle(10,10,7,p);line(raw,p,15,15,22,22);break;
        case "refresh":raw.drawArc(4,4,20,20,35,290,false,p);path(raw,p,20,3,20,9,14,9);break;
        case "trash":path(raw,p,4,6,20,6);path(raw,p,7,6,8,22,16,22,17,6);path(raw,p,9,6,9,2,15,2,15,6);break;
        case "power":raw.drawArc(3,3,21,21,-50,280,false,p);line(raw,p,12,1,12,12);break;
        case "phone":raw.drawRoundRect(6,1,18,23,3,3,p);line(raw,p,10,19,14,19);break;
        case "back":path(raw,p,14,4,6,12,14,20);line(raw,p,6,12,22,12);break;
        case "play":path(raw,p,7,3,21,12,7,21,7,3);break;
        case "close":line(raw,p,5,5,19,19);line(raw,p,19,5,5,19);break;
        default:raw.drawCircle(12,12,9,p);line(raw,p,12,10,12,17);raw.drawCircle(12,6,0.5f,p);
        }raw.restore();}
    private void line(Canvas c,Paint p,float x,float y,float a,float b){c.drawLine(x,y,a,b,p);}
    private void path(Canvas c,Paint p,float...v){Path path=new Path();path.moveTo(v[0],v[1]);for(int i=2;i<v.length;i+=2)path.lineTo(v[i],v[i+1]);c.drawPath(path,p);}
    @Override public void setAlpha(int alpha){}@Override public void setColorFilter(ColorFilter filter){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    public static String infer(String label){String s=label.toLowerCase(java.util.Locale.ROOT);
        if(s.contains("флеш")||s.contains("файл"))return "usb";if(s.contains("сеть")||s.contains("wi-fi"))return "wifi";if(s.contains("rustore")||s.contains("магазин"))return "store";if(s.contains("меню"))return "menu";if(s.equals("понятно")||s.equals("готово"))return "check";if(s.contains("отмена"))return "close";if(s.contains("назад")||s.contains("вернут"))return "back";if(s.contains("закрыть"))return "close";
        if(s.contains("happ")||s.contains("vpn"))return "vpn";if(s.contains("bluetooth"))return "bluetooth";
        if(s.contains("трансляц")||s.contains("приёмник"))return "cast";if(s.contains("телефон"))return "phone";
        if(s.contains("удал"))return "trash";if(s.contains("обнов"))return "refresh";
        if(s.contains("установ")||s.contains("apk")||s.contains("rustore"))return "install";
        if(s.contains("звук")||s.contains("громк"))return "sound";if(s.contains("изображ")||s.contains("экран"))return "screen";
        if(s.contains("сон")||s.contains("таймер"))return "sleep";if(s.contains("настрой"))return "settings";
        if(s.contains("приложен"))return "apps";if(s.contains("сериал")||s.contains("афиш")||s.contains("кино"))return "film";
        if(s.contains("смотр")||s.contains("видеосервис"))return "play";if(s.contains("главн"))return "home";
        return "info";}
}
