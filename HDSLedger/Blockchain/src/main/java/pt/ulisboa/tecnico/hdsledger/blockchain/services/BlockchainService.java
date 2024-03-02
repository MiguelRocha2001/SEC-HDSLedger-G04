package pt.ulisboa.tecnico.hdsledger.blockchain.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.Iterator;

import pt.ulisboa.tecnico.hdsledger.communication.AppendRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.AppendRequestResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusDecidedMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.StartConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ServerConfig;

public class BlockchainService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(BlockchainService.class.getName());

    // Self process configuration
    private final ServerConfig config;
    private String leaderId; // TODO: should be updated every view change
    // Link to communicate with nodes
    private final Link link;

    private ConsensusService nodeService;

    private Map<String, String> pendingRequests = new HashMap<String, String>(); // Client Id -> value

    public BlockchainService(Link link, ConsensusService nodeService, ServerConfig config, ServerConfig leaderConfig) {
        this.nodeService = nodeService;
        this.config = config;
        this.link = link;
        this.leaderId = leaderConfig.getId();
    }

    private boolean isLeader() {
        return config.getId() == leaderId;
    }

    private void appendString(AppendRequestMessage message) {
        String senderId = message.getSenderId();

        LOGGER.log(Level.INFO,
            MessageFormat.format(
                "{0} - Received APPEND-REQUEST message from {1}",
                config.getId(), senderId));

        AppendRequestMessage appendRequest = message.deserializeAppendRequestMessage();
        String valueToAppend = appendRequest.getMessage();

        StartConsensusMessage msg = new StartConsensusMessage(config.getId(), valueToAppend);
        nodeService.startConsensus(msg);
        /*
        if (isLeader())
            nodeService.startConsensus(msg);
        else {
            link.send(leaderId, msg); // leaderId should never be null
        }
        */

        return;
    }

    /*
    public void valueDecided(ConsensusDecidedMessage message) {
        
        String appendedValue = message.getAppendedValue();
        Iterator<Map.Entry<String, String>> iterator = pendingRequests.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if (Objects.equals(appendedValue, entry.getValue())) {
                // Send response back to client
                link.send(entry.getKey(), new AppendRequestResultMessage(config.getId(), message.getBlockIndex()));
                // Remove entry
                iterator.remove();
            }
        }
    }
    */

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

                                case APPEND_REQUEST ->
                                    appendString((AppendRequestMessage) message);
                                
                                /*
                                case VALUE_DECIDED ->
                                    valueDecided((ConsensusDecidedMessage) message);
                                */

                                default ->
                                    LOGGER.log(Level.INFO,
                                            MessageFormat.format("{0} - Received unknown message from {1}",
                                                    config.getId(), message.getSenderId()));
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
