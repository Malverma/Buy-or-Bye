# Buy or Bye — Product Specification

**Platform:** Android
**Version:** 0.1 (draft)
**Date:** 2026-10-05

---

## 1. Overview

**Buy or Bye** is a minimalist price checker. While in a store, the user takes a photo of a product on the shelf. The app identifies the product, looks up its price at nearby and online retailers (Walmart, Target, Amazon, etc.), and gives a single, clear verdict:

- **BUY** — the price here is the best deal, or a better deal elsewhere isn't worth the trip.
- **BYE** — a cheaper option exists, and after accounting for gas and time, it is worth going there (or ordering online) instead.

The app also checks local gas prices so it can work out whether driving to a cheaper store actually saves money once fuel and travel time are counted.

### 1.1 Goals

- Get from opening the app to a verdict in **under 10 seconds** on a typical mobile connection.
- One primary action: point, shoot, read the answer.
- Show the cost math openly so the user trusts the verdict.

### 1.2 Non-Goals (v1)

- Coupon clipping, cashback, or loyalty program integration.
- Shopping lists or price-drop alerts.
- In-app purchasing or checkout.
- iOS support.

---

## 2. Target User

Shoppers standing in a store aisle who want a quick answer to "Should I buy this here, or is it cheaper somewhere close by?" They want a fast verdict, not a comparison-shopping research tool.

---

## 3. Core User Flow

```
Open app ──► Camera viewfinder (default screen)
                │
                ▼
        Tap shutter / auto-detect barcode
                │
                ▼
        Identify product ──(low confidence)──► "Is this it?" confirm / pick from list
                │
                ▼
        Enter shelf price (auto-read from shelf tag via OCR, user can edit)
                │
                ▼
        Fetch prices: nearby stores + online retailers
        Fetch gas price + drive distance/time to each store
                │
                ▼
        Verdict screen: BUY or BYE + breakdown
```

---

## 4. Functional Requirements

### 4.1 Product Capture & Identification

