# Image statique de la PWA Librarius (build pnpm → nginx).
# Contexte de build attendu : racine du repo (workspace pnpm).
FROM node:26-alpine AS build
# Node 26 no longer bundles Corepack, so it is installed before being enabled. Corepack
# stays the way pnpm is provisioned: the version comes from `packageManager` in the root
# package.json, which keeps this image and a local checkout on the same pnpm.
RUN npm install -g corepack@latest && corepack enable
WORKDIR /repo
COPY pnpm-workspace.yaml package.json pnpm-lock.yaml ./
COPY apps/web/package.json apps/web/package.json
RUN pnpm install --frozen-lockfile
COPY apps/web apps/web
# Surcharge possible de la config OIDC au build (domaine public Keycloak).
ARG VITE_OIDC_AUTHORITY=""
ARG VITE_OIDC_CLIENT_ID=""
ENV VITE_OIDC_AUTHORITY=${VITE_OIDC_AUTHORITY}
ENV VITE_OIDC_CLIENT_ID=${VITE_OIDC_CLIENT_ID}
# Version stamped into the bundle and shown on the Settings screen: the semantic version
# on a released image, the commit on a staging one. Empty builds fall back to "dev".
ARG VITE_APP_VERSION=""
ENV VITE_APP_VERSION=${VITE_APP_VERSION}
RUN pnpm --filter @librarius/web build

FROM nginx:1.31-alpine
COPY apps/web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /repo/apps/web/dist /usr/share/nginx/html
EXPOSE 80
