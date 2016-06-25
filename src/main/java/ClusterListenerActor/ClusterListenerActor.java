package ClusterListenerActor;

import Utils.Utilities;
import ClusterListenerActor.messages.InitiateShutdown;
import ClusterListenerActor.messages.EndModify;
import ClusterListenerActor.messages.FileInfoTransfer;
import ClusterListenerActor.messages.FreeSpaceSpread;
import ClusterListenerActor.messages.CreationRequest;
import ClusterListenerActor.messages.UpdateInfos;
import ClusterListenerActor.messages.CreationResponse;
import ClusterListenerActor.messages.DeleteInfos;
import ClusterListenerActor.messages.SendDeleteInfos;
import ClusterListenerActor.messages.LeaveAndClose;
import ClusterListenerActor.messages.SpreadInfos;
import ClusterListenerActor.messages.TagSearchRequest;
import ClusterListenerActor.messages.TagSearchResponse;
import FileTransfer.FileTransferActor;
import FileTransfer.FileTransferSoulReaper;
import FileTransfer.messages.AuthorizationReply;
import FileTransfer.messages.EnumBehavior;
import FileTransfer.messages.EnumEnding;
import FileTransfer.messages.FileListRequest;
import FileTransfer.messages.FileListResponse;
import FileTransfer.messages.FileTransferResult;
import FileTransfer.messages.Handshake;
import FileTransfer.messages.SendFreeSpaceSpread;
import GUI.messages.GuiShutdown;
import GUI.messages.SearchRequest;
import GUI.messages.SendCreationRequest;
import GUI.messages.SendFileRequest;
import GUI.messages.UpdateFreeSpace;
import Utils.AddressResolver;
import Utils.WatchMe;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Address;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.ClusterEvent.CurrentClusterState;
import akka.cluster.ClusterEvent.MemberEvent;
import akka.cluster.ClusterEvent.MemberRemoved;
import akka.cluster.ClusterEvent.MemberUp;
import akka.cluster.ClusterEvent.UnreachableMember;
import akka.cluster.Member;
import akka.cluster.MemberStatus;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.typesafe.config.Config;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author nicky
 */
public class ClusterListenerActor extends UntypedActor {

    LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    private final Config config = getContext().system().settings().config();
    private final Cluster cluster = Cluster.get(getContext().system());
    private final HashMembersData membersMap;
    private final FreeSpaceMembersData membersFreeSpace;
    private final FileInfoDistributedTable infoTable;
    private final FoundFiles foundFiles;
    private final String localAddress;
    private ActorSelection guiActor, soulReaper, server;
    private ActorRef mediator;

    private final int clusterSystemPort;
    private long myFreeSpace = 0;
    private long initialFreeSpace;

    public ClusterListenerActor() throws Exception {
        this.clusterSystemPort = config.getInt("akka.remote.netty.tcp.port");

        //transforms the local reference to remote reference to uniformate hash accesses in case of remote actor
        Address mineAddress = getSelf().path().address();
        localAddress = Utilities.getAddress(mineAddress, clusterSystemPort);

        //construct the members map and member free space map
        membersMap = new HashMembersData();
        membersFreeSpace = new FreeSpaceMembersData();
        infoTable = new FileInfoDistributedTable();
        foundFiles = new FoundFiles();

        initialFreeSpace = config.getLong("app-settings.dedicated-space");
    }

    //subscribe to cluster changes
    @Override
    public void preStart() throws Exception {
        //#subscribe
        cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(),
                MemberEvent.class, UnreachableMember.class);
        //#subscribe

        //subscribe to the freeSpaceTopic
        mediator
                = DistributedPubSub.get(getContext().system()).mediator();
        mediator.tell(new DistributedPubSubMediator.Subscribe("freeSpaceTopic", getSelf()),
                getSelf());

        //create the references to the other actors
        soulReaper = getContext().actorSelection("akka.tcp://ClusterSystem@" + AddressResolver.getMyIpAddress() + ":" + clusterSystemPort + "/user/mainSoulReaper");
        guiActor = getContext().actorSelection("akka.tcp://ClusterSystem@" + AddressResolver.getMyIpAddress() + ":" + clusterSystemPort + "/user/gui");
        server = getContext().actorSelection("akka.tcp://ClusterSystem@" + AddressResolver.getMyIpAddress() + ":" + clusterSystemPort + "/user/server");

