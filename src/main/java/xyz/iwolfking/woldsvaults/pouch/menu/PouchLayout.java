package xyz.iwolfking.woldsvaults.pouch.menu;

/** Logical pixels shared by the screen, real slots and layout assertions. */
public final class PouchLayout {
    public static final int WIDTH = 304;
    public static final int HEIGHT = 236;
    public static final int TAB_Y = 34;
    public static final int TAB_WIDTH = 94;
    public static final int TAB_HEIGHT = 18;
    public static final int TAB_GAP = 3;
    public static final int GRID_X = (WIDTH - 162) / 2;
    public static final int GRID_Y = 66;
    public static final int INVENTORY_Y = 138;
    public static final int HOTBAR_Y = 198;
    public static final int COLLECTION_X = 11;
    public static final int COLLECTION_Y = 100;
    public static final int COLUMN_COUNT = 8;
    public static final int ROW_COUNT = 5;
    public static final int CELL_SIZE = 20;
    public static final int CELL_PITCH = 21;
    public static final int ROW_PITCH = 22;
    public static final int COLLECTION_SCROLL_HEIGHT = 110;
    public static final int VISIBLE_CELLS = COLUMN_COUNT * ROW_COUNT;
    public static final int PREVIEW_COLUMNS = 8;
    public static final int PREVIEW_ROWS = 4;
    public static final int PREVIEW_X = 106;
    public static final int PREVIEW_Y = 101;
    public static final int PREVIEW_PITCH = 22;
    public static final int PREVIEW_PANEL_HEIGHT = 96;
    public static final int PREVIEW_SCROLL_HEIGHT = 88;

    public static int tabX(int index) { return 8 + index * (TAB_WIDTH + TAB_GAP); }
    public static int panelTop(int screenHeight, int panelHeight) { return (screenHeight - panelHeight) / 2; }
    private PouchLayout() {}
}
