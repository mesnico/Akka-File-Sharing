/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package GUI;

import GUI.messages.MyGUIMessage;
import Startup.AddressResolver;
import Startup.WatchMe;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 *
 * @author nicky
 */
public class GuiActor extends UntypedActor{
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    private static ActorRef guiActorRef;
    private static ActorSelection clusterListenerActorRef;
    private int clusterSystemPort;
    
    public GuiActor(int basePort){
        this.clusterSystemPort = basePort;
    }
    
    @Override 
    public void preStart() throws Exception{
        guiActorRef = getSelf();
        clusterListenerActorRef = getContext().actorSelection("akka.tcp://ClusterSystem@"+AddressResolver.getMyIpAddress()+":"+clusterSystemPort+"/user/clusterListener");
        
        //subscrive to to the soul reaper
        getContext().actorSelection("akka.tcp://ClusterSystem@"+AddressResolver.getMyIpAddress()+":"+clusterSystemPort+"/user/soulReaper")
                .tell(new WatchMe(), getSelf());
    }
    
    @Override
    public void onReceive(Object message) throws Exception{
        if(message instanceof MyGUIMessage){
            System.out.println("Received message from controller; setting textbox message");
            FXMLMainController.addEntry(((MyGUIMessage) message).getMessage());
        }
    }

    public static ActorRef getGuiActorRef() {
        return guiActorRef;
    }

    public static ActorSelection getClusterListenerActorRef() {
        return clusterListenerActorRef;
    }
}
