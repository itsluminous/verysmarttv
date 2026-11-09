package com.itsluminous.verysmarttv;

/**
 * uniform_rows: no dock. The grid is one flat ordered list laid out {@code columns} per row, the first row too, so
 * 7 apps per row puts 7 in the top row. The first row has no band, no empty slots and no gap of its own: Config
 * resolves fill_dock to true and rows_below_dock to 0 and turns shelf_every_row on, so every row scrolls to the same
 * line and the shelf names the focused app (or the hero_feed carousel owns it). The resting line is the shelf's
 * bottom, scaled by hero_height; the rows fill downward from it and Down scrolls. dock_apps only seeds the head of
 * the order the first time (the saved order wins once there is one); dock_band, dock_opacity, dock_glass and
 * DockDecoration are not used. The pure arithmetic lives here so it can be tested without a TV.
 */
final class UniformRows {
    private UniformRows() { }

    /** Room the shelf keeps above the first row: the 40dp gap of the dock layout (MainActivity's shelf clearance). */
    static final float SHELF_GAP_DP = 40f;

    /** The key only applies to the dock_row layout; a single row has one row already. */
    static boolean on(boolean uniformRows, boolean dockRow, boolean singleRow) {
        return uniformRows && dockRow && !singleRow;
    }

    /** First position of the row {@code position} is in. */
    static int rowStart(int position, int columns) {
        return position - position % columns;
    }

    /** Row index of a position: 0 is the first row, the one the dock used to be. */
    static int rowOf(int position, int columns) {
        return position / columns;
    }

    /**
     * Tiles in the row {@code position} is in: {@code columns}, or what is left for a short last row. One dot each.
     * {@code itemCount} is the apps (AppAdapter.appCount), not the adapter's item count: the All apps row the library
     * appends is an item of its own under the last row and has no dot.
     */
    static int rowCount(int position, int columns, int itemCount) {
        return Math.max(0, Math.min(columns, itemCount - rowStart(position, columns)));
    }

    /**
     * The shelf's room above the first row, px: the taller of the measured shelf and the carousel's full height, plus
     * the gap, never less than the title's room (AppAdapter.TITLE_ROOM_DP) so a shelf measured at 0 on the first pass
     * cannot pull the rows up into the header.
     */
    static int shelfRoomPx(int heroMeasuredPx, int carouselRoomPx, float density) {
        int room = Math.max(heroMeasuredPx, carouselRoomPx) + Math.round(SHELF_GAP_DP * density);
        return Math.max(room, Math.round(AppAdapter.TITLE_ROOM_DP * density));
    }

    /** The grid's top padding, the line the first row rests on and every row scrolls to: the shelf's room at hero_height. */
    static int restingLinePx(int shelfRoomPx, int heroHeightPct) {
        return Math.max(0, Math.round(shelfRoomPx * heroHeightPct / 100f));
    }
}
