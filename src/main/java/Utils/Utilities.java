/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Utils;

import akka.actor.Address;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 *
 * @author nicky
 */
public class Utilities {
       //compute the member ID starting from the member address using the hash function
    static public BigInteger computeId(String inString) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.reset();
            md.update(inString.getBytes("UTF-8"));
            return new BigInteger(md.digest()).mod(new BigInteger("1000"));
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            return BigInteger.ZERO;
        }
    }
    
    //returns a string containing the remote address
    static public String getAddress(Address address, int clusterSystemPort) throws UnknownHostException, SocketException{
        return (address.hasLocalScope()) ? address.hostPort()+"@"+AddressResolver.getMyIpAddress()+":"+clusterSystemPort : address.hostPort();
    }
}
