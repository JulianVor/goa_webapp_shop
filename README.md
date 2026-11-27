# goa_webapp_shop

Der öffentliche Festivalshop-Storefront für `shop.heimfeld.life` - Spring Boot + Thymeleaf,
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
- Wartungsmodus-Schalter ("Festivalshop kommt bald" statt Sortiment)
- Kontakt-E-Mail
- Testzugang: sperrt den kompletten Shop (nicht nur `/admin`) hinter einer
  HTTP-Basic-Anmeldeabfrage des Browsers, mit denselben Zugangsdaten wie der Admin-Login
  (`ADMIN_USERNAME`/`ADMIN_PASSWORD`) - genau wie goa_webapp's eigener Testzugang (siehe
  dessen `SiteLockFilter`). Hier: `ShopLockFilter`, läuft vor Spring Security und greift
  deshalb auch für `/admin` und `/login` selbst. Standardmäßig aus.

## Vorbestellungen (Preorder)

Für Artikel mit festem Annahmeschluss (z.B. das Festival-Shirt für die nächste Ausgabe) -
ganz ohne Custom-Code in Medusa, ganz ohne eigenes Datenmodell: im Medusa Admin Dashboard
am jeweiligen Produkt unter **Metadata** folgende Keys setzen:

```json
{
  "preorder": true,
  "preorder_deadline": "2027-06-01T00:00:00+02:00",
  "pickup_only": true
}
```

- `preorder_deadline` ist ISO-8601 mit Zeitzonen-Offset (siehe Beispiel) - ohne Offset
  schlägt das Parsen fehl und das Produkt bleibt (absichtlich fail-open) sichtbar.
- `pickup_only` ist optional und zeigt den Hinweis "Nur Abholung auf dem Festival" an.
  Damit das auch im Checkout wirkt (kein Versandzwang/keine Versandkosten für dieses
  Produkt), muss es zusätzlich im Medusa Admin einem eigenen **Shipping Profile**
  zugewiesen werden (Produkt bearbeiten → Organize → Shipping Profile), das eine eigene,
  kostenlose Versandart hat (Settings → Locations → bei der Service Zone eine neue
  Shipping Option anlegen, z.B. "Abholung beim Festival", Preis 0 €). Der Checkout
  erkennt automatisch, wenn ein Warenkorb Artikel aus mehreren Shipping Profiles enthält
  (z.B. ein Abhol-Artikel zusammen mit einem Versand-Artikel) und lässt pro Profile eine
  eigene Versandart wählen (siehe `ShippingGrouping`). Ohne eigenes Shipping Profile
  bleibt ein `pickup_only`-Produkt im Standard-Profile und wird ganz normal mit
  Versandkosten berechnet - das Metadata-Feld allein reicht dafür nicht.
- Nach Ablauf von `preorder_deadline` verschwindet das Produkt automatisch aus der
  Übersicht und die Detailseite liefert 404 - keine manuelle Aktion nötig.
- Ein Produkt ohne `preorder: true` ist komplett unbeeinflusst - normaler Lagerartikel.

Die Prüfung läuft ausschließlich in diesem Storefront (`MedusaProduct.isPreorderExpired()`
in `StorefrontController`), nicht in Medusa selbst - bewusst, um
`goa_webapp_shop_admin` als unverändertes `@medusajs/medusa`-Projekt zu erhalten (siehe
dessen README). Ein direkter API-Call gegen Medusas Store-API am Storefront vorbei würde
die Deadline nicht kennen - für die Shop-Größe ein akzeptiertes Restrisiko, keine
Serverseitige Hard-Sperre in Medusa.

## Styles von goa_webapp

Zwei getrennte Mechanismen, je nachdem wie oft sich die Sache ändert:

**`static/css/style.css` und `static/fonts/Brugty.ttf`** sind **deploy-zeit-synchronisierte
Kopien** aus goa_webapp (siehe Konzept-Diskussion "Option B") - sie definieren nur, *wie*
eine Reihe CSS-Variablen (`--color-*`, `--font-*`, `--box-radius`, `--pill-radius`,
`--bg-image`) benutzt werden, und ändern sich selbst selten (neue CSS-Komponente, neue
Schriftdatei). Neu ziehen mit:

```bash
./scripts/sync-styles.sh                                   # von https://heimfeld.life
SOURCE=file:///pfad/zu/goa_webapp/src/main/resources/static ./scripts/sync-styles.sh  # lokal
```

Danach `git diff` prüfen und committen.

