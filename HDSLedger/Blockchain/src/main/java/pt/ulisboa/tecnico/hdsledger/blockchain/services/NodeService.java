package pt.ulisboa.tecnico.hdsledger.blockchain.services;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.sql.Time;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import com.google.gson.Gson;

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import pt.ulisboa.tecnico.hdsledger.communication.AppendRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.AppendRequestResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.BlockchainResponseMessage;
import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.LeaderChangeMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.PrePrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.RoundChangeMessage;
import pt.ulisboa.tecnico.hdsledger.communication.StartConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.TransactionV1;
import pt.ulisboa.tecnico.hdsledger.communication.TransactionV2;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.communication.cripto.CriptoUtils;
import pt.ulisboa.tecnico.hdsledger.blockchain.models.CryptocurrencyStorage;
import pt.ulisboa.tecnico.hdsledger.blockchain.models.InstanceInfo;
import pt.ulisboa.tecnico.hdsledger.blockchain.models.MessageBucket;
import pt.ulisboa.tecnico.hdsledger.utilities.ByzantineBehavior;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSSException;
import pt.ulisboa.tecnico.hdsledger.utilities.Pair;
import pt.ulisboa.tecnico.hdsledger.utilities.RandomIntGenerator;
import pt.ulisboa.tecnico.hdsledger.utilities.RandomStringGenerator;
import pt.ulisboa.tecnico.hdsledger.utilities.ServerConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.Utils;

