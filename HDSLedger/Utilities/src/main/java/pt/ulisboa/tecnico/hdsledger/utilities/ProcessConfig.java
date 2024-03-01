package pt.ulisboa.tecnico.hdsledger.utilities;

public class ProcessConfig {

    private String hostname;

    private String id;

    private int port;

    public ProcessConfig(String id, String hostname, int port) {
        this.id = id;
        this.hostname = hostname;
        this.port = port;
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

    public static ProcessConfig[] joinArrays(ProcessConfig[] array1, ProcessConfig[] array2) {
        int length = array1.length + array2.length;
        ProcessConfig[] result = new ProcessConfig[length];
        
        System.arraycopy(array1, 0, result, 0, array1.length);
        System.arraycopy(array2, 0, result, array1.length, array2.length);

        return result;
    }
}
