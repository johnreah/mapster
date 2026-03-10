# Architecture

## The application follows the Model-View-ViewModel (MVVM) pattern.

- The View layer implements the user interface and contains JavaFX code and UI logic. All JavaFX user interface objects are in the View layer. The View knows about the ViewModel and can have dependencies on it. Actions taken by the user by interacting with the View can affect the View model by method calls, data binding or event handlers.
- The ViewModel layer contains business logic and data that manage the state of the View layer. The ViewModel does not have direct dependencies on the View, but it can update objects in the View layer using data binding implemented by JavaFX properties or by raising events. The ViewModel can be unit tested in isolation of the View layer.
- The Model layer contains domain classes. It contains no JavaFX code or UI logic. It does not know about the View and ViewModel layers, and has no dependencies on them. It can be unit tested in isolation of the View and ViewModel layers.

## Layers and Responsibilities

- **`App`** — JavaFX entry point only. Builds the scene graph, wires up UI components, and delegates everything else. No map logic here.
- **`MapView`** — Container for all map layer views. Manages a `StackPane` of `TileLayerView` and `DrawingLayerView` instances, with an `InputOverlayPane` on top. Does not contain tile fetching, caching, or drawing logic.
- **`TileCache`** (`view.maptiles`) — All tile fetching and caching. Network requests run on a background `ExecutorService`; UI updates use `Platform.runLater`. Handles both memory cache (LRU, 512 entries) and disk cache (`~/.mapster/tiles/`).
- **`TileMath`** (`util`) — Pure static utility for Web Mercator coordinate conversions. No state, no dependencies.
- **`DrawingTool`** — All drawing state and behaviour (current line, completed lines, point editing/dragging). Stateful but not a JavaFX node. No JavaFX dependencies. Communicates with `DrawingLayerView` via the `CoordinateConverter` interface.
- **`TileSource` (interface)** (`util`) — Contract for tile providers: URL template, zoom range, attribution, availability check.

## TileSource Implementations

All tile providers implement `TileSource` (`util`) and live in `view.maptiles`. The pattern is:
- `getId()` returns a stable, filesystem-safe string used as the disk cache directory name.
- `isAvailable()` returns false when a required API key or environment variable is absent (e.g. Ordnance Survey).
- Never hardcode tile URLs — they belong in the implementing class, not in `MapView` or `TileCache`.

## Threading

- All network I/O happens on `TileCache`'s background `ExecutorService` (2 threads).
- `Platform.runLater` is the only way to trigger a re-render from a background thread — via the `onTileLoaded` callback injected into `TileCache`.
- Layer renders (`TileLayerView.render()`, `DrawingLayerView.render()`) are always called on the JavaFX Application Thread.

## Coordinate System

- Internal map state uses **tile coordinates** (`centerX`, `centerY`) at the current zoom level, not lat/lon. This avoids repeated trig in the render loop.
- Conversions between tile coordinates, screen pixels, and geographic coordinates are all in `TileMath`.
- `DrawingTool` stores points as `[lat, lon]` pairs and converts to/from screen coordinates via `CoordinateConverter` on every render.

## Drawing / Editing

- Drawing tools are encapsulated in `DrawingTool`, not embedded in `MapView`.
- `DrawingLayerView` creates a fresh `CoordinateConverter` per render/event and passes it into `DrawingTool`. Do not cache coordinate converters between renders.
- The two modes (`NAVIGATION`, `DRAWING`) are an enum inside `InputOverlayPane`. Mode transitions abort any in-progress line via `drawingTool.abortCurrentLine()`.

## Zoom

- Global max zoom is 20 (`MapViewport.MAX_ZOOM`).
- Each `TileSource` declares its own `getMinZoom()` and `getMaxZoom()`. `TileCache` scales tiles from the source's max zoom when the display zoom exceeds it.
- Zoom changes must preserve the geographic point under the cursor (or center for toolbar buttons).