FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY eval-common/pom.xml eval-common/
COPY eval-domain/pom.xml eval-domain/
COPY eval-infrastructure/pom.xml eval-infrastructure/
COPY eval-application/pom.xml eval-application/
COPY eval-api/pom.xml eval-api/
COPY eval-boot/pom.xml eval-boot/
RUN mvn dependency:go-offline -q
COPY . .
RUN mvn package -DskipTests -q

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/eval-boot/target/eval-boot-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
