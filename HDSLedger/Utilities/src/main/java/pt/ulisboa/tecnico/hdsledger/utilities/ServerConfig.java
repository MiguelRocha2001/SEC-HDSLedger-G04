package pt.ulisboa.tecnico.hdsledger.utilities;


public class ServerConfig {
    public ServerConfig() {}

    private boolean isLeader;

    private String hostname;

    private String id;

    private int port;

    private Atack atack; // null means it isn't byzantine

    public boolean isLeader() {
        return isLeader;
    }

    public int getPort() {
        return port;
    }

    public String getId() {
        return id;
    }

    public String getHostname() {
        return hostname;
    }

    public void setAtack(Atack atack) {
        this.atack = atack;
    }

    public Atack getAtack() {
        return atack;
    }

    public boolean isByzantine() {
        return atack != null;
    }
}
