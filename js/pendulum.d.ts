/**
 * Pendulum Minecraft API Type Declarations
 * 
 * Drop this file into your .minecraft/pendulum/ folder or configure
 * your editor's jsconfig.json / tsconfig.json to include it.
 * 
 * Usage in VS Code:
 *   // Add to jsconfig.json:
 *   { "compilerOptions": { "types": ["./pendulum.d.ts"] } }
 */

// ==================== Item ====================

interface PendulumItem {
  id: string;
  count: number;
  maxCount: number;
  durability: number;
  maxDurability: number;
  name: string;
  /** Only present in getAllItems() responses */
  slot?: number;
}

// ==================== World Types ====================

interface PendulumBlockPos {
  x: number;
  y: number;
  z: number;
}

interface PendulumBlockPosWithId extends PendulumBlockPos {
  block?: string;
}

interface PendulumBlockState {
  id: string;
  properties: Record<string, string>;
}

interface PendulumEntity {
  name: string;
  type: string;
  x: number;
  y: number;
  z: number;
  distance: number;
  /** LivingEntity only */
  health?: number;
  maxHealth?: number;
  isAlive?: boolean;
}

interface PendulumPlayer {
  name: string;
  x: number;
  y: number;
  z: number;
  distance: number;
}

interface PendulumRayTraceBlock {
  type: "block";
  x: number;
  y: number;
  z: number;
  face: string;
  distance: number;
}

interface PendulumRayTraceEntity {
  type: "entity";
  x: number;
  y: number;
  z: number;
  entityName: string;
  entityType: string;
  distance: number;
}

interface PendulumRayTraceMiss {
  type: "miss";
}

type PendulumRayTrace = PendulumRayTraceBlock | PendulumRayTraceEntity | PendulumRayTraceMiss;

// ==================== GUI Types ====================

interface PendulumWidget {
  type: string;
  text?: string;
  x?: number;
  y?: number;
  width?: number;
  height?: number;
  active?: boolean;
  focused?: boolean;
  children?: PendulumWidget[];
}

// ==================== mc.player ====================

interface PendulumPlayerAPI {
  // Movement
  /** Walk forward. No arg = hold, ticks = walk N ticks then auto-stop */
  forward(ticks?: number): void;
  /** Walk backward */
  back(ticks?: number): void;
  /** Strafe left */
  left(ticks?: number): void;
  /** Strafe right */
  right(ticks?: number): void;
  /** Release ALL movement keys (forward/back/left/right/jump/sneak) */
  stop(): void;

  // Vertical
  /** Jump once. jump(true) for continuous bouncing. */
  jump(hold?: boolean): void;
  /** Sneak. sneak(false) to stop. */
  sneak(hold?: boolean): void;
  /** Start sprinting */
  sprint(hold?: boolean): void;
  /** Stop sprinting specifically */
  stopSprint(): void;

  // Rotation
  /** Face a coordinate immediately */
  lookAt(x: number, y: number, z: number): void;
  /** Set yaw (horizontal rotation) in degrees. 0=south, 90=west, 180=north, -90=east */
  setYaw(yaw: number): void;
  /** Set pitch (vertical rotation) in degrees. -90=up, 90=down */
  setPitch(pitch: number): void;
  /** Current yaw in degrees */
  getYaw(): number;
  /** Current pitch in degrees */
  getPitch(): number;

  // Position
  /** Player X coordinate */
  getX(): number;
  /** Player Y coordinate (feet) */
  getY(): number;
  /** Player Z coordinate */
  getZ(): number;

  // Player State
  /** Current HP (0-20) */
  getPlayerHealth(): number;
  /** Food level (0-20) */
  getPlayerHunger(): number;
  /** Armor value */
  getPlayerArmor(): number;
  /** Attack cooldown (0.0-1.0) */
  getAttackCooldown(): number;
  /** Reach distance in blocks */
  getReachDistance(): number;
  /** Distance-based reach check to (x,y,z) */
  canReach(x: number, y: number, z: number): boolean;
  /** Line-of-sight check (no obstructions on ray) */
  canSeeBlock(x: number, y: number, z: number): boolean;

