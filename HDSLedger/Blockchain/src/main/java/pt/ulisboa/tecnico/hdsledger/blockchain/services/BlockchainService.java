package pt.ulisboa.tecnico.hdsledger.blockchain.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.logging.Level;
import pt.ulisboa.tecnico.hdsledger.communication.BlockchainRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.LeaderChangeMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
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
  
    private final ArrayList<Pair<String, String>> requests;
    private final NodeService nodeService;
    

    public BlockchainService(Link link, ServerConfig config, ClientConfig[] clientsConfig, NodeService nodeService, ArrayList<Pair<String, String>> requests) {
        this.link = link;
        this.config = config;
        this.clientConfigs = clientsConfig;
        this.nodeService = nodeService;
        this.requests = requests;
    }

    private void appendString(BlockchainRequestMessage message) {
        String senderId = message.getSenderId();

        LOGGER.log(Level.INFO,
            MessageFormat.format(
                "{0} - Received APPEND-REQUEST message from {1}",
                config.getId(), senderId));

        String valueToAppend = message.deserializeAppendRequest().getMessage();
    
        requests.add(new Pair<String,String>(senderId, valueToAppend));

        nodeService.startConsensus(valueToAppend);
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
