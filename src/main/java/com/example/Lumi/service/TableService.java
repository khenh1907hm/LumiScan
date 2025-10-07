package com.example.Lumi.service;

import com.example.Lumi.model.TableEntity;
import com.example.Lumi.repository.TableRepository;
import com.google.zxing.WriterException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class TableService {

    private final TableRepository tableRepository;
    private final QrCodeService qrCodeService;

    public TableService(TableRepository tableRepository, QrCodeService qrCodeService) {
        this.tableRepository = tableRepository;
        this.qrCodeService = qrCodeService;
    }

    // Lưu bàn mới + tự động sinh QR
    public void saveTable(TableEntity table) throws IOException, WriterException {
        String qrPath = qrCodeService.generateQrCode(table.getTableNumber());
        table.setQrCode(qrPath);
        tableRepository.save(table);
    }

    // Lấy tất cả bàn
    public List<TableEntity> getAll() {
        return tableRepository.findAll();
    }

    // Tìm bàn theo ID
    public Optional<TableEntity> findById(Long id) {
        return tableRepository.findById(id);
    }

    // Bật/tắt trạng thái bàn
    public void toggleTableStatus(Long id) {
        Optional<TableEntity> tableOpt = tableRepository.findById(id);
        if (tableOpt.isPresent()) {
            TableEntity table = tableOpt.get();
            if ("available".equals(table.getStatus())) {
                table.setStatus("occupied");
            } else {
                table.setStatus("available");
            }
            tableRepository.save(table);
        }
    }

    // Đếm tổng số bàn
    public long countAllTables() {
        return tableRepository.count();
    }

    // Đếm số bàn đang hoạt động
    public long countActiveTables() {
        return tableRepository.findAll().stream()
                .filter(table -> "available".equals(table.getStatus()))
                .count();
    }
}
