package FileManagerActor;


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
public class FreeSpaceRequest implements Serializable{
    long freeByteSpace;
    public FreeSpaceRequest(long freeByteSpace){
        this.freeByteSpace = freeByteSpace;
    }
    
    public void setFreeByteSpace(long freeByteSpace){
        this.freeByteSpace = freeByteSpace;
    }
    
    public long getFreeByteSpace(){
        return freeByteSpace;
    }
}
