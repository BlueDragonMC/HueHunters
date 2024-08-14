FROM gradle:8-jdk21 AS builder

WORKDIR /work
COPY . ./
RUN --mount=type=cache,target=/root/.m2 --mount=type=cache,target=/root/.gradle ./gradlew build

FROM eclipse-temurin:21-jre-ubi9-minimal

WORKDIR /run/server
COPY ./worlds /run/server/worlds
COPY --from=builder /work/build/libs/HueHunters-1.0-SNAPSHOT-all.jar /run/server/server.jar

CMD ["java", "-jar", "server.jar"]
