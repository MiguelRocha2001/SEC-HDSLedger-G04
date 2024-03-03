package pt.ulisboa.tecnico.hdsledger.utilities;

public class ServerConfig {
    public ServerConfig() {}

    private boolean isLeader;

    private String hostname;

    private String id;

    private int port;

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

    /*
    public static ProcessConfig[] toProcessConfig(ServerConfig[] configs) {
        ProcessConfig[] processConfig = new ProcessConfig[configs.length];

        for (int u = 0; u < processConfig.length; u++) {
            ServerConfig serverConfig = configs[u];
            processConfig[u] = new ProcessConfig(serverConfig.getId(), serverConfig.getHostname(), serverConfig.getPort());
        }

        return processConfig;
    }
    */
}
