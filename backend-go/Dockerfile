# syntax=docker/dockerfile:1
FROM golang:1.25-alpine AS builder
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
# Static, stripped binary: no libc needed at runtime, starts in a few milliseconds.
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/server ./cmd/server

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=builder /out/server /server
# Cloud Run injects PORT (default 8080); every other setting is the same environment variable the
# Spring Boot backend uses: DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET, GEMINI_API_KEY, MAIL_*.
EXPOSE 8080
ENTRYPOINT ["/server"]
