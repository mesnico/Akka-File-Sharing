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
import FileTransfer.messages.AuthorizationReply;
import FileTransfer.messages.EnumFileModifier;
import FileTransfer.messages.FileTransferResult;
import FileTransfer.messages.Handshake;
import FileTransfer.messages.Hello;
import FileTransfer.messages.SendFreeSpaceSpread;
import FileTransfer.messages.SimpleAnswer;
import Startup.AddressResolver;
import Startup.Configuration;
import Startup.WatchMe;
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
import java.util.HashMap;


public class Server extends UntypedActor {
    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    private long myFreeSpace;
    private ActorSelection myClusterListener;
    private final FileTable fileTable;
    private int localClusterSystemPort;
    private int remoteClusterSystemPort;
    private int tcpPort;
    private final String filePath = Configuration.getFilePath();
    private final String tmpFilePath = Configuration.getTmpFilePath();
    private HashMap<String, Integer> addressTable;
    private ActorSelection soulReaper;
    
    public Server(int localClusterSystemPort, int tcpPort) {
        this.localClusterSystemPort = localClusterSystemPort;
        this.tcpPort = tcpPort;
        myFreeSpace = Configuration.getMaxByteSpace();
        fileTable = new FileTable();
        addressTable = new HashMap<>();
    }

    @Override
    public void preStart() throws Exception {
        myClusterListener = getContext().actorSelection("akka.tcp://ClusterSystem@"+AddressResolver.getMyIpAddress()+":"
                +localClusterSystemPort+"/user/clusterListener");
        soulReaper = getContext().actorSelection("akka.tcp://ClusterSystem@"+AddressResolver.getMyIpAddress()+":"+localClusterSystemPort+"/user/soulReaper");
        // TODO: the server, at boot time, has to read from the fileTable stored on disk
        // which file it has, insert them in his fileTable, and calculate his freeSpace.
        // --- The server calculates its free space and tell the clusterListener to spread it into the cluster
        myClusterListener.tell(new SendFreeSpaceSpread(myFreeSpace), getSelf());
        
        //subscrive to to the soul reaper
        soulReaper.tell(new WatchMe(), getSelf());
        
        final ActorRef tcpManager = Tcp.get(getContext().system()).manager();
        tcpManager.tell(TcpMessage.bind(
                    getSelf(), 
                    new InetSocketAddress(InetAddress.getLocalHost(), tcpPort), 
                    100)
                , getSelf());
        
    }
     
    // -------------------------------------------------------- //
    // ---- BOUND RESULT AND CONNECTION TENTATIVE HANDLING ---- //
    // -------------------------------------------------------- //
    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof Bound) {
            myClusterListener.tell(msg, getSelf()); //Are we interested in this? (Bind was successful)
        } else if (msg instanceof Hello) {
            Hello hello = (Hello) msg;
            // --- the addressTable is used for concurrency purposes among different client
            // --- requests
            addressTable.put(hello.getIpAddress(), hello.getPort());
            Hello newHello = new Hello(AddressResolver.getMyIpAddress(), tcpPort);
            getSender().tell(newHello, getSelf());
        } else if (msg instanceof CommandFailed) {
            getContext().stop(getSelf()); //in this case we may bring down the application (bind failed)
        } else if (msg instanceof Connected) {
            Connected conn = (Connected) msg;
            InetSocketAddress remoteAddress = conn.remoteAddress();
            
            Integer lookupPort = addressTable.remove(remoteAddress.getAddress().getHostAddress());
            if(lookupPort == null){
                log.error("Error while looking up the address table for address {}",remoteAddress);
            }
            remoteClusterSystemPort = lookupPort;
            //myClusterListener.tell(conn, getSelf()); //Are we interested in this? (a client connected to us)
            
            
            final ActorRef handler = getContext().actorOf(Props.create(FileTransferActor.class, localClusterSystemPort, 
                    remoteAddress.getAddress(), remoteClusterSystemPort, getSender()));
            getSender().tell(TcpMessage.register(handler), getSelf());
            log.debug("I, the server, have received a connection request and I've accepted it");
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
                log.debug("Received AllocationRequest. Sending out the response: true");
            } else {
                getSender().tell(new SimpleAnswer(false), getSelf());
                log.debug("Received AllocationRequest. Sending out the response: false");
            }
        } 
        else if (msg instanceof Handshake){
            Handshake handshake = (Handshake)msg;
            // --- A FileTransferActor wants to send a file. I have to verify if it exists, is busy,
            // --- or if it's available. In the last case I have to mark it as  busy and
            // --- send back, togheder with the reply, the file's size and tags.
            AuthorizationReply reply = fileTable.testAndSet(handshake.getFileName(), handshake.getModifier());
            log.debug("Received Handshake. Sending out AuthReply: {}",reply);
            getSender().tell(reply, getSelf());
        }
        
        // --- FILE TRANSFER RESULT --- //
        else if (msg instanceof FileTransferResult){
            FileTransferResult fileTransferResult = ((FileTransferResult) msg);
            String fileName = fileTransferResult.getFileName();
            log.debug("FileTransferResult is: {}", fileTransferResult );
        
            switch(fileTransferResult.getMessageType()){             
                case FILE_RECEIVING_FAILED:
                    // --- In this case we have to delete the entry for the file, free the
                    // --- corresponding space and delete the received part of the file  
                    if (fileTransferResult.getFileModifier() == EnumFileModifier.WRITE){
                        log.info("Deleting fileEntry and corresponding corrputed file {}", fileName);
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
                    if (fileTransferResult.getFileModifier() == EnumFileModifier.WRITE){
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
                                log.debug("I've just deleted the sent file {}", fileName);
                            } else {
                                log.warning("File {} deleting failed. File does not exist: {}; File not writable: {}",
                                        fileName, !sentFile.exists(), !sentFile.canWrite());
                            }
                        }
                    }
                    break;
                case FILE_NO_MORE_BUSY:
                    if (fileTransferResult.getFileModifier() == EnumFileModifier.WRITE){
                        fileTable.freeEntry(fileName);
                    }
                    break;
            }
        }
    }    
}





