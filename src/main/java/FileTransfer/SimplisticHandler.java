package FileTransfer;

import FileTransfer.AuthorizationReply;
import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.io.Tcp;
import akka.io.Tcp.ConnectionClosed;
import akka.io.Tcp.Received;
import akka.io.TcpMessage;
import akka.util.ByteString;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class SimplisticHandler extends UntypedActor {
    final ActorRef clusterListener, connectionHandler, server;
    TcpBehavior behavior;
    String fileName, behaviorString;
    FileOutputStream output;
    FileModifier readOrWrite;
    char readOrWriteChar;
    EnumAuthorizationReply reply; //va usata affinchè nella closed si sappia qual è la risposta data
    long fileLength;
    
    // --------------------- //
    // ---- CONSTRUCTOR ---- //
    // --------------------- //
    public SimplisticHandler(ActorRef clusterListener, ActorRef connectionHandler, ActorRef server) {
        this.clusterListener = clusterListener;
        this.connectionHandler = connectionHandler;
        this.server = server;
        fileName = "";
        behavior = TcpBehavior.UNINITIALIZED;
        behaviorString = "";
        readOrWriteChar = 'z'; //uninitialized
        fileLength=0;
    }
    
    // -------------------------------------------- //
    // ---- SIMPLISTICHANDLER GENERAL BEHAVIOR ---- //
    // -------------------------------------------- //
    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof Received) {
            final ByteString data = ((Received) msg).data();
            final ByteString echo = ByteString.fromString("echo");
            
            switch(behavior){
                case UNINITIALIZED:
                    getSender().tell(TcpMessage.write(echo), getSelf()); //echo
                    behavior = TcpBehavior.valueOf(data.utf8String());
                    break;
                case SEND_FILENAME:
                    String fileNameSent = data.utf8String(); 
                    fileName = fileNameSent.concat("Out"); //nella versione ufficiale va tolto il .concat
                    getSender().tell(TcpMessage.write(echo), getSelf()); //echo
                    output = new FileOutputStream(fileName);
                    behavior = TcpBehavior.SEND_FILE_NOW;
                    break;
                case SEND_FILE_NOW:
                    ByteBuffer buffer = data.toByteBuffer();
                    try {
                        output.write(buffer.array());
                    } finally {}
                    break;
                case SEND_NAME_OF_REQUESTED_FILE:
                    readOrWriteChar = data.utf8String().charAt(0); 
                    readOrWrite = (readOrWriteChar == 'r') ? FileModifier.READ : FileModifier.WRITE;
                    fileName = data.utf8String().substring(1);
                    AuthorizationRequest requestToSend = new AuthorizationRequest(fileName, readOrWrite); 
                    behavior = TcpBehavior.AUTHORIZATION_REPLY_HANDLE; //Deve stare qui, posticiparla può creare problemi
                    server.tell(requestToSend, getSelf());
                    break;
                case RECEIVE_FILE_NOW:
                    Tcp.Event ack_or_notAck = new Tcp.NoAck$();
                    connectionHandler.tell(TcpMessage.writeFile(fileName, 0, fileLength, ack_or_notAck),getSelf());
                    connectionHandler.tell(TcpMessage.close(), getSelf());
                    break;
            }      
        }
        
        // ------------------------------------------- //
        // ---- SERVER AUTHORIZATION REPLY HANDLE ---- //
        // ------------------------------------------- //    
        else if(msg instanceof AuthorizationReply){
            reply = ((AuthorizationReply) msg).getResponse();
            System.out.printf("[simplisticHandler]: ho ricevuto la risposta %s\n", reply.toString());
            connectionHandler.tell(TcpMessage.write(ByteString.fromString(reply.toString())), getSelf());
            
            if(reply == EnumAuthorizationReply.AUTHORIZATION_GRANTED){
                File fileToSend = new File(fileName);
                fileLength = fileToSend.length();
                if (!fileToSend.exists()){
                    System.out.println("[simplHandler]: il file non esiste\n");
                    connectionHandler.tell(TcpMessage.close(), getSelf());
                    return;     //va bene per uscire, no? 
                                //è importante che non venga settato il behavior
                }
                behavior = TcpBehavior.RECEIVE_FILE_NOW;
            }      
        }  
        
        // ---------------------------- //
        // ---- CONNECITON CLOSING ---- //
        //----------------------------- //  
        else if (msg instanceof ConnectionClosed) {
            ConnectionClosed connection = (ConnectionClosed)msg;
            
            System.out.printf("\n[simplisticHandler]: sono nella ConnectionClose, e command vale %s,"
                            + "\n metre getErrorCause restituisce %s"
                            + "\n mentre isPeerClosed() restituisce %b"
                            + "\n mentre isAborted() restituisce %b"
                            + "\n mentre isErrorClosed() restituisce %b\n",
                            behavior.toString(), connection.getErrorCause(), connection.isPeerClosed(), 
                            connection.isAborted(), connection.isErrorClosed());
            
            if (connection.isErrorClosed()){
                if(behavior == TcpBehavior.AUTHORIZATION_REPLY_HANDLE || behavior == TcpBehavior.RECEIVE_FILE_NOW){
                    server.tell(new FileTransferResult(MessageType.FILE_NO_MORE_BUSY, fileName, readOrWrite), 
                            getSelf());
                } else if(behavior == TcpBehavior.SEND_FILE_NOW){
                    output.close(); 
                    try{ //must be tested
                        File corruptedFile = new File(fileName);
                        corruptedFile.delete();
                    } catch (Exception e){
                        System.out.println("Not a big deal!");
                    }
                } 
            } else{ 
            //A TcpMessage.close() was sent
                if(behavior == TcpBehavior.SEND_FILE_NOW){
                    output.close();
                    server.tell(new FileTransferResult(
                            MessageType.FILE_RECEIVED_SUCCESSFULLY, fileName, FileModifier.WRITE), getSelf());
                } else if(behavior == TcpBehavior.RECEIVE_FILE_NOW){
                    //devo aggiungere che, se la close e' stata mandata dal client, 
                    //significa che c'e' stato un errore
                    server.tell(new FileTransferResult(
                        MessageType.FILE_SENT_SUCCESSFULLY, fileName, readOrWrite), getSelf());
                    //else would be the case of file_busy or file_not_exists --> nothing to do
                } else if (behavior == TcpBehavior.AUTHORIZATION_REPLY_HANDLE){
                    if(reply == reply.AUTHORIZATION_GRANTED){
                        //The file exists but I wasn't able to open the file.
                        System.out.println("[simplHandler]: ho detto al server che può liberare il file\n");
                        server.tell(new FileTransferResult(MessageType.FILE_NO_MORE_BUSY, fileName, readOrWrite), 
                            getSelf());
                    }
                    //else if file doesn't exists or it's busy, I have to do nothings
                } 
            }
            getContext().stop(getSelf());
        }
    }
}