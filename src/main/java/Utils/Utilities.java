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
import java.nio.ByteBuffer;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;

/**
 *
 * @author nicky
 */
public class Utilities {
    /*compute the ID starting from the file name using a cipher
    using a cipher we can fullfill the following properties:
        - unicity of the generated IDs (if we always use the same key)
        - uniform distribution of the IDs
    */
    static public BigInteger computeId(byte[] inString) {
        String encryptionKey = "a7YgC24PèG._167";
        String iv = "CIDIV_g5W.M+g54J";

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "SunJCE");
            SecretKeySpec key = new SecretKeySpec(encryptionKey.getBytes("UTF-8"), "AES");
            cipher.init(Cipher.ENCRYPT_MODE, key,new IvParameterSpec(iv.getBytes("UTF-8")));
            byte[] rawId = cipher.doFinal(inString);
            return new BigInteger(rawId);
        } catch(Exception e){
            System.err.println("Some bad behavior occurred in Id generation: "+e.getMessage());
            return BigInteger.ZERO;
        }
    }
    static public BigInteger computeId(String inString) {
        // the tag id has to be truncated in 16 bytes
        byte[] mod = new byte[17];
        Arrays.fill(mod,(byte) 0);
        mod[0] = (byte) 1;
        return computeId(inString.getBytes()).mod(new BigInteger(mod));
    }
    
    //compute the ID from from the address
    static public BigInteger computeIdByAddress(String inString) {
        String[] splits = inString.split("@");
        String str1[] = splits[1].split(":");
        String str2[] = str1[0].split("\\.");
        Integer porta = Integer.valueOf(str1[1]);
        byte[] port = ByteBuffer.allocate(4).putInt(porta).array();
        byte IpPort[] = {Integer.valueOf(str2[0]).byteValue(),
            Integer.valueOf(str2[1]).byteValue(),
            Integer.valueOf(str2[2]).byteValue(),
            Integer.valueOf(str2[3]).byteValue(),
            port[2],
            port[3]
        };
        
        return computeId(IpPort);
    }
    
    //returns a string containing the remote address
    static public String getAddress(Address address, int clusterSystemPort) throws UnknownHostException, SocketException{
        return (address.hasLocalScope()) ? address.hostPort()+"@"+AddressResolver.getMyIpAddress()+":"+clusterSystemPort : address.hostPort();
    }
}
