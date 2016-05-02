/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package FileTransfer;

import ClusterListenerActor.messages.LeaveAndClose;
import Utils.AddressResolver;
import Utils.SoulReaper;
import Utils.WatchMe;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.PoisonPill;

/**
 *
 * @author nicky
 */
public class FileTransferSoulReaper extends SoulReaper{
    private ActorSelection server;
    private ActorSelection mainSoulReaper;
    private ActorRef clusterListener;

    public FileTransferSoulReaper(ActorSelection server, ActorSelection mainSoulReaper, ActorRef clusterListener) {
        this.server = server;
        this.clusterListener = clusterListener;
        this.mainSoulReaper = mainSoulReaper;
    }
    
    @Override
    public void preStart(){
        mainSoulReaper.tell(new WatchMe(), getSelf());
    }
    
    @Override
    public void allSoulsReaped(){
        server.tell(PoisonPill.getInstance(), getSender());
        clusterListener.tell(new LeaveAndClose(), getSelf());
    }
}
