package pt.ulisboa.tecnico.hdsledger.communication;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.LinkedList;
import java.util.List;

import pt.ulisboa.tecnico.hdsledger.utilities.RandomIntGenerator;
import pt.ulisboa.tecnico.hdsledger.utilities.RandomStringGenerator;

public class Block {
    private List<Transaction> transactions;
    private String receiverId;
    private int fee;

    public Block(List<Transaction> transactions, String receiverId, int fee) {
        this.transactions = transactions;
        this.receiverId = receiverId;
        this.fee = fee;
    }

    /**
     * Creates a block with one random transaction, signed by [privateKey].
     */
    public static Block createRandom(PrivateKey privateKey) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException, InvalidKeySpecException, IOException {
        Transaction randomTransaction = Transaction.createRandom(privateKey);
        String randomReceiverId = RandomStringGenerator.generateRandomString(2);
        int randomFee = RandomIntGenerator.generateRandomInt(1, 200);

        List<Transaction> transactions = new LinkedList<>();
        transactions.add(randomTransaction);

        return new Block(transactions, randomReceiverId, randomFee);
    }

    /**
     * Creates a Block with n random transactions, n given by the size of [signatures].
     * Each random transaction is given the valueSignature as the correspondent index in [signatures].
     */
    public static Block createRandom(List<byte[]> signatures) {
        String randomReceiverId = RandomStringGenerator.generateRandomString(2);
        int randomFee = RandomIntGenerator.generateRandomInt(1, 200);

        List<Transaction> transactions = new LinkedList<>();
        for (byte[] signature : signatures) {
            Transaction randomTransaction = Transaction.createRandom(signature);
            transactions.add(randomTransaction);
        }

        return new Block(transactions, randomReceiverId, randomFee);
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void removeTransactions(List<Transaction> transactionsToRemove) {
        transactions.removeAll(transactionsToRemove);
    }

    @Override
    public boolean equals(Object objectArg) {
        if (this == objectArg)
            return true;

        if (!(objectArg instanceof Block))
            return false;

        Block block = (Block) objectArg;
        
        if (
            receiverId.equals(block.getReceiverId())
            &&
            transactions.equals(block.getTransactions())
            &&
            fee == block.fee
        ) return true;

        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((receiverId == null) ? 0 : receiverId.hashCode());
        result = prime * result + ((transactions == null) ? 0 : transactions.hashCode());
        return result;
    }
}