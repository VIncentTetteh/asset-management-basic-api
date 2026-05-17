package com.assetiq.repositories;

import com.assetiq.dto.AssetFilterRequest;
import com.assetiq.models.Asset;
import com.assetiq.models.Organisation;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class AssetSpecification {

    private AssetSpecification() {}

    /**
     * Builds a compound JPA Specification scoped to the tenant org.
     * All predicates are AND-combined. Soft-deleted records are always excluded.
     *
     * Fetch joins are guarded from the COUNT query to prevent the Hibernate
     * "specified join fetching, but the owner of the fetched association was
     * not present" exception that Spring Data JPA triggers on its count query.
     */
    public static Specification<Asset> filtered(Organisation organisation, AssetFilterRequest req) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Tenant scope — always present
            predicates.add(cb.equal(root.get("organisation"), organisation));
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (req.status()    != null) predicates.add(cb.equal(root.get("status"),    req.status()));
            if (req.condition() != null) predicates.add(cb.equal(root.get("condition"), req.condition()));
            if (req.assetType() != null) predicates.add(cb.equal(root.get("assetType"), req.assetType()));

            if (req.departmentId()   != null) predicates.add(cb.equal(root.get("department").get("id"),   req.departmentId()));
            if (req.categoryId()     != null) predicates.add(cb.equal(root.get("category").get("id"),     req.categoryId()));
            if (req.locationId()     != null) predicates.add(cb.equal(root.get("location").get("id"),     req.locationId()));
            if (req.assignedUserId() != null) predicates.add(cb.equal(root.get("assignedUser").get("id"), req.assignedUserId()));

            if (req.purchaseDateFrom()     != null) predicates.add(cb.greaterThanOrEqualTo(root.get("purchaseDate"),    req.purchaseDateFrom()));
            if (req.purchaseDateTo()       != null) predicates.add(cb.lessThanOrEqualTo(root.get("purchaseDate"),       req.purchaseDateTo()));
            if (req.warrantyExpiryBefore() != null) predicates.add(cb.lessThanOrEqualTo(root.get("warrantyExpiryDate"), req.warrantyExpiryBefore()));

            if (req.search() != null && !req.search().isBlank()) {
                String pattern = "%" + req.search().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("name")),         pattern),
                    cb.like(cb.lower(root.get("assetTag")),     pattern),
                    cb.like(cb.lower(root.get("serialNumber")), pattern),
                    cb.like(cb.lower(root.get("manufacturer")), pattern),
                    cb.like(cb.lower(root.get("model")),        pattern)
                ));
            }

            // Fetch joins only on the data query — not on the COUNT query
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("category",     JoinType.LEFT);
                root.fetch("department",   JoinType.LEFT);
                root.fetch("location",     JoinType.LEFT);
                root.fetch("assignedUser", JoinType.LEFT);
                root.fetch("supplier",     JoinType.LEFT);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