| ID | Requirement |
|----|-------------|
| F-1 | App launches directly into a full-screen camera viewfinder. |
| F-2 | **Barcode first:** ML Kit continuously scans for UPC/EAN barcodes in the frame; the scan screen prompts the user to scan the barcode first. A code must read the same on two frames in a row. UPC-E is expanded to UPC-A, and US EAN-13 codes become UPC-A. The barcode is looked up in UPCitemdb, then, if unknown, by a Google web search for the code (the name the result titles agree on). |
| F-3 | **Image fallback:** if there is no readable barcode, the user taps the shutter and the photo goes to an image-recognition service to identify the product (brand, name, size/variant). |
| F-4 | **Shelf price OCR:** read the shelf price tag in the frame using on-device text recognition. Pre-fill it as the "price here" value; the user can edit it. |
| F-5 | The confirm screen states where the name came from: "Barcode matched", "matched by web search", "barcode not found", or "no barcode read" (name guessed from the label's largest print). A **Rescan** button returns to the camera when there's no barcode match. |
| F-6 | Size and variant (e.g., 12 oz vs 16 oz) must match when comparing prices. When sizes differ, compare by **unit price** and label it clearly. |

### 4.2 Price Lookup

| ID | Requirement |
|----|-------------|
| P-1 | Query prices for the identified product from supported retailers (see §6). |
| P-2 | **Local stores:** limit physical-store results to a configurable radius (default **10 miles**) and, where the source supports it, to stores that show the item **in stock**. |
| P-3 | **Online retailers:** include price, shipping cost (or free-shipping threshold), and estimated delivery date. |
| P-4 | Show each result's source and the time it was last updated. Mark prices older than 24 hours as possibly stale. |
| P-5 | Each retailer lookup has its own timeout (default 5 s). Show partial results as they arrive; a slow source must not block the verdict. |
| P-6 | Cache results by product ID + location for 1 hour to save data and API cost. |

### 4.3 Gas Price & Trip Cost

| ID | Requirement |
|----|-------------|
| G-1 | Get the current regular-unleaded price near the user's location. Fallback order: local station data → regional average → user-entered value. |
| G-2 | Get driving distance and drive time from the current store to each candidate store. |
| G-3 | Use the vehicle's fuel economy (MPG) set by the user (default **25 MPG**). |
| G-4 | Use the user's **value of time** ($/hour, default **$15/hr**, can be set to $0 to count gas only). |
| G-5 | Optional: per-mile vehicle wear cost (default **off**; suggested value $0.10/mi). |

### 4.4 Verdict Engine

The user is already at Store A. The question is whether the **extra** trip to Store B is worth it.

**Extra distance**

If the user has set a home location:

```
extra_miles = dist(A → B) + dist(B → Home) − dist(A → Home)
extra_minutes = time(A → B) + time(B → Home) − time(A → Home)
```

If no home is set, assume a round trip:

```
extra_miles   = 2 × dist(A → B)
extra_minutes = 2 × time(A → B)
```

**Trip cost**

```
fuel_cost  = (extra_miles / mpg) × gas_price
time_cost  = (extra_minutes / 60) × value_of_time
wear_cost  = extra_miles × wear_per_mile          (if enabled)
trip_cost  = fuel_cost + time_cost + wear_cost
```

**Net savings (physical store)**

```
net_savings = (price_here − price_B) − trip_cost
```

**Net savings (online)**

```
net_savings = price_here − (price_online + shipping)
```

Online options are shown with their delivery date. They count toward a BYE verdict only if the user has turned on "Online OK" (default **on**).

**Verdict**

- **BYE** if the best `net_savings` ≥ the user's **minimum worthwhile savings** (default **$3.00**).
- **BUY** otherwise.
- If no comparison data comes back, show **"No better price found — BUY"** with a note on which sources failed.

| ID | Requirement |
|----|-------------|
| V-1 | Show the verdict in large text at the top of the result screen (BUY = green, BYE = amber/red). |
| V-2 | Under the verdict, show the best alternative: the listing's product photo and title (so the user can confirm it's the same item), store, price, distance, and net savings. |
| V-3 | An expandable "Show the math" section lists the price difference, fuel cost, time cost, and net savings. |
| V-4 | Show all results in two tabs, **In store** and **Online**, each sorted by net savings. Every row shows the listing's product photo, retailer, listing title, and price. The screen opens on the tab with the best deal. |
| V-5 | Tapping a physical store opens navigation (Google Maps intent). Tapping an online result opens the product page in the browser or retailer app. |

### 4.5 Settings

Settings live on a single, minimal screen:

- Vehicle MPG
- Value of time ($/hr)
- Minimum worthwhile savings ($)
- Search radius (miles)
- Home location (optional; used for the extra-distance calculation)
- Include online retailers (on/off)
- Include vehicle wear cost (on/off + $/mi)
- Units: miles/MPG or km/L/100km
- Manual gas price override

### 4.6 History (lightweight)

- Store the last 50 scans on the device: product, price here, verdict, and date.
- Tapping a history item re-runs the lookup.
- "Clear history" option in Settings.

---

## 5. Non-Functional Requirements

| Category | Requirement |
|----------|-------------|
| **Performance** | Barcode detection < 1 s. First price results < 4 s. Full verdict < 10 s on 4G/LTE. |
| **Connectivity** | Needs Wi-Fi or mobile data for lookups. When offline: show a clear "No connection" state, keep the scan, and offer to retry when the connection returns. Barcode scanning and OCR work offline. |
| **Data usage** | Downscale and compress photos (≤ 1024 px long edge, JPEG ~80%) before upload. Target < 300 KB per scan. |
| **Battery** | The camera and location are active only while the app is in the foreground. No background location. |
| **Accessibility** | Supports TalkBack. Verdict is never shown by color alone (always text + icon). Minimum touch target 48 dp. Supports dynamic font sizes. |
| **Privacy** | Photos are used only for identification and are not stored on the server after processing. Location is sent only to resolve nearby stores, gas prices, and routes. No account required. |
| **Security** | All third-party API keys live on the backend proxy, never in the APK. HTTPS only. |
| **Reliability** | Failure of any single retailer source must not crash or block the flow. |

