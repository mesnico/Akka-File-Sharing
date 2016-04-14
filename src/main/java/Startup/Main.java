package Startup;


import ClusterListenerActor.ClusterListenerActor;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import javafx.application.Application;
import GUI.*;
import java.net.SocketException;
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
    public static void main(String[] args) throws SocketException {
        try{
            if (args.length == 0)
                startup("2551");
            else
                startup(args[0]);
        } catch(UnknownHostException e){
            System.err.println("Error initializing IP address. Do you have one?");
        }
    }

    public static void startup(String strPort) throws UnknownHostException, SocketException{
        int basePort = Integer.parseInt(strPort);
        
        //creates the GUI
        new Thread() {
            @Override
            public void run() {
                System.out.println("Starting GUI Thread");
                Application.launch(GUI.class);
            }
        }.start();
        
        // Override the configuration of the port
        Config clusterConf = ConfigFactory
                .parseString("akka.remote.netty.tcp.port=" + basePort + System.lineSeparator()
                             +"akka.remote.netty.tcp.hostname=" + AddressResolver.getMyIpAddress())
                .withFallback(
                        ConfigFactory.load());
        //Config localConf = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + (basePort+1)).withFallback(
        //                ConfigFactory.load("local.conf"));

        // Create an Akka system
        ActorSystem clusterSystem = ActorSystem.create("ClusterSystem", clusterConf);
        //ActorSystem localSystem = ActorSystem.create("LocalSystem", localConf);
        
        //create the Soul Reaper actor to watch out all the others
        clusterSystem.actorOf(Props.create(SoulReaper.class), "soulReaper");
            
        // Create an actor that handles cluster domain events
        clusterSystem.actorOf(Props.create(ClusterListenerActor.class, basePort),"clusterListener");
        clusterSystem.actorOf(Props.create(GuiActor.class,basePort).withDispatcher("javafx-dispatcher"), "gui");
    }
}
