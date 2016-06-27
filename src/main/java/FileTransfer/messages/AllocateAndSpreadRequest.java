/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package FileTransfer.messages;

import java.util.List;
import java.io.Serializable;

/**
 *
 * @author Alessandro
 */
public class AllocateAndSpreadRequest extends AllocationRequest implements Serializable {

    public AllocateAndSpreadRequest(String fileName, long size, List<String> tags, boolean busy) {
        super(fileName, size, tags, busy);
    }
}










