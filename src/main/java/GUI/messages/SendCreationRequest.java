/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package GUI.messages;

import java.io.Serializable;

/**
 *
 * @author francescop
 */
public class SendCreationRequest implements Serializable{
    String fileName;

    public SendCreationRequest(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }
    
}
