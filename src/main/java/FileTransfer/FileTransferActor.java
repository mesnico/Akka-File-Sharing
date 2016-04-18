/******************************** 
 * 5) guarare i vari TODO/check/Err sparsi nel testo
 ******************************/

package FileTransfer;

import FileTransfer.messages.Ack;
import FileTransfer.messages.AllocationRequest;
import FileTransfer.messages.AuthorizationReply;
import FileTransfer.messages.EnumAuthorizationReply;
import FileTransfer.messages.EnumBehavior;
import FileTransfer.messages.FileTransferResult;
import FileTransfer.messages.Handshake;
import FileTransfer.messages.EnumEnding;
import FileTransfer.messages.EnumFileModifier;
import FileTransfer.messages.SimpleAnswer;
import Startup.AddressResolver;
import Startup.Configuration;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.PoisonPill;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.io.Tcp;
import akka.io.Tcp.CommandFailed;
import akka.io.Tcp.Connected;
import akka.io.Tcp.ConnectionClosed;
import akka.io.Tcp.Received;
import akka.io.TcpMessage;
import akka.japi.Procedure;
import java.io.File;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import static java.util.concurrent.TimeUnit.SECONDS;
import scala.concurrent.Await;
import scala.concurrent.duration.FiniteDuration;

public class FileTransferActor extends UntypedActor {
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    ActorSelection myClusterListener;
    ActorSelection myServer;
    ActorSelection remoteServer;
    ActorSelection myGuiActor;
    ActorRef interlocutor;
    ActorRef connectionHandler;
    Handshake handshake;
    int clusterSystemPort;
    int tcpPort;
    String remoteServerIP;  
    long size;
    InetSocketAddress interlocutorAddress;
    FileOutputStream output;
    final String filePath = Configuration.getFilePath();
    final String tmpFilePath = Configuration.getTmpFilePath();
    
    // ---------------------- //
    // ---- CONSTRUCTORS ---- //
    // ---------------------- //
    public FileTransferActor(int basePort) {
        this.clusterSystemPort = basePort;
        this.tcpPort = basePort + 1;
        handshake.setBehavior(EnumBehavior.UNINITIALIZED);
        size = 0;
        //qui forse sarebbe da inizializzare behavior a "vuoto", in modo che
        //il passivo, se va nella terminated quando non ha ancora ricevuto il filename, non fa danni
    }
    
    // --- Constructor called by the asker --- //
    public FileTransferActor(int basePort, Handshake handshake) {
        this(basePort);
        this.handshake = handshake;
    }
    
    // --- Constructor called by the responder --- //
    public FileTransferActor(int basePort, InetSocketAddress interlocutorAddress, ActorRef connectionHandler ) {
        this(basePort);
        this.interlocutorAddress = interlocutorAddress;
        this.connectionHandler = connectionHandler;
    }    
    
    // ----------------------------- //
    // ---- ADDITIONAL FUNCTION ---- //
    // ----------------------------- //
    public void changeBehavior(){
        if(handshake.getBehavior() == EnumBehavior.SEND){
            // --- I enter in tcpSender behavior and tell myself to start the protocol --- //
            getContext().become(tcpSender());  
            getSelf().tell("go", getSelf()); 
        } else {
            getContext().become(tcpReceiver()); 
        }
    }
    
