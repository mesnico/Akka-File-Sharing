package FileTransfer.messages;

import java.io.Serializable;
import java.util.List;

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
    private List<String> tags;
    
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

    public FileTransferResult(EnumEnding msg, String fileName, EnumFileModifier modifier, List<String> tags) {
        this.msg = msg;
        this.fileName = fileName;
        this.modifier = modifier;
        this.tags = tags;
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

    public List<String> getTags() {
        return tags;
    }
    
    @Override
    public String toString(){
        return "fileName: " + fileName + " in " + modifier + " mode; exit status = " + msg;
    }
}
