// ------------ THINGS TO MODIFY ------------ //
// Messages sent to clusterListener are to be sent to GUI instead.
// 
// Delete "Out" concatenation to fileName of received files.
//
// Choose whether to handler or not not enought space available ON THE PC 
//  - this can be done looking at isPeerClosed()
// ------------------------------------------ //

package FileTransfer;

import java.net.InetSocketAddress;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.PoisonPill;
import akka.actor.UntypedActor;
import akka.io.Tcp;
import akka.io.Tcp.CommandFailed;
import akka.io.Tcp.Connected;
import akka.io.Tcp.ConnectionClosed;
import akka.io.Tcp.Received;
import akka.io.TcpMessage;
import akka.japi.Procedure;
import akka.util.ByteString;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.Object;
import java.nio.ByteBuffer;

public class Client extends UntypedActor {
    final InetSocketAddress remoteServerAddress;
    final ActorRef clusterListener;
    final ActorSelection myServer;
    String fileName, behaviorString;
    TcpBehavior behavior;
    FileModifier readOrWrite;
    FileOutputStream output;
    EnumAuthorizationReply reply;
    long fileLength;
    
    // ----------------------------------- //
    // ---- CONSTRUCTORS AND PRESTART ---- //
    // ----------------------------------- //
    public Client(InetSocketAddress remote, ActorRef listener, String fileName, TcpBehavior behavior) {        
        this.remoteServerAddress = remote;
        this.clusterListener = listener;
        this.fileName = fileName;
        this.behavior = behavior;
        this.fileLength = 0;
        this.readOrWrite = FileModifier.WRITE;
    }
    
    public Client(InetSocketAddress remote, ActorRef listener, String fileName, TcpBehavior behavior, 
            FileModifier readOrWrite) {        
        this(remote, listener, fileName, behavior);
        this.readOrWrite = readOrWrite;  
    }

    @Override
    public void preStart() throws Exception {
        if(behavior == TcpBehavior.SEND_FILE){
            //I will send a file: before this, I have to ask permission to myServer
            AuthorizationRequest requestToSend = new AuthorizationRequest(fileName, FileModifier.WRITE);
            myServer.tell(requestToSend, getSelf());
        } else {
            getSelf().tell("echo", getSelf());
        }
    }
    
    // ----------------------------------- //
    // ---- CONNECTION ENSTABLISHMENT ---- //
    // ----------------------------------- //
    @Override
    public void onReceive(Object msg) throws Exception {
        if(msg instanceof AuthorizationReply || msg instanceof String){
            if (msg instanceof AuthorizationReply){
                EnumAuthorizationReply replyFromMyServer = ((AuthorizationReply) msg).getResponse();
                if (replyFromMyServer == EnumAuthorizationReply.FILE_BUSY ||
                        replyFromMyServer == EnumAuthorizationReply.FILE_NOT_EXISTS){
                    getSelf().tell(PoisonPill.getInstance(),getSelf());
                    return; //da testare
                }
            }
            final ActorRef tcpManager = Tcp.get(getContext().system()).manager();
            tcpManager.tell(TcpMessage.connect(remoteServerAddress), getSelf());
        } else {
            // --- REPLY FROM CONNECTION_HANDLER --- //
            final ActorRef connectionHandler = getSender();
            if (msg instanceof CommandFailed) {
                if (behavior == TcpBehavior.REQUEST_FILE){
                    clusterListener.tell(new FileTransferResult(MessageType.CONNECTION_FAILED), getSelf());
                }
                getContext().stop(getSelf());
            } else if (msg instanceof Connected) {
                connectionHandler.tell(TcpMessage.register(getSelf()), getSelf());
                getContext().become(connected(connectionHandler));   

                if(behavior == TcpBehavior.SEND_FILE){
                    ByteString commandToSend = ByteString.fromString(TcpBehavior.SEND_FILENAME.toString());
                    connectionHandler.tell(TcpMessage.write(commandToSend), getSelf());
                    behavior = TcpBehavior.SEND_FILENAME;
                } else if(behavior == TcpBehavior.REQUEST_FILE){
                    ByteString commandToSend = ByteString.fromString(TcpBehavior.SEND_NAME_OF_REQUESTED_FILE.toString());
                    connectionHandler.tell(TcpMessage.write(commandToSend), getSelf());
                    behavior = TcpBehavior.SEND_NAME_OF_REQUESTED_FILE;
                }
            }
        }
    }

