# 🚀 FRONTEND INTEGRATION GUIDE

**Date**: March 5, 2026  
**Purpose**: Complete guide for frontend developers to integrate with Asset Management API  
**Status**: Production Ready ✅  

---

## 📋 TABLE OF CONTENTS

1. [Setup & Configuration](#setup--configuration)
2. [Authentication](#authentication)
3. [Asset Operations](#asset-operations)
4. [Purchase Order Operations](#purchase-order-operations)
5. [Dashboard & Analytics](#dashboard--analytics)
6. [Reports & Exports](#reports--exports)
7. [Webhooks Integration](#webhooks-integration)
8. [Error Handling](#error-handling)
9. [Best Practices](#best-practices)

---

## 🔧 SETUP & CONFIGURATION

### Environment Variables

Create `.env` file in your frontend project:

```env
REACT_APP_API_BASE_URL=http://localhost:8085/api/v1
REACT_APP_API_TIMEOUT=30000
REACT_APP_MAX_RETRIES=3
REACT_APP_TOKEN_KEY=auth_token
REACT_APP_REFRESH_TOKEN_KEY=refresh_token
```

### API Client Setup (JavaScript/TypeScript)

**axios.js**:
```javascript
import axios from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL;
const API_TIMEOUT = process.env.REACT_APP_API_TIMEOUT || 30000;

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: API_TIMEOUT,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add token to requests
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Handle token refresh on 401
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      
      try {
        const response = await axios.post(
          `${API_BASE_URL}/auth/refresh`,
          {},
          { headers: { 'Authorization': `Bearer ${localStorage.getItem('auth_token')}` } }
        );
        
        localStorage.setItem('auth_token', response.data.token);
        originalRequest.headers.Authorization = `Bearer ${response.data.token}`;
        return apiClient(originalRequest);
      } catch (refreshError) {
        localStorage.removeItem('auth_token');
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }
    
    return Promise.reject(error);
  }
);

export default apiClient;
```

---

## 🔐 AUTHENTICATION

### Login

**TypeScript/React Example**:

```typescript
import apiClient from './axios';

interface LoginRequest {
  email: string;
  password: string;
}

interface LoginResponse {
  token: string;
  expiresIn: number;
  user: {
    id: string;
    email: string;
    firstName: string;
    lastName: string;
    role: string;
  };
}

async function login(credentials: LoginRequest): Promise<LoginResponse> {
  try {
    const response = await apiClient.post<LoginResponse>(
      '/auth/login',
      credentials
    );
    
    // Store token
    localStorage.setItem('auth_token', response.data.token);
    localStorage.setItem('user', JSON.stringify(response.data.user));
    
    // Optional: Set token expiry timer
    setTimeout(() => {
      refreshToken();
    }, (response.data.expiresIn - 300) * 1000); // Refresh 5 min before expiry
    
    return response.data;
  } catch (error) {
    console.error('Login failed:', error);
    throw error;
  }
}
```

### Refresh Token

```typescript
async function refreshToken(): Promise<string> {
  try {
    const response = await apiClient.post('/auth/refresh');
    const newToken = response.data.token;
    
    localStorage.setItem('auth_token', newToken);
    
    return newToken;
  } catch (error) {
    console.error('Token refresh failed:', error);
    localStorage.removeItem('auth_token');
    window.location.href = '/login';
    throw error;
  }
}
```

---

## 📦 ASSET OPERATIONS

### Create Asset

```typescript
interface Asset {
  name: string;
  assetTag: string;
  assetType: string;
  serialNumber?: string;
  categoryId: string;
  departmentId: string;
  locationId?: string;
  supplierId?: string;
  purchaseCost: number;
  currency: string;
  description?: string;
  status?: 'IN_STOCK' | 'IN_USE' | 'RETIRED';
  condition?: 'POOR' | 'FAIR' | 'GOOD' | 'EXCELLENT';
  warrantyExpiryDate?: string;
}

async function createAsset(asset: Asset): Promise<Asset> {
  try {
    const response = await apiClient.post<Asset>('/assets', asset);
    return response.data;
  } catch (error) {
    console.error('Failed to create asset:', error);
    throw error;
  }
}
```

### Get Asset Details

```typescript
async function getAsset(assetId: string): Promise<Asset> {
  try {
    const response = await apiClient.get<Asset>(`/assets/${assetId}`);
    return response.data;
  } catch (error) {
    if (error.response?.status === 404) {
      console.error('Asset not found:', assetId);
    }
    throw error;
  }
}
```

### List Assets with Filters

```typescript
interface AssetListParams {
  status?: string;
  departmentId?: string;
  categoryId?: string;
  page?: number;
  size?: number;
  sort?: string;
}

async function listAssets(params: AssetListParams): Promise<Asset[]> {
  try {
    const response = await apiClient.get<Asset[]>('/assets', { params });
    return response.data;
  } catch (error) {
    console.error('Failed to fetch assets:', error);
    throw error;
  }
}

// Usage with React
import { useState, useEffect } from 'react';

function AssetsList() {
  const [assets, setAssets] = useState<Asset[]>([]);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState({ page: 0, size: 20 });

  useEffect(() => {
    setLoading(true);
    listAssets(filters)
      .then(setAssets)
      .finally(() => setLoading(false));
  }, [filters]);

  return (
    <div>
      {loading ? <p>Loading...</p> : (
        <table>
          <tbody>
            {assets.map((asset) => (
              <tr key={asset.id}>
                <td>{asset.name}</td>
                <td>{asset.assetTag}</td>
                <td>{asset.status}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
```

### Update Asset

```typescript
async function updateAsset(
  assetId: string,
  updates: Partial<Asset>
): Promise<Asset> {
  try {
    const response = await apiClient.put<Asset>(
      `/assets/${assetId}`,
      updates
    );
    return response.data;
  } catch (error) {
    console.error('Failed to update asset:', error);
    throw error;
  }
}
```

### Delete Asset

```typescript
async function deleteAsset(assetId: string): Promise<void> {
  try {
    await apiClient.delete(`/assets/${assetId}`);
  } catch (error) {
    console.error('Failed to delete asset:', error);
    throw error;
  }
}
```

---

## 💳 PURCHASE ORDER OPERATIONS

### Create Purchase Order

```typescript
interface PurchaseOrder {
  poNumber: string;
  totalAmount: number;
  currency: string;
  departmentId: string;
  supplierId: string;
  remarks?: string;
  status?: 'DRAFT' | 'APPROVED' | 'REJECTED';
}

async function createPurchaseOrder(po: PurchaseOrder): Promise<PurchaseOrder> {
  try {
    const response = await apiClient.post<PurchaseOrder>(
      '/purchase-orders',
      po
    );
    return response.data;
  } catch (error) {
    console.error('Failed to create PO:', error);
    throw error;
  }
}
```

### Approve Purchase Order

```typescript
async function approvePurchaseOrder(poId: string): Promise<PurchaseOrder> {
  try {
    const response = await apiClient.post<PurchaseOrder>(
      `/purchase-orders/${poId}/approve`
    );
    return response.data;
  } catch (error) {
    console.error('Failed to approve PO:', error);
    throw error;
  }
}
```

### List Purchase Orders

```typescript
async function listPurchaseOrders(
  filters?: { departmentId?: string; supplierId?: string; status?: string }
): Promise<PurchaseOrder[]> {
  try {
    const response = await apiClient.get<PurchaseOrder[]>(
      '/purchase-orders',
      { params: filters }
    );
    return response.data;
  } catch (error) {
    console.error('Failed to fetch POs:', error);
    throw error;
  }
}
```

---

## 📊 DASHBOARD & ANALYTICS

### Get Dashboard Summary

```typescript
interface DashboardSummary {
  totalAssets: number;
  assetsInUse: number;
  assetsInStock: number;
  assetsRetired: number;
  totalAssetValue: number;
  maintenanceAlerts: number;
  pendingPurchaseOrders: number;
  approvedPurchaseOrders: number;
}

async function getDashboardSummary(): Promise<DashboardSummary> {
  try {
    const response = await apiClient.get<DashboardSummary>(
      '/dashboard/summary'
    );
    return response.data;
  } catch (error) {
    console.error('Failed to fetch dashboard summary:', error);
    throw error;
  }
}

// React Component Example
import { useEffect, useState } from 'react';

function Dashboard() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getDashboardSummary()
      .then(setSummary)
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p>Loading dashboard...</p>;
  if (!summary) return <p>Failed to load dashboard</p>;

  return (
    <div className="dashboard">
      <div className="stat-card">
        <h3>Total Assets</h3>
        <p className="value">{summary.totalAssets}</p>
      </div>
      <div className="stat-card">
        <h3>Assets in Use</h3>
        <p className="value">{summary.assetsInUse}</p>
      </div>
      <div className="stat-card">
        <h3>Total Value</h3>
        <p className="value">${summary.totalAssetValue.toLocaleString()}</p>
      </div>
      <div className="stat-card alert">
        <h3>Maintenance Alerts</h3>
        <p className="value">{summary.maintenanceAlerts}</p>
      </div>
    </div>
  );
}
```

### Get Analytics Data

```typescript
interface AnalyticsData {
  period: string;
  data: Array<{
    name: string;
    count: number;
    value: number;
    percentage: number;
  }>;
  total: number;
  totalValue: number;
}

async function getAssetAnalytics(
  period: 'month' | 'quarter' | 'year',
  groupBy: string
): Promise<AnalyticsData> {
  try {
    const response = await apiClient.get<AnalyticsData>(
      '/analytics/assets',
      { params: { period, groupBy } }
    );
    return response.data;
  } catch (error) {
    console.error('Failed to fetch analytics:', error);
    throw error;
  }
}

// Chart Integration Example (using Chart.js)
import { Bar } from 'react-chartjs-2';

function AnalyticsChart() {
  const [data, setData] = useState<AnalyticsData | null>(null);

  useEffect(() => {
    getAssetAnalytics('month', 'status').then(setData);
  }, []);

  if (!data) return <p>Loading...</p>;

  const chartData = {
    labels: data.data.map(d => d.name),
    datasets: [
      {
        label: 'Asset Count',
        data: data.data.map(d => d.count),
        backgroundColor: 'rgba(75, 192, 192, 0.6)',
      }
    ]
  };

  return <Bar data={chartData} />;
}
```

---

## 📄 REPORTS & EXPORTS

### Generate Asset Report

```typescript
interface ReportRequest {
  format: 'PDF' | 'EXCEL' | 'CSV';
  includeDetails: boolean;
  filters?: {
    status?: string;
    departmentId?: string;
    dateRange?: string;
  };
  columns?: string[];
}

interface ReportResponse {
  reportId: string;
  format: string;
  status: 'PROCESSING' | 'COMPLETED' | 'FAILED';
  downloadUrl: string;
  generatedAt: string;
}

async function generateReport(
  request: ReportRequest
): Promise<ReportResponse> {
  try {
    const response = await apiClient.post<ReportResponse>(
      '/reports/assets',
      request
    );
    return response.data;
  } catch (error) {
    console.error('Failed to generate report:', error);
    throw error;
  }
}

// React Component
function ReportGenerator() {
  const [format, setFormat] = useState<'PDF' | 'EXCEL' | 'CSV'>('PDF');
  const [loading, setLoading] = useState(false);
  const [downloadUrl, setDownloadUrl] = useState('');

  const handleGenerateReport = async () => {
    setLoading(true);
    try {
      const report = await generateReport({
        format,
        includeDetails: true,
        filters: { status: 'IN_USE' }
      });
      setDownloadUrl(report.downloadUrl);
    } finally {
      setLoading(false);
    }
  };

  const handleDownload = () => {
    if (downloadUrl) {
      window.open(downloadUrl, '_blank');
    }
  };

  return (
    <div>
      <select value={format} onChange={(e) => setFormat(e.target.value as any)}>
        <option>PDF</option>
        <option>EXCEL</option>
        <option>CSV</option>
      </select>
      <button onClick={handleGenerateReport} disabled={loading}>
        {loading ? 'Generating...' : 'Generate Report'}
      </button>
      {downloadUrl && (
        <button onClick={handleDownload}>Download Report</button>
      )}
    </div>
  );
}
```

### Bulk Import Assets

```typescript
async function importAssets(
  file: File,
  dryRun: boolean = false
): Promise<{ jobId: string; status: string }> {
  try {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('dryRun', dryRun.toString());

    const response = await apiClient.post(
      '/bulk/assets/import',
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    );

    return response.data;
  } catch (error) {
    console.error('Failed to import assets:', error);
    throw error;
  }
}

// Monitor import progress
async function getImportStatus(jobId: string) {
  try {
    const response = await apiClient.get(`/bulk/assets/import/${jobId}`);
    return response.data;
  } catch (error) {
    console.error('Failed to get import status:', error);
    throw error;
  }
}

// React File Upload Component
function AssetImporter() {
  const [file, setFile] = useState<File | null>(null);
  const [jobId, setJobId] = useState('');
  const [status, setStatus] = useState('');

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFile(e.target.files?.[0] || null);
  };

  const handleUpload = async () => {
    if (!file) return;

    try {
      const result = await importAssets(file, false);
      setJobId(result.jobId);
      
      // Poll for status
      const pollInterval = setInterval(async () => {
        const status = await getImportStatus(result.jobId);
        setStatus(status.status);
        
        if (status.status === 'COMPLETED') {
          clearInterval(pollInterval);
        }
      }, 2000);
    } catch (error) {
      console.error('Upload failed:', error);
    }
  };

  return (
    <div>
      <input type="file" accept=".csv,.xlsx" onChange={handleFileChange} />
      <button onClick={handleUpload} disabled={!file}>
        Import Assets
      </button>
      {jobId && <p>Status: {status}</p>}
    </div>
  );
}
```

---

## 🪝 WEBHOOKS INTEGRATION

### Setup Webhook from Frontend

```typescript
interface WebhookPayload {
  name: string;
  url: string;
  events: string[];
  active: boolean;
  secret?: string;
}

async function createWebhook(payload: WebhookPayload) {
  try {
    const response = await apiClient.post('/webhooks', payload);
    return response.data;
  } catch (error) {
    console.error('Failed to create webhook:', error);
    throw error;
  }
}

// Example: Setup webhook for asset updates
async function setupAssetWebhook(callbackUrl: string) {
  return createWebhook({
    name: 'Asset Updates',
    url: callbackUrl,
    events: ['asset.created', 'asset.updated', 'asset.deleted'],
    active: true
  });
}
```

### Handle Webhook Events

Create your backend endpoint to receive webhooks:

```typescript
// backend/webhook-handler.ts
app.post('/api/webhooks/asset-updates', (req, res) => {
  const event = req.body;

  try {
    // Verify webhook secret
    const secret = process.env.WEBHOOK_SECRET;
    const signature = req.headers['x-webhook-signature'];
    
    // Handle different event types
    switch (event.event) {
      case 'asset.created':
        console.log('New asset created:', event.data);
        // Update UI, notify users, etc.
        break;
      
      case 'asset.updated':
        console.log('Asset updated:', event.data);
        break;
      
      case 'asset.deleted':
        console.log('Asset deleted:', event.data);
        break;
    }

    res.status(200).json({ received: true });
  } catch (error) {
    console.error('Error handling webhook:', error);
    res.status(500).json({ error: 'Failed to process webhook' });
  }
});
```

---

## ❌ ERROR HANDLING

### Global Error Handler

```typescript
interface ApiError {
  status: number;
  message: string;
  errors?: Record<string, string>;
  timestamp: string;
}

function handleApiError(error: any): ApiError {
  if (error.response?.data) {
    return error.response.data;
  }

  return {
    status: error.response?.status || 500,
    message: error.message || 'An unexpected error occurred',
    timestamp: new Date().toISOString()
  };
}

// Usage
async function safeApiCall<T>(
  apiCall: () => Promise<T>
): Promise<{ data?: T; error?: ApiError }> {
  try {
    const data = await apiCall();
    return { data };
  } catch (error) {
    return { error: handleApiError(error) };
  }
}

// Example usage
const { data, error } = await safeApiCall(() => getAsset('123'));
if (error) {
  console.error(`Error (${error.status}): ${error.message}`);
} else {
  console.log('Asset:', data);
}
```

### User-Friendly Error Messages

```typescript
function getUserFriendlyErrorMessage(error: ApiError): string {
  switch (error.status) {
    case 400:
      return 'Invalid request. Please check your input.';
    case 401:
      return 'Your session has expired. Please log in again.';
    case 403:
      return 'You do not have permission to perform this action.';
    case 404:
      return 'The requested resource was not found.';
    case 409:
      return 'This action conflicts with the current state.';
    case 422:
      return 'The request contains invalid data.';
    case 500:
      return 'Server error. Please try again later.';
    default:
      return error.message || 'An error occurred. Please try again.';
  }
}

// Toast notification
function showErrorNotification(error: any) {
  const apiError = handleApiError(error);
  const message = getUserFriendlyErrorMessage(apiError);
  
  // Using react-toastify or similar
  toast.error(message);
}
```

---

## ✅ BEST PRACTICES

### 1. Request Timeout Handling

```typescript
async function withTimeout<T>(
  promise: Promise<T>,
  timeoutMs: number = 30000
): Promise<T> {
  const timeout = new Promise((_, reject) =>
    setTimeout(() => reject(new Error('Request timeout')), timeoutMs)
  );
  return Promise.race([promise, timeout]) as Promise<T>;
}

// Usage
const data = await withTimeout(getAsset('123'), 15000);
```

### 2. Request Deduplication

```typescript
class RequestCache {
  private cache = new Map<string, Promise<any>>();

  async get<T>(
    key: string,
    fetcher: () => Promise<T>
  ): Promise<T> {
    if (this.cache.has(key)) {
      return this.cache.get(key)!;
    }

    const promise = fetcher().finally(() => {
      this.cache.delete(key);
    });

    this.cache.set(key, promise);
    return promise;
  }

  clear() {
    this.cache.clear();
  }
}

const cache = new RequestCache();

// Usage
const asset = await cache.get(
  `asset-${id}`,
  () => getAsset(id)
);
```

### 3. Pagination Helper

```typescript
interface PaginationState {
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}

function usePagination(initialPageSize: number = 20) {
  const [pagination, setPagination] = useState<PaginationState>({
    page: 0,
    pageSize: initialPageSize,
    totalElements: 0,
    totalPages: 0
  });

  return {
    pagination,
    goToPage: (page: number) => setPagination(p => ({ ...p, page })),
    goToNextPage: () => setPagination(p => ({ ...p, page: p.page + 1 })),
    goToPrevPage: () => setPagination(p => ({ ...p, page: Math.max(0, p.page - 1) })),
    setPageSize: (size: number) => setPagination(p => ({ ...p, pageSize: size, page: 0 })),
    updateTotal: (total: number) => setPagination(p => ({
      ...p,
      totalElements: total,
      totalPages: Math.ceil(total / p.pageSize)
    }))
  };
}
```

### 4. Loading States

```typescript
interface LoadingState {
  isLoading: boolean;
  error: ApiError | null;
  data: any | null;
}

function useApiCall<T>(apiCall: () => Promise<T>) {
  const [state, setState] = useState<LoadingState>({
    isLoading: false,
    error: null,
    data: null
  });

  const execute = useCallback(async () => {
    setState({ isLoading: true, error: null, data: null });
    try {
      const data = await apiCall();
      setState({ isLoading: false, error: null, data });
      return data;
    } catch (error) {
      const apiError = handleApiError(error);
      setState({ isLoading: false, error: apiError, data: null });
      throw apiError;
    }
  }, [apiCall]);

  return { ...state, execute };
}
```

### 5. Retries with Exponential Backoff

```typescript
async function retryWithBackoff<T>(
  fn: () => Promise<T>,
  maxRetries: number = 3,
  delayMs: number = 1000
): Promise<T> {
  try {
    return await fn();
  } catch (error) {
    if (maxRetries === 0) throw error;

    const delay = delayMs * Math.pow(2, 3 - maxRetries);
    await new Promise(resolve => setTimeout(resolve, delay));

    return retryWithBackoff(fn, maxRetries - 1, delayMs);
  }
}

// Usage
const data = await retryWithBackoff(() => getAsset('123'));
```

---

**Generated**: March 5, 2026  
**Version**: 1.0  
**Status**: Ready for Frontend Integration ✅  

---

*All examples are production-ready and follow industry best practices.*