**Die tatsächlichen *Werte*** dieser Variablen (welche Farben, welches der beiden
Font-Paare Brugty/Poppins vs. Bebas Neue/Oswald, Hintergrundbild, Logo) sind bei goa_webapp
**pro aktueller Edition** aus der Datenbank und ändern sich viel öfter (jedes neue "Jahr").
Dafür gibt's **keinen** Sync-Schritt mehr: `GoaWebappBrandingClient` holt sich
`GET /api/branding` von goa_webapp (`GOA_WEBAPP_URL`, siehe `.env.example`) live beim Start
und danach alle `BRANDING_REFRESH_INTERVAL_MS` (Default 5 Min.) erneut, cached das Ergebnis
im Speicher und rendert `fragments/head.html`/`fragments/nav.html` direkt damit - keine
manuelle Aktion nötig, wenn sich bei goa_webapp die Edition ändert. Ist goa_webapp gerade
nicht erreichbar, bleibt der zuletzt bekannte Stand (oder ein eingebauter generischer
Fallback, vor dem allerersten erfolgreichen Abruf) aktiv, statt dass die Seite kaputtgeht.

**Wichtig für den Server**: `GOA_WEBAPP_URL` zeigt standardmäßig auf goa_webapps
Container-Namen im gemeinsamen `proxy`-Netzwerk (kein Umweg über Caddy/TLS für einen
Server-zu-Server-Call), **nicht** auf die öffentliche Domain. Damit die darin enthaltenen
Hintergrundbild-/Logo-URLs trotzdem korrekt auf `https://heimfeld.life/...` zeigen (statt
auf die interne Container-Adresse, die der Browser der Besucher*innen nie erreichen kann -
derselbe Fehler wie der `localhost:9000`-Bug bei Medusa), **muss** `APP_BASE_URL` in
goa_webapps eigener `.env` auf `https://heimfeld.life` gesetzt sein. Prüfen mit:
```bash
docker compose -f /opt/life_webapp/docker-compose.yml run --rm app env | grep APP_BASE_URL
```

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

- **Zahlung**: `/kasse` führt durch Adresse → Versandart → Bestellübersicht + Zahlung
  (eingebettetes Stripe Payment Element, siehe `checkout.html`). Die Bestellübersicht vor
  der Zahlung zeigt Artikel, Lieferadresse, Versandart und die Kostenaufschlüsselung mit
  je einem "ändern"-Link zurück zum jeweiligen Schritt (`/kasse?step=adresse` bzw.
  `?step=versand`), der die bereits eingegebenen Daten vorausfüllt bzw. die zuvor gewählte
  Versandart vorauswählt. Die Bestellung wird erst nach erfolgreicher Zahlung angelegt
  (`/kasse/erfolg`). Publishable Key und Provider-ID
  kommen aus `STRIPE_PUBLISHABLE_KEY`/`STRIPE_PROVIDER_ID` - der Secret Key liegt nur
  in `goa_webapp_shop_admin` (`STRIPE_API_KEY`), siehe dessen README für die
  Webhook-Einrichtung. Versandarten/-zonen müssen im Medusa Admin konfiguriert sein
  (Settings → Locations → Shipping Options), sonst zeigt der Versand-Schritt keine
  Optionen an. Enthält ein Warenkorb Artikel aus mehreren Shipping Profiles (siehe
  `pickup_only` oben), zeigt der Versand-Schritt eine eigene Versandart-Auswahl pro
  Profile statt nur einer.
