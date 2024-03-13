package pt.ulisboa.tecnico.hdsledger.utilities;

public class Utils {
    public static String[] getNodeIds(ServerConfig[] nodes) {
        String[] nodeIds = new String[nodes.length];

        for (int u = 0; u < nodes.length; u++) {
            nodeIds[u] = nodes[u].getId();
        }

        return nodeIds;
    }
}
