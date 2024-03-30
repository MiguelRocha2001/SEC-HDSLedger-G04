package pt.ulisboa.tecnico.hdsledger.utilities;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class ClientConfigBuilder {

    private final ClientConfig instance = new ClientConfig();

    public ClientConfig[] removeUnknownClients(ClientConfig[] clientConfigs) {
        ArrayList<ClientConfig> trimmedClients = new ArrayList<ClientConfig>();
        
        for (int u = 0; u < clientConfigs.length; u++) {
            ClientConfig c = clientConfigs[u];
            if (!c.getId().equals("null"))
                trimmedClients.add(c);
        }

        return trimmedClients.toArray(new ClientConfig[1]);
    }

    public ClientConfig[] fromFile(String path) {
        try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(path))) {
            String input = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Gson gson = new Gson();
            return gson.fromJson(input, ClientConfig[].class);
        } catch (FileNotFoundException e) {
            throw new HDSSException(ErrorMessage.ConfigFileNotFound);
        } catch (IOException | JsonSyntaxException e) {
            throw new HDSSException(ErrorMessage.ConfigFileFormat);
        }
    }

    public static ProcessConfig[] fromClientConfigToProcessConfig(ClientConfig[] clients) {
        ProcessConfig[] configs = new ProcessConfig[clients.length];

        for (int i = 0; i < configs.length; i++) {
            ClientConfig clientConfig = clients[i];
            configs[i] = new ProcessConfig(clientConfig.getId(), clientConfig.getHostname(), clientConfig.getPort(), clientConfig.getByzantineBehavior());
        }

        return configs;
    }
}
