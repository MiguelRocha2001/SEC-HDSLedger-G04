package pt.ulisboa.tecnico.hdsledger.blockchain;

import pt.ulisboa.tecnico.hdsledger.communication.BlockchainRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.cripto.CriptoUtils;
import pt.ulisboa.tecnico.hdsledger.blockchain.services.CriptoService;
import pt.ulisboa.tecnico.hdsledger.blockchain.services.NodeService;
import pt.ulisboa.tecnico.hdsledger.utilities.ClientConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ClientConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.Pair;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ServerConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ServerConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.Utils;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;


public class Node {

    private static final CustomLogger LOGGER = new CustomLogger(Node.class.getName());
    // Hardcoded path to files
    private static String PROCESSE_CONFIG_PATH = "../resources/";

    public static void main(String[] args) {

        try {
            // Command line arguments
            String id = args[0];
            String nodesConfigArg = args[1];
            String clientsConfigArg = args[2];

            // Create configuration instances
            ServerConfig[] nodesConfigAUx = new ServerConfigBuilder().fromFile(PROCESSE_CONFIG_PATH + nodesConfigArg);
            ClientConfig[] clientConfigsAux = new ClientConfigBuilder().fromFile(PROCESSE_CONFIG_PATH + clientsConfigArg);
            
            // Removes clients with ID equal to "null"
            clientConfigsAux = new ClientConfigBuilder().removeUnknownClients(clientConfigsAux);

            ProcessConfig[] serversConfig = ServerConfigBuilder.fromServerConfigToProcessConfig(nodesConfigAUx, false);
            ProcessConfig[] clientsConfig = ClientConfigBuilder.fromClientConfigToProcessConfig(clientConfigsAux);

            ServerConfig nodeConfigAux = Arrays.stream(nodesConfigAUx).filter(c -> c.getId().equals(id)).findAny().get();
            ProcessConfig nodeConfig = new ProcessConfig(nodeConfigAux.getId(), nodeConfigAux.getHostname(), nodeConfigAux.getPort(), nodeConfigAux.getByzantineBehavior());

            String[] nodeIds = Utils.getNodeIds(nodesConfigAUx);
            CriptoUtils criptoUtils = new CriptoUtils(id, nodeIds);

            // Abstraction to send and receive messages
            Link linkToNodes = new Link(nodeConfig, nodeConfig.getPort(), serversConfig, ConsensusMessage.class, criptoUtils);
            Link linkToClients = new Link(nodeConfig, nodeConfigAux.getClientPort(), clientsConfig, BlockchainRequestMessage.class, criptoUtils);

            // Services that implement listen from UDPService
            NodeService nodeService = new NodeService(linkToNodes, nodeConfigAux, nodesConfigAUx, criptoUtils);
            CriptoService criptoService = new CriptoService(linkToClients, nodeConfigAux, clientConfigsAux, nodeService, nodeIds, criptoUtils);

            nodeService.setCriptoService(criptoService);
            
            nodeService.listen();
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Process is listenning on port host and port {1}:{2}",
                    nodeConfig.getId(), nodeConfig.getHostname(), nodeConfig.getPort()));
                    
            criptoService.listen();
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Process is listenning on port host and port {1}:{2}",
                    nodeConfig.getId(), nodeConfig.getHostname(), nodeConfigAux.getClientPort()));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
