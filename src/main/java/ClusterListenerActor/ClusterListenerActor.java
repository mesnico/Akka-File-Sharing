package ClusterListenerActor;

import ClusterListenerActor.messages.InitiateShutdown;
import ClusterListenerActor.messages.EndModify;
import ClusterListenerActor.messages.FileInfoTransfer;
import ClusterListenerActor.messages.FreeSpaceSpread;
import ClusterListenerActor.messages.CreationRequest;
import ClusterListenerActor.messages.UpdateTag;
import ClusterListenerActor.messages.CreationResponse;
import ClusterListenerActor.messages.Shutdown;
import ClusterListenerActor.messages.SpreadTags;
import ClusterListenerActor.messages.TagSearchRequest;
import ClusterListenerActor.messages.TagSearchResponse;
import FileTransfer.FileTransferActor;
import FileTransfer.messages.AuthorizationReply;
import FileTransfer.messages.EnumAuthorizationReply;
import FileTransfer.messages.EnumBehavior;
import FileTransfer.messages.EnumEnding;
import FileTransfer.messages.EnumFileModifier;
import FileTransfer.messages.FileTransferResult;
import FileTransfer.messages.Handshake;
import FileTransfer.messages.SendFreeSpaceSpread;
import FileTransfer.messages.UpdateFileEntry;
import GUI.messages.SearchRequest;
import GUI.messages.SendCreationRequest;
import GUI.messages.SendFileRequest;
import Startup.AddressResolver;
import Startup.WatchMe;
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
import java.util.List;

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
        soulReaper = getContext().actorSelection("akka.tcp://ClusterSystem@" + AddressResolver.getMyIpAddress() + ":" + clusterSystemPort + "/user/soulReaper");
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
            membersMap.newMember(Utilities.computeId(Utilities.getAddress(mMemberUp.member().address(), clusterSystemPort)), mMemberUp.member());    //IS IT RIGHT?
            log.info("new Member inserted in membersMap: {}", mMemberUp.member());
            log.debug("Current membersMap status: {} ", membersMap);

            //transfer control of the right tags to the new node, if it is my predecessor.
            //if it is my predecessor
            if (Utilities.computeId(Utilities.getAddress(getSelf().path().address(), clusterSystemPort)).compareTo(
                    membersMap.getSuccessorById(Utilities.computeId(Utilities.getAddress(mMemberUp.member().address(), clusterSystemPort)))) == 0) {
                //collect the infoTable entries to be passed to the arrived node
                FileInfoTransfer fit = infoTable.buildFileInfoTransfer(membersMap, Utilities.computeId(Utilities.getAddress(getSelf().path().address(), clusterSystemPort)));
                //send the infos to the new responsible
                getContext().actorSelection(mMemberUp.member().address() + "/user/clusterListener")
                        .tell(fit, getSelf());
                log.debug("Sending out file info: {}", fit.toString());
            }

            //send my free space to the new arrived member
            getContext().actorSelection(mMemberUp.member().address() + "/user/clusterListener")
                    .tell(new FreeSpaceSpread(myFreeSpace), getSelf());

        } else if (message instanceof UnreachableMember) {
            UnreachableMember mMemberUnreachable = (UnreachableMember) message;
            log.info("Member detected as unreachable: {}", mMemberUnreachable.member());

        } else if (message instanceof MemberRemoved) {
            MemberRemoved mMemberRemoved = (MemberRemoved) message;
            log.info("Member is Removed: {}", mMemberRemoved.member());

            if (mMemberRemoved.previousStatus() == MemberStatus.down()) {
                //the member was removed because crashed
            } else {
                //the member shutted down gracefully
            }

            //in any case, the member is removed from the local structure
            Member removed = membersMap.deleteMemberById(Utilities.computeId(Utilities.getAddress(mMemberRemoved.member().address(), clusterSystemPort)));
            if (removed == null) {
                log.error("Member {} does not exist in membersMap!!", mMemberRemoved.member());
            } else {
                log.info("Member removed in membersMap: {}", mMemberRemoved.member());
                log.debug("Current membersMap status: {} ", membersMap);
            }

            //the member is also removed from the free space queue
            //the only way is to search it by member address and to delete it.
            boolean exists = membersFreeSpace.deleteByMember(Utilities.computeId(Utilities.getAddress(mMemberRemoved.member().address(), clusterSystemPort)));
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
                    membersMap.newMember(Utilities.computeId(member.address().hostPort()), member);
                }
            }
            log.info("membersMap initialized: {}", membersMap);

        } else if (message instanceof SendFreeSpaceSpread) {
            long freeByteSpace = ((SendFreeSpaceSpread) message).getFreeByteSpace();
            // --- keep the freeSpace also in an updated clusterListener variable,
            // --- so that members entering could be immediately notified
            myFreeSpace = freeByteSpace;
            //tell my free space to all the other cluster nodes
            mediator.tell(new DistributedPubSubMediator.Publish("freeSpaceTopic", new FreeSpaceSpread(freeByteSpace)),
                    getSelf());

        } else if (message instanceof FreeSpaceSpread) {
            //insert the received information about the free space in the current priority queue.
            FreeSpaceSpread receivedFreeSpace = (FreeSpaceSpread) message;
            System.out.println(getSelf() + " " + getSender());
            membersFreeSpace.updateMemberFreeSpace(Utilities.computeId(Utilities.getAddress(getSender().path().address(), clusterSystemPort)), receivedFreeSpace.getFreeByteSpace());  //IS IT RIGHT?
            log.info("Added {} with {} bytes free", Utilities.getAddress(getSender().path().address(), clusterSystemPort), receivedFreeSpace.getFreeByteSpace());
            log.debug("Free space infos: {}", membersFreeSpace);

        } else if (message instanceof EndModify) {//this message is generated at the end of the modify operation, to begine the load distribution
            EndModify mNewFileCreation = (EndModify) message;
            String fileName = mNewFileCreation.getFileName();
            
            //load distribution
            BigInteger newOwnerId = membersFreeSpace.getHighestFreeSpaceMember();
            Member newOwner = membersMap.getMemberById(newOwnerId);

            //check if i'm the choosen by the load distribution
            if (newOwnerId.equals(Utilities.computeId(Utilities.getAddress(getSelf().path().address(), clusterSystemPort)))) {
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
                        "fileTransferAsker");
                
                //don't need to update tags... this is performed at the end of the file send
                //now we have just to create the file into the FileTable of my server
            }

        } else if (message instanceof SpreadTags) {
            //used from the EndModify and end of file transfer

            SpreadTags msg = (SpreadTags) message;
            for (String tag : msg.getTags()) {
                Member responsible = membersMap.getResponsibleMemberById(Utilities.computeId(tag));
                getContext().actorSelection(responsible.address() + "/user/clusterListener")
                        .tell(new UpdateTag(tag, msg.getFileName(), msg.getOwnerId()), getSelf());
            }

            //Also the file name information has to be stored as like as other tags
            Member responsible = membersMap.getResponsibleMemberById(Utilities.computeId(msg.getFileName()));
            getContext().actorSelection(responsible.address() + "/user/clusterListener")
                    .tell(new UpdateTag(msg.getFileName(), msg.getFileName(), msg.getOwnerId()), getSelf());

        } else if (message instanceof SendFileRequest) {
            SendFileRequest fileRequest = (SendFileRequest) message;
            Member fileOwner = membersMap.getMemberById(fileRequest.getOwnerId());

            //check if the owner is myself
            if (Utilities.computeId(Utilities.getAddress(fileOwner.address(), clusterSystemPort))
                    .equals(Utilities.computeId(Utilities.getAddress(getSelf().path().address(), clusterSystemPort)))) {
                //no transfer is needed

                log.info("Not needed to transfer the file {} from remote: the owner is myself!", fileRequest.getFileName());
                
                //ask for the freeness of the file in order to be opened in write mode
                Handshake handshake = new Handshake(null,fileRequest.getFileName(),EnumFileModifier.WRITE);
                server.tell(handshake, getSelf());
                
            } else {
                log.info("Starting remote transfering for the file {}", fileRequest.getFileName());
                //file transfer REQUEST
                final ActorRef asker = getContext().actorOf(Props
                        .create(FileTransferActor.class,
                                InetAddress.getByName(fileOwner.address().host().get()),
                                (int) fileOwner.address().port().get(),
                                new Handshake(EnumBehavior.REQUEST, fileRequest.getFileName(), fileRequest.getModifier())),
                        "fileTransferAsker");
            }
        
        } else if (message instanceof AuthorizationReply) {
            AuthorizationReply reply = (AuthorizationReply)message;
            FileTransferResult result;
            if(reply.getResponse() == EnumAuthorizationReply.AUTHORIZATION_GRANTED){
                result = new FileTransferResult(EnumEnding.FILE_RECEIVED_SUCCESSFULLY,
                        reply.getFileName(),EnumFileModifier.WRITE);
            } else {
                result = new FileTransferResult(EnumEnding.FILE_TO_RECEIVE_BUSY,
                        reply.getFileName(),EnumFileModifier.WRITE);
            }
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
            boolean success = infoTable.testAndSet(fileName, fileName, Utilities.computeId(Utilities.getAddress(getSender().path().address(), clusterSystemPort)));
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

        } else if (message instanceof UpdateTag) {
            //Receved a information for wich I'm the responsible
            UpdateTag mAddTag = (UpdateTag) message;
            infoTable.updateTag(mAddTag.getFileName(), mAddTag.getTag(), mAddTag.getOwnerId());
            log.info("Received tag: {}", mAddTag.toString());
            log.debug("Current File Info Table: {}", infoTable.toString());

        } else if (message instanceof FileInfoTransfer) {
            FileInfoTransfer infos = (FileInfoTransfer) message;

            //merge the arrived file informations into the local structure
            infoTable.mergeInfos(infos);
            log.info("Received File Infos: {}", infos.toString());
            log.debug("Current File Info Table: {}", infoTable.toString());

        } else if (message instanceof InitiateShutdown) {
            log.info("The system is going to shutdown!");
            //transfer all my infos to my successor node
            Member newInfoResponsable = membersMap.getSuccessorMemberById(
                    Utilities.computeId(Utilities.getAddress(getSelf().path().address(), clusterSystemPort)));
            //prepare the fileinfo transfer message
            FileInfoTransfer fit = infoTable.buildFileInfoTransfer(membersMap,
                    Utilities.computeId(Utilities.getAddress(newInfoResponsable.address(), clusterSystemPort)));
            //send the infos to the new responsible
            getContext().actorSelection(newInfoResponsable.address() + "/user/clusterListener")
                    .tell(fit, getSelf());

            getSelf().tell(new Shutdown(), getSelf());

        } else if (message instanceof Shutdown) {
            // --- Leave the cluster and stop the system. Then stop all needed actors, myself too
            cluster.leave(cluster.selfAddress());

            server.tell(PoisonPill.getInstance(), getSelf());
            getSelf().tell(PoisonPill.getInstance(), getSelf());

        } else if (message instanceof MemberEvent) {
            // ignore

        } else {
            unhandled(message);
        }
    }
}
