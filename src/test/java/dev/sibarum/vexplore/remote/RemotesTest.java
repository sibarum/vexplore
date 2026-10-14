package dev.sibarum.vexplore.remote;

import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.files.Folders;
import dev.sibarum.vexplore.files.Preview;
import dev.sibarum.vexplore.files.Previews;
import dev.sibarum.vexplore.ops.Operations;
import dev.sibarum.vexplore.ops.Plan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A remote folder, for real, without a network: Git for Windows ships OpenSSH's {@code sftp-server}, which speaks
 * exactly what {@code ssh -s sftp} would leave on the other end, so {@code vexplore.sftp.server} points every host at
 * it. Skipped where it is not installed, as the git tests skip without git.
 */
class RemotesTest {

    private static final List<String> SERVERS = List.of(
            "C:/Program Files/Git/usr/lib/ssh/sftp-server.exe",
            "/usr/lib/openssh/sftp-server",
            "/usr/lib/ssh/sftp-server",
            "/usr/libexec/sftp-server",
            "/usr/libexec/openssh/sftp-server");

    private static boolean server;

    @BeforeAll
    static void findServer() {
        for (String s : SERVERS) {
            if (Files.isRegularFile(Path.of(s))) {
                System.setProperty("vexplore.sftp.server", s);
                server = true;
                return;
            }
        }
    }

    /** The server's spelling of a local folder: Git's runtime calls {@code C:\x} {@code /c/x}. */
    private static String there(Path local) {
        String s = local.toAbsolutePath().toString().replace('\\', '/');
        if (s.length() > 1 && s.charAt(1) == ':') {
            s = "/" + Character.toLowerCase(s.charAt(0)) + s.substring(2);
        }
        return s;
    }

    private static Path remote(String host, Path local) {
        return Remotes.parse("sftp://" + host + there(local)).orElseThrow();
    }

    @Test
    void addressesAreParsedAndWindowsPathsAreNot() {
        Path p = Remotes.parse("sftp://me@box:2222/srv/data").orElseThrow();
        assertTrue(Remotes.is(p));
        assertEquals(new Host("me", "box", 2222), Remotes.host(p));
        assertEquals("/srv/data", p.toString());
        assertEquals("sftp://me@box:2222/srv/data", Remotes.address(p));

        Path scp = Remotes.parse("me@box:logs/today").orElseThrow();
        assertEquals("logs/today", scp.toString());
        assertFalse(scp.isAbsolute());

        assertTrue(Remotes.parse("C:\\Users\\me").isEmpty());
        assertTrue(Remotes.parse("C:/Users/me").isEmpty());
        assertTrue(Remotes.parse("D:").isEmpty());
        assertTrue(Remotes.parse("/home/me").isEmpty());
        assertTrue(Remotes.parse("sftp://-oProxyCommand=x/").isEmpty(), "a host that would be an ssh option");
        assertFalse(Remotes.is(Path.of("x")));
    }

    @Test
    void pathsBehaveLikePosixPaths() {
        Path p = Remotes.parse("sftp://box/a/b/c.txt").orElseThrow();
        assertEquals("c.txt", p.getFileName().toString());
        assertEquals("/a/b", p.getParent().toString());
        assertEquals("/", p.getParent().getParent().getParent().toString());
        assertEquals(null, p.getRoot().getParent());
        assertEquals(null, p.getRoot().getFileName());
        assertEquals(4, Folders.chain(p).size());
        assertEquals("b/c.txt", p.getParent().getParent().relativize(p).toString());
        assertEquals("/a/x", p.resolveSibling("../x").normalize().toString());
        assertEquals(p, Remotes.parse("sftp://box//a/b/c.txt/").orElseThrow());
        assertTrue(p.startsWith(p.getParent()));
        assertFalse(p.startsWith(Remotes.parse("sftp://other/a").orElseThrow()), "same path, another host");
        assertFalse(p.startsWith(Path.of("/a")));
    }

