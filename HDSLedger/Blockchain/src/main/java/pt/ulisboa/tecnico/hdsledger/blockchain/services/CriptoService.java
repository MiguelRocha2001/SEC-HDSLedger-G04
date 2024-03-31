package pt.ulisboa.tecnico.hdsledger.blockchain.services;

import java.io.IOException;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import pt.ulisboa.tecnico.hdsledger.blockchain.models.CryptocurrencyStorage;
import pt.ulisboa.tecnico.hdsledger.blockchain.models.CryptocurrencyStorage.InvalidAccountException;
import pt.ulisboa.tecnico.hdsledger.blockchain.models.CryptocurrencyStorage.InvalidAmountException;
import pt.ulisboa.tecnico.hdsledger.communication.BlockchainRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestErrorResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestSuccessResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.Block;
import pt.ulisboa.tecnico.hdsledger.communication.Transaction;
import pt.ulisboa.tecnico.hdsledger.communication.TransferRequestErrorResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.TransferRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.TransferRequestSuccessResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.cripto.CriptoUtils;
import pt.ulisboa.tecnico.hdsledger.communication.cripto.CriptoUtils.InvalidClientPublicKeyException;
import pt.ulisboa.tecnico.hdsledger.utilities.ByzantineBehavior;
import pt.ulisboa.tecnico.hdsledger.utilities.ClientConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ServerConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.Utils;

