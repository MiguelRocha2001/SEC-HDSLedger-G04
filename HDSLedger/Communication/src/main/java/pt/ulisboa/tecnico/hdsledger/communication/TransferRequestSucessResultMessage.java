package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.UUID;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.cripto.RSAKeyGenerator;

public class TransferRequestSucessResultMessage extends Message {

    private UUID uuid;

    public TransferRequestSucessResultMessage(String senderId, UUID uuid) {
        super(senderId, Type.TRANSFER_SUCESS_RESULT);
        this.uuid = uuid;
    }
    
    public String tojson() {
        return new Gson().toJson(this);
    }

    public UUID getUuid() {
        return uuid;
    }
}
