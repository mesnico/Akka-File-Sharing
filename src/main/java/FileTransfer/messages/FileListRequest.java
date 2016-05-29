/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package FileTransfer.messages;

import java.io.Serializable;
import java.math.BigInteger;

/**
 *
 * @author nicky
 */
public class FileListRequest implements Serializable{
    private BigInteger memberRemovedId;

    public FileListRequest(BigInteger memberRemovedId) {
        this.memberRemovedId = memberRemovedId;
    }

    public BigInteger getMemberRemovedId() {
        return memberRemovedId;
    } 
}
