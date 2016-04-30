/**
 * ******************************
 * -  guarare i vari TODO/check/Err sparsi nel testo
 *****************************
 */
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
import FileTransfer.messages.Hello;
import FileTransfer.messages.SimpleAnswer;
import Startup.AddressResolver;
import Startup.WatchMe;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.PoisonPill;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.io.Inet.SocketOption;
import akka.io.Tcp;
import akka.io.Tcp.CommandFailed;
import akka.io.Tcp.Connected;
import akka.io.Tcp.ConnectionClosed;
import akka.io.Tcp.Received;
import akka.io.TcpMessage;
import akka.japi.Procedure;
import com.typesafe.config.Config;
import java.io.File;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collections;
import static java.util.concurrent.TimeUnit.SECONDS;
import scala.concurrent.Await;
import scala.concurrent.duration.FiniteDuration;

public class FileTransferActor extends UntypedActor {

    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    private Config config = getContext().system().settings().config();
    private ActorSelection myClusterListener;
    private ActorSelection myServer;
    private ActorSelection remoteServer;
    private ActorSelection myGuiActor;
    private ActorSelection soulReaper;
    private ActorRef interlocutor;
    private ActorRef connectionHandler;
    private Handshake handshake;
    private int localClusterSystemPort;
    private int remoteClusterSystemPort;
    // --- tcpPort is used only when this FileTrransferActor is working as client
    private int tcpPort;
    private InetAddress interlocutorIp;
    private long size;
    private FileOutputStream output;
    private String filePath;
    private String tmpFilePath = System.getProperty("java.io.tmpdir");
    private boolean fileOccupiedByMe;

    // ---------------------- //
    // ---- CONSTRUCTORS ---- //
    // ---------------------- //
    // --- Base constructor, always called. Size is initialized at 0 so that,
    // --- if something goes wrong when the receiver has not asked yet to the server,
    // --- the permission to receive the file, the rollBack procedure isn't dangerous 
    // --- (e.g. it could try to free some space although no space was allocated
    public FileTransferActor(int remoteClusterSystemPort) {
        this.remoteClusterSystemPort = remoteClusterSystemPort;
        this.localClusterSystemPort = config.getInt("akka.remote.netty.tcp.port");
        filePath = config.getString("app-settings.file-path");
        size = 0;
        fileOccupiedByMe = false;
    }

    // --- Constructor called by the asker. The handshake variabile specify what the
    // --- asker has to do (e.g. send a file or request a file). The remoteServerIp is the IP address
    // --- of the server to which che asker will try to connect.
    public FileTransferActor(InetAddress remoteServerIp, int remoteClusterSystemPort,
            Handshake handshake) {
        this(remoteClusterSystemPort);
        this.handshake = handshake;
        this.interlocutorIp = remoteServerIp;
        log.debug("Interlocutor IP is {}", remoteServerIp.getHostAddress());
    }

    // --- Constructor called by the responder. The handshake variabile is also used to distinguish
    // --- which part of the code the FileTransferActor has to execute (see preStart)
    public FileTransferActor(InetAddress askerIp, int remoteClusterSystemPort,
            ActorRef connectionHandler) {
        this(remoteClusterSystemPort);
        handshake = new Handshake(EnumBehavior.UNINITIALIZED);
        this.interlocutorIp = askerIp;
        this.connectionHandler = connectionHandler;
        log.debug("Interlocutor IP is {}", askerIp.getHostAddress());
    }

