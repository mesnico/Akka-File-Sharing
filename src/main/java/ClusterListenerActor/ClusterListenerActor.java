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
        
        //construct the members map
        membersMap = new HashMembersData();
        membersFreeSpace = new FreeSpaceMembersData();
        
        /*
        //construct the free space priority queue
        membersFreeSpace = new PriorityQueue<FreeSpaceElement>(10,new Comparator<FreeSpaceElement>(){
            @Override
            public int compare(FreeSpaceElement o1, FreeSpaceElement o2){
                return (int)(o1.getFreeByteSpace() - o2.getFreeByteSpace());
            }
        });*/
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
            System.out.println("*member up: "+getAddress(mMemberUp.member().address()));
            //log.info("{} -->Member is Up: {}", localAddress, getAddress(mMemberUp.member().address()));

            //inserts the new member in the map
            membersMap.newMember(computeMemberID(getAddress(mMemberUp.member().address())), mMemberUp.member());    //IS IT RIGHT?
            System.out.println("*new Member inserted in membersMap");
            System.out.println("+"+membersMap.memberList);
           
            //send my free space to the new arrived member
            getContext().actorSelection(mMemberUp.member().address() + "/user/clusterListener")
                    .tell(new FreeSpaceSpread(myFreeSpace), getSelf());

        } else if (message instanceof UnreachableMember) {
            UnreachableMember mUnreachable = (UnreachableMember) message;
            //log.info("Member detected as unreachable: {}", mUnreachable.member());

        } else if (message instanceof MemberRemoved) {
            MemberRemoved mRemoved = (MemberRemoved) message;
            //log.info("Member is Removed: {}", mRemoved.member());
            
            if(mRemoved.previousStatus()==MemberStatus.down()){
                //the member was removed because crashed
            } else {
                //the member shutted down gracefully
            }
            
            //in any case, the member is removed from the local structure
            membersMap.deleteMemberById(computeMemberID(mRemoved.member().address().hostPort()));
            System.out.println("+"+membersMap.memberList);
            
            //the member is also removed from the free space queue
            //the only way is to search it by member address and to delete it.
            membersFreeSpace.deleteByMember(computeMemberID(getAddress(mRemoved.member().address())));
            System.out.println("+"+membersFreeSpace.freeSpace);
            /*
            for(FreeSpaceElement e : membersFreeSpace){
                if(e.getMemberID() == computeMemberID(getAddress(mRemoved.member().address()))){  //IS IT RIGHT?
                    membersFreeSpace.remove(e);
                }
            }
            
            System.out.println("----"+membersMap+"\n----"+membersFreeSpace);*/
            
        //message sent when the new member arrives in the cluster. The map has to be immediately filled with the current state
        } else if (message instanceof CurrentClusterState) {
            CurrentClusterState state = (CurrentClusterState) message;
            for (Member member : state.getMembers()) {
                if (member.status().equals(MemberStatus.up())) {
                    membersMap.newMember(computeMemberID(member.address().hostPort()),member);
                    System.out.println("+"+membersMap.memberList);
                }
            }
            
        } else if (message instanceof FreeSpaceSpread){
            System.out.println("*FreeSpaceSpread context");
            //insert the received information about the free space in the current priority queue.
            FreeSpaceSpread receivedFreeSpace = (FreeSpaceSpread) message;
            System.out.println(getSelf() + " " + getSender());
            membersFreeSpace.newMember(computeMemberID(getAddress(getSender().path().address())), receivedFreeSpace.getFreeByteSpace());  //IS IT RIGHT?
            System.out.println("+"+membersFreeSpace.freeSpace);
            System.out.println("**"+getAddress(getSender().path().address())+" told me "+receivedFreeSpace.getFreeByteSpace());
            //log.info("Free Space Infos: {}",membersFreeSpace);

        } else if (message instanceof MemberEvent) {
            // ignore

        } else {
            unhandled(message);
        }
    }

    //compute the member ID starting from the member address using the hash function
    private BigInteger computeMemberID(String memberAddress) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.reset();
            md.update(memberAddress.getBytes("UTF-8"));
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
