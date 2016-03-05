
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
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Consumer;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author nicky
 */
public class ClusterListener extends UntypedActor {

    LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    Cluster cluster = Cluster.get(getContext().system());
    TreeMap<BigInteger, Member> membersMap;
    PriorityQueue<FreeSpaceElement> membersFreeSpace;
    String localAddress;
    
    final long myFreeSpace = new Random().nextLong();    //THIS IS GENERATED INTERNALLY BUT IT SHOULD NOT (should be taken from mine file table)
    
    public ClusterListener(String port){
        //transforms the local reference to remote reference to uniformate hash accesses in case of remote actor
        Address mineAddress = getSelf().path().address();
        localAddress = (mineAddress.hasLocalScope()) ? mineAddress.hostPort()+"@127.0.0.1:"+port : mineAddress.hostPort();
        
        //construct the members map
        membersMap = new TreeMap();
         
        //construct the free space priority queue
        membersFreeSpace = new PriorityQueue<FreeSpaceElement>(10,new Comparator<FreeSpaceElement>(){
            @Override
            public int compare(FreeSpaceElement o1, FreeSpaceElement o2){
                return (int)(o1.getFreeByteSpace() - o2.getFreeByteSpace());
            }
        });
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
            MemberUp mUp = (MemberUp) message;
            log.info("{} -->Member is Up: {}", localAddress, mUp.member().address().hostPort());

            //inserts the new member in the map
            membersMap.put(computeMemberHash(mUp.member().address().hostPort()), mUp.member());
            
            System.out.println("----"+membersMap);
            
            //send my free space to the new arrived member
            getContext().actorSelection(mUp.member().address() + "/user/clusterListener")
                    .tell(new FreeSpaceElement(localAddress,myFreeSpace), getSelf());

        } else if (message instanceof UnreachableMember) {
            UnreachableMember mUnreachable = (UnreachableMember) message;
            log.info("Member detected as unreachable: {}", mUnreachable.member());

        } else if (message instanceof MemberRemoved) {
            MemberRemoved mRemoved = (MemberRemoved) message;
            log.info("Member is Removed: {}", mRemoved.member());
            
            if(mRemoved.previousStatus()==MemberStatus.down()){
                //the member was removed because crashed
            } else {
                //the member shutted down gracefully
            }
            
            //in any case, the member is removed from the local structure
            membersMap.remove(computeMemberHash(mRemoved.member().address().hostPort()));
            
            //the member is also removed from the free space queue
            //the only way is to search it by member address and to delete it.
            for(FreeSpaceElement e : membersFreeSpace){
                if(e.getMemberAddress().equals(mRemoved.member().address().hostPort())){
                    membersFreeSpace.remove(e);
                }
            }
            
            System.out.println("----"+membersMap+"\n----"+membersFreeSpace);
            
        //message sent when the new member arrives in the cluster. The map has to be immediately filled with the current state
        } else if (message instanceof CurrentClusterState) {
            CurrentClusterState state = (CurrentClusterState) message;
            for (Member member : state.getMembers()) {
                if (member.status().equals(MemberStatus.up())) {
                    membersMap.put(computeMemberHash(member.address().hostPort()),member);
                }
            }
            
        } else if (message instanceof FreeSpaceElement){
            //insert the received information about the free space in the current priority queue.
            FreeSpaceElement receivedFreeSpace = (FreeSpaceElement) message;
            membersFreeSpace.add(receivedFreeSpace);
            log.info("--- Received free space: {}",receivedFreeSpace);

        } else if (message instanceof MemberEvent) {
            // ignore

        } else {
            unhandled(message);
        }
    }

    private BigInteger computeMemberHash(String memberAddress) {
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
}