    @Test
    void theHostsOfAnSshConfigAreItsNamesThatAreNotPatterns(@TempDir Path dir) throws IOException {
        Path extra = Files.writeString(dir.resolve("work.conf"), "Host build\n  HostName 10.0.0.9\n");
        Path config = Files.writeString(dir.resolve("config"), String.join("\n",
                "# mine",
                "Host pi nas",
                "    HostName 192.168.1.20",
                "Host *.corp !bad",
                "Host=web",
                "Include " + extra.getFileName(),
                "Host *",
                "    ServerAliveInterval 30"));
        assertEquals(List.of("pi", "nas", "web", "build"),
                Host.configured(config).stream().map(Host::host).toList());
        assertEquals(List.of(), Host.configured(dir.resolve("absent")));
    }

    @Test
    void sshIsAskedNeverToPrompt() {
        String before = System.getProperty("vexplore.sftp.server");
        System.clearProperty("vexplore.sftp.server");
        try {
            List<String> cmd = new Host("me", "box", 2200).command();
            assertTrue(cmd.contains("BatchMode=yes"));
            assertEquals(List.of("-l", "me", "-p", "2200", "-s", "box", "sftp"),
                    cmd.subList(cmd.size() - 7, cmd.size()));
        } finally {
            if (before != null) {
                System.setProperty("vexplore.sftp.server", before);
            }
        }
    }

    @Test
    void aRemoteFolderListsReadsAndPreviews(@TempDir Path local) throws IOException {
        assumeTrue(server, "no sftp-server here");
        Files.createDirectories(local.resolve("sub"));
        Files.writeString(local.resolve("notes.txt"), "hello\nthere\n");
        byte[] blob = new byte[100_000];
        new java.util.Random(7).nextBytes(blob);
        Files.write(local.resolve("blob.bin"), blob);
        Files.setLastModifiedTime(local.resolve("notes.txt"), FileTime.from(Instant.parse("2024-05-01T10:00:00Z")));

        Path dir = remote("list-host", local);
        List<Entry> entries = Folders.list(dir);
        assertEquals(List.of("sub", "blob.bin", "notes.txt"), entries.stream().map(Entry::name).toList());
        assertTrue(entries.get(0).folder());
        assertEquals(100_000L, entries.get(1).size());
        assertEquals(Instant.parse("2024-05-01T10:00:00Z"), entries.get(2).modified());
        assertTrue(Remotes.is(entries.get(2).path()));
        assertTrue(Remotes.connected(dir));

        assertEquals(List.of("sub"), Folders.subfolders(dir).stream().map(p -> p.getFileName().toString()).toList());
        assertArrayEquals(blob, Files.readAllBytes(dir.resolve("blob.bin")));
        Preview text = Previews.of(dir.resolve("notes.txt"));
        assertEquals(Preview.Tier.TEXT, text.tier());
        assertEquals(List.of("hello", "there"), text.lines());

        assertThrows(java.nio.file.NoSuchFileException.class,
                () -> Files.readAttributes(dir.resolve("absent"), java.nio.file.attribute.BasicFileAttributes.class));
    }

    @Test
    void homeIsWhereTheServerStarted() throws IOException {
        assumeTrue(server, "no sftp-server here");
        Path home = Remotes.absolute(Remotes.home(Host.of("home-host")));
        assertTrue(home.isAbsolute());
        assertEquals(there(Path.of("").toAbsolutePath()).toLowerCase(), home.toString().toLowerCase());
    }

    @Test
    void downloadingIsACopyThatUndoes(@TempDir Path root) throws IOException {
        assumeTrue(server, "no sftp-server here");
        Path served = Files.createDirectories(root.resolve("served"));
        Files.createDirectories(served.resolve("album"));
        Files.writeString(served.resolve("album").resolve("one.txt"), "1");
        Files.writeString(served.resolve("a.txt"), "aaa");
        Path out = Files.createDirectories(root.resolve("out"));
        Path dir = remote("copy-host", served);

        List<Path> targets = List.of(dir.resolve("a.txt"), dir.resolve("album"));
        Plan plan = Plan.of(Plan.Kind.COPY, targets, List.of(3L, 0L), out, Set.of(), null);
        assertEquals(2, plan.count(), "a remote folder is never 'already in' a local one");
        Operations.Done done = Operations.execute(plan, root.resolve("trash"), n -> { });

        assertEquals(List.of(), done.failed());
        assertEquals("aaa", Files.readString(out.resolve("a.txt")));
        assertEquals("1", Files.readString(out.resolve("album").resolve("one.txt")));
        assertEquals(List.of(), Operations.undo(done));
        assertFalse(Files.exists(out.resolve("a.txt")));
        assertTrue(Files.exists(served.resolve("a.txt")));
    }

