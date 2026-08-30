# ===== 后端镜像：多阶段构建 =====
# 阶段1：Maven 编译
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# 先拷贝 pom + 镜像配置，利用层缓存
COPY pom.xml .
COPY docker-settings.xml /usr/share/maven/conf/docker-settings.xml
RUN mvn -s /usr/share/maven/conf/docker-settings.xml -q dependency:go-offline || true
# 再拷贝源码编译
COPY src ./src
RUN mvn -s /usr/share/maven/conf/docker-settings.xml -q clean package -DskipTests

# 阶段2：精简 JRE 运行
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/competition-platform-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
