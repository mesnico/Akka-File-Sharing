package ClusterListenerActor;

import ClusterListenerActor.messages.InitiateShutdown;
import ClusterListenerActor.messages.EndModify;
import ClusterListenerActor.messages.FileInfoTransfer;
import ClusterListenerActor.messages.FreeSpaceSpread;
import ClusterListenerActor.messages.CreationRequest;
import ClusterListenerActor.messages.AddTag;
import ClusterListenerActor.messages.CreationResponse;
import ClusterListenerActor.messages.Shutdown;
import Startup.WatchMe;
import akka.actor.Address;
import akka.actor.PoisonPill;
import akka.actor.UntypedActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.ClusterEvent.CurrentClusterState;
import akka.cluster.ClusterEvent.MemberEvent;
import akka.cluster.ClusterEvent.MemberRemoved;
import akka.cluster.ClusterEvent.MemberUp;
import akka.cluster.ClusterEvent.UnreachableMember;
import akka.cluster.Member;
import akka.cluster.MemberStatus;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import scala.concurrent.duration.Duration;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author nicky
 */
public class ClusterListenerActor extends UntypedActor {

    LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    Cluster cluster = Cluster.get(getContext().system());
    HashMembersData membersMap;
    FreeSpaceMembersData membersFreeSpace;
    FileInfoDistributedTable infoTable;
    String localAddress;
    
    int clusterSystemPort;
    final long myFreeSpace = new Random().nextLong();    //THIS IS GENERATED INTERNALLY BUT IT SHOULD NOT (should be taken from mine file table)
    
    public ClusterListenerActor(int basePort){
        //cluster port is that specified from the user; instead the client port (for handling of the files) is opened in clusterPort + 1
        this.clusterSystemPort = basePort;
        
        //transforms the local reference to remote reference to uniformate hash accesses in case of remote actor
        Address mineAddress = getSelf().path().address();
        localAddress = getAddress(mineAddress);
        
        //construct the members map and member free space map
        membersMap = new HashMembersData();
        membersFreeSpace = new FreeSpaceMembersData();
        infoTable = new FileInfoDistributedTable();
    }

    //subscribe to cluster changes
    @Override
    public void preStart() {
        //#subscribe
        cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(),
                MemberEvent.class, UnreachableMember.class);
        //#subscribe
        
        //subscrive to to the soul reaper
        getContext().actorSelection("akka.tcp://ClusterSystem@127.0.0.1:"+clusterSystemPort+"/user/soulReaper")
                .tell(new WatchMe(), getSelf());
        
        //TO DELETEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEeeEEEEEEEEEEEEEEEEEEEEEEEEEe
        //after 15 seconds the actor creates a new file
        getContext().system().scheduler().scheduleOnce(Duration.create(15000, TimeUnit.MILLISECONDS),
            new Runnable() {
                @Override
                public void run() {
                    Member responsibleMember = membersMap.getResponsibleMemberById(HashUtilities.computeId("prova"+clusterSystemPort+".txt"));
                    getContext().actorSelection(responsibleMember.address() + "/user/clusterListener")
                        .tell(new CreationRequest("prova"+clusterSystemPort+".txt"), getSelf());
                }
            }, getContext().system().dispatcher());
        
