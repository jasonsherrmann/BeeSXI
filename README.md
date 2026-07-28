
# BeeSXI

BeeSXI is a NeoForge mod for Minecraft 1.21.1 that adds a BeeSXI Server multiblock for Forestry-style bee automation.

Instead of physical apiaries, BeeSXI lets you analyze bees, unlock species traits, and run virtual hives that consume RF and produce Forestry products.

## Current Features

- Dynamic BeeSXI Server multiblock (3x3x3 up to 15x15x15)
- Controller can be on any outside face as long as it is not on an edge/corner
- Assembled-state visuals for controller and part blocks
- Molecular Analyzer workflow with timed, per-tick RF drain
- Paper data-card workflow:
	- Analyze plain paper to export unlocked species (0 RF)
	- Analyze species card paper to import/unlock species instantly (0 RF)
- Virtual hives driven by CPU lines and total RAM budget
- Per-instance RF/t usage for virtual hive operation
- Activity-aware production behavior (diurnal/nocturnal reduced rate)
- Power network support with Power Supply and Battery parts
- HDD-backed network inventory (type/byte model) shown through controller UI
- RF/t breakdown shown in GUI (total + instance + analysis)
- Creative tab containing all BeeSXI blocks/items
- Controller diagnostics when multiblock is incomplete:
	- chat output with dimensions
	- missing/invalid block details
- GUI tabs:
	- Analysis
	- Virtual Hives
	- Inventory
	- Info (dimensions and block counts)

## Multiblock Rules

- Structure dimensions must be between 3 and 15 on each axis
- The entire rectangular volume must be filled with BeeSXI blocks
- Edges and corners must be casing
- Controller must be on an outer face and not on an edge/corner
- Required parts:
	- at least 1 CPU
	- at least 1 RAM
	- at least 1 HDD
	- at least 1 Power Supply
- Optional parts:
	- Battery (recommended for energy buffering)
- Molecular Analyzer is optional but required for analysis actions

## Gameplay Flow

1. Build and form a valid BeeSXI multiblock.
2. Insert a bee in the analyzer and run analysis to unlock species traits.
3. Optionally export/import unlocks using paper species cards.
4. Configure virtual hive lines in the Virtual Hives tab.
5. Keep the structure powered; instances and analysis consume RF gradually per tick.
6. Collect products from the controller Inventory tab (HDD network view).

## Dependencies

- Minecraft 1.21.1
- NeoForge
- Forestry: Community Edition

## License

This project is licensed under the GNU General Public License v3.0 only (GPL-3.0-only).

See the LICENSE file for full terms.

Special Thanks: 
EcksOdinson 