---

## 6. Data Sources & Integrations

### 6.1 Price data: Google Shopping via SerpApi

Retailer prices are public on Google Shopping, which already lists Walmart, Target, Amazon, Best Buy, and others for the same product in one place. The app does **not** scrape Google's HTML directly: Google blocks automated requests with CAPTCHAs within a few queries (especially from a phone's IP), the page markup changes often, and it breaks Google's terms. Instead the app uses **[SerpApi](https://serpapi.com)**, a service that runs these Google searches and returns the results as structured JSON. It handles proxies, CAPTCHAs, and parsing, and has a free tier (100–250 searches per month, depending on the plan) for development.

Each check uses SerpApi in three ways:

1. **`engine=google_shopping`** with the product name and the user's city → a list of offers: retailer (`source`), `extracted_price`, delivery text, link, photo.
2. **`engine=google_immersive_product`** for the best-matching listing → Google's product page for that exact item, which groups identical products and lists every store selling it. Offers from this page are marked **Same product**, but only when the page confirms the size the user is looking for; a listing without a size can belong to another size's page.
3. **`engine=google_maps`** for each of the top physical retailers → the nearest branch's coordinates. A retailer with a branch inside the search radius becomes an **in-store** option; otherwise it is treated as **online** (price + shipping). Third-party marketplace sellers (e.g. "Walmart - Seller Co") are always online.

Google Shopping does **not** match barcode numbers (searching a UPC returns unrelated products), so the barcode is used to get the exact name, and every listing title is then checked against that name. A listing that conflicts on brand, size, pack count (e.g. "2x", "Pack of 3") or flavor/variant words (e.g. "Sour Cream", "Lightly Salted") is marked **May be a different item**, shows no savings, and never drives the verdict.

Google Shopping shows each retailer's online price. For big chains this is usually the same as the shelf price, but not always, so in-store results are labeled "online price; may vary in store."

All price sources sit behind a `PriceProvider` interface, so SerpApi can be swapped for a similar service (Serper.dev, SearchApi.io) or a backend proxy without changing the rest of the app.

**Cost per scan:** 1 shopping search + 1 product page + up to 4 store lookups (store locations are cached per retailer for the session), plus 1 web search when UPCitemdb doesn't know a barcode.

### 6.2 Other sources

| Need | Primary option | Fallback |
|------|----------------|----------|
| Barcode scanning | Google ML Kit Barcode Scanning (on-device) | — |
| Shelf price + label text | Google ML Kit Text Recognition (on-device) | Manual entry |
| UPC → product name | UPCitemdb (free trial endpoint, no key, ~100/day) | Google web search for the code via SerpApi |
| No barcode | Use label text read by OCR as an editable search query | Manual text search |
| Retail prices | SerpApi Google Shopping (§6.1) | — |
| Store locations | SerpApi Google Maps (§6.1) | — |
| Gas prices | Google Places API (New) `fuelOptions` from nearby stations (median regular price) | Manual gas price in Settings |
| Driving distance / time | Google Routes API (Compute Route Matrix) | Straight-line distance × 1.3, at an average of 25 mph |

The Google Maps Platform key (gas + routes) is **optional**. With only a SerpApi key the app still works, using the manual gas price and estimated distances.

### 6.3 API keys

- **Development:** keys are read from `local.properties` (`SERPAPI_KEY`, `MAPS_API_KEY`) into `BuildConfig`. `local.properties` is git-ignored.
- **Production:** move the provider calls behind the backend proxy (§7.3) so no keys ship in the APK.

---

## 7. Architecture

