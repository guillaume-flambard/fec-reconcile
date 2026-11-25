package dev.memolabs.fec.core;

/** The file cannot be read as a FEC. The message is shown to the user, in French. */
public class FecFormatException extends RuntimeException {

    public FecFormatException(String message) {
        super(message);
    }
}
