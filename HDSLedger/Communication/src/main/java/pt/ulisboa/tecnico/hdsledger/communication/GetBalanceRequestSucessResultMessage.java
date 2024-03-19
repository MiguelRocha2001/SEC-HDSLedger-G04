package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.cripto.RSAKeyGenerator;

public class GetBalanceRequestSucessResultMessage extends Message {

    private byte[] requestedPublickey;
    private int balance;

    public GetBalanceRequestSucessResultMessage(String senderId, int balance, PublicKey requestedPublickey) {
        super(senderId, Type.APPEND_REQUEST_RESULT);
        this.balance = balance;
        this.requestedPublickey = requestedPublickey.getEncoded();
    }

    public int getBalance() { return balance; }
    
    public PublicKey getRequestedPublickey() throws NoSuchAlgorithmException, InvalidKeySpecException { 
        return (PublicKey) RSAKeyGenerator.read(requestedPublickey, "pub");
    }

    public String tojson() {
        return new Gson().toJson(this);
    }
}
