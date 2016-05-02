/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Startup;

import Utils.SoulReaper;

/**
 *
 * @author nicky
 */
public class MainSoulReaper extends SoulReaper{
    @Override
    public void allSoulsReaped(){
        getContext().system().terminate();
    }
}
