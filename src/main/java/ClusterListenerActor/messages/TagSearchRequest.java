/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ClusterListenerActor.messages;

import java.io.Serializable;

/**
 *
 * @author nicky
 */
public class TagSearchRequest implements Serializable{
    private String tag;

    public TagSearchRequest(String tag) {
        this.tag = tag;
    }

    public String getTag() {
        return tag;
    }    
}