    // ----------------------------- //
    // ---- ADDITIONAL FUNCTION ---- //
    // ----------------------------- //
    // --- This function is invoked both by the asker and by the responder, once the
    // --- handshake.behavior variabile has been initialized, to change their own
    // --- behavior to tcpSender or tcpReceiver.    
    public void changeBehavior() {
        log.debug("My behavior is {}", handshake.getBehavior());
        if (handshake.getBehavior() == EnumBehavior.SEND) {
            // --- I change my behavior to tcpSender and I tell myself to start the protocol --- //
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
        myServer = getContext().actorSelection("akka.tcp://ClusterSystem@" + AddressResolver.getMyIpAddress() + ":"
                + localClusterSystemPort + "/user/server");
        soulReaper = getContext().actorSelection("akka.tcp://ClusterSystem@"+AddressResolver.getMyIpAddress()+":"
                +localClusterSystemPort+"/user/soulReaper");
        // TODO: check if this reference is needed by both behavior
        myClusterListener = getContext().actorSelection("akka.tcp://ClusterSystem@" + AddressResolver.getMyIpAddress() + ":"
                + localClusterSystemPort + "/user/clusterListener");
        myGuiActor = getContext().actorSelection("akka.tcp://ClusterSystem@" + AddressResolver.getMyIpAddress() + ":"
                + localClusterSystemPort + "/user/gui");
        log.debug("My server's name is {} ", myServer);
        soulReaper.tell(new WatchMe(), getSelf());

        switch (handshake.getBehavior()) {
            case SEND:
            case REQUEST:
                // --- I am the asker, so I have to connect to the remoteServer.
                // --- The tcpManager does this for me.
                remoteServer = getContext().actorSelection("akka.tcp://ClusterSystem@" + interlocutorIp.getHostAddress() + ":"
                        + remoteClusterSystemPort + "/user/server");
                Hello hello = new Hello(AddressResolver.getMyIpAddress(), localClusterSystemPort);
                remoteServer.tell(hello, getSelf());
                log.debug("Remote server's name is {}", remoteServer);
                break;
            case UNINITIALIZED:
                // --- I am the responder, I was spawned by my server for handling an
                // --- incoming connection. I'll ack the asker peer just to let him know my address
                ActorSelection interlocutorSelection = getContext().actorSelection("akka.tcp://ClusterSystem@" + interlocutorIp.getHostAddress() + ":"
                        + remoteClusterSystemPort + "/user/clusterListener/fileTransferAsker");
                FiniteDuration timeout = new FiniteDuration(10, SECONDS);

                interlocutor = Await.result(interlocutorSelection.resolveOne(timeout), timeout);
                interlocutor.tell(new Ack(), getSelf());
                log.debug("Interlocutor actorRef is {}", interlocutor);
                break;
        }
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof Hello) {
            tcpPort = ((Hello) msg).getPort();

            // --- I use the received port from the server to connect to it.
            final ActorRef tcpManager = Tcp.get(getContext().system()).manager();
            InetSocketAddress remoteServerAddress = new InetSocketAddress(interlocutorIp, tcpPort);
            InetSocketAddress localAddress = new InetSocketAddress(AddressResolver.getMyIpAddress(), 0);
            FiniteDuration timeout = new FiniteDuration(10, SECONDS);
            tcpManager.tell(TcpMessage.connect(remoteServerAddress, localAddress,
                    Collections.<SocketOption>emptyList(), timeout, false), getSelf());

        } else if (msg instanceof CommandFailed) {
            // --- I am the asker. The connectionHandler tells me the connection enstablishment failed
            FileTransferResult result = new FileTransferResult(
                    EnumEnding.CONNECTION_FAILED, handshake.getFileName(), handshake.getModifier());
            myServer.tell(result, getSelf());
            getSelf().tell(PoisonPill.getInstance(), getSelf());
            log.error("Connection enstablishment with remote server failed");
        } else if (msg instanceof Connected) {
            // --- I am the asker. The connectionHandler tells me the connection is enstablished
            connectionHandler = getSender();
            connectionHandler.tell(TcpMessage.register(getSelf()), getSelf());
            log.info("Connection established with remote server");
        } else if (msg instanceof Ack) {
            // --- I am the asker. The responder have acked me, so that now I can reach him and tell
            // --- him what I am willing to do (e.g sending him a file or requesting a file from him)
            // --- Finally, I invoke changeBehavior() to became sender or receiver according to the
            // --- handshake variabile that was given to me by the one who has created me
            interlocutor = getSender();
            interlocutor.tell(handshake, getSelf());
            changeBehavior();
            log.debug("I have received the Ack, so now I can contact the remote peer");
        } else if (msg instanceof Handshake) {
            // --- I am the responder. I've received the handshake variabile who tell me how
            // --- I must set my behavior.
            // --- Then the behavior is initially specialized at the opposite value of behavior within
            // --- the Handshake message
            this.handshake = (Handshake) msg;
            if (handshake.getBehavior() == EnumBehavior.SEND) {
                handshake.setBehavior(EnumBehavior.REQUEST);
            } else {
                handshake.setBehavior(EnumBehavior.SEND);
            }
            changeBehavior();
            log.debug("Handshake message received");
        }
    }

