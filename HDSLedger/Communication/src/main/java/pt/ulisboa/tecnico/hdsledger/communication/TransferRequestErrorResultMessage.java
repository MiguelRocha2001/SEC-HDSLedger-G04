package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.UUID;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.cripto.RSAKeyGenerator;

public class TransferRequestErrorResultMessage extends Message {

    private String errorMessage;
    private UUID uuid;

    public TransferRequestErrorResultMessage(String senderId, String errorMessage, UUID uuid) {
        super(senderId, Type.TRANSFER_ERROR_RESULT);
        this.errorMessage = errorMessage;
        this.uuid = uuid;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String tojson() {
        return new Gson().toJson(this);
    }

    public UUID getUuid() {
        return uuid;
    }
}
