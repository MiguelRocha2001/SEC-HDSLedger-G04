package pt.ulisboa.tecnico.hdsledger.communication;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.UUID;

import pt.ulisboa.tecnico.hdsledger.communication.cripto.CriptoUtils;
import pt.ulisboa.tecnico.hdsledger.utilities.RandomByteArrayGenerator;
import pt.ulisboa.tecnico.hdsledger.utilities.RandomIntGenerator;
import pt.ulisboa.tecnico.hdsledger.utilities.RandomStringGenerator;
import pt.ulisboa.tecnico.hdsledger.utilities.Utils;

public class Transaction {
    private String sourceId; 
    private String destinationId;
    private int amount;
    private UUID requestUUID;
    private byte[] valueSignature;

    public Transaction(String sourceId, String destinationId, int amount, UUID requestUUID, byte[] valueSignature) {
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.amount = amount;
        this.requestUUID = requestUUID;
        this.valueSignature = valueSignature;
    }

    public static Transaction createRandom(PrivateKey privateKey) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException, InvalidKeySpecException, IOException {
        String randomSourceId = RandomStringGenerator.generateRandomString(2);
        String randomDestinationId = RandomStringGenerator.generateRandomString(2);
        int randomAmount = RandomIntGenerator.generateRandomInt(1, 100);
        UUID randomRequestUUID = UUID.randomUUID();

        byte[] messageToSign = Utils.joinArray(randomSourceId.getBytes(), randomDestinationId.getBytes(), Integer.toString(randomAmount).getBytes());
        byte[] requestSignature = CriptoUtils.getMessageSignature(messageToSign, privateKey);

        return new Transaction(randomSourceId, randomDestinationId, randomAmount, randomRequestUUID, requestSignature);
    }

    public static Transaction createRandom(byte[] signature) {
        String randomSourceId = RandomStringGenerator.generateRandomString(2);
        String randomDestinationId = RandomStringGenerator.generateRandomString(2);
        int randomAmount = RandomIntGenerator.generateRandomInt(1, 100);
        UUID randomRequestUUID = UUID.randomUUID();

        return new Transaction(randomSourceId, randomDestinationId, randomAmount, randomRequestUUID, signature);
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public int getAmount() {
        return amount;
    }

    public UUID getRequestUUID() {
        return requestUUID;
    }

    public byte[] getValueSignature() {
        return valueSignature;
    }

    @Override
    public boolean equals(Object objectArg) {
        if (this == objectArg)
            return true;

        if (!(objectArg instanceof Transaction))
            return false;

        Transaction otherTransaction = (Transaction) objectArg;

        return sourceId.equals(otherTransaction.getSourceId()) &&
            destinationId.equals(otherTransaction.getDestinationId()) &&
            amount == otherTransaction.getAmount() &&
            Arrays.equals(valueSignature, otherTransaction.getValueSignature());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + sourceId.hashCode();
        result = prime * result + destinationId.hashCode();
        result = prime * result + amount;
        result = prime * result + Arrays.hashCode(valueSignature);
        return result;
    }

}