public class NodeService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(NodeService.class.getName());
    // Nodes configurations
    private final ServerConfig[] nodesConfig;

    // Current node is leader
    private final ServerConfig config;

    // Link to communicate with nodes
    private final Link linkToNodes;
    // Link to communicate with clients
    private final Link linkToClients;

    // Consensus instance -> Round -> List of prepare messages
    private final MessageBucket prepareMessages;
    // Consensus instance -> Round -> List of commit messages
    private final MessageBucket commitMessages;
    // Consensus instance -> Round -> List of round change messages
    private final MessageBucket roundChangeMessages;

    // Store if already received pre-prepare for a given <consensus, round>
    private final Map<Integer, Map<Integer, Boolean>> receivedPrePrepare = new ConcurrentHashMap<>();
    // Consensus instance information per consensus instance
    private final Map<Integer, InstanceInfo> instanceInfo = new ConcurrentHashMap<>();
    // Current consensus instance
    private final AtomicInteger consensusInstance = new AtomicInteger(0);
    // Last decided consensus instance
    private final AtomicInteger lastDecidedConsensusInstance = new AtomicInteger(0);

    // Ledger (for now, just a list of strings)
    private ArrayList<TransactionV2> ledger = new ArrayList<TransactionV2>();

    private CriptoUtils criptoUtils;

    private CriptoService criptoService;

    public NodeService(
        Link linkToNodes, 
        ServerConfig config, 
        ServerConfig[] nodesConfig, 
        Link linkToClients, 
        CriptoUtils criptoUtils
    ) {
        this.linkToNodes = linkToNodes;
        this.linkToClients = linkToClients;
        this.config = config;
        this.nodesConfig = nodesConfig;
        this.criptoUtils = criptoUtils;

        this.prepareMessages = new MessageBucket(nodesConfig.length);
        this.commitMessages = new MessageBucket(nodesConfig.length);
        this.roundChangeMessages = new MessageBucket(nodesConfig.length);
    }

    public void setCriptoService(CriptoService criptoService) {
        this.criptoService = criptoService;
    }

    public ServerConfig getConfig() {
        return this.config;
    }

    public int getConsensusInstance() {
        return this.consensusInstance.get();
    }

    public ArrayList<TransactionV2> getLedger() {
        return this.ledger;
    }

    public String getNextLeader(String leaderId) {
        for (int u = 0; u < nodesConfig.length; u++) {
            if (leaderId.equals(nodesConfig[u].getId())) {
                if (u == nodesConfig.length - 1)
                    return nodesConfig[0].getId();
                else
                    return nodesConfig[u+1].getId();
            }
        }
        throw new HDSSException(ErrorMessage.ProgrammingError);
    }

    private ConsensusMessage createConsensusMessageCommon(String senderId, TransactionV2 value, int instance, int round, byte[] helloSignature) {
        PrePrepareMessage prePrepareMessage = new PrePrepareMessage(value, helloSignature);
        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(senderId, Message.Type.PRE_PREPARE)
                .setConsensusInstance(instance)
                .setRound(round)
                .setMessage(prePrepareMessage.toJson())
                .build();

        return consensusMessage;
    }
 
    public ConsensusMessage createConsensusMessage(TransactionV2 value, int instance, int round, byte[] helloSignature) {
        return createConsensusMessageCommon(config.getId(), value, instance, round, helloSignature);
    }

    public ConsensusMessage createConsensusMessage(String senderId, TransactionV2 value, int instance, int round, byte[] helloSignature) {   
        return createConsensusMessageCommon(senderId, value, instance, round, helloSignature);
    }

    private void broadcastRoundChangeMsg(int instanceNumber) {
        InstanceInfo instance = this.instanceInfo.get(instanceNumber);

        int currentRound = instance.getCurrentRound();
        int newRound = currentRound + 1;
        instance.setCurrentRound(newRound); // increments current round

        RoundChangeMessage message = new RoundChangeMessage(instance.getPreparedValue(), instance.getPreparedRound());

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
                .setConsensusInstance(instanceNumber)
                .setRound(newRound)
                .setMessage(message.toJson())
                .build();

        instance.schedualeTask(createRoundChangeTimerTask(instanceNumber));

        linkToNodes.broadcast(consensusMessage); // broadcasts ROUND_CHANGE message
    }


    // triggers round change
    private TimerTask createRoundChangeTimerTask(int instanceNumber) {
        return new TimerTask() {
            @Override
            public void run() {
                InstanceInfo instance = instanceInfo.get(instanceNumber);
                instance.setLeaderId(getNextLeader(instance.getLeaderId()));

                broadcastRoundChangeMsg(instanceNumber);
            }
        };
    }

    /**
     * Uses this node Private key to generate the signature.
     * @return the signature correspondent to the [transactionV2] value.
     */
    private byte[] generateValueSignature(TransactionV2 transactionV2) 
        throws 
            InvalidKeyException, 
            NoSuchAlgorithmException, 
            SignatureException, 
            InvalidKeySpecException, IOException 
    {
        byte[] messageToSign = Utils.joinArray(
            transactionV2.getSourceId().getBytes(), 
            transactionV2.getDestinationId().getBytes(), 
            Integer.toString(transactionV2.getAmount()).getBytes()
        );
        return criptoUtils.getMessageSignature(messageToSign, config.getId());
    }

    /**
     * @return the first instance where [nodeId] is leader.
     */
    private int getConsensusInstanceWithLeader(String nodeId) {
        for (int u = 0; u < nodesConfig.length; u++) {
            if (nodesConfig[u].getId().equals(nodeId))
                return u + 1;
        }
        throw new HDSSException(ErrorMessage.ProgrammingError); // Should be improved later!
    }

    private boolean waitMsAndBroadcastRoundChange(Long time, int consensusInstance) {
        try {
            Thread.sleep(time); // Gives time for other nodes to start initialize respective consensus instance
            broadcastRoundChangeMsg(consensusInstance);
            return true;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /*
     * Start an instance of consensus for a value
     * Only the current leader will start a consensus instance
     * the remaining nodes only update values.
     *
     * @param inputValue Value to value agreed upon
     */
    public void startConsensus(TransactionV1 transactionV1, byte[] valueSignature) {
    
        // Set initial consensus values
        int localConsensusInstance;

        if (config.getByzantineBehavior() == ByzantineBehavior.FAKE_CONSENSUS_INSTANCE) {
            localConsensusInstance = getConsensusInstanceWithLeader(config.getId()) + nodesConfig.length; // This only matters if self is the leader
            
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Node is Byzantine... Faking consensus instance number with {1}",
                    config.getId(), localConsensusInstance));
        } else {
            localConsensusInstance = this.consensusInstance.incrementAndGet();
        }

        String leaderId;
        
        // If self is faking instance number, 
        if (config.getByzantineBehavior() == ByzantineBehavior.FAKE_CONSENSUS_INSTANCE) {
            leaderId = config.getId();
        } else {
            leaderId = getLeaderId(localConsensusInstance);
        }

        TransactionV2 value = new TransactionV2(transactionV1, leaderId);

        InstanceInfo existingConsensus = this.instanceInfo.put(localConsensusInstance, new InstanceInfo(value, valueSignature, leaderId)); // should be putIfAbsent!!! WHat if he receives a PREPARE message first, and already creates a new InstanceInfo???

        InstanceInfo instance = this.instanceInfo.get(localConsensusInstance);

        instance.setLeaderId(leaderId);

        // If startConsensus was already called for a given round
        if (existingConsensus != null) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Node already started consensus for instance {1}",
                    config.getId(), localConsensusInstance));
        }

        // set timer
        instance.schedualeTask(createRoundChangeTimerTask(localConsensusInstance));

        // Leader broadcasts PRE-PREPARE message
        if (
            config.getId().equals(instance.getLeaderId()) ||
            config.getByzantineBehavior() == ByzantineBehavior.FAKE_LEADER ||
            config.getByzantineBehavior() == ByzantineBehavior.FAKE_LEADER_WITH_FORGED_PRE_PREPARE_MESSAGE
        ) {

            if (config.getByzantineBehavior() == ByzantineBehavior.NONE)
            LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Node is leader, sending PRE-PREPARE message", config.getId()));
        
            if (config.getByzantineBehavior() == ByzantineBehavior.FAKE_LEADER)
                LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Node is byzanine leader (FAKE-LEADER). Sending PRE-PREPARE message", config.getId()));
            
            if (config.getByzantineBehavior() == ByzantineBehavior.FAKE_LEADER_WITH_FORGED_PRE_PREPARE_MESSAGE)
                LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Node is byzanine leader (FORGED_PRE_PREPARE_MESSAGE). Sending PRE-PREPARE message with leaderId", config.getId()));
            

            // Proposes random generated value to all nodes.
            // Uses self Private Key to generate signature.
            if (
                config.getByzantineBehavior() == ByzantineBehavior.BAD_LEADER_PROPOSE_WITH_GENERATED_SIGNATURE
                ||
                config.getByzantineBehavior() == ByzantineBehavior.BAD_LEADER_PROPOSE_WITH_ORIGINAL_SIGNATURE
            ) {
                for (ServerConfig node : nodesConfig) {
                    TransactionV2 randomValue = TransactionV2.createRandom();
                    try {
                        byte[] signature;
                        if (config.getByzantineBehavior() == ByzantineBehavior.BAD_LEADER_PROPOSE_WITH_GENERATED_SIGNATURE)
                            signature = generateValueSignature(randomValue);
                        else
                            signature = valueSignature;

                        this.linkToNodes.send(node.getId(), this.createConsensusMessage(randomValue, localConsensusInstance, instance.getCurrentRound(), signature));
                    } catch (Exception e) {
                        throw new HDSSException(ErrorMessage.ProgrammingError); // Should be improved later!
                    }
                }
                return;
            } 

            // TODO: this only makes sense if the sent value is fake
            if (config.getByzantineBehavior() == ByzantineBehavior.FAKE_LEADER_WITH_FORGED_PRE_PREPARE_MESSAGE) {
                this.linkToNodes.broadcast(this.createConsensusMessage(instance.getLeaderId(), value, localConsensusInstance, instance.getCurrentRound(), valueSignature));    
                return;
            }
            
            this.linkToNodes.broadcast(this.createConsensusMessage(value, localConsensusInstance, instance.getCurrentRound(), valueSignature));    

        } else {
            if (config.getByzantineBehavior() == ByzantineBehavior.FORCE_ROUND_CHANGE) {
                LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Node is Byzantine. Broadcasting unexpected ROUND-CHANGE messages", 
                        config.getId()));
                waitMsAndBroadcastRoundChange(100L, localConsensusInstance);
            }
            else {
                LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Node is not leader. Waiting for PRE-PREPARE message", config.getId()));
            }
        }
    }

    private boolean justifyPrePrepareMessage(int instance, int round) {
        if (round == 1) return true;

        if (round > 1) {
            return justifyRoundChange(instance, round);
        } else {
            throw new HDSSException(ErrorMessage.ProgrammingError);
        }
    }

    private String getLeaderId(int instaceId) {
        String currentLeaderId = nodesConfig[0].getId();
        for (int currentInstanceCounter = 1; true; currentInstanceCounter++) {
            if (currentInstanceCounter == instaceId)
                return currentLeaderId;
            currentLeaderId = getNextLeader(currentLeaderId);
        }
    }

    private InstanceInfo createInstanceInfo(int instaceId, byte[] helloSignature, TransactionV2 value) {
        InstanceInfo instanceInfo;
        if (instaceId != 1)
            instanceInfo = new InstanceInfo(value, helloSignature, getNextLeader(getLeaderId(instaceId-1)));
        else
            instanceInfo = new InstanceInfo(value, helloSignature, nodesConfig[0].getId());

        return instanceInfo;
    }

    /**
     * Returns the bytes that were used to sign value message.
     */
    private byte[] getOriginalValue(TransactionV2 value) {
        return Utils.joinArray(
            value.getSourceId().getBytes(), 
            value.getDestinationId().getBytes(), 
            Integer.toString(value.getAmount()).getBytes()
        );
    }

    /*
     * Handle pre prepare messages and if the message
     * came from leader and is justified then broadcast prepare
     *
     * @param message Message to be handled
     */
    public void uponPrePrepare(ConsensusMessage message) {

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        String senderId = message.getSenderId();
        int senderMessageId = message.getMessageId();

        PrePrepareMessage prePrepareMessage = message.deserializePrePrepareMessage();

        TransactionV2 value = prePrepareMessage.getValue();
        byte[] valueSignature = prePrepareMessage.getValueSignature();
        
        try {
            byte[] originalMessage = getOriginalValue(value);

            if (!criptoUtils.verifySignatureWithClientKey(originalMessage, valueSignature, value.getSourceId())) {
                LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Received PRE-PREPARE message from {1} Consensus Instance {2}, Round {3}, but the value could not be verified",
                            config.getId(), senderId, consensusInstance, round));    
                return;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - {1}\n{2}", config.getId(), e.getLocalizedMessage()));
            throw new HDSSException(ErrorMessage.ProgrammingError);
        }
        

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PRE-PREPARE message from {1} Consensus Instance {2}, Round {3}",
                        config.getId(), senderId, consensusInstance, round));

        // Set instance value
        this.instanceInfo.putIfAbsent(consensusInstance, createInstanceInfo(consensusInstance, valueSignature, value));
                        
        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        // Verify if pre-prepare was sent by leader
        if (!senderId.equals(instance.getLeaderId())) { // compare against the current round and not the one in the received message
            LOGGER.log(Level.WARNING,
                    MessageFormat.format("{0} - Received PRE-PREPARE message from a node {1}, that is not the lider. Not doing anything.", 
                    config.getId(), senderId));
            return;
        }

        try {
            if (!justifyPrePrepareMessage(consensusInstance, round)) {
                LOGGER.log(Level.WARNING,
                    MessageFormat.format("{0} - Received PRE-PREPARE message from a node {1}, that is not justified.", 
                    config.getId(), senderId));
                return;
            }
        } catch(Exception e) {
            // TODO
        }

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        receivedPrePrepare.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        if (receivedPrePrepare.get(consensusInstance).put(round, true) != null) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received PRE-PREPARE message for Consensus Instance {1}, Round {2}, "
                                    + "replying again to make sure it reaches the initial sender",
                            config.getId(), consensusInstance, round));
        }
        
        // set timer
        instance.schedualeTask(createRoundChangeTimerTask(consensusInstance));

        PrepareMessage prepareMessage = new PrepareMessage(prePrepareMessage.getValue(), valueSignature);

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PREPARE)
                .setConsensusInstance(consensusInstance)
                .setRound(round)
                .setMessage(prepareMessage.toJson())
                .setReplyTo(senderId)
                .setReplyToMessageId(senderMessageId)
                .build();

        LOGGER.log(Level.INFO,
            MessageFormat.format(
                "{0} - Broadcasting PREPARE message for intance {1}, round {2}",
                config.getId(), consensusInstance, round));

        this.linkToNodes.broadcast(consensusMessage);
    }

    /*
     * Handle prepare messages and if there is a valid quorum broadcast commit
     *
     * @param message Message to be handled
     */
    public synchronized void uponPrepare(ConsensusMessage message) {

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        String senderId = message.getSenderId();

        LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Received PREPARE message from {1}: Consensus Instance {2}, Round {3}",
                    config.getId(), senderId, consensusInstance, round));

        PrepareMessage prepareMessage = message.deserializePrepareMessage();

        TransactionV2 value = prepareMessage.getValue();
        byte[] valueSignature = prepareMessage.getValueSignature();

        try {
            byte[] originalMessage = getOriginalValue(value);

            if (!criptoUtils.verifySignatureWithClientKey(originalMessage, valueSignature, value.getSourceId())) {
                LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Received PREPARE message from {1} Consensus Instance {2}, Round {3}, but the value could not be verified",
                            config.getId(), senderId, consensusInstance, round));    
                return;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Error: {1}", config.getId(), e.getMessage()));
            throw new HDSSException(ErrorMessage.ProgrammingError);
        }

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PREPARE message from {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), senderId, consensusInstance, round));

        // Doesn't add duplicate messages
        prepareMessages.addMessage(message);

        // Set instance values
        this.instanceInfo.putIfAbsent(consensusInstance, createInstanceInfo(consensusInstance, valueSignature, value));

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        // Late prepare (consensus already ended for other nodes) only reply to him (as
        // an ACK)
        if (instance.getPreparedRound() >= round) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received PREPARE message for Consensus Instance {1}, Round {2}, "
                                    + "replying again to make sure it reaches the initial sender",
                            config.getId(), consensusInstance, round));

            ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                    .setConsensusInstance(consensusInstance)
                    .setRound(round)
                    .setReplyTo(senderId)
                    .setReplyToMessageId(message.getMessageId())
                    .setMessage(instance.getCommitMessage().toJson())
                    .build();

            linkToNodes.send(senderId, m);
            return;
        }

        // Find value with valid quorum
        Optional<TransactionV2> preparedValue = prepareMessages.hasValidPrepareQuorum(config.getId(), consensusInstance, round);
        if (preparedValue.isPresent() && instance.getPreparedRound() < round) {
            
            LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Received quorum of PREPARE messages for instance {1}",
                    config.getId(), consensusInstance));

            instance.setPreparedRound(round);

            // generates a random value with random size
            if (config.getByzantineBehavior() == ByzantineBehavior.BYZANTINE_UPON_PREPARE_QUORUM) {
                
                LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Node is byzantine, setting a fake/random PREPARE VALUE after receiving quorum of PREPARE-MESSAGE's", config.getId()));

                TransactionV2 randomValue = TransactionV2.createRandom();
                instance.setPreparedValue(randomValue);
            } else
                instance.setPreparedValue(preparedValue.get());

            // Must reply to prepare message senders
            Collection<ConsensusMessage> sendersMessage = prepareMessages.getMessages(consensusInstance, round)
                    .values();

            CommitMessage c = new CommitMessage(instance.getPreparedValue());
            instance.setCommitMessage(c);

            sendersMessage.forEach(senderMessage -> {
                ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                        .setConsensusInstance(consensusInstance)
                        .setRound(round)
                        .setReplyTo(senderMessage.getSenderId())
                        .setReplyToMessageId(senderMessage.getMessageId())
                        .setMessage(c.toJson())
                        .build();

                linkToNodes.send(senderMessage.getSenderId(), m);
            });
        }
    }


    /*
     * Handle commit messages and decide if there is a valid quorum
     *
     * @param message Message to be handled
     */
    public synchronized void uponCommit(ConsensusMessage message) {

        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Received COMMIT message from {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), message.getSenderId(), consensusInstance, round));

        commitMessages.addMessage(message);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        if (instance == null) {
            // Should never happen because only receives commit as a response to a prepare message
            MessageFormat.format(
                    "{0} - CRITICAL: Received COMMIT message from {1}: Consensus Instance {2}, Round {3} BUT NO INSTANCE INFO",
                    config.getId(), message.getSenderId(), consensusInstance, round);
            return;
        }

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        if (instance.getCommittedRound() >= round) {
            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Already received COMMIT message for Consensus Instance {1}, Round {2}, ignoring",
                            config.getId(), consensusInstance, round));
            return;
        }

        Optional<TransactionV2> commitValue = commitMessages.hasValidCommitQuorum(config.getId(),
                consensusInstance, round);
                
        if (commitValue.isPresent()) {
            LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Received quorum of COMMIT messages for instance {1}",
                    config.getId(), consensusInstance));
        }

        if (commitValue.isPresent() && instance.getCommittedRound() < round) {

            // stop timer
            instance.cancelTimer();

            instance = this.instanceInfo.get(consensusInstance);
            instance.setCommittedRound(round);

            TransactionV2 value = commitValue.get();

            // Only proceeds appends to ledger if the last one was decided
            // We need to be sure that the previous value has been appended
            if (!(lastDecidedConsensusInstance.get() < consensusInstance - 1)) {
                tryAppendValue(consensusInstance, round, value);
                
                // Deals with pending decided values
                while (true) {
                    int nextConsensusInstance = this.lastDecidedConsensusInstance.get() + 1;
                    InstanceInfo nextInstanceInfo = this.instanceInfo.get(nextConsensusInstance);
                    
                    if (nextInstanceInfo == null || nextInstanceInfo.getCommittedRound() == -1) return;

                    tryAppendValue(nextConsensusInstance, nextInstanceInfo.getCommittedRound(), nextInstanceInfo.getInputValue());
                }
            } else {
                LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Consensus instance: {1}. There is lower consensus instances that were not yet decided. Doing nothing...",
                        config.getId(), consensusInstance));
            }
        }
    }

    private void appendToLedger(int consensusInstance, TransactionV2 value) {
        // Append value to the ledger (must be synchronized to be thread-safe)
        synchronized(ledger) {
            // Increment size of ledger to accommodate current instance
            ledger.ensureCapacity(consensusInstance);
            
            int index = consensusInstance - 1;
            ledger.add(index, value);
            
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Current Ledger size: {1}", config.getId(), ledger.size()));

        }
    }

    private boolean existentInLedger(UUID requestUuid) {
        for (TransactionV2 transactionV2 : ledger) {
            if (transactionV2.getRequestUUID().equals(requestUuid))
                return true;
        }
        return false;
    }

    /**
     * Appends the value to the ledger if the transaction is validated, and increments [lastDecidedConsensusInstance]
     */
    private void tryAppendValue(int consensusInstance, int round, TransactionV2 value) {

        // The transaction could already be appended in a previous consensus instance, if two nodes have received the same request in diferent orders.
        // They will propose the same transaction in a diferent consensus instance.
        if(!existentInLedger(value.getRequestUUID())) {

            // Applies transaction and warn client
            // At this point, every node will decide the same transaction. Therefore, this transaction will be successfull or not for every node.
            // For instance, T1 could invalidate T2, waiting for T1, because it removed every unit in the account.
            boolean successfull = criptoService.applyTransaction(value);

            if (successfull)
                appendToLedger(consensusInstance, value);
        } else {
            LOGGER.log(
                Level.INFO, MessageFormat.format("{0} - Transaction {1} was already appended to ledger in a different previous consensus instance! Not appending...", 
                    config.getId(), value.getRequestUUID()));
        }

        // Will be always incremented despite transaction is appended or not.
        // Therefore, next consensus instances could be decided.
        lastDecidedConsensusInstance.getAndIncrement();

        LOGGER.log(Level.WARNING,
                MessageFormat.format(
                        "{0} - Decided on Consensus Instance {1}, Round {2}, Successful? {3}",
                        config.getId(), consensusInstance, round, true));
    }

    private boolean justifyRoundChange(int instance, int round) {
        if (roundChangeMessages.areAllRoundChangeMessagesNotPrepared(config.getId(), instance, round))
            return true;

        Optional<Pair<Integer, TransactionV2>> heighestPreparedRoundAndValue = roundChangeMessages.getHeighestPreparedRoundAndValueIfAny(config.getId(), instance, round);
        int heighestPreparedRound = heighestPreparedRoundAndValue.get().getKey();
        TransactionV2 heighestPreparedValue = heighestPreparedRoundAndValue.get().getValue();
        
        if (prepareMessages.checkRoundAndValue(config.getId(), instance, round, heighestPreparedRound, heighestPreparedValue))
            return true;

        return false;
    }

    private int getSmallerRound(ConsensusMessage[] messages) {
        int smallerRound = Integer.MAX_VALUE;
        
        for (int u = 0; u < messages.length; u++) {
            int round = messages[u].getRound();
            if (round < smallerRound)
                smallerRound = round;
        }
        return smallerRound;
    }

    // TODO: log next leader for debug porposes
    public void uponRoundChange(ConsensusMessage message) {
        int roundChangeMsgConsensusInstance = message.getConsensusInstance();
        int roundChangeMsgRound = message.getRound();

        LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Received ROUND_CHANGE message from {1}: Consensus Instance {2}, Round {3}",
                    config.getId(), message.getSenderId(), roundChangeMsgConsensusInstance, roundChangeMsgRound));

        roundChangeMessages.addMessage(message);

        // if CHANGE-ROUND consensus instance was already decided
        if (lastDecidedConsensusInstance.get() >= roundChangeMsgConsensusInstance) {

            LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Received ROUND_CHANGE message from {1}: Consensus Instance {2}, Round {3}. Consensus instance was already decided. Broadcasting commit messages to sender...",
                    config.getId(), message.getSenderId(), roundChangeMsgConsensusInstance, roundChangeMsgRound));

            int commitedRound = this.instanceInfo.get(roundChangeMsgConsensusInstance).getCommittedRound();
            Collection<ConsensusMessage> receivedCommitMessages = commitMessages.getMessages(roundChangeMsgConsensusInstance, commitedRound)
                    .values();

            // sends the whole quorum
            for (ConsensusMessage msg : receivedCommitMessages)
                linkToNodes.send(message.getSenderId(), msg);
        }

        InstanceInfo roundChangeMsgInstance = this.instanceInfo.get(roundChangeMsgConsensusInstance);
        
        // TODO: confirm this!!!
        if (roundChangeMsgInstance == null) {
            LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Consensus Instance {1} is not stored! Doing nothing...",
                    config.getId(), roundChangeMsgConsensusInstance));
            return;   
        }

        // null if there isn't a set
        ConsensusMessage[] msgs = roundChangeMessages.getQuorumWithRoundGreaterThan(config.getId(), roundChangeMsgConsensusInstance, roundChangeMsgRound);

        if (msgs != null) { // if there is a set of f+1 msgs
            int smallerRound = getSmallerRound(msgs);
            roundChangeMsgInstance.setCurrentRound(smallerRound);
            roundChangeMsgInstance.schedualeTask(createRoundChangeTimerTask(message.getConsensusInstance())); // set timer
            broadcastRoundChangeMsg(message.getConsensusInstance());
        }
        
        if (
            roundChangeMessages.hasValidRoundChangeQuorum(config.getId(), roundChangeMsgConsensusInstance, roundChangeMsgRound) &&
            config.getId().equals(roundChangeMsgInstance.getLeaderId()) && 
            justifyRoundChange(roundChangeMsgConsensusInstance, roundChangeMsgRound)
        ) {
            Optional<Pair<Integer, TransactionV2>> heighestPreparedRoundAndValue = roundChangeMessages.getHeighestPreparedRoundAndValueIfAny(config.getId(), roundChangeMsgConsensusInstance, roundChangeMsgRound);

            LOGGER.log(Level.INFO,
                        MessageFormat.format("{0} - Received justified quorum of ROUND_CHANGE messages and Iam the leader",
                            config.getId()));

            // Changes the input value to a newly generated random value.
            if (config.getByzantineBehavior() == ByzantineBehavior.BYZANTINE_UPON_ROUND_CHANGE_QUORUM) {

                LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Node is byzantine, setting a fake/random VALUE in round change", config.getId()));

                TransactionV2 randomValue = TransactionV2.createRandom();
                roundChangeMsgInstance.setInputValue(randomValue);
            } else {
                
                if (heighestPreparedRoundAndValue.isPresent()) {
                    TransactionV2 newValue = heighestPreparedRoundAndValue.get().getValue();
                    roundChangeMsgInstance.setInputValue(newValue);
                    LOGGER.log(Level.INFO,
                        MessageFormat.format("{0} - Received quorum of ROUND_CHANGE messages. Setting input value to {1}",
                            config.getId(), newValue));
                } else {
                    LOGGER.log(Level.INFO,
                        MessageFormat.format("{0} - Received quorum of ROUND_CHANGE messages. Input value will stay the same",
                            config.getId()));
                }

                // othewrise, value stays the same
            }

            LOGGER.log(Level.INFO,
                        MessageFormat.format("{0} - Broadcasting PRE-PREPARE messages for instance {1}",
                            config.getId(), roundChangeMsgConsensusInstance));

            // broadcast PRE PREPARE message
            this.linkToNodes.broadcast(
                this.createConsensusMessage(
                    roundChangeMsgInstance.getInputValue(), 
                    roundChangeMsgConsensusInstance, 
                    roundChangeMsgInstance.getCurrentRound(), 
                    roundChangeMsgInstance.getValueSignature()
                )
            );
        }
    }

    @Override
    public void listen() {
        try {
            // Thread to listen on every request
            new Thread(() -> {
                try {
                    while (true) {
                        Message message = linkToNodes.receive();

                        // Separate thread to handle each message
                        new Thread(() -> {

                            if (config.getByzantineBehavior() == ByzantineBehavior.IGNORE_REQUESTS) { // byzantine node
                                
                                LOGGER.log(Level.INFO,
                                MessageFormat.format("{0} - Byzantine node ignoring requests...",
                                        config.getId()));
                                        
                
                            } else {
                                switch (message.getType()) {

                                    case PRE_PREPARE ->
                                        uponPrePrepare((ConsensusMessage) message);
    
                                    case PREPARE ->
                                        uponPrepare((ConsensusMessage) message);
    
                                    case COMMIT ->
                                        uponCommit((ConsensusMessage) message);
    
                                    case ROUND_CHANGE ->
                                        uponRoundChange((ConsensusMessage) message);
    
                                    case ACK ->
                                        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received ACK message from {1}",
                                                config.getId(), message.getSenderId()));
    
                                    case IGNORE ->
                                        LOGGER.log(Level.INFO,
                                                MessageFormat.format("{0} - Received IGNORE message from {1}",
                                                        config.getId(), message.getSenderId()));
    
                                    /*
                                    default ->
                                        LOGGER.log(Level.INFO,
                                                MessageFormat.format("{0} - Received unknown message from {1}",
                                                        config.getId(), message.getSenderId()));
                                    */
    
                                }
                            }

                        }).start();
                    }
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
