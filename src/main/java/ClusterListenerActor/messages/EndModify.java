/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ClusterListenerActor.messages;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;

/**
 *
 * @author francescop
 */
public class EndModify implements Serializable{
    String fileName;
    long fileByteSize;

    public EndModify(String fileName, long fileByteSize) {
        this.fileName = fileName;
        this.fileByteSize = fileByteSize;
        //this.ownerId = ownerId;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileByteSize() {
        return fileByteSize;
    }
    
}
