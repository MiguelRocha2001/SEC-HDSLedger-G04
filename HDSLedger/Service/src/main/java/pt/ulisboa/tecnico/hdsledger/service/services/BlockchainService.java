package pt.ulisboa.tecnico.hdsledger.service.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.logging.Level;

import pt.ulisboa.tecnico.hdsledger.communication.AppendRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.LeaderChangeMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.StartConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

public class BlockchainService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(BlockchainService.class.getName());

    // Self process configuration
    private final ProcessConfig config;
    private String leaderId; // TODO: should be updated every view change
    // Link to communicate with nodes
    private final Link link;

    private ConsensusService nodeService;

    public BlockchainService(Link link, ConsensusService nodeService, ProcessConfig config, ProcessConfig leaderConfig) {
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
        if (isLeader())
            nodeService.startConsensus(msg);
        else {
            link.send(leaderId, msg); // leaderId should never be null
        }

        return;
    }

    public void valueDecided() {
        
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

                                case APPEND_REQUEST ->
                                    appendString((AppendRequestMessage) message);
                                
                                case VALUE_DECIDED ->
                                    valueDecided();

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
