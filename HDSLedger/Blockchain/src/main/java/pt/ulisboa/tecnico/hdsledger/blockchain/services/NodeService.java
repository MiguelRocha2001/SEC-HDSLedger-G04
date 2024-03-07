package pt.ulisboa.tecnico.hdsledger.blockchain.services;

import java.io.IOException;
import java.sql.Time;
import java.text.MessageFormat;
import java.util.ArrayList;
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
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
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

public class NodeService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(NodeService.class.getName());
    // Nodes configurations
    private final ServerConfig[] nodesConfig;

    // Current node is leader
    private final ServerConfig config;

    // Link to communicate with nodes
    private final Link linkToNodes;
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

    private final ArrayList<Pair<String, String>> requests;

    // Ledger (for now, just a list of strings)
    private ArrayList<String> ledger = new ArrayList<String>();

    private long TIMEOUT = 999999;
    Timer timer;

    public NodeService(Link linkToNodes, ServerConfig config, ServerConfig[] nodesConfig, Link linkToClients, ArrayList<Pair<String, String>> requests) {

        this.linkToNodes = linkToNodes;
        this.linkToClients = linkToClients;
        this.config = config;
        this.nodesConfig = nodesConfig;
        this.requests = requests;

        this.prepareMessages = new MessageBucket(nodesConfig.length);
        this.commitMessages = new MessageBucket(nodesConfig.length);
        this.roundChangeMessages = new MessageBucket(nodesConfig.length);

    }

    public ServerConfig getConfig() {
        return this.config;
    }

    public int getConsensusInstance() {
        return this.consensusInstance.get();
    }

    public ArrayList<String> getLedger() {
        return this.ledger;
    }

    // TODO: what if there is only two nodes and round == 3 ?
    public ServerConfig getCurrentLeader(int round) {
        for (int u = 0; u < nodesConfig.length; u++) {
            if (u == round - 1) // remember: rounds start on 1
                return nodesConfig[u];
        }
        throw new HDSSException(ErrorMessage.ProgrammingError);
    }

    private boolean isLeader(String id, int round) {
        for (int u = 0; u < nodesConfig.length; u++) {
            if (u == round - 1) // remember: rounds start on 1
                return id.equals(nodesConfig[u].getId());
        }
        throw new HDSSException(ErrorMessage.ProgrammingError);
    }
 
    public ConsensusMessage createConsensusMessage(String value, int instance, int round) {
        PrePrepareMessage prePrepareMessage = new PrePrepareMessage(value);

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE)
                .setConsensusInstance(instance)
                .setRound(round)
                .setMessage(prePrepareMessage.toJson())
                .build();

        return consensusMessage;
    }

    // triggers round change
    private TimerTask createTimerTaks() {
        return new TimerTask() {
            @Override
            public void run() {
                int localConsensusInstance = consensusInstance.get();
                InstanceInfo consensus = instanceInfo.get(localConsensusInstance);
                
                int currentRound = consensus.getCurrentRound();
                int newRound = currentRound + 1;
                consensus.setCurrentRound(newRound); // increments current round
    
                RoundChangeMessage message = new RoundChangeMessage(consensus.getPreparedValue(), consensus.getPreparedRound());
    
                ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
                        .setConsensusInstance(consensusInstance.get())
                        .setRound(newRound)
                        .setMessage(message.toJson())
                        .build();
    
                linkToNodes.broadcast(consensusMessage); // broadcasts ROUND_CHANGE message
            }
        };
    }

    private void schedualeTask() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = new Timer();    
        } else {
            timer = new Timer();
        }
        timer.schedule(createTimerTaks(), TIMEOUT); // set timer
    }

    public enum StartConsensusResult {
        EXISTING_CONSENSUS,
        IAM_NOT_THE_LEADER,
        STARTED
    }

    /*
     * Start an instance of consensus for a value
     * Only the current leader will start a consensus instance
     * the remaining nodes only update values.
     *
     * @param inputValue Value to value agreed upon
     */
    public StartConsensusResult startConsensus(String value) {

        // Set initial consensus values
        int localConsensusInstance = this.consensusInstance.incrementAndGet();
        InstanceInfo existingConsensus = this.instanceInfo.put(localConsensusInstance, new InstanceInfo(value));

        // If startConsensus was already called for a given round
        if (existingConsensus != null) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Node already started consensus for instance {1}",
                    config.getId(), localConsensusInstance));
            return StartConsensusResult.EXISTING_CONSENSUS;
        }

        // Only start a consensus instance if the last one was decided
        // We need to be sure that the previous value has been decided
        while (lastDecidedConsensusInstance.get() < localConsensusInstance - 1) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        InstanceInfo instance = this.instanceInfo.get(localConsensusInstance);

        StartConsensusResult toReturn;

        // Leader broadcasts PRE-PREPARE message
        if (
            isLeader(config.getId(), instance.getCurrentRound()) ||
            config.getByzantineBehavior() == ByzantineBehavior.FAKE_LEADER
        ) {
            if (isLeader(config.getId(), instance.getCurrentRound())) {
                LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Node is leader, sending PRE-PREPARE message", config.getId()));
            } else {
                LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Node is byzanine leader, sending PRE-PREPARE message", config.getId()));
            }

            if (config.getByzantineBehavior() == ByzantineBehavior.NONE) // not byzantine
                this.linkToNodes.broadcast(this.createConsensusMessage(value, localConsensusInstance, instance.getCurrentRound()));

            // sends a different value to each process
            else {
                for (ServerConfig node : nodesConfig) {
                    int valueLength = RandomIntGenerator.generateRandomInt(1, 5);
                    String randomValue = RandomStringGenerator.generateRandomString(valueLength);
                    this.linkToNodes.send(node.getId(), this.createConsensusMessage(randomValue, localConsensusInstance, instance.getCurrentRound()));
                }
            }

            toReturn = StartConsensusResult.STARTED;
        } else {
            LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Node is not leader, waiting for PRE-PREPARE message", config.getId()));
            
            toReturn = StartConsensusResult.IAM_NOT_THE_LEADER;
        }

        // set timer
        schedualeTask();

        return toReturn;
    }

    private boolean justifyPrePrepareMessage(int instance, int round) {
        if (round == 1) return true;

        if (round > 1) {
            return justifyRoundChange(instance, round);
        } else {
            throw new HDSSException(ErrorMessage.ProgrammingError);
        }
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

        String value = prePrepareMessage.getValue();

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PRE-PREPARE message from {1} Consensus Instance {2}, Round {3}",
                        config.getId(), senderId, consensusInstance, round));
                        
        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        // Verify if pre-prepare was sent by leader
        if (!isLeader(senderId, instance.getCurrentRound())) { // compare against the current round and not the one in the received message
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

        }

        // Set instance value
        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(value));

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
        schedualeTask();

        PrepareMessage prepareMessage = new PrepareMessage(prePrepareMessage.getValue());

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PREPARE)
                .setConsensusInstance(consensusInstance)
                .setRound(round)
                .setMessage(prepareMessage.toJson())
                .setReplyTo(senderId)
                .setReplyToMessageId(senderMessageId)
                .build();

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

        PrepareMessage prepareMessage = message.deserializePrepareMessage();

        String value = prepareMessage.getValue();

        LOGGER.log(Level.INFO,
                MessageFormat.format(
                        "{0} - Received PREPARE message from {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), senderId, consensusInstance, round));

        // Doesn't add duplicate messages
        prepareMessages.addMessage(message);

        // Set instance values
        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(value));
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
        Optional<String> preparedValue = prepareMessages.hasValidPrepareQuorum(config.getId(), consensusInstance, round);
        if (preparedValue.isPresent() && instance.getPreparedRound() < round) {

            instance.setPreparedRound(round);

            // generates a random value with random size
            if (config.getByzantineBehavior() == ByzantineBehavior.BYZANTINE_UPON_PREPARE_QUORUM) {
                
                LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Node is byzantine, setting a fake/random PREPARE VALUE after receiving a prepare message", config.getId()));

                int valueLength = RandomIntGenerator.generateRandomInt(1, 5);
                String randomValue = RandomStringGenerator.generateRandomString(valueLength);
                instance.setPreparedValue(randomValue);
            } else
                instance.setPreparedValue(preparedValue.get());

            // Must reply to prepare message senders
            Collection<ConsensusMessage> sendersMessage = prepareMessages.getMessages(consensusInstance, round)
                    .values();

            CommitMessage c = new CommitMessage(preparedValue.get());
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

        Optional<String> commitValue = commitMessages.hasValidCommitQuorum(config.getId(),
                consensusInstance, round);

        if (commitValue.isPresent() && instance.getCommittedRound() < round) {

            // stop timer
            timer.cancel(); // cancells also the tasks

            instance = this.instanceInfo.get(consensusInstance);
            instance.setCommittedRound(round);

            String value = commitValue.get();

            // Append value to the ledger (must be synchronized to be thread-safe)
            synchronized(ledger) {

                // Increment size of ledger to accommodate current instance
                ledger.ensureCapacity(consensusInstance);
                while (ledger.size() < consensusInstance - 1) {
                    ledger.add("");
                }
                
                int index = consensusInstance - 1;
                ledger.add(index, value);
                
                LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Current Ledger: {1}",
                            config.getId(), String.join("", ledger)));

            }

            lastDecidedConsensusInstance.getAndIncrement();

            LOGGER.log(Level.INFO,
                    MessageFormat.format(
                            "{0} - Decided on Consensus Instance {1}, Round {2}, Successful? {3}",
                            config.getId(), consensusInstance, round, true));

            // Warns clients
            for (int u = 0; u < requests.size(); u++) {
                String clientId = requests.get(u).getKey();
                String valueToAppend = requests.get(u).getValue();
                if (valueToAppend.equals(value)) {
                    requests.remove(u);

                    LOGGER.log(Level.INFO,
                        MessageFormat.format(
                            "{0} - Sending APPEND_REQUEST_RESULT to client: {1}",
                            config.getId(), clientId));

                    
                    AppendRequestResultMessage result = new AppendRequestResultMessage(config.getId(), consensusInstance, valueToAppend);
                    String resultStr = new Gson().toJson(result);
                    linkToClients.send(clientId, new BlockchainResponseMessage(config.getId(), Message.Type.APPEND_REQUEST_RESULT, resultStr));
                }
            }

            // start new consensus instance, if theres more pending requests
            if (!requests.isEmpty()) {
                String nextRequestValueToAppend = requests.get(0).getValue();
                startConsensus(nextRequestValueToAppend);
            }
        }
    }

    private boolean justifyRoundChange(int instance, int round) {
        Optional<String> value = roundChangeMessages.getHeighestPreparedValueIfAny(config.getId(), instance, round);
        InstanceInfo instanceInfo = this.instanceInfo.get(instance);

        return !value.isPresent() || value.get() == instanceInfo.getPreparedValue();

    }

    public void uponRoundChange(ConsensusMessage message) {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        LOGGER.log(Level.INFO,
                MessageFormat.format("{0} - Received ROUND_CHANGE message from {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), message.getSenderId(), consensusInstance, round));

        roundChangeMessages.addMessage(message);

        // if CHANGE-ROUND consensus instance was already decided
        if (lastDecidedConsensusInstance.get() < consensusInstance) {
            
            Collection<ConsensusMessage> receivedCommitMessages = commitMessages.getMessages(consensusInstance, round)
                    .values();

            // sends the whole quorum
            for (ConsensusMessage msg : receivedCommitMessages)
                linkToNodes.send(message.getSenderId(), msg);
        }

        if (
            roundChangeMessages.hasValidRoundChangeQuorum(config.getId(), consensusInstance, round) &&
            isLeader(config.getId(), round) &&
            justifyRoundChange(consensusInstance, round)
        ) {
            Optional<String> value = roundChangeMessages.getHeighestPreparedValueIfAny(config.getId(), consensusInstance, round);

            InstanceInfo instance = this.instanceInfo.get(consensusInstance);        

            // generates a random value with random size
            if (config.getByzantineBehavior() == ByzantineBehavior.BYZANTINE_UPON_ROUND_CHANGE_QUORUM) {

                LOGGER.log(Level.INFO,
                    MessageFormat.format("{0} - Node is byzantine, setting a fake/random VALUE in round change", config.getId()));

                int valueLength = RandomIntGenerator.generateRandomInt(1, 5);
                String randomValue = RandomStringGenerator.generateRandomString(valueLength);
                instance.setInputValue(randomValue);
            } else {
                if (value.isPresent())
                    instance.setInputValue(value.get());

                // othewrise, value stays the same
            }

            // broadcast PRE PREPARE message
            int localConsensusInstance = this.consensusInstance.get();
            this.linkToNodes.broadcast(this.createConsensusMessage(value.get(), localConsensusInstance, instance.getCurrentRound()));
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

                            if (config.getByzantineBehavior() == ByzantineBehavior.DONT_RESPOND) { // byzantine node
                                // Do nothing...
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
