package pt.ulisboa.tecnico.hdsledger.communication;

public class TransferRequestErrorResultMessage extends Message {

    private String error;

    public TransferRequestErrorResultMessage(String senderId, String error) {
        super(senderId, Type.TRANSFER_ERROR_RESULT);
        this.error = error;
    }

    public String getError() {
        return error;
    }
}
