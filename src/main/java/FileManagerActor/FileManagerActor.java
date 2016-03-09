package FileManagerActor;


import akka.actor.UntypedActor;
import java.util.Random;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author nicky
 */
public class FileManagerActor extends UntypedActor{
    final long myFreeSpace = 13;//new Random().nextLong();    //THIS IS GENERATED INTERNALLY BUT IT SHOULD NOT (should be taken from mine file table)
    
    @Override
    public void onReceive(Object message){
        if(message instanceof FreeSpaceRequest){
            System.out.println("RECEIVED");
            FreeSpaceRequest f = (FreeSpaceRequest)message;
            f.setFreeByteSpace(myFreeSpace);
            getSender().tell(f, getSelf());
        }
    }
    
}
