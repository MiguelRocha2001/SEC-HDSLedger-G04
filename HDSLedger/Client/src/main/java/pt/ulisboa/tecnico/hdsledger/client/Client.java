package pt.ulisboa.tecnico.hdsledger.client;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Scanner;
import java.util.logging.Level;

import pt.ulisboa.tecnico.hdsledger.client.services.ClientService;
import pt.ulisboa.tecnico.hdsledger.communication.BlockchainResponseMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.cripto.CriptoUtils;
import pt.ulisboa.tecnico.hdsledger.utilities.ByzantineBehavior;
import pt.ulisboa.tecnico.hdsledger.utilities.ClientConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ClientConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ServerConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ServerConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.Utils;

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
            // By only giving info about the nodes, Byzantine clients pretending to be nodes won't have any effect in the system
            Link linkToNodes = new Link(nodeConfig, nodeConfig.getPort(), serverConfigs, BlockchainResponseMessage.class, criptoUtils);

            int nodeCount = nodeIds.length;

            // Services that implement listen from UDPService
            ClientService clientService = new ClientService(linkToNodes, nodeConfig, criptoUtils, nodeCount);

            clientService.listen();
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Process is listenning on port host and port {1}:{2}",
                    nodeConfig.getId(), nodeConfig.getHostname(), nodeConfig.getPort()));

            Scanner in = new Scanner(System.in);
            boolean isByzantine = clientConfigAux.getByzantineBehavior() == ByzantineBehavior.CLIENT_IS_BYZANTINE;
            processRequests(clientService, in, isByzantine);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void processRequests(ClientService clientService, Scanner in, boolean isByzantine) {
        while (true) {
            printMenu();
            Operation oper = getOperation(in, isByzantine);

            if (oper instanceof Balance)
                clientService.getBalance();
            else if (oper instanceof ByzantineBalance)
                clientService.getBalance(((ByzantineBalance)oper).clientId);
            else if (oper instanceof Transfer) {
                Transfer operCasted = (Transfer)(oper);
                clientService.transfer(operCasted.targetId, operCasted.amount);
            } else if (oper instanceof ByzantineTransfer) {
                ByzantineTransfer operCasted = (ByzantineTransfer)(oper);
                clientService.transfer(operCasted.sourceId, operCasted.targetId, operCasted.amount);
            }
            else if (oper == null)
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

    private static abstract class Operation {}
    private static class Balance extends Operation {}
    private static class ByzantineBalance extends Operation {
        private String clientId;
        public ByzantineBalance(String clientId) {
            this.clientId = clientId;
        }
    }
    private static class Transfer extends Operation {
        String targetId;
        int amount;

        public Transfer(String targetId, int amount) {
            this.targetId = targetId;
            this.amount = amount;
        }
    }
    private static class ByzantineTransfer extends Transfer {
        private String sourceId;

        public ByzantineTransfer(String sourceId, String targetId, int amount) {
            super(targetId, amount);
            this.sourceId = sourceId;
        }
    }

    private static Operation getOperation(Scanner in, boolean isByzantine) {
        switch (in.nextLine()) {
            case "1": {
                if (isByzantine) {
                    System.out.println("Client ID: ");
                    String clientId = in.nextLine();
                    return new ByzantineBalance(clientId);
                } else
                    return new Balance();
            }

            case "2": {
                System.out.println("Source ID: ");
                String sourceId = in.nextLine();
                System.out.println("Amount: ");
                int amount = in.nextInt();
                if (isByzantine) {
                    System.out.println("Target ID: ");
                    String destinationId = in.nextLine();
                    return new ByzantineTransfer(sourceId, destinationId, amount);
                } else {
                    return new Transfer(sourceId, amount);
                }
            }

            default:
                return null;
        }
    }

}
