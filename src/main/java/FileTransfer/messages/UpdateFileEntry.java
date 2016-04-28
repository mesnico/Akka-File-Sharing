/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package FileTransfer.messages;

import java.io.Serializable;

/**
 *
 * @author nicky
 */
public class UpdateFileEntry implements Serializable{
    private String fileName;
    private long size;
    private boolean occupied;

    public UpdateFileEntry(String fileName, long size, boolean occupied) {
        this.fileName = fileName;
        this.size = size;
        this.occupied = occupied;
    }

    public String getFileName() {
        return fileName;
    }

    public long getSize() {
        return size;
    }

    public boolean isOccupied() {
        return occupied;
    }
    
    @Override
    public String toString(){
        return "file "+fileName+" is "+size+"bytes long and is occupied: "+occupied;
    }
}
