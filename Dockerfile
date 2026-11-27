# --- Build stage ---------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependencies separately from source so code changes don't re-download them.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# static/css/style.css and static/fonts/Brugty.ttf are committed, synced snapshots of
# goa_webapp's branding (see scripts/sync-styles.sh) - not fetched at build time, so a
# shop deploy never depends on heimfeld.life being reachable.
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# --- Runtime stage ---------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# PDF generation (AGB/Widerrufsbelehrung/Muster-Widerrufsformular, see LegalDocumentPdfService)
# needs at least one real font installed - Alpine ships none by default, and without one,
# PDFBox's font substitution for bullet-list glyphs has nothing to fall back to.
RUN apk add --no-cache fontconfig ttf-dejavu

RUN addgroup -S shop && adduser -S shop -G shop
COPY --from=build /build/target/goa-webapp-shop-*.jar app.jar
USER shop

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
