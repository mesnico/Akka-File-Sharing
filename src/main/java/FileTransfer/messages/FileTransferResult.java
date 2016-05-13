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
    private boolean isAsker;
    
    public FileTransferResult(EnumEnding msg){
        this.msg = msg;
        this.fileName = "";
        isAsker = false;
        // --- About isAsker: is it's is esplicitly setted in the fileTransferActor,
        // --- while this doesn't happen (and it's ok this way) in the other cases,
        // --- so in other cases it must be set to false so the server doesn't occupy the file
    }
    
    public FileTransferResult(EnumEnding msg, String fileName){
        this(msg);
        this.fileName = fileName;
    }
    
    public FileTransferResult(EnumEnding msg, String fileName, EnumFileModifier modifier){
        this(msg, fileName);
        this.modifier = modifier;
    }
    
    public FileTransferResult(EnumEnding msg, String fileName, EnumFileModifier modifier, boolean isAsker){
        this(msg, fileName, modifier);
        this.isAsker = isAsker;
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

    public boolean isAsker() {
        return isAsker;
    }
    
    @Override
    public String toString(){
        return "fileName: " + fileName + " in " + modifier + " mode; exit status = " + msg;
    }
}
