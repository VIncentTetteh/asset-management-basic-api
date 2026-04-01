package com.example.demo.repositories;

import com.example.demo.enums.CheckoutStatus;
import com.example.demo.models.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CheckoutRecordRepository extends JpaRepository<CheckoutRecord, UUID> {
    List<CheckoutRecord> findByOrganisationAndDeletedAtIsNull(Organisation org);
    List<CheckoutRecord> findByAssetAndDeletedAtIsNull(Asset asset);
    List<CheckoutRecord> findByCheckedOutByAndDeletedAtIsNull(User user);
    Optional<CheckoutRecord> findByAssetAndStatusAndDeletedAtIsNull(Asset asset, CheckoutStatus status);
    List<CheckoutRecord> findByOrganisationAndStatusAndDeletedAtIsNull(Organisation org, CheckoutStatus status);
}
