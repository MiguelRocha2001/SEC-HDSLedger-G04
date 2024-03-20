package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.cripto.RSAKeyGenerator;

public class GetBalanceRequestErrorResultMessage extends Message {

    private String errorMessage;
    private byte[] requestedPublickey;
    
    public GetBalanceRequestErrorResultMessage(String senderId, String errorMessage, PublicKey requestedPublickey) {
        super(senderId, Type.APPEND_REQUEST_RESULT);
        this.errorMessage = errorMessage;
        this.requestedPublickey = requestedPublickey.getEncoded();
    }

    public String getErrorMessage() { return errorMessage; }
 
    public PublicKey getRequestedPublickey() throws NoSuchAlgorithmException, InvalidKeySpecException { 
        return (PublicKey) RSAKeyGenerator.read(requestedPublickey, "pub");
    }

    public String tojson() {
        return new Gson().toJson(this);
    }
}