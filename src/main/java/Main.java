
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

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
    public static void main(String[] args) {
        if (args.length == 0)
            startup(new String[] { "2551", "2552", "0" });
        else
            startup(args);
    }

    public static void startup(String[] port) {
        // Override the configuration of the port
            Config config = ConfigFactory.parseString(
                "akka.remote.netty.tcp.port=" + port[0]).withFallback(
                ConfigFactory.load());

        // Create an Akka system
            ActorSystem system = ActorSystem.create("ClusterSystem", config);

        // Create an actor that handles cluster domain events
            system.actorOf(Props.create(ClusterListener.class,port[0]),
                "clusterListener");

        
    }
}
