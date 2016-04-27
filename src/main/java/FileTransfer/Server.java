// ------------ THINGS TO MODIFY ------------ //
// 
// 
// See comments below
//
// 
// 
// ------------------------------------------ //

package FileTransfer;

import ClusterListenerActor.Utilities;
import ClusterListenerActor.messages.FakeFileTransfer;
import ClusterListenerActor.messages.SpreadTags;
import FileTransfer.messages.AllocationRequest;
import FileTransfer.messages.AuthorizationReply;
import FileTransfer.messages.EnumEnding;
import FileTransfer.messages.EnumFileModifier;
import FileTransfer.messages.FileTransferResult;
import FileTransfer.messages.Handshake;
import FileTransfer.messages.Hello;
import FileTransfer.messages.SendFreeSpaceSpread;
import FileTransfer.messages.SimpleAnswer;
import Startup.AddressResolver;
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
import com.typesafe.config.Config;
import java.io.File;
import java.util.HashMap;
import java.util.List;


public class Server extends UntypedActor {
    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    private Config config = getContext().system().settings().config();
    private long myFreeSpace;
    private ActorSelection myClusterListener;
    private ActorSelection myGuiActor;    
    private final FileTable fileTable;
    private int localClusterSystemPort;
    private int remoteClusterSystemPort;
    private int tcpPort;
    private final String filePath;
    private final String tmpFilePath = System.getProperty("java.io.tmpdir");
    private HashMap<String, Integer> addressTable;
    private ActorSelection soulReaper;
    
    public Server() {
        filePath = config.getString("app-settings.file-path");
        myFreeSpace = config.getLong("app-settings.dedicated-space");
        tcpPort = config.getInt("app-settings.server-port");
        localClusterSystemPort = config.getInt("akka.remote.netty.tcp.port");
        System.out.println(filePath+"--"+myFreeSpace+"--"+tcpPort);
        fileTable = new FileTable();
        addressTable = new HashMap<>();
    }
    
    // ----------------------------- //
    // ---- ROLL BACK FUNCTIONS ---- //
    // ----------------------------- //
    private void senderRollBack(FileTransferResult fileTransferResult){
        String fileName = fileTransferResult.getFileName();
        if (fileTransferResult.getFileModifier() == EnumFileModifier.WRITE){
            fileTable.setOccupied(fileName,false);
        }
    }

    private void receiverRollBack(FileTransferResult fileTransferResult){
        // --- We have to delete the entry for the file, free the
        // --- corresponding space and delete the received part of the file  
        String fileName = fileTransferResult.getFileName();
        if (fileTransferResult.getFileModifier() == EnumFileModifier.WRITE){
            log.info("Deleting fileEntry and corresponding corrputed file {}", filePath + fileName);
            FileElement e = fileTable.deleteEntry(fileName);
            if (e == null){
                log.info("The rollBack has no effect on {}", fileName);
            } else {
                myFreeSpace += e.getSize();
            }
            File corruptedFile = new File(filePath + fileName);
            if(corruptedFile.exists() && corruptedFile.canWrite()){
                corruptedFile.delete();
                log.debug("Now the file must be deleted");
            }                        
        }
    }

