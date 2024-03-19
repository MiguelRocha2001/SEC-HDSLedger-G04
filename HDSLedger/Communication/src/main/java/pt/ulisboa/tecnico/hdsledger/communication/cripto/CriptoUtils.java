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
import java.util.Base64;
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

    private PrivateKey privateKey;
    private Map<String, PublicKey> clientPublicKeys = new HashMap<>();
    private Map<String, PublicKey> nodePublicKeys = new HashMap<>();

    private final String nodeIds[];

    public class InvalidClientKeyException extends RuntimeException {}
    public class InvalidClientIdException extends RuntimeException {}

    /**
     * 
     */
    public CriptoUtils(String nodeId, String nodeIds[]) {
        try {
            this.nodeIds = nodeIds;
            loadPrivateKey(nodeId);
            loadPublicKeys();
        } catch (Exception e) {
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
    private boolean isNode(String processId) {
        for (int u=0; u < nodeIds.length; u++) {

            // Is a node/server
            if (processId.equals(nodeIds[u])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Loads private key from path "HDSLedger/resources/keys".
     * @param nodeId used to locate public key inside path
     */
    private void loadPrivateKey(String nodeId) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String pathToPrivKey = KEY_LOCATION + "private" + nodeId + ".key";
        this.privateKey = (PrivateKey) RSAKeyGenerator.read(pathToPrivKey, "priv");
    }

    /**
     * Loads all public keys inside "HDSLedger/resources/keys" path.
     */
    private void loadPublicKeys() throws IOException
    {
        // Iterate over all files in the directory
        Files.walk(directory)
            .filter(Files::isRegularFile)
            .forEach(filePath -> {

                String filename = filePath.getFileName().toString();

                if (filename.startsWith("public"))
                {
                    String processId = extractId(filename, "public"); // files are in form of <public[NUM].key>
                
                    // avoids loading repeated keys
                    if (
                        processId != null  
                        && 
                        (
                            !clientPublicKeys.containsKey(processId)
                            ||
                            !nodePublicKeys.containsKey(processId)
                        )
                    ) {
                        String pathToPubKey = KEY_LOCATION + "public" + processId + ".key";                
        
                        try {
                            PublicKey publicKey = (PublicKey) RSAKeyGenerator.read(pathToPubKey, "pub");

                            if (isNode(processId))
                                nodePublicKeys.put(processId, publicKey);
                            else
                                clientPublicKeys.put(processId, publicKey);

                            LOGGER.log(Level.INFO, MessageFormat.format("Process {0} keys loaded", processId));

                        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
                            // TODO: log error using costumn logger
                            e.printStackTrace();
                            throw new HDSSException(ErrorMessage.CannotLoadKeys);
                        }
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


    private PrivateKey getPrivateKey() {
        return privateKey;
    }

    public byte[] addSignatureToDataAndEncode(byte[] buf, String nodeId) 
        throws 
                IOException,
                NoSuchAlgorithmException, 
                InvalidKeyException, 
                SignatureException,
                InvalidKeySpecException
    {
        byte[] buffSigned = addSignatureToData(buf, nodeId);
        return Base64.getEncoder().encodeToString(buffSigned).getBytes(); // encodes to Base 64        
    }

    public byte[] addSignatureToData(byte[] buf, String nodeId) 
        throws 
            IOException, 
            NoSuchAlgorithmException, 
            InvalidKeyException, 
            SignatureException,
            InvalidKeySpecException
    {
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
        for (Map.Entry<String, PublicKey> entry : clientPublicKeys.entrySet()) {
            String clientId = entry.getKey();
            if (verifySignature(clientId, originalMessage, signature))
                return true;
        }
        return false;
    }

    public boolean verifySignature(PublicKey publicKey, byte[] originalMessage, byte[] signature) 
        throws 
                NoSuchAlgorithmException, 
                InvalidKeyException,
                SignatureException
    {
        Signature rsaForVerify = Signature.getInstance("SHA1withRSA");
        rsaForVerify.initVerify(publicKey);
        rsaForVerify.update(originalMessage);
        return rsaForVerify.verify(signature);
    }

    public boolean verifySignature(String senderId, byte[] originalMessage, byte[] signature)
        throws 
            IOException, 
            NoSuchAlgorithmException, 
            InvalidKeySpecException, 
            InvalidKeyException,
            SignatureException
    {
        PublicKey publicKey = null;

        for (Map.Entry<String, PublicKey> entry : clientPublicKeys.entrySet()) {
            String nodeId = entry.getKey();
            if (nodeId.equals(senderId)) {
                publicKey = entry.getValue();
                break; // Exit the loop once the key is found
            }
        }

        if (publicKey == null) {
            for (Map.Entry<String, PublicKey> entry : nodePublicKeys.entrySet()) {
                String nodeId = entry.getKey();
                if (nodeId.equals(senderId)) {
                    publicKey = entry.getValue();
                    break; // Exit the loop once the key is found
                }
            }
        }
        
        if (publicKey != null) {
            return verifySignature(publicKey, originalMessage, signature);
        } else {
            // Handle the case where keys for the specified node ID are not found
            throw new HDSSException(ErrorMessage.ProgrammingError); // TODO: improve later
        }
    }

    public boolean isAcossiatedWithClient(PublicKey publicKey) {
        for (Map.Entry<String, PublicKey> entry : clientPublicKeys.entrySet()) {
            if (entry.getValue().equals(publicKey))
                return true;
        }
        return false;
    }

    public String getClientId(PublicKey publicKey) {
        for (Map.Entry<String, PublicKey> entry : clientPublicKeys.entrySet()) {
            if (entry.getValue().equals(publicKey))
                return entry.getKey(); // client ID
        }
        throw new InvalidClientKeyException();
    }

    public PublicKey getClientPublicKey(String clientId) {
        PublicKey key = clientPublicKeys.get(clientId);

        if (key == null)
            throw new InvalidClientIdException();
        else
            return key;
    }
}