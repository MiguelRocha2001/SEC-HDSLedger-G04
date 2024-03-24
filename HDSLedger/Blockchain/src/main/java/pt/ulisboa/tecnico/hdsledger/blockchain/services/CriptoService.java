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
  
    private final ArrayList<Pair<UUID, TransactionV1>> transferRequests;
    private final NodeService nodeService;

    private CriptoUtils criptoUtils;

    private final CryptocurrencyStorage storage;
    

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

        String[] clientIds = getClientIds(clientsConfig);
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
     * 
     * @param key account user Public key
     * @return the balance or null if the user does not exist
     */
    private Integer getBalanceOrNull(PublicKey key) {
        if (criptoUtils.isAcossiatedWithClient(key)) {
            String clientId = criptoUtils.getClientId(key);
            return storage.getBalance(clientId);
        } else {
            return null;
        }
    }

    private void transfer(UUID requestUuid, PublicKey source, PublicKey destination, int amount, byte[] valueSignature, String requestSenderId) {
        int sourceBalance = getBalanceOrNull(source);

        try {
            String sourceClientId = criptoUtils.getClientId(source);
            String destinationClientId = criptoUtils.getClientId(destination);

            if (config.getByzantineBehavior() != ByzantineBehavior.DONT_VALIDATE_TRANSACTION) {
                if (sourceBalance < amount) {
                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction {1} could not be verified!", config.getId(), requestUuid));
                    sendInvalidAmountReply(sourceClientId, requestUuid);
                    return;
                } else {
                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction {1} verified.", config.getId(), requestUuid));
                }
            } else {
                LOGGER.log(Level.INFO, MessageFormat.format("{0} - Node is Byzantine. Not verifying transaction {1}.", config.getId(), requestUuid));
            }
    
            TransactionV1 transaction = new TransactionV1(sourceClientId, destinationClientId, amount);

            // Stores request so, after consensus is finished, is possible to locate original request
            transferRequests.add(new Pair<UUID,TransactionV1>(requestUuid, transaction));
            
            nodeService.startConsensus(transaction, valueSignature);

        } catch(InvalidClientKeyException e) {
            // This should never happen because if the node received a transfer request, it means that the Link layer confirmed
            // the sender ID with a valid client known public key. Therefore, [source] argument should always be associated with
            // a valid Client ID
            sendInvalidAccountReply(requestSenderId, requestUuid);
        }
    }

    private void sendTransactionReplyToClient(String clientId, UUID requestUuid) {
        TransferRequestSucessResultMessage reply = new TransferRequestSucessResultMessage(config.getId(), requestUuid);
        link.send(clientId, new BlockchainRequestMessage(config.getId(), Message.Type.TRANSFER_SUCESS_RESULT, reply.tojson()));   
    }

    private class InvalidTransferRequest extends RuntimeException {}

    private UUID getRequestUuid(String sourceClientId, String destinationClientId, int amount) {
        for (Pair<UUID, TransactionV1> item : transferRequests) {
            TransactionV1 t = item.getValue();
            if (
                t.getSourceId().equals(sourceClientId)
                &&
                t.getDestinationId().equals(destinationClientId)
                &&
                t.getAmount() == amount
            ) {
                return item.getKey();
            }
        }
        throw new InvalidTransferRequest();
    }

    /**
     * Transfers units from [sourceClientId] to destinationClientId, and a fee to receiverId.
     * If, meanwhile, [sourceClientId] has not a sufficient balance, the function sends an apropriate
     * reply to the client.
     * @return true if transaction was successfull. Otherwise, retuns false
     */
    public boolean applyTransaction(String sourceClientId, String destinationClientId, int amount, String feeReceiverId) {
        try {
            UUID requestUuid = getRequestUuid(sourceClientId, destinationClientId, amount);

            boolean validateTransaction = config.getByzantineBehavior() != ByzantineBehavior.DONT_VALIDATE_TRANSACTION;
        
            if (validateTransaction) {
                // checks balance again
                int sourceBalance = storage.getBalance(sourceClientId);
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
        }
    }

    // It's not necessary to verify if the sender is the owner of the public key since the Link layer already garantees that.
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

            Integer balance = getBalanceOrNull(clientPublicKey); // null if [clientPublicKey] is unknown
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

    private boolean validatePublicKey(String senderId, PublicKey sourcePublicKey) {
        System.out.println(criptoUtils.getClientId(sourcePublicKey));
        return senderId.equals(criptoUtils.getClientId(sourcePublicKey));
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
            if (config.getByzantineBehavior() == ByzantineBehavior.DONT_VALIDATE_TRANSACTION) {
                LOGGER.log(Level.INFO, MessageFormat.format("{0} - Node is Byzantine. Not validating Transaction request", config.getId(), requestUuid));
            } else {
                if (validatePublicKey(senderId, request.getSourcePubKey()))
                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction request validated", config.getId(), requestUuid));
                else {
                    LOGGER.log(Level.INFO, MessageFormat.format("{0} - Transaction request not accepted because Public key doesn't belong to sender", config.getId(), requestUuid));
                    sendInvalidAccountReply(senderId, requestUuid); 
                    return;
                }
            }

            transfer(requestUuid, request.getSourcePubKey(), request.getDestinationPubKey(), request.getAmount(), request.getValueSignature(), senderId);

            // Dont send reply because it has to wait for consensus...

        } catch(InvalidAccountException e) {
            sendInvalidAccountReply(senderId, requestUuid);

        } catch (InvalidAmmountException e) {
            sendInvalidAmountReply(senderId, requestUuid);

        } catch (InvalidClientKeyException e) {
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
