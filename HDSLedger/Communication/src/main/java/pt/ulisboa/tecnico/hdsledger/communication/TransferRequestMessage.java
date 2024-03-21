package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.UUID;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.cripto.RSAKeyGenerator;

public class TransferRequestMessage extends Message {

    private byte[] sourcePubKey;
    private byte[] destinationPubKey;
    private int amount;
    private byte[] valueSignature;
    
    private UUID uuid = UUID.randomUUID();

    public TransferRequestMessage(String senderId, PublicKey sourcePubKey, PublicKey destinationPubKey, int amount, byte[] valueSignature) {
        super(senderId, Type.APPEND_REQUEST);
        this.sourcePubKey = sourcePubKey.getEncoded();
        this.destinationPubKey = destinationPubKey.getEncoded();
        this.amount = amount;
        this.valueSignature = valueSignature;
    }

    public PublicKey getSourcePubKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        return (PublicKey) RSAKeyGenerator.read(this.sourcePubKey, "pub");
    }

    public PublicKey getDestinationPubKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        return (PublicKey) RSAKeyGenerator.read(this.destinationPubKey, "pub");
    }

    public int getAmount() {
        return amount;
    }

    public String tojson() {
        return new Gson().toJson(this);
    }

    public byte[] getValueSignature() {
        return valueSignature;
    }

    public UUID getUuid() {
        return uuid;
    }
}
