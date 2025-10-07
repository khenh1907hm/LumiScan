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
        String qrText = "http://localhost:8080/order/" + tableNumber; // nội dung QR

        BitMatrix bitMatrix = qrCodeWriter.encode(qrText, BarcodeFormat.QR_CODE, 250, 250);

        // Đảm bảo thư mục tồn tại
        Path dirPath = Paths.get("src/main/resources/static/qrcodes");
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        // Tên file
        Path filePath = dirPath.resolve("table_" + tableNumber + ".png");

        // Ghi file QR
        MatrixToImageWriter.writeToPath(bitMatrix, "PNG", filePath);

        // Trả về path để lưu vào DB
        return "/qrcodes/table_" + tableNumber + ".png";
    }
}
