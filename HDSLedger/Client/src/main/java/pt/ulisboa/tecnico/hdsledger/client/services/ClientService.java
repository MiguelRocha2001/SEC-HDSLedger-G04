package pt.ulisboa.tecnico.hdsledger.client.services;

import java.io.IOException;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import pt.ulisboa.tecnico.hdsledger.client.models.ClientMessageBucket;
import pt.ulisboa.tecnico.hdsledger.communication.BlockchainRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.BlockchainResponseMessage;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestErrorResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestSucessResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.TransferRequestErrorResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.TransferRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.TransferRequestSucessResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.cripto.CriptoUtils;
import pt.ulisboa.tecnico.hdsledger.communication.cripto.CriptoUtils.InvalidClientIdException;
import pt.ulisboa.tecnico.hdsledger.communication.cripto.CriptoUtils.InvalidIdException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.Utils;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

public class ClientService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(ClientService.class.getName());

    private final ProcessConfig config;
    private final CriptoUtils criptoUtils;

    // Link to communicate with nodes
    private final Link link;

    private final ClientMessageBucket bucket;

     private final Set<UUID> displayedReplies = Collections.synchronizedSet(new HashSet<UUID>());

    public ClientService(
        Link link, 
        ProcessConfig config,
        CriptoUtils criptoUtils,
        int nodeCount
    ){
        this.link = link;
        this.config = config;
        this.criptoUtils = criptoUtils;
        this.bucket = new ClientMessageBucket(nodeCount);

        LOGGER.shutdown();
    }

    public ProcessConfig getConfig() {
        return this.config;
    }

    /**
     * Requests current account balance for self.
     */
    public void getBalance() {
        getBalance(config.getId());
    }

    /**
     * Requests current account balance for [processId].
     * This could be a client or a node.
     */
    public void getBalance(String processId) {
        try {
            PublicKey clientPublicKey = criptoUtils.getPublicKey(processId);

            GetBalanceRequestMessage request = new GetBalanceRequestMessage(config.getId(), clientPublicKey);
            String requestStr = request.tojson();
            
            link.broadcast(new BlockchainRequestMessage(config.getId(), Message.Type.GET_BALANCE, requestStr));
        } catch (InvalidClientIdException e) { // [clientId] is unknown
            LOGGER.log(Level.INFO, "Invalid client ID");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, 
                MessageFormat.format("Error while trying to request balance for client id: {0}. \n{1}", processId, e.getMessage())
            );
        }
    }

    private void getBalanceSuccessResultReceived(BlockchainResponseMessage message) {
        LOGGER.log(Level.INFO, MessageFormat.format("Received get-balance sucess Result message from process {0}", message.getSenderId()));

        GetBalanceRequestSucessResultMessage response = message.deserializeGetBalanceSucessResultMessage();

        try {
            bucket.addAccountBalanceSuccessResponseMsg(response);

            if (
                bucket.hasAccountBalanceSucessQuorum(response)
                &&
                !displayedReplies.contains(response.getUuid())
            ) {
                displayedReplies.add(response.getUuid());
                formatReply(MessageFormat.format("Balance: {0} units", response.getBalance()));
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, 
                MessageFormat.format("Error after receiving getBalance success result. \n{0}", e.getMessage())
            );
        }
    }

    private void getBalanceErrorResultReceived(BlockchainResponseMessage message) {        
        LOGGER.log(Level.INFO, MessageFormat.format("Received get-balance error result message from process {0}", message.getSenderId()));
        
        GetBalanceRequestErrorResultMessage response = message.deserializeGetBalanceErrorResultMessage();
        try {
            bucket.addAccountBalanceErrorResponseMsg(response);

            if (
                bucket.hasAccountBalanceErrorQuorum(response)
                &&
                !displayedReplies.contains(response.getUuid())
            ) {
                displayedReplies.add(response.getUuid());
                formatReply(
                    MessageFormat.format("Error from the server: {0}", response.getErrorMessage())
                );
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, 
                MessageFormat.format("Error after receiving getBalance error result. \n{0}", e.getMessage())
            );
        }
    }

    public void transfer(String clientSourceId, String clientDestinationId, int amount, boolean isByzantine) {
        try {
            PublicKey destPublicKey;
            PublicKey sourcePublicKey;

            if (isByzantine) {
                sourcePublicKey = criptoUtils.getPublicKey(clientSourceId);
                destPublicKey = criptoUtils.getPublicKey(clientDestinationId); // searches for clients and nodes
            } else {
                sourcePublicKey = criptoUtils.getClientPublicKey(clientSourceId);
                destPublicKey = criptoUtils.getClientPublicKey(clientDestinationId);
            }

            // [messageToSign] represents a transaction
            byte[] messageToSign = Utils.joinArray(clientSourceId.getBytes(), clientDestinationId.getBytes(), Integer.toString(amount).getBytes());
            byte[] requestSignature = criptoUtils.getMessageSignature(messageToSign);

            TransferRequestMessage request = new TransferRequestMessage(config.getId(), sourcePublicKey, destPublicKey, amount, requestSignature);
            String requestStr = request.tojson();
            
            link.broadcast(new BlockchainRequestMessage(config.getId(), Message.Type.TRANSFER, requestStr));

        } catch (InvalidClientIdException e) { // [clientDestinationId] is unknown
            LOGGER.log(Level.INFO, "Invalid client ID!");
        } catch (InvalidIdException e) { // [clientDestinationId] is unknown
            LOGGER.log(Level.INFO, "Invalid ID!");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, 
                MessageFormat.format("Error while trying to request transfer to client id: {0}. \n{1}", clientDestinationId, e.getMessage())
            );
        }
    }

    public void transfer(String clientDestinationId, int amount) {
        transfer(config.getId(), clientDestinationId, amount, false);
    }

    private void onTransferSuccess(BlockchainResponseMessage message) {
        LOGGER.log(Level.INFO, MessageFormat.format("Received transfer sucess result message from process {0}", message.getSenderId()));

        TransferRequestSucessResultMessage response = message.deserializeTranferSucessResultMessage();
        try {
            bucket.addAccountTransferSuccessResponseMsg(response);

            if (
                bucket.hasAccountTransferSucessQuorum(response)
                &&
                !displayedReplies.contains(response.getUuid())
            ) {
                displayedReplies.add(response.getUuid());
                formatReply(MessageFormat.format("Transfer operation with id {0}, concluded!", response.getUuid()));
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, 
                MessageFormat.format("Error after receiving getBalance success result. \n{0}", e.getMessage())
            );
        }
    }

    private void onTransferError(BlockchainResponseMessage message) {
        LOGGER.log(Level.INFO, MessageFormat.format("Received tranfer error result message from process {0}", message.getSenderId()));

        TransferRequestErrorResultMessage response = message.deserializeTransferErrorResultMessage();
        try {
            bucket.addAccountTransferErrorResponseMsg(response);

            if (
                bucket.hasAccountTransferErrorQuorum(response)
                &&
                !displayedReplies.contains(response.getUuid())
            ) {
                displayedReplies.add(response.getUuid());
                formatReply(
                    MessageFormat.format("Error from the server: {0}", response.getError())
                );
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, 
                MessageFormat.format("Error after receiving transfer error result. \n{0}", e.getMessage())
            );
        }
    }

    private void formatReply(String msg) {
        System.out.println("\n\n-----------------------------------");
        System.out.println(msg);
        System.out.println("-----------------------------------\n\n");
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

                            switch (message.getType()) {

                                /*
                                case APPEND_REQUEST_RESULT ->
                                    appendValueResultReceived((BlockchainResponseMessage) message);
                                */

                                case GET_BALANCE_SUCESS_RESULT ->
                                    getBalanceSuccessResultReceived((BlockchainResponseMessage) message);

                                case GET_BALANCE_ERROR_RESULT ->
                                    getBalanceErrorResultReceived((BlockchainResponseMessage) message);

                                case TRANSFER_SUCESS_RESULT ->
                                    onTransferSuccess((BlockchainResponseMessage) message);

                                case TRANSFER_ERROR_RESULT ->
                                    onTransferError((BlockchainResponseMessage) message);

                                //default ->
                                //LOGGER.log(Level.INFO,
                                //        MessageFormat.format("{0} - Received unknown message from {1}",
                                //                config.getId(), message.getSenderId()));
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
