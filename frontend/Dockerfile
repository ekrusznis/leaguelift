# syntax=docker/dockerfile:1

FROM node:22-alpine AS build
WORKDIR /app
# Vite bakes VITE_API_BASE_URL into the built JS at build time (import.meta.env), not
# read at container-run time — so it must be a build ARG, not a run-time environment
# variable like the backend's config. Defaults to local dev's value so an unparameterized
# `docker build` still works; CI passes --build-arg VITE_API_BASE_URL=https://api.leaguelift.io/api/v1
# for staging/prod images.
ARG VITE_API_BASE_URL=http://localhost:8080/api/v1
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
