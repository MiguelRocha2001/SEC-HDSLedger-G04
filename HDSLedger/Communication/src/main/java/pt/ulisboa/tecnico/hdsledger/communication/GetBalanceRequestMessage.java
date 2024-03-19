package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.PublicKey;

public class GetBalanceRequestMessage extends Message {

    private PublicKey clientPublicKey;
    private String helloSignature;

    public GetBalanceRequestMessage(String senderId, PublicKey clientPublicKey, String helloSignature) {
        super(senderId, Type.APPEND_REQUEST);
        this.clientPublicKey = clientPublicKey;
        this.helloSignature = helloSignature;
    }

    public GetBalanceRequestMessage(String senderId, PublicKey clientPublicKey, byte[] helloSignature) {
        this(senderId, clientPublicKey, new String(helloSignature));
    }

    public PublicKey getClientPublicKey() {
        return clientPublicKey;
    }

    public String getHelloSignature() {
        return helloSignature;
    }
}
