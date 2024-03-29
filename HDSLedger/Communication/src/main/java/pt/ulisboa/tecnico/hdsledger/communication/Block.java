package pt.ulisboa.tecnico.hdsledger.communication;

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

    public static Block createRandom() {
        Transaction randomTransactionV1 = Transaction.createRandom();
        String randomReceiverId = RandomStringGenerator.generateRandomString(2);

        List<Transaction> transactions = new LinkedList<>();
        transactions.add(randomTransactionV1);

        return new Block(transactions, randomReceiverId); // TODO: improve later!
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public String getReceiverId() {
        return receiverId;
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