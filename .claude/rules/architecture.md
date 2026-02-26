# Architecture

## Layers and Responsibilities

- **`App`** — JavaFX entry point only. Builds the scene graph, wires up UI components, and delegates everything else. No map logic here.
- **`MapView`** — Rendering and input handling for the map canvas. Owns the operation mode state (`NAVIGATION` / `DRAWING`). Does not contain tile fetching, caching, or drawing logic — it delegates those to `TileCache` and `DrawingSkill`.
- **`TileCache`** — All tile fetching and caching. Network requests run on a background `ExecutorService`; UI updates use `Platform.runLater`. Handles both memory cache (LRU, 512 entries) and disk cache (`~/.mapster/tiles/`).
- **`TileMath`** — Pure static utility for Web Mercator coordinate conversions. No state, no dependencies.
- **`DrawingSkill`** — All drawing state and behaviour (current line, completed lines, point editing/dragging). Stateful but not a JavaFX node. Communicates with `MapView` via the `CoordinateConverter` interface.
- **`TileSource` (interface)** — Contract for tile providers: URL template, zoom range, attribution, availability check.

## TileSource Implementations

All tile providers implement `TileSource`. The pattern is:
- `getId()` returns a stable, filesystem-safe string used as the disk cache directory name.
- `isAvailable()` returns false when a required API key or environment variable is absent (e.g. Ordnance Survey).
- Never hardcode tile URLs — they belong in the implementing class, not in `MapView` or `TileCache`.

## Threading

- All network I/O happens on `TileCache`'s background `ExecutorService` (2 threads).
- `Platform.runLater` is the only way to trigger a re-render from a background thread — via the `onTileLoaded` callback injected into `TileCache`.
- `MapView.render()` is always called on the JavaFX Application Thread.

## Coordinate System

- Internal map state uses **tile coordinates** (`centerX`, `centerY`) at the current zoom level, not lat/lon. This avoids repeated trig in the render loop.
- Conversions between tile coordinates, screen pixels, and geographic coordinates are all in `TileMath`.
- `DrawingSkill` stores points as `[lat, lon]` pairs and converts to/from screen coordinates via `CoordinateConverter` on every render.

## Drawing / Editing

- Drawing tools are encapsulated in `DrawingSkill`, not embedded in `MapView`.
- `MapView` creates a fresh `CoordinateConverter` per render/event and passes it into `DrawingSkill`. Do not cache coordinate converters between renders.
- The two modes (`NAVIGATION`, `DRAWING`) are an enum inside `MapView`. Mode transitions abort any in-progress line via `drawingSkill.abortCurrentLine()`.

## Zoom

- Global max zoom is 20 (constant in `MapView`).
- Each `TileSource` declares its own `getMinZoom()` and `getMaxZoom()`. `TileCache` scales tiles from the source's max zoom when the display zoom exceeds it.
- Zoom changes must preserve the geographic point under the cursor (or center for toolbar buttons).