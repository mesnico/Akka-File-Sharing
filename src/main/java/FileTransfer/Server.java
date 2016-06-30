// ------------ THINGS TO MODIFY ------------ //
// 
// 
// See comments below
//
// 
// 
// ------------------------------------------ //
package FileTransfer;

import Utils.Utilities;
import ClusterListenerActor.messages.EndModify;
import ClusterListenerActor.messages.InitiateShutdown;
import ClusterListenerActor.messages.LeaveAndClose;
import ClusterListenerActor.messages.SendDeleteInfos;
import ClusterListenerActor.messages.SpreadInfos;
import FileTransfer.messages.AllocateAndSpreadRequest;
import FileTransfer.messages.AllocateOnlyRequest;
import FileTransfer.messages.AllocationRequest;
import FileTransfer.messages.AuthorizationReply;
import FileTransfer.messages.EnumEnding;
import FileTransfer.messages.EnumFileModifier;
import FileTransfer.messages.FileListRequest;
import FileTransfer.messages.FileListResponse;
import FileTransfer.messages.FileTransferResult;
import FileTransfer.messages.Handshake;
import FileTransfer.messages.Hello;
import FileTransfer.messages.SendFreeSpaceSpread;
import FileTransfer.messages.SimpleAnswer;
import FileTransfer.messages.UpdateFileEntry;
import Utils.AddressResolver;
import Utils.WatchMe;
import java.net.InetSocketAddress;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.actor.UntypedActorWithStash;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.io.Tcp;
import akka.io.Tcp.Bound;
import akka.io.Tcp.CommandFailed;
import akka.io.Tcp.Connected;
import akka.io.TcpMessage;
import akka.japi.Procedure;
import com.typesafe.config.Config;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Map.Entry;

public class Server extends UntypedActorWithStash {

    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    private Config config = getContext().system().settings().config();
    private long myFreeSpace;
    private ActorSelection myClusterListener;
    private ActorSelection myGuiActor;
    private FileTable fileTable;
    private int localClusterSystemPort;
    private int tcpPort;
    private final String filePath;
    private final String tmpFilePath = System.getProperty("java.io.tmpdir");
    private ActorSelection soulReaper, fileTransferSoulReaper;
    private long initialFreeSpace;

    public Server() {
        filePath = config.getString("app-settings.file-path");
        myFreeSpace = config.getLong("app-settings.dedicated-space");
        initialFreeSpace = myFreeSpace;
        tcpPort = config.getInt("app-settings.server-port");
        localClusterSystemPort = config.getInt("akka.remote.netty.tcp.port");
        System.out.println(filePath + "--" + myFreeSpace + "--" + tcpPort);

        fileTable = retrieveFileTable();
        fileTable.keepConsistent();
        myFreeSpace -= fileTable.getTotalOccupiedSpace();
    }

    // ----------------------------- //
    // ---- ROLL BACK FUNCTIONS ---- //
    // ----------------------------- //
    private void senderRollBack(FileTransferResult fileTransferResult) {
        String fileName = fileTransferResult.getFileName();
        if (fileTransferResult.getFileModifier() == EnumFileModifier.WRITE) {
            log.info("Rollback: freeing file {} {}", filePath + fileName);
            fileTable.setOccupied(fileName, false);
        }
    }

    private void receiverRollBack(FileTransferResult fileTransferResult) {
        // --- We have to delete the entry for the file, free the
        // --- corresponding space and delete the received part of the file  
        String fileName = fileTransferResult.getFileName();
        if (fileTransferResult.getFileModifier() == EnumFileModifier.WRITE) {
            log.info("Rollback: Deleting fileEntry and corresponding corrupted file {}", filePath + fileName);
            FileElement e = fileTable.deleteEntry(fileName);
            if (e == null) {
                log.info("The rollBack has no effect on {}", fileName);
            } else {
                myFreeSpace += e.getSize();
            }
            File corruptedFile = new File(filePath + fileName);

            // --- if I have not updated search tags and casually the file to delete in the rollback is mine,
            // --- an error occurs (the file is really deleted). To avoid this, the last check (the messageType)
            // --- has to be taken in consideration
            if (corruptedFile.exists() && corruptedFile.canWrite() && fileTransferResult.getMessageType() != EnumEnding.FILE_TO_RECEIVE_NOT_EXISTS) {
                corruptedFile.delete();
                log.debug("Now the file must be deleted");
            }
        }
    }

