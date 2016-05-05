/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package FileTransfer;

import ClusterListenerActor.messages.LeaveAndClose;
import GUI.messages.ProgressUpdate;
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
    private ActorSelection gui;
    private ActorRef clusterListener;

    public FileTransferSoulReaper(ActorSelection server, ActorSelection gui, ActorRef clusterListener) {
        this.server = server;
        this.gui = gui;
        this.clusterListener = clusterListener;
    }
    
    @Override
    public void allSoulsReaped(){
        server.tell(PoisonPill.getInstance(), getSender());
        clusterListener.tell(new LeaveAndClose(), getSelf());
    }
    
    @Override
    public void progress(int completion, int total){
        gui.tell(new ProgressUpdate(completion, total), getSelf());
    }
}
