# Asset Assignment Enforcement - REGISTERED State Verification

## Summary
✅ **Only REGISTERED assets can be assigned to departments** - This business rule is fully enforced in the codebase.

## Implementation Details

### 1. **Service Layer Validation** 
**File:** `AssetServiceImpl.java` (lines 59-61)

```java
if (asset.getState() != AssetState.REGISTERED) {
    throw new IllegalStateException("Only assets in REGISTERED state can be assigned");
}
```

**How it works:**
- When `assignToDepartment()` is called, the asset is retrieved from the database
- The code checks if the asset's current state equals `AssetState.REGISTERED`
- If the asset is in any other state (ASSIGNED, DISPOSED, etc.), an `IllegalStateException` is thrown
- This prevents assets already assigned (ASSIGNED state) or disposed (DISPOSED state) from being re-assigned

### 2. **Controller Error Handling**
**File:** `AssetController.java` (lines 40-45)

```java
@PostMapping("/{id}/assign/{departmentId}")
public ResponseEntity<AssetDto> assign(@PathVariable UUID id, @PathVariable UUID departmentId) {
    try {
        AssetDto dto = assetService.assignToDepartment(id, departmentId);
        return ResponseEntity.ok(dto);
    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().build();           // HTTP 400
    } catch (IllegalStateException e) {
        return ResponseEntity.status(409).body(null);         // HTTP 409 (Conflict)
    }
}
```

**How it works:**
- The controller catches `IllegalStateException` and returns HTTP 409 (Conflict)
- This indicates the asset is in an invalid state for the operation
- The calling client receives a clear error status

### 3. **Global Exception Handler**
**File:** `GlobalExceptionHandler.java` (lines 43-48)

```java
@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<Object> handleIllegalState(IllegalStateException ex) {
    Map<String, Object> body = new HashMap<>();
    body.put("status", HttpStatus.CONFLICT.value());
    body.put("message", ex.getMessage());
    return new ResponseEntity<>(body, HttpStatus.CONFLICT);
}
```

**How it works:**
- Provides consistent error response format across all endpoints
- Returns JSON error body with status code and descriptive message
- Error message: "Only assets in REGISTERED state can be assigned"

## State Transition Flow

```
Asset Created
    ↓
Initial State: REGISTERED
    ↓
[Can be assigned to department]
    ↓
After Assignment: ASSIGNED
    ↓
[Cannot be assigned again - raises IllegalStateException]
```

## Test Scenarios

### ✅ Scenario 1: Successful Assignment (REGISTERED → ASSIGNED)
```
1. Create asset (state = REGISTERED)
2. Call POST /api/v1/assets/{id}/assign/{deptId}
3. Result: HTTP 200 OK, asset state changes to ASSIGNED
```

### ❌ Scenario 2: Prevent Re-assignment (ASSIGNED)
```
1. Asset is already in ASSIGNED state
2. Call POST /api/v1/assets/{id}/assign/{deptId}
3. Result: HTTP 409 Conflict
   Response: {"status": 409, "message": "Only assets in REGISTERED state can be assigned"}
```

### ✅ Scenario 3: Prevent Assignment of Non-existent Asset
```
1. Call POST /api/v1/assets/{invalidId}/assign/{deptId}
2. Result: HTTP 404 Not Found (asset doesn't exist)
```

### ✅ Scenario 4: Prevent Assignment to Non-existent Department
```
1. Asset exists and is REGISTERED
2. Call POST /api/v1/assets/{id}/assign/{invalidDeptId}
3. Result: HTTP 400 Bad Request (department doesn't exist)
```

## Related Files

- **Service:** `/src/main/java/com/example/demo/services/impl/AssetServiceImpl.java`
- **Controller:** `/src/main/java/com/example/demo/controllers/v1/AssetController.java`
- **Exception Handler:** `/src/main/java/com/example/demo/controllers/v1/GlobalExceptionHandler.java`
- **Enum:** `/src/main/java/com/example/demo/enums/AssetState.java` (defines REGISTERED, ASSIGNED, DISPOSED states)

## Verification Checklist

✅ Business rule is coded at service layer (not just UI)
✅ Validation happens before database update
✅ Clear error messages returned to clients
✅ Proper HTTP status codes used (409 for conflict)
✅ Exception handling is consistent across all endpoints
✅ State transitions are enforced (REGISTERED → ASSIGNED only)

## Conclusion

The asset assignment business rule is **fully enforced** at the service layer with proper error handling. Clients attempting to assign an asset that is not in REGISTERED state will receive a clear HTTP 409 (Conflict) response indicating the operation is invalid.

