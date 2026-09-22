package com.printverse.repository;

import com.printverse.domain.ProductionOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductionOrderRepository extends JpaRepository<ProductionOrder, Long> {

    @EntityGraph(attributePaths = {"quote", "quote.customer", "items", "items.assignedPrinter"})
    @Query("select productionOrder from ProductionOrder productionOrder "
            + "order by case when productionOrder.dueDate is null then 1 else 0 end, "
            + "productionOrder.dueDate, productionOrder.createdAt")
    List<ProductionOrder> findAllForList();

    @Override
    @EntityGraph(attributePaths = {"quote", "quote.customer", "items", "items.assignedPrinter"})
    Optional<ProductionOrder> findById(Long id);

    @EntityGraph(attributePaths = {"quote", "quote.customer", "items", "items.assignedPrinter"})
    Optional<ProductionOrder> findByQuoteId(Long quoteId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select productionOrder from ProductionOrder productionOrder where productionOrder.id = :id")
    Optional<ProductionOrder> findByIdForUpdate(@Param("id") Long id);

    long countByQuoteCustomerId(Long customerId);
}
