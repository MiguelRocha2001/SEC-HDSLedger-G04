package pt.ulisboa.tecnico.hdsledger.blockchain;

import pt.ulisboa.tecnico.hdsledger.communication.BlockchainRequestMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.blockchain.services.BlockchainService;
import pt.ulisboa.tecnico.hdsledger.blockchain.services.NodeService;
import pt.ulisboa.tecnico.hdsledger.utilities.ClientConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ClientConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.Pair;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ServerConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ServerConfigBuilder;

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

            // Create configuration instances
            ServerConfig[] nodesConfigAUx = new ServerConfigBuilder().fromFile(PROCESSE_CONFIG_PATH + "blockchainConfig.json");
            ClientConfig[] clientConfigsAux = new ClientConfigBuilder().fromFile(PROCESSE_CONFIG_PATH + "clientConfig.json");

            ProcessConfig[] serversConfig = ServerConfigBuilder.fromServerConfigToProcessConfig(nodesConfigAUx, false);
            ProcessConfig[] clientsConfig = ClientConfigBuilder.fromClientConfigToProcessConfig(clientConfigsAux);

            ServerConfig nodeConfigAux = Arrays.stream(nodesConfigAUx).filter(c -> c.getId().equals(id)).findAny().get();
            ProcessConfig nodeConfig = new ProcessConfig(nodeConfigAux.getId(), nodeConfigAux.getHostname(), nodeConfigAux.getPort());

            // Abstraction to send and receive messages
            Link linkToNodes = new Link(nodeConfig, nodeConfig.getPort(), serversConfig, ConsensusMessage.class);
            Link linkToClients = new Link(nodeConfig, nodeConfigAux.getClientPort(), clientsConfig, BlockchainRequestMessage.class);

            ArrayList<Pair<String, String>> requests = new ArrayList<Pair<String, String>>(); // holds client requests
            
            // Services that implement listen from UDPService
            NodeService nodeService = new NodeService(linkToNodes, nodeConfigAux, nodesConfigAUx, linkToClients, requests);
            BlockchainService blockchainService = new BlockchainService(linkToClients, nodeConfigAux, clientConfigsAux, nodeService, requests);
            
            nodeService.listen();
            blockchainService.listen();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
