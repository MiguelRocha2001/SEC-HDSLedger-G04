package pt.ulisboa.tecnico.hdsledger.utilities;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ClientConfigBuilder {

    private final ClientConfig instance = new ClientConfig();

    public ClientConfig[] fromFile(String path) {
        System.out.println(path);
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
            configs[i] = new ProcessConfig(clientConfig.getId(), clientConfig.getHostname(), clientConfig.getPort());
        }

        return configs;
    }
}
