/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ClusterListenerActor;

import akka.cluster.Member;
import java.math.BigInteger;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 *
 * @author francescop
 */
public class HashMembersData {
    private TreeMap<BigInteger,Member> memberList;
    public HashMembersData(){
        memberList = new TreeMap();
    }
    
    //Insert a new member with the passed values hash of member address (id or key)
    //and the Member whose refeer
    public Member newMember(BigInteger memberId, Member member){
        return memberList.put(memberId,member);
    }
    //Get the Member associated to the id
    public Member getMemberById(BigInteger memberId){
        return memberList.get(memberId);
    }
    //Get Member for the least key greater than or equal to the given key
    //a.k.a. the responsible for that id
    public Member getResponsibleMemberById(BigInteger memberId){
        Entry<BigInteger,Member> entry = memberList.ceilingEntry(memberId);
        if(entry == null){
            entry = memberList.firstEntry();
        }
        return entry.getValue();
    }
    public BigInteger getResponsibleById(BigInteger memberId){
        Entry<BigInteger,Member> entry = memberList.ceilingEntry(memberId);
        if(entry == null){
            entry = memberList.firstEntry();
        }
        return entry.getKey();
    }
    
    //Removes the mapping for this id from this TreeMap if present.
    public Member deleteMemberById(BigInteger memberId){
        return memberList.remove(memberId);
    }
    
    //returns predecessor id
    public BigInteger getSuccessorById(BigInteger memberId){
        Entry<BigInteger,Member> entry = memberList.higherEntry(memberId);
        if(entry == null){
            entry = memberList.firstEntry();
        }
        return entry.getKey();
    }
    
    
    @Override
    public String toString(){
        return memberList.toString();
    }
}
