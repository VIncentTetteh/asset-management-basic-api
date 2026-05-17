package com.assetiq.repositories;

import com.assetiq.dto.ExpenseFilterRequest;
import com.assetiq.models.Expense;
import com.assetiq.models.Organisation;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import java.util.ArrayList;
import java.util.List;

public final class ExpenseSpecification {
    private ExpenseSpecification() {}

    public static Specification<Expense> filtered(Organisation organisation, ExpenseFilterRequest req) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("organisation"), organisation));
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (req.status()          != null) predicates.add(cb.equal(root.get("status"),                 req.status()));
            if (req.category()        != null) predicates.add(cb.equal(root.get("category"),               req.category()));
            if (req.linkedBudgetId()  != null) predicates.add(cb.equal(root.get("linkedBudget").get("id"), req.linkedBudgetId()));
            if (req.linkedAssetId()   != null) predicates.add(cb.equal(root.get("linkedAsset").get("id"),  req.linkedAssetId()));
            if (req.submittedUserId() != null) predicates.add(cb.equal(root.get("submittedBy").get("id"),  req.submittedUserId()));
            if (req.departmentId()    != null) predicates.add(cb.equal(root.get("department").get("id"),   req.departmentId()));
            if (req.dateFrom()        != null) predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), req.dateFrom()));
            if (req.dateTo()          != null) predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"),    req.dateTo()));

            if (req.search() != null && !req.search().isBlank()) {
                String p = "%" + req.search().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")),       p),
                    cb.like(cb.lower(root.get("description")), p)
                ));
            }

            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("submittedBy",  JoinType.LEFT);
                root.fetch("linkedBudget", JoinType.LEFT);
                root.fetch("linkedAsset",  JoinType.LEFT);
                root.fetch("department",   JoinType.LEFT);
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
