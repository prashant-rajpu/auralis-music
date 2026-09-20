# The whole relay, on anything that runs a container.
#
# Two stages so the image ships without TypeScript or the test runner: the build stage compiles,
# the runtime stage carries node_modules and dist and nothing else.

FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json tsconfig.build.json ./
COPY src ./src
RUN npm run build

FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY --from=build /app/dist ./dist

# Rooms live here. Mount a volume on it to keep codes across a redeploy; without one the relay
# still works and a room code lasts until the next deploy.
RUN mkdir -p /data && chown node:node /data
VOLUME ["/data"]
ENV DATA_DIR=/data
ENV PORT=8787
EXPOSE 8787

USER node

# The health route is the readiness check every host below is pointed at.
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
  CMD node -e "fetch('http://127.0.0.1:'+(process.env.PORT||8787)+'/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "dist/server.js"]
