FROM eclipse-temurin:17.0.3_7-jre as builder
WORKDIR /app
COPY target/ ./
RUN java -Djarmode=layertools -jar *.jar extract

FROM eclipse-temurin:17.0.3_7-jre
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=80 -XX:TieredStopAtLevel=1"
HEALTHCHECK --interval=30s --retries=3 --start-period=60s --timeout=2s CMD curl -f http://localhost:8080/actuator/health/liveness
USER 1000:1000
WORKDIR /app

# Copy artifacts
COPY --from=builder /app/container/* ./
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
RUN true  # Workaround https://github.com/moby/moby/issues/37965
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/application/ ./

ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]