        //subscrive to to the soul reaper
        soulReaper.tell(new WatchMe(), getSelf());
    }

    //re-subscribe when restart
    @Override
    public void postStop() {
        cluster.unsubscribe(getSelf());
    }

    @Override
    public void onReceive(Object message) throws Exception {
        if (message instanceof DistributedPubSubMediator.SubscribeAck) {
            log.info("Subscribed to freeSpace Topic");
        } else if (message instanceof MemberUp) {
            MemberUp mMemberUp = (MemberUp) message;
            log.info("{} -->Member is Up: {}", localAddress, Utilities.getAddress(mMemberUp.member().address(), clusterSystemPort));

            //inserts the new member in the map
            membersMap.newMember(Utilities.computeIdByAddress(Utilities.getAddress(mMemberUp.member().address(), clusterSystemPort)), mMemberUp.member());    //IS IT RIGHT?
            log.info("new Member inserted in membersMap: {}", mMemberUp.member());
            log.debug("Current membersMap status: {} ", membersMap);

            //transfer control of the right tags to the new node, if it is my predecessor.
            //if it is my predecessor
            if (Utilities.computeIdByAddress(localAddress).compareTo(
                    membersMap.getSuccessorById(Utilities.computeIdByAddress(Utilities.getAddress(mMemberUp.member().address(), clusterSystemPort)))) == 0) {
                //collect the infoTable entries to be passed to the arrived node
                FileInfoTransfer fit = infoTable.buildFileInfoTransfer(membersMap, Utilities.computeIdByAddress(localAddress));
                //send the infos to the new responsible
                getContext().actorSelection(mMemberUp.member().address() + "/user/clusterListener")
                        .tell(fit, getSelf());
                log.debug("Sending out file info: {}", fit.toString());
            }

            //send my free space to the new arrived member, if the new arrivde member is not myself
            if (!mMemberUp.member().address().equals(cluster.selfAddress())) {
                getContext().actorSelection(mMemberUp.member().address() + "/user/clusterListener")
                        .tell(new FreeSpaceSpread(myFreeSpace), getSelf());
            }
        } else if (message instanceof UnreachableMember) {
            UnreachableMember mMemberUnreachable = (UnreachableMember) message;
            log.info("Member detected as unreachable: {}", mMemberUnreachable.member());

        } else if (message instanceof MemberRemoved) {
            MemberRemoved mMemberRemoved = (MemberRemoved) message;
            BigInteger memberRemovedId = Utilities.computeIdByAddress(Utilities.getAddress(mMemberRemoved.member().address(), clusterSystemPort));

            //if I am removed myself from the cluster, then it is time to commit suicide
            if (mMemberRemoved.member().address().equals(cluster.selfAddress())) {
                guiActor.tell(new GuiShutdown(), getSelf());
                getSelf().tell(PoisonPill.getInstance(), getSelf());
            }

            if (mMemberRemoved.previousStatus() == MemberStatus.down()) {
                //the member was removed because crashed
                
                //i request the file list to the server in order to check for file name tags
                //that must be regenerated
                server.tell(new FileListRequest(memberRemovedId),getSelf());
                log.info("Member {} is removed because crashed!!", mMemberRemoved.member());
            } else {
                //the member shutted down gracefully
                log.info("Member {} left the cluster gracefully", mMemberRemoved.member());
            }

            //in any case, the member is removed from the local structure
            Member removed = membersMap.deleteMemberById(memberRemovedId);
            if (removed == null) {
                log.error("Member {} does not exist in membersMap!!", mMemberRemoved.member());
            } else {
                log.info("Member removed in membersMap: {}", mMemberRemoved.member());
                log.debug("Current membersMap status: {} ", membersMap);
            }

            //the member is also removed from the free space queue
            //the only way is to search it by member address and to delete it.
            boolean exists = membersFreeSpace.deleteByMember(Utilities.computeIdByAddress(Utilities.getAddress(mMemberRemoved.member().address(), clusterSystemPort)));
            if (!exists) {
                log.error("Member {} does not exist in membersFreeSpace!!", mMemberRemoved.member());
            } else {
                log.info("Member removed in membersFreeSpace: {}", mMemberRemoved.member());
                log.debug("Current membersFreeSpace status: {} ", membersFreeSpace);
            }

            //message sent when the new member arrives in the cluster. The map has to be immediately filled with the current state
        } else if (message instanceof CurrentClusterState) {
            CurrentClusterState state = (CurrentClusterState) message;
            for (Member member : state.getMembers()) {
                if (member.status().equals(MemberStatus.up())) {
                    membersMap.newMember(Utilities.computeIdByAddress(Utilities.getAddress(member.address(), clusterSystemPort)), member);
                }
            }
            log.info("membersMap initialized: {}", membersMap);
        
        } else if (message instanceof FileListResponse){
            FileListResponse flr = (FileListResponse) message;
            BigInteger memberRemovedId = flr.getMemberRemovedId();
            BigInteger myself = Utilities.computeIdByAddress(Utilities.getAddress(getSelf().path().address(), clusterSystemPort));
            
            for(String fileName : flr.getFileList()){
                BigInteger tagId = Utilities.computeId(fileName);
                if(membersMap.getResponsibleById(tagId).equals(membersMap.getSuccessorById(memberRemovedId))){
                    log.info("Tag fileName for file {} is going to be regenerated", fileName);
                    SpreadInfos newInfo = new SpreadInfos(fileName, Collections.EMPTY_LIST, myself);
                    getSelf().tell(newInfo, getSelf());
                }
            }

        } else if (message instanceof SendFreeSpaceSpread) {
            long freeByteSpace = ((SendFreeSpaceSpread) message).getFreeByteSpace();
            // --- keep the freeSpace also in an updated clusterListener variable,
            // --- so that members entering could be immediately notified
            myFreeSpace = freeByteSpace;

            //tell my free space to all the other cluster nodes
            mediator.tell(new DistributedPubSubMediator.Publish("freeSpaceTopic", new FreeSpaceSpread(freeByteSpace)),
                    getSelf());
            //tell the GuiActor to update the showing free space
            guiActor.tell(new UpdateFreeSpace(myFreeSpace), getSelf());

        } else if (message instanceof FreeSpaceSpread) {
            //insert the received information about the free space in the current priority queue.
            FreeSpaceSpread receivedFreeSpace = (FreeSpaceSpread) message;
            System.out.println(getSelf() + " " + getSender());
            membersFreeSpace.updateMemberFreeSpace(Utilities.computeIdByAddress(Utilities.getAddress(getSender().path().address(), clusterSystemPort)), receivedFreeSpace.getFreeByteSpace());  //IS IT RIGHT?
            log.info("Added {} with {} bytes free", Utilities.getAddress(getSender().path().address(), clusterSystemPort), receivedFreeSpace.getFreeByteSpace());
            log.debug("Free space infos: {}", membersFreeSpace);

        } else if (message instanceof EndModify) {//this message is generated at the end of the modify operation, to begine the load distribution
            EndModify mNewFileCreation = (EndModify) message;
            String fileName = mNewFileCreation.getFileName();

            //load distribution
            BigInteger newOwnerId = membersFreeSpace.getHighestFreeSpaceMember();
            log.debug("The chosen one for taking care of {} is {}", fileName, newOwnerId);
            Member newOwner = membersMap.getMemberById(newOwnerId);

            //if the member with higher free space has a negative free space, it means that all nodes are quitting,
            //so it's useless to distribute the files I own. I kill all.
            if (membersFreeSpace.getHighestFreeSpace() < 0) {
                server.tell(PoisonPill.getInstance(), getSelf());
                getSelf().tell(new LeaveAndClose(), getSelf());
            }

            //check if i'm the choosen by the load distribution
            if (newOwnerId.equals(Utilities.computeIdByAddress(localAddress))) {
                //no transfer is needed
                log.info("I am the receiver of the load balancing of file {}", fileName);
            } else {
                log.info("I going send the file to {} for load balancing", newOwner);
                //file transfer SEND
                final ActorRef asker = getContext().actorOf(Props
                        .create(FileTransferActor.class,
                                InetAddress.getByName(newOwner.address().host().get()),
                                (int) newOwner.address().port().get(),
                                new Handshake(EnumBehavior.SEND, fileName)),
                        "fileTransferAsker" + UUID.randomUUID().toString());

                //don't need to update tags... this is performed at the end of the file send
                //now we have just to create the file into the FileTable of my server
            }

        } else if (message instanceof SendFileRequest) {
            SendFileRequest fileRequest = (SendFileRequest) message;
            Member fileOwner = membersMap.getMemberById(fileRequest.getOwnerId());

            if (fileOwner == null) {
                log.warning("The file {} cannot be transferred! The owner is falled", fileRequest.getFileName());
                FileTransferResult transferResult = new FileTransferResult(
                        EnumEnding.FILE_TO_RECEIVE_NOT_EXISTS, fileRequest.getFileName(), fileRequest.getModifier());
                guiActor.tell(transferResult, getSelf());
                return;
            }

            //check if the owner is myself
            if (Utilities.computeIdByAddress(Utilities.getAddress(fileOwner.address(), clusterSystemPort))
                    .equals(Utilities.computeIdByAddress(localAddress))) {
                //no transfer is needed

                log.info("Not needed to transfer the file {} from remote: the owner is myself!", fileRequest.getFileName());

                //ask for the freeness of the file in order to be opened in write mode
                Handshake handshake = new Handshake(null, fileRequest.getFileName(), fileRequest.getModifier());
                server.tell(handshake, getSelf());

            } else {
                log.info("Starting remote transfering for the file {}", fileRequest.getFileName());
                //file transfer REQUEST
                final ActorRef asker = getContext().actorOf(Props
                        .create(FileTransferActor.class,
                                InetAddress.getByName(fileOwner.address().host().get()),
                                (int) fileOwner.address().port().get(),
                                new Handshake(EnumBehavior.REQUEST, fileRequest.getFileName(), fileRequest.getModifier())),
                        "fileTransferAsker" + UUID.randomUUID().toString());
            }

        } else if (message instanceof AuthorizationReply) {
            AuthorizationReply reply = (AuthorizationReply) message;
            EnumEnding ending;
            switch (reply.getResponse()) {
                case AUTHORIZATION_GRANTED:
                    ending = EnumEnding.OWNER_IS_MYSELF;
                    break;
                case FILE_BUSY:
                    ending = EnumEnding.FILE_TO_RECEIVE_BUSY;
                    break;
                case FILE_NOT_EXISTS:
                    ending = EnumEnding.FILE_TO_RECEIVE_NOT_EXISTS;
                    break;
                default:
                    ending = EnumEnding.FILE_TO_RECEIVE_NOT_EXISTS;
            }
            FileTransferResult result = new FileTransferResult(ending,
                    reply.getFileName(), reply.getModifier());
            guiActor.tell(result, getSelf());

        } else if (message instanceof SendCreationRequest) {
            //I send the creation request (the filename tag) to the responsible member
            //so that it can perform the checks and return me a CreationResponse
            SendCreationRequest cr = (SendCreationRequest) message;
            Member responsibleMember = membersMap.getResponsibleMemberById(
                    Utilities.computeId(cr.getFileName()));
            getContext().actorSelection(responsibleMember.address() + "/user/clusterListener")
                    .tell(new CreationRequest(cr.getFileName()), getSelf());

        } else if (message instanceof CreationRequest) {
            //i am the responsible for the new filename tag. I have to add it to my FileInfoDistributedTable, only if it does not exist yet.
            String fileName = ((CreationRequest) message).getFileName();
            boolean success = infoTable.testAndSet(fileName, fileName, Utilities.computeIdByAddress(Utilities.getAddress(getSender().path().address(), clusterSystemPort)));
            getSender().tell(new CreationResponse(success), getSelf());

            log.debug("Received a creation request. \n Success:{}\nCurrent info table is: {}", success, infoTable.toString());
            log.debug("tag id: {}", Utilities.computeId(fileName));

        } else if (message instanceof CreationResponse) {
            //forward the response to the GUI actor
            guiActor.tell((CreationResponse) message, getSelf());

        } else if (message instanceof SearchRequest) {
            //reset the found file structure for the new search
            foundFiles.reset();

            SearchRequest sr = (SearchRequest) message;
            Member responsibleMember;
            //foreach tag (tags + filename) I send the request for the search
            for (String tag : sr.getSearchString().split(" ")) {
                responsibleMember = membersMap.getResponsibleMemberById(
                        Utilities.computeId(tag));
                getContext().actorSelection(responsibleMember.address() + "/user/clusterListener")
                        .tell(new TagSearchRequest(tag), getSelf());
                System.out.println("sent tag for " + tag);
            }

        } else if (message instanceof TagSearchRequest) {
            TagSearchRequest tsr = (TagSearchRequest) message;
            //lookup and retrieve the requested file info
            //then send it to the sender
            List<FileInfoElement> requested = infoTable.getByTag(tsr.getTag());

            //if the tag exists on this node, it is sent; otherwise the search query is ignored
            if (requested != null) {
                getSender().tell(new TagSearchResponse(requested), getSelf());
            }

        } else if (message instanceof TagSearchResponse) {
            List<FileInfoElement> receivedFileInfo = ((TagSearchResponse) message).getReturnedList();
            //aggiungo tutti gli elementi 
            foundFiles.addAll(receivedFileInfo);

            //tell the GUI actor the calculated response list
            guiActor.tell(foundFiles.createGuiResponse(), getSelf());

        } else if (message instanceof SpreadInfos) {
            //used from the EndModify and end of file transfer
            SpreadInfos msg = (SpreadInfos) message;
            log.debug("Spreading infos for file {}",msg.getFileName());
            
            Member responsible;
            for (String tag : msg.getTags()) {
                //If the member where i'm going to put the tag is closing, then I put the tag on its successor
                responsible = getNonClosingResponsable(tag);
                getContext().actorSelection(responsible.address() + "/user/clusterListener")
                        .tell(new UpdateInfos(msg.getFileName(), tag, msg.getOwnerId()), getSelf());
            }

            //Also the file name information has to be stored as like as other tags
            responsible = getNonClosingResponsable(msg.getFileName());
            getContext().actorSelection(responsible.address() + "/user/clusterListener")
                    .tell(new UpdateInfos(msg.getFileName(), msg.getFileName(), msg.getOwnerId()), getSelf());

        } else if (message instanceof UpdateInfos) {
            //Receved a information for wich I'm the responsible
            UpdateInfos mAddTag = (UpdateInfos) message;
            infoTable.updateTag(mAddTag.getFileName(), mAddTag.getTag(), mAddTag.getOwnerId());
            log.info("Received tag: {}", mAddTag.toString());
            log.debug("Current File Info Table: {}", infoTable.toString());

        } else if (message instanceof SendDeleteInfos) {
            SendDeleteInfos delInfos = (SendDeleteInfos) message;
            Member responsible;
            for (String tag : delInfos.getTags()) {
                //If the member where i'm going to put the tag is closing, then I put the tag on its successor
                responsible = getNonClosingResponsable(tag);
                getContext().actorSelection(responsible.address() + "/user/clusterListener")
                        .tell(new DeleteInfos(delInfos.getFileName(), tag), getSelf());
            }

            //Also the file name information has to be deleted as like as other tags
            responsible = getNonClosingResponsable(delInfos.getFileName());
            getContext().actorSelection(responsible.address() + "/user/clusterListener")
                    .tell(new DeleteInfos(delInfos.getFileName(), delInfos.getFileName()), getSelf());

        } else if (message instanceof DeleteInfos) {
            DeleteInfos delInfos = (DeleteInfos) message;
            log.debug("deleting the tag {} for the file {}", delInfos.getTag(), delInfos.getFileName());
            infoTable.removeByTagAndName(delInfos.getTag(), delInfos.getFileName());
            log.debug("Current File Info Table: {}", infoTable.toString());

        } else if (message instanceof FileInfoTransfer) {
            FileInfoTransfer infos = (FileInfoTransfer) message;

            //merge the arrived file informations into the local structure
            infoTable.mergeInfos(infos);
            log.info("Received File Infos: {}", infos.toString());
            log.debug("Current File Info Table: {}", infoTable.toString());

        } else if (message instanceof InitiateShutdown) {
            //create the FileTransfer soul reaper in order to close the server and the cluster listener
            //only when all the transfers are completed
            //create the Soul Reaper actor to watch out all the others
            getContext().actorOf(Props.create(FileTransferSoulReaper.class, server, guiActor, getSelf()), "fileTransferSoulReaper");

            log.info("The system is going to shutdown!");

            //transfer all my infos to my successor node
            Member newInfoResponsable = membersMap.getSuccessorMemberById(
                    Utilities.computeIdByAddress(localAddress));
            //prepare the fileinfo transfer message
            FileInfoTransfer fit = infoTable.buildFileInfoTransfer(membersMap,
                    Utilities.computeIdByAddress(Utilities.getAddress(newInfoResponsable.address(), clusterSystemPort)));
            //send the infos to the new responsible
            getContext().actorSelection(newInfoResponsable.address() + "/user/clusterListener")
                    .tell(fit, getSelf());

            //virtually remove myself from the priority queue putting a very low size
            myFreeSpace = -initialFreeSpace;
            membersFreeSpace.updateMemberFreeSpace(Utilities.computeIdByAddress(localAddress), myFreeSpace);
            FreeSpaceSpread veryLowSpace = new FreeSpaceSpread(myFreeSpace);
            mediator.tell(new DistributedPubSubMediator.Publish("freeSpaceTopic", veryLowSpace),
                    getSelf());

            server.tell(new InitiateShutdown(), getSelf());

        } else if (message instanceof LeaveAndClose) {

            cluster.leave(cluster.selfAddress());

        } else if (message instanceof MemberEvent) {
            // ignore

        } else {
            unhandled(message);
        }
    }

    private Member getNonClosingResponsable(String tag) {
        Member nonClosingResponsable;
        BigInteger realResponsible = membersMap.getResponsibleById(Utilities.computeId(tag));
        if (membersFreeSpace.getFreeSpaceElement(realResponsible).freeByteSpace < 0) {
            nonClosingResponsable = membersMap.getSuccessorMemberById(realResponsible);
        } else {
            nonClosingResponsable = membersMap.getResponsibleMemberById(realResponsible);
        }
        return nonClosingResponsable;
    }
}
