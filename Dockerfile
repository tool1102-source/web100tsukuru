# ベースイメージ
FROM eclipse-temurin:17-jdk

# 作業ディレクトリ設定
WORKDIR /app

# プロジェクトファイルをコピー
COPY . .

# jarファイルをビルド
RUN ./mvnw clean package -DskipTests

# アプリを起動
CMD ["java", "-jar", "target/*.jar"]
