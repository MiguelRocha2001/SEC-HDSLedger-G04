package pt.ulisboa.tecnico.hdsledger.communication;

import pt.ulisboa.tecnico.hdsledger.utilities.RandomStringGenerator;

public class TransactionV2 {
    private TransactionV1 transactionv1;
    private String receiverId;

    public TransactionV2(TransactionV1 transactionv1, String receiverId) {
        this.transactionv1 = transactionv1;
        this.receiverId = receiverId;
    }

    public static TransactionV2 createRandom() {
        TransactionV1 randomTransactionV1 = TransactionV1.createRandom();
        String randomReceiverId = RandomStringGenerator.generateRandomString(2);
        return new TransactionV2(randomTransactionV1, randomReceiverId); // TODO: improve later!
    }

    public String getSourceId() {
        return transactionv1.getSourceId();
    }

    public String getDestinationId() {
        return transactionv1.getDestinationId();
    }

    public int getAmount() {
        return transactionv1.getAmount();
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