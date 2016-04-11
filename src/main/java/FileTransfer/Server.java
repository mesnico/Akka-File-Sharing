package FileTransfer;

import FileTransfer.AuthorizationReply;
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
            clusterListener.tell(msg, getSelf());
        } else if (msg instanceof CommandFailed) {
            getContext().stop(getSelf());
        } else if (msg instanceof Connected) {
            final Connected conn = (Connected) msg;
            clusterListener.tell(conn, getSelf());
            final ActorRef handler = getContext().actorOf(
                Props.create(SimplisticHandler.class, clusterListener, getSender(), getSelf()));
            getSender().tell(TcpMessage.register(handler), getSelf());
        }
        
        // --------------------------------- //
        // ---- SERVER GENERAL BEHAVIOR ---- //
        // --------------------------------- //
        // --- AUTHORIZATION REQUEST --- //
        else if (msg instanceof AuthorizationRequest){
            AuthorizationRequest receivedRequest = (AuthorizationRequest)msg;
            AuthorizationReply rensponseToSend = fileTable.testAndSet(((AuthorizationRequest) msg).getFileName(),
                    ((AuthorizationRequest) msg).getModifier());
            getSender().tell(rensponseToSend, getSelf());   
            
            System.out.printf("Ho ricevuto una richiesta di %s sul file %s.\n",
                    receivedRequest.getModifier(),receivedRequest.getFileName());
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
                    fileTable.freeEntry(fileName);
                    break;
            }
            //mancano "libera pure il file" e "file inviato con successo --> cancella file")
        }
    }    
}





