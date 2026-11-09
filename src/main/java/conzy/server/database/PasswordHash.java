/**
 * @author Conzy
 **/
package conzy.server.database;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHash {
    private PasswordHash() {
    }

    public static boolean isMd5(String stored) {
        if (stored == null || stored.length() != 32) {
            return false;
        }
        for (int i = 0; i < stored.length(); i++) {
            char c = stored.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    public static String hash(String password) {
        return hex(digest(password));
    }

    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        if (isMd5(stored)) {
            return MessageDigest.isEqual(digest(password), fromHex(stored));
        }
        if (stored.startsWith("pbkdf2:")) {
            return verifyPbkdf2(password, stored);
        }
        return password.equals(stored);
    }

    private static byte[] digest(String password) {
        try {
            return MessageDigest.getInstance("MD5").digest(password.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("hash", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            out.append(Character.forDigit((value >> 4) & 15, 16));
            out.append(Character.forDigit(value & 15, 16));
        }
        return out.toString();
    }

    private static byte[] fromHex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            out[i] = (byte) ((high << 4) + low);
        }
        return out;
    }

    private static boolean verifyPbkdf2(String password, String stored) {
        String[] part = stored.split(":", 4);
        if (part.length != 4) {
            return false;
        }
        int iterations;
        try {
            iterations = Integer.parseInt(part[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (iterations < 1 || iterations > 2_000_000) {
            return false;
        }
        char[] chars = password.toCharArray();
        try {
            byte[] salt = Base64.getDecoder().decode(part[2]);
            byte[] expected = Base64.getDecoder().decode(part[3]);
            PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, 256);
            byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(actual, expected);
        } catch (Exception e) {
            return false;
        } finally {
            java.util.Arrays.fill(chars, '\0');
        }
    }
}
