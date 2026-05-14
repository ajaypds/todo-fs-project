import axios from "axios";

export const apiClient = axios.create({
    baseURL: "http://localhost:8080/api/v1",
});
const refreshClient = axios.create({
    baseURL: "http://localhost:8080/api/v1",
});

apiClient.interceptors.request.use((config) => {
    const token = localStorage.getItem("token");

    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
});

apiClient.interceptors.response.use(

    (response) => response,

    async (error) => {

        const originalRequest = error.config;

        if (error.response?.status === 401 && !originalRequest._retry) {

            originalRequest._retry = true;

            try {

                const refreshToken = localStorage.getItem("refreshToken");

                const response = await refreshClient.post("/auth/refresh", { refreshToken });

                const newToken = response.data.token;

                localStorage.setItem("token", newToken);

                originalRequest.headers.Authorization = `Bearer ${newToken}`;

                return apiClient(originalRequest);

            } catch {

                localStorage.removeItem("token");

                localStorage.removeItem("refreshToken");

                window.location.href = "/login";
            }
        }

        return Promise.reject(error);
    }
);