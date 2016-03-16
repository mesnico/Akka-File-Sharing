/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ClusterListenerActor;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author nicky
 */
class FileInfoElement{
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
}

public class FileInfoDistributedTable {
    HashMap<String,List<FileInfoElement>> fileInfo;
    public FileInfoDistributedTable(){
        fileInfo = new HashMap();
    }
    
    void add(String tag, String fileName, BigInteger ownerId){
        if(!fileInfo.containsKey(tag)){
            fileInfo.put(tag, new LinkedList());
        }
        fileInfo.get(tag).add(new FileInfoElement(fileName,ownerId));
    }
    
    boolean removeByTagAndName(String tag, String fileName){
        if(fileInfo.containsKey(tag)){
            for(FileInfoElement f : fileInfo.get(tag)){
                if(f.getFileName().equals(fileName)){
                    return fileInfo.get(tag).remove(f);
                }
            }
        }
        return false;
    }
    
    boolean updateOwner(String tag, String fileName, BigInteger newOwnerId){
        if(fileInfo.containsKey(tag)){
            for(FileInfoElement f : fileInfo.get(tag)){
                if(f.getFileName().equals(fileName)){
                    f.setOwnerId(newOwnerId);
                    return true;
                }
            }
        }
        return false;
    }
    
    List<FileInfoElement> getByTag(String tag){
        return fileInfo.get(tag);
    }
    
}
