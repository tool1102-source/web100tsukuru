# ---------- ビルド用 ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# 先に依存ライブラリだけ取得（pom.xml が変わらなければ次回から使い回される）
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# ソースをコピーして jar を作る
COPY src src
RUN ./mvnw -B -q package -DskipTests

# ---------- 実行用（JDK ではなく軽い JRE だけ） ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --no-create-home app
COPY --from=build /app/target/web100tsukuru-0.0.1-SNAPSHOT.jar app.jar
USER app

EXPOSE 8080
# 無料プランの小さなサーバー向け：メモリの使い方を抑え、起動を速くする
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-Xss512k", "-jar", "app.jar"]
