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

        } else if (message instanceof NewFileCreation) {//this message have to be generated by the event of creating a new file
            //I have just created a new file. Distribute info among other nodes
            NewFileCreation mNewFileCreation = (NewFileCreation) message;
            for(String tag : mNewFileCreation.getTags()){
                Member responsible = membersMap.getResponsibleById(computeId(tag));
                getContext().actorSelection(responsible.address() + "/user/clusterListener")
                    .tell(new AddTag(tag,mNewFileCreation.getFileName(),mNewFileCreation.getOwnerId()), getSelf());
            }
            //Also the file name information has to be stored as like as happens for other tags
            String fileName = mNewFileCreation.getFileName();
            Member responsible = membersMap.getResponsibleById(computeId(fileName));
                getContext().actorSelection(responsible.address() + "/user/clusterListener")
                    .tell(new AddTag(fileName,mNewFileCreation.getFileName(),mNewFileCreation.getOwnerId()), getSelf());

        } else if (message instanceof AddTag) {
            //Receved a information for wich I'm the responsible
            AddTag mAddTag = (AddTag) message;
            infoTable.add(mAddTag.getTag(), mAddTag.getFileName(), mAddTag.getOwnerId());

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