        //after 20 seconds the actor shutdown
        /*getContext().system().scheduler().scheduleOnce(Duration.create(20000, TimeUnit.MILLISECONDS),
            new Runnable() {
                @Override
                public void run() {
                        getSelf().tell(new ClusterShutdown(), getSelf());
                }
            }, getContext().system().dispatcher());
        */

    }

    //re-subscribe when restart
    @Override
    public void postStop() {
        cluster.unsubscribe(getSelf());
    }

    @Override
    public void onReceive(Object message) {
        if (message instanceof MemberUp) {
            MemberUp mMemberUp = (MemberUp) message;
            log.info("{} -->Member is Up: {}", localAddress, getAddress(mMemberUp.member().address()));

            //inserts the new member in the map
            membersMap.newMember(HashUtilities.computeId(getAddress(mMemberUp.member().address())), mMemberUp.member());    //IS IT RIGHT?
            log.info("new Member inserted in membersMap: {}",mMemberUp.member());
            log.debug("Current membersMap status: {} ",membersMap);
            
            //transfer control of the right tags to the new node, if it is my predecessor.
            //if it is my predecessor
            if(HashUtilities.computeId(getAddress(getSelf().path().address())).compareTo(
                    membersMap.getSuccessorById(HashUtilities.computeId(getAddress(mMemberUp.member().address()))))==0){
                //collect the infoTable entries to be passed to the arrived node
                FileInfoTransfer fit = infoTable.buildFileInfoTransfer(membersMap,HashUtilities.computeId(getAddress(getSelf().path().address()))); 
                //send the infos to the new responsible
                getContext().actorSelection(mMemberUp.member().address() + "/user/clusterListener")
                        .tell(fit, getSelf());
                log.debug("Sending out file info: {}",fit.toString());
            }
           
            //send my free space to the new arrived member
            getContext().actorSelection(mMemberUp.member().address() + "/user/clusterListener")
                    .tell(new FreeSpaceSpread(myFreeSpace), getSelf());

        } else if (message instanceof UnreachableMember) {
            UnreachableMember mMemberUnreachable = (UnreachableMember) message;
            log.info("Member detected as unreachable: {}", mMemberUnreachable.member());

        } else if (message instanceof MemberRemoved) {
            MemberRemoved mMemberRemoved = (MemberRemoved) message;
            log.info("Member is Removed: {}", mMemberRemoved.member());
            
            if(mMemberRemoved.previousStatus()==MemberStatus.down()){
                //the member was removed because crashed
            } else {
                //the member shutted down gracefully
            }
            
            //in any case, the member is removed from the local structure
            Member removed = membersMap.deleteMemberById(HashUtilities.computeId(getAddress(mMemberRemoved.member().address())));
            if(removed == null){
                log.error("Member {} does not exist in membersMap!!",mMemberRemoved.member());
            } else {
                log.info("Member removed in membersMap: {}",mMemberRemoved.member());
                log.debug("Current membersMap status: {} ",membersMap);
            }
            
            //the member is also removed from the free space queue
            //the only way is to search it by member address and to delete it.
            boolean exists = membersFreeSpace.deleteByMember(HashUtilities.computeId(getAddress(mMemberRemoved.member().address())));
            if(!exists){
                log.error("Member {} does not exist in membersFreeSpace!!",mMemberRemoved.member());
            } else {
                log.info("Member removed in membersFreeSpace: {}",mMemberRemoved.member());
                log.debug("Current membersFreeSpace status: {} ",membersFreeSpace);
            }
        //message sent when the new member arrives in the cluster. The map has to be immediately filled with the current state
        } else if (message instanceof CurrentClusterState) {
            CurrentClusterState state = (CurrentClusterState) message;
            for (Member member : state.getMembers()) {
                if (member.status().equals(MemberStatus.up())) {
                    membersMap.newMember(HashUtilities.computeId(member.address().hostPort()),member);
                }
            }
            log.info("membersMap initialized: {}",membersMap);
            
        } else if (message instanceof FreeSpaceSpread){
            //insert the received information about the free space in the current priority queue.
            FreeSpaceSpread receivedFreeSpace = (FreeSpaceSpread) message;
            System.out.println(getSelf() + " " + getSender());
            membersFreeSpace.newMember(HashUtilities.computeId(getAddress(getSender().path().address())), receivedFreeSpace.getFreeByteSpace());  //IS IT RIGHT?
            log.info("Added {} with {} bytes free",getAddress(getSender().path().address()),receivedFreeSpace.getFreeByteSpace());
            log.debug("Free space infos: {}",membersFreeSpace);

        } else if (message instanceof EndModify) {//this message is generated at the end of the modify operation, to begine the load distribution
            //I have just created a new file.
            EndModify mNewFileCreation = (EndModify) message;
            //load distribution
            long biggestFreeSpace = membersFreeSpace.getHighestFreeSpace();
            if(biggestFreeSpace < mNewFileCreation.getFileByteSize()){
                //oh no! the file cannot be delivered
                //smaller size please
            } else{
                BigInteger ownerId = membersFreeSpace.getHighestFreeSpaceMember();

                //Distribute info among other nodes
                for(String tag : mNewFileCreation.getTags()){
                    Member responsible = membersMap.getResponsibleMemberById(HashUtilities.computeId(tag));
                    getContext().actorSelection(responsible.address() + "/user/clusterListener")
                        .tell(new AddTag(tag,mNewFileCreation.getFileName(),ownerId), getSelf());
                }
                /*this had to be done durig the "Check of the name"
                * instad now I have to set the new owner and new file size

                //Also the file name information has to be stored as like as happens for other tags
                String fileName = mNewFileCreation.getFileName();
                Member responsible = membersMap.getResponsibleById(HashUtilities.computeId(fileName));
                    getContext().actorSelection(responsible.address() + "/user/clusterListener")
                        .tell(new AddTag(fileName,mNewFileCreation.getFileName(),mNewFileCreation.getOwnerId()), getSelf());
                */
                
            }
        } else if (message instanceof CreationRequest){
            //i am the responsible for the new filename tag. I have to add it to my FileInfoDistributedTable, only if it does not exist yet.
            String fileName = ((CreationRequest)message).getFileName();
            boolean success = infoTable.testAndSet(fileName, fileName, HashUtilities.computeId(getAddress(getSender().path().address())));
            getSender().tell(new CreationResponse(success), getSelf());
            
            log.debug("Received a creation request. \n Success:{}\nCurrent info table is: {}",success,infoTable.toString());
            log.debug("tag id: {}",HashUtilities.computeId(fileName));
        } else if (message instanceof AddTag) {
            //Receved a information for wich I'm the responsible
            AddTag mAddTag = (AddTag) message;
            infoTable.updateTag(mAddTag.getTag(), mAddTag.getFileName(), mAddTag.getOwnerId());
        
        } else if (message instanceof FileInfoTransfer) {
            FileInfoTransfer infos = (FileInfoTransfer)message;
            
            //merge the arrived file informations into the local structure
            infoTable.mergeInfos(infos);
            log.info("Received File Infos: {}",infos.toString());
            log.debug("Current File Info Table: {}",infoTable.toString());
            
        } else if (message instanceof InitiateShutdown) {
            log.info("The system is going to shutdown!");
            //transfer all my infos to my successor node
            Member newInfoResponsable = membersMap.getSuccessorMemberById(
                    HashUtilities.computeId(getAddress(getSelf().path().address())));
            //prepare the fileinfo transfer message
            FileInfoTransfer fit = infoTable.buildFileInfoTransfer(membersMap,
                    HashUtilities.computeId(getAddress(newInfoResponsable.address())));
            //send the infos to the new responsible
            getContext().actorSelection(newInfoResponsable.address() + "/user/clusterListener")
                    .tell(fit, getSelf());
            
            getSelf().tell(new Shutdown(), getSelf());
        
        } else if (message instanceof Shutdown){
            //leave the cluster and stop the system
            cluster.leave(cluster.selfAddress());
            
            //send Poison Pill to me to kill myself
            getSelf().tell(PoisonPill.getInstance(), getSelf());
            
        } else if (message instanceof MemberEvent) {
            // ignore

        } else {
            unhandled(message);
        }
    }
    
    //returns a string containing the remote address
    private String getAddress(Address address){
        return (address.hasLocalScope()) ? address.hostPort()+"@127.0.0.1:"+clusterSystemPort : address.hostPort();
    }
}
