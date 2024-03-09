package pt.ulisboa.tecnico.hdsledger.utilities;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ServerConfigBuilder {

    private final ServerConfig instance = new ServerConfig();

    public ServerConfig[] fromFile(String path) {
        System.out.println(path);
        try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(path))) {
            String input = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Gson gson = new Gson();
            return gson.fromJson(input, ServerConfig[].class);
        } catch (FileNotFoundException e) {
            throw new HDSSException(ErrorMessage.ConfigFileNotFound);
        } catch (IOException | JsonSyntaxException e) {
            throw new HDSSException(ErrorMessage.ConfigFileFormat);
        }
    }

    public static ProcessConfig[] fromServerConfigToProcessConfig(ServerConfig[] servers, boolean getClientPort) {
        ProcessConfig[] configs = new ProcessConfig[servers.length];

        for (int i = 0; i < configs.length; i++) {
            ServerConfig serverConfig = servers[i];
            
            int port;
            if (getClientPort)
                port = serverConfig.getClientPort();
            else
                port = serverConfig.getPort();

            configs[i] = new ProcessConfig(serverConfig.getId(), serverConfig.getHostname(), port, serverConfig.getByzantineBehavior());
        }

        return configs;
    }

}
