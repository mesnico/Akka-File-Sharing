/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package GUI;

import GUI.messages.MyGUIMessage;
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
    public void preStart(){
        guiActorRef = getSelf();
        clusterListenerActorRef = getContext().actorSelection("akka.tcp://ClusterSystem@127.0.0.1:"+clusterSystemPort+"/user/clusterListener");
    }
    
    @Override
    public void onReceive(Object message){
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
