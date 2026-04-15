# 1. Этап сборки
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

# 2. Этап запуска
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Копируем JAR
COPY --from=build /app/target/*-with-dependencies.jar app.jar

# ВАЖНО: Воссоздаем структуру папок для WatchService
# Чтобы AppConfig мог найти и следить за файлом по пути "src/main/resources/config.yaml"
RUN mkdir -p src/main/resources
COPY src/main/resources/config.yaml src/main/resources/config.yaml

EXPOSE 8080

# Запускаем из папки /app
ENTRYPOINT ["java", "-jar", "app.jar"]
