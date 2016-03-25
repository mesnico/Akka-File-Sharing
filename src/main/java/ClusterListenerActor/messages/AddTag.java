/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ClusterListenerActor.messages;

import java.io.Serializable;
import java.math.BigInteger;

/**
 *
 * @author francescop
 */
public class AddTag  implements Serializable{
    String fileName;
    String tag;
    BigInteger ownerId;

    public AddTag(String fileName, String tag, BigInteger ownerId) {
        this.fileName = fileName;
        this.tag = tag;
        this.ownerId = ownerId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getTag() {
        return tag;
    }

    public BigInteger getOwnerId() {
        return ownerId;
    }
}
