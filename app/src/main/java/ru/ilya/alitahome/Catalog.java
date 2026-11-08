package ru.ilya.alitahome;
import android.content.Context;
import android.graphics.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
public final class Catalog {
    public static final class Show {
        public String title,summary,image,url,genre;
        public JSONObject raw;
        public int id;
        public Show(JSONObject j)throws JSONException{
            raw=j;
            id=j.getInt("id");title=j.getString("name");summary=j.optString("summary","");url=j.optString("url","");
            JSONObject img=j.optJSONObject("image");image=img==null?"":img.optString("medium","");
            JSONArray genres=j.optJSONArray("genres");genre=genres==null?"":genres.join(" · ").replace("\"","");
        }
    }
    private static boolean allowed(JSONObject j){String lang=j.optString("language","English");return lang.equals("English")||lang.equals("Russian");}
    public static List<Show> seed(Context c)throws Exception{
        try(InputStream in=c.getAssets().open("catalog.json")){
            JSONArray data=new JSONArray(read(in,1024*1024));ArrayList<Show> shows=new ArrayList<>();
            for(int i=0;i<data.length();i++)shows.add(new Show(data.getJSONObject(i)));return shows;
        }
    }
    public static List<Show> stored(Context c)throws Exception{
        File file=new File(c.getFilesDir(),"catalog-live.json");
        if(file.isFile())try(InputStream in=new FileInputStream(file)){return parse(new JSONArray(read(in,4*1024*1024)));}
        return seed(c);
    }
    private static List<Show> parse(JSONArray data)throws JSONException{
        ArrayList<Show> list=new ArrayList<>();for(int n=0;n<data.length();n++){JSONObject item=data.getJSONObject(n);if(allowed(item))list.add(new Show(item));};return list;
    }
    public static List<Show> current(Context c)throws Exception{return current(c,false);}
    public static List<Show> current(Context c,boolean force)throws Exception{
        File file=new File(c.getFilesDir(),"catalog-live.json");
        if(!force&&file.isFile()&&System.currentTimeMillis()-file.lastModified()<6*60*60*1000)return stored(c);
        String date=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date());
        HttpURLConnection connection=InstallerActivity.open("https://api.tvmaze.com/schedule/web?date="+date);
        JSONArray feed;try(InputStream in=connection.getInputStream()){feed=new JSONArray(read(in,8*1024*1024));}finally{connection.disconnect();}
        LinkedHashMap<Integer,JSONObject> unique=new LinkedHashMap<>();
        for(int n=0;n<feed.length();n++){
            JSONObject episode=feed.getJSONObject(n);JSONObject embedded=episode.optJSONObject("_embedded");
            JSONObject show=embedded==null?episode.optJSONObject("show"):embedded.optJSONObject("show");
            if(show!=null&&allowed(show)&&show.optJSONObject("image")!=null)unique.put(show.getInt("id"),show);
            if(unique.size()>=16)break;
        }
        if(unique.isEmpty())throw new IOException("На выбранную дату нет афиш с изображениями");
        JSONArray data=new JSONArray();for(JSONObject show:unique.values())data.put(show);
        File temp=new File(c.getFilesDir(),"catalog-live.tmp");try(OutputStream out=new FileOutputStream(temp)){out.write(data.toString().getBytes("UTF-8"));}
        if(!temp.renameTo(file)){temp.delete();throw new IOException("Не удалось сохранить каталог");}
        c.getSharedPreferences("home",0).edit().putString("catalog_date",date).apply();return parse(data);
    }
    public static String read(InputStream in,int limit)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[16384];int n;
        while((n=in.read(buf))!=-1){if(out.size()+n>limit)throw new IOException("Ответ слишком большой");out.write(buf,0,n);}return out.toString("UTF-8");
    }
    public static Show refresh(Show old)throws Exception{
        HttpURLConnection c=InstallerActivity.open("https://api.tvmaze.com/shows/"+old.id);
        try(InputStream in=c.getInputStream()){return new Show(new JSONObject(read(in,1024*1024)));}finally{c.disconnect();}
    }
    public static Bitmap image(Context context,Show show)throws Exception{
        try(InputStream in=context.getAssets().open("posters/"+show.id+".jpg")){return BitmapFactory.decodeStream(in);}
        catch(IOException ignored){}
        if(show.image.isEmpty())return null;
        String name=String.format(Locale.ROOT,"%064x",new java.math.BigInteger(1,MessageDigest.getInstance("SHA-256").digest(show.image.getBytes("UTF-8"))));
        File file=new File(context.getCacheDir(),name+".jpg");
        if(file.isFile())return BitmapFactory.decodeFile(file.getPath());
        HttpURLConnection c;
        try{c=InstallerActivity.open(show.image);}catch(IOException e){
            try(InputStream in=context.getAssets().open("posters/"+show.id+".jpg")){return BitmapFactory.decodeStream(in);}
            catch(IOException ignored){throw e;}
        }
        try(InputStream in=c.getInputStream()){
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[16384];int n;
            while((n=in.read(buf))!=-1){if(out.size()+n>3*1024*1024)throw new IOException("Изображение слишком большое");out.write(buf,0,n);}
            byte[] bytes=out.toByteArray();Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
            if(b!=null){try(OutputStream save=new FileOutputStream(file)){save.write(bytes);}
                File[] cached=context.getCacheDir().listFiles((dir,nm)->nm.endsWith(".jpg"));
                if(cached!=null&&cached.length>96){Arrays.sort(cached,(a,z)->Long.compare(a.lastModified(),z.lastModified()));for(int index=0;index<cached.length-80;index++)cached[index].delete();}
            }return b;
        }finally{c.disconnect();}
    }
}
