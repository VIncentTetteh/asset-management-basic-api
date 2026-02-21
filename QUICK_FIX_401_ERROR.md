# ⚡ QUICK FIX - 401 Login Error

**Problem**: Frontend getting 401 when trying to login
**Solution**: CORS configuration added
**Time to Fix**: 2 minutes

---

## 🔧 What Was Done

✅ Created `CorsConfig.java` - Allows requests from localhost:3000
✅ Updated `SecurityConfig.java` - Applies CORS configuration
✅ Frontend can now make requests to backend

---

## 🚀 What You Need To Do

### Step 1: Rebuild Backend (1 minute)
```bash
cd /Users/vincenttetteh/Downloads/demo\ 2

# Option A: Maven rebuild
mvn clean package -DskipTests

# Option B: If using IDE, just rebuild/restart Spring Boot
```

### Step 2: Restart Backend (30 seconds)
```bash
# Kill old process and restart
# Or if using IDE, just restart the application
```

### Step 3: Test Login (30 seconds)

**In your frontend, make sure your authService uses:**
```typescript
const API_BASE_URL = 'http://localhost:8080/api';

const response = await axios.post(
  `${API_BASE_URL}/v1/auth/login`,
  { email, password }
);
```

**Test credentials:**
```
Email:    testuser@testcompany.com
Password: TestPassword123
```

---

## ✅ Expected Result

Login should now return:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440004",
    "email": "testuser@testcompany.com",
    "firstName": "Test",
    "lastName": "User",
    "role": "Admin"
  },
  "expiresIn": 86400
}
```

---

## 📋 Files to Use/Review

1. **`authService.ts`** - Copy this to your frontend project
2. **`CorsConfig.java`** - Already created in backend ✅
3. **`SecurityConfig.java`** - Already updated in backend ✅
4. **`FRONTEND_401_FIX.md`** - Detailed troubleshooting guide

---

## ⚠️ Common Issues

**Still getting 401?**
1. ✅ Did you rebuild the backend?
2. ✅ Did you restart the application?
3. ✅ Did you run the test user SQL script?
4. ✅ Is backend running on port 8080?
5. ✅ Is frontend running on port 3000?

**Check database has test user:**
```bash
psql -h localhost -U asset_user -d asset_management
SELECT email FROM app_user WHERE email = 'testuser@testcompany.com';
```

If not found, run:
```bash
psql -h localhost -U asset_user -d asset_management -f test-user-setup.sql
```

---

## 🎯 Step-by-Step

```
1. mvn clean package -DskipTests
         ↓
2. Restart Spring Boot application
         ↓
3. Frontend calls: POST http://localhost:8080/api/v1/auth/login
         ↓
4. Backend returns token ✅
         ↓
5. Frontend stores token & uses in Authorization header
         ↓
6. All other API calls work! ✅
```

---

**Fix Status**: ✅ Applied
**Next Step**: Rebuild backend
**Est. Time**: 2 minutes


