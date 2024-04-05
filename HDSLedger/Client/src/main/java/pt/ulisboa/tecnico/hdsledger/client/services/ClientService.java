package pt.ulisboa.tecnico.hdsledger.client.services;

import java.io.IOException;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import javax.swing.text.html.Option;

import pt.ulisboa.tecnico.hdsledger.client.models.ClientMessageBucket;
import pt.ulisboa.tecnico.hdsledger.communication.BlockchainRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.BlockchainResponseMessage;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestErrorResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestSuccessResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.TransferRequestErrorResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.TransferRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.TransferRequestSuccessResultMessage;
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

     private final Set<UUID> processedReplies = Collections.synchronizedSet(new HashSet<UUID>());

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
            PublicKey processPublicKey = criptoUtils.getPublicKey(processId);

            GetBalanceRequestMessage request = new GetBalanceRequestMessage(config.getId(), processPublicKey);
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
        LOGGER.log(Level.INFO, MessageFormat.format("Received get-balance success result message from process {0}", message.getSenderId()));

        GetBalanceRequestSuccessResultMessage response = message.deserializeGetBalanceSuccessResultMessage();

        try {
            bucket.addAccountBalanceSuccessResponseMsg(response);

            Optional<Integer> balance = bucket.hasAccountBalanceSuccessQuorum(response.getUuid());
            if (
                balance.isPresent()
                &&
                !processedReplies.contains(response.getUuid())
            ) {
                processedReplies.add(response.getUuid());
                formatReply(MessageFormat.format("Balance: {0} units", balance.get()));
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

            Optional<String> errorMessage = bucket.hasAccountBalanceErrorQuorum(response.getUuid());
            if (
                errorMessage.isPresent()
                &&
                !processedReplies.contains(response.getUuid())
            ) {
                processedReplies.add(response.getUuid());
                formatReply(
                    MessageFormat.format("Error from the server: {0}", errorMessage.get())
                );
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                MessageFormat.format("Error after receiving getBalance error result. \n{0}", e.getMessage())
            );
        }
    }

    public void transfer(String processSourceId, String processDestinationId, int amount, boolean isByzantine) {
        try {
            PublicKey destPublicKey;
            PublicKey sourcePublicKey;

            if (isByzantine) {
                sourcePublicKey = criptoUtils.getPublicKey(processSourceId);
                destPublicKey = criptoUtils.getPublicKey(processDestinationId); // searches for clients and nodes
            } else {
                sourcePublicKey = criptoUtils.getClientPublicKey(processSourceId);
                destPublicKey = criptoUtils.getClientPublicKey(processDestinationId);
            }

            // [messageToSign] represents a transaction
            byte[] messageToSign = Utils.joinArray(processSourceId.getBytes(), processDestinationId.getBytes(), Integer.toString(amount).getBytes());
            byte[] requestSignature = criptoUtils.getMessageSignature(messageToSign);

            TransferRequestMessage request = new TransferRequestMessage(config.getId(), sourcePublicKey, destPublicKey, amount, requestSignature);
            String requestStr = request.tojson();

            link.broadcast(new BlockchainRequestMessage(config.getId(), Message.Type.TRANSFER, requestStr));

        } catch (InvalidClientIdException | InvalidIdException e) { // [clientDestinationId] is unknown
            formatReply("Invalid client ID!");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                MessageFormat.format("Error while trying to request transfer to client id: {0}. \n{1}", processDestinationId, e.getMessage())
            );
        }
    }

    public void transfer(String clientDestinationId, int amount) {
        transfer(config.getId(), clientDestinationId, amount, false);
    }

    private void onTransferSuccess(BlockchainResponseMessage message) {
        LOGGER.log(Level.INFO, MessageFormat.format("Received transfer success result message from process {0}", message.getSenderId()));

        TransferRequestSuccessResultMessage response = message.deserializeTransferSuccessResultMessage();
        try {
            bucket.addAccountTransferSuccessResponseMsg(response);

            if (
                bucket.hasAccountTransferSuccessQuorum(response.getUuid())
                &&
                !processedReplies.contains(response.getUuid())
            ) {
                processedReplies.add(response.getUuid());
                formatReply(MessageFormat.format("Transfer operation with id {0}, concluded!", response.getUuid()));

                // TODO: fetch the most common value
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                MessageFormat.format("Error after receiving getBalance success result. \n{0}", e.getMessage())
            );
        }
    }

    private void onTransferError(BlockchainResponseMessage message) {
        LOGGER.log(Level.INFO, MessageFormat.format("Received transfer error result message from process {0}", message.getSenderId()));

        TransferRequestErrorResultMessage response = message.deserializeTransferErrorResultMessage();
        try {
            bucket.addAccountTransferErrorResponseMsg(response);

            Optional<String> errorMessage = bucket.hasAccountTransferErrorQuorum(response.getUuid());
            if (
                errorMessage.isPresent()
                &&
                !processedReplies.contains(response.getUuid())
            ) {
                processedReplies.add(response.getUuid());
                formatReply(
                    MessageFormat.format("Error from the server: {0}", errorMessage.get())
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

                                case GET_BALANCE_SUCCESS_RESULT ->
                                    getBalanceSuccessResultReceived((BlockchainResponseMessage) message);

                                case GET_BALANCE_ERROR_RESULT ->
                                    getBalanceErrorResultReceived((BlockchainResponseMessage) message);

                                case TRANSFER_SUCCESS_RESULT ->
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