    // ---------------------------- //
    // ---- INITIAL HANDSHAKES ---- //
    // ---------------------------- //
    @Override
    public void preStart() throws Exception {
        myServer = getContext().actorSelection("akka.tcp://ClusterSystem@"+AddressResolver.getMyIpAddress()+":"
                +clusterSystemPort+"/user/server");
        // TODO: check if this reference is needed by both behavior
        myClusterListener = getContext().actorSelection("akka.tcp://ClusterSystem@"+AddressResolver.getMyIpAddress()+":"
                +clusterSystemPort+"/user/clusterListener");
        myGuiActor = getContext().actorSelection("akka.tcp://ClusterSystem@"+AddressResolver.getMyIpAddress()+":"
                +clusterSystemPort+"/user/gui");   
        
        switch(handshake.getBehavior()){
            case SEND:
            case REQUEST:
                remoteServer = getContext().actorSelection("akka.tcp://ClusterSystem@"+remoteServerIP+":"
                +clusterSystemPort+"/user/server");
                final ActorRef tcpManager = Tcp.get(getContext().system()).manager();
                InetSocketAddress remoteServerAddress = new InetSocketAddress(remoteServerIP, tcpPort);
                tcpManager.tell(TcpMessage.connect(remoteServerAddress), getSelf());
                break;
            case UNINITIALIZED:
                ActorSelection interlocutorSelection = getContext().actorSelection("akka.tcp://ClusterSystem@"+interlocutorAddress
                        .getAddress().getHostAddress()+":"+clusterSystemPort+"/user/fileTransferSender"); 
                FiniteDuration timeout = new FiniteDuration(10, SECONDS);
                interlocutor = Await.result(interlocutorSelection.resolveOne(timeout), timeout); //Checl
                interlocutor.tell("ack", getSelf());
                log.debug("interlocutor actorRef is {}", interlocutor);
                break;     
        }
    }
    
    @Override
    public void onReceive(Object msg) {
        if (msg instanceof CommandFailed) {
            FileTransferResult result = new FileTransferResult(
                    EnumEnding.FILE_SENDING_FAILED, handshake.getFileName(), handshake.getModifier()); 
            myGuiActor.tell(result, getSelf()); //toCheck
            getSelf().tell(PoisonPill.getInstance(), getSelf());
        } else if (msg instanceof Connected) {
            connectionHandler = getSender();
            connectionHandler.tell(TcpMessage.register(getSelf()), getSelf());
        } else if (msg instanceof Ack){
            interlocutor = getSender();
            interlocutor.tell(handshake, getSelf());
            getContext().watch(interlocutor);
            changeBehavior();
        } else if (msg instanceof Handshake){
            this.handshake = (Handshake)msg;
            getContext().watch(interlocutor);
            changeBehavior();
        }
    }
    
