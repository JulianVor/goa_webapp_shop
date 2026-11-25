# goa_webapp_shop

Der öffentliche Fanshop-Storefront für `shop.heimfeld.life` - Spring Boot + Thymeleaf,
genau wie [goa_webapp](https://github.com/JulianVor/goa_webapp), aber ohne eigene
Produktdaten: die kommen vollständig über die Store-API von
[goa_webapp_shop_admin](https://github.com/JulianVor/goa_webapp_shop_admin) (ein
Medusa.js-Backend inkl. Admin-Dashboard).

## Architektur

```
                      Caddy (Reverse Proxy)
                    /                      \
        shop.heimfeld.life          shop-admin.heimfeld.life
               |                              |
      goa_webapp_shop (dieses Repo)      goa_webapp_shop_admin (Medusa)
       eigene kleine Postgres-DB          eigene Postgres-DB + Redis
       (nur ShopSettings)                 (Produkte, Varianten, Preise,
               |                           Lagerbestand, Bestellungen)
               └──── Store-API-Calls ────────────┘
```

Dieses Repo hält **keine** Produkt-, Preis- oder Bestelldaten - die liegen komplett in
Medusa und werden über das **Medusa Admin Dashboard** gepflegt (erreichbar unter
`MEDUSA_ADMIN_URL`, z.B. `shop-admin.heimfeld.life/app`). Der eigene `/admin`-Bereich
hier deckt nur ab, was Medusa nicht mitbringt:

- Impressum, AGB, Widerrufsbelehrung, Datenschutzerklärung
- Wartungsmodus-Schalter ("Fanshop kommt bald" statt Sortiment)
- Kontakt-E-Mail

## Styles von goa_webapp

`static/css/style.css` und `static/fonts/Brugty.ttf` sind **synchronisierte Kopien** aus
goa_webapp (nicht live verlinkt, siehe Konzept-Diskussion "Option B"). `style.css`
definiert dabei nur, *wie* eine Reihe CSS-Variablen (`--color-*`, `--font-*`,
`--box-radius`, `--pill-radius`, `--bg-image`) benutzt werden - die tatsächlichen *Werte*
(welche Farben, welches der beiden Font-Paare Brugty/Poppins vs. Bebas Neue/Oswald,
welches Hintergrundbild) sind bei goa_webapp selbst **pro aktueller Edition** aus der
Datenbank und werden dort serverseitig in `fragments/head.html` injiziert - die hat dieser
Shop bewusst nicht (keine DB-Kopplung an goa_webapp). Stattdessen zieht
`scripts/sync-styles.sh` sich diesen gerenderten `<style>`-Block direkt von der laufenden
Startseite und friert ihn in `static/css/edition.css` ein (inkl. des Hintergrundbilds,
falls als server-relativer Pfad referenziert):

```bash
./scripts/sync-styles.sh                                   # von https://heimfeld.life
SOURCE=file:///pfad/zu/goa_webapp/src/main/resources/static ./scripts/sync-styles.sh  # lokal
```

**Wichtig**: `edition.css` driftet auseinander, sobald sich die aktuelle Edition bei
goa_webapp ändert (neue Farben, neues Jahr, neuer Hintergrund) - der lokale
Dateisystem-Modus (`SOURCE=file://...`) kann das NICHT mitziehen (keine laufende Seite zum
Abgreifen) und lässt ein vorhandenes `edition.css` unangetastet. Nach jeder
Branding-Änderung bei goa_webapp also `./scripts/sync-styles.sh` gegen die echte Live-Seite
erneut laufen lassen, `git diff` prüfen und committen.

`shop.css` enthält nur die shop-eigenen Komponenten (Produktraster, Warenkorb,
Rechtstexte) und baut auf denselben CSS-Variablen auf.

## Lokale Entwicklung

Voraussetzung: Postgres läuft, eine Datenbank für dieses Projekt existiert, und ein
Medusa-Backend läuft erreichbar unter `MEDUSA_STORE_API_URL` (siehe
`goa_webapp_shop_admin`).

```bash
createdb goa_webapp_shop   # einmalig
export MEDUSA_PUBLISHABLE_API_KEY=pk_...   # aus dem Medusa Admin: Settings > API Key Management
mvn spring-boot:run
```

Admin-Bereich: `http://localhost:8080/admin` (Standard-Login `admin`/`admin`, über
`ADMIN_USERNAME`/`ADMIN_PASSWORD` änderbar).

## Deployment

`docker-compose.yml` geht von **zwei unterschiedlichen** Docker-Netzwerken aus - Caddys
Netzwerk und das Netzwerk, auf dem goa_webapps Postgres-Container sitzt, sind auf dem
Server i.d.R. NICHT dasselbe. Beide Netzwerknamen herausfinden mit:

```bash
docker inspect <goa_webapp-app-container> \
  --format '{{range $n,$c := .NetworkSettings.Networks}}{{$n}}{{"\n"}}{{end}}'
docker inspect <goa_webapp-db-container> \
  --format '{{range $n,$c := .NetworkSettings.Networks}}{{$n}}{{"\n"}}{{end}}'
```

Den Caddy-Netzwerknamen als `PROXY_NETWORK_NAME`, den Postgres-Netzwerknamen als
`DB_NETWORK_NAME` in `.env` setzen (siehe `.env.example`). `SHOP_DB_HOST` ist dabei der
**Containername** des Postgres-Containers (z.B. `life_webapp-db-1`, per `docker ps`
ermitteln), keine Compose-Service-Alias - dieses Projekt ist nicht Teil von goa_webapps
eigener `docker-compose.yml`. Einmalig auf dem geteilten Postgres-Container eine eigene
Datenbank für dieses Projekt anlegen (Befehl in `.env.example`).

Neuer Caddy-Block, z.B.:

```caddy
shop.heimfeld.life {
    encode zstd gzip
    reverse_proxy shop-app:8080
}
```

### Troubleshooting Ersteinrichtung/Neuaufsetzen

Siehe [goa_webapp_shop_admin's README](https://github.com/JulianVor/goa_webapp_shop_admin#troubleshooting-ersteinrichtungneuaufsetzen)
für die beim ersten Deploy aufgetretenen Stolperfallen beim Anlegen von
User/Datenbank auf dem gemeinsamen Postgres-Container (echten Superuser/DB-Namen
ermitteln statt raten, `CREATE USER`/`CREATE DATABASE` nie im selben `-c`) - gilt hier
genauso für `SHOP_DB_USER`/`SHOP_DB_NAME`.

## Offene Punkte / nächste Schritte

- **Zahlung**: Checkout ist bis zum Warenkorb voll funktionsfähig; die eigentliche
  Bezahlung (Medusa Payment Collections + Stripe Elements) ist noch nicht angebunden -
  dafür wird ein echtes Stripe-Konto mit API-Schlüsseln gebraucht. Siehe `/kasse`
  (`checkout-stub.html`) für den aktuellen Platzhalter.
- **Produkte anlegen**: passiert ausschließlich im Medusa Admin Dashboard, nicht hier.
- Wartungsmodus ist standardmäßig **an** - im eigenen Admin-Bereich ausschalten, sobald
  Produkte in Medusa stehen.
