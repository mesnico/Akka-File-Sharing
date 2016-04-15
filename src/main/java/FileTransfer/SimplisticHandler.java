package FileTransfer;

import Startup.AddressResolver;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.UntypedActor;
import akka.io.Tcp;
import akka.io.Tcp.CommandFailed;
import akka.io.Tcp.Connected;
import akka.io.TcpMessage;
import akka.japi.Procedure;
import java.net.InetSocketAddress;

public class SimplisticHandler extends UntypedActor {
    ActorSelection myClusterListener;
    ActorSelection myServer;
    ActorSelection remoteServer;
    ActorRef interlocutor;
    ActorRef connectionHandler;
    Handshake handshake;
    int clusterSystemPort;
    int tcpPort;
    String remoteServerIP;
    
    // --------------------- //
    // ---- CONSTRUCTORS ---- //
    // --------------------- //
    public SimplisticHandler(int basePort) {
        this.clusterSystemPort = basePort;
        this.tcpPort = basePort + 1;
        handshake.setBehavior(Behavior.UNINITIALIZED);
    }
    
    public SimplisticHandler(int basePort, Handshake handshake, ActorRef connectionHandler) {
        this(basePort);
        this.connectionHandler = connectionHandler;
    }
    
    public SimplisticHandler(int basePort, ActorRef interlocutor) {
        this(basePort);
        this.interlocutor = interlocutor; //solo se si è passivi, il server ci passerà questo riferimento
    }    
    
    // ----------------------------- //
    // ---- ADDITIONAL FUNCTION ---- //
    // ----------------------------- //
    public void changeBehavior(){
    //ha senso mettere come argomento il connectionHandler?
        if(handshake.getBehavior() == Behavior.SEND){
            getContext().become(tcpSender(connectionHandler));  
        } else {
            getContext().become(tcpReceiver(connectionHandler)); 
        }
    }
    
    // ---------------------------- //
    // ---- INITIAL HANDSHAKES ---- //
    // ---------------------------- //
    @Override
    public void preStart() throws Exception {
        myServer = getContext().actorSelection("akka.tcp://ClusterSystem@"+AddressResolver.getMyIpAddress()+":"
                +clusterSystemPort+"/user/server");
        remoteServer = getContext().actorSelection("akka.tcp://ClusterSystem@"+remoteServerIP+":"
                +clusterSystemPort+"/user/server");
        myClusterListener = getContext().actorSelection("akka.tcp://ClusterSystem@"+AddressResolver.getMyIpAddress()+":"
                +clusterSystemPort+"/user/clusterListener");
        
        switch(handshake.getBehavior()){
            case SEND:
            case REQUEST:
                final ActorRef tcpManager = Tcp.get(getContext().system()).manager();
                InetSocketAddress remoteServerAddress = new InetSocketAddress(remoteServerIP, tcpPort);
                tcpManager.tell(TcpMessage.connect(remoteServerAddress), getSelf());
                break;
            case UNINITIALIZED:
                interlocutor.tell("ack", getSelf());
                break;     
        }
    }
    
    @Override
    public void onReceive(Object msg) {
        if (msg instanceof CommandFailed) {
            //informare la gui che l'operazione non e' andata a buon fine per sbloccarla (fileTransferResult)
            getContext().stop(getSelf());
        } else if (msg instanceof Connected) {
            connectionHandler = getSender();
            connectionHandler.tell(TcpMessage.register(getSelf()), getSelf());
        } else if (msg instanceof String){
            getSender().tell(handshake, getSelf());
            changeBehavior();
        } else if (msg instanceof Handshake){
            this.handshake = (Handshake)msg; //va bene come modo di scrivere o occorreva una funzione?
            changeBehavior();
        }
    }
    
    private Procedure<Object> tcpSender(final ActorRef connectionHandler) {
        return new Procedure<Object>() {
            @Override
            public void apply(Object msg) throws Exception {
                
            }
        };
    }
    
    private Procedure<Object> tcpReceiver(final ActorRef connectionHandler) {
        return new Procedure<Object>() {
            @Override
            public void apply(Object msg) throws Exception {
                
            }
        };
    }    
}





