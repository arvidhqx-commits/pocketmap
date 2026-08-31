# PocketMap — Nischenbeleg und Positionierung

Stand 31.08.2026. Alle Zahlen an der Modrinth- bzw. GitHub-API geprüft, beide fremden Jars
selbst heruntergeladen und auf den lokalen Paper-Servern gestartet.

## ⚠️ Korrektur an M16 vom 30.08.
In `recherche/M16-minimap-machbarkeit.md` steht „**Kein aktiver Rivale**". **Das ist falsch.**
Die gestrige Suche benutzte die Begriffe „server side minimap" und „minimap without mods" und
übersah damit `nminimap` — ein aktiv gepflegtes Plugin, dessen neueste Version vom **22.08.2026**
stammt und 26.2 unterstützt. Die Nische ist also **nicht leer**. Was unten steht, ist die
korrigierte Fassung; PocketMap wird als *zweiter Anbieter mit anderem Versprechen* geführt,
nicht als Alleinversorger.

## Der Orphan
`vanillaminimaps` (VanillaMinimaps von JNNGL) — **59.996 Downloads**, 116 Follower, Loader
`paper/purpur`, insgesamt nur 2 Versionen, neueste **1.0.1 vom 21.07.2024** (25 Monate still).
GitHub `JNNGL/VanillaMinimaps`: letzter Push **17.02.2025** (18 Monate), 190 Sterne, GPL-3.0,
nicht archiviert. Höchste je unterstützte Minecraft-Version laut Modrinth: **1.21**.

## Pflichtfilter 1: läuft das Original noch? — **Nein, auf keiner aktuellen Version**
Das Original-Jar 1.0.1 selbst heruntergeladen und allein auf beiden Servern gestartet:

| Server | Ergebnis |
|---|---|
| Paper 1.21.11 | **stürzt beim Aktivieren ab** |
| Paper 26.2 | **stürzt beim Aktivieren ab** |

```
1.21.11: java.lang.NoClassDefFoundError: org/bukkit/craftbukkit/v1_21_R1/CraftServer
         at com.jnngl.vanillaminimaps.command.NMSCommandDispatcherAccessor.vanillaDispatcher

26.2:    java.lang.NoClassDefFoundError: net/minecraft/world/level/World
         at com.jnngl.vanillaminimaps.VanillaMinimaps.onEnable
```

Das ist ein **härterer** Befund als bei TradeHall: VillagerOptimizer lief wenigstens auf 1.21.11
noch. Hier ist das Plugin auf **beiden** aktuellen Versionen tot, und zwar an genau zwei Stellen:
dem versionierten CraftBukkit-Paket (`v1_21_R1`, von Paper abgeschafft) und den Mojang-Mappings
von NMS (`net.minecraft.world.level.World`, in 26.x umbenannt).

**Das ist die Positionierung des Portfolios in einem einzigen Stacktrace.** Und es ist die
Begründung für unseren Entwurf: PocketMap fasst weder NMS noch Pakete an. Der komplette Nutzen
entsteht aus `Bukkit.createMap`, `MapView.setCenterX/Z` und einer `FILLED_MAP` in der zweiten
Hand — drei Bukkit-API-Aufrufe, die es seit Jahren gibt.

## Pflichtfilter 2: gibt es einen aktiven Nachfolger? — **Ja, einen. Und er hat eine Hürde.**
`nminimap` (NMinimap von NezuShin/DartCat25) — **3.922 Downloads**, 13 Versionen, neueste
**1.0.8 vom 22.08.2026**, unterstützt bis **26.2**, Folia-tauglich. Beschreibung: „Papermc plugin
to display Minimap on vanilla client". Das ist derselbe Zweck. Verhältnis zum Orphan: **Faktor 15**
(59.996 zu 3.922) — die geweckte Nachfrage ist also zu rund 93 % unbedient, aber sie ist bedient.

Auch dieses Jar selbst heruntergeladen und gestartet. Ergebnis auf **beiden** Servern:

```
[NMinimap] AnvilORM plugin is not found. It is mandatory dependency.
Please download it from https://github.com/NezuShin/AnvilORM/releases/
```

NMinimap braucht also eine **Pflicht-Abhängigkeit, die nicht auf Modrinth liegt**, sondern von
Hand aus einem GitHub-Release geholt werden muss — dazu weiche Abhängigkeiten auf `packetevents`,
`PassengerAPI`, `AnvilORM`, `PlaceholderAPI`. Und es arbeitet über PacketEvents, also auf
derselben Ebene, an der das Original gestorben ist. Die Versionsliste zeigt den Preis dafür:
`1.0.6-quickfix-1`, `1.0.6-quickfix-2`, `1.0.7-quickfix-1` — drei Notfallkorrekturen in acht Wochen.

## Was daraus folgt: PocketMap ist der Zweitanbieter mit dem anderen Versprechen
Keine leere Nische, sondern eine besetzte mit einer offenen Flanke. Unsere Zusage ist nicht
„mehr Funktionen", sondern **„eine Datei, keine Abhängigkeit, nichts, was an einer
Minecraft-Version zerbrechen kann"**:

