package dev.sibarum.vexplore.linux;

import dev.sibarum.vexplore.spi.FilePlatform.Place;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LinuxFilePlatformTest {

    private final LinuxFilePlatform platform = new LinuxFilePlatform();

    @Test
    void theRootHasANameOfItsOwn() {
        assertEquals("File System", platform.rootName(Path.of("/")));
    }

    @Test
    void theUserFoldersAreTheOnesTheDesktopNames(@TempDir Path home) throws Exception {
        Files.createDirectories(home.resolve("Bilder"));
        Files.createDirectories(home.resolve("Downloads"));
        Files.createDirectories(home.resolve("Musik"));
        Path dirs = Files.writeString(home.resolve("user-dirs.dirs"), """
                # written by xdg-user-dirs-update
                XDG_DESKTOP_DIR="$HOME/"
                XDG_PICTURES_DIR="$HOME/Bilder"
                XDG_MUSIC_DIR="$HOME/Musik"
                """);
        // Desktop is turned off ($HOME), Documents and Videos do not exist, Downloads is the usual folder.
        assertEquals(List.of("Downloads", "Musik", "Bilder"),
                LinuxFilePlatform.userFolders(home, dirs).stream().map(Place::label).toList());
    }

    @Test
    void withoutTheDesktopsFileTheUsualFolders(@TempDir Path home) throws Exception {
        Files.createDirectories(home.resolve("Documents"));
        assertEquals(List.of(new Place(home.resolve("Documents"), "Documents")),
                LinuxFilePlatform.userFolders(home, home.resolve("absent")));
    }

    @Test
    void theRootThenTheVolumesMountedForAPerson(@TempDir Path dir) throws Exception {
        Path mounts = Files.writeString(dir.resolve("mounts"), """
                sysfs /sys sysfs rw 0 0
                /dev/sda2 / ext4 rw 0 0
                vmhgfs-fuse /mnt/hgfs fuse.vmhgfs-fuse rw 0 0
                /dev/sdb1 /media/user/My\\040Stick vfat rw 0 0
                tmpfs /run/user/1000 tmpfs rw 0 0
                """);
        assertEquals(List.of(new Place(Path.of("/"), "File System"), new Place(Path.of("/mnt/hgfs"), "hgfs"),
                new Place(Path.of("/media/user/My Stick"), "My Stick")), LinuxFilePlatform.volumes(mounts));
    }

    /** One mount is one volume, wherever in the tree; a destination still to be made is on its parent's. */
    @Test
    @EnabledOnOs(value = OS.LINUX, disabledReason = "/proc is a file system of its own only on Linux")
    void aVolumeIsAFileSystemNotARoot(@TempDir Path dir) throws Exception {
        Path a = Files.createDirectories(dir.resolve("a"));
        assertEquals(true, platform.sameVolume(a, dir.resolve("b/not-yet")));
        assertEquals(false, platform.sameVolume(a, Path.of("/proc")));
    }

    @Test
    void aNameMayHoldAnythingButASlash() {
        assertEquals("a:b?c_d", platform.safeName("a:b?c/d"));
        assertEquals("_..", platform.safeName(".."));
    }
}
