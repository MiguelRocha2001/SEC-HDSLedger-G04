package pt.ulisboa.tecnico.hdsledger.client.models;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;

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

    public Optional<Integer> hasAccountBalanceSuccessQuorum(UUID uuid) throws NoSuchAlgorithmException, InvalidKeySpecException {
        HashSet<GetBalanceRequestSuccessResultMessage> responses = accountBalanceSuccessResponses.get(uuid);

        // Create mapping of value to frequency
        HashMap<Integer, Integer> frequency = new HashMap<>();
        responses.forEach((message) -> {
            int balance = message.getBalance();
            frequency.put(balance, frequency.getOrDefault(balance, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<Integer, Integer> entry) -> {
            return entry.getValue() >= byzantineQuorumSize;
        }).map((Map.Entry<Integer, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
    }

    public Optional<String> hasAccountBalanceErrorQuorum(UUID uuid) throws NoSuchAlgorithmException, InvalidKeySpecException {
        HashSet<GetBalanceRequestErrorResultMessage> responses = accounBalanceErrorResponses.get(uuid);

        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<>();
        responses.forEach((message) -> {
            String errorMessage = message.getErrorMessage();
            frequency.put(errorMessage, frequency.getOrDefault(errorMessage, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<String, Integer> entry) -> {
            return entry.getValue() >= byzantineQuorumSize;
        }).map((Map.Entry<String, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
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

    public Optional<String> hasAccountTransferErrorQuorum(UUID uuid) throws NoSuchAlgorithmException, InvalidKeySpecException {
        HashSet<TransferRequestErrorResultMessage> responses = transferErrorResponses.get(uuid);

        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<>();
        responses.forEach((message) -> {
            String errorMessage = message.getErrorMessage();
            frequency.put(errorMessage, frequency.getOrDefault(errorMessage, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream().filter((Map.Entry<String, Integer> entry) -> {
            return entry.getValue() >= byzantineQuorumSize;
        }).map((Map.Entry<String, Integer> entry) -> {
            return entry.getKey();
        }).findFirst();
    }
}
