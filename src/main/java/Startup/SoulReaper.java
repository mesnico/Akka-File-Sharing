/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Startup;

import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author nicky
 */
public class SoulReaper extends UntypedActor{
    List<ActorRef> watchedActors = new ArrayList<ActorRef>(5);
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    
    @Override
    public void onReceive(Object message){
        if(message instanceof WatchMe){
            //keep track of subscribed actors
            log.info("I'm watching actor {}",getSender());
            getContext().watch(getSender());
            watchedActors.add(getSender());
        } else if(message instanceof Terminated){
            //some actor dead. I remove it from the list.
            log.info("Actor {} is shutted down",getSender());
            watchedActors.remove(getSender());
            if(watchedActors.isEmpty()){
                //terminate the system
                getContext().system().terminate();
            }
        }
    }
}