  // Interaction
  /** Left-click attack (void). Requires allowAttack permission. */
  attack(): void;
  /** Single right-click */
  use(): void;
  /** Hold right-click for N ticks then auto-release (eat/bow/shield) */
  useItem(ticks?: number): boolean;
  /** Start holding right-click (pair with stopUse) */
  startUse(): void;
  /** Release right-click */
  stopUse(): void;

  /** Break block at crosshair — waits until done. Prefer breakBlockAt for precision. */
  breakBlock(): boolean;
  /** Break block at specific coordinate (recommended) */
  breakBlockAt(x: number, y: number, z: number): boolean;
  /** Place on crosshair target (unreliable — prefer placeBlockAt) */
  placeBlock(slot?: number): boolean;
  /** Place at exact coordinate (recommended) */
  placeBlockAt(x: number, y: number, z: number, slot?: number): boolean;
  /** Jump up and place block under feet. Returns success. */
  jumpAndPlaceBelow(): boolean;

  /** Swap main ↔ offhand */
  swapHands(): void;
  /** Drop 1 from held stack */
  drop(): void;
  /** Drop entire held stack */
  dropAll(): void;
  /** Pick block (middle-click) */
  pickBlock(): void;
}

// ==================== mc.world ====================

interface PendulumWorldAPI {
  // Block Queries
  /** Get block ID at (x,y,z). Returns "air" if world is null. */
  getBlock(x: number, y: number, z: number): string;
  /** Check if block at (x,y,z) matches given ID */
  isBlock(x: number, y: number, z: number, blockId: string): boolean;
  /** Check if block at (x,y,z) matches given tag */
  isBlockByTag(x: number, y: number, z: number, tag: string): boolean;
  /** Get full block state: {id, properties: {facing: "north", ...}} */
  getBlockState(x: number, y: number, z: number): PendulumBlockState | null;

  // Block Search
  /** Find blocks by ID in sphere around player (default radius=16) */
  findBlocks(blockId: string, radius?: number): PendulumBlockPos[];
  /** Find blocks by tag in sphere around player */
  findBlocksByTag(tag: string, radius?: number): PendulumBlockPos[];
  /** Find blocks in box area. No blockId = all non-air blocks. */
  findBlocksInBox(x1: number, y1: number, z1: number, x2: number, y2: number, z2: number, blockId?: string): PendulumBlockPosWithId[];

  // Crosshair
  /** Is crosshair on this block? */
  facingBlock(blockId: string): boolean;
  /** Is crosshair on this entity type? */
  facingEntity(entityType: string): boolean;
  /** Get block ID the player is looking at, or "" */
  getFacingBlock(): string;
  /** Get info about the entity at crosshair */
  getLookingEntity(): PendulumEntity | null;

  // Entities
  /** Get nearby players within radius */
  getNearbyPlayers(radius: number): PendulumPlayer[];
  /** Get nearby non-player entities */
  getNearbyEntities(radius: number, typeFilter?: string): PendulumEntity[];

  // Ray Trace
  /** Cast ray from player eyes, return first hit (entities before blocks) */
  rayTrace(maxDist?: number): PendulumRayTrace;

  // Environment
  /** Get biome at coordinates, e.g. "minecraft:plains" */
  getBiomeAt(x: number, y: number, z: number): string;
  /** Get light level (0-15) */
  getLightLevel(x: number, y: number, z: number): number;
  /** Get difficulty: "peaceful" | "easy" | "normal" | "hard" */
  getDifficulty(): string;
  /** Get dimension: "minecraft:overworld" | "minecraft:the_nether" | "minecraft:the_end" */
  getDimension(): string;
}

// ==================== mc.inv ====================

interface PendulumInventoryAPI {
  // Hotbar
  /** Select hotbar slot (1-9) */
  selectHotbar(slot: number): void;
  /** Current hotbar slot number (1-9) */
  getSelectedSlot(): number;

  // Query
  /** Check if player has at least N items of given ID */
  hasItem(itemId: string, required?: number): boolean;
  /** Get item info for inventory slot (0-35) */
  getItemInSlot(slot: number): PendulumItem | null;
  /** Get held item details */
  getItemInHand(): PendulumItem | null;
  /** Get offhand item details */
  getItemOffhand(): PendulumItem | null;
  /** Get all non-empty inventory slots */
  getAllItems(): PendulumItem[];

