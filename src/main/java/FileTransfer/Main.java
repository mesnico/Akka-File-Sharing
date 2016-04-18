package FileTransfer;

import FileTransfer.messages.EnumBehavior;
import FileTransfer.messages.EnumFileModifier;
import FileTransfer.messages.Handshake;
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
                System.out.println("La mia porta è 7777\n");
                System.out.printf("Il mio ip est %s\n",InetAddress.getLocalHost().toString());
                
                //fileTransferSender is the name through which the Server lookup the sender
                final ActorRef server = system.actorOf(Props.create(Server.class,7777,2220),"server");
                
                Handshake h = new Handshake(EnumBehavior.REQUEST,"inputFile.txt",EnumFileModifier.WRITE);
                InetAddress ia = InetAddress.getLocalHost();
                final ActorRef client = system.actorOf(Props.create(FileTransferActor.class,7777,ia,2221,h),"fileTransferSender");
            }
            else if(port.equals("2551")){
                System.out.println("La mia porta è 2551\n");
                System.out.printf("Il mio ip est %s\n",InetAddress.getLocalHost().toString());
                
                //final ActorRef client = system.actorOf(Props.create(FileTransferActor.class,2551,2220),"fileTransferSender");
                final ActorRef server = system.actorOf(Props.create(Server.class,2551,2221),"server");
            }
    }
}
