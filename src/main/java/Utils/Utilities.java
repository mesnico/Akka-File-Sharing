/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Utils;

import akka.actor.Address;
import java.math.BigInteger;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Random;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 *
 * @author nicky
 */
public class Utilities {
    /*compute the member ID starting from the member address using a cipher
    using a cipher we can fullfill the following properties:
        - unicity of the generated IDs (if we always use the same key)
        - uniform distribution of the IDs
    */
    static public BigInteger computeId(String inString) {
        String encryptionKey = "a7YgC24PèG._167";
        String iv = "CIDIV_g5W.M+g54J";

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "SunJCE");
            SecretKeySpec key = new SecretKeySpec(encryptionKey.getBytes("UTF-8"), "AES");
            cipher.init(Cipher.ENCRYPT_MODE, key,new IvParameterSpec(iv.getBytes("UTF-8")));
            byte[] rawId = cipher.doFinal(inString.getBytes("UTF-8"));
            return new BigInteger(rawId);
        } catch(Exception e){
            System.err.println("Some bad behavior occurred in Id generation: "+e.getMessage());
            return BigInteger.ZERO;
        }
    }
    
    //returns a string containing the remote address
    static public String getAddress(Address address, int clusterSystemPort) throws UnknownHostException, SocketException{
        return (address.hasLocalScope()) ? address.hostPort()+"@"+AddressResolver.getMyIpAddress()+":"+clusterSystemPort : address.hostPort();
    }
}
