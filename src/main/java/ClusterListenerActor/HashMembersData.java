/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ClusterListenerActor;

import akka.cluster.Member;
import java.math.BigInteger;
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
    public Member getResponsibleById(BigInteger memberId){
        return memberList.ceilingEntry(memberId).getValue();
    }
    //Removes the mapping for this id from this TreeMap if present.
    public Member deleteMemberById(BigInteger memberId){
        return memberList.remove(memberId);
    }
    
    @Override
    public String toString(){
        return memberList.toString();
    }
}
