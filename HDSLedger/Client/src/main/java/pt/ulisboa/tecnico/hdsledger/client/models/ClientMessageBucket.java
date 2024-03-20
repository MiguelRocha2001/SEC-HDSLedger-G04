package pt.ulisboa.tecnico.hdsledger.client.models;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.HashSet;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestErrorResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestSucessResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.TransferRequestSucessResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.TransferRequestErrorResultMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

public class ClientMessageBucket {

    private static final CustomLogger LOGGER = new CustomLogger(ClientMessageBucket.class.getName());
    // Quorum size
    private final int byzantineQuorumSize;
    private final int shortQuorumSize;

    private final HashMap<PublicKey, HashSet<GetBalanceRequestSucessResultMessage>> accounBalanceSuccessResponses = new HashMap<>();
    private final HashMap<PublicKey, HashSet<GetBalanceRequestErrorResultMessage>> accounBalanceErrorResponses = new HashMap<>();

    private final HashMap<PublicKey, HashSet<TransferRequestSucessResultMessage>> transferSuccessResponses = new HashMap<>();
    private final HashMap<PublicKey, HashSet<TransferRequestErrorResultMessage>> transferErrorResponses = new HashMap<>();

    public ClientMessageBucket(int nodeCount) {
        int f = Math.floorDiv(nodeCount - 1, 3);
        byzantineQuorumSize = Math.floorDiv(nodeCount + f, 2) + 1;
        shortQuorumSize = f + 1;
    }

    public void addAccountBalanceSuccessResponseMsg(GetBalanceRequestSucessResultMessage message) throws NoSuchAlgorithmException, InvalidKeySpecException {
        PublicKey requestedPublicKey = message.getRequestedPublickey();

        HashSet<GetBalanceRequestSucessResultMessage> msgs = accounBalanceSuccessResponses.putIfAbsent(requestedPublicKey, new HashSet<>());
        if (msgs == null)
            msgs = accounBalanceSuccessResponses.get(requestedPublicKey);

        msgs.add(message);
    }

    public void addAccountBalanceErrorResponseMsg(GetBalanceRequestErrorResultMessage message) throws NoSuchAlgorithmException, InvalidKeySpecException {
        PublicKey requestedPublicKey = message.getRequestedPublickey();

        HashSet<GetBalanceRequestErrorResultMessage> msgs = accounBalanceErrorResponses.putIfAbsent(requestedPublicKey, new HashSet<>());
        if (msgs == null)
            msgs = accounBalanceErrorResponses.get(requestedPublicKey);

        msgs.add(message);
    }

    public boolean hasAccountBalanceSucessQuorum(GetBalanceRequestSucessResultMessage message) throws NoSuchAlgorithmException, InvalidKeySpecException {
        HashSet<GetBalanceRequestSucessResultMessage> responses = accounBalanceSuccessResponses.get(message.getRequestedPublickey());
        int count = 0;

        if (responses == null) {
            return false; // No responses for this account owner ID
        }

        for (GetBalanceRequestSucessResultMessage response : responses) {
            if (response.getRequestedPublickey().equals(message.getRequestedPublickey())) {
                count++;
            }
        }

        return count >= byzantineQuorumSize;
    }

    public boolean hasAccountBalanceErrorQuorum(GetBalanceRequestErrorResultMessage message) throws NoSuchAlgorithmException, InvalidKeySpecException {
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



    public void addAccountTransferSuccessResponseMsg(TransferRequestSucessResultMessage message) throws NoSuchAlgorithmException, InvalidKeySpecException {
        PublicKey requestedPublicKey = message.getClientDestinationPubKey();

        HashSet<TransferRequestSucessResultMessage> msgs = transferSuccessResponses.putIfAbsent(requestedPublicKey, new HashSet<>());
        if (msgs == null)
            msgs = transferSuccessResponses.get(requestedPublicKey);

        msgs.add(message);
    }

    public void addAccountTransferErrorResponseMsg(TransferRequestErrorResultMessage message) throws NoSuchAlgorithmException, InvalidKeySpecException {
        PublicKey requestedPublicKey = message.getClientDestinationPubKey();

        HashSet<TransferRequestErrorResultMessage> msgs = transferErrorResponses.putIfAbsent(requestedPublicKey, new HashSet<>());
        if (msgs == null)
            msgs = transferErrorResponses.get(requestedPublicKey);

        msgs.add(message);
    }

    public boolean hasAccountTransferSucessQuorum(TransferRequestSucessResultMessage message) throws NoSuchAlgorithmException, InvalidKeySpecException {
        HashSet<TransferRequestSucessResultMessage> responses = transferSuccessResponses.get(message.getClientDestinationPubKey());
        int count = 0;

        if (responses == null) {
            return false; // No responses for this account owner ID
        }

        for (TransferRequestSucessResultMessage response : responses) {
            if (response.getClientDestinationPubKey().equals(message.getClientDestinationPubKey())) {
                count++;
            }
        }

        return count >= byzantineQuorumSize;
    }

    public boolean hasAccountTransferErrorQuorum(TransferRequestErrorResultMessage message) throws NoSuchAlgorithmException, InvalidKeySpecException {
        HashSet<TransferRequestErrorResultMessage> responses = transferErrorResponses.get(message.getClientDestinationPubKey());
        int count = 0;

        if (responses == null) {
            return false; // No responses for this account owner ID
        }

        for (TransferRequestErrorResultMessage response : responses) {
            if (response.getClientDestinationPubKey().equals(message.getClientDestinationPubKey()))
                count++;
        }

        return count >= byzantineQuorumSize;
    }
}