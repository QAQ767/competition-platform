# ===== 前端镜像：多阶段构建 =====
# 阶段1：Node 构建 dist
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json ./
RUN npm install --registry=https://registry.npmmirror.com --no-audit --no-fund
COPY . .
RUN npm run build

# 阶段2：Nginx 托管 + 反向代理
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
