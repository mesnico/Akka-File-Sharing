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


public class AuthorizationReply implements Serializable {
    private EnumAuthorizationReply response;
    
    public AuthorizationReply(EnumAuthorizationReply response){
        this.response = response;
    }
    
    public EnumAuthorizationReply getResponse(){
        return response;
    }
}
