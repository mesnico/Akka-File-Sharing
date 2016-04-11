package FileTransfer;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.net.InetSocketAddress;
import akka.actor.ActorRef;
import java.net.InetAddress;
import java.net.UnknownHostException;



/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author nicky
 */
public class Main {
    public static void main(String[] args) throws UnknownHostException {
        if (args.length == 0)
            startup(new String[] { "2551", "2552", "0" });
        else
            startup(args);
    }

    public static void startup(String[] args) throws UnknownHostException {
        System.out.println("Sono nella main.startup\n");
            String port = args[0];
            String serverIp = args[1];
        // Override the configuration of the port
            Config config = ConfigFactory.parseString(
                "akka.remote.netty.tcp.port=" + port + System.lineSeparator() 
                        + "akka.remote.netty.tcp.hostname="
                        +InetAddress.getLocalHost().getHostAddress()).withFallback(
                        ConfigFactory.load());

        // Create an Akka system and an actor that handles cluster domain events
            ActorSystem system = ActorSystem.create("ClusterSystem", config);
            final ActorRef clusterListener = system.actorOf(Props.create(ClusterListener.class, port),
                "clusterListener"+port);
            
            if(port.equals("7777")||port.equals("7779")){  
                System.out.printf("Il mio ip est %s\n",InetAddress.getLocalHost().toString());
                InetAddress address = InetAddress.getByName(serverIp);
                InetSocketAddress remote = new InetSocketAddress(address,5678);
                
                final ActorRef tcpClient = system.actorOf(Props.create(Client.class, 
                        remote, clusterListener, "inputFile.txt", TcpBehavior.REQUEST_FILE, FileModifier.WRITE),
                        "tcpClient"+port);
                

            }
            else if(port.equals("2551")){
                System.out.println("La mia porta è 2551\n");
                System.out.printf("Il mio ip est %s\n",InetAddress.getLocalHost().toString());
                //System.out.println("La mia porta è la 2551");    
                final ActorRef tcpServer = system.actorOf(Props.create(Server.class, clusterListener),
                        "tcpServer"+port);
            }
    }
}
