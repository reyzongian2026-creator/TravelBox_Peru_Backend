package com.tuempresa.storage.reservations.application.usecase;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

@Service
public class QrCodeService {

    public byte[] generatePng(String text) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 320, 320);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (WriterException | IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "QR_GENERATION_ERROR", "No se pudo generar el código QR.");
        }
    }

    public String generateDataUrl(String text) {
        byte[] png = generatePng(text);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }
}
