/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package FileTransfer;

import java.util.HashMap;

/**
 *
 * @author nicky
 */

class FileElement{
    boolean occupied;
    long size;

    public FileElement(boolean occupied, long size) {
        this.occupied = occupied;
        this.size = size;
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
}

public class FileTable {
    private HashMap<String,FileElement> fileTable;
    
    public FileTable(){
         fileTable = new HashMap<String,FileElement>();
    }
    
    //returns false if the entry already existed
    //this method replaces the entry if it already existed
    public boolean createOrUpdateEntry(String fileName, FileElement e){
        FileElement old = fileTable.put(fileName, e);
        if(old==null) return true;
        return false;
    }
    
    //returns false if the fileName doesn't exists.
    public boolean deleteEntry(String fileName){
        FileElement e = fileTable.remove(fileName);
        if(e==null) return false;
        return true;
    }
    
    //check if a file exists in the table
    public AuthorizationReply getFileStatus(String fileName){
        if(!fileTable.containsKey(fileName)){
            return new AuthorizationReply(EnumAuthorizationReply.FILE_NOT_EXISTS);
        } else if(fileTable.get(fileName).isOccupied()) {
            return new AuthorizationReply(EnumAuthorizationReply.FILE_BUSY);
        } else {
            return new AuthorizationReply(EnumAuthorizationReply.AUTHORIZATION_GRANTED);
        }
    }
    
    public long getTotalOccupiedSpace(){
        long sum=0;
        for(FileElement e : fileTable.values()){
            sum += e.getSize();
        }
        return sum;
    }
    
    @Override
    public String toString(){
        return fileTable.toString();
    }
}