    // ----------------------------- //
    // ---- TCP SENDER BEHAVIOR ---- //
    // ----------------------------- //    
    // --- Here is specified what the FileTransferActor, when he assume the role of tcpSender,
    // --- has to do in response to the message that can arrive.
    private Procedure<Object> tcpSender() {
        return new Procedure<Object>() {
            private FileTransferResult result;
            
            // --- In the terminate function we create a FileTransferResult message inizializated with the 
            // --- passed EnumEnding. I'll send this to the server, who will take the appropriate action
            // --- based on the EnumEnding value, the it will forward the FileTransferResult message
            // --- to the GuiActor. 
            // --- In the meanwhile, I'll unwatch che interlocutor, because I'm no more interested
            // --- in knowing his failures, and finally I suicide myself
            public void terminate(EnumEnding msg) {
                result = new FileTransferResult(
                        msg, handshake.getFileName(), handshake.getModifier());
                myServer.tell(result, getSelf());
                getSelf().tell(PoisonPill.getInstance(), getSelf());
            }

            @Override
            public void apply(Object msg) throws Exception {
                if (msg instanceof String) {
                    // --- I receive the "go" message from myself, telling me to start the protocol
                    myServer.tell(handshake, getSelf());
                } else if (msg instanceof AuthorizationReply) {
                    // --- I receive this answer from myServer, who says if the file I have to send
                    // --- is busy, available, or if it doesn't exists (this is an error)
                    AuthorizationReply reply = (AuthorizationReply) msg;
                    switch (reply.getResponse()) {
                        case FILE_NOT_EXISTS:
                            // --- The file I had to send doesn't exists. I forward the information
                            // --- to the receiver, and terminate the protocol.
                            interlocutor.tell(reply, getSelf());
                            terminate(EnumEnding.FILE_TO_SEND_NOT_EXISTS);
                            log.error("File {} doesn't exists", handshake.getFileName());
                            break;
                        case FILE_BUSY:
                            // --- In this case I must not perform the rollBack, because
                            // --- it frees the file I was trying to send, but in this case
                            // --- that file is keept busy by someone different from me, so I must not free it.
                            // --- There is no need to inform the guiActor: if a exitLoadBalancing
                            // --- is happening, sending a FILE_SENDING_FAILED would cause the
                            // --- guiActor to decrease the count of files to send, and we don't want this.                            
                            interlocutor.tell(reply, getSelf());
                            terminate(EnumEnding.FILE_TO_SEND_BUSY);
                            log.info("File {} is busy", handshake.getFileName());
                            break;
                        case AUTHORIZATION_GRANTED:
                            // --- The file exists and I'm allowed to send it. I have to perform some checks
                            File fileToSend = new File(filePath + handshake.getFileName());
                            size = reply.getSize();
                            if (fileToSend.exists() && fileToSend.canRead()) {
                                interlocutor.tell(reply, getSelf());
                                // --- I enter the following branch if the file does exists but I couldn't 
                                // --- open it. I cannot forward the AUTHORIZATION_GRANTED message received
                                // --- from the server, we have to override it with a FILE_NOT_EXISTS message.
                            } else {
                                reply = new AuthorizationReply(EnumAuthorizationReply.FILE_NOT_EXISTS);
                                interlocutor.tell(reply, getSelf());
                                terminate(EnumEnding.FILE_OPENING_FAILED);
                                log.error("Impossible to open file {}", handshake.getFileName());
                            }
                            break;
                    }
                } else if (msg instanceof SimpleAnswer) {
                    // --- I've received a message from the tcpReceiver who tells me
                    // --- if he has enought space for storing the file I have to send.
                    SimpleAnswer answer = (SimpleAnswer) msg;
                    if (answer.getAnswer() == true) {
                        Tcp.Event ack_or_notAck = new Tcp.NoAck$();
                        connectionHandler.tell(TcpMessage.writeFile(filePath + handshake.getFileName(),
                                0, size, ack_or_notAck), getSelf());
                        connectionHandler.tell(TcpMessage.close(), getSelf());
                        log.info("Close performed");
                    } else {
                        terminate(EnumEnding.NOT_ENOUGH_SPACE_FOR_SENDING);
                        log.info("Permission of sending file {} was denied", handshake.getFileName());
                    }
                } else if (msg instanceof FileTransferResult) {
                    FileTransferResult result = (FileTransferResult) msg;
                    terminate(EnumEnding.IO_ERROR_WHILE_SENDING);
                    log.info("The receiver reported: {}", result.getMessageType());
                    
                } else if (msg instanceof CommandFailed) {
                    // --- We enter this branch if the OS kernel socket buffer was full
                    // --- TODO: Here we should probably bring down the whole Tcp connection
                    log.error("Error: the OS kernel socket buffer was full");
                } else if (msg instanceof ConnectionClosed) {
                    ConnectionClosed connection = (ConnectionClosed) msg;
                    log.info("connectionClosed received, with errorCause {}", connection.getErrorCause());
                    if (connection.isErrorClosed()) {
                        // --- This branch handle the case there was an error during the TcpConnection
                        EnumEnding ending;
                        ending = (fileOccupiedByMe == true) ? EnumEnding.FILE_NO_MORE_BUSY
                                : EnumEnding.FILE_SENDING_FAILED;
                        terminate(ending);
                        log.info("TcpConnection error. Sending of {} failed", handshake.getFileName());
                    } else {
                        // --- I enter in this branch if the file was successfully transferred.
                        // --- I communicate the outcome to the server, who is in charge of
                        // --- deleting the file from my hard disk and from the file table and
                        // --- deallocate the space who was occupied by him.                        
                        terminate(EnumEnding.FILE_SENT_SUCCESSFULLY);
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
            
            public void terminate(EnumEnding msg) {
                result = new FileTransferResult(msg, handshake.getFileName(), handshake.getModifier());
                myServer.tell(result, getSelf());

                getSelf().tell(PoisonPill.getInstance(), getSelf());
            }

            @Override
            public void apply(Object msg) throws Exception {
                if (msg instanceof AuthorizationReply) {
                    log.info("AuthorizationReply from {} said: {}", getSender(), (AuthorizationReply) msg);
                    AuthorizationReply reply = (AuthorizationReply) msg;
                    switch (reply.getResponse()) {
                        case FILE_NOT_EXISTS:
                            terminate(EnumEnding.FILE_TO_RECEIVE_NOT_EXISTS);
                            log.error("File {} doesn't exists.", handshake.getFileName());
                            break;
                        case FILE_BUSY:
                            terminate(EnumEnding.FILE_TO_RECEIVE_BUSY);
                            log.info("File {} is busy", handshake.getFileName());
                            break;
                        case AUTHORIZATION_GRANTED:
                            // --- If the request is a READ, the server is not required to store
                            // --- the file in the fileTable, so there is no need to ask for his permission.
                            // --- Of course, we must create ourself a reply for the sender.
                            // --- Instead if it's a WRITE, he will do it, and in this case the
                            // --- file will not be marked as busy ('false' parameter),
                            // --- because the fileInfoTable will not know I have the file
                            // --- until the end of the protocol, when I'll spread this information
                            if (handshake.getModifier() == EnumFileModifier.WRITE) {
                                size = reply.getSize();
                                AllocationRequest request = new AllocationRequest(
                                        handshake.getFileName(), size, reply.getTags(), false);
                                myServer.tell(request, getSelf());
                                // --- if size is 0 I need also to send immediately a response to the interlocutor
                                // --- the response is for sure positive
                                if(size==0){
                                    SimpleAnswer answer = new SimpleAnswer(true);
                                    getSelf().tell(answer, getSelf());
                                }
                            } else {
                                // --- READ request: I will give the permission without asking the server --- //
                                SimpleAnswer answer = new SimpleAnswer(true);
                                getSelf().tell(answer, getSelf());
                            break;
                            }
                    }
                } else if (msg instanceof SimpleAnswer) {
                    // --- This is the reply from my server, telling me if there is enough space
                    // --- for storing the file the tcpSendere wants to send me.
                    // --- If fileSize !=0, the sender has to send me the file (if I have enough space),
                    // --- so I forward him the reply from myServer, telling if there is enough space.
                    // --- If the response is negative, the protocol ends here. I must communicate
                    // --- this also to my interlocutor 
                    SimpleAnswer answer = (SimpleAnswer) msg;
                    String path = (handshake.getModifier() == EnumFileModifier.WRITE) ? filePath : tmpFilePath;
                    if (size != 0){
                        interlocutor.tell(answer, getSelf());
                        if (answer.getAnswer() == false) {
                            //answer = new SimpleAnswer(false);
                            terminate(EnumEnding.NOT_ENOUGH_SPACE_FOR_RECEIVING);
                            log.info("Not enough space for receiving file {}", handshake.getFileName());
                        } else {
                            // --- The permission was granted. The connection will be closed at the end

                            File newFile = new File(path + handshake.getFileName());
                            if (!newFile.exists()) {
                                output = new FileOutputStream(path + handshake.getFileName()); //TODO: non dovrebbe sollevare eccezioni, vedi documentazione  
                            } else if (newFile.canWrite()) {
                                newFile.delete();
                                output = new FileOutputStream(path + handshake.getFileName()); //TODO: non dovrebbe sollevare eccezioni, vedi documentazione  
                                log.warning("The file {} was already existing, so I've overridden it", handshake.getFileName());
                            } else {
                                log.warning("The file {} already exists, and I was not able to override it", handshake.getFileName());
                            }
                            //connectionHandler.tell(TcpMessage.close(), getSender());                                
                        }   
                    } else {
                        // --- Size is 0
                        
                        // --- special case: create the file and end the protocol.
                        // --- In the close() handling, the guiActor
                        // --- will be informed of the success.
                        // --- If the file already exists, we override it (but this should not happen)
                        File newFile = new File(path + handshake.getFileName());
                        if (!newFile.exists()) {
                            newFile.createNewFile(); //TODO: maybe in this case a close is in order? (the sender doesn't perform this step)
                            log.info("New file {} has been created", handshake.getFileName());
                        } else if (newFile.canWrite()) {
                                newFile.delete();
                                newFile.createNewFile();
                                log.warning("The file {} was already existing, so I've overridden it", handshake.getFileName());
                        } else {
                                log.warning("The file {} already exists, and I was not able to override it", handshake.getFileName());
                        }
                        connectionHandler.tell(TcpMessage.close(), getSelf()); //magari dovremmo gesrtire il fatto che, se la dimensione era 0, non aggiorniamo lo spazio nella fileTable                            
                    }
                } else if (msg instanceof Received) {
                    ByteBuffer buffer = ((Received) msg).data().toByteBuffer();
                    try {
                        output.write(buffer.array());
                    } catch (Exception e) {
                        result = new FileTransferResult(EnumEnding.IO_ERROR_WHILE_RECEIVING);
                        interlocutor.tell(result, getSelf());
                        terminate(EnumEnding.IO_ERROR_WHILE_RECEIVING);
                        log.error("Error in receiving file {}: there is not enough space on my hard disk", handshake.getFileName());
                    }
                    
                } else if (msg instanceof CommandFailed) {
                    // --- OS kernel socket buffer was full
                    // --- TODO: Here we should probably bring down the whole Tcp connection
                    log.error("the OS kernel socket buffer was full");
                } else if (msg instanceof ConnectionClosed) {
                    ConnectionClosed connection = (ConnectionClosed) msg;
                    if(output!=null)
                        output.close(); //TODO: Throws IOException if an I/O error occurs. In case of exception, rollBack etc.
                    log.info("connectionClosed received, with errorCause {}", connection.getErrorCause());
                    if (connection.isErrorClosed()) {
                        terminate(EnumEnding.FILE_RECEIVING_FAILED);
                        log.info("There was an error during the TcpConnection, so receiving of file {} has failed", handshake.getFileName());
                    } else {
                        // --- all went the right direction: communicate to the clusterListener...
                        terminate(EnumEnding.FILE_RECEIVED_SUCCESSFULLY);
                    }
                }
            }
        };
    }
}
