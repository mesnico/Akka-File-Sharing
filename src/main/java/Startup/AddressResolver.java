/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Startup;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 *
 * @author nicky
 */
public class AddressResolver {
    static public String getMyIpAddress() throws UnknownHostException{
        return InetAddress.getLocalHost().getHostAddress();
    }
}
