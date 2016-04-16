/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package FileTransfer.messages;

import java.io.Serializable;
import java.util.List;

/**
 *
 * @author Alessandro
 */
public class AllocationRequest implements Serializable {
    private String fileName;
    private long size;
    private List<String> tags;

    public AllocationRequest(String fileName, long size, List<String> tags) {
        this.fileName = fileName;
        this.size = size;
        this.tags = tags;
    }

    public String getFileName() {
        return fileName;
    }

    public long getSize() {
        return size;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