    private void allocate(AllocationRequest request, boolean isTransfer) throws Exception {
        //log.debug("An allocationRequest arrived, with ");

        if (!isTransfer && request.getSize() == 0) {
            boolean occupied = request.isBusy();
            FileElement newElement = new FileElement(occupied, request.getSize(),
                    request.getTags());
            if (fileTable.createOrUpdateEntry(request.getFileName(), newElement) == false) {
                log.error("Someone tried to send me the file {} I already own", request.getFileName());
            }

            //spread the tags
            SpreadInfos tagsMessage = new SpreadInfos(request.getFileName(),
                    request.getTags(),
                    Utilities.computeIdByAddress(Utilities.getAddress(getSelf().path().address(), localClusterSystemPort)));
            myClusterListener.tell(tagsMessage, getSelf());

            log.debug("Received AllocationRequest. The size was 0 so no SimpleAnswer is sent back");
        } else {
            if (myFreeSpace >= request.getSize()) {
                myFreeSpace -= request.getSize();
                boolean occupied = request.isBusy();
                FileElement newElement = new FileElement(occupied, request.getSize(),
                        request.getTags());
                if (fileTable.createOrUpdateEntry(request.getFileName(), newElement) == false) {
                    log.error("Someone tried to send me the file {} I already own", request.getFileName());
                }

                if (!isTransfer) {
                    //spread the tags
                    SpreadInfos tagsMessage = new SpreadInfos(request.getFileName(),
                            request.getTags(),
                            Utilities.computeIdByAddress(Utilities.getAddress(getSelf().path().address(), localClusterSystemPort)));
                    myClusterListener.tell(tagsMessage, getSelf());
                }

                getSender().tell(new SimpleAnswer(true), getSelf());
                log.debug("Received AllocationRequest. Sending out the response: true");
            } else {
                getSender().tell(new SimpleAnswer(false), getSelf());
                FileTransferResult dummyTransferResult
                        = new FileTransferResult(EnumEnding.FILE_RECEIVING_FAILED,
                                request.getFileName(), EnumFileModifier.WRITE);
                receiverRollBack(dummyTransferResult);
                log.debug("Received AllocationRequest. Sending out the response: false");
            }
            if (!isTransfer) {
                myClusterListener.tell(new SendFreeSpaceSpread(myFreeSpace), getSelf());
            }
        }
    }

    @Override
    public void preStart() throws Exception {
        myClusterListener = getContext().actorSelection("akka.tcp://ClusterSystem@" + AddressResolver.getMyIpAddress() + ":"
                + localClusterSystemPort + "/user/clusterListener");
        soulReaper = getContext().actorSelection("akka.tcp://ClusterSystem@" + AddressResolver.getMyIpAddress() + ":"
                + localClusterSystemPort + "/user/mainSoulReaper");
        fileTransferSoulReaper = getContext().actorSelection("akka.tcp://ClusterSystem@" + AddressResolver.getMyIpAddress() + ":"
                + localClusterSystemPort + "/user/clusterListener/fileTransferSoulReaper");
        myGuiActor = getContext().actorSelection("akka.tcp://ClusterSystem@" + AddressResolver.getMyIpAddress() + ":"
                + localClusterSystemPort + "/user/gui");
        // TODO: the server, at boot time, has to read from the fileTable stored on disk
        // which file it has, insert them in his fileTable, and calculate his freeSpace.
        // --- The server calculates its free space and tell the clusterListener to spread it into the cluster
        myClusterListener.tell(new SendFreeSpaceSpread(myFreeSpace), getSelf());

        // --- Subscrive to to the soul reaper
        soulReaper.tell(new WatchMe(), getSelf());

        final ActorRef tcpManager = Tcp.get(getContext().system()).manager();
        tcpManager.tell(
                TcpMessage.bind(
                        getSelf(),
                        new InetSocketAddress(
                                AddressResolver.getMyIpAddress(),
                                tcpPort),
                        100),
                getSelf());
    }

