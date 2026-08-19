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
# nginx.conf.template lives in /etc/nginx/templates/ so the official image's
# docker-entrypoint envsubst step renders it to conf.d/default.conf at container
# *startup*, substituting API_ORIGIN into the CSP connect-src directive from whatever
# the container's actual runtime environment is — this is what lets one built image
# serve both production (default below) and a local docker-compose backend (compose.yaml
# overrides API_ORIGIN) without a rebuild. Must match VITE_API_BASE_URL's origin, or
# every browser API call is silently blocked by CSP with no server-side log line.
ENV API_ORIGIN=https://api.rally26.com
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
EXPOSE 80
