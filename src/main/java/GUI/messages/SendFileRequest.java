/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package GUI.messages;

import FileTransfer.messages.EnumFileModifier;
import java.io.Serializable;
import java.math.BigInteger;

/**
 *
 * @author nicky
 */
public class SendFileRequest implements Serializable{
    private String fileName;
    private BigInteger ownerId;
    private EnumFileModifier modifier;

    public SendFileRequest(String fileName, BigInteger ownerId, EnumFileModifier modifier) {
        this.fileName = fileName;
        this.ownerId = ownerId;
        this.modifier = modifier;
    }

    public String getFileName() {
        return fileName;
    }

    public BigInteger getOwnerId() {
        return ownerId;
    }

    public EnumFileModifier getModifier() {
        return modifier;
    }
}
