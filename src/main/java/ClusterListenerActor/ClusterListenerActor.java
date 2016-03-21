package ClusterListenerActor;

import akka.actor.Address;
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
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

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
    
    int clusterPort, clientPort;
    final long myFreeSpace = new Random().nextLong();    //THIS IS GENERATED INTERNALLY BUT IT SHOULD NOT (should be taken from mine file table)
    
    public ClusterListenerActor(int clusterPort){
        //cluster port is that specified from the user; instead the client port (for handling of the files) is opened in clusterPort + 1
        this.clusterPort = clusterPort;
        this.clientPort = clusterPort + 1;
        
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
            membersMap.newMember(computeId(getAddress(mMemberUp.member().address())), mMemberUp.member());    //IS IT RIGHT?
            log.info("new Member inserted in membersMap: {}",mMemberUp.member());
            log.debug("Current membersMap status: {} ",membersMap);
            
            //transfer control of the right tags to the new node, if it is my predecessor.
            FileInfoTransfer fit = new FileInfoTransfer();
            
            //if it is my predecessor
            if(computeId(getAddress(getSelf().path().address())) == 
                    membersMap.getSuccessorById(computeId(getAddress(mMemberUp.member().address())))){
                //collect the infoTable entries to be passed to the arrived node
                for(String tag: infoTable.allTags()){
                    if(membersMap.getResponsibleById(computeId(tag)) !=
                            computeId(getAddress(getSelf().path().address()))){
                        fit.addEntry(tag,infoTable.removeByTag(tag));
                    }
                }
                //send the infos to the new responsible
                getContext().actorSelection(mMemberUp.member().address() + "/user/clusterListener")
                        .tell(fit, getSelf());
            }
           
            //send my free space to the new arrived member
            getContext().actorSelection(mMemberUp.member().address() + "/user/clusterListener")
                    .tell(new FreeSpaceSpread(myFreeSpace), getSelf());
            
            
            //TO DELETEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEeeEEEEEEEEEEEEEEEEEEEEEEEEEe
            Member responsibleMember = membersMap.getResponsibleMemberById(computeId("prova"+clusterPort+".txt"));
            getContext().actorSelection(responsibleMember.address() + "/user/clusterListener")
                    .tell(new CreationRequest("prova"+clusterPort+".txt"), getSelf());

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
            Member removed = membersMap.deleteMemberById(computeId(getAddress(mMemberRemoved.member().address())));
            if(removed == null){
                log.error("Member {} does not exist in membersMap!!",mMemberRemoved.member());
            } else {
                log.info("Member removed in membersMap: {}",mMemberRemoved.member());
                log.debug("Current membersMap status: {} ",membersMap);
            }
            
            //the member is also removed from the free space queue
            //the only way is to search it by member address and to delete it.
            boolean exists = membersFreeSpace.deleteByMember(computeId(getAddress(mMemberRemoved.member().address())));
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
                    membersMap.newMember(computeId(member.address().hostPort()),member);
                }
            }
            log.info("membersMap initialized: {}",membersMap);
            
        } else if (message instanceof FreeSpaceSpread){
            //insert the received information about the free space in the current priority queue.
            FreeSpaceSpread receivedFreeSpace = (FreeSpaceSpread) message;
            System.out.println(getSelf() + " " + getSender());
            membersFreeSpace.newMember(computeId(getAddress(getSender().path().address())), receivedFreeSpace.getFreeByteSpace());  //IS IT RIGHT?
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
                    Member responsible = membersMap.getResponsibleMemberById(computeId(tag));
                    getContext().actorSelection(responsible.address() + "/user/clusterListener")
                        .tell(new AddTag(tag,mNewFileCreation.getFileName(),ownerId), getSelf());
                }
                /*this had to be done durig the "Check of the name"
                * instad now I have to set the new owner and new file size

                //Also the file name information has to be stored as like as happens for other tags
                String fileName = mNewFileCreation.getFileName();
                Member responsible = membersMap.getResponsibleById(computeId(fileName));
                    getContext().actorSelection(responsible.address() + "/user/clusterListener")
                        .tell(new AddTag(fileName,mNewFileCreation.getFileName(),mNewFileCreation.getOwnerId()), getSelf());
                */
                
            }
        } else if (message instanceof CreationRequest){
            //i am the responsible for the new filename tag. I have to add it to my FileInfoDistributedTable, only if it does not exist yet.
            String fileName = ((CreationRequest)message).getFileName();
            boolean success = infoTable.testAndSet(fileName, fileName, computeId(getAddress(getSender().path().address())));
            //getSender().tell(new CreationResponse(success), getSelf());
            log.debug("Received a creation request. \n Success:{}\nCurrent info table is: {}",success,infoTable.toString());
            log.debug("tag id: {}",computeId(fileName));
        } else if (message instanceof AddTag) {
            //Receved a information for wich I'm the responsible
            AddTag mAddTag = (AddTag) message;
            infoTable.updateTag(mAddTag.getTag(), mAddTag.getFileName(), mAddTag.getOwnerId());
        
        } else if (message instanceof FileInfoTransfer) {
            FileInfoTransfer infos = (FileInfoTransfer)message;
            
            //merge the arrived file informations into the local structure
            infoTable.mergeInfos(infos.getInfos());
            log.debug("Received Infos: {}",infos.getInfos().toString());
        } else if (message instanceof MemberEvent) {
            // ignore

        } else {
            unhandled(message);
        }
    }

    //compute the member ID starting from the member address using the hash function
    private BigInteger computeId(String inString) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.reset();
            md.update(inString.getBytes("UTF-8"));
            return new BigInteger(md.digest());
        } catch (NoSuchAlgorithmException e) {
            return BigInteger.ZERO;
        } catch (UnsupportedEncodingException e) {
            return BigInteger.ZERO;
        }
    }
    
    //returns a string containing the remote address
    private String getAddress(Address address){
        return (address.hasLocalScope()) ? address.hostPort()+"@127.0.0.1:"+clusterPort : address.hostPort();
    }
}
