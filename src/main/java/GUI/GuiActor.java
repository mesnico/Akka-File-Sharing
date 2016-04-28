/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package GUI;

import ClusterListenerActor.Utilities;
import ClusterListenerActor.messages.CreationResponse;
import ClusterListenerActor.messages.EndModify;
import ClusterListenerActor.messages.SpreadTags;
import ClusterListenerActor.messages.TagSearchGuiResponse;
import FileTransfer.messages.AllocationRequest;
import FileTransfer.messages.EnumFileModifier;
import FileTransfer.messages.FileTransferResult;
import FileTransfer.messages.SimpleAnswer;
import Startup.AddressResolver;
import Startup.WatchMe;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.typesafe.config.Config;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

/**
 *
 * @author nicky
 */
public class GuiActor extends UntypedActor {

    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    private Config config = getContext().system().settings().config();
    private static String filePath;
    private static ActorRef guiActorRef;
    private static ActorSelection clusterListenerActorRef, soulReaper, server;
    private final int clusterSystemPort;
    private final String tmpFilePath;

    public GuiActor() {
        clusterSystemPort = config.getInt("akka.remote.netty.tcp.port");
        filePath = config.getString("app-settings.file-path");
        tmpFilePath = System.getProperty("java.io.tmpdir");
    }

    public static String getFilePath() {
        return filePath;
    }

    private void startModify(File file) throws IOException {
        Desktop.getDesktop().edit(file);
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/Modify.fxml"));
            Parent root = (Parent) fxmlLoader.load();
            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.setTitle("Modify");
            //remove "minimize" and "restore down" buttons
            stage.initStyle(StageStyle.UTILITY);
            //disable close button
            stage.setOnCloseRequest((final WindowEvent windowEvent) -> {
                windowEvent.consume();
            });

            GUI.setSecondaryStage(stage);
            GUI.getSecondaryStage().show();
            GUI.getStage().hide();

        } catch (IOException ex) {
            //System.out.println(ex.getMessage());
            //System.out.println(ex.getCause().toString());
        }
    }

    @Override
    public void preStart() throws Exception {
        guiActorRef = getSelf();
        clusterListenerActorRef = getContext().actorSelection("akka.tcp://ClusterSystem@" + AddressResolver.getMyIpAddress() + ":" + clusterSystemPort + "/user/clusterListener");
        soulReaper = getContext().actorSelection("akka.tcp://ClusterSystem@" + AddressResolver.getMyIpAddress() + ":" + clusterSystemPort + "/user/soulReaper");
        server = getContext().actorSelection("akka.tcp://ClusterSystem@" + AddressResolver.getMyIpAddress() + ":" + clusterSystemPort + "/user/server");

        //subscrive to to the soul reaper
        soulReaper.tell(new WatchMe(), getSelf());
    }

    @Override
    public void onReceive(Object message) throws Exception {
        if (message instanceof CreationResponse) {
            log.info("GUI received creation response for file: success {}", ((CreationResponse) message).isSuccess());
            if (((CreationResponse) message).isSuccess()) {
                //from a request of creation I obtained positive response => Start che creation and modify of the new file
                GUI.getSecondaryStage().close();
                File newFile, fileToImport;
                newFile = new File(filePath + GUI.OpenedFile.getName()); 
                if(GUI.OpenedFile.getImportedFile()!= null){
                    Files.copy(GUI.OpenedFile.getImportedFile().toPath(), newFile.toPath(), REPLACE_EXISTING);
                } else {
                    newFile.createNewFile();
                }
                
                //tell to the server to create a new entry for the FileTable
                AllocationRequest newReq = new AllocationRequest(GUI.OpenedFile.getName(), newFile.length(),
                        GUI.OpenedFile.getTags(), (newFile.length()==0)?true:false);
                
                //spread the tags
                SpreadTags tagsMessage = new SpreadTags(GUI.OpenedFile.getName(), 
                        GUI.OpenedFile.getTags(), 
                        Utilities.computeId(Utilities.getAddress(getSelf().path().address(), clusterSystemPort)));
                        clusterListenerActorRef.tell(tagsMessage, getSelf());
                server.tell(newReq, getSelf());
                
                //the edit is performed only if the new file size is 0
                if(newFile.length()==0){
                    startModify(newFile);
                }
                
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Bad name");
                alert.setContentText("This file is already present in the cluster!");

                alert.showAndWait();
            }

        } else if (message instanceof TagSearchGuiResponse) {
            TagSearchGuiResponse r = (TagSearchGuiResponse) message;
            log.info("Received search infos: {}", r.getReturnedList());

            ObservableList<FileEntry> tags = FXCollections.observableList(r.getReturnedList());
            //for(FileEntry fe : r.getReturnedList()) tags.add(fe);
            log.info("Received search infos (ObservableList<FileEntry>): {}", tags);
            FXMLMainController.getTable().setItems(tags);
            FXMLMainController.getTable().sort();

        } else if (message instanceof FileTransferResult) {
            FileTransferResult ftr = (FileTransferResult) message;
            log.info("FileTransferResult: {}", ftr);

            //if (GUI.getStage().isShowing()) {
            if (ftr.getFileName().equals(GUI.OpenedFile.getName())){
                GUI.getStage().show();
                switch (ftr.getMessageType()) {
                    case FILE_RECEIVED_SUCCESSFULLY:
                        boolean isWrite = (ftr.getFileModifier() == EnumFileModifier.WRITE);
                        String path = (isWrite) ? filePath : tmpFilePath;
                        File file = new File(path + ftr.getFileName());
                        file.setWritable(true);

                        if (isWrite) {
                            //tags are in fileTable

                            startModify(file);
                        } else {
                            //startRead(file)... not really a method, just a line of code
                            Desktop.getDesktop().open(file);
                        }

                        break;

                    case FILE_RECEIVING_FAILED:
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Error");
                        alert.setHeaderText("Reciving Failed");
                        alert.setContentText("An error occurred during file transfer!");

                        alert.showAndWait();
                        break;

                    //following case handles progress bars during load balancing...
                    //case FILE_SENT_SUCCESSFULLY:
                }

            }
        } else if (message instanceof SimpleAnswer) {
            SimpleAnswer sa = (SimpleAnswer) message;
            log.debug("I received the simpleAnswer {} from the server", sa.getAnswer());
            if (sa.getAnswer()==true) {
                File modifile = new File(GuiActor.getFilePath() + GUI.OpenedFile.getName());
                GuiActor.getClusterListenerActorRef().tell(
                        new EndModify(GUI.OpenedFile.getName(), modifile.length()),
                        GuiActor.getGuiActorRef());
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Insufficient free space");
                alert.setContentText("You have not enough free space for the modifies you would apply to the file " + GUI.OpenedFile.getName() + "!");

                alert.showAndWait();
                System.out.printf("simple answer handling if answer is not: Is the gui shown? %s", GUI.getStage().isShowing());   
                
                /*
                    TODO: handle rollback:
                            deleting all tags and the file itself
                */
                    
            }
        }

        /*
         if(message instanceof ModifyRequest){
            
         }
         if(message instanceof ReadRequest){
            
         }*/
    }

    public static ActorRef getGuiActorRef() {
        return guiActorRef;
    }

    public static ActorSelection getServer() {
        return server;
    }

    public static ActorSelection getClusterListenerActorRef() {
        return clusterListenerActorRef;
    }
}
