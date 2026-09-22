package com.assetiq.repositories;

import com.assetiq.models.PoLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface PoLineItemRepository extends JpaRepository<PoLineItem, UUID> {

    List<PoLineItem> findByPurchaseOrderIdAndDeletedAtIsNullOrderByLineNumberAsc(UUID purchaseOrderId);

    List<PoLineItem> findByPurchaseOrderIdInAndDeletedAtIsNullOrderByLineNumberAsc(Collection<UUID> purchaseOrderIds);
}
