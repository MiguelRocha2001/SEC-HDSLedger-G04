package pt.ulisboa.tecnico.hdsledger.blockchain;

import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.blockchain.services.ConsensusService;
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
    private static String processesConfigPath = "src/main/resources/";

    public static void main(String[] args) {

        try {
            // Command line arguments
            String id = args[0];

            // Create configuration instances
            ServerConfig[] serverConfigsAux = new ServerConfigBuilder().fromFile(processesConfigPath + "blockchainConfig.json");
            ClientConfig[] clientConfigsAux = new ClientConfigBuilder().fromFile(processesConfigPath + "clientConfig.json");

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
            Link link = new Link(nodeConfig, nodeConfig.getPort(), nodesConfig);
            
            // Services that implement listen from UDPService
            ConsensusService consensusService = new ConsensusService(link, nodeConfigAux, leaderConfig,
            serverConfigsAux);
            
            consensusService.listen();

            if (nodeConfigAux.isLeader()) {
                consensusService.startConsensus("SOME RANDOM VALUE");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
