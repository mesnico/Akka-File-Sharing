/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package GUI;

import ClusterListenerActor.messages.CreationResponse;
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
    private static ActorSelection clusterListenerActorRef,soulReaper;
    private final int clusterSystemPort;
    
    public GuiActor(int basePort){
        this.clusterSystemPort = basePort;
    }
    
    @Override 
    public void preStart() throws Exception{
        guiActorRef = getSelf();
        clusterListenerActorRef = getContext().actorSelection("akka.tcp://ClusterSystem@"+AddressResolver.getMyIpAddress()+":"+clusterSystemPort+"/user/clusterListener");
        soulReaper =              getContext().actorSelection("akka.tcp://ClusterSystem@"+AddressResolver.getMyIpAddress()+":"+clusterSystemPort+"/user/soulReaper");
        
        //subscrive to to the soul reaper
        soulReaper.tell(new WatchMe(), getSelf());
    }
    
    @Override
    public void onReceive(Object message) throws Exception{
        if(message instanceof CreationResponse){
            log.info("GUI received creation response for file: success {}",((CreationResponse) message).isSuccess());
        }
    }

    public static ActorRef getGuiActorRef() {
        return guiActorRef;
    }

    public static ActorSelection getClusterListenerActorRef() {
        return clusterListenerActorRef;
    }
}