    // -------------------------------------------------------- //
    // ---- BOUND RESULT AND CONNECTION TENTATIVE HANDLING ---- //
    // -------------------------------------------------------- //
    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof Bound) {
            myClusterListener.tell(msg, getSelf()); //Are we interested in this? (Bind was successful)
        } else if (msg instanceof Hello) {
            getContext().watch(getSender());
            Hello hello = (Hello) msg;
            // --- the addressTable is used for concurrency purposes among different client
            // --- requests
            Hello newHello = new Hello(AddressResolver.getMyIpAddress(), tcpPort);
            getSender().tell(newHello, getSelf());

            unstashAll();
            getContext().become(waitingForConnection(hello.getPort(), getSender()), false);

        } else if (msg instanceof CommandFailed) {
            getContext().stop(getSelf()); //in this case we may bring down the application (bind failed)

        // ---------------------------- //
        // ---- ALLOCATION REQUEST ---- //
        // ---------------------------- //
        } else if (msg instanceof AllocateOnlyRequest) {
            AllocateOnlyRequest request = (AllocateOnlyRequest) msg;
            allocate(request, true);
        } else if (msg instanceof AllocateAndSpreadRequest) {
            AllocateAndSpreadRequest request = (AllocateAndSpreadRequest) msg;
            allocate(request, false);
        } else if (msg instanceof UpdateFileEntry) {
            boolean permit;
            UpdateFileEntry updateRequest = (UpdateFileEntry) msg;
            log.debug("UpdateFileEntry was received: {}", updateRequest);

            FileElement toUpdate = fileTable.getFileElement(updateRequest.getFileName());
            if (toUpdate == null) {
                log.error("Fatal error! The FileEntry was not present for file {}", updateRequest.getFileName());
                return;
            }
            toUpdate.setOccupied(updateRequest.isOccupied());

            //the size update concerns only the difference between the new and old size of the file
            if (myFreeSpace >= updateRequest.getSize() - toUpdate.getSize()) {
                permit = true;
                myFreeSpace -= updateRequest.getSize() - toUpdate.getSize();
                toUpdate.setSize(updateRequest.getSize());

            } else {
                permit = false;
                //delete infos
                myClusterListener.tell(new SendDeleteInfos(updateRequest.getFileName(), toUpdate.getTags()), getSelf());
                FileTransferResult dummyTransferResult
                        = new FileTransferResult(EnumEnding.FILE_RECEIVING_FAILED,
                                updateRequest.getFileName(), EnumFileModifier.WRITE);
                receiverRollBack(dummyTransferResult);
            }

            //tell the cluster the updated size
            myClusterListener.tell(new SendFreeSpaceSpread(myFreeSpace), getSelf());

            SimpleAnswer answer = new SimpleAnswer(permit);
            getSender().tell(answer, getSelf());

        } 
        
        // --------------------------- //
        // ---- HANDSHAKE MESSAGE ---- //
        // --------------------------- //        
        else if (msg instanceof Handshake) {
            // --- A FileTransferActor wants to send a file. I have to verify if it exists, is busy,
            // --- or if it's available. In the last case I have to mark it as  busy and
            // --- send back, togheder with the reply, the file's size and tags.            
            Handshake handshake = (Handshake) msg;
            AuthorizationReply reply = fileTable.testAndSet(handshake.getFileName(), handshake.getModifier());
            log.debug("Received Handshake. Sending out AuthReply: {}", reply);
            getSender().tell(reply, getSelf());
        } 

        // ------------------------------ //
        // ---- FILE TRANSFER RESULT ---- //
        // ------------------------------ //
        else if (msg instanceof FileTransferResult) {
            FileTransferResult fileTransferResult = ((FileTransferResult) msg);
            String fileName = fileTransferResult.getFileName(); //TODO: serve?
            log.debug("FileTransferResult is: {}", fileTransferResult);

            switch (fileTransferResult.getMessageType()) {
                case CONNECTION_FAILED:
                    if (fileTransferResult.isAsker()) {
                        myGuiActor.tell(msg, getSelf());
                    }
                    break;

                case FILE_TO_RECEIVE_BUSY:
                case FILE_TO_RECEIVE_NOT_EXISTS:
                case NOT_ENOUGH_SPACE_FOR_RECEIVING:
                case FILE_RECEIVING_FAILED:
                case IO_ERROR_WHILE_RECEIVING:
                    receiverRollBack(fileTransferResult);
                    if (fileTransferResult.isAsker()) {
                        myGuiActor.tell(msg, getSelf());
                    }
                    break;

                case FILE_RECEIVED_SUCCESSFULLY:
                    if (fileTransferResult.getFileModifier() == EnumFileModifier.WRITE) {
                        SendFreeSpaceSpread spaceToPublish = new SendFreeSpaceSpread(myFreeSpace);
                        myClusterListener.tell(spaceToPublish, getSelf());

                        SpreadInfos tagsMessage = new SpreadInfos(fileName,
                                fileTable.getFileElement(fileName).getTags(),
                                Utilities.computeIdByAddress(Utilities.getAddress(getSelf().path().address(), localClusterSystemPort)));
                        myClusterListener.tell(tagsMessage, getSelf());
                    }

                    if (fileTransferResult.getFileModifier() == EnumFileModifier.WRITE
                            && fileTransferResult.isAsker() == true) {
                        System.out.printf("I, the server, have received a"
                                + "FILE_RECEIVED_SUCCESSFULLY on file %s that I was requesting"
                                + "in write mode, so I have to occupy it\n", fileTransferResult.getFileName());
                        // --- We have asked a file for modifying it, so it must be occupied.
                        FileElement toUpdate = fileTable.getFileElement(fileTransferResult.getFileName());
                        toUpdate.setOccupied(true);
                    }
                    if (fileTransferResult.isAsker()) {
                        myGuiActor.tell(msg, getSelf());
                    }
                    break;

                case FILE_TO_SEND_NOT_EXISTS:
                case NOT_ENOUGH_SPACE_FOR_SENDING:
                case IO_ERROR_WHILE_SENDING:
                case FILE_NO_MORE_BUSY:
                case FILE_SENDING_FAILED:
                    senderRollBack(fileTransferResult);
                    //myGuiActor.tell(msg, getSelf());
                    break;

                case FILE_TO_SEND_BUSY:
                    //myGuiActor.tell(msg, getSelf());
                    break;

                case FILE_OPENING_FAILED:
                    File fileToDelete = new File(filePath + fileName);
                    if (fileToDelete.exists() && fileToDelete.canWrite()) {
                        fileToDelete.delete();
                        log.debug("I've just deleted the sent file {}", fileName);
                    } else {
                        log.warning("File {} deleting failed. File does not exist: {}; File not writable: {}",
                                fileName, !fileToDelete.exists(), !fileToDelete.canWrite());
                    }
                    break;

                case FILE_SENT_SUCCESSFULLY:
                    if (fileTransferResult.getFileModifier() == EnumFileModifier.WRITE) {
                        FileElement e = fileTable.deleteEntry(fileName);
                        if (e == null) {
                            log.error("File entry for file {} does't exist", fileName);
                        } else {
                            myFreeSpace += e.getSize();
                            SendFreeSpaceSpread spaceToPublish = new SendFreeSpaceSpread(myFreeSpace);
                            myClusterListener.tell(spaceToPublish, getSelf());
                            File sentFile = new File(filePath + fileName);
                            if (sentFile.exists() && sentFile.canWrite()) {
                                sentFile.delete();
                                log.debug("I've just deleted the sent file {}", fileName);
                            } else {
                                log.warning("File {} deleting failed. File does not exist: {}; File not writable: {}",
                                        fileName, !sentFile.exists(), !sentFile.canWrite());
                            }
                        }
                    }
                    break;
            }

        } else if (msg instanceof FileListRequest) {
            FileListRequest request = (FileListRequest) msg;
            FileListResponse response = new FileListResponse(fileTable.getKeySet(), request.getMemberRemovedId());
            getSender().tell(response, getSelf());

        } else if (msg instanceof InitiateShutdown) {
            //virtually remove myself from the priority queue putting a very low size
            myFreeSpace = -initialFreeSpace;

            //send as many messages as entries in the FileTable to tell the cluster listener to spread all my files
            //away in the cluster
            for (Entry<String, FileElement> e : fileTable.asSet()) {
                EndModify spreadMessage = new EndModify(e.getKey(), e.getValue().getSize());
                myClusterListener.tell(spreadMessage, getSelf());
            }

            //if no entries in the table I can kill all the remaining actors. Otherwise the
            //fileTransferSoulReaper has to wait for the termination of the server and clusterListener
            if (fileTable.asSet().isEmpty()) {
                myClusterListener.tell(new LeaveAndClose(), getSelf());
                getSelf().tell(PoisonPill.getInstance(), getSelf());
            }

        } else {
            stash();
        }
    }

    private Procedure<Object> waitingForConnection(int remoteClusterSystemPort, ActorRef remoteActor) {
        return new Procedure<Object>() {
            String remoteActorName = remoteActor.path().name();
            
            @Override
            public void apply(Object msg) throws Exception {
                if (msg instanceof Connected) {
                    Connected conn = (Connected) msg;
                    InetSocketAddress remoteAddress = conn.remoteAddress();
                    //myClusterListener.tell(conn, getSelf()); //Are we interested in this? (a client connected to us)

                    final ActorRef handler = getContext().actorOf(Props.create(FileTransferActor.class,
                            remoteAddress.getAddress(), remoteClusterSystemPort, remoteActorName, getSender()));
                    getSender().tell(TcpMessage.register(handler), getSelf());
                    log.debug("I, the server, have received a connection request and I've accepted it");
                    getContext().unwatch(remoteActor);
                    unstashAll();
                    getContext().unbecome();
                } else if (msg instanceof Terminated){
                    log.error("Remote FileTransferActor is dead. Stopped waiting for him");
                    unstashAll();
                    getContext().unbecome();
                } else {
                    stash();
                }
            }
        };
    }

    private FileTable retrieveFileTable() {
        FileTable fTable;
        try {
            FileInputStream fin = new FileInputStream(filePath + "fileTable.ser");
            ObjectInputStream ois = new ObjectInputStream(fin);
            fTable = (FileTable) ois.readObject();
            log.info("fileTable found on disk. It is loaded");

            ois.close();
            fin.close();
        } catch (FileNotFoundException e) {
            log.info("FileTable not found on disk. It is created just now");
            fTable = new FileTable(filePath);
        } catch (IOException | ClassNotFoundException e) {
            log.error("Cannot do I/O on serialized fileTable: {}", e.getMessage());
            fTable = new FileTable(filePath);
        }
        return fTable;
    }
}
