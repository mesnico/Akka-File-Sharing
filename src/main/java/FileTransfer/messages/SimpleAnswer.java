/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package FileTransfer.messages;

import java.io.Serializable;

/**
 *
 * @author Alessandro
 */
public class SimpleAnswer implements Serializable {
    private boolean answer;

    public SimpleAnswer(boolean answer) {
        this.answer = answer;
    }

    public boolean getAnswer() {
        return answer;
    }
}
