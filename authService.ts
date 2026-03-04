// authService.ts - TypeScript Example

import axios, { AxiosInstance, AxiosError } from 'axios';

interface LoginRequest {
  email: string;
  password: string;
}

interface LoginResponse {
  token: string;
  user: {
    id: string;
    email: string;
    firstName: string;
    lastName: string;
    role: string;
  };
  expiresIn: number;
}

interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  password: string;
  jobTitle?: string;
  organisationId: string;
  roleId?: string;
}

class AuthService {
  private api: AxiosInstance;
  private readonly API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080/api';

  constructor() {
    this.api = axios.create({
      baseURL: this.API_BASE_URL,
      headers: {
        'Content-Type': 'application/json',
        'Accept-Encoding': 'identity', // Disable compression to avoid encoding errors
      },
      // Important: Allow credentials for CORS
      withCredentials: false, // Set to true if using httpOnly cookies
    });

    // Add token to request if available
    this.api.interceptors.request.use((config) => {
      const token = localStorage.getItem('token');
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
      return config;
    });

    // Handle token expiration
    this.api.interceptors.response.use(
      (response) => response,
      async (error: AxiosError) => {
        if (error.response?.status === 401) {
          // Token expired - try to refresh
          const refreshed = await this.refreshToken();
          if (refreshed) {
            // Retry original request
            return this.api(error.config!);
          } else {
            // Refresh failed - redirect to login
            this.logout();
            window.location.href = '/login';
          }
        }
        return Promise.reject(error);
      }
    );
  }

  async login(email: string, password: string): Promise<LoginResponse> {
    try {
      console.log('Attempting login with email:', email);

      const response = await this.api.post<LoginResponse>('/v1/auth/login', {
        email,
        password,
      });

      console.log('Login successful');

      // Store token
      localStorage.setItem('token', response.data.token);
      localStorage.setItem('user', JSON.stringify(response.data.user));
      localStorage.setItem('expiresIn', response.data.expiresIn.toString());

      // Set token expiration timer
      this.setTokenExpirationTimer(response.data.expiresIn);

      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error)) {
        console.error('Login error:', error.response?.status, error.response?.data);
        throw new Error(error.response?.data?.error || 'Login failed');
      }
      throw error;
    }
  }

  async register(data: RegisterRequest): Promise<{ id: string }> {
    try {
      const response = await this.api.post('/v1/auth/register', data);
      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error)) {
        throw new Error(error.response?.data?.error || 'Registration failed');
      }
      throw error;
    }
  }

  async refreshToken(): Promise<boolean> {
    try {
      const token = localStorage.getItem('token');
      if (!token) return false;

      const response = await this.api.post<LoginResponse>('/v1/auth/refresh', {}, {
        headers: { Authorization: `Bearer ${token}` },
      });

      localStorage.setItem('token', response.data.token);
      this.setTokenExpirationTimer(response.data.expiresIn);
      return true;
    } catch (error) {
      console.error('Token refresh failed:', error);
      return false;
    }
  }

  async getProfile() {
    try {
      const response = await this.api.get('/v1/auth/profile');
      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error)) {
        throw new Error(error.response?.data?.error || 'Failed to fetch profile');
      }
      throw error;
    }
  }

  async logout(): Promise<void> {
    try {
      const token = localStorage.getItem('token');
      if (token) {
        await this.api.post('/v1/auth/logout', {}, {
          headers: { Authorization: `Bearer ${token}` },
        });
      }
    } catch (error) {
      console.error('Logout error:', error);
    } finally {
      // Clear local storage
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      localStorage.removeItem('expiresIn');
    }
  }

  isAuthenticated(): boolean {
    return !!localStorage.getItem('token');
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  getUser() {
    const user = localStorage.getItem('user');
    return user ? JSON.parse(user) : null;
  }

  private setTokenExpirationTimer(expiresIn: number) {
    // Refresh token 5 minutes before expiration
    const refreshTime = (expiresIn - 300) * 1000;
    setTimeout(() => {
      this.refreshToken();
    }, refreshTime);
  }
}

export default new AuthService();