    @Test
    void uploadingIsACopyThereThatUndoes(@TempDir Path root) throws IOException {
        assumeTrue(server, "no sftp-server here");
        Path local = Files.createDirectories(root.resolve("here"));
        Files.writeString(local.resolve("a.txt"), "aaa");
        Files.setLastModifiedTime(local.resolve("a.txt"), FileTime.from(Instant.parse("2023-03-03T03:03:03Z")));
        Files.createDirectories(local.resolve("album"));
        Files.writeString(local.resolve("album").resolve("one.txt"), "1");
        Path served = Files.createDirectories(root.resolve("served"));
        Files.writeString(served.resolve("taken.txt"), "keep me");
        Files.writeString(local.resolve("taken.txt"), "not me");
        Path there = remote("upload-host", served);

        List<Path> targets = List.of(local.resolve("a.txt"), local.resolve("album"), local.resolve("taken.txt"));
        Plan plan = Plan.of(Plan.Kind.COPY, targets, List.of(3L, 0L, 6L), there, Set.of("taken.txt"), null);
        assertEquals(2, plan.count(), "a name already on the server is left alone, before anything runs");
        Operations.Done done = Operations.execute(plan, root.resolve("trash"), n -> { });

        assertEquals(List.of(), done.failed());
        assertEquals("aaa", Files.readString(served.resolve("a.txt")));
        assertEquals(Instant.parse("2023-03-03T03:03:03Z"),
                Files.getLastModifiedTime(served.resolve("a.txt")).toInstant(), "the copy keeps its date");
        assertEquals("1", Files.readString(served.resolve("album").resolve("one.txt")));
        assertEquals("keep me", Files.readString(served.resolve("taken.txt")));

        assertEquals(List.of(), Operations.undo(done));
        assertFalse(Files.exists(served.resolve("a.txt")));
        assertFalse(Files.exists(served.resolve("album")));
        assertTrue(Files.exists(local.resolve("a.txt")), "an undone upload never touches the originals");
    }

