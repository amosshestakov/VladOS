package ru.ilya.alitahome;

import java.net.URL;

/** Local TorrServe only; remote provider traffic retains its HTTPS policy. */
final class LocalTorrServer {
    static boolean address(URL url) {
        return "http".equals(url.getProtocol()) && "127.0.0.1".equals(url.getHost())
            && url.getPort()==8090 && url.getUserInfo()==null && url.getRef()==null;
    }
    static boolean request(URL url) {
        if(!address(url))return false;
        String path=url.getPath();
        return path.matches("/(echo|settings|torrents|cache|viewed|stat|version|search|torznab/search)/?");
    }
    static boolean stream(String value) {
        try {URL url=new URL(value);return address(url)&&url.getPath().matches("/stream(?:/[^\\\\]*)?");}
        catch(Exception ignored){return false;}
    }
}
