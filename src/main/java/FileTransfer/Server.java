// ------------ THINGS TO MODIFY ------------ //
// 
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
import FileTransfer.messages.SimpleAnswer;
import Startup.AddressResolver;
import Startup.Configuration;
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
    final String filePath = Configuration.getFilePath();
    final String tmpFilePath = Configuration.getTmpFilePath();
    
    public Server(int basePort) {
        clusterSystemPort = basePort;
        tcpPort = basePort + 1;
        // TODO: the server, at boot time, has to read from the fileTable stored on disk
        // which file it has, insert them in his fileTable, and calculate his freeSpace.
        myFreeSpace = Configuration.getMaxByteSpace();
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
            AllocationRequest request = (AllocationRequest)msg;
            if (myFreeSpace >= request.getSize()){
                myFreeSpace -= request.getSize();
                FileElement newElement = new FileElement(false, request.getSize(),
                        request.getTags());
                if(fileTable.createOrUpdateEntry(request.getFileName(), newElement)==false){
                    log.error("Someone tried to send me the file {} I already own", request.getFileName());
                }                
                getSender().tell(new SimpleAnswer(true), getSelf());
            } else {
                getSender().tell(new SimpleAnswer(false), getSelf());
            }
        } 
        
        // --- FILE TRANSFER RESULT --- //
        else if (msg instanceof FileTransferResult){
            FileTransferResult transferResult = ((FileTransferResult) msg);
            String fileName = transferResult.getFileName();
        
            switch(transferResult.getMessageType()){
                case FILE_RECEIVING_FAILED:
                    // --- In this case we have to delete the entry for the file, free the --- //
                    // --- corresponding space and delete the received part of the file --- //
                    if (transferResult.getFileModifier() == EnumFileModifier.WRITE){
                        FileElement e = fileTable.deleteEntry(fileName);
                        if (e == null){
                            log.info("The rollBack has no effect on {}", fileName);
                        } else {
                            myFreeSpace += e.getSize();
                        }
                        File corruptedFile = new File(filePath + fileName);
                        if(corruptedFile.exists() && corruptedFile.canWrite()){
                            corruptedFile.delete();
                        }                        
                    }
                    break;
                case FILE_SENT_SUCCESSFULLY:
                    if (transferResult.getFileModifier() == EnumFileModifier.WRITE){
                        FileElement e = fileTable.deleteEntry(fileName);
                        if(e == null){
                            log.error("File entry for file {} does't exist", fileName);
                        } else {
                            myFreeSpace += e.getSize();
                            SendFreeSpaceSpread spaceToPublish = new SendFreeSpaceSpread(myFreeSpace);
                            myClusterListener.tell(spaceToPublish, getSelf());
                            File sentFile = new File(filePath + fileName);
                            if(sentFile.exists() && sentFile.canWrite()){
                                sentFile.delete();
                            }
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