public class CriptoService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(CriptoService.class.getName());

    // Current node is leader
    private final ServerConfig config;

    // Link to communicate with nodes
    private final Link link;

    private final NodeService nodeService;

    private CriptoUtils criptoUtils;

    private final CryptocurrencyStorage storage;

    private String[] clientIds;
    private String[] nodeIds;

    private final int FEE = 1;
    private final int NUMBER_OF_TRANSACTIONS_PER_BLOCK = 1;

    private LinkedList<Transaction> pendingTransactions = new LinkedList<Transaction>();

    public CriptoService(
        Link link,
        ServerConfig config,
        ClientConfig[] clientsConfig,
        NodeService nodeService,
        String[] nodeIds,
        CriptoUtils criptoUtils
    ) {
        this.link = link;
        this.config = config;
        this.nodeService = nodeService;
        this.criptoUtils = criptoUtils;

        clientIds = getClientIds(clientsConfig);
        String[] processIds = Utils.joinArray(nodeIds, clientIds);
        this.nodeIds = nodeIds;

        this.storage = new CryptocurrencyStorage(processIds);

    }

    private static String[] getClientIds(ClientConfig[] clientsConfig) {
        String[] clientIds = new String[clientsConfig.length];

        for (int u = 0; u < clientsConfig.length; u++) {
            clientIds[u] = clientsConfig[u].getId();
        }
        return clientIds;
    }

    /**
     * @param key account user Public key
     * @return the balance or null if the user does not exist
     */
    private Integer getClientBalanceOrNull(PublicKey key) {
        if (criptoUtils.isAcossiatedWithClient(key)) {
            String clientId = criptoUtils.getClientId(key);
            return storage.getBalance(clientId);
        } else {
            return null;
        }
    }

    private Integer getClientBalance(PublicKey key) {
        Integer clientBalance = getClientBalanceOrNull(key);
        if (clientBalance == null)
            throw new InvalidClientPublicKeyException();
        else
            return clientBalance;
    }

    private boolean isClient(String processId) {
        for (int u = 0; u < clientIds.length; u++) {
            if (clientIds[u].equals(processId))
                return true;
        }
        return false;
    }

    private boolean isNode(String processId) {
        for (int u = 0; u < nodeIds.length; u++) {
            if (nodeIds[u].equals(processId))
                return true;
        }
        return false;
    }

    private int getClientBalance(String clientId) {
        if (isClient(clientId))
            return storage.getBalance(clientId);
        throw new InvalidAccountException();
    }

    private void transfer(UUID requestUuid, PublicKey source, PublicKey destination, int amount, byte[] valueSignature, String requestSenderId) {
        String sourceClientId;
        String destinationClientId;

        if (config.getByzantineBehavior() == ByzantineBehavior.DONT_VALIDATE_TRANSACTION) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Node is Byzantine. Not verifying transaction {1}.", config.getId(), requestUuid));

            sourceClientId = criptoUtils.getId(source); // could be Client or Node
            destinationClientId = criptoUtils.getId(destination); // could be Client or Node

        } else {
            if (!criptoUtils.isOwnerOfKey(source, requestSenderId)) {
                throw new InvalidTransferRequest();
            }

            int sourceBalance = getClientBalance(source);

            sourceClientId = criptoUtils.getClientId(source);
            destinationClientId = criptoUtils.getClientId(destination);

            if (sourceBalance < amount + FEE)
                throw new InvalidAmountException();
        }

        Transaction transaction = new Transaction(sourceClientId, destinationClientId, amount, requestUuid, valueSignature);

        pendingTransactions.add(transaction);

        if (pendingTransactions.size() < NUMBER_OF_TRANSACTIONS_PER_BLOCK)
            return;

        List<Transaction> transactionsToPropose = new LinkedList<>();
        transactionsToPropose.addAll(pendingTransactions);
        pendingTransactions.clear();

        nodeService.startConsensus(transactionsToPropose);
    }

    private void sendTransactionReplyToClient(String clientId, UUID requestUuid) {
        TransferRequestSuccessResultMessage reply = new TransferRequestSuccessResultMessage(config.getId(), requestUuid);
        link.send(clientId, new BlockchainRequestMessage(config.getId(), Message.Type.TRANSFER_SUCCESS_RESULT, reply.tojson()));
    }



    private class InvalidTransferRequest extends RuntimeException {}

    public boolean payFeeToNode(String sourceClientId, String destinationId) {
        if (isClient(sourceClientId) && isNode(destinationId)) {

            int sourceBalance = getClientBalance(sourceClientId);

            if (sourceBalance < FEE)
                return false;

            storage.transfer(sourceClientId, destinationId, FEE);
            return true;
        }
        return false;
    }

    /**
     * Transfers units from [sourceClientId] to destinationClientId, for all transactions included [block].
     * If, meanwhile, [sourceClientId] has not a sufficient balance, the function sends an apropriate
     * reply to the client.
     * If one transaction is not valid anymore, it wont be applied.
     */
    public boolean applyTransactions(Block block) {
        int transactionAppliedCount = 0;
        for (Transaction transactionV1 : block.getTransactions()) {

            String sourceClientId = transactionV1.getSourceId();
            String destinationClientId = transactionV1.getDestinationId();
            int amount = transactionV1.getAmount();
            UUID requestUuid = transactionV1.getRequestUUID();

            try {
                boolean validateTransaction = config.getByzantineBehavior() != ByzantineBehavior.DONT_VALIDATE_TRANSACTION;

                if (validateTransaction) {
                    // checks balance again...
                    int sourceBalance = getClientBalance(sourceClientId);
                    if (sourceBalance < amount + FEE) {
                        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction {1} could not be verified after end of consensus!", config.getId(), requestUuid));

                        sendInvalidAmountReply(sourceClientId, requestUuid);
                        continue;
                    } else {
                        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction {1} verified after end of consensus!", config.getId(), requestUuid));
                    }
                } else {
                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Node is Byzantine. Not verifying transaction {1} after end of consensus.", config.getId(), requestUuid));
                }

                try {
                    storage.transfer(sourceClientId, destinationClientId, amount);
                    boolean successful = payFeeToNode(sourceClientId, block.getReceiverId()); // Should always be successful
                    transactionAppliedCount += 1;

                    if (
                        config.getByzantineBehavior() == ByzantineBehavior.DONT_VALIDATE_TRANSACTION
                        &&
                        !isClient(sourceClientId)
                    ) {
                        // Don't send reply
                    } else
                        sendTransactionReplyToClient(sourceClientId, requestUuid);

                    continue;

                } catch(InvalidAmountException e) {
                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction {1} could not be verified after end of consensus!", config.getId(), requestUuid));

                    sendInvalidAmountReply(sourceClientId, requestUuid);
                    continue;
                }
            } catch(InvalidTransferRequest e) { // Happens when UUID cannot be retreived because transaction is unknown.
                LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction request rejected!", config.getId()));
                continue;
            } catch(InvalidAccountException e) {
                LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction request rejected!", config.getId(), requestUuid));
                continue;
            }
        }
        return transactionAppliedCount > 0;
    }

    /**
     * The request is fulfilled based on the senderId of the request, which, is already authenticated by the Link layer.
     * So, if the request contains a public key of another process, it doesn't do anything since it is not used.
     */
    private void getBalanceRequest(BlockchainRequestMessage message) {
        String senderId = message.getSenderId();

        LOGGER.log(Level.INFO,
            MessageFormat.format(
                "{0} - Received GET-BALANCE-REQUEST message from {1}",
                config.getId(), senderId));

        GetBalanceRequestMessage request = message.deserializeGetBalanceRequest();
        UUID requestUuid = request.getUuid();

        try {
            PublicKey clientPublicKey = criptoUtils.getClientPublicKey(senderId);

            Integer balance = getClientBalanceOrNull(clientPublicKey); // null if [clientPublicKey] is unknown
            if (balance != null) {
                link.send(senderId, buildGetBalanceRequestSuccessResult(balance, requestUuid));
            } else {
                link.send(senderId, buildGetBalanceRequestErrorResult(GetBalanceErrorResultType.INVALID_ACCOUNT, requestUuid));
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Error: {1}", config.getId(), e.getMessage()));
        }
    }


    private enum GetBalanceErrorResultType { NOT_AUTHORIZED, INVALID_ACCOUNT }

    private BlockchainRequestMessage buildGetBalanceRequestErrorResult(GetBalanceErrorResultType type, UUID requestUuid) {
        String message;
        if (type == GetBalanceErrorResultType.INVALID_ACCOUNT)
            message = "Invalid Account";
        else
            message = "Not authorized";
        GetBalanceRequestErrorResultMessage reply = new GetBalanceRequestErrorResultMessage(config.getId(), message, requestUuid);
        return new BlockchainRequestMessage(config.getId(), Message.Type.GET_BALANCE_ERROR_RESULT, reply.tojson());
    }

    private BlockchainRequestMessage buildGetBalanceRequestSuccessResult(int balance, UUID requestUuid) {
        GetBalanceRequestSuccessResultMessage reply = new GetBalanceRequestSuccessResultMessage(config.getId(), balance, requestUuid);
        return new BlockchainRequestMessage(config.getId(), Message.Type.GET_BALANCE_SUCCESS_RESULT, reply.tojson());
    }

    private void transferRequest(BlockchainRequestMessage message) {
        String senderId = message.getSenderId();

        LOGGER.log(Level.INFO,
            MessageFormat.format(
                "{0} - Received TRANSFER-REQUEST message from {1}",
                    config.getId(), senderId));

        TransferRequestMessage request = message.deserializeTransferRequest();
        UUID requestUuid = request.getUuid();

        try {
            transfer(requestUuid, request.getSourcePubKey(), request.getDestinationPubKey(), request.getAmount(), request.getValueSignature(), senderId);

            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction request validated", config.getId(), requestUuid));

            // Don't send reply because it has to wait for consensus...

        } catch(InvalidAccountException e) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction request rejected!", config.getId(), requestUuid));
            sendInvalidAccountReply(senderId, requestUuid);

        } catch (InvalidAmountException e) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction request rejected duo to not enough funds!", config.getId(), requestUuid));
            sendInvalidAmountReply(senderId, requestUuid);

        } catch (InvalidClientPublicKeyException e) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction request rejected duo to an invalid client Public key!!", config.getId(), requestUuid));
            sendInvalidCLientPublicKey(senderId, requestUuid);
        } catch (InvalidTransferRequest e) { // Happens when UUID cannot be retrieved because transaction is unknown.
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction request rejected!", config.getId()));
            sendInvalidCLientPublicKey(senderId, requestUuid);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private void sendInvalidCLientPublicKey(String targetId, UUID requestUuid) {
        TransferRequestErrorResultMessage reply = new TransferRequestErrorResultMessage(config.getId(), "Invalid client public key!", requestUuid);
        link.send(targetId, new BlockchainRequestMessage(config.getId(), Message.Type.TRANSFER_ERROR_RESULT, reply.tojson()));
    }

    private void sendInvalidAmountReply(String targetId, UUID requestUuid) {
        TransferRequestErrorResultMessage reply = new TransferRequestErrorResultMessage(config.getId(), "Invalid amount!", requestUuid);
        link.send(targetId, new BlockchainRequestMessage(config.getId(), Message.Type.TRANSFER_ERROR_RESULT, reply.tojson()));
    }

    private void sendInvalidAccountReply(String targetId, UUID requestUuid) {
        TransferRequestErrorResultMessage reply = new TransferRequestErrorResultMessage(config.getId(), "Invalid account!", requestUuid);
        link.send(targetId, new BlockchainRequestMessage(config.getId(), Message.Type.TRANSFER_ERROR_RESULT, reply.tojson()));
    }

    @Override
    public void listen() {
        try {
            // Thread to listen on every request
            new Thread(() -> {
                try {
                    while (true) {
                        Message message = link.receive();

                        // Separate thread to handle each message
                        new Thread(() -> {

                            if (config.getByzantineBehavior() == ByzantineBehavior.IGNORE_REQUESTS) { // byzantine node

                                LOGGER.log(Level.INFO,
                                MessageFormat.format("{0} - Byzantine node ignoring requests...",
                                        config.getId()));
                            } else {
                                switch (message.getType()) {

                                    /*
                                    case APPEND_REQUEST ->
                                        appendString((BlockchainRequestMessage) message);
                                    */

                                    case GET_BALANCE ->
                                        getBalanceRequest((BlockchainRequestMessage) message);

                                    case TRANSFER ->
                                        transferRequest((BlockchainRequestMessage) message);

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
