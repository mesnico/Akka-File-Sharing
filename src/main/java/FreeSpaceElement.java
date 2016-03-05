
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
    String memberAddress;
    long freeByteSpace;
    
    public FreeSpaceElement(String memberAddress, long freeByteSpace){
        this.memberAddress = memberAddress;
        this.freeByteSpace = freeByteSpace;
    }
    
    public String getMemberAddress(){
        return memberAddress;
    }
    
    public long getFreeByteSpace(){
        return freeByteSpace;
    }
    
    @Override
    public String toString(){
        return memberAddress+" has "+freeByteSpace+" bytes free";
    }
}
