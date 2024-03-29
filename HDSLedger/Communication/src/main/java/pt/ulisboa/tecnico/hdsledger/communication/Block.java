package pt.ulisboa.tecnico.hdsledger.communication;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.LinkedList;
import java.util.List;

import pt.ulisboa.tecnico.hdsledger.utilities.RandomStringGenerator;

public class Block {
    private List<Transaction> transactions;
    private String receiverId;

    public Block(List<Transaction> transactions, String receiverId) {
        this.transactions = transactions;
        this.receiverId = receiverId;
    }

    /**
     * Creates a block with one random transaction, signed by [privateKey].
     */
    public static Block createRandom(PrivateKey privateKey) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException, InvalidKeySpecException, IOException {
        Transaction randomTransactionV1 = Transaction.createRandom(privateKey);
        String randomReceiverId = RandomStringGenerator.generateRandomString(2);

        List<Transaction> transactions = new LinkedList<>();
        transactions.add(randomTransactionV1);

        return new Block(transactions, randomReceiverId); // TODO: improve later!
    }

    /**
     * Creates a Block with n random transactions, n given by the size of [signatures].
     * Each random transaction is given the valueSignature as the correspondent index in [signatures].
     */
    public static Block createRandom(List<byte[]> signatures) {
        String randomReceiverId = RandomStringGenerator.generateRandomString(2);

        List<Transaction> transactions = new LinkedList<>();
        for (byte[] signature : signatures) {
            Transaction randomTransactionV1 = Transaction.createRandom(signature);
            transactions.add(randomTransactionV1);
        }

        return new Block(transactions, randomReceiverId); // TODO: improve later!
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