package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.UUID;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.cripto.RSAKeyGenerator;

public class TransferRequestErrorResultMessage extends Message {

    private String error;
    private UUID uuid;

    public TransferRequestErrorResultMessage(String senderId, String error, UUID uuid) {
        super(senderId, Type.TRANSFER_ERROR_RESULT);
        this.error = error;
        this.uuid = uuid;
    }

    public String getError() {
        return error;
    }

    public String tojson() {
        return new Gson().toJson(this);
    }

    public UUID getUuid() {
        return uuid;
    }
}
