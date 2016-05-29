/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package FileTransfer;

import FileTransfer.messages.EnumFileModifier;
import FileTransfer.messages.AuthorizationReply;
import FileTransfer.messages.EnumAuthorizationReply;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

/**
 *
 * @author nicky
 */
class FileElement implements Serializable {

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

    public void setSize(long size) {
        this.size = size;
    }

    public long getSize() {
        return size;
    }

    public List<String> getTags() {
        return tags;
    }
}

public class FileTable implements Serializable {

    private HashMap<String, FileElement> fileTable;
    private String filePath;

    public FileTable(String filePath) {
        System.out.println("[fileTable]: fileTable creata");
        fileTable = new HashMap<String, FileElement>();
        this.filePath = filePath;
    }

    //returns false if the entry already existed
    //this method replaces the entry if it already existed
    public boolean createOrUpdateEntry(String fileName, FileElement e) {
        FileElement old = fileTable.put(fileName, e);
        serializeToFile();
        if (old == null) {
            return true;
        }
        return false;
    }

    // --- Returns false if the fileName doesn't exists.
    public FileElement deleteEntry(String fileName) {
        FileElement e = fileTable.remove(fileName);
        serializeToFile();
        return e;
    }

    public boolean setOccupied(String fileName, boolean occupied) {
        FileElement e = fileTable.get(fileName);
        if (e == null) {
            return false;
        }
        fileTable.get(fileName).setOccupied(occupied);
        serializeToFile();
        return true;
    }

    // --- Check if a file exists in the table or if it's busy.
    // --- If it's free, I set it as busy and return also the file's size and tags
    public AuthorizationReply testAndSet(String fileName, EnumFileModifier readOrWrite) {
        if (!fileTable.containsKey(fileName)) {
            return new AuthorizationReply(fileName, EnumAuthorizationReply.FILE_NOT_EXISTS, readOrWrite);
        } else if (fileTable.get(fileName).isOccupied()) {
            return new AuthorizationReply(fileName, EnumAuthorizationReply.FILE_BUSY, readOrWrite);
        } else {
            if (readOrWrite == EnumFileModifier.WRITE) {
                System.out.println("[fileTable]: sono nella testAndSet, sto per marcare il file come occupato");
                fileTable.get(fileName).setOccupied(true);
                serializeToFile();
            }
            return new AuthorizationReply(
                    fileName,
                    EnumAuthorizationReply.AUTHORIZATION_GRANTED,
                    fileTable.get(fileName).getSize(),
                    fileTable.get(fileName).getTags(),
                    readOrWrite);
        }
    }

    public long getTotalOccupiedSpace() {
        long sum = 0;
        for (FileElement e : fileTable.values()) {
            sum += e.getSize();
        }
        return sum;
    }

    public FileElement getFileElement(String fileName) {
        return fileTable.get(fileName);
    }

    public Set<Entry<String, FileElement>> asSet() {
        return fileTable.entrySet();
    }
    
    public Set<String> getKeySet(){
        return fileTable.keySet();
    }

    @Override
    public String toString() {
        return fileTable.toString();
    }

    //serialize to file in order to be reloaded at every time
    private void serializeToFile() {
        try {
            FileOutputStream fout = new FileOutputStream(filePath + "fileTable.ser");
            ObjectOutputStream oos = new ObjectOutputStream(fout);
            oos.writeObject(this);

            oos.close();
            fout.close();
        } catch (FileNotFoundException e) {
            System.err.println("Error opening the file fileTable.ser! " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error serializing the fileTable to file fileTable.ser! " + e.getMessage());
        }
    }
}
