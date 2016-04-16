// ------------ THINGS TO MODIFY ------------ //
// Server port and BACKLOG_SIZE
// 
// See comments below
//
// 
// 
// ------------------------------------------ //

package FileTransfer;

import FileTransfer.messages.AllocationRequest;
import FileTransfer.messages.EnumFileModifier;
import FileTransfer.messages.AuthorizationReply;
import FileTransfer.messages.FileTransferResult;
import FileTransfer.messages.Handshake;
import FileTransfer.messages.SendFreeSpaceSpread;
import Startup.AddressResolver;
import java.net.InetSocketAddress;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.io.Tcp;
import akka.io.Tcp.Bound;
import akka.io.Tcp.CommandFailed;
import akka.io.Tcp.Connected;
import akka.io.TcpMessage;
import java.io.File;
import java.net.InetAddress;


public class Server extends UntypedActor {
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    long myFreeSpace;
    ActorSelection myClusterListener;
    final FileTable fileTable;
    int clusterSystemPort;
    int tcpPort;
    
    public Server(int basePort) {
        clusterSystemPort = basePort;
        tcpPort = basePort + 1;
        // TODO: the server, at boot time, has to read from the fileTable stored on disk
        // which file it has, insert them in his fileTable, and calculate his freeSpace.
        myFreeSpace = 1000000000;
        fileTable = new FileTable();
    }

    @Override
    public void preStart() throws Exception {
        myClusterListener = getContext().actorSelection("akka.tcp://ClusterSystem@"+AddressResolver.getMyIpAddress()+":"
                +clusterSystemPort+"/user/clusterListener");
        
        final ActorRef tcpManager = Tcp.get(getContext().system()).manager();
        tcpManager.tell(TcpMessage.bind(
                    getSelf(), 
                    new InetSocketAddress(InetAddress.getLocalHost(), tcpPort), 
                    100)
                , getSelf());
        //FileElement newElement = new FileElement(false, 1);
        //boolean ret = fileTable.createOrUpdateEntry("inputFile.txt", newElement);
        //System.out.printf("[server]: createOrUpdateEntry restituisce %b\n", ret);
    }
     
    // -------------------------------- //
    // ---- BOUND MESSAGE HANDLING ---- //
    // -------------------------------- //
    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof Bound) {
            myClusterListener.tell(msg, getSelf()); //Are we interested in this? (Bind was successful)
        } else if (msg instanceof CommandFailed) {
            getContext().stop(getSelf()); //in this case we may bring down the application (bind failed)
        } else if (msg instanceof Connected) {
            
            Connected conn = (Connected) msg;
            // From akka documentation it wasn't clear if we should use remoteAddress or localAddress
            InetSocketAddress remoteAddress = conn.remoteAddress();
            myClusterListener.tell(conn, getSelf()); //Are we interested in this? (a client connected to us)
            final ActorRef handler = getContext().actorOf(
                Props.create(FileTransferActor.class, clusterSystemPort, remoteAddress, getSender()));
            getSender().tell(TcpMessage.register(handler), getSelf());
        }
        
        // --------------------------------- //
        // ---- SERVER GENERAL BEHAVIOR ---- //
        // --------------------------------- //
        // --- AUTHORIZATION REQUEST --- //
        else if (msg instanceof AllocationRequest){
            /*  STILL TO DO
            Handshake receivedRequest = (Handshake)msg;
            
            System.out.printf("I've received a %s request on file %s\n",
                    receivedRequest.getModifier(),receivedRequest.getFileName());
            
            AuthorizationReply rensponseToSend = fileTable.testAndSet(
                    receivedRequest.getFileName(), receivedRequest.getModifier());
            getSender().tell(rensponseToSend, getSelf());  
           */
        } 
        
        // --- FILE TRANSFER RESULT --- //
        else if (msg instanceof FileTransferResult){
            FileTransferResult transferResult = ((FileTransferResult) msg);
            String fileName = transferResult.getFileName();
        
            switch(transferResult.getMessageType()){
                case FILE_RECEIVING_FAILED:
                    //qui bisogna cancellare il file col nome dato
                    //e deallocare uno spazio pari alla sua dimensione
                    
                    //FileElement newElement = new FileElement(false, new File(fileName).length());
                    //fileTable.createOrUpdateEntry(fileName, newElement);
                    break;
                case FILE_SENT_SUCCESSFULLY:
                    if (transferResult.getFileModifier() == EnumFileModifier.WRITE){
                        FileElement e = fileTable.deleteEntry(fileName);
                        if(e == null){
                            log.error("File entry for file {} does't exist", fileName);
                        } else {
                            myFreeSpace -= e.getSize();
                            SendFreeSpaceSpread spaceToPublish = new SendFreeSpaceSpread(myFreeSpace);
                            myClusterListener.tell(spaceToPublish, getSelf());
                        }
                    }
                    break;
                case FILE_NO_MORE_BUSY:
                    if (transferResult.getFileModifier() == EnumFileModifier.WRITE){
                        fileTable.freeEntry(fileName);
                    }
                    break;
            }
        }
    }    
}





