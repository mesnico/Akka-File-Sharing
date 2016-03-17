/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ClusterListenerActor;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 *
 * @author francescop
 */
//class used to memorize freespace elements, built of memberID + freeSpace (in bytes)
class FreeSpaceElement implements Serializable {
    BigInteger memberID;
    long freeByteSpace;
    
    public FreeSpaceElement(BigInteger memberID, long freeByteSpace){
        this.memberID = memberID;
        this.freeByteSpace = freeByteSpace;
    }
    
    public BigInteger getMemberID(){
        return memberID;
    }
    
    public long getFreeByteSpace(){
        return freeByteSpace;
    }
    public void setFreeByteSpace(long freeByteSpace) {
        this.freeByteSpace = freeByteSpace;
    }
    
    @Override
    public String toString(){
        return memberID+" has "+freeByteSpace+" bytes free";
    }
}

public class FreeSpaceMembersData {
    
    private PriorityQueue<FreeSpaceElement> freeSpace;
    
    public FreeSpaceMembersData(){
        freeSpace = new PriorityQueue<FreeSpaceElement>(10,new Comparator<FreeSpaceElement>(){
            @Override
            public int compare(FreeSpaceElement o1, FreeSpaceElement o2){
                return (int)(o1.getFreeByteSpace() - o2.getFreeByteSpace());
            }
        });
    }
    
    //Performs the load balancing to obtain the memberId of the member with
    //largest free space
    public BigInteger getHighestFreeSpaceMember(){
        return freeSpace.peek().getMemberID();
    }
    //Find the entry corresponding to the member id passed as parameter
    public boolean deleteByMember(BigInteger memberId){
        for(FreeSpaceElement e : freeSpace)
            if(e.getMemberID().equals(memberId))
                return freeSpace.remove(e);
        return false;
    }
    //Add a new entry for the new member
    public void newMember(BigInteger memberId, long space){
        freeSpace.add(new FreeSpaceElement(memberId, space));
    }
    //Find the member and replace it with a another with the updated free space
    //return the previous free space or -1 otherwise
    public long updateMemberFreeSpace(BigInteger memberId, long newSpace){
        for(FreeSpaceElement e : freeSpace)
            if(e.getMemberID() == memberId){
                freeSpace.add(new FreeSpaceElement(memberId, newSpace));
                freeSpace.remove(e);
                return e.getFreeByteSpace();
            }
        return -1;
    }
    
    @Override
    public String toString(){
        return freeSpace.toString();
    }
}
