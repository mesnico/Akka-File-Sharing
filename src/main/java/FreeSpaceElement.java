
import akka.cluster.Member;
import java.io.Serializable;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author nicky
 */
public class FreeSpaceElement implements Serializable {
    Member member;
    long freeByteSpace;
    
    public FreeSpaceElement(Member member, long freeByteSpace){
        this.member = member;
        this.freeByteSpace = freeByteSpace;
    }
    
    public Member getMember(){
        return member;
    }
    
    public long getFreeByteSpace(){
        return freeByteSpace;
    }
    
    @Override
    public String toString(){
        return member.address()+" has "+freeByteSpace+" bytes free";
    }
}
