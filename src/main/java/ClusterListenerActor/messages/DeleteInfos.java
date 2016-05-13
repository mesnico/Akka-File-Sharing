/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ClusterListenerActor.messages;

import java.io.Serializable;

/**
 *
 * @author francescop
 */
public class DeleteInfos implements Serializable {

    private String fileName;
    private String tag;

    public DeleteInfos(String fileName, String tag) {
        this.fileName = fileName;
        this.tag = tag;
    }

    public String getFileName() {
        return fileName;
    }

    public String getTag() {
        return tag;
    }

}
