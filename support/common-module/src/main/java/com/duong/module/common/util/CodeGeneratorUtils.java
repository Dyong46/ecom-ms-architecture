package com.duong.module.common.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import lombok.SneakyThrows;

public class CodeGeneratorUtils {

  public static int DEFAULT_WIDTH = 150;
  public static int DEFAULT_HEIGHT = 150;


  @SneakyThrows
  public static byte[] generateQRCode(String text, int width, int height) {
    QRCodeWriter qrCodeWriter = new QRCodeWriter();
    BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height);

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    MatrixToImageWriter.writeToStream(bitMatrix, "PNG", byteArrayOutputStream);
    return byteArrayOutputStream.toByteArray();
  }

  public static byte[] generateQRCode(String text) {
    return generateQRCode(text, DEFAULT_WIDTH, DEFAULT_HEIGHT);
  }

  public static String generateQRCodeBase64(String text) {
    return Base64.getEncoder().encodeToString(generateQRCode(text));
  }

}
