package FileTransfer;
import java.io.Serializable;

enum Behavior{
    SEND, REQUEST, UNINITIALIZED;
}

public class Handshake implements Serializable {
    private Behavior behavior;
    private String fileName;
    private FileModifier modifier;
    
    public Handshake(Behavior behavior, String fileName, FileModifier modifier){
        this.behavior = behavior;
        this.fileName = fileName;
        this.modifier = modifier;
    }   
    
    public FileModifier getModifier(){
        return modifier;
    }
    
    public String getFileName(){
        return fileName;
    }
    
    public Behavior getBehavior(){
        return behavior;
    }
}
