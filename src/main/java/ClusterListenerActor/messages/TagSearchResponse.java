/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ClusterListenerActor.messages;

import ClusterListenerActor.FileInfoElement;
import java.io.Serializable;
import java.util.List;

/**
 *
 * @author nicky
 */
public class TagSearchResponse implements Serializable{
    private List<FileInfoElement> returnedList;

    public TagSearchResponse(List<FileInfoElement> returnedList) {
        this.returnedList = returnedList;
    }

    public List<FileInfoElement> getReturnedList() {
        return returnedList;
    }
}
