/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ClusterListenerActor;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;

/**
 *
 * @author francescop
 */
public class EndModify implements Serializable{
    String fileName;
    List<String> tags;
    long fileByteSize;
    //BigInteger ownerId;

    public EndModify(String fileName, List<String> tags, long fileByteSize /*BigInteger ownerId*/) {
        this.fileName = fileName;
        this.tags = tags;
        this.fileByteSize = fileByteSize;
        //this.ownerId = ownerId;
    }

    public String getFileName() {
        return fileName;
    }

    public List<String> getTags() {
        return tags;
    }

    /*public BigInteger getOwnerId() {
        return ownerId;
    }*/

    public long getFileByteSize() {
        return fileByteSize;
    }
    
}
