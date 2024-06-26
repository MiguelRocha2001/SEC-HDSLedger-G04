package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.UUID;

import com.google.gson.Gson;

public class TransferRequestSuccessResultMessage extends Message {

    private UUID uuid;

    public TransferRequestSuccessResultMessage(String senderId, UUID uuid) {
        super(senderId, Type.TRANSFER_SUCCESS_RESULT);
        this.uuid = uuid;
    }

    public String tojson() {
        return new Gson().toJson(this);
    }

    public UUID getUuid() {
        return uuid;
    }
}
