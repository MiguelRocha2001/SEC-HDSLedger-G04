package pt.ulisboa.tecnico.hdsledger.client.models;

import java.security.PublicKey;
import java.util.HashMap;
import java.util.HashSet;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestErrorResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestSucessResultMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

public class ClientMessageBucket {

    private static final CustomLogger LOGGER = new CustomLogger(ClientMessageBucket.class.getName());
    // Quorum size
    private final int byzantineQuorumSize;
    private final int shortQuorumSize;

    private final HashMap<PublicKey, HashSet<GetBalanceRequestSucessResultMessage>> accounBalanceSuccessResponses = new HashMap<>();
    private final HashMap<PublicKey, HashSet<GetBalanceRequestErrorResultMessage>> accounBalanceErrorResponses = new HashMap<>();

    public ClientMessageBucket(int nodeCount) {
        int f = Math.floorDiv(nodeCount - 1, 3);
        byzantineQuorumSize = Math.floorDiv(nodeCount + f, 2) + 1;
        shortQuorumSize = f + 1;
    }

    public void addAccountBalanceSuccessResponseMsg(GetBalanceRequestSucessResultMessage message) {
        PublicKey requestedPublicKey = message.getRequestedPublickey();

        HashSet<GetBalanceRequestSucessResultMessage> msgs = accounBalanceSuccessResponses.putIfAbsent(requestedPublicKey, new HashSet<>());
        msgs.add(message);
    }

    public void addAccountBalanceErrorResponseMsg(GetBalanceRequestErrorResultMessage message) {
        PublicKey requestedPublicKey = message.getRequestedPublickey();

        HashSet<GetBalanceRequestErrorResultMessage> msgs = accounBalanceErrorResponses.putIfAbsent(requestedPublicKey, new HashSet<>());
        msgs.add(message);
    }

    public boolean hasAccountBalanceSucessQuorum(GetBalanceRequestSucessResultMessage message) {
        HashSet<GetBalanceRequestSucessResultMessage> responses = accounBalanceSuccessResponses.get(message.getRequestedPublickey());
        int count = 0;

        if (responses == null) {
            return false; // No responses for this account owner ID
        }

        for (GetBalanceRequestSucessResultMessage response : responses) {
            if (response.getRequestedPublickey().equals(message.getRequestedPublickey()))
                count++;
        }

        return count >= byzantineQuorumSize;
    }

    public boolean hasAccountBalanceErrorQuorum(GetBalanceRequestErrorResultMessage message) {
        HashSet<GetBalanceRequestErrorResultMessage> responses = accounBalanceErrorResponses.get(message.getRequestedPublickey());
        int count = 0;

        if (responses == null) {
            return false; // No responses for this account owner ID
        }

        for (GetBalanceRequestErrorResultMessage response : responses) {
            if (response.getRequestedPublickey().equals(message.getRequestedPublickey()))
                count++;
        }

        return count >= byzantineQuorumSize;
    }
}