### 7.1 High-Level

```
┌──────────────────────────┐        HTTPS        ┌───────────────────────────────┐
│      Android App         │ ──────────────────► │    Buy or Bye Backend (proxy) │
│                          │                     │                               │
│  CameraX + ML Kit        │                     │  /identify   → vision / UPC   │
│  (barcode, OCR on-device)│                     │  /prices     → retailer fan-out│
│  Verdict engine (local)  │ ◄────────────────── │  /gas        → gas price API  │
│  DataStore (history)     │       JSON          │  /routes     → routes matrix  │
│  DataStore (settings)    │                     │  Cache (Redis), rate limiting │
└──────────────────────────┘                     └───────────────────────────────┘
```

- The **verdict engine runs on the device**, so changing settings recalculates instantly without network calls.
- The **backend** holds API keys, fans out retailer requests in parallel, normalizes results, and caches them.

### 7.2 Android Tech Stack

| Layer | Choice |
|-------|--------|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Camera | CameraX |
| On-device ML | ML Kit Barcode Scanning, ML Kit Text Recognition |
| Networking | OkHttp + kotlinx.serialization |
| Async | Kotlin Coroutines + Flow |
| DI | Manual (single `AppContainer`); move to Hilt if the app grows |
| Local storage | DataStore (settings + history as JSON); in-memory response cache |
| Location | Fused Location Provider (Google Play Services) |
| Architecture | MVVM, single activity, screen state in the ViewModel |
| Min / Target SDK | minSdk 26 (Android 8.0) / targetSdk 36 |

### 7.3 Backend (suggested)

- Stateless HTTP service (e.g., Kotlin/Ktor, Node, or Python/FastAPI) on a serverless or container platform.
- Redis cache: product lookups (24 h), prices (1 h), gas prices (6 h), routes (1 h).
- Per-device rate limiting using an anonymous install ID.

### 7.4 Android Permissions

| Permission | Purpose | When requested |
|------------|---------|----------------|
| `CAMERA` | Photograph products / scan barcodes | First launch |
| `INTERNET` | Price, gas, and route lookups | Install-time (normal) |
| `ACCESS_NETWORK_STATE` | Detect offline state | Install-time (normal) |
| `ACCESS_COARSE_LOCATION` | Find nearby stores and gas prices | Before the first lookup |
| `ACCESS_FINE_LOCATION` | Accurate drive distance (optional upgrade) | Before the first lookup, optional |

No background location, storage, or contacts permissions.

---

## 8. Data Models (client)

```kotlin
data class Product(
    val id: String,              // internal ID or UPC
    val upc: String?,
    val name: String,
    val brand: String?,
    val size: String?,           // e.g. "16 oz"
    val unitQuantity: Double?,   // normalized for unit pricing
    val unit: String?,           // "oz", "ct", "lb", ...
    val imageUrl: String?
)

data class PriceResult(
    val retailer: String,        // "Walmart", "Target", "Amazon", ...
    val channel: Channel,        // IN_STORE or ONLINE
    val price: Double,
    val unitPrice: Double?,
    val shipping: Double?,       // ONLINE only
    val deliveryEstimate: LocalDate?,
    val inStock: Boolean?,
    val store: StoreLocation?,   // IN_STORE only
    val productUrl: String?,
    val fetchedAt: Instant
)

data class StoreLocation(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val driveMiles: Double?,
    val driveMinutes: Double?
)

data class TripCost(
    val extraMiles: Double,
    val extraMinutes: Double,
    val fuelCost: Double,
    val timeCost: Double,
    val wearCost: Double
) { val total get() = fuelCost + timeCost + wearCost }

data class Verdict(
    val decision: Decision,      // BUY or BYE
    val best: PriceResult?,
    val netSavings: Double,
    val trip: TripCost?,
    val alternatives: List<Pair<PriceResult, Double>> // result to net savings
)
```

---

## 9. UI / UX