    // ------------------------- //
    // ---- SENDER BEHAVIOR ---- //
    // ------------------------- //    
    private Procedure<Object> tcpSender() {
        return new Procedure<Object>() {
            private FileTransferResult result;
            
            public void rollBack(){
                //the file was marked as busy, it has to be set free
                result = new FileTransferResult(
                        EnumEnding.FILE_NO_MORE_BUSY, handshake.getFileName());  
                myServer.tell(result, getSelf());
            }
            
            public void terminate(EnumEnding msg){
                // --- I send the same error message to the interlocutor and --- //
                // --- to the gui --- //
                result = new FileTransferResult(
                        msg, handshake.getFileName(), handshake.getModifier());
                myGuiActor.tell(result, getSelf());
                /**********************************
                 *  I enter here if 
                 *  1) the interlocutor fall --> file_sending_failed (msg should not be sent to interlocutor)
                 *  2) there is a file_not_exists error --> file_not_exists (msg should be sent to interlocutor)
                 *  3) the received didn't give us the permission to send him a file --> file_sending_failer (msg should not be sent to interlocutor)
                 **********************************/
                
                //interlocutor.tell(result, getSelf());
                rollBack();
                getSelf().tell(PoisonPill.getInstance(), getSelf());
            }
            
            @Override
            public void apply(Object msg) throws Exception {
                if(msg instanceof String){
                    //The "go" message is arrived, telling me to start the protocol
                    myServer.tell(handshake, getSelf());    
                } else if (msg instanceof AuthorizationReply){
                    AuthorizationReply reply = (AuthorizationReply)msg;
                    switch(reply.getResponse()){
                        case FILE_NOT_EXISTS:
                            interlocutor.tell(reply, getSelf());
                            terminate(EnumEnding.FILE_NOT_EXISTS);
                            break;
                            //vedere se la busy si può uniformare con quella di sopra
                        case FILE_BUSY:
                            interlocutor.tell(reply, getSelf());
                            terminate(EnumEnding.FILE_SENDING_FAILED);
                            break;
                        case AUTHORIZATION_GRANTED:
                            File fileToSend = new File(filePath + handshake.getFileName());
                            size = reply.getSize();
                            if (fileToSend.exists()&&fileToSend.canRead()){
                                interlocutor.tell(reply, getSelf());
                            } else {
                                // --- The file does exists but we couldn't open it.
                                // --- So we cannot forward the AUTHORIZATION_GRANTED message received
                                // --- from the server but we have to override it with a
                                // --- a FILE_NOT_EXISTS
                                reply = new AuthorizationReply(EnumAuthorizationReply.FILE_NOT_EXISTS);
                                interlocutor.tell(reply, getSelf());
                                terminate(EnumEnding.FILE_NOT_EXISTS);
                            }       
                            if (handshake.getModifier() == EnumFileModifier.READ){
                                // --- In READ mode, we will not receive any answer from server NOT TRUE --- //
                                //SimpleAnswer goAhead = new SimpleAnswer(true);
                                //getSelf().tell(goAhead, getSelf());
                            }
                            break;
                    } 
                } else if (msg instanceof SimpleAnswer){
                    SimpleAnswer answer = (SimpleAnswer)msg;
                    if (answer.getAnswer() == true){
                        Tcp.Event ack_or_notAck = new Tcp.NoAck$();
                        connectionHandler.tell(TcpMessage.writeFile(filePath + handshake.getFileName(), 
                                0, size, ack_or_notAck), getSelf());
                        connectionHandler.tell(TcpMessage.close(), getSelf());      
                    } else {
                        // --- the receiver doesn't have enought free space to store the file --- //
                        terminate(EnumEnding.FILE_SENDING_FAILED);
                                             
                    }
                } else if (msg instanceof Terminated){
                    terminate(EnumEnding.FILE_SENDING_FAILED);
                } else if(msg instanceof CommandFailed) {
                    // OS kernel socket buffer was full
                    // Here we should probably bring down the whole Tcp connection
                } else if (msg instanceof ConnectionClosed){
                    ConnectionClosed connection = (ConnectionClosed)msg;
                    if(connection.isErrorClosed()){
                        terminate(EnumEnding.FILE_SENDING_FAILED);
                    } else {
                        result = new FileTransferResult(
                            EnumEnding.FILE_SENT_SUCCESSFULLY, handshake.getFileName(), handshake.getModifier());
                        myServer.tell(result, getSelf());
                        getSelf().tell(PoisonPill.getInstance(), getSelf());
                    }
                }
            }
        };
    }
    