| | VanillaMinimaps | NMinimap | **PocketMap** |
|---|---|---|---|
| läuft auf 1.21.11 | ✗ Absturz | ✓ (mit AnvilORM) | ✓ |
| läuft auf 26.2 | ✗ Absturz | ✓ (mit AnvilORM) | ✓ |
| Abhängigkeiten | — | **AnvilORM (nur GitHub)** | **keine** |
| Technik | NMS + Pakete | PacketEvents | reine Bukkit-API |
| kann Spieler/Mobs zeichnen | ✓ | ✓ | ✗ (Vanilla-Gelände) |
| belegt die zweite Hand | ✗ | ✗ | **✓ (der Preis)** |

Die letzten beiden Zeilen sind bewusst so aufgeschrieben: NMinimap kann mehr. PocketMap kauft
Unzerbrechlichkeit mit dem Offhand-Slot und mit fehlenden Spielerpunkten. Wer die Funktionen
braucht, ist bei NMinimap besser aufgehoben — das steht auch so in der README.

## Gekillt: die vermeintlichen weiteren Rivalen
- `minimap-control` (29.474 DL, aktiv 09.05.2026) — **gegenteiliges Produkt**: es *beschränkt*
  Client-Minimap-Mods (JourneyMap, Xaero's). Kein Anbieter, sondern ein Verbieter. Nebenbefund:
  29k Downloads dafür, dass Server Minimaps überhaupt regeln wollen — die Nachfrage ist real.
- `force-fair-minimap` (2.058 DL) — dasselbe, Xaero-Beschränker.
- `voxelmap-updated` (1.309.803 DL) — **Client-Mod.** Die Loader-Liste am *Projekt* nennt
  `paper/spigot`, die **neueste Version** hingegen nur `fabric`. Genau der Messfehler, den der
  Scanner am 30.08. abgestellt hat, hier zum dritten Mal bestätigt.
- `xaero-map-spigot` (116.888 DL) — anderer Zweck (Welt-ID für Xaero-Clients), am 30.08. bereits
  gekillt, weil der Nachfolger MapModCompanion upstream lebt.

## Nachfragebeleg von der Client-Seite (unverändert gültig)
Xaero's Minimap **105.994.115 Downloads**, Xaero's World Map 92.789.383, JourneyMap 13.911.877 —
alle aktiv gepflegt. Der Bedarf ist riesig. Leer ist nur der Platz für die Lösung, die *nichts*
verlangt: keinen Client-Mod beim Spieler, keine Fremdbibliothek beim Serverbetreiber.

## Vor dem Bau bewiesen (tools/prober, 1.21.11 + 26.2)
**19 Zusicherungen zu den zwei offenen Entwurfsfragen aus M16, 19/19 grün:**
`getMap(id)` findet eine Karte wieder (also **eine ID pro Spieler, wiederverwendet — kein Leck**)
· `getMap(unbekannt)` liefert `null` statt zu werfen · `setWorld` hängt dieselbe Karte an eine
andere Welt um und der Vanilla-Renderer bleibt dran (also **eine ID reicht für alle Welten**) ·
`setLocked(false)` · `ItemStack.serializeAsBytes/deserializeBytes` überlebt Anzeigenamen und
Stapelgröße 64 · das HUD-Item trägt MapView **und** unsere Marke gleichzeitig, auch nach dem
Rundlauf durch eine Truhe · eine fremde Karte trägt die Marke **nicht** · Karten-ID und
Offhand-Speicher liegen als `INTEGER` bzw. `BYTE_ARRAY` im PDC und kommen heil zurück.

**Dazu 29 Zusicherungen gegen die ausgelieferte `dev.pocketmap.Hud`, 29/29 grün auf beiden.**

## Beim Bau gefundene und behobene Defekte
1. **Der Offhand-Speicher konnte still überschrieben werden.** `putUp` legte das Item aus der
   zweiten Hand ab, ohne zu prüfen, ob dort schon eines lag. Wer die Minimap ausschaltete,
   danach ein Totem in die Hand nahm und wieder einschaltete, hätte sein ursprüngliches Item
   **spurlos verloren** — der teuerste denkbare Fehler in diesem Plugin. `putUp` gibt einen
   vorhandenen Speicher jetzt erst zurück, bevor es neu ablegt. Die Konfigurationsoption
   `give-item-back` wurde bei der Gelegenheit **ersatzlos gestrichen**: sie konnte nur Schaden
   anrichten, und ein Serverbetreiber, der sie auf `false` stellt, verliert Spieleritems.
2. **Aus einer gesperrten Welt kam die Minimap nie zurück.** Beim Betreten einer Welt aus
   `disabled-worlds` wurde sie abgeschaltet, beim Verlassen aber nie wieder eingeschaltet — auf
   einem Server mit gesperrtem Nether wäre sie nach dem ersten Portal für immer weg gewesen.
   `PlayerChangedWorldEvent` schaltet sie jetzt in beide Richtungen.
3. **Die Vollbild-Sendung war nicht abschaltbar.** Jedes Neuzentrieren schickte 128×128 Pixel an
   den Spieler, ohne Notausgang für Server mit knapper Bandbreite. Jetzt `update.force-send`,
   und die Vorgabe für `recenter-distance` steht auf 8 statt 4 (bei 128 Blöcken Kartenbreite
   unsichtbar, halbiert aber den Verkehr).
4. **Befehlskollision mit dem aktiven Rivalen.** `/minimap` und `/mm` waren als Aliase gesetzt —
   exakt die Befehle von NMinimap. Auf einem Server mit beiden hätte das die Spieler verwirrt.
   Jetzt `/pocketmap` mit Alias `/pmap`.
