/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ClusterListenerActor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

/**
 *
 * @author nicky
 */
public class FileInfoTransfer implements Serializable{
    HashMap<String,List<FileInfoElement>> info;

    public FileInfoTransfer() {
        info = new HashMap();
    }
    
    public void addEntry(String tag, List<FileInfoElement> files){
        info.put(tag, files);
    }
    
    public HashMap<String,List<FileInfoElement>> getInfos(){
        return info;
    }
    
}
