/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package FileTransfer;

import FileTransfer.messages.EnumFileModifier;
import FileTransfer.messages.AuthorizationReply;
import FileTransfer.messages.EnumEnding;
import FileTransfer.messages.EnumAuthorizationReply;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 *
 * @author nicky
 */

class FileElement{
    private boolean occupied;
    private long size;
    private List<String> tags;

    public FileElement(boolean occupied, long size, List<String> tags) {
        this.occupied = occupied;
        this.size = size;
        this.tags = tags;
    }
    
    public boolean isOccupied() {
        return occupied;
    }

    public void setOccupied(boolean occupied) {
            this.occupied = occupied;
    }

    public long getSize() {
        return size;
    }
    
    public List<String> getTags(){
        return tags;
    }
}

public class FileTable {
    private HashMap<String,FileElement> fileTable;
    
    public FileTable(){
        System.out.println("[fileTable]: fileTable creata");
         fileTable = new HashMap<String,FileElement>();
    }
    
    //returns false if the entry already existed
    //this method replaces the entry if it already existed
    public boolean createOrUpdateEntry(String fileName, FileElement e){
        FileElement old = fileTable.put(fileName, e);
        if(old==null) return true;
        return false;
    }
    
    // --- Returns false if the fileName doesn't exists.
    public FileElement deleteEntry(String fileName){
        FileElement e = fileTable.remove(fileName);
        return e;
    }
    
    public boolean setOccupied(String fileName, boolean occupied){
        FileElement e = fileTable.get(fileName);
        if(e == null) return false;
        fileTable.get(fileName).setOccupied(occupied);
        return true;
    }
    
    // --- Check if a file exists in the table or if it's busy.
    // --- If it's free, I set it as busy and return also the file's size and tags
    public AuthorizationReply testAndSet(String fileName, EnumFileModifier readOrWrite){
        if(!fileTable.containsKey(fileName)){
            return new AuthorizationReply(EnumAuthorizationReply.FILE_NOT_EXISTS);
        } else if(fileTable.get(fileName).isOccupied()) {
            return new AuthorizationReply(EnumAuthorizationReply.FILE_BUSY);
        } else {
            if(readOrWrite == EnumFileModifier.WRITE){
                System.out.println("[fileTable]: sono nella testAndSet, sto per marcare il file come occupato");
                fileTable.get(fileName).setOccupied(true);
            }
            return new AuthorizationReply(EnumAuthorizationReply.AUTHORIZATION_GRANTED, 
                    fileTable.get(fileName).getSize(), 
                    fileTable.get(fileName).getTags());
        }
    }
    
    public long getTotalOccupiedSpace(){
        long sum=0;
        for(FileElement e : fileTable.values()){
            sum += e.getSize();
        }
        return sum;
    }
    
    public FileElement getFileElement(String fileName){
        return fileTable.get(fileName);
    }
    
    public List<String>getTags(String fileName){
        return fileTable.get(fileName).getTags();
    }
    
    /*
    public long getFileSize(String fileName){
        return fileTable.get(fileName).getSize();
    }
    */
    
    @Override
    public String toString(){
        return fileTable.toString();
    }
}
