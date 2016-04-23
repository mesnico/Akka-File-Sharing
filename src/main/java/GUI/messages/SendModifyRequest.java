/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package GUI.messages;

import FileTransfer.messages.EnumFileModifier;
import java.io.Serializable;

/**
 *
 * @author francescop
 */
public class SendModifyRequest implements Serializable{
    String fileName;
    String owner;
    EnumFileModifier mode;

    public SendModifyRequest(String fileName, String owner, EnumFileModifier mode) {
        this.fileName = fileName;
        this.owner = owner;
        this.mode = mode;
    }
    
    public String getFileName() {
        return fileName;
    }
    
}
