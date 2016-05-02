/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Utils;

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
abstract public class SoulReaper extends UntypedActor{
    List<ActorRef> watchedActors = new ArrayList<>(5);
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    
    public abstract void allSoulsReaped();
    
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
                allSoulsReaped();
            }
        }
    }
}
