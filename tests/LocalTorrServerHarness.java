package ru.ilya.alitahome;
import java.net.URL;
public final class LocalTorrServerHarness {
    static void expect(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        for(String path:new String[]{"","/","/echo","/settings","/torrents","/cache","/viewed","/search/?query=film","/torznab/search/?query=film"})expect(LocalTorrServer.request(new URL("http://127.0.0.1:8090"+path)),path);
        for(String url:new String[]{"http://localhost:8090/settings","http://127.0.0.1:8091/settings","http://127.0.0.1.evil.invalid:8090/settings","http://127.0.0.1:8090/admin","http://user@127.0.0.1:8090/settings","http://192.168.0.1:8090/settings","http://127.0.0.1:8090/settings/../admin"})expect(!LocalTorrServer.request(new URL(url)),url);
        expect(LocalTorrServer.stream("http://127.0.0.1:8090/stream/movie.mkv?link=abc&index=1&play"),"Local stream");
        expect(!LocalTorrServer.stream("http://127.0.0.1:8090/settings"),"Settings cannot be media");
        expect(!LocalTorrServer.stream("http://example.invalid:8090/stream/movie.mkv"),"Remote cleartext media");
        System.out.println("Local TorrServer endpoint and media boundaries passed");
    }
}
