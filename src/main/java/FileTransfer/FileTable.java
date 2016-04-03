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

    public FileElement(boolean occupied) {
        this.occupied = occupied;
    }
    
    public boolean isOccupied() {
        return occupied;
    }

    public void setOccupied(boolean occupied) {
        this.occupied = occupied;
    }
}

public class FileTable {
    private HashMap<String,FileElement> fileTable;
    
    public FileTable(){
         fileTable = new HashMap<String,FileElement>();
    }
    
    //returns false if the entry already existed
    //this method replaces the entry if it already existed
    private boolean createOrUpdateEntry(String fileName, FileElement e){
        FileElement old = fileTable.put(fileName, e);
        if(old==null) return true;
        return true;
    }
    
    //returns false if the fileName doesn't exists.
    private boolean deleteEntry(String fileName){
        FileElement e = fileTable.remove(fileName);
        if(e==null) return false;
        return true;
    }
    
    @Override
    public String toString(){
        return fileTable.toString();
    }
}
