FROM node:22-alpine AS builder
RUN apk add --no-cache git python3 py3-setuptools make g++ pkgconfig libsecret-dev
WORKDIR /app
ADD . .
RUN yarn install
RUN yarn run build

FROM node:22-alpine AS production
WORKDIR /app
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/continuum-workbench ./
EXPOSE 8080
CMD [ "lib/backend/main.js", "-h", "0.0.0.0", "-p", "8080" ]