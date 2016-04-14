// ------------ THINGS TO MODIFY ------------ //
// Server port and BACKLOG_SIZE
// 
// See comments below
//
// 
// 
// ------------------------------------------ //

package FileTransfer;

import java.net.InetSocketAddress;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.io.Tcp;
import akka.io.Tcp.Bound;
import akka.io.Tcp.CommandFailed;
import akka.io.Tcp.Connected;
import akka.io.TcpMessage;
import java.io.File;
import java.net.InetAddress;


public class Server extends UntypedActor {
    final ActorRef clusterListener;
    final FileTable fileTable;
    
    public Server(ActorRef clusterListener) {
        this.clusterListener = clusterListener;
        fileTable = new FileTable();
    }

    @Override
    public void preStart() throws Exception {
        final ActorRef tcpManager = Tcp.get(getContext().system()).manager();
        tcpManager.tell(TcpMessage.bind(
                    getSelf(), 
                    new InetSocketAddress(InetAddress.getLocalHost(), 5678), 
                    100)
                , getSelf());
        FileElement newElement = new FileElement(false, 1);
        boolean ret = fileTable.createOrUpdateEntry("inputFile.txt", newElement);
        System.out.printf("[server]: createOrUpdateEntry restituisce %b\n", ret);
    }
     
    // -------------------------------- //
    // ---- BOUND MESSAGE HANDLING ---- //
    // -------------------------------- //
    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof Bound) {
            clusterListener.tell(msg, getSelf()); //Are we interested in this? (Bind was successful)
        } else if (msg instanceof CommandFailed) {
            getContext().stop(getSelf()); //in this case we may bring down the application (bind failed)
        } else if (msg instanceof Connected) {
            final Connected conn = (Connected) msg;
            clusterListener.tell(conn, getSelf()); //Are we interested in this? (a client connected to us)
            final ActorRef handler = getContext().actorOf(
                Props.create(SimplisticHandler.class, clusterListener, getSender(), getSelf()));
            getSender().tell(TcpMessage.register(handler), getSelf());
            System.out.printf("[server] io sono %s\n", getSelf());
            System.out.printf("[server] ora credo l'handler %s\n", handler.toString());
        }
        
        // --------------------------------- //
        // ---- SERVER GENERAL BEHAVIOR ---- //
        // --------------------------------- //
        // --- AUTHORIZATION REQUEST --- //
        else if (msg instanceof AuthorizationRequest){
            AuthorizationRequest receivedRequest = (AuthorizationRequest)msg;
            
            System.out.printf("I've received a %s request on file %s\n",
                    receivedRequest.getModifier(),receivedRequest.getFileName());
            
            AuthorizationReply rensponseToSend = fileTable.testAndSet(
                    receivedRequest.getFileName(), receivedRequest.getModifier());
            getSender().tell(rensponseToSend, getSelf());   
        } 
        
        // --- FILE TRANSFER RESULT --- //
        else if (msg instanceof FileTransferResult){
            FileTransferResult transferResult = ((FileTransferResult) msg);
            String fileName = transferResult.getFileName();
        
            switch(transferResult.getMessageType()){
                case FILE_RECEIVED_SUCCESSFULLY:
                    FileElement newElement = new FileElement(false, new File(fileName).length());
                    fileTable.createOrUpdateEntry(fileName, newElement);
                    break;
                case FILE_SENT_SUCCESSFULLY:
                    if (transferResult.getFileModifier() == FileModifier.WRITE){
                        fileTable.deleteEntry(fileName);
                    }
                    break;
                case FILE_NO_MORE_BUSY:
                    if (transferResult.getFileModifier() == FileModifier.WRITE){
                        fileTable.freeEntry(fileName);
                    }
                    break;
            }
        }
    }    
}





