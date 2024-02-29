package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.client.services.ClientService;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

import java.util.Arrays;

public class Client {

    private static final CustomLogger LOGGER = new CustomLogger(Client.class.getName());
    // Hardcoded path to files
    private static String nodesConfigPath = "src/main/resources/";

    public static void main(String[] args) {

        try {
            // Command line arguments
            String id = args[0];
            nodesConfigPath += args[1];

            // Create configuration instances
            ProcessConfig[] serverConfigs = new ProcessConfigBuilder().fromFile(nodesConfigPath);
            ProcessConfig nodeConfig = Arrays.stream(nodeConfigs).filter(c -> c.getId().equals(id)).findAny().get();
            
            // Abstraction to send and receive messages
            Link linkToNodes = new Link(nodeConfig, nodeConfig.getPort(), serverConfigs,
                    ClientMessage.class);

            // Services that implement listen from UDPService
            ClientService nodeService = new ClientService(linkToNodes, nodeConfig,
                    nodeConfigs);

            nodeService.listen();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
