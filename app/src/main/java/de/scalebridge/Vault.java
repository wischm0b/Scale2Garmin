package de.scalebridge;

import android.content.Context;
import android.security.keystore.*;
import java.security.KeyStore;
import java.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;
import static java.nio.charset.StandardCharsets.UTF_8;

/** Session tokens encrypted with a non-exportable Android Keystore key. */
public final class Vault {
    private final Context context;
    private static final String ALIAS="scalebridge.session.v1";
    public Vault(Context c) {context=c.getApplicationContext();}
    private SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias(ALIAS)) {
            KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();
        }
        return (SecretKey)ks.getKey(ALIAS,null);
    }
    public synchronized void save(JSONObject session) throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
        String data=Base64.getEncoder().encodeToString(c.getIV())+":"+Base64.getEncoder().encodeToString(c.doFinal(session.toString().getBytes(UTF_8)));
        if(!context.getSharedPreferences("vault",0).edit().putString("session",data).commit()) throw new java.io.IOException("Sitzung konnte nicht gespeichert werden");
    }
    public synchronized JSONObject load() throws Exception {
        String data=context.getSharedPreferences("vault",0).getString("session",null);if(data==null) return null;
        String[] parts=data.split(":");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.getDecoder().decode(parts[0])));
        return new JSONObject(new String(c.doFinal(Base64.getDecoder().decode(parts[1])),UTF_8));
    }
    public boolean exists() {return context.getSharedPreferences("vault",0).contains("session");}
    public void clear() {context.getSharedPreferences("vault",0).edit().clear().commit();}
}
