# syntax=docker/dockerfile:1

FROM node:22-alpine AS build
WORKDIR /app
# Vite bakes VITE_API_BASE_URL into the built JS at build time (import.meta.env), not
# read at container-run time — so it must be a build ARG, not a run-time environment
# variable like the backend's config. Defaults to local dev's value so an unparameterized
# `docker build` still works; CI passes --build-arg VITE_API_BASE_URL=https://api.rally26.com/api/v1
# for staging/prod images.
ARG VITE_API_BASE_URL=http://localhost:8080/api/v1
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
# New Relic Browser/RUM agent (Workstream C, 2026-08-12) — same build-time-bake
# reasoning as VITE_API_BASE_URL above. Blank by default: main.tsx's bootstrap
# no-ops cleanly when either is unset (see monitoring/newRelicBrowser.ts), same
# blank-means-unconfigured convention as every other provider key in this codebase.
# applicationID has no value yet — it only exists once a Browser App is created in
# the New Relic UI (New Relic > Browser > Add data); the founder hasn't done that yet.
ARG VITE_NEW_RELIC_BROWSER_LICENSE_KEY=""
ARG VITE_NEW_RELIC_APP_ID=""
ENV VITE_NEW_RELIC_BROWSER_LICENSE_KEY=$VITE_NEW_RELIC_BROWSER_LICENSE_KEY
ENV VITE_NEW_RELIC_APP_ID=$VITE_NEW_RELIC_APP_ID
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
