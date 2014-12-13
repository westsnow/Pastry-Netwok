package main.scala;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class SHA1 {
	
	//the sha1 result has 20 bytes, so length <=20
	public static String getHexString(String base, int length) {
	    try{
	        MessageDigest digest = MessageDigest.getInstance("SHA-256");
	        byte[] hashtmp = digest.digest(base.getBytes("UTF-8"));
	        
	        byte[] hash = new byte[length];
	        for(int i = 0; i < length ; ++i)
	        	hash[i] = hashtmp[i];
	        
	        StringBuffer hexString = new StringBuffer();
	        //System.out.println(hash.length);
	        for (int i = 0; i < hash.length; i++) {
	            String hex = Integer.toHexString(0xff & hash[i]);
	            if(hex.length() == 1) hexString.append('0');
	            hexString.append(hex);
	        }
	      
	        return hexString.toString();
	    } catch(Exception ex){
	       throw new RuntimeException(ex);
	    }
	}
	
}
