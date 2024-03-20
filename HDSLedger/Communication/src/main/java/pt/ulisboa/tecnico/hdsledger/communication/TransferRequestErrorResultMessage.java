package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.cripto.RSAKeyGenerator;

public class TransferRequestErrorResultMessage extends Message {

    private String error;
    private byte[] clientDestinationPubKey;

    public TransferRequestErrorResultMessage(String senderId, String error, PublicKey clientDestinationPubKey) {
        super(senderId, Type.TRANSFER_ERROR_RESULT);
        this.error = error;
        this.clientDestinationPubKey = clientDestinationPubKey.getEncoded();
    }

    public String getError() {
        return error;
    }

    public PublicKey getClientDestinationPubKey() throws NoSuchAlgorithmException, InvalidKeySpecException { 
        return (PublicKey) RSAKeyGenerator.read(clientDestinationPubKey, "pub");
    }

    public String tojson() {
        return new Gson().toJson(this);
    }
}
