package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.cripto.RSAKeyGenerator;

public class TransferRequestSucessResultMessage extends Message {

    private byte[] clientDestinationPubKey;

    public TransferRequestSucessResultMessage(String senderId, PublicKey clientDestinationPubKey) {
        super(senderId, Type.TRANSFER_SUCESS_RESULT);
        this.clientDestinationPubKey = clientDestinationPubKey.getEncoded();
    }
    
    public PublicKey getClientDestinationPubKey() throws NoSuchAlgorithmException, InvalidKeySpecException { 
        return (PublicKey) RSAKeyGenerator.read(clientDestinationPubKey, "pub");
    }

    public String tojson() {
        return new Gson().toJson(this);
    }
}
