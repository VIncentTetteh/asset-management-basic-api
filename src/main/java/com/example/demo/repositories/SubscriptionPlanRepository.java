package com.example.demo.repositories;

import com.example.demo.models.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {
    Optional<SubscriptionPlan> findByCodeAndDeletedAtIsNull(String code);

    List<SubscriptionPlan> findByActiveIsTrueAndDeletedAtIsNullOrderByAmountMinorAsc();
}

