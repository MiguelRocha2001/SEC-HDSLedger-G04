package pt.ulisboa.tecnico.hdsledger.client.services;

import java.io.IOException;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Level;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.client.models.ClientMessageBucket;
import pt.ulisboa.tecnico.hdsledger.communication.AppendRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.LeaderChangeMessage;
import pt.ulisboa.tecnico.hdsledger.communication.AppendRequestResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.BlockchainRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.BlockchainResponseMessage;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestErrorResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestSucessResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.TransferRequestErrorResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.cripto.CriptoUtils;
import pt.ulisboa.tecnico.hdsledger.communication.cripto.CriptoUtils.InvalidClientIdException;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ServerConfig;

public class ClientService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(ClientService.class.getName());

    private final ProcessConfig config;
    private final CriptoUtils criptoUtils;

    // Link to communicate with nodes
    private final Link link;

    private final ClientMessageBucket bucket;

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
    }

    public ProcessConfig getConfig() {
        return this.config;
    }

    public void appendRequest(String value) {
        try {
            byte[] signature = criptoUtils.getMessageSignature(value.getBytes(), config.getId());
            byte[] signatureEnconded = Base64.getEncoder().encodeToString(signature).getBytes(); // encodes to Base 64
            AppendRequestMessage request = new AppendRequestMessage(config.getId(), value, new String(signatureEnconded));
            String requestStr = new Gson().toJson(request);

            link.broadcast(new BlockchainRequestMessage(config.getId(), Message.Type.APPEND_REQUEST, requestStr));
        } catch (Exception e) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0}", e));
        }
    }

    private void appendValueResultReceived(BlockchainResponseMessage message) {
        AppendRequestResultMessage response = message.deserializeAppendRequestResultMessage();

        LOGGER.log(Level.INFO,
            MessageFormat.format(
                "Value {0} appended in block: {1}",
                response.getAppendedValue(), response.getBlockIndex()));
    }

    public void getBalance(String clientId) {
        try {
            PublicKey clientPublicKey = criptoUtils.getClientPublicKey(clientId);
            GetBalanceRequestMessage request = new GetBalanceRequestMessage(config.getId(), clientPublicKey);
            String requestStr = new Gson().toJson(request);
            
            link.broadcast(new BlockchainRequestMessage(config.getId(), Message.Type.GET_BALANCE, requestStr));
        } catch (InvalidClientIdException e) {
            // TODO
        }
    }

    private void getBalanceSuccessResultReceived(BlockchainResponseMessage message) {        
        GetBalanceRequestSucessResultMessage response = message.deserializeGetBalanceSucessResultMessage();

        bucket.addAccountBalanceSuccessResponseMsg(response);

        if (bucket.hasAccountBalanceSucessQuorum(response))
            LOGGER.log(Level.INFO, MessageFormat.format("Balance: {0}", response.getBalance()));
    }

    private void getBalanceErrorResultReceived(BlockchainResponseMessage message) {        
        GetBalanceRequestErrorResultMessage response = message.deserializeGetBalanceErrorResultMessage();

        bucket.addAccountBalanceErrorResponseMsg(response);

        if (bucket.hasAccountBalanceErrorQuorum(response))
            LOGGER.log(Level.INFO, "Invalid public key!");        
    }

    public void transfer() {

    }

    private void onTransferSuccess() {
        LOGGER.log(Level.INFO, "Transfer conclided with success!");
    }

    private void onTransferError(BlockchainResponseMessage message) {
        TransferRequestErrorResultMessage response = message.deserializeTransferErrorResultMessage();
        
        LOGGER.log(Level.INFO, MessageFormat.format("Error on transder operation: {0}", response.getError()));
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

                                case APPEND_REQUEST_RESULT ->
                                    appendValueResultReceived((BlockchainResponseMessage) message);

                                case GET_BALANCE_SUCESS_RESULT ->
                                    getBalanceSuccessResultReceived((BlockchainResponseMessage) message);

                                case GET_BALANCE_ERROR_RESULT ->
                                    getBalanceErrorResultReceived((BlockchainResponseMessage) message);

                                case TRANSFER_SUCESS_RESULT ->
                                    onTransferSuccess();

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
