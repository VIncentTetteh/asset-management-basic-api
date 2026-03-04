# Lazy Loading Fix for PostgreSQL Column Limit Issue

## Problem
You were experiencing this error when trying to save/merge Asset entities:
```
ERROR: target lists can have at most 1664 entries
org.postgresql.util.PSQLException
```

## Root Cause
Hibernate was configured with **eager loading** (the default) for all `@ManyToOne` relationships. This caused massive SQL JOIN queries when:
1. Saving an Asset through the repository
2. The entity merge operation tries to fetch the entire entity
3. Hibernate eagerly loads all related entities and their relationships
4. This created a chain of JOINs with 1700+ columns, exceeding PostgreSQL's limit of 1664

### Example Entity Relationships
- **Asset** has: Category, Location, User, Department, Organisation, PurchaseOrder, Supplier
- **Category** has: ParentCategory, DepreciationPolicy, Organisation
- **Department** has: ParentDepartment, Manager (User), Organisation
- **User** has: Role, Organisation, Department
- **PurchaseOrder** has: ApprovedBy (User), Department, Supplier, Organisation

Each level adds more joins and columns to the SELECT query.

## Solution
Changed all `@ManyToOne` relationships from eager loading (default) to **lazy loading** across these entities:

1. **Asset.java** - 6 relationships changed
   - `category`, `location`, `assignedUser`, `supplier`, `department`, `organisation`, `purchaseOrder`

2. **Category.java** - 2 relationships changed
   - `parentCategory`, `depreciationPolicy`, `organisation`

3. **Department.java** - 2 relationships changed
   - `parentDepartment`, `manager`, `organisation`

4. **User.java** - 2 relationships changed
   - `role`, `organisation`, `department`

5. **Location.java** - 2 relationships changed
   - `parentLocation`, `organisation`

6. **PurchaseOrder.java** - 3 relationships changed
   - `approvedBy`, `organisation`, `department`, `supplier`

7. **DepreciationPolicy.java** - 1 relationship changed
   - `organisation`

8. **Supplier.java** - 1 relationship changed
   - `organisation`

9. **Role.java** - 1 relationship changed
   - `organisation`

## Changes Made
```java
// BEFORE - Eager loading (default behavior)
@ManyToOne
private Category category;

// AFTER - Lazy loading
@ManyToOne(fetch = FetchType.LAZY)
private Category category;
```

## Benefits
1. ✅ Fixes the PostgreSQL column limit error
2. ✅ Reduces SQL query size significantly
3. ✅ Improves database performance by loading only needed data
4. ✅ Reduces memory consumption
5. ✅ Prevents circular loading issues

## Trade-offs
- **Lazy loading** means related entities are loaded only when accessed
- If you access a lazy-loaded relationship outside a transaction, you may get a `LazyInitializationException`
- **Solution**: Use `@Transactional` annotations on service methods (which you already do)

## Testing
1. Rebuild the project: `mvn clean compile`
2. Test Asset creation/update operations
3. Test merge/save operations
4. Monitor that related entities load correctly when accessed

## Next Steps (if needed)
If you need to eagerly load specific relationships in certain queries:
1. Use JPA named queries with `@Query` and `fetch join`
2. Use Spring Data's `EntityGraph` annotations
3. Create custom query methods that explicitly fetch what you need

Example:
```java
@Query("SELECT a FROM Asset a JOIN FETCH a.category JOIN FETCH a.organisation WHERE a.id = :id")
Asset findAssetWithRelations(@Param("id") UUID id);
```

## Files Modified
- `/src/main/java/com/example/demo/models/Asset.java`
- `/src/main/java/com/example/demo/models/Category.java`
- `/src/main/java/com/example/demo/models/Department.java`
- `/src/main/java/com/example/demo/models/User.java`
- `/src/main/java/com/example/demo/models/Location.java`
- `/src/main/java/com/example/demo/models/PurchaseOrder.java`
- `/src/main/java/com/example/demo/models/DepreciationPolicy.java`
- `/src/main/java/com/example/demo/models/Supplier.java`
- `/src/main/java/com/example/demo/models/Role.java`

