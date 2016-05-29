/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package FileTransfer.messages;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Set;

/**
 *
 * @author nicky
 */
public class FileListResponse implements Serializable{
    private Set<String> fileList;
    private BigInteger memberRemovedId;

    public FileListResponse(Set<String> fileList, BigInteger memberRemovedId) {
        this.fileList = fileList;
        this.memberRemovedId = memberRemovedId;
    }

    public Set<String> getFileList() {
        return fileList;
    }

    public BigInteger getMemberRemovedId() {
        return memberRemovedId;
    }
}
