package FileTransfer.messages;

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
    private EnumEnding msg;
    private String fileName;
    private EnumFileModifier modifier;
    
    public FileTransferResult(EnumEnding msg){
        this.msg = msg;
        this.fileName = "";
    }
    
    public FileTransferResult(EnumEnding msg, String fileName){
        this.msg = msg;
        this.fileName = fileName;
    }
    
    public FileTransferResult(EnumEnding msg, String fileName, EnumFileModifier modifier){
        this.msg = msg;
        this.fileName = fileName;
        this.modifier = modifier;
    }
    
    public EnumEnding getMessageType(){
        return msg;
    }
    
    public String getFileName(){
        return fileName;
    }
    
    public EnumFileModifier getFileModifier(){
        return modifier;
    }
    
    @Override
    public String toString(){
        return "fileName: " + fileName + "in " + modifier + "mode; exit status = " + msg;
    }
}
