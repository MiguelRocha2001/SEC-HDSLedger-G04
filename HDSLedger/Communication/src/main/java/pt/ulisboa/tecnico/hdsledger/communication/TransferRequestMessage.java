package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.PublicKey;
import java.util.UUID;

import com.google.gson.Gson;

public class TransferRequestMessage extends Message {

    private PublicKey sourcePubKey;
    private PublicKey destinationPubKey;
    private int amount;
    private UUID uuid = UUID.randomUUID();

    public TransferRequestMessage(String senderId, PublicKey sourcePubKey, PublicKey destinationPubKey, int amount) {
        super(senderId, Type.APPEND_REQUEST);
        this.sourcePubKey = sourcePubKey;
        this.destinationPubKey = destinationPubKey;
        this.amount = amount;
    }

    public PublicKey getSourcePubKey() {
        return sourcePubKey;
    }

    public PublicKey getDestinationPubKey() {
        return destinationPubKey;
    }

    public int getAmount() {
        return amount;
    }

    public String tojson() {
        return new Gson().toJson(this);
    }

    public UUID getUuid() {
        return uuid;
    }
}
