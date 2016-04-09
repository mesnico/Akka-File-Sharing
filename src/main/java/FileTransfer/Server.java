import java.net.InetSocketAddress;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.io.Tcp;
import akka.io.Tcp.Bound;
import akka.io.Tcp.CommandFailed;
import akka.io.Tcp.Connected;
import akka.io.TcpMessage;
import java.net.InetAddress;


public class Server extends UntypedActor {
    final ActorRef clusterListener;
    
    public Server(ActorRef clusterListener) {
        this.clusterListener = clusterListener;
    }

    @Override
    public void preStart() throws Exception {
        final ActorRef tcpManager = Tcp.get(getContext().system()).manager();
        tcpManager.tell(TcpMessage.bind(
                    getSelf(), 
                    new InetSocketAddress(InetAddress.getLocalHost(), 5678), 
                    100)
                , getSelf());
    }
     
    // --------------------------------- //
    // ---- SERVER GENERAL BEHAVIOR ---- //
    // --------------------------------- //
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
        
        // --- AUTHORIZATION REQUEST --- //
        else if (msg instanceof AuthorizationRequest){
            System.out.printf("[server %s]\n", InetAddress.getLocalHost().toString());
            AuthorizationRequest receivedRequest = (AuthorizationRequest)msg;
            System.out.printf("Ho ricevuto una richiesta di %s sul file %s.\n"
                    + "Facciamo che è libero.\n",
                    receivedRequest.getModifier(),receivedRequest.getFileName());
            AuthorizationReply rensponseToSend = new AuthorizationReply(EnumAuthorizationReply.AUTHORIZATION_GRANTED);
            getSender().tell(rensponseToSend, getSelf());   
        } 
        
        // --- FILE TRANSFER RESULT --- //
        else if (msg instanceof FileTransferResult){
        FileTransferResult messageToSent = (FileTransferResult)msg;
        System.out.printf("[server %s]: ho ricevuto il seguente messaggio:\n"
                + "tipo del messaggio: %s\n"
                + "nome del file: %s\n"
                + "modificatore d'accesso: %s\n\n", 
                InetAddress.getLocalHost().toString(), messageToSent.getMessageType(), 
                messageToSent.getFileName(), messageToSent.getFileModifier());
        }
    }
}





