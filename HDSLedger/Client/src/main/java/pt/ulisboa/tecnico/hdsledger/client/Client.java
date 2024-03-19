package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.communication.BlockchainResponseMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.cripto.CriptoUtils;
import pt.ulisboa.tecnico.hdsledger.client.services.ClientService;
import pt.ulisboa.tecnico.hdsledger.utilities.ServerConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ServerConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.Utils;
import pt.ulisboa.tecnico.hdsledger.utilities.ClientConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ClientConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Scanner;
import java.util.logging.Level;

public class Client {

    private static final CustomLogger LOGGER = new CustomLogger(Client.class.getName());
    private static String PROCESSE_CONFIG_PATH = "../resources/";

    public static void main(String[] args) {

        try {
            // Command line arguments
            String id = args[0];
            String clientsConfig = args[1];
            String nodesConfig = args[2];

            // Create configuration instances of the vailable server processes
            ServerConfig[] serverConfigsAux = new ServerConfigBuilder().fromFile(PROCESSE_CONFIG_PATH + nodesConfig);
            ClientConfig[] clientConfigsAux = new ClientConfigBuilder().fromFile(PROCESSE_CONFIG_PATH + clientsConfig);

            ProcessConfig[] serverConfigs = ServerConfigBuilder.fromServerConfigToProcessConfig(serverConfigsAux, true);

            ClientConfig clientConfigAux = Arrays.stream(clientConfigsAux).filter(c -> c.getId().equals(id)).findAny().get();
            ProcessConfig nodeConfig = new ProcessConfig(id, clientConfigAux.getHostname(), clientConfigAux.getPort(), clientConfigAux.getByzantineBehavior());

            String[] nodeIds = Utils.getNodeIds(serverConfigsAux);
            CriptoUtils criptoUtils = new CriptoUtils(id, nodeIds);

            // Abstraction to send and receive messages
            Link linkToNodes = new Link(nodeConfig, nodeConfig.getPort(), serverConfigs, BlockchainResponseMessage.class, criptoUtils);

            int nodeCount = nodeIds.length;

            // Services that implement listen from UDPService
            ClientService clientService = new ClientService(linkToNodes, nodeConfig, criptoUtils, nodeCount);

            clientService.listen();
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Process is listenning on port host and port {1}:{2}",
                    nodeConfig.getId(), nodeConfig.getHostname(), nodeConfig.getPort()));

            Scanner in = new Scanner(System.in);
            processRequests(clientService, in);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void processRequests(ClientService clientService, Scanner in) {
        while (true) {
            printMenu();
            Operation oper = getOperation(in);

            if (oper == Operation.BALANCE)
                clientService.getBalance();
            else if (oper == Operation.TRANSFER)
                clientService.transfer();
            else if (oper == Operation.INVALID)
                System.out.println("Invalid operation!");
        }
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("!!!!!!!!!!!!!!!! Options !!!!!!!!!!!!!!!!");
        System.out.println("1 to Request User Balance");
        System.out.println("2 to Request Tranfer");
        System.out.print("> ");
    }

    private static enum Operation { BALANCE, TRANSFER, INVALID }
    private static Operation getOperation(Scanner in) {
        String input = in.nextLine();
        switch (input) {
            case "1": return Operation.BALANCE;
            case "2": return Operation.TRANSFER;
            default:
                return Operation.INVALID;
        }
    }

}
