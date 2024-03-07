package pt.ulisboa.tecnico.hdsledger.client.services;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.Level;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.AppendRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.LeaderChangeMessage;
import pt.ulisboa.tecnico.hdsledger.communication.AppendRequestResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.BlockchainRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.BlockchainResponseMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ServerConfig;

public class ClientService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(ClientService.class.getName());
    // Nodes configurations
    private final ServerConfig[] serverConfig;

    private final ProcessConfig config;

    private String leaderId = null;

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

    public void appendRequest(String value) {
        AppendRequestMessage request = new AppendRequestMessage(config.getId(), value);
        String requestStr = new Gson().toJson(request);

        link.broadcast(new BlockchainRequestMessage(config.getId(), Message.Type.APPEND_REQUEST, requestStr));
    }

    private void resultReceived(BlockchainResponseMessage message) {
        AppendRequestResultMessage response = message.deserializeAppendRequestResultMessage();

        LOGGER.log(Level.INFO,
            MessageFormat.format(
                "Value {0} appended in block: {1}",
                response.getAppendedValue(), response.getBlockIndex()));
        return;
    }

    public void onLeaderChange(BlockchainResponseMessage message) {
        LeaderChangeMessage response = message.deserializeLeaderChangeMessage();

        leaderId = response.getLeaderId();
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
                                    resultReceived((BlockchainResponseMessage) message);

                                case LIDER_CHANGE ->
                                    onLeaderChange((BlockchainResponseMessage) message);

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
