package FileTransfer;

import java.io.Serializable;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Alessandro
 */


public class FileTransferResult implements Serializable {
    private MessageType msg;
    private String fileName;
    private FileModifier modifier;
    
    public FileTransferResult(MessageType msg){
        this.msg = msg;
        this.fileName = "";
    }
    
    public FileTransferResult(MessageType msg, String fileName){
        this.msg = msg;
        this.fileName = fileName;
    }
    
    public FileTransferResult(MessageType msg, String fileName, FileModifier modifier){
        this.msg = msg;
        this.fileName = fileName;
        this.modifier = modifier;
    }
    
    public MessageType getMessageType(){
        return msg;
    }
    
    public String getFileName(){
        return fileName;
    }
    
    public FileModifier getFileModifier(){
        return modifier;
    }
}
