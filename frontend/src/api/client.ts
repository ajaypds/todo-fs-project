import axios from "axios";
import { authStorage } from "../features/auth/authStorage";

export const apiClient = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL,
});
const refreshClient = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL,
});

apiClient.interceptors.request.use((config) => {
    const token = authStorage.getAccessToken();

    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
});

apiClient.interceptors.response.use(

    (response) => response,

    async (error) => {

        const originalRequest = error.config;

        if ((error.response?.status === 401 || error.response?.status === 403) && !originalRequest._retry) {

            originalRequest._retry = true;

            try {

                // const refreshToken = localStorage.getItem("refreshToken");
                const refreshToken = authStorage.getRefreshToken();

                const response = await refreshClient.post("/auth/refresh", { refreshToken });

                const newToken = response.data.token;

                // localStorage.setItem("token", newToken);
                authStorage.setAccessToken(newToken);

                originalRequest.headers.Authorization = `Bearer ${newToken}`;

                return apiClient(originalRequest);

            } catch {

                // localStorage.removeItem("token");
                // localStorage.removeItem("refreshToken");
                authStorage.removeAccessToken();
                authStorage.removeRefreshToken();

                window.location.href = "/login";
            }
        }

        return Promise.reject(error);
    }
);