package GUI;

import FileTransfer.messages.UpdateFileEntry;
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
        
        //the UpdateFileEntry is sent to free the file and to set the new file size after the modify.
        UpdateFileEntry updateRequest = new UpdateFileEntry(OpenedFile.getName(),modifile.length(),false);
        GuiActor.getServer().tell(updateRequest, GuiActor.getGuiActorRef());

        // GUI.OpenedFile.unset();
        GUI.getStage().show();
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {

    }
}
