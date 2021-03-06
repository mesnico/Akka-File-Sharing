/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package GUI;

import ClusterListenerActor.messages.CreationResponse;
import ClusterListenerActor.messages.EndModify;
import ClusterListenerActor.messages.TagSearchGuiResponse;
import FileTransfer.messages.AllocateAndSpreadRequest;
import FileTransfer.messages.EnumEnding;
import FileTransfer.messages.EnumFileModifier;
import FileTransfer.messages.FileTransferResult;
import FileTransfer.messages.SimpleAnswer;
import FileTransfer.messages.UpdateFileEntry;
import GUI.messages.GuiShutdown;
import GUI.messages.ProgressUpdate;
import GUI.messages.UpdateFreeSpace;
import Utils.AddressResolver;
import Utils.Utilities;
import Utils.WatchMe;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.PoisonPill;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.typesafe.config.Config;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.text.DecimalFormat;
import java.util.Comparator;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.paint.Color;
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

    public GuiActor() throws Exception {
        clusterSystemPort = config.getInt("akka.remote.netty.tcp.port");
        filePath = config.getString("app-settings.file-path");
        tmpFilePath = System.getProperty("java.io.tmpdir");
        GUI.setClusterListenerId(Utilities.computeIdByAddress(Utilities.getAddress(getSelf().path().address(), clusterSystemPort)));
    }

    public static String getFilePath() {
        return filePath;
    }

    private void startModify(File file) throws IOException {
        try {
            Desktop.getDesktop().edit(file);

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

        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to edit the file");
            alert.setContentText("The file is not editable! Try to ask for it in READ mode");

            alert.showAndWait();
            //--- free the file busy-ness 
            // load distribution is performed by the EndModify, that is achieved
            // after the SimpleAnswer (with response YES because the file size isn't changed)
            UpdateFileEntry updateRequest = new UpdateFileEntry(file.getName(), file.length(), false);
            getServer().tell(updateRequest, getSelf());

        } finally {
            GUI.getSecondaryStage().show();
            GUI.getStage().hide();
        }
    }

    @Override
    public void preStart() throws Exception {
        guiActorRef = getSelf();
        clusterListenerActorRef = getContext().actorSelection("akka.tcp://ClusterSystem@" + AddressResolver.getMyIpAddress() + ":" + clusterSystemPort + "/user/clusterListener");
        soulReaper = getContext().actorSelection("akka.tcp://ClusterSystem@" + AddressResolver.getMyIpAddress() + ":" + clusterSystemPort + "/user/mainSoulReaper");
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
                File newFile;
                newFile = new File(filePath + GUI.OpenedFile.getName());
                try {
                    if (GUI.OpenedFile.getImportedFile() != null) {
                        if (GUI.OpenedFile.getImportedFile().compareTo(newFile) != 0) {
                            Files.copy(GUI.OpenedFile.getImportedFile().toPath(), newFile.toPath(), REPLACE_EXISTING);
                        }
                    } else {
                        newFile.createNewFile();
                    }
                } catch (IOException ioe) {
                    log.error("A file I/O error occurred while copying or creating the new file!");
                    //TODO: destroy the program.
                }
                //file length is saved in a variable because it can be modified by the server after the following
                //tell (the file could be deleted by the server, so its dimensions could virtually become 0.)
                long fileLength = newFile.length();

                //tell to the server to create a new entry for the FileTable
                AllocateAndSpreadRequest newReq = new AllocateAndSpreadRequest(GUI.OpenedFile.getName(), newFile.length(),
                        GUI.OpenedFile.getTags(), (newFile.length() == 0) ? true : false);
                server.tell(newReq, getSelf());

                //the edit is performed only if the new file size is 0
                if (fileLength == 0) {
                    startModify(newFile);
                } else {
                    GUI.getStage().show();
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

        } else if (message instanceof FileTransferResult) {
            FileTransferResult ftr = (FileTransferResult) message;
            log.info("FileTransferResult: {}", ftr);

            //if (GUI.getStage().isShowing()) {
            if (ftr.getFileName().equals(GUI.OpenedFile.getName())) {
                GUI.getStage().show();
                Alert alert;
                switch (ftr.getMessageType()) {
                    case OWNER_IS_MYSELF:
                    case FILE_RECEIVED_SUCCESSFULLY:
                        if (ftr.getMessageType() == EnumEnding.OWNER_IS_MYSELF && ftr.getFileModifier() == EnumFileModifier.READ) {
                            // copy the file from current directory to tmp directory
                            Files.copy(
                                    new File(filePath + ftr.getFileName()).toPath(),
                                    new File(tmpFilePath + ftr.getFileName()).toPath(),
                                    REPLACE_EXISTING
                            );
                        }
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
                            GUI.OpenedFile.unset();
                        }

                        break;

                    case FILE_TO_RECEIVE_BUSY:
                    case FILE_TO_RECEIVE_NOT_EXISTS:
                    case NOT_ENOUGH_SPACE_FOR_RECEIVING:
                    case FILE_RECEIVING_FAILED:
                    case IO_ERROR_WHILE_RECEIVING:
                        alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Error");
                        alert.setHeaderText("Receiving Failed");
                        alert.setContentText("An error occurred during file transfer!: " + ftr.getMessageType().toString());

                        alert.showAndWait();
                        GUI.OpenedFile.unset();
                        break;
                    case CONNECTION_FAILED:
                        alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Error");
                        alert.setHeaderText("Connection Failed");
                        alert.setContentText("An error occurred during file transfer!: " + ftr.getMessageType().toString());

                        alert.showAndWait();
                        GUI.OpenedFile.unset();
                        break;

                }
            }

        } else if (message instanceof SimpleAnswer) {
            SimpleAnswer sa = (SimpleAnswer) message;
            log.debug("I received the simpleAnswer {} from the server", sa.getAnswer());
            if (sa.getAnswer() == true) {
                File modifile = new File(GuiActor.getFilePath() + GUI.OpenedFile.getName());
                GuiActor.getClusterListenerActorRef().tell(
                        new EndModify(GUI.OpenedFile.getName(), modifile.length()),
                        GuiActor.getGuiActorRef());
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Insufficient free space");
                alert.setContentText("You have not enough free space to store the changes you've done to " + GUI.OpenedFile.getName() + "!");

                alert.showAndWait();
                System.out.printf("simple answer handling if answer is not: Is the gui shown? %s", GUI.getStage().isShowing());

                /*
                 TODO: handle rollback:
                 deleting all tags and the file itself
                 can find infos in OpenedFile
                 */
            }
            GUI.OpenedFile.unset();

        } else if (message instanceof UpdateFreeSpace) {
            UpdateFreeSpace ufs = (UpdateFreeSpace) message;
            DecimalFormat df = new DecimalFormat("#0.00");
            Label l = (Label) GUI.getStage().getScene().lookup("#freeSpaceLabel");
            String formattedFreeSpace = (ufs.getFreeSpace() < 1000) ? ufs.getFreeSpace() + " B"
                    : (ufs.getFreeSpace() < 1000000) ? df.format((double) ufs.getFreeSpace() / 1024) + " KiB"
                            : (ufs.getFreeSpace() < 1000000000) ? df.format((double) ufs.getFreeSpace() / 1048576) + " MiB"
                                    : df.format((double) ufs.getFreeSpace() / 1073741824) + " GiB";
            l.setText(formattedFreeSpace);
            if (ufs.getFreeSpace() < 20 * 1024 * 1024) {
                l.setTextFill(Color.web("#ff0000"));
            } else {
                l.setTextFill(Color.web("#00ff00"));
            }
            log.debug("update free space: " + formattedFreeSpace);

        } else if (message instanceof GuiShutdown) {
            getSelf().tell(PoisonPill.getInstance(), getSelf());
            Platform.exit();

        } else if (message instanceof ProgressUpdate) {
            ProgressUpdate pu = (ProgressUpdate) message;
            Label label = (Label) GUI.getSecondaryStage().getScene().lookup("#label");
            ProgressBar pb = (ProgressBar) GUI.getSecondaryStage().getScene().lookup("#progrBar");
            double progress = ((double) pu.getCompletion()) / pu.getTotal();

            label.setText("Status " + (int) (100 * progress) + "% (" + pu.getCompletion() + "/" + pu.getTotal() + ")");
            pb.setProgress(progress);

        }
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
