package pt.ulisboa.tecnico.hdsledger.blockchain.services;

import java.io.IOException;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import javax.swing.text.TabSet;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.blockchain.models.CryptocurrencyStorage;
import pt.ulisboa.tecnico.hdsledger.blockchain.models.CryptocurrencyStorage.InvalidAccountException;
import pt.ulisboa.tecnico.hdsledger.blockchain.models.CryptocurrencyStorage.InvalidAmmountException;
import pt.ulisboa.tecnico.hdsledger.communication.AppendRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.BlockchainRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestErrorResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestSucessResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.TransferRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.TransferRequestSucessResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.LeaderChangeMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.TransactionV1;
import pt.ulisboa.tecnico.hdsledger.communication.TransactionV2;
import pt.ulisboa.tecnico.hdsledger.communication.TransferRequestErrorResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.cripto.CriptoUtils;
import pt.ulisboa.tecnico.hdsledger.communication.cripto.CriptoUtils.InvalidClientKeyException;
import pt.ulisboa.tecnico.hdsledger.utilities.ByzantineBehavior;
import pt.ulisboa.tecnico.hdsledger.utilities.ClientConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.Pair;
import pt.ulisboa.tecnico.hdsledger.utilities.ServerConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.Utils;

public class CriptoService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(CriptoService.class.getName());

    // Current node is leader
    private final ServerConfig config;

    // Link to communicate with nodes
    private final Link link;
  
    private final ArrayList<Pair<UUID, Pair<String, TransactionV1>>> transferRequests;
    private final NodeService nodeService;

    private CriptoUtils criptoUtils;

    private final CryptocurrencyStorage storage;

    String[] clientIds;
    

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

        this.storage = new CryptocurrencyStorage(processIds);

        this.transferRequests = new ArrayList<>();
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
            throw new InvalidClientKeyException();
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

    private int getClientBalance(String clientId) {
        if (isClient(clientId))
            return storage.getBalance(clientId);
        throw new InvalidAccountException();
    }

    private void transfer(UUID requestUuid, PublicKey source, PublicKey destination, int amount, byte[] valueSignature, String requestSenderId) {        
        String sourceClientId;
        String destinationClientId;

        if (config.getByzantineBehavior() != ByzantineBehavior.DONT_VALIDATE_TRANSACTION) {
            int sourceBalance = getClientBalance(source);
            
            sourceClientId = criptoUtils.getClientId(source);
            destinationClientId = criptoUtils.getClientId(destination);

            if (sourceBalance < amount)
                throw new InvalidAmmountException();
            
        } else {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Node is Byzantine. Not verifying transaction {1}.", config.getId(), requestUuid));

            sourceClientId = criptoUtils.getId(source); // could be Client or Node
            destinationClientId = criptoUtils.getId(destination); // could be Client or Node
        }

        TransactionV1 transaction = new TransactionV1(sourceClientId, destinationClientId, amount, requestUuid);

        // Stores request so, after consensus is finished, is possible to locate original request
        transferRequests.add(new Pair<UUID, Pair<String, TransactionV1>>(requestUuid, new Pair(requestSenderId, transaction)));
        
        nodeService.startConsensus(transaction, valueSignature);
    }

    private void sendTransactionReplyToClient(String clientId, UUID requestUuid) {
        TransferRequestSucessResultMessage reply = new TransferRequestSucessResultMessage(config.getId(), requestUuid);
        link.send(clientId, new BlockchainRequestMessage(config.getId(), Message.Type.TRANSFER_SUCESS_RESULT, reply.tojson()));   
    }

    private String getSenderIdOrNull(UUID requeUuid) {
        // Iterate over the transferRequests ArrayList
        for (Pair<UUID, Pair<String, TransactionV1>> pair : transferRequests) {
            UUID uuid = pair.getKey();
            Pair<String, TransactionV1> innerPair = pair.getValue();
            String senderId = innerPair.getKey();
            TransactionV1 transaction = innerPair.getValue();

            if (uuid.equals(requeUuid))
                return senderId;
        }
        return null;
    }

    private class InvalidTransferRequest extends RuntimeException {}

    /**
     * Transfers units from [sourceClientId] to destinationClientId, and a fee to receiverId.
     * If, meanwhile, [sourceClientId] has not a sufficient balance, the function sends an apropriate
     * reply to the client.
     * @return true if transaction was successfull. Otherwise, retuns false
     */
    public boolean applyTransaction(TransactionV2 transactionV2) {
        String sourceClientId = transactionV2.getSourceId();
        String destinationClientId = transactionV2.getDestinationId();
        String feeReceiverId = transactionV2.getReceiverId();
        int amount = transactionV2.getAmount();
        UUID requestUuid = transactionV2.getRequestUUID();

        try {
            boolean validateTransaction = config.getByzantineBehavior() != ByzantineBehavior.DONT_VALIDATE_TRANSACTION;
        
            if (validateTransaction) {
                // checks balance again...
                int sourceBalance = getClientBalance(sourceClientId);
                if (sourceBalance < amount) {
                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction {1} could not be verified after end of consensus!", config.getId(), requestUuid));

                    sendInvalidAmountReply(sourceClientId, requestUuid);
                } else {
                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction {1} verified after end of consensus!", config.getId(), requestUuid));
                }
            } else {
                LOGGER.log(Level.INFO, MessageFormat.format("{0} - Node is Byzantine. Not verifying transaction {1} after end of consensus.", config.getId(), requestUuid));
            }

            try {
                storage.transferTwoTimes(sourceClientId, destinationClientId, amount, feeReceiverId, 1, validateTransaction); // TODO: maybe change from 1 to another value

                if (
                    config.getByzantineBehavior() == ByzantineBehavior.DONT_VALIDATE_TRANSACTION
                    &&
                    !isClient(sourceClientId)
                ) {
                    // Dont send reply
                } else 
                    sendTransactionReplyToClient(sourceClientId, requestUuid);

                return true;
            
            } catch(InvalidAmmountException e) {
                LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction {1} could not be verified after end of consensus!", config.getId(), requestUuid));

                sendInvalidAmountReply(sourceClientId, requestUuid);
                return false;
            }
        } catch(InvalidTransferRequest e) { // Happens when UUID cannot be retreived because transaction is unknown.
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction is not registered in storage. Not executing!", config.getId()));
            return false;
        } catch(InvalidAccountException e) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction request rejected!", config.getId(), requestUuid));
            
            String senderId = getSenderIdOrNull(requestUuid);
            if (senderId != null) // could be null if the request is not registered locally
                sendInvalidAccountReply(senderId, requestUuid);

            return false;
        }
    }

    /**
     * The resquest is fullfiled based on the senderId of the request, which, is already authenticated by the Link layer.
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
                link.send(senderId, buildGetBalanceRequestErrorResult(GetBalanceErrroResultType.INVALID_ACCOUNT, requestUuid));
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Error: {1}", config.getId(), e.getMessage()));
        }
    }

    
    private enum GetBalanceErrroResultType { NOT_AUTHORIZED, INVALID_ACCOUNT }

    private BlockchainRequestMessage buildGetBalanceRequestErrorResult(GetBalanceErrroResultType type, UUID requestUuid) {
        String message;
        if (type == GetBalanceErrroResultType.INVALID_ACCOUNT)
            message = "Invalid Account";
        else
            message = "Not authorized";
        GetBalanceRequestErrorResultMessage reply = new GetBalanceRequestErrorResultMessage(config.getId(), message, requestUuid);
        return new BlockchainRequestMessage(config.getId(), Message.Type.GET_BALANCE_ERROR_RESULT, reply.tojson());
    }

    private BlockchainRequestMessage buildGetBalanceRequestSuccessResult(int balance, UUID requestUuid) {
        GetBalanceRequestSucessResultMessage reply = new GetBalanceRequestSucessResultMessage(config.getId(), balance, requestUuid);
        return new BlockchainRequestMessage(config.getId(), Message.Type.GET_BALANCE_SUCESS_RESULT, reply.tojson());
    }

    private void tranferRequest(BlockchainRequestMessage message) {
        String senderId = message.getSenderId();

        LOGGER.log(Level.INFO,
            MessageFormat.format(
                "{0} - Received TRANFER-REQUEST message from {1}",
                    config.getId(), senderId));

        TransferRequestMessage request = message.deserializeTransferRequest();
        UUID requestUuid = request.getUuid();

        try {
            transfer(requestUuid, request.getSourcePubKey(), request.getDestinationPubKey(), request.getAmount(), request.getValueSignature(), senderId);

            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction request validated", config.getId(), requestUuid));

            // Dont send reply because it has to wait for consensus...

        } catch(InvalidAccountException e) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction request rejected!", config.getId(), requestUuid));
            sendInvalidAccountReply(senderId, requestUuid);

        } catch (InvalidAmmountException e) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction request rejected duo to not enough funds!", config.getId(), requestUuid));
            sendInvalidAmountReply(senderId, requestUuid);

        } catch (InvalidClientKeyException e) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction request rejected duo to an invalid client Public key!!", config.getId(), requestUuid));
            TransferRequestErrorResultMessage reply = new TransferRequestErrorResultMessage(config.getId(), "Invalid client public key!", requestUuid);
            link.send(senderId, new BlockchainRequestMessage(config.getId(), Message.Type.TRANSFER_ERROR_RESULT, reply.tojson()));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Error: {1}", config.getId(), e.getMessage()));
        }
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
                                        tranferRequest((BlockchainRequestMessage) message);

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
