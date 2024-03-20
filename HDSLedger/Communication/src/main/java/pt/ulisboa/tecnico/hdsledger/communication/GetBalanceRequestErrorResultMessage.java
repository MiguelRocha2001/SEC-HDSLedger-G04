package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.UUID;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.cripto.RSAKeyGenerator;

public class GetBalanceRequestErrorResultMessage extends Message {

    private String errorMessage;
    private UUID uuid;
    
    public GetBalanceRequestErrorResultMessage(String senderId, String errorMessage, UUID uuid) {
        super(senderId, Type.APPEND_REQUEST_RESULT);
        this.errorMessage = errorMessage;
        this.uuid = uuid;
    }

    public String getErrorMessage() { return errorMessage; }

    public String tojson() {
        return new Gson().toJson(this);
    }

    public UUID getUuid() {
        return uuid;
    }
}