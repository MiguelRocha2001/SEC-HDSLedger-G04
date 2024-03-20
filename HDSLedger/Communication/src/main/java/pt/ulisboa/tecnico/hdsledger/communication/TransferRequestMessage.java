package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.PublicKey;

import com.google.gson.Gson;

public class TransferRequestMessage extends Message {

    private PublicKey sourcePubKey;
    private PublicKey destinationPubKey;
    private int amount;

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
}
