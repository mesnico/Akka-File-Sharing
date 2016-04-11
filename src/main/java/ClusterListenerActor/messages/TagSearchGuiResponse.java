/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ClusterListenerActor.messages;

import GUI.FileEntry;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author nicky
 */
public class TagSearchGuiResponse implements Serializable{
    private List<FileEntry> returnedList;

    public TagSearchGuiResponse() {
        this.returnedList = new LinkedList<FileEntry>();
    }
    
    public void addEntry(FileEntry fe){
        returnedList.add(fe);
    }

    public List<FileEntry> getReturnedList() {
        return returnedList;
    }
}
