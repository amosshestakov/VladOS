package ru.ilya.alitahome;
import java.security.MessageDigest;
public class UpdatePolicyHarness {
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception{
        String base="https://github.com/amosshestakov/VladOS/releases/download/v0.5.4/";
        check(UpdatePolicy.releaseAsset(base+"VladOS.apk"),"official APK");
        for(String bad:new String[]{"http://github.com/amosshestakov/VladOS/releases/download/v1/a.apk","https://github.com.evil.test/amosshestakov/VladOS/releases/download/v1/a.apk","https://github.com/other/VladOS/releases/download/v1/a.apk",base+"../other.apk",base+"%2e%2e/other.apk",base+"a.apk?token=secret",base+"a.apk#x","https://user@github.com/amosshestakov/VladOS/releases/download/v1/a.apk","https://github.com:444/amosshestakov/VladOS/releases/download/v1/a.apk"})check(!UpdatePolicy.releaseAsset(bad),"reject wrong release origin/path");
        check(UpdatePolicy.transport("https://release-assets.githubusercontent.com/github-production-release-asset/1/a?sig=x"),"GitHub CDN redirect");
        check(!UpdatePolicy.transport("https://release-assets.githubusercontent.com.evil.test/a"),"reject fake CDN");
        check(!UpdatePolicy.transport("http://127.0.0.1/update.apk"),"reject local insecure update");
        check(UpdatePolicy.transport(UpdatePolicy.RAW_MANIFEST),"fixed raw metadata channel");
        check(UpdatePolicy.artifact(UpdatePolicy.RAW_APK),"fixed raw APK channel");
        check(!UpdatePolicy.artifact("https://raw.githubusercontent.com/other/VladOS/updates/VladOS.apk"),"reject another repository mirror");
        check(!UpdatePolicy.transport(UpdatePolicy.RAW_APK+"?other=1"),"reject altered mirror URL");
        byte[] digest=MessageDigest.getInstance("SHA-256").digest("verified apk fixture".getBytes("UTF-8"));String expected=UpdatePolicy.hex(digest);
        check(UpdatePolicy.hashMatches(expected.toUpperCase(),digest),"valid hash");
        check(!UpdatePolicy.hashMatches("0000000000000000000000000000000000000000000000000000000000000000",digest),"reject corrupted file");
        check(!UpdatePolicy.hashMatches("invalid",digest),"reject malformed hash");
        System.out.println("PASS: release repository, redirects, credentials, traversal, SHA256 integrity");
    }
}
