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

public class AuthorizationReply implements Serializable {
    private EnumAuthorizationReply response;
    private long size;
    private List<String> tags;
    private String fileName;
    
    public AuthorizationReply(String fileName, EnumAuthorizationReply response){
        this.fileName = fileName;
        this.response = response;
    }
    
    public AuthorizationReply(String fileName, EnumAuthorizationReply response, long size, List<String> tags){
        this.response = response;
        this.size = size;
        this.tags = tags;
        this.fileName = fileName;
    }
    
    public EnumAuthorizationReply getResponse(){
        return response;
    }
    
    public long getSize(){
        return size;
    }
    
    public List<String> getTags(){
        return tags;
    }

    public String getFileName() {
        return fileName;
    }
    
    @Override
    public String toString(){
        return response +"; size= "+size+"; tags= "+tags;
    }
}
