# ---------- No.001 変換ツール（別リポジトリ）を /tools 用に書き出す ----------
# 変換ツールのソースは tool1102-source/spring-date-converter にある。
# GitHub から最新の中身を取ってきて、web100tsukuru.com/tools/ に置ける形に書き出す。
# （中身が変わっていれば、ここから下は作り直される）
FROM node:22-alpine AS tools
WORKDIR /src
ADD https://github.com/tool1102-source/spring-date-converter/archive/refs/heads/main.tar.gz tools.tar.gz
RUN tar xzf tools.tar.gz --strip-components=1 && BASE_PATH=/tools node scripts/build.mjs

# ---------- ビルド用 ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# 先に依存ライブラリだけ取得（pom.xml が変わらなければ次回から使い回される）
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# ソースと変換ツールをコピーして jar を作る
COPY src src
COPY --from=tools /src/dist src/main/resources/static/tools
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
