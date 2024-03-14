package pt.ulisboa.tecnico.hdsledger.blockchain.models;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.RoundChangeMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.Pair;

public class MessageBucket {

    private static final CustomLogger LOGGER = new CustomLogger(MessageBucket.class.getName());
    // Quorum size
    private final int byzantineQuorumSize;
    private final int shortQuorumSize;
    // Instance -> Round -> Sender ID -> Consensus message
    private final Map<Integer, Map<Integer, Map<String, ConsensusMessage>>> bucket = new ConcurrentHashMap<>();

    public MessageBucket(int nodeCount) {
        int f = Math.floorDiv(nodeCount - 1, 3);
        byzantineQuorumSize = Math.floorDiv(nodeCount + f, 2) + 1;
        shortQuorumSize = f + 1;
    }

    /*
     * Add a message to the bucket
     * 
     * @param consensusInstance
     * 
     * @param message
     */
    public void addMessage(ConsensusMessage message) {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        bucket.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        bucket.get(consensusInstance).putIfAbsent(round, new ConcurrentHashMap<>());
        bucket.get(consensusInstance).get(round).put(message.getSenderId(), message);
    }

    public Optional<String> hasValidPrepareQuorum(String nodeId, int instance, int round) {
        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<>();
        bucket.get(instance).get(round).values().forEach((message) -> {
            PrepareMessage prepareMessage = message.deserializePrepareMessage();
            String value = prepareMessage.getValue();
            frequency.put(value, frequency.getOrDefault(value, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<String, Integer> entry) -> {
            return entry.getValue() >= byzantineQuorumSize;
        }).map((Map.Entry<String, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
    }

    public Optional<String> hasValidCommitQuorum(String nodeId, int instance, int round) {
        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<>();
        bucket.get(instance).get(round).values().forEach((message) -> {
            CommitMessage commitMessage = message.deserializeCommitMessage();
            String value = commitMessage.getValue();
            frequency.put(value, frequency.getOrDefault(value, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<String, Integer> entry) -> {
            return entry.getValue() >= byzantineQuorumSize;
        }).map((Map.Entry<String, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
    }

    public List<CommitMessage> getCommitMessages(String nodeId, int instance, int round) {
        LinkedList<CommitMessage> commitMessages = new LinkedList<>();
        bucket.get(instance).get(round).values().forEach((message) -> {
            CommitMessage commitMessage = message.deserializeCommitMessage();
            commitMessages.add(commitMessage);
        });

        return commitMessages;
    }

    /**
     * Checks if there is a quorum of ROUND CHANGE messages.
     */
    public boolean hasValidRoundChangeQuorum(String nodeId, int instance, int round) {
        return bucket.get(instance).get(round).size() >= byzantineQuorumSize;
    }

    /**
     * Checks if there is f+1 ROUND CHANGE messages.
     */
    public boolean hasValidRoundChangeSmallQuorum(String nodeId, int instance, int round) {
        return bucket.get(instance).get(round).size() >= shortQuorumSize;
    }

    /**
     * Assuming that there is a quorum, returns the heighest prepared value, or null,
     * if there isn't any.
     */
    public Optional<Pair<Integer, String>> getHeighestPreparedRoundAndValueIfAny(String nodeId, int instance, int round) {
        
        int heighestPreparedRound = -1; // TODO: check if this is safe
        String heighestPreparedValue = null;

        for (Map.Entry<String, ConsensusMessage> entry : bucket.get(instance).get(round).entrySet()) {
            
            RoundChangeMessage roundChangeMessage = entry.getValue().deserializeRoundChangeMessage();
            
            int preparedRound = roundChangeMessage.getPreparedRound();
            if (preparedRound > heighestPreparedRound) {
                heighestPreparedValue = roundChangeMessage.getPreparedValue();
            }
        }

        return Optional.ofNullable(new Pair<Integer, String>(heighestPreparedRound, heighestPreparedValue));
    }

    /**
     * Checks if all PREPARE messages have the round and value equal to [preparedRound] and [preparedValue].
     */
    public boolean checkRoundAndValue(String nodeId, int instance, int round, int preparedRound, String preparedValue) {
        
        for (Map.Entry<String, ConsensusMessage> entry : bucket.get(instance).get(round).entrySet()) {
            
            PrepareMessage prepareMessage = entry.getValue().deserializePrepareMessage();
            
            int roundAux = entry.getValue().getRound();
            String valueAux = prepareMessage.getValue();
            
            if (roundAux != preparedRound || valueAux != preparedValue)
                return false;
        }
        return true;
    }

    /**
     * @return true if all ROUND-MESSAGE's prepared round and value are not set. False otherwise
     */
    public boolean isRoundChangeMessagesNotPrepared(String nodeId, int instance, int round) {
        for (Map.Entry<String, ConsensusMessage> entry : bucket.get(instance).get(round).entrySet()) {
            
            RoundChangeMessage roundChangeMessage = entry.getValue().deserializeRoundChangeMessage();
            
            int preparedRound = roundChangeMessage.getPreparedRound();
            String preparedValue = roundChangeMessage.getPreparedValue();
            
            if (preparedRound != -1 || preparedValue != null)
                return false;
        }
        return true;
    }

    public Map<String, ConsensusMessage> getMessages(int instance, int round) {
        return bucket.get(instance).get(round);
    }

    /**
     * Returns a set of f+1 messages with round greater than [lowerLimit] or null if there isn't one.
     */
    public ConsensusMessage[] getQuorumWithRoundGreaterThan(String nodeId, int instance, int lowerLimit) {
        ConsensusMessage[] toReturn = new ConsensusMessage[shortQuorumSize];

        int count = -1;
        for (Map.Entry<Integer,Map<String,ConsensusMessage>> outerEntry : bucket.get(instance).entrySet()) {
            
            int round = outerEntry.getKey();

            // adds all messages with this round
            if (round > lowerLimit) {
                for (Map.Entry<String, ConsensusMessage> innerEntry : outerEntry.getValue().entrySet()) {
                    toReturn[++count] = innerEntry.getValue();
                }
            }
        }

        if (count >= shortQuorumSize)
            return toReturn;
        else
            return null;
    }
}