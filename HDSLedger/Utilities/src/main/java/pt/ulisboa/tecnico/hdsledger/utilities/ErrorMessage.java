package pt.ulisboa.tecnico.hdsledger.utilities;

public enum ErrorMessage {
    ConfigFileNotFound("The configuration file is not available at the path supplied"),
    ConfigFileFormat("The configuration file has wrong syntax"),
    NoSuchNode("Can't send a message to a non existing node"),
    SocketSendingError("Error while sending message"),
    CannotOpenSocket("Error while opening socket"),
    MessageVerificationFail("Digital signature could not be verified"),
    
    ProgrammingError("Programming error"), // TODO: Change to a more specific error
    CannotLoadKeys("Assymetric keys could not be loaded");

    private final String message;

    ErrorMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
