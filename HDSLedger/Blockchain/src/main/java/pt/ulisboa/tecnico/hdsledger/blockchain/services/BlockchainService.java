package pt.ulisboa.tecnico.hdsledger.blockchain.services;

import java.io.IOException;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.logging.Level;

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
import pt.ulisboa.tecnico.hdsledger.communication.LeaderChangeMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.TransferRequestErrorResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.cripto.CriptoUtils;
import pt.ulisboa.tecnico.hdsledger.communication.cripto.CriptoUtils.InvalidClientKeyException;
import pt.ulisboa.tecnico.hdsledger.utilities.ByzantineBehavior;
import pt.ulisboa.tecnico.hdsledger.utilities.ClientConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.Pair;
import pt.ulisboa.tecnico.hdsledger.utilities.ServerConfig;

public class BlockchainService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(BlockchainService.class.getName());

    // Nodes configurations
    private final ClientConfig[] clientConfigs;
    // Current node is leader
    private final ServerConfig config;

    // Link to communicate with nodes
    private final Link link;
  
    private final ArrayList<Pair<String, Pair<String, String>>> requests;
    private final NodeService nodeService;

    private CriptoUtils criptoUtils;

    private final CryptocurrencyStorage storage;
    

    public BlockchainService(
        Link link,
        ServerConfig config, 
        ClientConfig[] clientsConfig,
        NodeService nodeService, 
        ArrayList<Pair<String, Pair<String, String>>> requests,
        String[] nodeIds,
        CriptoUtils criptoUtils
    ) {
        this.link = link;
        this.config = config;
        this.clientConfigs = clientsConfig;
        this.nodeService = nodeService;
        this.requests = requests;
        this.storage = new CryptocurrencyStorage(getClientIds(clientsConfig));
        this.criptoUtils = criptoUtils;
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
    private Integer checkBalance(PublicKey key) {
        if (criptoUtils.isAcossiatedWithClient(key)) {
            String clientId = criptoUtils.getClientId(key);
            return storage.getBalance(clientId);
        } else {
            return null;
        }
    }

    private void transfer(PublicKey source, PublicKey destination, int amount) {
        String sourceClientId = criptoUtils.getClientId(source);
        String destinationClientId = criptoUtils.getClientId(source);

        storage.transfer(sourceClientId, destinationClientId, amount);
    }

    private void appendString(BlockchainRequestMessage message) {
        String senderId = message.getSenderId();

        LOGGER.log(Level.INFO,
            MessageFormat.format(
                "{0} - Received APPEND-REQUEST message from {1}",
                config.getId(), senderId));

        AppendRequestMessage request = message.deserializeAppendRequest();
        String valueToAppend = request.getMessage();
        String valueSignature = request.getValueSignature();
    
        requests.add(new Pair<String, Pair<String, String>>(senderId, new Pair<String,String>(valueToAppend, valueSignature)));

        nodeService.startConsensus(valueToAppend, valueSignature);
    }

    private void getBalanceRequest(BlockchainRequestMessage message) {
        String senderId = message.getSenderId();

        LOGGER.log(Level.INFO,
            MessageFormat.format(
                "{0} - Received GET-BALANCE-REQUEST message from {1}",
                config.getId(), senderId));

        GetBalanceRequestMessage request = message.deserializeGetBalanceRequest();

        try {
            PublicKey clientPublicKey = request.getClientPublicKey();
            byte[] helloSiganture = request.getHelloSignature();

            // verifies if correspondent private key was used to sign the "hello" message
            if (!criptoUtils.verifySignature(clientPublicKey, "hello".getBytes(), helloSiganture)) {
                link.send(senderId, buildGetBalanceRequestErrorResult(GetBalanceErrroResultType.NOT_AUTHORIZED, clientPublicKey));
            }

            Integer balance = checkBalance(clientPublicKey); // null if [clientPublicKey] is unknown
            if (balance != null) {
                link.send(senderId, buildGetBalanceRequestSuccessResult(balance, clientPublicKey));
            } else {
                link.send(senderId, buildGetBalanceRequestErrorResult(GetBalanceErrroResultType.INVALID_ACCOUNT, clientPublicKey));
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Error: {1}", config.getId(), e.getMessage()));
        }
    }


    private enum GetBalanceErrroResultType { NOT_AUTHORIZED, INVALID_ACCOUNT }
    private BlockchainRequestMessage buildGetBalanceRequestErrorResult(GetBalanceErrroResultType type, PublicKey requestedPubliCkey) {
        String message;
        if (type == GetBalanceErrroResultType.INVALID_ACCOUNT)
            message = "Invalid Account";
        else
            message = "Not authorized";
        GetBalanceRequestErrorResultMessage reply = new GetBalanceRequestErrorResultMessage(config.getId(), message, requestedPubliCkey);
        String requestStr = new Gson().toJson(reply);
        return new BlockchainRequestMessage(config.getId(), Message.Type.GET_BALANCE_ERROR_RESULT, requestStr);
    }

    private BlockchainRequestMessage buildGetBalanceRequestSuccessResult(int balance, PublicKey requestedPubliCkey) {
        GetBalanceRequestSucessResultMessage reply = new GetBalanceRequestSucessResultMessage(config.getId(), balance, requestedPubliCkey);
        String requestStr = new Gson().toJson(reply);
        return new BlockchainRequestMessage(config.getId(), Message.Type.GET_BALANCE_SUCESS_RESULT, requestStr);
    }

    private void tranferRequest(BlockchainRequestMessage message) {
        String senderId = message.getSenderId();

        LOGGER.log(Level.INFO,
            MessageFormat.format(
                "{0} - Received TRANFER-REQUEST message from {1}",
                config.getId(), senderId));

        TransferRequestMessage request = message.deserializeTransferRequest();

        try {
            transfer(request.getSourcePubKey(), request.getDestinationPubKey(), request.getAmount());
            link.send(senderId, new BlockchainRequestMessage(config.getId(), Message.Type.TRANSFER_SUCESS_RESULT, null));

        } catch(InvalidAccountException e) {
            TransferRequestErrorResultMessage reply = new TransferRequestErrorResultMessage(config.getId(), "Invalid account!");
            String requestStr = new Gson().toJson(reply);
            link.send(senderId, new BlockchainRequestMessage(config.getId(), Message.Type.TRANSFER_ERROR_RESULT, requestStr));

        } catch (InvalidAmmountException e) {
            TransferRequestErrorResultMessage reply = new TransferRequestErrorResultMessage(config.getId(), "Invalid amount!");
            String requestStr = new Gson().toJson(reply);
            link.send(senderId, new BlockchainRequestMessage(config.getId(), Message.Type.TRANSFER_ERROR_RESULT, requestStr));

        } catch (InvalidClientKeyException e) {
            TransferRequestErrorResultMessage reply = new TransferRequestErrorResultMessage(config.getId(), "Invalid client public key!");
            String requestStr = new Gson().toJson(reply);
            link.send(senderId, new BlockchainRequestMessage(config.getId(), Message.Type.TRANSFER_ERROR_RESULT, requestStr));
        }
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

                                    case APPEND_REQUEST ->
                                        appendString((BlockchainRequestMessage) message);

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