- **Bestellbestätigung per Mail**: wird nach erfolgreicher Zahlung automatisch an die
  beim Checkout angegebene Adresse verschickt (siehe `OrderMailService`), über
  `MAIL_HOST`/`MAIL_USERNAME`/`MAIL_PASSWORD`/`MAIL_FROM` (gleiches SMTP-Setup wie
  goa_webapp's Newsletter, z.B. Brevo). Ohne `MAIL_HOST` wird die Mail übersprungen
  und nur eine Warnung geloggt - der Checkout selbst schlägt dadurch nie fehl.
  Betreff/Inhalt sind unter **Admin → Bestellbestätigung** (`/admin/bestellbestaetigung`)
  mit demselben Rich-Text-Editor + Live-Vorschau editierbar wie goa_webapp's Newsletter-
  Bestätigungsmail (inkl. Hintergrundbild der aktuellen Edition, sofern gesetzt). Dort
  gibt es auch einen "E-Mail Versand testen"-Button, der eine Beispielmail über die
  echte SMTP-Verbindung an `MAIL_FROM`/`MAIL_USERNAME` verschickt - nützlich zur Fehlersuche,
  wenn Bestellbestätigungen nicht ankommen: ein Fehlschlag zeigt die genaue SMTP-
  Fehlermeldung direkt im Admin an (zusätzlich mit vollem Stacktrace im Server-Log, siehe
  `OrderMailService.sendTestMail()`), statt nur eine stille Warnung zu loggen wie der
  normale Versand nach einer Bestellung.
  `{{bestellnummer}}` wird durch die Bestellnummer ersetzt, `{{bestellungen}}` durch
  eine Tabelle mit den bestellten Artikeln, Mengen und Preisen, separat ausgewiesenen
  Versandkosten und der Gesamtsumme.
- **Produkte anlegen**: passiert ausschließlich im Medusa Admin Dashboard, nicht hier.
- Wartungsmodus ist standardmäßig **an** - im eigenen Admin-Bereich ausschalten, sobald
  Produkte in Medusa stehen.

## Rechtskonformität (DE/EU)

Umgesetzt für den Verkaufsstart (siehe das Analyse-Dokument "Rechtskonformität
Festivalshop" für den vollständigen Hintergrund):

- **Checkout**: finaler Button heißt "Zahlungspflichtig bestellen" (§312j BGB), eine
  Pflicht-Checkbox "Ich akzeptiere die AGB ..." (mit Links zu AGB/Widerrufsbelehrung)
  muss angehakt sein, bevor der Browser das Zahlungsformular überhaupt absendet. Unter
  **Admin → Einstellungen → Versand & Checkout-Hinweise** lässt sich ein Hinweistext zu
  Lieferbeschränkungen/Zahlungsmitteln pflegen, der am Anfang des Checkouts erscheint.
- **Verkäuferdaten**: strukturierte Felder (Firmenname, Anschrift, Rücksende-E-Mail)
  unter **Admin → Einstellungen → Verkäuferdaten** - werden ins Muster-Widerrufsformular
  und die Bestellbestätigungsmail eingesetzt, statt nur im Impressum-Fließtext zu stehen.
- **Rechtstext-Versionierung**: `ShopSettingsService` zählt beim Speichern automatisch
  eine Versionsnummer + Datum hoch, sobald sich AGB- oder Widerruf-Text ändern
  (`ShopSettings.agbVersion`/`widerrufVersion` + die zugehörigen `*VersionDate`-Felder).
  Jede Bestellbestätigungsmail zeigt, welche Fassung zum Bestellzeitpunkt galt.
- **Elektronischer Widerruf** (§356a BGB, Pflicht seit 19.06.2026): öffentliches Formular
  unter `/widerruf-erklaeren` (Name, Bestellreferenz, E-Mail, optionale Notiz) - legt einen
  `WithdrawalRequest`-Datensatz an, schickt sofort eine Bestätigung an die Kundin/den
  Kunden (mit allen Angaben zur eigenen Dokumentation) und eine Benachrichtigung an
  `contactEmail`. Dauerhafter Footer-Link "Vertrag widerrufen". Unter **Admin →
  Widerrufe** (`/admin/widerrufe`) gibt es die Worklist mit manuellem Status-Umschalter
  (neu/bearbeitet) - die eigentliche Stornierung/Rückerstattung passiert bewusst manuell
  im Medusa Admin + Stripe Dashboard, nicht automatisiert (Medusa bleibt vanilla, siehe
  Architektur oben).
- **Bestellbestätigungsmail**: hängt unterhalb des admin-editierbaren Textes automatisch
  einen festen Block an (Verkäuferangaben, Zahlungsstatus - siehe
  `OrderMailService.legalFooterHtml()`). AGB, Widerrufsbelehrung und Muster-Widerrufsformular
  kommen zusätzlich als eigene PDF-Dateien im Anhang, live aus dem admin-gepflegten Text
  und den Versionsständen erzeugt (`LegalDocumentPdfService`, per openhtmltopdf+jsoup -
  das rich-text-editor-HTML ist nicht zwingend wohlgeformtes XML, jsoup räumt es vorher auf).
  Für einen absoluten Link zur elektronischen Widerrufserklärung in der Widerrufsbelehrung-PDF
  `SHOP_BASE_URL` setzen (sonst wird der Link weggelassen). Die Docker-Runtime braucht dafür
  mindestens eine echte Schriftart (`fontconfig`/`ttf-dejavu` im Dockerfile) - Alpine liefert
  von Haus aus keine mit, und PDFBox braucht eine zur Glyphen-Substitution.
- **Muster-Widerrufsformular**: eigene Seite `/muster-widerrufsformular` mit dem
  gesetzlichen Text und den strukturierten Verkäuferdaten eingesetzt.
- **"Versand und Abholung"**: eigene Seite `/versand-und-abholung` mit live aus Medusa
  geladenen Versandoptionen (Preis) plus Admin-Freitext für Lieferzeiten/Abholinfo
  (**Admin → Einstellungen → Versand & Checkout-Hinweise**).
- **Produktseite**: zeigt SKU/Artikelnummer (natives Medusa-Variantenfeld `sku`) und
  Material/Hersteller aus Metadata (`metadata.material`/`metadata.manufacturer`), sowie
  bei `pickup_only`-Produkten einen konkreten Abholzeitraum statt nur des Badges, wenn
  `metadata.pickup_start`/`pickup_end` gesetzt sind (gleiches Metadata-Prinzip wie
  `preorder`/`preorder_deadline`/`pickup_only` oben).
- **Bewusst nicht umgesetzt** (siehe Analyse-Dokument für die Begründung): keine
  automatisierte PDF-Rechnungserstellung, kein eigener Medusa-Endpunkt für den Widerruf,
  kein Cookie-Banner (kein nicht-essenzielles Tracking im Shop gefunden).
- **Preishistorie für "X % reduziert"-Badges** (§11 PAngV): Der Shop führt eine eigene,
  von Medusas Price Lists unabhängige Preishistorie (`PriceObservation`/
  `PriceHistoryService`) - Medusas eigenes "original_amount" ist nur, was ein Admin
  eingetragen hat, kein Beleg dafür, dass dieser Preis je tatsächlich verlangt wurde, und
  taugt daher allein nicht als rechtssicherer Vergleichspreis. Einmal täglich (und sofort
  beim Start) wird für jede Variante der aktuelle Preis geloggt, aber nur wenn er sich seit
  dem letzten Log-Eintrag geändert hat. Die "−X %"-Badge auf Produktkarte/Produktseite
  erscheint nur noch, wenn der aktuelle Preis **nachweislich unter dem niedrigsten in den
  letzten 30 Tagen tatsächlich im Shop verlangten Preis** liegt (`PriceDisplay`,
  `PriceHistoryService.lowestPriceLast30Days`) - nicht mehr automatisch, sobald in Medusa
  eine Price List mit höherem "original_amount" hinterlegt ist.
  **Konkrete Folge:** Für ein Produkt, das im Shop selbst nie zu einem höheren Preis
  gelistet war (z.B. nur vor Ort beim Festival teurer verkauft, nie online), verschwindet
  die Badge direkt nach diesem Deploy - es gibt noch keine echte, im Shop nachverfolgte
  Preissenkung, auf die sie sich stützen könnte. Sobald ein Preis im Shop echt einmal
  angehoben und wieder gesenkt wird, erscheint die Badge automatisch und korrekt wieder,
  ganz ohne Admin-Eingriff - einfach den Preis in Medusa ändern. Es gibt aktuell keine
  Admin-Oberfläche, um die geloggte Preishistorie selbst einzusehen (nur in der Datenbank,
  Tabelle `price_observation`).

## Vorbestellungen / Bestellungen-Export

Zwei unterschiedliche Bedürfnisse, zwei unterschiedliche Lösungen:

- **Bestellungen für die Buchhaltung**: braucht keinen eigenen Code - Medusa selbst kann das
  schon. Im Medusa Admin Dashboard unter **Orders** gibt es eine eingebaute Export-Funktion
  (Filter nach Zeitraum/Status, dann Download als CSV). Da es rein um Medusas eigene
  Bestelldaten geht, gibt es keinen Grund, das hier zu duplizieren ("Medusa bleibt vanilla").
- **Vorbestellungen fürs Festival** (**Admin → Vorbestellungen**, `/admin/vorbestellungen`):
  das kann Medusas Export nicht leisten, weil es shop-eigene Logik braucht (welche Artikel
  `pickup_only` sind, siehe `MedusaProduct.isPickupOnly()`) - deshalb eigener Code
  (`MedusaAdminClient`/`AdminExportController`). Die Seite zeigt eine Zeile pro noch nicht
  abgeholter Pickup-Position (Bestellnummer, Name, Produkt, Größe), mit einem Button darüber
  für denselben Inhalt als CSV-Download (`/admin/export/abholung.csv`). Eine Zeile
  verschwindet von selbst, sobald die Position im Medusa Admin als erfüllt markiert wird
  (nach Übergabe auf dem Festival) oder die ganze Bestellung storniert wird.

  Braucht einen Medusa **Secret API Key** (Admin Dashboard: Settings → API Key Management →
  Secret API Keys - nicht den Publishable Key, der ist store-seitig und reicht dafür nicht)
  als `MEDUSA_ADMIN_API_KEY`. Secret API Keys akzeptiert Medusa ausschließlich per HTTP Basic
  Auth, nicht als Bearer-Token (verifiziert gegen `@medusajs/framework`s
  `authenticate-middleware.js`) - `MedusaAdminClient` setzt den Header dafür schon richtig.