    // ---------------------------------- //
    // ---- CLIENT GENERAL BEHAVIOR ----- //
    // ---------------------------------- //
    private Procedure<Object> connected(final ActorRef connectionHandler) {
        return (Object msg) -> {
            if (msg instanceof Received) {
                switch(behavior) {
                    case SEND_FILENAME:
                        File fileToSend = new File(fileName);
                        fileLength = fileToSend.length();
                        if (!fileToSend.exists()){
                            System.out.println("[client]: il file non esiste\n");
                            connectionHandler.tell(TcpMessage.close(), getSelf());
                            break;
                        }
                        ByteString fileNameToSend = ByteString.fromString(fileName);
                        connectionHandler.tell(TcpMessage.write(fileNameToSend), getSelf());
                        behavior = TcpBehavior.SEND_FILE_NOW;
                        break;
                    case SEND_FILE_NOW:  
                        Tcp.Event ack_or_notAck = new Tcp.NoAck$();
                        connectionHandler.tell(TcpMessage.writeFile(fileName, 0, fileLength, ack_or_notAck),
                                getSelf());
                        connectionHandler.tell(TcpMessage.close(), getSelf());
                        break;
                    case SEND_NAME_OF_REQUESTED_FILE:
                        String modifier = (readOrWrite == FileModifier.READ) ? "r" : "w";
                        ByteString modifierAndFileName = ByteString.fromString(modifier.concat(fileName));
                        connectionHandler.tell(TcpMessage.write(modifierAndFileName), getSelf());
                        behavior = TcpBehavior.AUTHORIZATION_REPLY_HANDLE;
                        break;
                    case AUTHORIZATION_REPLY_HANDLE:
                        String replyString = ((Received) msg).data().utf8String();
                        reply = EnumAuthorizationReply.valueOf(replyString);
                        System.out.printf("[client]: ho ricevuto %s\n", reply.toString());
                        
                        switch(reply){
                            case AUTHORIZATION_GRANTED:
                                connectionHandler.tell(TcpMessage.write(ByteString.fromString("echo")), 
                                        getSelf()); //echo
                                fileName = fileName.concat("Out"); //.concat va tolto nella versione finale
                                output = new FileOutputStream(fileName);
                                behavior = TcpBehavior.RECEIVE_FILE_NOW;
                                System.out.println("[client]: authorization_granted\n");
                                break;
                            case FILE_NOT_EXISTS:
                                clusterListener.tell(new FileTransferResult(
                                        MessageType.FILE_NOT_EXISTS, fileName), getSelf());
                                connectionHandler.tell(TcpMessage.close(), getSelf());
                                System.out.println("[client]: file_not_exists\n");
                                break;
                            case FILE_BUSY:
                                clusterListener.tell(new FileTransferResult(
                                        MessageType.FILE_BUSY, fileName), getSelf());
                                connectionHandler.tell(TcpMessage.close(), getSelf());
                                System.out.println("[client]: file_busy\n");
                                break;
                        }
                        break;
                    case RECEIVE_FILE_NOW:
                        ByteBuffer buffer = ((Received) msg).data().toByteBuffer();
                        try{
                            output.write(buffer.array());
                        } catch (Exception e){}
                        break;
                }
            } else if(msg instanceof CommandFailed) {
                // OS kernel socket buffer was full
                // Here we should probably bring down the whole Tcp connection
            }
               
            // ---------------------------- //
            // ---- CONNECTION CLOSING ---- //
            // ---------------------------- //
            else if(msg instanceof ConnectionClosed) {
                ConnectionClosed connection = (ConnectionClosed)msg;
                System.out.printf("[client]: sono nella ConnectionClose, e command vale %s,"
                        + "\n metre getErrorCause restituisce %s"
                        + "\n mentre isPeerClosed() restituisce %b"
                        + "\n mentre isAborted() restituisce %b"
                        + "\n mentre isErrorClosed() restituisce %b\n",
                        behavior.toString(), connection.getErrorCause(), connection.isPeerClosed(),
                        connection.isAborted(), connection.isErrorClosed());

                if(connection.isErrorClosed()){
                    if(behavior == TcpBehavior.SEND_FILE || behavior == TcpBehavior.SEND_FILENAME ||
                            behavior == TcpBehavior.SEND_FILE_NOW){
                        myServer.tell(new FileTransferResult(
                                MessageType.FILE_NO_MORE_BUSY, fileName, readOrWrite), getSelf());
                    } else{
                        if (behavior == TcpBehavior.RECEIVE_FILE_NOW){
                            output.close();
                            //bisogna anche cancellare il "pezzo" di file che e' stato creato
                        }
                        clusterListener.tell(new FileTransferResult(
                                MessageType.FILE_RECEIVING_ERROR, fileName),getSelf());                    
                    }
                } else{ 
                //sarebbe la connectionClosed
                    if(behavior == TcpBehavior.SEND_FILE_NOW){
                        myServer.tell(new FileTransferResult(
                                MessageType.FILE_SENT_SUCCESSFULLY, fileName, readOrWrite), getSelf());
                    } else if(behavior == TcpBehavior.SEND_FILENAME){
                        //file opening failed: nothing to do
                    }
                    else if(behavior == TcpBehavior.RECEIVE_FILE_NOW){
                        if (reply == EnumAuthorizationReply.AUTHORIZATION_GRANTED){
                            output.close();
                            myServer.tell(new FileTransferResult(
                                    MessageType.FILE_RECEIVED_SUCCESSFULLY, fileName, readOrWrite), 
                                    getSelf());
                            clusterListener.tell(new FileTransferResult(
                                    MessageType.FILE_RECEIVED_SUCCESSFULLY, fileName, readOrWrite), 
                                    getSelf());
                        }
                    } 
                }
                getContext().stop(getSelf());
            }
        };
    }
}

