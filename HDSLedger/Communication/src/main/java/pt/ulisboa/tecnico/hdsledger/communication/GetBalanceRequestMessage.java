package pt.ulisboa.tecnico.hdsledger.communication;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.UUID;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.cripto.RSAKeyGenerator;

public class GetBalanceRequestMessage extends Message {

    private byte[] clientPublicKey;
    private byte[] helloSignature;
    private UUID uuid = UUID.randomUUID();

    public GetBalanceRequestMessage(String senderId, PublicKey clientPublicKey, byte[] helloSignature) {
        super(senderId, Type.APPEND_REQUEST);
        this.clientPublicKey = clientPublicKey.getEncoded();
        this.helloSignature = helloSignature;
    }

    public PublicKey getClientPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        return (PublicKey) RSAKeyGenerator.read(this.clientPublicKey, "pub");
    }

    public byte[] getHelloSignature() {
        return helloSignature;
    }

    public String tojson() {
        return new Gson().toJson(this);
    }

    public UUID getUuid() {
        return uuid;
    }
}
