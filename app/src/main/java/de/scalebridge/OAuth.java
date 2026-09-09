package de.scalebridge;

import java.net.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static java.nio.charset.StandardCharsets.UTF_8;

/** RFC 5849 HMAC-SHA1 signing, independent of HTTP transport. */
public final class OAuth {
    public static String enc(String s) {try{return URLEncoder.encode(s,"UTF-8").replace("+","%20").replace("*","%2A").replace("%7E","~");}catch(java.io.UnsupportedEncodingException e){throw new AssertionError(e);}}
    private static String dec(String s) {try{return URLDecoder.decode(s,"UTF-8");}catch(java.io.UnsupportedEncodingException e){throw new AssertionError(e);}}
    public static Map<String,String> parse(String s) {
        Map<String,String> m=new LinkedHashMap<>();if(s==null||s.isEmpty())return m;
        for(String pair:s.split("&")){String[] p=pair.split("=",2);m.put(dec(p[0]),p.length>1?dec(p[1]):"");}return m;
    }
    public static String form(Map<String,String> m) {
        List<String> p=new ArrayList<>();m.forEach((k,v)->p.add(enc(k)+"="+enc(v)));return String.join("&",p);
    }
    public static String signature(String method,String baseUrl,Map<String,String> params,String consumerSecret,String tokenSecret) throws Exception {
        List<String> encoded=new ArrayList<>();params.forEach((k,v)->encoded.add(enc(k)+"="+enc(v)));Collections.sort(encoded);
        String base=method+"&"+enc(baseUrl)+"&"+enc(String.join("&",encoded));
        Mac mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec((enc(consumerSecret)+"&"+enc(tokenSecret)).getBytes(UTF_8),"HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(base.getBytes(UTF_8)));
    }
    public static String header(String method,String url,Map<String,String> body,String key,String secret,String token,String tokenSecret) throws Exception {
        Map<String,String> auth=new TreeMap<>();auth.put("oauth_consumer_key",key);auth.put("oauth_nonce",UUID.randomUUID().toString());
        auth.put("oauth_signature_method","HMAC-SHA1");auth.put("oauth_timestamp",""+(System.currentTimeMillis()/1000));auth.put("oauth_version","1.0");
        if(!token.isEmpty())auth.put("oauth_token",token);
        URI u=URI.create(url);Map<String,String> all=new HashMap<>(parse(u.getRawQuery()));all.putAll(body);all.putAll(auth);
        auth.put("oauth_signature",signature(method,url.split("\\?",2)[0],all,secret,tokenSecret));
        List<String> h=new ArrayList<>();auth.forEach((k,v)->h.add(enc(k)+"=\""+enc(v)+"\""));return "OAuth "+String.join(", ",h);
    }
}
