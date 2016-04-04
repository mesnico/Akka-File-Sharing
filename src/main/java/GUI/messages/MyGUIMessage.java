package GUI.messages;



/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Nicola
 */
public class MyGUIMessage implements java.io.Serializable{
    private int msg;
    public MyGUIMessage(int msg){
        this.msg = msg;
    }
    public int getMessage(){
        return msg;
    }
}
