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
import java.util.Map.Entry;
import java.util.Set;

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
    
    @Override
    public String toString(){
        return "fileName: "+fileName+"; ownerId: "+ownerId;
    }
}

public class FileInfoDistributedTable {
    HashMap<String,List<FileInfoElement>> fileInfo;
    public FileInfoDistributedTable(){
        fileInfo = new HashMap();
    }
    
    private void add(String tag, String fileName, BigInteger ownerId){
        if(!fileInfo.containsKey(tag)){
            fileInfo.put(tag, new LinkedList());
        }
        fileInfo.get(tag).add(new FileInfoElement(fileName,ownerId));
    }
    
    private FileInfoElement existsFile(String tag, String fileName){
        if(fileInfo.containsKey(tag)){
            for(FileInfoElement f : fileInfo.get(tag)){
                if(f.getFileName().equals(fileName)){
                    return f;
                }
            }
        }
        return null;
    }
    
    public List<FileInfoElement> removeByTag(String tag){
        return fileInfo.remove(tag);
    }
    
    public boolean removeByTagAndName(String tag, String fileName){
        FileInfoElement toDelete = existsFile(tag,fileName);
        if(toDelete == null) 
            return false;
        return fileInfo.get(tag).remove(toDelete);
    }
    
    /*if the tag exists, update it; otherwise, create it.
    This method returns true if the tag was found; if the tag was created because it wasn't present, returns false.
    */
    public boolean updateTag(String tag, String fileName, BigInteger newOwnerId){
        FileInfoElement toUpdate = existsFile(tag,fileName);
        if(toUpdate == null){
            add(tag,fileName,newOwnerId);
            return false;
        } else {
            toUpdate.setOwnerId(newOwnerId);
            return true;
        }
    }
    
    //very similar to updateTag except for the fact that if the file already exists, it is not created
    public boolean testAndSet(String tag, String fileName, BigInteger ownerId){
        FileInfoElement toCreate = existsFile(tag,fileName);
        if(toCreate == null){
            add(tag,fileName,ownerId);
            return true;
        }
        return false;        
    }
    
    public void mergeInfos(HashMap<String,List<FileInfoElement>> newInfos){
        fileInfo.putAll(newInfos);
    }
    
    public List<FileInfoElement> getByTag(String tag){
        return fileInfo.get(tag);
    }
    
    public Set<String> allTags(){
        return fileInfo.keySet();
    }
    
    @Override
    public String toString(){
        return fileInfo.toString();
    }
    
}
