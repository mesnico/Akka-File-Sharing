/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ClusterListenerActor;

import java.io.Serializable;

/**
 *
 * @author nicky
 */
public class CreationResponse implements Serializable{
    boolean success;

    public CreationResponse(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }
    
}
