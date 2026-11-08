package ru.ilya.alitahome;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** One-shot, three-minute LAN input. Payloads authenticated and encrypted; never logged or stored. */
public final class PhoneInputServer implements AutoCloseable {
    public interface Listener {void received(String value);}
    private final ServerSocket socket;
    private final byte[] secret=new byte[32],script;
    private final String origin;
    private final Listener listener;
    private final long deadline=System.currentTimeMillis()+180000;
    private volatile boolean running=true;
    private int failures;
    public PhoneInputServer(String ip,byte[] cryptoScript,Listener callback)throws IOException{
        listener=callback;script=cryptoScript;new SecureRandom().nextBytes(secret);
        socket=new ServerSocket(0,2,InetAddress.getByName(ip));socket.setSoTimeout(1000);origin="http://"+ip+":"+socket.getLocalPort();
        Thread worker=new Thread(()->serve(),"VladOS phone input");worker.setDaemon(true);worker.start();
    }
    public String url(){return origin+"/#"+hex(secret);}
    public boolean active(){return running&&System.currentTimeMillis()<deadline;}
    private static String hex(byte[] value){StringBuilder s=new StringBuilder();for(byte b:value)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    private byte[] key(String purpose)throws GeneralSecurityException{MessageDigest hash=MessageDigest.getInstance("SHA-256");hash.update(secret);return hash.digest(purpose.getBytes(StandardCharsets.US_ASCII));}
    private void serve(){while(active())try(Socket client=socket.accept()){client.setSoTimeout(10000);handle(client);}catch(SocketTimeoutException ignored){}catch(Exception ignored){}close();}
    private String line(InputStream in)throws IOException{ByteArrayOutputStream buffer=new ByteArrayOutputStream();int b;while((b=in.read())!=-1&&b!='\n'){if(buffer.size()>8192)throw new IOException("Header limit");if(b!='\r')buffer.write(b);}return buffer.toString("US-ASCII");}
    private void reply(Socket client,int code,String type,byte[] body)throws IOException{OutputStream out=client.getOutputStream();out.write(("HTTP/1.1 "+code+" Result\r\nContent-Type: "+type+"; charset=utf-8\r\nContent-Length: "+body.length+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'unsafe-inline'; frame-ancestors 'none'\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(body);out.flush();}
    private void text(Socket client,int code,String value)throws IOException{reply(client,code,"text/plain",value.getBytes(StandardCharsets.UTF_8));}
    private void handle(Socket client)throws Exception{
        InputStream in=new BufferedInputStream(client.getInputStream());String request=line(in);Map<String,String> headers=new HashMap<>();String h;int total=0;
        while(!(h=line(in)).isEmpty()){if((total+=h.length())>16384)throw new IOException("Headers limit");int colon=h.indexOf(':');if(colon>0)headers.put(h.substring(0,colon).toLowerCase(Locale.ROOT),h.substring(colon+1).trim());}
        if(!origin.substring(7).equals(headers.get("host"))){text(client,403,"Адрес не совпадает");return;}
        if(request.equals("GET / HTTP/1.1")||request.equals("GET / HTTP/1.0")){reply(client,200,"text/html",PAGE.getBytes(StandardCharsets.UTF_8));return;}
        if(request.startsWith("GET /crypto.js HTTP/")){reply(client,200,"application/javascript",script);return;}
        if(!request.startsWith("POST /input HTTP/")){text(client,404,"Не найдено");return;}
        if(!origin.equals(headers.get("origin"))||headers.containsKey("transfer-encoding")){text(client,403,"Открой форму по QR-коду с телевизора");return;}
        int length;try{length=Integer.parseInt(headers.get("content-length"));}catch(Exception e){text(client,411,"Нет размера");return;}
        if(length<32||length>8192){text(client,413,"Слишком длинный ввод");return;}
        byte[] packet=new byte[length];new DataInputStream(in).readFully(packet);
        byte[] plaintext=null;
        try{
            String[] parts=new String(packet,StandardCharsets.US_ASCII).split("\\.");if(parts.length!=3)throw new GeneralSecurityException();
            byte[] iv=Base64.getDecoder().decode(parts[0]),ciphertext=Base64.getDecoder().decode(parts[1]);if(iv.length!=16)throw new GeneralSecurityException();
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key("mac"),"HmacSHA256"));mac.update(iv);
            if(!MessageDigest.isEqual(hex(mac.doFinal(ciphertext)).getBytes(StandardCharsets.US_ASCII),parts[2].getBytes(StandardCharsets.US_ASCII)))throw new GeneralSecurityException();
            Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key("enc"),"AES"),new IvParameterSpec(iv));plaintext=cipher.doFinal(ciphertext);
            String value=new String(plaintext,StandardCharsets.UTF_8);if(value.length()>2048)throw new GeneralSecurityException();
            text(client,200,"Передано на телевизор. Нажми «Войти в аккаунт» на ТВ.");listener.received(value);close();
        }catch(GeneralSecurityException|IllegalArgumentException e){text(client,403,"Не удалось проверить ввод. Открой новый QR-код на ТВ.");if(++failures>=5)close();}
        finally{Arrays.fill(packet,(byte)0);if(plaintext!=null)Arrays.fill(plaintext,(byte)0);}
    }
    @Override public void close(){running=false;try{socket.close();}catch(IOException ignored){}Arrays.fill(secret,(byte)0);}
    private static final String PAGE="<!doctype html><html lang='ru'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>VladOS — ввод с телефона</title><style>body{font:17px system-ui;background:#080a0d;color:#eaf0f6;max-width:440px;margin:5vh auto;padding:24px}h1{font-size:30px}label{display:block;margin-top:20px}input,button{box-sizing:border-box;width:100%;padding:16px;border:1px solid #34454d;border-radius:14px;margin-top:8px;background:#151a22;color:inherit;font:inherit}button{background:#79dcce;color:#080a0d;margin-top:24px;font-weight:600}small{display:block;color:#93a1b2;line-height:1.5;margin:18px 0}</style><h1>VladOS</h1><p>Ввод для HDRezka на телевизоре</p><small>Только для своей доверенной домашней сети. Страница открывается по HTTP; отправляемые поля шифруются. Не используй в публичном Wi-Fi. ВладOS не отправит форму входа в кинотеатр автоматически.</small><form id='form'><label>Логин или email<input id='login' autocomplete='username' required maxlength='256'></label><label>Пароль<input id='password' type='password' autocomplete='off' required maxlength='512'></label><button id='send'>Передать на ТВ</button></form><p id='status'></p><script src='/crypto.js'></script><script>var master=location.hash.slice(1);history.replaceState(null,'',location.pathname);document.getElementById('form').onsubmit=async function(e){e.preventDefault();var status=document.getElementById('status'),button=document.getElementById('send');if(!/^[a-f0-9]{64}$/.test(master)){status.textContent='Открой QR-код с телевизора заново';return}button.disabled=true;try{var C=CryptoJS,k=C.enc.Hex.parse(master),enc=C.SHA256(k.clone().concat(C.enc.Utf8.parse('enc'))),auth=C.SHA256(k.clone().concat(C.enc.Utf8.parse('mac'))),iv=C.lib.WordArray.random(16),data=JSON.stringify({login:document.getElementById('login').value,password:document.getElementById('password').value}),ct=C.AES.encrypt(data,enc,{iv:iv}).ciphertext,tag=C.HmacSHA256(iv.clone().concat(ct),auth).toString(),r=await fetch('/input',{method:'POST',headers:{'Content-Type':'text/plain'},body:iv.toString(C.enc.Base64)+'.'+ct.toString(C.enc.Base64)+'.'+tag});status.textContent=await r.text();if(r.ok){document.getElementById('password').value='';master='';document.getElementById('form').hidden=true}else button.disabled=false}catch(error){status.textContent='Связь прервана. Открой ввод с телефона заново на ТВ.';button.disabled=false}};</script></html>";
}
