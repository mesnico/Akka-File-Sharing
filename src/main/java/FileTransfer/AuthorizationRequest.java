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


public class AuthorizationRequest implements Serializable {
    String fileName;
    FileModifier modifier;
    
    public AuthorizationRequest(String fileName, FileModifier modifier){
        this.fileName = fileName;
        this.modifier = modifier;
    }   
    
    public FileModifier getModifier(){
        return modifier;
    }
    
    public String getFileName(){
        return fileName;
    }
}
