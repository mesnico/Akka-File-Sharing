package ClusterListenerActor;


import akka.cluster.Member;
import java.io.Serializable;
import java.math.BigInteger;

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
    
    @Override
    public String toString(){
        return memberID+" has "+freeByteSpace+" bytes free";
    }
}
