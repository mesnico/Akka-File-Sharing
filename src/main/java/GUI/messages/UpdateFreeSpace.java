/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package GUI.messages;

import java.io.Serializable;

/**
 *
 * @author francescop
 */
public class UpdateFreeSpace implements Serializable{
    private final long freeSpace;

    public UpdateFreeSpace(long freeSpace) {
        this.freeSpace = freeSpace;
    }

    public long getFreeSpace() {
        return freeSpace;
    }
}
