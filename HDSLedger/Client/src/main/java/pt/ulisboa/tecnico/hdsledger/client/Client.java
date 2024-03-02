package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.client.services.ClientService;
import pt.ulisboa.tecnico.hdsledger.utilities.ServerConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ServerConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

import java.util.Scanner;

public class Client {

    private static final CustomLogger LOGGER = new CustomLogger(Client.class.getName());
    private static String NODES_CONFIG_FILE_PATH = "../resources/";

    public static void main(String[] args) {

        try {
            // Command line arguments
            String id = args[0];
            String hostname = args[1];
            int port = Integer.parseInt(args[2]);

            // Create configuration instances of the vailable server processes
            ServerConfig[] serverConfigs = new ServerConfigBuilder().fromFile(NODES_CONFIG_FILE_PATH + "blockchainConfig.json");
            ProcessConfig[] serversConfig = ServerConfigBuilder.fromServerConfigToProcessConfig(serverConfigs);

            ProcessConfig nodeConfig = new ProcessConfig(id, hostname, port);
            
            // Abstraction to send and receive messages
            Link linkToNodes = new Link(nodeConfig, nodeConfig.getPort(), serversConfig);

            // Services that implement listen from UDPService
            ClientService clientService = new ClientService(linkToNodes, nodeConfig,
                    serverConfigs);

            clientService.listen();

            Scanner in = new Scanner(System.in);
            while (true) {
                
                // Prompt the user to enter some input
                System.out.print("Enter something: ");
                String input = in.nextLine();

                if (input == "exit") break;

                clientService.appendRequest(input);

            }
            in.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