    // --------------------------- //
    // ---- RECEIVER BEHAVIOR ---- //
    // --------------------------- //        
    private Procedure<Object> tcpReceiver() {
        return new Procedure<Object>() {
            private FileTransferResult result;
            
            public void rollBack(){
                // --- We ask the server to deallocate fileEntry and corresponding space --- //
                // --- Furthermore, we must delete the corrupted file --- //
                result = new FileTransferResult(
                        EnumEnding.FILE_RECEIVING_FAILED, handshake.getFileName());  
                myServer.tell(result, getSelf());
            }
            
            public void terminate(EnumEnding msg){
                result = new FileTransferResult(msg, handshake.getFileName(), handshake.getModifier());
                myGuiActor.tell(result, getSelf());
                /**********************************
                 *  I enter here if 
                 *  1) we are receiving the file, and there is an IO error
                 **********************************/
                interlocutor.tell(result, getSelf()); //toCheck
                rollBack();
                getSelf().tell(PoisonPill.getInstance(), getSelf());
            }
            
            @Override
            public void apply(Object msg) throws Exception {
                if (msg instanceof AuthorizationReply){
                    AuthorizationReply reply = (AuthorizationReply)msg;
                    switch(reply.getResponse()){
                        case FILE_NOT_EXISTS:
                            terminate(EnumEnding.FILE_NOT_EXISTS);
                            break;
                        case FILE_BUSY:
                            terminate(EnumEnding.FILE_BUSY);
                            break;
                        case AUTHORIZATION_GRANTED:
                            // --- if the request is a READ, the server is not required to store         --- //
                            // --- the file in the fileTable, so there is no need to ask him permission. --- //
                            // --- Of course, we must create outself a reply for the interlocutor        --- //
                            if(handshake.getModifier() == EnumFileModifier.WRITE){
                                size = reply.getSize();
                                AllocationRequest request = new AllocationRequest(
                                        handshake.getFileName(), size, reply.getTags());
                                myServer.tell(request, getSelf()); 
                                if (size == 0){
                                    // --- special case: create the file and end the protocol --- //
                                    // --- in the close() handling the guiActor               --- //
                                    // --- will be informed of the success                    --- //
                                    File newFile = new File(filePath + handshake.getFileName() + "Out");
                                    if (!newFile.exists()){
                                        newFile.createNewFile();
                                        log.info("New file {} has been created", handshake.getFileName());
                                    } else {
                                        log.error("This should not happen: File {} already exists", handshake.getFileName());
                                        //si cancella e si ricrea
                                    }
                                    connectionHandler.tell(TcpMessage.close(), getSender()); //magari dovremmo gesrtire il fatto che, se la dimensione era 0, non aggiorniamo lo spazio nella fileTable
                                }
                            } else {  
                                // --- READ request: we will give the permission without asking the server --- //
                                if (size != 0){
                                    // --- we have to create the output stream since from here --- //
                                    // --- we will jump directly in the receiving stage        --- //
                                    output = new FileOutputStream(tmpFilePath + handshake.getFileName()); //TODO: non dovrebbe sollevare eccezioni, vedi documentazione  
                                    SimpleAnswer answer = new SimpleAnswer(true);
                                    interlocutor.tell(answer, getSelf());
                                } else {
                                    //caso un po' particolare: bisogna creare un file nuovo e concludere
                                    File newFile = new File(tmpFilePath + handshake.getFileName());
                                    if (!newFile.exists()){
                                        newFile.createNewFile();
                                        log.info("New file {} has been created", handshake.getFileName());
                                    } else {
                                        log.error("This should not happen: File {} already exists", handshake.getFileName());
                                        //TOD: si cancella e si ricrea
                                    }
                                    connectionHandler.tell(TcpMessage.close(), getSender()); //magari dovremmo gesrtire il fatto che, se la dimensione era 0, non aggiorniamo lo spazio nella fileTable                                    
                                }
                            }
                            break;
                    }
                } else if (msg instanceof SimpleAnswer){                 
                    SimpleAnswer answer = (SimpleAnswer)msg;
                    interlocutor.tell(answer, getSelf());
                    if(answer.getAnswer() == false){
                        terminate(EnumEnding.NOT_ENOUGH_SPACE);
                    } else {
                        output = new FileOutputStream(filePath + handshake.getFileName() + "Out"); //TODO: non dovrebbe sollevare eccezioni, vedi documentazione  
                    }
                } else if (msg instanceof Received){
                    ByteBuffer buffer = ((Received) msg).data().toByteBuffer();
                        try{
                            output.write(buffer.array());
                        } catch (Exception e){
                            terminate(EnumEnding.FILE_RECEIVING_FAILED);
                        }
                } else if (msg instanceof Terminated){
                    terminate(EnumEnding.FILE_RECEIVING_FAILED);
                } else if(msg instanceof CommandFailed) {
                // OS kernel socket buffer was full
                // Here we should probably bring down the whole Tcp connection
                } else if(msg instanceof ConnectionClosed) {
                    ConnectionClosed connection = (ConnectionClosed)msg;
                    if(connection.isErrorClosed()){
                        terminate(EnumEnding.FILE_RECEIVING_FAILED);
                    } else {
                        //all went the right direction: communicate to the clusterListener...
                        output.close(); //TODO: Throws IOException if an I/O error occurs. In case of exception, rollBack etc.
                        FileTransferResult result = new FileTransferResult(
                                EnumEnding.FILE_RECEIVED_SUCCESSFULLY, handshake.getFileName(), handshake.getModifier());
                        myGuiActor.tell(result, getSelf());
                    }
                }
            }
        };
    }    
}





