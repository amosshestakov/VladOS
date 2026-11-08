package ru.ilya.alitahome;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

/** Temporary LAN-only file receiver. No Android dependencies; tested on the JVM. */
public final class UploadServer implements AutoCloseable {
    public interface Listener { void received(File file); }
    private final ServerSocket socket;
    private final File folder;
    private final Listener listener;
    private volatile boolean running=true;
    private int failures=0;
    public final String code=String.format(Locale.US,"%06d",new SecureRandom().nextInt(1000000));
    private final long deadline=System.currentTimeMillis()+600000;
    public UploadServer(String ip,File folder,Listener listener)throws IOException{
        this.folder=folder;this.listener=listener;folder.mkdirs();
        socket=new ServerSocket(0,2,InetAddress.getByName(ip));socket.setSoTimeout(1000);
        Thread t=new Thread(()->serve(),"VladOS upload");t.setDaemon(true);t.start();
    }
    public int port(){return socket.getLocalPort();}
    public boolean active(){return running && System.currentTimeMillis()<deadline;}
    private void serve(){
        while(active()){
            try(Socket client=socket.accept()){
                client.setSoTimeout(30000);handle(client);
            }catch(SocketTimeoutException ignored){}catch(IOException ignored){}
        }
        close();
    }
    private static String line(InputStream in)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();int b;
        while((b=in.read())!=-1 && b!='\n'){
            if(out.size()>=8192)throw new IOException("Header too large");
            if(b!='\r')out.write(b);
        }
        return out.toString("US-ASCII");
    }
    private void reply(Socket s,int status,String type,String body)throws IOException{
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        OutputStream out=s.getOutputStream();
        out.write(("HTTP/1.1 "+status+" Result\r\nContent-Type: "+type+"; charset=utf-8\r\nContent-Length: "+bytes.length+
            "\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);out.flush();
    }
    private void handle(Socket s)throws IOException{
        InputStream in=new BufferedInputStream(s.getInputStream());String request=line(in);
        Map<String,String> headers=new HashMap<>();String h;int total=0;
        while(!(h=line(in)).isEmpty()){
            total+=h.length();if(total>16384)throw new IOException("Headers too large");
            int colon=h.indexOf(':');if(colon>0)headers.put(h.substring(0,colon).toLowerCase(Locale.ROOT),h.substring(colon+1).trim());
        }
        if(request.startsWith("GET / HTTP/")){reply(s,200,"text/html",PAGE);return;}
        if(!request.startsWith("POST /upload HTTP/")){reply(s,404,"text/plain","Not found");return;}
        String received=headers.get("x-vlados-code");
        if(received==null || !MessageDigest.isEqual(code.getBytes(StandardCharsets.US_ASCII),received.getBytes(StandardCharsets.US_ASCII))){
            reply(s,403,"text/plain","Неверный код. Посмотри код на телевизоре.");if(++failures>=8)close();return;
        }
        if(headers.containsKey("transfer-encoding")){reply(s,400,"text/plain","Передача chunked не поддерживается");return;}
        long length;
        try{length=Long.parseLong(headers.get("content-length"));}catch(Exception e){reply(s,411,"text/plain","Нужен размер файла");return;}
        if(length<4 || length>200L*1024*1024){reply(s,413,"text/plain","Размер APK: от 4 байт до 200 МБ");return;}
        File partial=new File(folder,"received_"+System.nanoTime()+".part");File apk=new File(partial.getPath().replace(".part",".apk"));
        try{
            try(OutputStream out=new FileOutputStream(partial)){
                byte[] buf=new byte[32768];long remaining=length;
                while(remaining>0){int n=in.read(buf,0,(int)Math.min(buf.length,remaining));if(n<0)throw new EOFException();out.write(buf,0,n);remaining-=n;}
            }
            try(InputStream check=new FileInputStream(partial)){
                if(check.read()!='P'||check.read()!='K'){reply(s,400,"text/plain","Это не APK/ZIP");return;}
            }
            if(!partial.renameTo(apk))throw new IOException("Cannot save");
            reply(s,200,"text/plain","APK получен. Подтверди установку на телевизоре.");listener.received(apk);
        }finally{partial.delete();}
    }
    @Override public void close(){running=false;try{socket.close();}catch(IOException ignored){}}
    public static final String PAGE="<!doctype html><html lang='ru'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
        +"<title>VladOS — передача APK</title><style>body{font:18px system-ui;background:#eaf3f0;color:#193b38;max-width:480px;margin:8vh auto;padding:24px}input,button{box-sizing:border-box;width:100%;padding:16px;margin:12px 0;border:1px solid #90aaa4;border-radius:14px}button{background:#247a72;color:white;font:inherit}small{line-height:1.6}</style>"
        +"<h1>VladOS</h1><p>Передать приложение на телевизор</p><small>Телефон и ТВ должны быть в одной доверенной сети Wi-Fi. Выбери обычный .apk, до 200 МБ. Это передача файла, а не трансляция экрана.</small>"
        +"<input id='code' inputmode='numeric' maxlength='6' placeholder='Код с экрана телевизора'><input id='file' type='file' accept='.apk'><button id='send'>Передать APK</button><p id='status'></p>"
        +"<script>document.getElementById('send').onclick=async()=>{let b=document.getElementById('send'),s=document.getElementById('status'),f=document.getElementById('file').files[0];if(!f){s.textContent='Выбери APK';return}if(f.size>209715200){s.textContent='Файл больше 200 МБ';return}b.disabled=true;s.textContent='Передача…';try{let r=await fetch('/upload',{method:'POST',headers:{'X-VladOS-Code':document.getElementById('code').value},body:f});s.textContent=await r.text()}catch(e){s.textContent='Соединение прервано. Открой приём файлов заново на ТВ.'}finally{b.disabled=false}}</script></html>";
}
