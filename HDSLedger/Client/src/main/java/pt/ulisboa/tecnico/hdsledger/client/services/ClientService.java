package pt.ulisboa.tecnico.hdsledger.client.services;

import java.io.IOException;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.Base64;
import java.util.logging.Level;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.AppendRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.LeaderChangeMessage;
import pt.ulisboa.tecnico.hdsledger.communication.AppendRequestResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.BlockchainRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.BlockchainResponseMessage;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.GetBalanceRequestResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
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

    public ClientService(
        Link link, 
        ProcessConfig config,
        CriptoUtils criptoUtils
    ){
        this.link = link;
        this.config = config;
        this.criptoUtils = criptoUtils;
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

    private void getBalanceResultReceived(BlockchainResponseMessage message) {
        GetBalanceRequestResultMessage response = message.deserializeGetBalanceResultMessage();

        LOGGER.log(Level.INFO, MessageFormat.format("Balance: {0}", response.getBalance()));
    }

    public void transfer() {

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

                                case GET_BALANCE_RESULT ->
                                    getBalanceResultReceived((BlockchainResponseMessage) message);

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
