package com.printverse.repository;

import com.printverse.domain.Printer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PrinterRepository extends JpaRepository<Printer, Long> {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    List<Printer> findAllByActiveTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select printer from Printer printer where printer.id = :id")
    Optional<Printer> findByIdForUpdate(@Param("id") Long id);
}
