/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Startup;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;

/**
 *
 * @author nicky
 */
public class AddressResolver {
    static public String getMyIpAddress() throws UnknownHostException, SocketException {
        Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
        //skim all the network interfaces looking for the one I'm interest
        for (NetworkInterface netint : Collections.list(nets)){
            //displayInterfaceInformation(netint);
            //*
            if(netint.getDisplayName() != null //when netint.getDisplayName() is not null
                    && netint.getDisplayName().contains("Hamachi")){//to connect to "Hamachi" VPN network interface
                //I'm interested only in the first address
                String res = Collections.list(netint.getInetAddresses()).get(0).toString();
                //the address is in the format "/x.y.w.z" and I want "x.y.w.z"
                res = res.substring(1);
                //System.out.println("invio... "+res);
                return res;
            }
            //*/
        }
        //else return "127.0.0.1"
        return InetAddress.getLocalHost().getHostAddress();
    }
    
    // --- Show interface's details: display name, name, and the list of inetAddress --- //
    static void displayInterfaceInformation(NetworkInterface netint) throws SocketException {
        System.out.printf("Display name(%b): %s\n", netint.getDisplayName()==null, netint.getDisplayName());
        System.out.printf("Name: %s\n", netint.getName());
        Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
        int i = 0;
        for (InetAddress inetAddress : Collections.list(inetAddresses)) {
            System.out.printf("%d) InetAddress: %s\n", i, inetAddress);
            i++;
        }
        System.out.printf("\n");
     }
}
