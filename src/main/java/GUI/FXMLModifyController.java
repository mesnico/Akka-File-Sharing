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
       
       //the file under modify is: GUI.ModifiedFile.getName();
       
       //AllocationRequest to update file's size
       //the file exits the busy-state
       AllocationRequest newReq = new AllocationRequest(GUI.ModifiedFile.getName(),0,GUI.ModifiedFile.getTags(),false);
       
       //(-- TO DO INTO THE ClusterListener
       //- load distribution (only destination election) -> (ClusterListener knows membersFreeSpace.getHighestFreeSpaceMember())
       //- update tags in the fileInfoTabe -> (ClusterListener knows membersMap, for the responsible of each tag)
       //- send file -> (i must say to the clusterListener to initiate the file transfer)
       // --)
       File modifile = new File(GuiActor.getFilePath() + ModifiedFile.getName());
       GuiActor.getClusterListenerActorRef().tell(
               new EndModify(ModifiedFile.getName(), ModifiedFile.getTags(),  modifile.length()), 
               GuiActor.getGuiActorRef());
       
       //can't show the main stage before the check of the clusterListener
       //GUI.getStage().show();
    }
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        
    }
}
