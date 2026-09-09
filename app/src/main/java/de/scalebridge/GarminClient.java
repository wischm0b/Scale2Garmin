package de.scalebridge;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/** Garmin endpoints adapted from Garth (MIT, Copyright 2023 Matin Tamizi).
 * No passwords are persisted and HTTP errors never contain response bodies or tokens.
 */
public final class GarminClient {
    private static final String API="https://connectapi.garmin.com", SSO="https://sso.garmin.com";
    private static final String MOBILE="https://mobile.integration.garmin.com/gcm/android";
    private final Vault vault;
    private final Context context;
    private final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private String mfaMethod="email";
    public GarminClient(Context c){context=c.getApplicationContext();vault=new Vault(c);}
    public static final class HttpFailure extends IOException {
        public final int status;
        HttpFailure(int s){super(s==429?"Garmin begrenzt Anfragen. Frühestens in 30 Minuten erneut versuchen.":s==401||s==403?"Garmin-Anmeldung abgelaufen oder Zugriff abgelehnt ("+s+").":"Garmin antwortet mit HTTP "+s);status=s;}
    }
    private String request(String method,String url,byte[] body,Map<String,String> headers) throws Exception {
        URL endpoint=new URL(url);
        String host=endpoint.getHost();
        if(!endpoint.getProtocol().equals("https")||!(host.equals("sso.garmin.com")||host.equals("connectapi.garmin.com")||host.equals("thegarth.s3.amazonaws.com"))) throw new IOException("Unzulässiges Verbindungsziel");
        HttpURLConnection c=(HttpURLConnection)endpoint.openConnection();c.setConnectTimeout(20000);c.setReadTimeout(25000);c.setInstanceFollowRedirects(false);c.setRequestMethod(method);
        c.setRequestProperty("User-Agent","com.garmin.android.apps.connectmobile");c.setRequestProperty("Accept","application/json");
        headers.forEach(c::setRequestProperty);
        // Cookie jar is scoped to this login attempt and never shared with other apps.
        cookies.get(endpoint.toURI(),Map.of()).forEach((k,v)->c.setRequestProperty(k,String.join("; ",v)));
        try {
            if(body!=null){c.setDoOutput(true);try(OutputStream out=c.getOutputStream()){out.write(body);}}
            int status=c.getResponseCode();cookies.put(endpoint.toURI(),c.getHeaderFields());
            if(status<200||status>=300) throw new HttpFailure(status);
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>1024*1024)throw new IOException("Antwort zu groß");}return new String(out.toByteArray(),UTF_8);
            }
        } finally {c.disconnect();}
    }
    private String query(){return OAuth.form(Map.of("clientId","GCM_ANDROID_DARK","locale","en-US","service",MOBILE));}
    private JSONObject ssoPost(String path,JSONObject body) throws Exception {
        return new JSONObject(request("POST",SSO+path+"?"+query(),body.toString().getBytes(UTF_8),Map.of("Content-Type","application/json","User-Agent","Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")));
    }
    public boolean login(String email,String password) throws Exception {
        long blocked=context.getSharedPreferences("settings",0).getLong("loginAfter",0);
        if(System.currentTimeMillis()<blocked)throw new IOException("Bitte die Anmeldepause abwarten (mindestens eine Minute, bei HTTP 429 dreißig Minuten).");
        context.getSharedPreferences("settings",0).edit().putLong("loginAfter",System.currentTimeMillis()+60000).apply();
        try {
            try {request("GET",SSO+"/mobile/sso/en/sign-in?clientId=GCM_ANDROID_DARK",null,Map.of());} catch(HttpFailure e){if(e.status<300||e.status>=400)throw e;}
            return handle(ssoPost("/mobile/api/login",new JSONObject().put("username",email).put("password",password).put("rememberMe",false).put("captchaToken","")));
        }catch(HttpFailure e){if(e.status==429)context.getSharedPreferences("settings",0).edit().putLong("loginAfter",System.currentTimeMillis()+1800000).apply();throw e;}
    }
    public boolean verify(String code) throws Exception {
        return handle(ssoPost("/mobile/api/mfa/verifyCode",new JSONObject().put("mfaMethod",mfaMethod).put("mfaVerificationCode",code).put("rememberMyBrowser",false).put("reconsentList",new JSONArray()).put("mfaSetup",false)));
    }
    private boolean handle(JSONObject response) throws Exception {
        String type=response.optJSONObject("responseStatus")==null?"":response.getJSONObject("responseStatus").optString("type");
        if("MFA_REQUIRED".equals(type)){JSONObject info=response.optJSONObject("customerMfaInfo");mfaMethod=info==null?"email":info.optString("mfaLastMethodUsed","email");return false;}
        if(!"SUCCESSFUL".equals(type))throw new IOException("Garmin hat die Anmeldung nicht bestätigt. Zugangsdaten oder Anmeldung im Browser prüfen.");
        completeTicket(response.getString("serviceTicketId"));return true;
    }
    public void completeTicket(String ticket) throws Exception {
        completeTicket(ticket,MOBILE,true);
    }
    public void completeBrowserTicket(String ticket) throws Exception {
        completeTicket(ticket,SSO+"/sso/embed",false);
    }
    private void completeTicket(String ticket,String loginUrl,boolean mobileAudience) throws Exception {
        if(!ticket.startsWith("ST-")||ticket.length()>4096)throw new IOException("Ungültiges Anmeldeticket");
        JSONObject consumer=new JSONObject(request("GET","https://thegarth.s3.amazonaws.com/oauth_consumer.json",null,Map.of()));
        String key=consumer.getString("consumer_key"), secret=consumer.getString("consumer_secret");
        String url=API+"/oauth-service/oauth/preauthorized?"+OAuth.form(Map.of("ticket",ticket,"login-url",loginUrl,"accepts-mfa-tokens","true"));
        Map<String,String> response=OAuth.parse(request("GET",url,null,Map.of("Authorization",OAuth.header("GET",url,Map.of(),key,secret,"",""))));
        if(!response.containsKey("oauth_token")||!response.containsKey("oauth_token_secret"))throw new IOException("Garmin hat keine Sitzung ausgestellt");
        JSONObject session=new JSONObject();session.put("consumer_key",key).put("consumer_secret",secret).put("oauth_token",response.get("oauth_token")).put("oauth_token_secret",response.get("oauth_token_secret")).put("mfa_token",response.getOrDefault("mfa_token",""));
        exchange(session,mobileAudience);vault.save(session);
    }
    private void exchange(JSONObject session,boolean login) throws Exception {
        Map<String,String> body=new HashMap<>();if(login)body.put("audience","GARMIN_CONNECT_MOBILE_ANDROID_DI");
        if(!session.optString("mfa_token").isEmpty())body.put("mfa_token",session.getString("mfa_token"));
        String url=API+"/oauth-service/oauth/exchange/user/2.0";
        JSONObject r=new JSONObject(request("POST",url,OAuth.form(body).getBytes(UTF_8),Map.of("Content-Type","application/x-www-form-urlencoded","Authorization",OAuth.header("POST",url,body,session.getString("consumer_key"),session.getString("consumer_secret"),session.getString("oauth_token"),session.getString("oauth_token_secret")))));
        session.put("access_token",r.getString("access_token")).put("expires_at",System.currentTimeMillis()+r.getLong("expires_in")*1000-60000);
    }
    public void ensureSession() throws Exception {
        JSONObject s=vault.load();if(s==null)throw new IOException("Bitte zuerst bei Garmin anmelden");
        if(System.currentTimeMillis()>=s.getLong("expires_at")){exchange(s,false);vault.save(s);}
    }
    public void upload(Measurement m,boolean includeMuscle) throws Exception {
        JSONObject s=vault.load();if(s==null)throw new IOException("Keine Garmin-Sitzung");
        String boundary="Scale2Garmin"+UUID.randomUUID().toString().replace("-","");
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"weight-"+m.user+"-"+m.scaleSeconds+".fit\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(UTF_8));
        body.write(FitWriter.encode(m,includeMuscle));body.write(("\r\n--"+boundary+"--\r\n").getBytes(UTF_8));
        JSONObject r=new JSONObject(request("POST",API+"/upload-service/upload",body.toByteArray(),Map.of("Authorization","Bearer "+s.getString("access_token"),"Content-Type","multipart/form-data; boundary="+boundary)));
        JSONObject result=r.optJSONObject("detailedImportResult");
        // A 2xx response alone is not evidence that Garmin imported a measurement.
        if(result==null||result.optJSONArray("successes")==null||result.getJSONArray("successes").length()==0)throw new IOException("Garmin hat den Import nicht eindeutig bestätigt. Bitte dort prüfen.");
    }
}
