FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /workspace

COPY pom.xml ./pom.xml
COPY sbg-marking-contracts ./sbg-marking-contracts
COPY sbg-marking-server ./sbg-marking-server

RUN mvn -B -V -ntp -pl sbg-marking-server -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=builder /workspace/sbg-marking-server/target/sbg-marking-server-1.0.0-SNAPSHOT.jar /app/sbg-marking-server.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/sbg-marking-server.jar"]
