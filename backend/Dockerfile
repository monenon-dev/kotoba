# 설계: docs/design/06-deploy.md
#
# 빌드 단계. 테스트는 CI 의 h2, postgres 작업이 맡으므로 여기서는 jar 만 만든다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
# 소스가 바뀌어도 의존성 레이어를 재사용한다.
RUN ./gradlew dependencies --no-daemon --console=plain > /dev/null
COPY src src
RUN ./gradlew bootJar --no-daemon --console=plain

# 실행 단계
FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 kotoba \
    && useradd --system --uid 10001 --gid kotoba --no-create-home --shell /usr/sbin/nologin kotoba
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar

# 프로필을 빠뜨리면 dev(H2, 개발용 JWT 키)로 뜨므로 이미지 기본값을 prod 로 둔다.
ENV SPRING_PROFILES_ACTIVE=prod
# DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET 은 실행 시 환경변수로 받는다. 이미지에 기본값을 두지 않는다.
# 힙 상한을 컨테이너 메모리의 40% 로 둔다. 힙 밖에서 약 250MiB 를 쓰므로 512MB 에서 60% 이상이면 강제 종료된다 (설계 문서 측정 결과).
# 더 큰 환경에서는 JAVA_TOOL_OPTIONS 를 실행 시 덮어써서 올린다.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=40 -XX:+ExitOnOutOfMemoryError"

USER 10001:10001
EXPOSE 8080
# start-period: 0.1 CPU 에서 기동에 약 4분까지 걸린 측정값을 기준으로 잡았다.
HEALTHCHECK --interval=15s --timeout=5s --start-period=300s --start-interval=5s --retries=3 \
    CMD curl -fsS http://localhost:8080/api/health > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
