package pt.ulisboa.tecnico.hdsledger.communication.cripto;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;

import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSSException;
import pt.ulisboa.tecnico.hdsledger.utilities.Pair;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;

public class CriptoUtils {

    private String KEY_LOCATION = "../keys/";

    private Map<String, Pair<PublicKey, PrivateKey>> keys = new HashMap<>();

    public CriptoUtils(ProcessConfig[] nodes) {
        try {
            this.keys = loadKeys(nodes); 
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            // TODO: log error using costumn logger
            e.printStackTrace();
            throw new HDSSException(ErrorMessage.CannotLoadKeys);
        }
    }

    private Map<String, Pair<PublicKey, PrivateKey>> loadKeys(ProcessConfig[] nodes)
        throws IOException, NoSuchAlgorithmException, InvalidKeySpecException
    {
        Map<String, Pair<PublicKey, PrivateKey>> keys = new HashMap<>();

        for (int u=0; u < nodes.length; u++) {
            String nodeId = nodes[u].getId();

            String pathToPrivKey = KEY_LOCATION + "private" + nodeId + ".key";
            PrivateKey privateKey = (PrivateKey) RSAKeyGenerator.read(pathToPrivKey, "priv");
            String pathToPubKey = KEY_LOCATION + "public" + nodeId + ".key";                
            PublicKey publicKey = (PublicKey) RSAKeyGenerator.read(pathToPubKey, "pub");

            keys.put(nodeId, new Pair<>(publicKey, privateKey));
        }

        return keys;
    }

    private static byte[] appendArrays(byte[] arr1, byte[] arr2) {
        byte[] result = new byte[arr1.length + arr2.length];
        System.arraycopy(arr1, 0, result, 0, arr1.length);
        System.arraycopy(arr2, 0, result, arr1.length, arr2.length);
        return result;
    }

    public byte[] addSignatureToData(byte[] buf, String nodeId) 
        throws 
            IOException, 
            NoSuchAlgorithmException, 
            InvalidKeyException, 
            SignatureException,
            InvalidKeySpecException
    {
        PrivateKey privateKey = keys.get(nodeId).getValue();

        Signature rsaToSign = Signature.getInstance("SHA1withRSA");
        rsaToSign.initSign(privateKey);
        rsaToSign.update(buf);
        byte[] signature = rsaToSign.sign();

        return appendArrays(buf, signature);
    }

    public static byte[] removeSignature(byte[] buf) {
        byte[] signature = new byte[512];

        for (int i = 0; i < signature.length; i++) {
            signature[i] = buf[buf.length - 512 + i];
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

    public boolean verifySignature(String senderNodeId, byte[] originalMessage, byte[] signature)
        throws 
            IOException, 
            NoSuchAlgorithmException, 
            InvalidKeySpecException, 
            InvalidKeyException, 
            SignatureException
    {
        PublicKey publicKey = keys.get(senderNodeId).getKey();

        Signature rsaForVerify = Signature.getInstance("SHA1withRSA");
        rsaForVerify.initVerify(publicKey);
        rsaForVerify.update(originalMessage);
        return rsaForVerify.verify(signature);
    }
}
