package com.example.Lumi.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class QrCodeService {

    public String generateQrCode(String tableNumber) throws IOException, WriterException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        String qrText = "http://localhost:8080/order/" + tableNumber;

        BitMatrix bitMatrix = qrCodeWriter.encode(qrText, BarcodeFormat.QR_CODE, 250, 250);

        // Using absolute path for production
        String rootPath = System.getProperty("user.dir");
        Path qrPath = Paths.get(rootPath, "src", "main", "resources", "static", "qrcodes");
        
        // Create directories if they don't exist
        if (!Files.exists(qrPath)) {
            Files.createDirectories(qrPath);
        }

        String fileName = "table_" + tableNumber + ".png";
        Path filePath = qrPath.resolve(fileName);
        
        // Write QR code to file
        MatrixToImageWriter.writeToPath(bitMatrix, "PNG", filePath);
        
        // Return relative path for database and web access
        return "/qrcodes/" + fileName;
    }
}
