package org.mydreamduels.dreamcoinshop.screen;

/**
 * Small pictures for the leaderboard screens, drawn here in code (original art): a trophy for
 * the title and a plain face for players whose skin hasn't loaded yet. 16x16 / 8x8 RGB, -1 = empty.
 */
public final class BoardArt {

    private BoardArt() {
    }

    private static final String[] TROPHY = {
            "................",
            "...OOOOOOOOOO...",
            ".OOGGGGGGGGHGOO.",
            "OG.OGGGGGGGHGO.GO",
            "OG.OGGGGGGHGGO.GO",
            ".OGOGGGGGGHGGOGO.",
            "..OOGGGGGGGGGOO..",
            "....OGGGGGGGO....",
            ".....OGGGGGO.....",
            "......OGGGO......",
            ".......OGO.......",
            ".......OGO.......",
            ".....OOGGGOO.....",
            "....OBBBBBBBO....",
            "....OBBBBBBBO....",
            "....OOOOOOOOO....",
    };

    /** A gold cup with handles on a dark base. */
    public static int[] trophy() {
        int[] px = new int[256];
        for (int y = 0; y < 16; y++) {
            String row = TROPHY[y];
            for (int x = 0; x < 16; x++) {
                char ch = x < row.length() ? row.charAt(x) : '.';
                px[y * 16 + x] = switch (ch) {
                    case 'O' -> 0x3A2A08;
                    case 'G' -> 0xF2B82E;
                    case 'H' -> 0xFFF0A8;
                    case 'B' -> 0x5A4A3A;
                    default -> -1;
                };
            }
        }
        return px;
    }

    /** A plain face (skin not loaded / unknown player): grey skin, dark eyes and mouth. */
    public static int[] defaultFace() {
        int[] px = new int[64];
        for (int i = 0; i < 64; i++) px[i] = (i / 8) < 2 ? 0x3B3F4A : 0x9AA1AD;  // hair band + skin
        px[3 * 8 + 1] = px[3 * 8 + 6] = 0x3B3F4A;                                // brows corners
        px[4 * 8 + 2] = px[4 * 8 + 5] = 0xF4F6FA;                                // eye whites
        px[4 * 8 + 1] = px[4 * 8 + 6] = 0x2B2F3A;                                // pupils
        px[6 * 8 + 3] = px[6 * 8 + 4] = 0x5C626E;                                // mouth
        return px;
    }

    /** RGB pixels scaled (nearest) to size x size map colours; -1 pixels stay transparent. */
    public static byte[] toMap(int[] src, int srcSize, int size) {
        byte[] out = new byte[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int c = src[(y * srcSize / size) * srcSize + (x * srcSize / size)];
                out[y * size + x] = c < 0 ? Palette.TRANSPARENT : Palette.of(c);
            }
        }
        return out;
    }
}
