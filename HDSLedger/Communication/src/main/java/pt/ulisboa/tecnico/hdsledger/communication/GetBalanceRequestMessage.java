package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.PublicKey;

public class GetBalanceRequestMessage extends Message {

    private PublicKey clientPublicKey;

    public GetBalanceRequestMessage(String senderId, PublicKey clientPublicKey) {
        super(senderId, Type.APPEND_REQUEST);
        this.clientPublicKey = clientPublicKey;
    }

    public PublicKey getClientPublicKey() {
        return clientPublicKey;
    }
}
