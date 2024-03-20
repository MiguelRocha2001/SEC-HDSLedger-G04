package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.UUID;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.cripto.RSAKeyGenerator;

public class GetBalanceRequestSucessResultMessage extends Message {

    private int balance;
    private UUID uuid;

    public GetBalanceRequestSucessResultMessage(String senderId, int balance, UUID uuid) {
        super(senderId, Type.APPEND_REQUEST_RESULT);
        this.balance = balance;
        this.uuid = uuid;
    }

    public int getBalance() { return balance; }
    
    public String tojson() {
        return new Gson().toJson(this);
    }

    public UUID getUuid() {
        return uuid;
    }
}
