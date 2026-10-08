package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Picture;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.ImageRegion;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.input.CursorShape;
import dev.vexelray.gui.core.input.InteractionState;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Relief;
import dev.vexelray.gui.core.style.Role;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The viewer's filmstrip: the shown image in the middle and its neighbours either side, each a {@link Tile}-sized
 * square that shows that image when clicked. The shown one is edged in the accent.
 *
 * <p><b>Thumbnails are what is already decoded.</b> The strip asks for nothing itself: it shows the dock-sized picture
 * {@link Previewer} keeps for a neighbour, and an empty square until there is one. The previewer reads the neighbours
 * ahead while the viewer is open and says when each lands, and the viewer then calls {@link #show} again.
 *
 * <p><b>Squares never move.</b> A slot past either end of the folder stays in its place, empty and inert, so stepping to
 * the first or last image does not slide the strip under the pointer. How many slots show is decided by the width the
 * bar has ({@link #fit}), not by where in the folder the user is.
 */
final class Filmstrip {

    /** The most slots: the shown image and three either side. */
    static final int MOST = 7;

    private static final Length INSET = Length.rem(0.3125f);
    private static final Length INNER_CORNER = Length.rem(0.8125f);

    private final Gui gui;
    private final Textures textures;
    private final Function<Path, Picture> thumbnail;
    private final Consumer<Path> pick;
    private final Node[] slots = new Node[MOST];
    private final Node[] plates = new Node[MOST];
    private final InteractionState[] states = new InteractionState[MOST];

    // Guarded by this.
    private final Path[] paths = new Path[MOST];
    private final Picture[] pictures = new Picture[MOST];
    private final int[] generations = new int[MOST];
    private int count = MOST;
    private List<Path> folder = List.of();
    private int at = -1;

    /**
     * @param thumbnail the picture already decoded for an image, or null; called off the GUI thread
     * @param pick      show this image
     */
    Filmstrip(Gui gui, Textures textures, Function<Path, Picture> thumbnail, Consumer<Path> pick) {
        this.gui = gui;
        this.textures = textures;
        this.thumbnail = thumbnail;
        this.pick = pick;
        for (int i = 0; i < MOST; i++) {
            int slot = i;
            states[i] = InteractionState.NORMAL;
            plates[i] = gui.box().size(Length.FILL, Length.FILL).corner(INNER_CORNER).hitInert(true);
            slots[i] = gui.box().role("button").size(Tile.SIDE, Tile.SIDE).padding(INSET).corner(Tile.CORNER)
                    .scroll(false, false).children(plates[i]);
            gui.onClick(slots[i], () -> press(slot));
            gui.onState(slots[i], s -> {
                states[slot] = s;
                paint(slot);
            });
            paint(i);
        }
    }

    /** The slots, in order, for the caller to put between its Previous and Next. */
    Node[] nodes() {
        return slots.clone();
    }

    /** Show {@code folder}'s images around the one at {@code at} (or nothing shown, at -1). */
    synchronized void show(List<Path> folder, int at) {
        this.folder = List.copyOf(folder);
        this.at = at;
        for (int i = 0; i < MOST; i++) {
            Path p = pathAt(i);
            Picture pic = p == null ? null : thumbnail.apply(p);
            if (Objects.equals(p, paths[i]) && pic == pictures[i]) {
                continue;
            }
            paths[i] = p;
            pictures[i] = pic;
            int g = ++generations[i];
            Node plate = plates[i];
            plate.image(null);
            if (pic != null) {
                ImageRegion crop = cover(pic);
                int slot = i;
                textures.show(pic, texture -> {
                    synchronized (this) {
                        if (generations[slot] == g) {
                            plate.image(texture, crop);
                        }
                    }
                });
            }
            paint(i);
        }
    }

    /** Show {@code wanted} slots (odd, at most {@link #MOST}), centred on the shown image. Zero hides the strip. */
    synchronized void fit(int wanted) {
        int n = Math.max(0, Math.min(MOST, wanted));
        if (n > 0 && n % 2 == 0) {
            n--;
        }
        if (n == count) {
            return;
        }
        count = n;
        for (int i = 0; i < MOST; i++) {
            slots[i].visible(Math.abs(i - MOST / 2) <= (n - 1) / 2 && n > 0);
        }
    }

    /** The image slot {@code i} shows, or null past either end. Holding this. */
    private Path pathAt(int i) {
        if (at < 0) {
            return null;
        }
        int j = at + i - MOST / 2;
        return j >= 0 && j < folder.size() ? folder.get(j) : null;
    }

    private void press(int slot) {
        Path p;
        synchronized (this) {
            p = paths[slot];
        }
        if (p != null && slot != MOST / 2) {
            pick.accept(p);
        }
    }

    private void paint(int slot) {
        Path p;
        synchronized (this) {
            p = paths[slot];
        }
        boolean here = slot == MOST / 2 && p != null;
        boolean live = p != null && !here;
        InteractionState s = live ? states[slot] : InteractionState.NORMAL;
        Node n = slots[slot];
        gui.focusable(n, false);
        gui.cursor(n, live ? CursorShape.POINTER : CursorShape.DEFAULT);
        if (p == null) {
            n.background(gui.theme().color(Role.CHROME)).border(Length.dp(1), gui.theme().color(Role.LINE))
                    .lit(false).elevation(Length.ZERO);
            plates[slot].background(gui.theme().color(Role.NONE));
            return;
        }
        n.background(gui.theme().color(here ? Role.ACCENT : Role.RAISED, s))
                .border(Length.dp(1), gui.theme().color(here ? Look.ENTROPY_HIGH : Look.LINE_STRONG))
                .lit(true).elevation(gui.theme().elevation(Relief.RAISED, s));
        plates[slot].background(gui.theme().color(Role.WELL));
    }

    /** The middle square of the first frame: a thumbnail fills its square, cropped rather than letterboxed. */
    static ImageRegion cover(Picture p) {
        float sw = p.sheetWidth();
        float sh = p.sheetHeight();
        float side = Math.min(p.frameWidth(), p.frameHeight());
        float x = (p.frameWidth() - side) / 2f;
        float y = (p.frameHeight() - side) / 2f;
        return new ImageRegion(x / sw, y / sh, (x + side) / sw, (y + side) / sh);
    }
}
