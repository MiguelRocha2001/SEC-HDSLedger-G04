package pt.ulisboa.tecnico.hdsledger.utilities;

public class ClientConfig {
    public ClientConfig() {}

    private String hostname;

    private String id;

    private int port;

    public int getPort() {
        return port;
    }

    public String getId() {
        return id;
    }

    public String getHostname() {
        return hostname;
    }


}
