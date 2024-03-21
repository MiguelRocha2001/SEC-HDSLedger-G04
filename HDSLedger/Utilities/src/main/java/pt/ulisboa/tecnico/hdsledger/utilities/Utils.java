package pt.ulisboa.tecnico.hdsledger.utilities;

public class Utils {
    public static String[] getNodeIds(ServerConfig[] nodes) {
        String[] nodeIds = new String[nodes.length];

        for (int u = 0; u < nodes.length; u++) {
            nodeIds[u] = nodes[u].getId();
        }

        return nodeIds;
    }

    public static String[] joinArray(String[] arr1, String[] arr2) {
        String[] newArray = new String[arr1.length + arr2.length];

        int globalCounter = 0;
        for (int u = 0; u < arr1.length; u++) {
            newArray[globalCounter++] = arr1[u];
        }
        for (int u = 0; u < arr2.length; u++) {
            newArray[globalCounter++] = arr2[u];
        }

        return newArray;
    }

    
    public static byte[] joinArray(byte[] arr1, byte[] arr2) {
        byte[] newArray = new byte[arr1.length + arr2.length];

        int globalCounter = 0;
        for (int u = 0; u < arr1.length; u++) {
            newArray[globalCounter++] = arr1[u];
        }
        for (int u = 0; u < arr2.length; u++) {
            newArray[globalCounter++] = arr2[u];
        }

        return newArray;
    }

    public static byte[] joinArray(byte[] arr1, byte[] arr2, byte[] arr3) {
        byte[] newArrayAux = joinArray(arr1, arr2);
        return joinArray(newArrayAux, arr3);
    }
}
