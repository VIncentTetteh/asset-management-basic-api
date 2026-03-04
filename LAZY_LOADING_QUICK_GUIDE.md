# Quick Reference: Lazy Loading Configuration

## What Changed?
All `@ManyToOne` relationships in your model entities now use lazy loading instead of eager loading.

## Syntax
```java
// Single relationship - now lazy
@ManyToOne(fetch = FetchType.LAZY)
private Category category;

// Multiple relationships - independently lazy
@ManyToOne(fetch = FetchType.LAZY)
private Department department;

@ManyToOne(fetch = FetchType.LAZY)
private Organisation organisation;
```

## Entities Modified (9 total)
| Entity | Relationships Changed |
|--------|----------------------|
| Asset | 7 |
| Category | 3 |
| Department | 3 |
| User | 3 |
| Location | 2 |
| PurchaseOrder | 4 |
| DepreciationPolicy | 1 |
| Supplier | 1 |
| Role | 1 |

## How It Works
### Before (Eager Loading)
```
GET Asset(123)
  ↓ Hibernate automatically joins:
    → Category + DepreciationPolicy + ParentCategory + Organisation
    → Location + ParentLocation + Organisation
    → User (assignedUser) + Role + Department + Organisation
    → Department + ParentDepartment + Manager + Organisation
    → PurchaseOrder + ApprovedBy + Department + Supplier + Organisation
    → Supplier + Organisation
    → Organisation
  ↓ Result: 1700+ column SELECT query (FAILS - exceeds PostgreSQL limit)
```

### After (Lazy Loading)
```
GET Asset(123)
  ↓ Hibernate loads only Asset columns (~40 columns)
  ↓ When you access asset.getCategory():
    → Loads Category columns (~20 columns)
  ↓ Result: Multiple smaller queries (WORKS - efficient)
```

## Usage Tips

### ✅ DO: Access relationships within service methods
```java
@Service
@Transactional
public class AssetService {
    public AssetDto getAsset(UUID id) {
        Asset asset = assetRepository.findById(id).orElseThrow();
        // Safe to access lazy relationships here
        String categoryName = asset.getCategory().getName();
        return mapToDto(asset);
    }
}
```

### ❌ DON'T: Access lazy relationships after transaction closes
```java
@Service
public class AssetService {
    public AssetDto getAsset(UUID id) {
        return transactionTemplate.execute(status -> {
            Asset asset = assetRepository.findById(id).orElseThrow();
            return mapToDto(asset); // Transaction closes here
        });
        // Can't access asset.getCategory() here anymore
    }
}
```

### ✅ DO: Use EntityGraph for specific queries needing eager loading
```java
@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {
    // Load Asset with Category eagerly when needed
    @Query("SELECT a FROM Asset a JOIN FETCH a.category WHERE a.id = :id")
    Optional<Asset> findByIdWithCategory(@Param("id") UUID id);
}
```

## Performance Recommendations

1. **For REST endpoints** - Use lazy loading (you already have @Transactional)
2. **For batch operations** - Use queries with `JOIN FETCH` for better performance
3. **For reports** - Use DTOs with selective field loading
4. **Monitor logs** - Watch for N+1 query problems (multiple queries for lists)

## Troubleshooting

### Issue: LazyInitializationException
**Error**: "could not initialize proxy - no Session"
**Cause**: Accessing lazy relationship outside transaction
**Fix**: Ensure method is marked with `@Transactional`

### Issue: Still getting column limit error
**Cause**: OneToMany relationships also eagerly loading
**Solution**: Add `fetch = FetchType.LAZY` to `@OneToMany` annotations

### Issue: Poor performance with N+1 queries
**Cause**: Fetching list of Assets and accessing `.getCategory()` on each
**Solution**: Use repository query with `JOIN FETCH` for that specific scenario

## Files Modified
```
✓ Asset.java
✓ Category.java
✓ Department.java
✓ User.java
✓ Location.java
✓ PurchaseOrder.java
✓ DepreciationPolicy.java
✓ Supplier.java
✓ Role.java
```

## Next Compile
```bash
./mvnw clean compile
```

Expected: Should pass compilation (pre-existing errors in AuthController are unrelated)

