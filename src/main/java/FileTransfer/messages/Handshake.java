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

public class Handshake implements Serializable {
    private EnumBehavior behavior;
    private String fileName;
    private EnumFileModifier modifier;
    
    public Handshake(EnumBehavior behavior, String fileName){
        this.behavior = behavior;
        this.fileName = fileName;
        this.modifier = EnumFileModifier.WRITE;
    }   
    
    public Handshake(EnumBehavior behavior, String fileName, EnumFileModifier modifier){
        this(behavior, fileName);        
        this.modifier = modifier;
    }   
    
    public EnumFileModifier getModifier(){
        return modifier;
    }
    
    public String getFileName(){
        return fileName;
    }
    
    public EnumBehavior getBehavior(){
        return behavior;
    }
    
    public void setBehavior(EnumBehavior behavior){
        this.behavior = behavior;
    }
            
}
