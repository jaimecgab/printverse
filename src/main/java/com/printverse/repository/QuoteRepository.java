package com.printverse.repository;

import com.printverse.domain.Quote;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface QuoteRepository extends JpaRepository<Quote, Long> {
    @Override
    @EntityGraph(attributePaths = {"customer", "items", "items.material", "items.printer"})
    Optional<Quote> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"customer", "items", "items.material", "items.printer"})
    @Query("select quote from Quote quote where quote.id = :id")
    Optional<Quote> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "customer")
    List<Quote> findAllByOrderByCreatedAtDesc();
}
