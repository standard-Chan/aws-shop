FROM eclipse-temurin:21-jdk

WORKDIR /workspace

COPY gradlew build.gradle ./
COPY gradle ./gradle
COPY event-pipeline/common ./event-pipeline/common
COPY event-pipeline/product-ranking ./event-pipeline/product-ranking

RUN printf "%s\n" \
    "rootProject.name = 'aws-shop-product-ranking-load-test'" \
    "include 'event-pipeline:common'" \
    "include 'event-pipeline:product-ranking'" \
    > settings.gradle

RUN chmod +x ./gradlew \
    && ./gradlew :event-pipeline:product-ranking:classes --no-daemon

ENV EVENT_PRODUCT_RANKING_PORT=18083

EXPOSE 18083

ENTRYPOINT ["./gradlew", ":event-pipeline:product-ranking:bootRun", "--no-daemon"]
