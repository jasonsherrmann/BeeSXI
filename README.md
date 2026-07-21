
# BeeSXI

BeeSXI is a NeoForge mod for Minecraft 1.21.1 that adds a BeeSXI Server multiblock for Forestry-style bee automation.

Instead of running physical apiaries, BeeSXI lets you analyze bees, unlock species, and run virtual hive lines backed by server resources.

## Features

- 5x5x5 BeeSXI Server multiblock
- Controller can be placed on any non-edge outer block position
- Required parts validation (CPU, RAM, HDD) with assembled-state visuals
- Molecular Analyzer integration for species unlocks
- Virtual hive lines tied to CPU count
- Per-line instance limits tied to RAM capacity
- Product generation based on species products and specialties
- HDD-backed output storage with controller inventory fallback
- Custom tabbed GUI for Analysis, Virtual Hives, and Inventory

## Multiblock Rules (Current)

- Structure size must be exactly 5x5x5
- All positions in the 5x5x5 volume must be BeeSXI parts or controller
- Edges and corners must be casing
- Controller must be on an outside face and not on an edge/corner
- At least 1 CPU, 1 RAM, and 1 HDD are required

## Dependencies

- Minecraft 1.21.1
- NeoForge (see version in gradle.properties)
- Forestry: Community Edition (runtime dependency provided by your modpack/instance)


## License

This project is licensed under the GNU General Public License v3.0 only (GPL-3.0-only).

See the LICENSE file for full terms.
