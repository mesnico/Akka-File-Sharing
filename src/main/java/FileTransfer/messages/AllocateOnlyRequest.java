/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package FileTransfer.messages;

import java.io.Serializable;
import java.util.List;

/**
 *
 * @author Alessandro
 */
public class AllocateOnlyRequest extends AllocationRequest implements Serializable {

    public AllocateOnlyRequest (String fileName, long size, List<String> tags, boolean busy) {
        super(fileName, size, tags, busy);
    }
    
}
