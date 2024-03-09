package pt.ulisboa.tecnico.hdsledger.utilities;

public class ClientConfig {
    public ClientConfig() {}

    private String hostname;

    private String id;

    private int port;

    private ByzantineBehavior byzantineBehavior;

    public int getPort() {
        return port;
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
