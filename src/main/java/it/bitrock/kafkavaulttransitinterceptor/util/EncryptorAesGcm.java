package it.bitrock.kafkavaulttransitinterceptor.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

public class EncryptorAesGcm {

  public static final int IV_LENGTH_BYTE = 12;
  private static final String ENCRYPT_ALGO = "AES/GCM/NoPadding";
  private static final int TAG_LENGTH_BIT = 128;

  public static byte[] getRandomNonce(int numBytes) {
    byte[] nonce = new byte[numBytes];
    new SecureRandom().nextBytes(nonce);
    return nonce;
  }

  // AES-GCM needs GCMParameterSpec
  public static byte[] encrypt(byte[] pText, SecretKey secret, byte[] iv) throws Exception {

    Cipher cipher = Cipher.getInstance(ENCRYPT_ALGO);
    cipher.init(Cipher.ENCRYPT_MODE, secret, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
    return cipher.doFinal(pText);

  }

  // prefix IV length + IV bytes to cipher text
  public static byte[] encryptWithPrefixIV(byte[] pText, SecretKey secret, byte[] iv) throws Exception {

    byte[] cipherText = encrypt(pText, secret, iv);

    return ByteBuffer.allocate(iv.length + cipherText.length)
      .put(iv)
      .put(cipherText)
      .array();

  }


  public static byte[] decrypt(byte[] cText, SecretKey secret, byte[] iv) throws Exception {

    Cipher cipher = Cipher.getInstance(ENCRYPT_ALGO);
    cipher.init(Cipher.DECRYPT_MODE, secret, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
    return cipher.doFinal(cText);

  }

  public static byte[] decryptWithPrefixIV(byte[] cText, SecretKey secret) {

    ByteBuffer bb = ByteBuffer.wrap(cText);

    byte[] iv = new byte[IV_LENGTH_BYTE];
    bb.get(iv);
    //bb.get(iv, 0, iv.length);

    byte[] cipherText = new byte[bb.remaining()];
    bb.get(cipherText);

    byte[] plainText = null;
    try {
      plainText = decrypt(cipherText, secret, iv);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return plainText;

  }

}
