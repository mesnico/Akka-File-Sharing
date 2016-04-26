package GUI;

import ClusterListenerActor.messages.EndModify;
import FileTransfer.messages.AllocationRequest;
import GUI.GUI.OpenedFile;
import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;

public class FXMLModifyController implements Initializable {

    @FXML
    private void done(ActionEvent event) {
        GUI.getSecondaryStage().close();
        File modifile = new File(GuiActor.getFilePath() + OpenedFile.getName());
        
        //the file under modify is: GUI.ModifiedFile.getName();
        
        //free busy-ness
        //but if the file has size zero send directly to EndModify message
        AllocationRequest newReq = new AllocationRequest(GUI.OpenedFile.getName(), modifile.length(), GUI.OpenedFile.getTags(), false);
        GuiActor.getServer().tell(newReq, GuiActor.getGuiActorRef());
        
        if(modifile.length() == 0){
            GuiActor.getClusterListenerActorRef().tell(new EndModify(OpenedFile.getName(), OpenedFile.getTags(), modifile.length()),
                    GuiActor.getGuiActorRef());           
        }
        GUI.getStage().show();
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {

    }
}
