package pt.ulisboa.tecnico.hdsledger.client.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.Level;

import pt.ulisboa.tecnico.hdsledger.communication.AppendRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.LeaderChangeMessage;
import pt.ulisboa.tecnico.hdsledger.communication.AppendRequestResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.PrePrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ServerConfig;

public class ClientService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(ClientService.class.getName());
    // Nodes configurations
    private final ServerConfig[] serverConfig;

    private final ProcessConfig config;

    private String leaderId;

    // Link to communicate with nodes
    private final Link link;

    public ClientService(
        Link link, 
        ProcessConfig config, 
        ServerConfig[] serverConfig
    ){

        this.link = link;
        this.config = config;
        this.serverConfig = serverConfig;
    }

    public ProcessConfig getConfig() {
        return this.config;
    }

    public ConsensusMessage createConsensusMessage(String value, int instance, int round) {
        PrePrepareMessage prePrepareMessage = new PrePrepareMessage(value);

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE)
                .setConsensusInstance(instance)
                .setRound(round)
                .setMessage(prePrepareMessage.toJson())
                .build();

        return consensusMessage;
    }

    private void resultReceived(AppendRequestResultMessage message) {

        LOGGER.log(Level.INFO,
            MessageFormat.format(
                "{0} - Value appended in block: {1}",
                config.getId(), message.getBlockIndex()));

        return;
    }

    private void appendRequest(String value) {
        link.send(leaderId, new AppendRequestMessage(config.getId(), value));
    }

    public void onLeaderChange(LeaderChangeMessage message) {
        leaderId = message.getLeaderProcessId();
        String value = message.getValueToBeAppended();
        appendRequest(value);
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
                                    resultReceived((AppendRequestResultMessage) message);

                                case LIDER_CHANGE ->
                                    onLeaderChange((LeaderChangeMessage) message);

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
