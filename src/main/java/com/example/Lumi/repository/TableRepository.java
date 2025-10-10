package com.example.Lumi.repository;

import com.example.Lumi.model.TableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface TableRepository extends JpaRepository<TableEntity, Long> {
    Optional<TableEntity> findByTableNumber(String tableNumber);
    
   @Query("SELECT COUNT(t) FROM TableEntity t WHERE t.status = 'available'")
   long countByStatusAvailable();

   @Override
   @Query("SELECT t FROM TableEntity t")
   List<TableEntity> findAll();
}
