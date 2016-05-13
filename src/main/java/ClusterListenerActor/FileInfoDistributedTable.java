/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ClusterListenerActor;

import Utils.Utilities;
import ClusterListenerActor.messages.FileInfoTransfer;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 *
 * @author nicky
 */

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
    
    /*public List<FileInfoElement> removeByTag(String tag){
        return fileInfo.remove(tag);
    }*/
    
    public boolean removeByTagAndName(String tag, String fileName){
        FileInfoElement toDelete = existsFile(tag,fileName);
        if(toDelete == null) 
            return false;
        return fileInfo.get(tag).remove(toDelete);
    }
    
    //This method update the tag if the is was found, and return true;
    //else if the tag wasn't present, it is created and returns false.
    public boolean updateTag(String fileName, String tag, BigInteger newOwnerId){
        FileInfoElement toUpdate = existsFile(tag,fileName);
        if(toUpdate == null){
            add(tag,fileName,newOwnerId);
            return false;
        } else {
            toUpdate.setOwnerId(newOwnerId);
            return true;
        }
    }
    
    //Very similar to updateTag except for the fact that if the file already exists, it is not updated
    public boolean testAndSet(String tag, String fileName, BigInteger ownerId){
        FileInfoElement toCreate = existsFile(tag,fileName);
        if(toCreate == null){
            add(tag,fileName,ownerId);
            return true;
        }
        return false;        
    }
    
    //Construct the FileInfoTransfer for current node id.
    //Tags resident on that node,infact, must be transferred.
    FileInfoTransfer buildFileInfoTransfer(HashMembersData membersMap, BigInteger nodeId){
        
        FileInfoTransfer fit = new FileInfoTransfer();
        for(Iterator<HashMap.Entry<String,List<FileInfoElement>>>it=fileInfo.entrySet().iterator();it.hasNext();){
            HashMap.Entry<String, List<FileInfoElement>> entry = it.next();
            if(membersMap.getResponsibleById(Utilities.computeId(entry.getKey())).compareTo(nodeId)!=0){
                fit.addEntry(entry.getKey(), entry.getValue());
                it.remove();
            }
        }
        return fit;
    }
    
    public void mergeInfos(FileInfoTransfer fit){
        fileInfo.putAll(fit.getInfos());
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
