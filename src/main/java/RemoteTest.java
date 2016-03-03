
import akka.actor.ActorIdentity;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Identify;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import akka.japi.Procedure;
import static java.util.concurrent.TimeUnit.SECONDS;
import scala.concurrent.duration.Duration;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Nicola
 */
public class RemoteTest {
    public static void main(String[] args){
        if(args.length != 3){
            System.out.println("Needed 3 arguments");
            System.exit(1);
        }
        String localActorName = args[0];
        String remoteActorPath = args[1];
        boolean sendFirst = (args[2].equals("true"));
        
        System.out.println("Started with parameters: "+localActorName+", "+remoteActorPath+", "+sendFirst);
        
        RemoteTest t = new RemoteTest(localActorName,remoteActorPath,sendFirst);
    }
    
    public static class MyMessage implements java.io.Serializable{
        private int msg;
        public MyMessage(int msg){
            this.msg = msg;
        }
        public int getMessage(){
            return msg;
        }
    }
    
    public static class Client extends UntypedActor{
        private String path;
        private boolean sendFirst;
        public Client(String path, boolean sendFirst){
            this.path = path;
            this.sendFirst = sendFirst;
        }
        private ActorRef remote;
        private ActorRef sender;
        int valueReceived = 0;
        
        @Override
        public void onReceive(Object message){
            if (message instanceof ActorIdentity){
                //messaggio di risposta alla domanda: chi sei tu dall'altra parte?
                remote = ((ActorIdentity)message).getRef();
                if(remote==null){
                    //non c'è nessuno dall'altra parte
                    System.out.println("Remote actor not available: " + path);
                } else {
                    //posso iniziare a parlare con il mio interlocutore
                    //controllo il mio interlocutore per sapere se è ancora vivo mentre gli sto parlando
                    //e divento un attore attivo (non più in stato di polling)
                    getContext().watch(remote);
                    if(sendFirst) remote.tell(new MyMessage(0),getSelf());
                    
                    System.out.println("Becoming active");
                    getContext().become(active);
                }
                
            } else if (message instanceof ReceiveTimeout){
                //timeout scaduto: reinvio la richiesta di indentificazione
                getContext().actorSelection(path).tell(new Identify(path),getSelf());
                getContext().system().scheduler().scheduleOnce(Duration.create(6, SECONDS),new Runnable(){
                    @Override
                    public void run(){
                        getSelf().tell(ReceiveTimeout.getInstance(),getSelf());
                    }
                }, getContext().dispatcher());
                
            } else {
                unhandled(message);
            }
        }
        
        Procedure<Object> active = new Procedure<Object>(){        
            @Override
            public void apply(Object message){
                if(message instanceof MyMessage){
                    sender = getSender();
                    valueReceived = ((MyMessage)message).getMessage();
                    
                    if(valueReceived>=20){
                        sender.tell(PoisonPill.getInstance(), getSelf());
                        getSelf().tell(PoisonPill.getInstance(), getSelf());
                        //getContext().system().terminate();
                    }
                    System.out.println("Messaggio ricevuto da "+sender.path().name()+": "+((MyMessage)message).getMessage());
                    
                    //wait 2 seconds then resend the message to the sender increased by 1
                    getContext().system().scheduler().scheduleOnce(Duration.create(2, SECONDS),new Runnable(){
                        @Override
                        public void run(){
                            sender.tell(new MyMessage(valueReceived+1), getSelf());
                        }
                    }, getContext().dispatcher());
                    
                    
                } else if(message instanceof ReceiveTimeout){
                    //questo messaggio arriva da me stesso, a parte quando arriva prima che io sia diventato attivo
                    //nel qual caso sender è null, quindi questo evento viene ignorato
                    
                    
                } else if(message instanceof Terminated){
                    //il mio interlocutore è morto.. torno nello stato di polling
                    System.out.println(sender.path().name()+" is DEAD :(");
                    getContext().unbecome();
                    getSelf().tell(ReceiveTimeout.getInstance(), getSelf());
                } else {
                    unhandled(message);
                }
            }
        };
    }
    public RemoteTest(String localActorName, String remoteActorPath, boolean sendFirst){
        final String remotePath = "akka.tcp://RemoteTestSystem@"+remoteActorPath;
        
        //creo l'actor system
        final ActorSystem system = ActorSystem.create("RemoteTestSystem");
        
        final ActorRef localClient = system.actorOf(Props.create(Client.class,remotePath,sendFirst),localActorName);
        localClient.tell(ReceiveTimeout.getInstance(), localClient);
    }
}
