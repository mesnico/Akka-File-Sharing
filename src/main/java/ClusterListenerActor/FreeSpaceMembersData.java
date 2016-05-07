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
            //the comparator needs to be hacked because I want the highest free space member to be on top of the queue
            @Override
            public int compare(FreeSpaceElement o1, FreeSpaceElement o2){
                int result;
                result = o2.getFreeByteSpace() > o1.getFreeByteSpace() ? 1 : 
                         o2.getFreeByteSpace() < o1.getFreeByteSpace() ? -1 :
                         0;
                return result;
            }
        });
    }
    
    //Find a member by memberId
    public FreeSpaceElement getFreeSpaceElement(BigInteger memberId){
        for(FreeSpaceElement e : freeSpace)
            if(e.getMemberID().equals(memberId)){
                return e;
            }
        return null;
    }
    
    //Performs the load balancing to obtain the memberId of the member with
    //largest free space
    public BigInteger getHighestFreeSpaceMember(){
        return freeSpace.peek().getMemberID();
    }
    
    //Return the highest free space
    public long getHighestFreeSpace(){
        return freeSpace.peek().getFreeByteSpace();
    }
    
    //Find the entry corresponding to the member id passed as parameter
    public boolean deleteByMember(BigInteger memberId){
        FreeSpaceElement toRemove = getFreeSpaceElement(memberId);
        return freeSpace.remove(toRemove);
    }
    
    //Find the member and replace it with a another with the updated free space
    //If the member is not present, it is created and returns false
    //otherwise, it returns true
    public boolean updateMemberFreeSpace(BigInteger memberId, long newSpace){
        FreeSpaceElement element = getFreeSpaceElement(memberId);
        if(element == null){
            freeSpace.add(new FreeSpaceElement(memberId,newSpace));
            return false;
        } else {
            //element.setFreeByteSpace(newSpace);
            freeSpace.remove(element);
            freeSpace.add(new FreeSpaceElement(memberId,newSpace));
            return true;
        }
    }
    
    @Override
    public String toString(){
        return freeSpace.toString();
    }
}