**Design principles:** minimal, one-handed, glanceable. No more than one primary action per screen. Neutral palette; color is reserved for the verdict.

### 9.1 Screens

1. **Scan** (home)
   - Full-bleed camera preview, large shutter button at the bottom.
   - Small icons: History (top-left), Settings (top-right).
   - A subtle overlay frame; a barcode highlight appears when one is detected.

2. **Confirm** (shown only when needed)
   - The user's photo of the product stays visible (shrinking when the keyboard opens) while they edit the name and "Price here: $__.__".
   - "Check prices" button.

3. **Verdict**
   ```
   ┌─────────────────────────────┐
   │            BYE              │  ← large, colored, with icon
   │  Save $6.40 at Walmart      │
   │ ┌───┐ Brand Product 16 oz   │  ← listing photo + title
   │ │img│ Walmart $8.99 · 3.2 mi│
   │ └───┘                       │
   │  [ Navigate ]               │
   ├─────────────────────────────┤
   │  ▸ Show the math            │
   │    Price difference  $9.00  │
   │    Gas (6.1 mi @ $3.29) $0.80│
   │    Time (16 min)     $1.80  │
   │    Net savings       $6.40  │
   ├─────────────────────────────┤
   │  Price here         $17.99  │
   │  [ In store (3) | Online (5) ]
   │  [img] Walmart       $8.99  │
   │        Brand Product 16 oz  │
   │  [img] Target       $14.49  │
   └─────────────────────────────┘
   ```

4. **Settings** — single scrolling list (see §4.5).

5. **History** — simple list: thumbnail, name, verdict badge, date.

### 9.2 Empty & Error States

| State | Message |
|-------|---------|
| No connection | "You're offline. Connect to Wi-Fi or mobile data to check prices." + Retry |
| Product not recognized | "Couldn't identify this item." + Try again / Search by name |
| No price data | "No better price found nearby — BUY." + list of sources that failed |
| Location denied | Online-only comparison, with a banner explaining that local stores and gas need location |
| Gas price unavailable | Use the last known value or the manual setting, labeled "estimated" |

---

## 10. Onboarding

Three short screens, all skippable:

1. "Snap a product. We'll tell you if it's cheaper nearby."
2. Camera + location permission request, with a reason for each.
3. Quick setup: MPG and value of time (pre-filled with defaults).

---

## 11. Analytics (privacy-respecting, opt-in)

- Scan → verdict success rate
- Identification method used (barcode / image / manual)
- Time to verdict
- Per-source failure and timeout rates
- BUY vs BYE ratio

No personal data, precise location, or photos are logged.

---

## 12. Testing

- **Unit:** verdict engine (extra-distance math, unit-price normalization, thresholds, edge cases such as zero MPG, negative savings, and missing data).
- **Integration:** backend adapters with recorded API responses; timeouts and partial failures.
- **UI:** Compose tests for each screen and empty/error state.
- **Field:** real in-store testing at 3+ store chains, on low-signal connections, and with poor lighting and angled barcodes.

---

## 13. Milestones

| Phase | Scope |
|-------|-------|
| **M1 — Core scan** | CameraX, barcode scanning, UPC lookup, manual price entry |
| **M2 — Price compare** | Backend proxy, 2+ retailer sources, results list |
| **M3 — Trip math** | Location, routes, gas prices, verdict engine, Settings |
| **M4 — Polish** | Image recognition fallback, shelf-tag OCR, history, onboarding, accessibility |
| **M5 — Beta** | Closed testing via Google Play internal track |

---

## 14. Open Questions

1. Which retailer data sources are affordable and licensed for this use at launch scale?
2. Should the app pay for station-level gas prices, or is a regional average accurate enough for v1?
3. Should the trip cost include a return to the original store (e.g., the user also has other items in their cart there)?
4. Monetization: affiliate links on online results, a one-time purchase, or free?
5. Support for store-brand / generic equivalents ("similar product, cheaper")?