  // Container
  /** Total slots in open container */
  getContainerSize(): number;
  /** Get item at container slot */
  getContainerItem(slot: number): PendulumItem | null;
  /** Get all non-empty container slots */
  getContainerAllItems(): PendulumItem[];
  /** Container type: "chest"|"crafting_table"|"furnace"|"enchanting"|"anvil"|"container"|"none"|"unknown" */
  getContainerType(): string;

  // Slot Clicking
  /** Left-click container slot */
  clickSlot(slotId: number, button?: number): void;
  /** Right-click container slot */
  clickSlotRight(slotId: number): void;
  /** Move item (left-click from, then left-click to) */
  moveItem(fromSlot: number, toSlot: number): void;
  /** Shift+click to quick-move item */
  quickMoveItem(slotId: number): void;

  // Crafting
  /** Click result slot (craft 1) */
  craft(): void;
  /** Shift-click result slot (craft max) */
  craftAll(): void;
}

// ==================== mc.gui ====================

interface PendulumGuiAPI {
  // Screen Info
  /** Is any GUI screen open? */
  isOpen(): boolean;
  /** Current screen title, "" if none */
  getTitle(): string;
  /** Close current screen */
  close(): void;
  /** Open chat input */
  openChat(): void;

  // Widget Enumeration
  /** Recursively enumerate ALL GUI widgets */
  getElements(): PendulumWidget[];

  // Mouse Input
  /** Click at screen coordinates. button: "left" (default), "right", "middle" */
  click(x: number, y: number, button?: "left" | "right" | "middle"): void;
  /** Find button by text substring and click. Returns JSON result string. */
  clickButton(target: string): string;
  /** Drag mouse from (x1,y1) to (x2,y2) */
  mouseDrag(x1: number, y1: number, x2: number, y2: number, button?: "left" | "right" | "middle"): void;
  /** Scroll mouse wheel. Positive=up, negative=down. */
  scroll(clicks: number): void;

  // Keyboard Input
  /** Press and release a key. holdSeconds for sustained press. */
  pressKey(key: string, holdSeconds?: number): void;
  /** Type text character by character. pressEnter to hit Enter after. */
  typeText(text: string, pressEnter?: boolean): void;
  /** Fast text input (same as typeText). */
  pasteText(text: string, pressEnter?: boolean): void;
  /** Press key combination, e.g. hotkey("ctrl,s") */
  hotkey(keys: string): void;

  // Container Slots
  /** Left-click container slot */
  clickSlot(slotId: number, button?: number): void;
  /** Right-click container slot */
  clickSlotRight(slotId: number): void;
  /** Craft 1 */
  craft(): void;
  /** Craft max */
  craftAll(): void;

  /** Total slots in container */
  getSlotCount(): number;
  /** Get item at container slot */
  getSlotItem(slot: number): PendulumItem | null;
  /** Get all non-empty container slots */
  getAllItems(): PendulumItem[];
  /** Container type name */
  getType(): string;
  /** Move item between slots */
  moveItem(fromSlot: number, toSlot: number): void;
  /** Shift+click (quick transfer) */
  quickMoveItem(slotId: number): void;

  // Control / Advanced
  /** Wait N seconds */
  wait(seconds?: number): void;
  /** ⚠️ Call an arbitrary no-arg method on the current screen via reflection */
  callMethod(methodName: string): string;
  /** Select a dropdown list item by text substring */
  selectListItem(text: string): string;
}

// ==================== mc (root) ====================

interface PendulumMC {
  player: PendulumPlayerAPI;
  world: PendulumWorldAPI;
  inv: PendulumInventoryAPI;
  gui: PendulumGuiAPI;

  /** Send a public chat message. Requires allowSay permission. */
  say(message: string): void;
  /** Log to action bar with [Pendulum] prefix */
  log(message: string): void;
  /** Execute a client-side command. Requires allowExecuteCommand permission. */
  executeCommand(command: string): void;
  /** Pause script execution for N game ticks (default 1) */
  waitTick(ticks?: number): void;
  /** Run another script file from .minecraft/pendulum/ */
  execFile(relativePath: string): void;
  /** Get absolute path to .minecraft/pendulum/ */
  getScriptDir(): string;
  /** Display full API help in chat */
  help(): void;
}

// ==================== br / baritone ====================

