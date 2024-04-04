package pt.ulisboa.tecnico.hdsledger.client.models;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestErrorResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestSuccessResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.TransferRequestSuccessResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.TransferRequestErrorResultMessage;

public class ClientMessageBucket {
    // Quorum size
    private final int byzantineQuorumSize;

    private final HashMap<UUID, HashSet<GetBalanceRequestSuccessResultMessage>> accountBalanceSuccessResponses = new HashMap<>();
    private final HashMap<UUID, HashSet<GetBalanceRequestErrorResultMessage>> accounBalanceErrorResponses = new HashMap<>();

    private final HashMap<UUID, HashSet<TransferRequestSuccessResultMessage>> transferSuccessResponses = new HashMap<>();
    private final HashMap<UUID, HashSet<TransferRequestErrorResultMessage>> transferErrorResponses = new HashMap<>();

    public ClientMessageBucket(int nodeCount) {
        int f = Math.floorDiv(nodeCount - 1, 3);
        byzantineQuorumSize = Math.floorDiv(nodeCount + f, 2) + 1;
    }

    public void addAccountBalanceSuccessResponseMsg(GetBalanceRequestSuccessResultMessage message) throws NoSuchAlgorithmException, InvalidKeySpecException {
        UUID uuid = message.getUuid();

        HashSet<GetBalanceRequestSuccessResultMessage> msgs = accountBalanceSuccessResponses.putIfAbsent(uuid, new HashSet<>());
        if (msgs == null)
            msgs = accountBalanceSuccessResponses.get(uuid);

        msgs.add(message);
    }

    public void addAccountBalanceErrorResponseMsg(GetBalanceRequestErrorResultMessage message) throws NoSuchAlgorithmException, InvalidKeySpecException {
        UUID uuid = message.getUuid();

        HashSet<GetBalanceRequestErrorResultMessage> msgs = accounBalanceErrorResponses.putIfAbsent(uuid, new HashSet<>());
        if (msgs == null)
            msgs = accounBalanceErrorResponses.get(uuid);

        msgs.add(message);
    }

    public boolean hasAccountBalanceSuccessQuorum(UUID uuid) throws NoSuchAlgorithmException, InvalidKeySpecException {
        HashSet<GetBalanceRequestSuccessResultMessage> responses = accountBalanceSuccessResponses.get(uuid);

        if (responses == null) {
            return false; // No responses for this account owner ID
        } else {
            return responses.size() >= byzantineQuorumSize;
        }
    }

    public boolean hasAccountBalanceErrorQuorum(GetBalanceRequestErrorResultMessage message) throws NoSuchAlgorithmException, InvalidKeySpecException {
        HashSet<GetBalanceRequestErrorResultMessage> responses = accounBalanceErrorResponses.get(message.getUuid());
        
        if (responses == null) {
            return false; // No responses for this account owner ID
        } else {
            return responses.size() >= byzantineQuorumSize;
        }
    }



    public void addAccountTransferSuccessResponseMsg(TransferRequestSuccessResultMessage message) throws NoSuchAlgorithmException, InvalidKeySpecException {
        UUID uuid = message.getUuid();

        HashSet<TransferRequestSuccessResultMessage> msgs = transferSuccessResponses.putIfAbsent(uuid, new HashSet<>());
        if (msgs == null)
            msgs = transferSuccessResponses.get(uuid);

        msgs.add(message);
    }

    public void addAccountTransferErrorResponseMsg(TransferRequestErrorResultMessage message) throws NoSuchAlgorithmException, InvalidKeySpecException {
        UUID uuid = message.getUuid();

        HashSet<TransferRequestErrorResultMessage> msgs = transferErrorResponses.putIfAbsent(uuid, new HashSet<>());
        if (msgs == null)
            msgs = transferErrorResponses.get(uuid);

        msgs.add(message);
    }

    public boolean hasAccountTransferSuccessQuorum(UUID uuid) throws NoSuchAlgorithmException, InvalidKeySpecException {
        HashSet<TransferRequestSuccessResultMessage> responses = transferSuccessResponses.get(uuid);
        
        if (responses == null) {
            return false; // No responses for this account owner ID
        } else {
            return responses.size() >= byzantineQuorumSize;
        }
    }

    public boolean hasAccountTransferErrorQuorum(UUID uuid) throws NoSuchAlgorithmException, InvalidKeySpecException {
        HashSet<TransferRequestErrorResultMessage> responses = transferErrorResponses.get(uuid);
        
        if (responses == null) {
            return false; // No responses for this account owner ID
        } else {
            return responses.size() >= byzantineQuorumSize;
        }
    }
}
