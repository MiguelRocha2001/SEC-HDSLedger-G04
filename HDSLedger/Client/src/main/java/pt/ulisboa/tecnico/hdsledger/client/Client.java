package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.communication.AppendRequestResultMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.client.services.ClientService;
import pt.ulisboa.tecnico.hdsledger.utilities.ServerConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ServerConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.ClientConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

import java.util.Arrays;

public class Client {

    private static final CustomLogger LOGGER = new CustomLogger(Client.class.getName());
    // Hardcoded path to files
    private static String nodesConfigPath = "src/main/resources/";

    public static void main(String[] args) {

        try {
            // Command line arguments
            String id = args[0];
            String hostname = args[1];
            int port = Integer.parseInt(args[2]);

            // Create configuration instances of the vailable server processes
            ServerConfig[] serverConfigs = new ServerConfigBuilder().fromFile(nodesConfigPath);
            ProcessConfig[] processesConfig = ServerConfigBuilder.fromServerConfigToProcessConfig(serverConfigs);

            ProcessConfig nodeConfig = new ProcessConfig(id, hostname, port);
            
            // Abstraction to send and receive messages
            Link linkToNodes = new Link(nodeConfig, nodeConfig.getPort(), processesConfig,
                    AppendRequestResultMessage.class);

            // Services that implement listen from UDPService
            ClientService nodeService = new ClientService(linkToNodes, nodeConfig,
                    serverConfigs);

            nodeService.listen();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
