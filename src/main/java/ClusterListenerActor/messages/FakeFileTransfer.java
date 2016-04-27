/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ClusterListenerActor.messages;

import java.io.Serializable;

/**
 *
 * @author Alessandro
 */
public class FakeFileTransfer implements Serializable{
    private String fileName;

    public FakeFileTransfer(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }
}
