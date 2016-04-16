/******************************
 *  TODO: in the TcpReceived, we must decide what happen in case of IO error.
 *  (smarter opinion below --> below) 
 * In my opinion, it would be useful to send a FileTransferResult with the EnumEnding field
 *  set to IO_ERROR: this way we could in a first moment send a receiverInterlocutor.tell(IO_ERROR, ...),
 *  and then call the terminate with FILE_RECEIVING_ERROR as argument.
 *  For uniformity, but losing something in readability, we could send FILE_RECEIVING_ERROR also
 *  to the receiverInterlocutor. 
 * 
 * 2) check all the readOrWrite-depending features have been correctly implemented:
 * nel sender, se siamo in una richiesta di lettura, dopo aver mandato la authorizationGranted 
 * bisogna subito cominciare a far girare il file, senza aspettare una risposta, dato che questa
 * non arriverà. E' sufficiente mandare a se stessi una simpleAnswer
 * 
 * 3) nel Server bisogna ancora fare la allocationRequest
 ******************************/

package FileTransfer;

import FileTransfer.messages.Ack;
import FileTransfer.messages.AllocationRequest;
import FileTransfer.messages.AuthorizationReply;
import FileTransfer.messages.EnumBehavior;
import FileTransfer.messages.FileTransferResult;
import FileTransfer.messages.Handshake;
import FileTransfer.messages.EnumEnding;
import FileTransfer.messages.EnumFileModifier;
import FileTransfer.messages.SimpleAnswer;
import Startup.AddressResolver;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.PoisonPill;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
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

public class FileTransferActor extends UntypedActor {
    ActorSelection myClusterListener;
    ActorSelection myServer;
    ActorSelection remoteServer;
    ActorSelection myGuiActor;
    ActorSelection receiverInterlocutor;
    ActorRef senderInterlocutor;
    ActorRef connectionHandler;
    Handshake handshake;
    int clusterSystemPort;
    int tcpPort;
    String remoteServerIP;  
    long size;
    InetSocketAddress interlocutorAddress;
    FileOutputStream output;
    
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
    
    // --- constructor called by sender --- //
    public FileTransferActor(int basePort, Handshake handshake) {
        this(basePort);
        this.handshake = handshake;
    }
    
    // --- constructor called by responder --- //
    public FileTransferActor(int basePort, InetSocketAddress interlocutorAddress, ActorRef connectionHandler ) {
        this(basePort);
        this.interlocutorAddress = interlocutorAddress;
        this.connectionHandler = connectionHandler;
    }    
    
    // ----------------------------- //
    // ---- ADDITIONAL FUNCTION ---- //
    // ----------------------------- //
    public void changeBehavior(){
    //ha senso mettere come argomento il connectionHandler?
        if(handshake.getBehavior() == EnumBehavior.SEND){
            getContext().become(tcpSender());  
            getSelf().tell("go", getSelf()); //per fare il trigger, sennò il sender non parte
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
        // check if this ref is needed by both behaviro
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
                receiverInterlocutor = getContext().actorSelection("akka.tcp://ClusterSystem@"+interlocutorAddress
                .getAddress().getHostAddress()+":"+clusterSystemPort+"/user/fileTransferSender");     
                receiverInterlocutor.tell("ack", getSelf());
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
            senderInterlocutor = getSender();
            senderInterlocutor.tell(handshake, getSelf());
            getContext().watch(senderInterlocutor);
            changeBehavior();
        } else if (msg instanceof Handshake){
            this.handshake = (Handshake)msg;
            getContext().watch(senderInterlocutor);
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
                senderInterlocutor.tell(result, getSelf());
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
                            terminate(EnumEnding.FILE_NOT_EXISTS);
                            break;
                            //vedere se la busy si può uniformare con quella di sopra
                        case FILE_BUSY:
                            senderInterlocutor.tell(reply, getSelf());
                            result = new FileTransferResult(EnumEnding.FILE_SENDING_FAILED, handshake.getFileName());
                            myGuiActor.tell(result, getSelf());
                            rollBack();
                            break;
                        case AUTHORIZATION_GRANTED:
                            File fileToSend = new File(handshake.getFileName());
                            size = reply.getSize();
                            if (fileToSend.exists()&&fileToSend.canRead()){
                                senderInterlocutor.tell(reply, getSelf());
                            } else {
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
                        connectionHandler.tell(TcpMessage.writeFile(handshake.getFileName(), 0, size, 
                                ack_or_notAck), getSelf());
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
                File corruptedFile = new File(handshake.getFileName());
                if(corruptedFile.exists() && corruptedFile.canWrite()){
                    corruptedFile.delete();
                }
                result = new FileTransferResult(
                        EnumEnding.FILE_RECEIVING_FAILED, handshake.getFileName());  
                myServer.tell(result, getSelf());
            }
            
            public void terminate(EnumEnding msg){
                // TODO: Propongo, per mandare lo stesso messaggio sia a myGuiActor che all'interlocutore,
                // di, in caso siamo nel trasferimento dei dati, mandare ad entrambi un messaggio
                // di tipo "file_receiving_failed";
                result = new FileTransferResult(msg, handshake.getFileName(), handshake.getModifier());
                myGuiActor.tell(result, getSelf());
                /**********************************
                 *  I enter here if 
                 *  1) we are receiving the file, and there is an IO error
                 **********************************/
                //senderInterlocutor.tell(result, getSelf());
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
                            // --- The AllocationRequest is sended also if the request is a READ: --- //
                            // --- in this case, the server will surely have enought space and accept --- //
                            if(handshake.getModifier() == EnumFileModifier.WRITE){
                                size = reply.getSize();
                                AllocationRequest request = new AllocationRequest(
                                        handshake.getFileName(), size, reply.getTags());
                                myServer.tell(request, getSelf()); 
                                if (size == 0){
                                    connectionHandler.tell(TcpMessage.close(), getSender()); //magari dovremmo gesrtire il fatto che, se la dimensione era 0, non aggiorniamo lo spazio nella fileTable
                                }
                            } else {  
                                // --- READ request: we will give the permission without asking the server --- //
                                if (size == 0){
                                    //caso un po' particolare: bisogna creare un file nuovo e concludere
                                    connectionHandler.tell(TcpMessage.close(), getSender()); //magari dovremmo gesrtire il fatto che, se la dimensione era 0, non aggiorniamo lo spazio nella fileTable
                                } else {
                                    output = new FileOutputStream(handshake.getFileName()); //TODO: non dovrebbe sollevare eccezioni, vedi documentazione  
                                    SimpleAnswer answer = new SimpleAnswer(true);
                                    receiverInterlocutor.tell(answer, getSelf());
                                }
                            }
                            break;
                    }
                } else if (msg instanceof SimpleAnswer){                 
                    SimpleAnswer answer = (SimpleAnswer)msg;
                    receiverInterlocutor.tell(answer, getSelf());
                    if(answer.getAnswer() == false){
                        terminate(EnumEnding.FILE_RECEIVING_FAILED);
                    } else {
                        output = new FileOutputStream(handshake.getFileName()); //TODO: non dovrebbe sollevare eccezioni, vedi documentazione  
                    }
                } else if (msg instanceof Received){
                    ByteBuffer buffer = ((Received) msg).data().toByteBuffer();
                        try{
                            output.write(buffer.array());
                        } catch (Exception e){
                            //see TODO at the file beginning
                            terminate(EnumEnding.FILE_RECEIVING_FAILED);
                        }
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
                    }
                }
            }
        };
    }    
}