    @Test
    void writingRefusesToOverwrite(@TempDir Path root) throws IOException {
        assumeTrue(server, "no sftp-server here");
        Files.writeString(root.resolve("x.txt"), "first");
        Files.writeString(root.resolve("y.txt"), "second");
        Path there = remote("refuse-host", root);
        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> Files.copy(Path.of("pom.xml"), there.resolve("x.txt")));
        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> Files.move(there.resolve("x.txt"), there.resolve("y.txt")));
        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> Files.createDirectory(there.resolve("x.txt")));
        Files.createDirectories(root.resolve("full"));
        Files.writeString(root.resolve("full").resolve("z"), "z");
        assertThrows(java.nio.file.DirectoryNotEmptyException.class, () -> Files.delete(there.resolve("full")));
        assertEquals("first", Files.readString(root.resolve("x.txt")));
        assertEquals("second", Files.readString(root.resolve("y.txt")));
    }

    @Test
    void renamingOnTheServerUndoes(@TempDir Path root) throws IOException {
        assumeTrue(server, "no sftp-server here");
        Files.writeString(root.resolve("draft.txt"), "words");
        Path there = remote("rename-host", root);
        List<Entry> listed = Folders.list(there);   // paths carrying what the listing said, as the rail's do
        Path draft = listed.get(0).path();

        assertEquals(0, Plan.rename(draft, 5, "", Set.of()).count());
        assertEquals(0, Plan.rename(draft, 5, "draft.txt", Set.of()).count());
        assertEquals(0, Plan.rename(draft, 5, "../up.txt", Set.of()).count());
        assertEquals(0, Plan.rename(draft, 5, "other.txt", Set.of("other.txt")).count());
        Plan plan = Plan.rename(draft, 5, " final.txt ", Set.of("draft.txt"));
        assertEquals(1, plan.count());

        Operations.Done done = Operations.execute(plan, root.resolve("trash"), n -> { });
        assertEquals(List.of(), done.failed());
        assertFalse(Files.exists(root.resolve("draft.txt")));
        assertEquals("words", Files.readString(root.resolve("final.txt")));

        // At once, while what the listing said about draft.txt would still be fresh: it must not be believed.
        assertEquals(List.of(), Operations.undo(done));
        assertEquals("words", Files.readString(root.resolve("draft.txt")));
        assertFalse(Files.exists(root.resolve("final.txt")));
    }

    @Test
    void deletingOnTheServerGoesToItsOwnTrash(@TempDir Path root) throws IOException {
        assumeTrue(server, "no sftp-server here");
        Path served = Files.createDirectories(root.resolve("served"));
        Files.writeString(served.resolve("old.log"), "log");
        Files.createDirectories(served.resolve("stale"));
        Files.writeString(served.resolve("stale").resolve("inner.txt"), "in");
        Path there = remote("delete-host", served);
        Path trash = remote("delete-host", root).resolve(Remotes.TRASH);

        Plan plan = Plan.of(Plan.Kind.DELETE, List.of(there.resolve("old.log"), there.resolve("stale")),
                List.of(3L, 0L), null, Set.of(), null);
        Operations.Done done = Operations.execute(plan, trash, n -> { });

        assertEquals(List.of(), done.failed());
        assertFalse(Files.exists(served.resolve("old.log")));
        assertFalse(Files.exists(served.resolve("stale")));
        assertTrue(Remotes.is(done.done().get(0).to()), "kept on the host, not brought here");
        try (var bins = Files.list(root.resolve(Remotes.TRASH))) {
            Path bin = bins.findFirst().orElseThrow();
            assertEquals("in", Files.readString(bin.resolve("stale").resolve("inner.txt")));
        }

        assertEquals(List.of(), Operations.undo(done));
        assertEquals("log", Files.readString(served.resolve("old.log")));
        assertEquals("in", Files.readString(served.resolve("stale").resolve("inner.txt")));
    }

    @Test
    void aNewFolderUndoesOnlyWhileEmpty(@TempDir Path root) throws IOException {
        assumeTrue(server, "no sftp-server here");
        Path there = remote("folder-host", root);

        Operations.Done made = Operations.makeFolder(there.resolve("photos"));
        assertEquals(1, made.count());
        assertTrue(Files.isDirectory(root.resolve("photos")));
        assertEquals(0, Operations.makeFolder(there.resolve("photos")).count(), "a name already there");
        assertEquals(List.of(), Operations.undo(made));
        assertFalse(Files.exists(root.resolve("photos")));

        Operations.Done again = Operations.makeFolder(there.resolve("photos"));
        Files.writeString(root.resolve("photos").resolve("kept.jpg"), "x");
        assertEquals(1, Operations.undo(again).size(), "what was put in it since is not ours to remove");
        assertTrue(Files.exists(root.resolve("photos").resolve("kept.jpg")));
    }

    @Test
    void aLocalCopyIsKeptUntilTheRemoteChanges() throws IOException {
        assumeTrue(server, "no sftp-server here");
        Path served = Files.createTempDirectory("vexplore-served");
        String home = System.getProperty("user.home");
        Path cacheRoot = Files.createTempDirectory("vexplore-home");
        System.setProperty("user.home", cacheRoot.toString());
        try {
            Files.writeString(served.resolve("x.txt"), "first");
            Path remote = remote("cache-host", served).resolve("x.txt");
            Path copy = Remotes.local(remote);
            assertTrue(copy.startsWith(cacheRoot));
            assertEquals("first", Files.readString(copy));

            Files.writeString(served.resolve("x.txt"), "second!");
            Files.setLastModifiedTime(served.resolve("x.txt"), FileTime.from(Instant.parse("2030-01-01T00:00:00Z")));
            assertEquals("second!", Files.readString(Remotes.local(remote)));
        } finally {
            System.setProperty("user.home", home);
        }
    }

    @Test
    void aHostThatCannotBeReachedSaysWhy() {
        String before = System.getProperty("vexplore.sftp.server");
        System.setProperty("vexplore.sftp.server", "vexplore-no-such-command");
        try {
            Path dir = Remotes.home(Host.of("nowhere-host"));
            assertEquals(List.of(), Folders.list(dir), "unreachable is empty, as an unreadable folder is");
            assertFalse(Remotes.problem(dir).isEmpty());
            assertFalse(Remotes.connected(dir));
        } finally {
            if (before == null) {
                System.clearProperty("vexplore.sftp.server");
            } else {
                System.setProperty("vexplore.sftp.server", before);
            }
        }
    }
}
