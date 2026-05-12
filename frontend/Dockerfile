# syntax=docker/dockerfile:1.7

FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine AS runtime
COPY --from=build /app/dist/coachsim/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
# Use 127.0.0.1 explicitly: Alpine's musl resolver returns ::1 first for
# "localhost", but nginx only listens on the IPv4 socket (see nginx.conf),
# so a "localhost" probe would falsely report the container unhealthy.
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1/ > /dev/null || exit 1
EXPOSE 80