interface PendulumBaritone {
  // Pathing & Movement
  /** Pathfind to coordinates */
  goto(x: number, y: number, z: number): void;
  /** Set goal (use path() to start) */
  goal(x?: number, y?: number, z?: number): void;
  /** Start pathing to current goal */
  path(): void;
  /** Come to player camera position */
  come(): void;
  /** Return to surface (highest air space) */
  surface(): void;
  /** Go to nearest axis X=0 or Z=0 */
  axis(): void;
  /** Go in facing direction */
  thisWay(): void;
  /** Elytra flight mode */
  elytra(): void;

  // Mining & Digging
  /** Automated mining with optional count */
  mine(blockId: string, count?: number): void;
  /** Dig a 1×2 tunnel */
  tunnel(): void;

  // Farming & Exploration
  /** Auto-harvest crops (default range=100) */
  farm(range?: number): void;
  /** Explore new chunks */
  explore(): void;
  /** Walk to nearest instance of a block */
  getToBlock(blockId: string): void;

  // Following & Items
  /** Follow entity type. No arg = follow any player. */
  follow(entityType?: string): void;
  /** Collect nearby dropped items */
  pickup(): void;
  /** Click destination (must face target first) */
  click(): void;

  // Building
  /** Build from schematics file */
  build(schematicName: string, x?: number, y?: number, z?: number): void;
  /** Build current Litematica schematic */
  litematica(): void;

  // Control
  /** Cancel everything */
  stop(): void;
  /** Same as stop() */
  cancel(): void;
  /** Force cancel */
  forceCancel(): void;
  /** Pause current task */
  pause(): void;
  /** Resume paused task */
  resume(): void;
  /** Is pathing or working? */
  isActive(): boolean;
  /** Is paused? */
  isPaused(): boolean;
  /** Alias for isPaused() */
  paused(): boolean;

  // Selections
  /** Box selection */
  select(x1: number, y1: number, z1: number, x2: number, y2: number, z2: number): void;
  /** Clear all selections */
  clearSelection(): void;
  /** Set pos1 to current position (or coordinates) */
  selPos1(x?: number, y?: number, z?: number): void;
  /** Set pos2 to current position (or coordinates) */
  selPos2(x?: number, y?: number, z?: number): void;

  // Settings & Commands
  /** Execute any baritone command string */
  command(rawCommand: string): void;
  /** Toggle baritone setting */
  setting(key: string, value: unknown): void;
  /** Search world cache for block */
  find(blockId: string): void;
  /** Blacklist crosshair block from pathfinding */
  blacklist(): void;

  // Waypoints & Home
  /** Save current position as waypoint */
  waypointSave(name: string): void;
  /** List all waypoints */
  waypointList(): void;
  /** Delete waypoint by name */
  waypointDelete(name: string): void;
  /** Set home position */
  sethome(): void;
  /** Pathfind to home */
  home(): void;

  // Info & Tools
  /** Show current process info */
  proc(): void;
  /** Estimated time to arrival */
  eta(): void;
  /** Baritone version */
  version(): void;
  /** Re-cache surrounding chunks */
  repack(): void;
  /** Garbage collect / release memory */
  gc(): void;
  /** Invert goal (go away from target) */
  invert(): void;
  /** Fix glitched chunk rendering */
  render(): void;
  /** Reload world cache */
  reloadAll(): void;
  /** Save world cache to disk */
  saveAll(): void;

  /** Display Baritone API help */
  help(): void;
}

// ==================== Global Declarations ====================

/**
 * Main Minecraft API.
 * Domain functions are grouped:
 *   mc.player.* — movement, rotation, interaction, player state
 *   mc.world.* — block/entity/environment queries
 *   mc.inv.*   — inventory & container
 *   mc.gui.*   — screen-level GUI interaction
 *   mc.say/log/executeCommand/waitTick/execFile/getScriptDir/help — root utilities
 */
declare const mc: PendulumMC;
declare const minecraft: PendulumMC;
declare const game: PendulumMC;

/**
 * Baritone API (requires Baritone mod).
 * Throws if Baritone is not installed.
 */
declare const br: PendulumBaritone;
declare const baritone: PendulumBaritone;

/**
 * Console logging.
 * console.log(...) outputs to both the Minecraft log AND the MCP eval return.
 */
declare const console: {
  log(...args: unknown[]): void;
};
