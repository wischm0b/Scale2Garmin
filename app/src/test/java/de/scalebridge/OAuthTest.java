package de.scalebridge;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class OAuthTest {
    @Test public void matchesPublishedOAuthPhotosExample() throws Exception {
        Map<String,String> p=new HashMap<>();p.put("file","vacation.jpg");p.put("size","original");p.put("oauth_consumer_key","dpf43f3p2l4k3l03");p.put("oauth_token","nnch734d00sl2jdk");p.put("oauth_nonce","kllo9940pd9333jh");p.put("oauth_timestamp","1191242096");p.put("oauth_signature_method","HMAC-SHA1");p.put("oauth_version","1.0");
        assertEquals("tR3+Ty81lMeYAr/Fid0kMTYa/WM=",OAuth.signature("GET","http://photos.example.net/photos",p,"kd94hf93k423kf44","pfkkdhi9sl3r4s00"));
    }
    @Test public void reservedCharactersRoundTrip(){Map<String,String> m=Map.of("token","a+b/c= &ä","empty","");assertEquals(m,OAuth.parse(OAuth.form(m)));assertEquals("%20%2B%2A~",OAuth.enc(" +*~"));}
}
