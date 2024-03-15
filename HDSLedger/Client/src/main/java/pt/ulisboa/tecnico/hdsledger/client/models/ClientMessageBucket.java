package pt.ulisboa.tecnico.hdsledger.client.models;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import pt.ulisboa.tecnico.hdsledger.communication.BlockchainResponseMessage;
import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestErrorResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestSucessResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.RoundChangeMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.Pair;

public class ClientMessageBucket {

    private static final CustomLogger LOGGER = new CustomLogger(ClientMessageBucket.class.getName());
    // Quorum size
    private final int byzantineQuorumSize;
    private final int shortQuorumSize;

    private final HashMap<String, HashSet<GetBalanceRequestSucessResultMessage>> accounBalanceSuccessResponses = new HashMap<>();
    private final HashMap<String, HashSet<GetBalanceRequestErrorResultMessage>> accounBalanceErrorResponses = new HashMap<>();

    public ClientMessageBucket(int nodeCount) {
        int f = Math.floorDiv(nodeCount - 1, 3);
        byzantineQuorumSize = Math.floorDiv(nodeCount + f, 2) + 1;
        shortQuorumSize = f + 1;
    }

    public void addAccountBalanceSuccessResponseMsg(GetBalanceRequestSucessResultMessage message) {
        String acountOwnerId = message.getAcountOwnerId();

        HashSet<GetBalanceRequestSucessResultMessage> msgs = accounBalanceSuccessResponses.putIfAbsent(acountOwnerId, new HashSet<>());
        msgs.add(message);
    }

    public void addAccountBalanceErrorResponseMsg(GetBalanceRequestErrorResultMessage message) {
        String acountOwnerId = message.getAcountOwnerId();

        HashSet<GetBalanceRequestErrorResultMessage> msgs = accounBalanceErrorResponses.putIfAbsent(acountOwnerId, new HashSet<>());
        msgs.add(message);
    }

    public boolean hasAccountBalanceSucessQuorum(GetBalanceRequestSucessResultMessage message) {
        HashSet<GetBalanceRequestSucessResultMessage> responses = accounBalanceSuccessResponses.get(message.getAcountOwnerId());
        int count = 0;

        if (responses == null) {
            return false; // No responses for this account owner ID
        }

        for (GetBalanceRequestSucessResultMessage response : responses) {
            if (response.getAcountOwnerId().equals(message.getAcountOwnerId()))
                count++;
        }

        return count >= byzantineQuorumSize;
    }

    public boolean hasAccountBalanceErrorQuorum(GetBalanceRequestErrorResultMessage message) {
        HashSet<GetBalanceRequestErrorResultMessage> responses = accounBalanceErrorResponses.get(message.getAcountOwnerId());
        int count = 0;

        if (responses == null) {
            return false; // No responses for this account owner ID
        }

        for (GetBalanceRequestErrorResultMessage response : responses) {
            if (response.getAcountOwnerId().equals(message.getAcountOwnerId()))
                count++;
        }

        return count >= byzantineQuorumSize;
    }
}