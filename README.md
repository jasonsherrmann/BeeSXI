
# BeeSXI

BeeSXI is a NeoForge mod for Minecraft 1.21.1 that adds a BeeSXI Server multiblock for Forestry-style bee automation.

Instead of physical apiaries, BeeSXI lets you analyze bees, unlock species traits, and run virtual hives that consume RF and produce Forestry products.

## Current Features

- Dynamic BeeSXI Server multiblock (3x3x3 up to 15x15x15)
- Controller can be on any outside face as long as it is not on an edge/corner

- GUI tabs:
	- Analysis
	- Virtual Hives
	- Inventory
	- Info (dimensions and block counts)
	- Bee Species: shows analyzed Bee Species
	- Flowers: shows analyzed flowers
	- Biomes: shows analyzed biomes 

- Paper data-card workflow:
	- Analyze plain paper to export an Export Report containing the analyzed bee specimens, species, flowers and biomes
	- Analyze an Export Report to import/unlock the saved specimen data instantly into another Controller (useful for if you build more than one)

## Multiblock Rules

- Structure dimensions must be between 3 and 15 on each axis
- The entire rectangular volume must be filled with BeeSXI blocks
- Edges and corners must be casing
- Controller must be on an outer face and not on an edge/corner
- Required parts:
	- exactly 1 Controller
	- at least 1 CPU
	- at least 1 RAM
	- at least 1 Power Supply
	- exactly 1 Molecular Analyzer
- Optional parts:
	- Battery 
	- Export Bus


## Gameplay Flow

1. Obtain Weather Reports: Craft a Weather Reporter, bring it to the desired biome, power it and put in paper, after some time a Weather Report will be created.
2. Breed the 4 new bee species, Faber, Fervid, Gelus, Memento
3. Build a valid BeeSXI multiblock and power it.
4. Insert a bee in the analyzer and run analysis to unlock it.
5. Analyze a flower and a biome Weather Report. 
6. Set the species, biome and flower in the Virtual Hives tab.
7. Collect products from the controller Inventory tab or export using an Export Bus.

## Dependencies

- Minecraft 1.21.1
- NeoForge
- Forestry: Community Edition

## License

This project is licensed under the GNU General Public License v3.0 only (GPL-3.0-only).

See the LICENSE file for full terms.

Special Thanks: 
EcksOdinson
TheDarkColour
IMakeBadChoices