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
    int totalSize = 0;
    
    public abstract void allSoulsReaped();
    
    public void progress(int completion, int total){}
    
    @Override
    public void onReceive(Object message){
        if(message instanceof WatchMe){
            //keep track of subscribed actors
            if(watchedActors.contains(getSender())){
                //do not add it again... 
                log.debug("actor {} was already present in the soul list. It is ignored", getSender());
                return;
            }
            log.info("I'm watching actor {}",getSender());
            getContext().watch(getSender());
            watchedActors.add(getSender());
            totalSize++;
            progress(0,totalSize);
        } else if(message instanceof Terminated){
            //some actor dead. I remove it from the list.
            log.info("Actor {} is shutted down",getSender());
            watchedActors.remove(getSender());
            progress(totalSize-watchedActors.size(),totalSize);
            if(watchedActors.isEmpty()){
                allSoulsReaped();
            }
        }
    }
}
