package GUI;

import ClusterListenerActor.messages.EndModify;
import FileTransfer.messages.AllocationRequest;
import GUI.GUI.ModifiedFile;
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
        File modifile = new File(GuiActor.getFilePath() + ModifiedFile.getName());

        //the file under modify is: GUI.ModifiedFile.getName();
        
        //free busy-ness
        //but if the file has size zero send directly to EndModify message
        AllocationRequest newReq = new AllocationRequest(GUI.ModifiedFile.getName(), modifile.length(), GUI.ModifiedFile.getTags(), false);
        GuiActor.getServer().tell(newReq, GuiActor.getGuiActorRef());
        
        if(modifile.length() == 0)
            GuiActor.getClusterListenerActorRef().tell(
                new EndModify(ModifiedFile.getName(), ModifiedFile.getTags(), modifile.length()),
                GuiActor.getGuiActorRef());

        //ERROR: can't show the main stage before the check of the clusterListener
        GUI.getStage().show();
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {

    }
}
