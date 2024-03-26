package pt.ulisboa.tecnico.hdsledger.utilities;

import java.security.SecureRandom;
import java.util.Random;

public class RandomByteArrayGenerator {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public static byte[] generateRandomByteArray(int length) {
        // Create a byte array to store the generated random bytes
        byte[] randomBytes = new byte[length];

        // Generate random bytes using SecureRandom
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(randomBytes);

        return randomBytes;
    }
}
