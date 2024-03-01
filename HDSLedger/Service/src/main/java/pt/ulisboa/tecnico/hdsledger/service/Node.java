package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.service.services.ConsensusService;
import pt.ulisboa.tecnico.hdsledger.service.services.BlockchainService;
import pt.ulisboa.tecnico.hdsledger.utilities.ClientConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ClientConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ServerConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ServerConfigBuilder;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.logging.Level;

public class Node {

    private static final CustomLogger LOGGER = new CustomLogger(Node.class.getName());
    // Hardcoded path to files
    private static String serversConfigPath = "src/main/resources/";
    private static String clientsConfigPath = "src/main/resources/";

    public static void main(String[] args) {

        try {
            // Command line arguments
            String id = args[0];
            serversConfigPath += args[1];

            // Create configuration instances
            ServerConfig[] serverConfigsAux = new ServerConfigBuilder().fromFile(serversConfigPath);
            ClientConfig[] clientConfigsAux = new ClientConfigBuilder().fromFile(clientsConfigPath);

            ProcessConfig[] serverConfigs = ServerConfigBuilder.fromServerConfigToProcessConfig(serverConfigsAux);
            ProcessConfig[] clientConfigs = ClientConfigBuilder.fromClientConfigToProcessConfig(clientConfigsAux);

            ProcessConfig[] nodesConfig = ProcessConfig.joinArrays(serverConfigs, clientConfigs);

            ServerConfig leaderConfig = Arrays.stream(serverConfigsAux).filter(ServerConfig::isLeader).findAny().get();

            ServerConfig nodeConfigAux = Arrays.stream(serverConfigsAux).filter(c -> c.getId().equals(id)).findAny().get();
            ProcessConfig nodeConfig = new ProcessConfig(nodeConfigAux.getId(), nodeConfigAux.getHostname(), nodeConfigAux.getPort());

            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Running at {1}:{2}; is leader: {3}",
                    nodeConfig.getId(), nodeConfig.getHostname(), nodeConfig.getPort(),
                    nodeConfigAux.isLeader()));

            // Abstraction to send and receive messages
            Link link = new Link(nodeConfig, nodeConfig.getPort(), nodesConfig,
                    Message.class);

            // Services that implement listen from UDPService
            ConsensusService consensusService = new ConsensusService(link, nodeConfigAux, leaderConfig,
            serverConfigsAux);
            
            BlockchainService blockchainService = new BlockchainService(link, consensusService, nodeConfigAux, leaderConfig);

            consensusService.listen();
            blockchainService.listen();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
