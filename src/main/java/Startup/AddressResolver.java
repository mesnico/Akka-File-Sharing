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
        for (NetworkInterface netint : Collections.list(nets)){
            //System.out.println("---------------------------");
            //displayInterfaceInformation(netint);
            //*
            if(netint.getDisplayName() != null //when netint.getDisplayName() is not null
                    && netint.getDisplayName().contains("Hamachi")){//to connect to Hamachi network interface
                String res = Collections.list(netint.getInetAddresses()).get(0).toString();
                res = res.substring(1);
                //System.out.println("invio... "+res);
                return res;
            }
            //*/
        }
        
        return InetAddress.getLocalHost().getHostAddress();
    }/*
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
     }//*/
}
