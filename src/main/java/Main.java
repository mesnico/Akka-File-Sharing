
import ClusterListenerActor.ClusterListenerActor;
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
            startup("2551");
        else
            startup(args[0]);
    }

    public static void startup(String strPort) {
        int port = Integer.parseInt(strPort);
        
        // Override the configuration of the port
        Config clusterConf = ConfigFactory.parseString(
                "akka.remote.netty.tcp.port=" + port).withFallback(
                        ConfigFactory.load("cluster.conf"));
        Config clientConf = ConfigFactory.parseString(
                "akka.remote.netty.tcp.port=" + (port+1)).withFallback(
                        ConfigFactory.load("client.conf"));

        // Create an Akka system
        ActorSystem clusterSystem = ActorSystem.create("ClusterSystem", clusterConf);
        //ActorSystem fileManagerSystem = ActorSystem.create("FileManagerSystem", clientConf);

        // Create an actor that handles cluster domain events
        clusterSystem.actorOf(Props.create(ClusterListenerActor.class, port),"clusterListener");
        //fileManagerSystem.actorOf(Props.create(FileManagerActor.class),"fileManager");
    }
}
