/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package GUI.messages;

import java.io.Serializable;

/**
 *
 * @author nicky
 */
public class ProgressUpdate implements Serializable{
    private int completion;
    private int total;

    public ProgressUpdate(int completion, int total) {
        this.completion = completion;
        this.total = total;
    }

    public int getCompletion() {
        return completion;
    }

    public int getTotal() {
        return total;
    }
}
