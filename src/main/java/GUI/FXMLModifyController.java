package GUI;

import FileTransfer.messages.AllocationRequest;
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

       //load distribution (only destination election)
       //update tags in the fileInfoTabe
       //send file -> (i must say to the clusterListener to initiate the file transfer)
       
       GUI.getStage().show();
    }
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        
    }
}
