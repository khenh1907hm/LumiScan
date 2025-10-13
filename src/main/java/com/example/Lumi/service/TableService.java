package com.example.Lumi.service;

import com.example.Lumi.model.TableEntity;
import com.example.Lumi.repository.TableRepository;
import com.google.zxing.WriterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class TableService {

    private static final Logger logger = LoggerFactory.getLogger(TableService.class);
    private final TableRepository tableRepository;
    private final QrCodeService qrCodeService;

    public TableService(TableRepository tableRepository, QrCodeService qrCodeService) {
        this.tableRepository = tableRepository;
        this.qrCodeService = qrCodeService;
    }

    public List<TableEntity> findAllTables() {
        try {
            logger.info("Fetching all tables from repository");
            return tableRepository.findAll();
        } catch (Exception e) {
            logger.error("Error fetching tables: ", e);
            throw new RuntimeException("Failed to fetch tables", e);
        }
    }

    public Optional<TableEntity> findById(Long id) {
        return tableRepository.findById(id);
    }

    public void createTable(TableEntity table) throws IOException, WriterException {
        // Set default status if not provided
        if (table.getStatus() == null) {
            table.setStatus("available");
        }
        
        // Generate and set QR code
        String qrPath = qrCodeService.generateQrCode(table.getTableNumber());
        table.setQrCode(qrPath);
        
        tableRepository.save(table);
    }

    public void updateTable(Long id, TableEntity updatedTable) {
        TableEntity existingTable = tableRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Invalid table Id:" + id));
        
        existingTable.setTableNumber(updatedTable.getTableNumber());
        existingTable.setStatus(updatedTable.getStatus());
        
        tableRepository.save(existingTable);
    }

    public void toggleTableStatus(Long id) {
        TableEntity table = tableRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Invalid table Id:" + id));
        
        // Toggle the status
        table.setStatus("available".equals(table.getStatus()) ? "occupied" : "available");
        tableRepository.save(table);
    }

    public long countAllTables() {
        logger.debug("Counting all tables");
        try {
            long count = tableRepository.count();
            logger.debug("Total table count: {}", count);
            return count;
        } catch (Exception e) {
            logger.error("Error counting tables: ", e);
            throw e;
        }
    }

    public long countActiveTables() {
        logger.debug("Counting active tables");
        try {
            long count = tableRepository.countByStatusAvailable();
            logger.debug("Active table count: {}", count);
            return count;
        } catch (Exception e) {
            logger.error("Error counting active tables: ", e);
            throw e;
        }
    }
    
}
