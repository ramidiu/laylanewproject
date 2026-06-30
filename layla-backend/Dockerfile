FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# OCR toolchain for the KYC verification workspace:
#   tesseract-ocr (+ English data) reads text from ID images;
#   poppler-utils provides pdftoppm to rasterize PDF pages before OCR.
RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-eng tesseract-ocr-data-osd poppler-utils imagemagick
COPY --from=build /app/target/layla-monolith-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
