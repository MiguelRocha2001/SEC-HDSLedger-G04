package pt.ulisboa.tecnico.hdsledger.communication.cripto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSSException;
import pt.ulisboa.tecnico.hdsledger.utilities.Pair;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

public class CriptoUtils {

    private static final CustomLogger LOGGER = new CustomLogger(CriptoUtils.class.getName());

    private String KEY_LOCATION = "../resources/keys/";
    private Path directory = Paths.get(KEY_LOCATION);

    private Map<String, Pair<PublicKey, PrivateKey>> nodeKeys = new HashMap<>();
    private Map<String, Pair<PublicKey, PrivateKey>> clientKeys = new HashMap<>();

    /**
     * 
     */
    public CriptoUtils(String nodeIds[]) {
        try {
            loadKeys(nodeIds);
        } catch (IOException e) {
            // TODO: log error using costumn logger
            e.printStackTrace();
            throw new HDSSException(ErrorMessage.CannotLoadKeys);
        }
    }

     public static String extractId(String input, String patternAux) {
        // Define the pattern for the ID
        Pattern pattern = Pattern.compile(patternAux + "(\\d+)\\.key");
        Matcher matcher = pattern.matcher(input);

        // Check if the pattern matches the input string
        if (matcher.find()) {
            // Extract the ID group
            return matcher.group(1);
        } else {
            return null; // Pattern not found
        }
    }

    // checks if is node/server or not
    private boolean isNode(String processId, String nodeIds[]) {
        for (int u=0; u < nodeIds.length; u++) {

            // Is a node/server
            if (processId.equals(nodeIds[u])) {
                return true;
            }
        }
        return false;
    }

    // TODO: should only load public keys for other processes
    private void loadKeys(String nodeIds[]) throws IOException
    {
        // Iterate over all files in the directory
        Files.walk(directory)
            .filter(Files::isRegularFile)
            .forEach(filePath -> {

                String filename = filePath.getFileName().toString();
                String processId = extractId(filename, "public"); // files are in form of <public[NUM].key>
                
                // avoids loading repeated keys
                if (
                    processId != null 
                    && 
                    (
                        !clientKeys.containsKey(processId) 
                        || 
                        !nodeKeys.containsKey(processId)
                    )
                ) {
                    String pathToPrivKey = KEY_LOCATION + "private" + processId + ".key";
                    String pathToPubKey = KEY_LOCATION + "public" + processId + ".key";                
    
                    try {
                        PrivateKey privateKey = (PrivateKey) RSAKeyGenerator.read(pathToPrivKey, "priv");
                        PublicKey publicKey = (PublicKey) RSAKeyGenerator.read(pathToPubKey, "pub");

                        if (isNode(processId, nodeIds))
                            nodeKeys.put(processId, new Pair<>(publicKey, privateKey));
                        else
                            clientKeys.put(processId, new Pair<>(publicKey, privateKey));
    
                        LOGGER.log(Level.INFO, MessageFormat.format("Process {0} keys loaded", processId));
                    } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
                        // TODO: log error using costumn logger
                        e.printStackTrace();
                        throw new HDSSException(ErrorMessage.CannotLoadKeys);
                    }
                }
            });
    }

    private static byte[] appendArrays(byte[] arr1, byte[] arr2) {
        byte[] result = new byte[arr1.length + arr2.length];
        System.arraycopy(arr1, 0, result, 0, arr1.length);
        System.arraycopy(arr2, 0, result, arr1.length, arr2.length);
        return result;
    }


    private PrivateKey getPrivateKey(String processId) {
        Pair<PublicKey, PrivateKey> keys = nodeKeys.get(processId);

        if (keys == null)
            keys = clientKeys.get(processId);

        if (keys == null)
            throw new HDSSException(ErrorMessage.ProgrammingError); // TODO: improve later

        else
            return keys.getValue();
    }

    public byte[] addSignatureToData(byte[] buf, String nodeId) 
        throws 
            IOException, 
            NoSuchAlgorithmException, 
            InvalidKeyException, 
            SignatureException,
            InvalidKeySpecException
    {
        PrivateKey privateKey = getPrivateKey(nodeId);

        Signature rsaToSign = Signature.getInstance("SHA1withRSA");
        rsaToSign.initSign(privateKey);
        rsaToSign.update(buf);
        byte[] signature = rsaToSign.sign();

        return appendArrays(buf, signature);
    }

    public byte[] getMessageSignature(byte[] message, String senderId) 
        throws 
            IOException, 
            NoSuchAlgorithmException, 
            InvalidKeyException, 
            SignatureException,
            InvalidKeySpecException
    {
        byte[] msgWithSign = addSignatureToData(message, senderId);
        byte[] signature = extractSignature(msgWithSign);
        return signature;
    }

    public static byte[] extractSignature(byte[] data) {
        byte[] signature = new byte[512];

        for (int i = 0; i < signature.length; i++) {
            signature[i] = data[data.length - 512 + i];
        }
        return signature;
    }

    public static byte[] removeMessage(byte[] buf) {
        byte[] message = new byte[buf.length - 512];

        for (int i = 0; i < message.length; i++) {
            message[i] = buf[i];
        }
        return message;
    }

    /**
     * Tries to verify originalMessage with all client existing public keys until one is valid.
     */
    public boolean verifySignatureWithClientKeys(byte[] originalMessage, byte[] signature) 
        throws 
            IOException, 
            NoSuchAlgorithmException, 
            InvalidKeySpecException, 
            InvalidKeyException,
            SignatureException
    {
        for (Map.Entry<String, Pair<PublicKey, PrivateKey>> entry : clientKeys.entrySet()) {
            String clientId = entry.getKey();
            if (verifySignature(clientId, originalMessage, signature))
                return true;
        }
        return false;
    }

    public boolean verifySignature(String senderId, byte[] originalMessage, byte[] signature)
        throws 
            IOException, 
            NoSuchAlgorithmException, 
            InvalidKeySpecException, 
            InvalidKeyException,
            SignatureException
    {
        Pair<PublicKey, PrivateKey> keys = null;

        for (Map.Entry<String, Pair<PublicKey, PrivateKey>> entry : nodeKeys.entrySet()) {
            String nodeId = entry.getKey();
            if (nodeId.equals(senderId)) {
                keys = entry.getValue();
                break; // Exit the loop once the keys are found
            }
        }

        if (keys == null) {
            for (Map.Entry<String, Pair<PublicKey, PrivateKey>> entry : clientKeys.entrySet()) {
                String nodeId = entry.getKey();
                if (nodeId.equals(senderId)) {
                    keys = entry.getValue();
                    break; // Exit the loop once the keys are found
                }
            }
        }
        
        if (keys != null) {
            PublicKey publicKey = keys.getKey();
            
            Signature rsaForVerify = Signature.getInstance("SHA1withRSA");
            rsaForVerify.initVerify(publicKey);
            rsaForVerify.update(originalMessage);
            return rsaForVerify.verify(signature);
        
        } else {
            // Handle the case where keys for the specified node ID are not found
            throw new HDSSException(ErrorMessage.ProgrammingError); // TODO: improve later

        }
    }
}
