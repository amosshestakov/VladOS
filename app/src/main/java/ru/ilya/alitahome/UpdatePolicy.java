package ru.ilya.alitahome;

import java.net.URI;
import java.security.MessageDigest;

/** Release channel is fixed in the signed app; metadata cannot change it. */
final class UpdatePolicy {
    static final String REPOSITORY="amosshestakov/VladOS";
    static final String API="https://api.github.com/repos/"+REPOSITORY+"/releases/latest";
    static final long LIMIT=200L*1024*1024;
    static boolean releaseAsset(String value){
        try{URI u=new URI(value);return "https".equals(u.getScheme())&&"github.com".equals(u.getHost())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&u.getQuery()==null&&u.getFragment()==null&&u.getPath().startsWith("/"+REPOSITORY+"/releases/download/")&&!u.getPath().contains("/../");}catch(Exception e){return false;}
    }
    static boolean transport(String value){
        try{URI u=new URI(value);String host=u.getHost();return "https".equals(u.getScheme())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&("api.github.com".equals(host)||"github.com".equals(host)||"release-assets.githubusercontent.com".equals(host)||"objects.githubusercontent.com".equals(host));}catch(Exception e){return false;}
    }
    static String hex(byte[] data){StringBuilder result=new StringBuilder();for(byte b:data)result.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return result.toString();}
    static boolean hashMatches(String expected,byte[] actual){return expected!=null&&expected.matches("[a-fA-F0-9]{64}")&&MessageDigest.isEqual(expected.toLowerCase(java.util.Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.US_ASCII),hex(actual).getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
}
