/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ClusterListenerActor;

import java.io.Serializable;
import java.math.BigInteger;

/**
 *
 * @author nicky
 */
public class FileInfoElement implements Serializable{
    String fileName;
    BigInteger ownerId;
    
    public FileInfoElement(String fileName, BigInteger ownerId) {
        this.fileName = fileName;
        this.ownerId = ownerId;
    }
    
    public String getFileName() {
        return fileName;
    }

    public BigInteger getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(BigInteger ownerId) {
        this.ownerId = ownerId;
    }
    
    @Override
    public String toString(){
        return "fileName: "+fileName+"; ownerId: "+ownerId;
    }
}