    @Override
    public void preStart() throws Exception {
        myClusterListener = getContext().actorSelection("akka.tcp://ClusterSystem@"+AddressResolver.getMyIpAddress()+":"
                +localClusterSystemPort+"/user/clusterListener");
        soulReaper = getContext().actorSelection("akka.tcp://ClusterSystem@"+AddressResolver.getMyIpAddress()+":"
                +localClusterSystemPort+"/user/soulReaper");
        myGuiActor = getContext().actorSelection("akka.tcp://ClusterSystem@"+AddressResolver.getMyIpAddress()+":"
                +localClusterSystemPort+"/user/gui"); 
        // TODO: the server, at boot time, has to read from the fileTable stored on disk
        // which file it has, insert them in his fileTable, and calculate his freeSpace.
        // --- The server calculates its free space and tell the clusterListener to spread it into the cluster
        myClusterListener.tell(new SendFreeSpaceSpread(myFreeSpace), getSelf());
        
        // --- Subscrive to to the soul reaper
        soulReaper.tell(new WatchMe(), getSelf());
        
        final ActorRef tcpManager = Tcp.get(getContext().system()).manager();
        tcpManager.tell(TcpMessage.bind(
                    getSelf(), 
                    new InetSocketAddress(AddressResolver.getMyIpAddress(), tcpPort), 
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
            
            
            final ActorRef handler = getContext().actorOf(Props.create(FileTransferActor.class, 
                    remoteAddress.getAddress(), remoteClusterSystemPort, getSender()));
            getSender().tell(TcpMessage.register(handler), getSelf());
            log.debug("I, the server, have received a connection request and I've accepted it");
        }
        
        // ---------------------------- //
        // ---- ALLOCATION REQUEST ---- //
        // ---------------------------- //
        else if (msg instanceof AllocationRequest){
            //log.debug("An allocationRequest arrived, with ");
            AllocationRequest request = (AllocationRequest)msg;
            
            if (request.getSize() == 0){
                boolean occupied = request.isBusy();
                FileElement newElement = new FileElement(occupied, request.getSize(),
                        request.getTags());
                if(fileTable.createOrUpdateEntry(request.getFileName(), newElement)==false){
                    log.info("Someone tried to send me the file {} I already own", request.getFileName());
                }
            } else {
                if (myFreeSpace >= request.getSize()){
                    myFreeSpace -= request.getSize();
                    boolean occupied = request.isBusy();
                    FileElement newElement = new FileElement(occupied, request.getSize(),
                            request.getTags());
                    if(fileTable.createOrUpdateEntry(request.getFileName(), newElement)==false){
                        log.info("Someone tried to send me the file {} I already own", request.getFileName());
                    }                
                    getSender().tell(new SimpleAnswer(true), getSelf());
                    log.debug("Received AllocationRequest. Sending out the response: true");
                } else {
                    getSender().tell(new SimpleAnswer(false), getSelf());
                    log.debug("Received AllocationRequest. Sending out the response: false");
                }
            }
        } 
        
        // --------------------------- //
        // ---- HANDSHAKE MESSAGE ---- //
        // --------------------------- //        
        else if (msg instanceof Handshake){
            // --- A FileTransferActor wants to send a file. I have to verify if it exists, is busy,
            // --- or if it's available. In the last case I have to mark it as  busy and
            // --- send back, togheder with the reply, the file's size and tags.            
            Handshake handshake = (Handshake)msg;
            AuthorizationReply reply = fileTable.testAndSet(handshake.getFileName(), handshake.getModifier());
            log.debug("Received Handshake. Sending out AuthReply: {}",reply);
            getSender().tell(reply, getSelf());
            
        } else if (msg instanceof FakeFileTransfer){
            String fileName = ((FakeFileTransfer) msg).getFileName();
            log.debug("The fake file transfer is received. Sending back the tags for file {}", fileName);
            fileTable.setOccupied(fileName, true);
            // --- TODO: notavo che, riguardo ai membri dato della classe FileElement,
            // --- ognuno di essi ovviamente ha un getter/setter.
            // --- Inoltre, per alcuni di essi, come occupied, abbiamo anche le funzioni per
            // --- lavorarci direttamente da un elemento di tipo FileTable (ad es
            // --- fileTable.setOccupied(fileName)), mentre per altri, come i tag, questa
            // --- scorciatoia non c'e', bisogna fare a mano di prima recuperare l'elemento,
            // --- e poi fare getTags su di esso. Vogliamo uniformare le due cose?
            // --- beh, io intanto l'ho fatta questa scorciatoia, poi vediamo se tenerla o no
            
            // --- IO MI INTERROMPO QUA
            // --- perchè ci sono alcune cose che non mi tornano: (facendo riferimento al caso
            // --- in cui faccio "modify" oppure "read" di un file che gia' possiedo)
            // 1) perchè mi servono già i tags? non ho ancora modificato il file, la dimensione
            // non e' variata, non vedo perche' dovremmo gia' avvisare il cluster. Non basta
            // che questi vengano restituiti dopo che ho fatto la done ed ho ricevuto 
            // la simpleAnswer? L'unico caso in cui puo' aver senso e' se, dopo aver fatto la
            // modify, la dimensione e' 0, caso in cui non arriva la simpleAnswer.
            // 2) cosa si manda come risultato? un FILE_RECEIVED_SUCCESSFULLY, immagino
            // 3) mi sa che nel messaggio SendFileRequest bisogna specificare anche se 
            // volevo leggere o scrivere. Anzi no perche' la lettura e' molto semplice, non 
            // bisogna neanche modificare le fileInfoTable
            // 4) A proposito: Nel caso che io voglia leggere un file che gia' possiedo,
            // lo copiamo nella cartella temporanea o no? a regola si', perche' se e'
            // in lettura non lo blocchiamo, e dobbiamo permettere che, mentre noi lo
            // leggiamo, qualcun altro lo possa prendere in scrittura
            //*** FLUSH DEL MIO CERVELLO EFFETTUATO *** //
            List<String> tags = fileTable.getTags(fileName);
            FileTransferResult fileTransferResult = new FileTransferResult(
                    EnumEnding.FILE_RECEIVED_SUCCESSFULLY, fileName, EnumFileModifier.WRITE, tags);
        }
            
            
            //send back FileTransferResult with the tags
            
            
        // ------------------------------ //
        // ---- FILE TRANSFER RESULT ---- //
        // ------------------------------ //
        else if (msg instanceof FileTransferResult){
            FileTransferResult fileTransferResult = ((FileTransferResult) msg);
            String fileName = fileTransferResult.getFileName(); //TODO: serve?
            log.debug("FileTransferResult is: {}", fileTransferResult );
        
            switch(fileTransferResult.getMessageType()){                                 
                case CONNECTION_FAILED:
                    myGuiActor.tell(msg,getSelf());
                    break;
                    
                case FILE_TO_RECEIVE_BUSY:
                case FILE_TO_RECEIVE_NOT_EXISTS:
                case NOT_ENOUGH_SPACE_FOR_RECEIVING:
                case FILE_RECEIVING_FAILED:
                case IO_ERROR_WHILE_RECEIVING:
                    receiverRollBack(fileTransferResult);
                    myGuiActor.tell(msg,getSelf());
                    break;
                    
                case FILE_RECEIVED_SUCCESSFULLY:
                    if (fileTransferResult.getFileModifier() == EnumFileModifier.WRITE){
                        SendFreeSpaceSpread spaceToPublish = new SendFreeSpaceSpread(myFreeSpace);
                        myClusterListener.tell(spaceToPublish, getSelf());
                        
                        SpreadTags tagsMessage = new SpreadTags(fileName, 
                                fileTable.getFileElement(fileName).getTags(), 
                                Utilities.computeId(Utilities.getAddress(getSelf().path().address(), localClusterSystemPort)));
                        myClusterListener.tell(tagsMessage, getSelf());
                    }
                    myGuiActor.tell(msg,getSelf());
                    break;
                    
                case FILE_TO_SEND_NOT_EXISTS:
                case NOT_ENOUGH_SPACE_FOR_SENDING:
                case IO_ERROR_WHILE_SENDING:
                case FILE_NO_MORE_BUSY:
                    senderRollBack(fileTransferResult);
                    myGuiActor.tell(msg,getSelf());
                    break;
                    
                case FILE_TO_SEND_BUSY:
                case FILE_SENDING_FAILED:
                    myGuiActor.tell(msg,getSelf());
                    break;                    
                    
                case FILE_OPENING_FAILED:
                    File fileToDelete = new File(filePath + fileName);
                    if(fileToDelete.exists() && fileToDelete.canWrite()){
                        fileToDelete.delete();
                        log.debug("I've just deleted the sent file {}", fileName);
                    } else {
                        log.warning("File {} deleting failed. File does not exist: {}; File not writable: {}",
                                fileName, !fileToDelete.exists(), !fileToDelete.canWrite());
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
            }
        }
    }    
}





