package Startup;


import Utils.AddressResolver;
import Utils.SoulReaper;
import ClusterListenerActor.ClusterListenerActor;
import FileTransfer.Server;
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
    public static void main(String[] args) throws UnknownHostException, SocketException{
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
                .parseString("akka.remote.netty.tcp.hostname=" + AddressResolver.getMyIpAddress())
                .withFallback(
                        ConfigFactory.load());
        //Config localConf = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + (basePort+1)).withFallback(
        //                ConfigFactory.load("local.conf"));

        // Create an Akka system
        ActorSystem clusterSystem = ActorSystem.create("ClusterSystem", clusterConf);
        //ActorSystem localSystem = ActorSystem.create("LocalSystem", localConf);
        
        //create the Soul Reaper actor to watch out all the others
        clusterSystem.actorOf(Props.create(MainSoulReaper.class), "mainSoulReaper");
        
        //create the fileTransfer server
        clusterSystem.actorOf(Props.create(Server.class), "server");
            
        // Create an actor that handles cluster domain events
        clusterSystem.actorOf(Props.create(ClusterListenerActor.class),"clusterListener");
        clusterSystem.actorOf(Props.create(GuiActor.class).withDispatcher("javafx-dispatcher"), "gui");
    }
}
