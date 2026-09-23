package xyz.iwolfking.woldsvaults.pouch.menu;

/** Logical pixels shared by the screen, real slots and layout assertions. */
public final class PouchLayout {
    public static final int WIDTH = 288;
    public static final int HEIGHT = 198;
    public static final int TAB_Y = 5;
    public static final int TAB_WIDTH = 22;
    public static final int TAB_HEIGHT = 22;
    public static final int TAB_GAP = 2;
    public static final int GRID_X = (WIDTH - 162) / 2;
    public static final int GRID_Y = 24;
    public static final int INVENTORY_Y = 94;
    public static final int HOTBAR_Y = 154;
    public static final int COLLECTION_X = 11;
    public static final int COLLECTION_Y = 52;
    public static final int COLUMN_COUNT = 8;
    public static final int ROW_COUNT = 5;
    public static final int CELL_SIZE = 20;
    public static final int CELL_PITCH = 20;
    public static final int ROW_PITCH = 23;
    public static final int COLLECTION_SCROLL_HEIGHT = 115;
    public static final int VISIBLE_CELLS = COLUMN_COUNT * ROW_COUNT;
    public static final int PREVIEW_COLUMNS = 8;
    public static final int PREVIEW_ROWS = 4;
    public static final int PREVIEW_X = 100;
    public static final int PREVIEW_Y = 53;
    public static final int PREVIEW_PITCH = 21;
    public static final int PREVIEW_PANEL_HEIGHT = 102;
    public static final int PREVIEW_SCROLL_HEIGHT = 88;

    public static final int FOOTER_Y = 178;

    public static int tabY(int index) { return TAB_Y + index * (TAB_HEIGHT + TAB_GAP); }
    public static int panelLeft(int screenWidth) { return (screenWidth - WIDTH + TAB_WIDTH) / 2; }
    public static int panelTop(int screenHeight, int panelHeight) { return (screenHeight - panelHeight) / 2; }
    private PouchLayout() {}
}
