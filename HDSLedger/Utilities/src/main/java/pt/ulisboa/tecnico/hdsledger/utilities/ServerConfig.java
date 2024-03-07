package pt.ulisboa.tecnico.hdsledger.utilities;


public class ServerConfig {
    public ServerConfig() {}

    private boolean isLeader;

    private String hostname;

    private String id;

    private int port;
    
    private int clientPort;

    private ByzantineBehavior byzantineBehavior;

    public boolean isLeader() {
        return isLeader;
    }

    public int getPort() {
        return port;
    }

    public int getClientPort() {
        return clientPort;
    }

    public String getId() {
        return id;
    }

    public String getHostname() {
        return hostname;
    }

    public ByzantineBehavior getByzantineBehavior() {
        return byzantineBehavior;
    }
}
