package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import pt.ulisboa.tecnico.hdsledger.utilities.RandomStringGenerator;

public class TransactionBlock {
    private List<TransactionV1> transactions;
    private String receiverId;

    public TransactionBlock(List<TransactionV1> transactions, String receiverId) {
        this.transactions = transactions;
        this.receiverId = receiverId;
    }

    public static TransactionBlock createRandom() {
        TransactionV1 randomTransactionV1 = TransactionV1.createRandom();
        String randomReceiverId = RandomStringGenerator.generateRandomString(2);

        List<TransactionV1> transactions = new LinkedList<>();
        transactions.add(randomTransactionV1);

        return new TransactionBlock(transactions, randomReceiverId); // TODO: improve later!
    }

    public List<TransactionV1> getTransactions() {
        return transactions;
    }

    public String getReceiverId() {
        return receiverId;
    }

    @Override
    public boolean equals(Object objectArg) {
        if (this == objectArg)
            return true;

        if (!(objectArg instanceof TransactionV2))
            return false;

        TransactionV2 transactionV2 = (TransactionV2) objectArg;
        
        if (
            receiverId.equals(transactionV2.getReceiverId())
            &&
            transactionv1.getAmount() == transactionV2.getAmount()
            &&
            transactionv1.getSourceId().equals(transactionV2.getSourceId())
            &&
            transactionv1.getDestinationId().equals(transactionV2.getDestinationId())
        ) return true;

        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((receiverId == null) ? 0 : receiverId.hashCode());
        result = prime * result + ((transactionv1 == null) ? 0 : transactionv1.hashCode());
        return result;
    }
}