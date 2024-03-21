package pt.ulisboa.tecnico.hdsledger.communication;

public class TransactionV2 {
    private TransactionV1 transactionv1;
    private String receiverId;

    public TransactionV2(TransactionV1 transactionv1, String receiverId) {
        this.transactionv1 = transactionv1;
        this.receiverId = receiverId;
    }

    public static TransactionV2 createRandom() {
        return new TransactionV2(null, null); // TODO: improve later!
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
}