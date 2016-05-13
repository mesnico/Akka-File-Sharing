/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ClusterListenerActor.messages;

import java.io.Serializable;
import java.util.List;

/**
 *
 * @author francescop
 */
public class SendDeleteInfos implements Serializable {

    private String fileName;
    private List<String> tags;

    public SendDeleteInfos(String fileName, List<String> tags) {
        this.fileName = fileName;
        this.tags = tags;
    }

    public String getFileName() {
        return fileName;
    }

    public List<String> getTags() {
        return tags;
    }
}
