/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ClusterListenerActor;

import ClusterListenerActor.messages.TagSearchGuiResponse;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author nicky
 */
public class FoundFiles {
    private List<FileInfoElement> foundFiles;
    
    public FoundFiles() {
        foundFiles = new LinkedList<FileInfoElement>();
    }
    
    public boolean addAll(List<FileInfoElement> fileInfos){
        return foundFiles.addAll(fileInfos);
    }
    
    public void reset(){
        foundFiles.clear();
    }
    
    //performs transformation between list of FileInfoElements and list of File Entries
    //tagSearchGuiResponse contains infact a collection of FileEntries
    public TagSearchGuiResponse createGuiResponse(){
        //TODO: THE ALGORITHM FOR CALCULATING FREQUENCIES ON THE ELEMENTS OF foundFiles
        return null;
    }
}
