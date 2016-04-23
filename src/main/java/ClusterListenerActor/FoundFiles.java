/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ClusterListenerActor;

import ClusterListenerActor.messages.TagSearchGuiResponse;
import GUI.FileEntry;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        //in the map "counted" there will be, for every FileInfoElement, its frequency
        Map<FileInfoElement, Long> counted = foundFiles.stream()
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        
        //I prepare the response
        TagSearchGuiResponse response = new TagSearchGuiResponse();
        for(Map.Entry<FileInfoElement, Long> entry : counted.entrySet()){
            response.addEntry(new FileEntry(
                    entry.getValue().intValue(),
                    entry.getKey().getFileName(),
                    entry.getKey().getOwnerId()));
        }
        
        //and return the prepared response
        return response;
        
    }
}
