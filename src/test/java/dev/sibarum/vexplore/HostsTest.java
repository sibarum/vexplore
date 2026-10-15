package dev.sibarum.vexplore;

import dev.sibarum.vexplore.remote.Host;
import dev.vexelray.gui.core.app.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** New host: what a typed host means, and that an added one is kept beside the config's and never doubled. */
class HostsTest {

    @TempDir
    Path home;

    private String before;

    @BeforeEach
    void home() {
        before = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
    }

    @AfterEach
    void back() {
        System.setProperty("user.home", before);
    }

    @Test
    void aTypedHostIsUserHostAndPort() {
        assertEquals(new Host("me", "box", 2222), Host.parse(" me@box:2222 "));
        assertEquals(new Host(null, "pi", 0), Host.parse("pi"));
        assertEquals(new Host("me", "box.example.org", 0), Host.parse("sftp://me@box.example.org/srv/data"));
        assertEquals(new Host("deploy", "10.0.0.9", 22), Host.parse("ssh://deploy@10.0.0.9:22"));

        assertTrue(assertThrows(IllegalArgumentException.class, () -> Host.parse("box:logs")).getMessage()
                .contains("not a port"));
        assertThrows(IllegalArgumentException.class, () -> Host.parse(""));
        assertThrows(IllegalArgumentException.class, () -> Host.parse("me@:22"));
        assertThrows(IllegalArgumentException.class, () -> Host.parse("-oProxyCommand=x"));
        assertThrows(IllegalArgumentException.class, () -> Host.parse("me@my box"));
    }

    @Test
    void anAddedHostIsKeptAfterTheConfigsAndOnlyOnce() throws IOException {
        Files.createDirectories(home.resolve(".ssh"));
        Files.writeString(home.resolve(".ssh").resolve("config"), "Host nas\n  HostName 192.168.1.20\n");
        Path file = home.resolve("settings.properties");
        Hosts hosts = new Hosts(Settings.at(file));

        hosts.add(Host.parse("me@box:2222"));
        hosts.add(Host.parse("me@box:2222"));
        hosts.add(Host.parse("nas"));   // the config names it already

        assertEquals(List.of("nas", "me@box:2222"), hosts.all().stream().map(Host::authority).toList());
        assertEquals(List.of("me@box:2222"),
                new Hosts(Settings.at(file)).added().stream().map(Host::authority).toList(), "kept in the file");
        assertEquals("Host nas\n  HostName 192.168.1.20\n", Files.readString(home.resolve(".ssh").resolve("config")),
                "the ssh config is never written");
    }

    @Test
    void aValueInTheFileThatIsNotAHostIsSkipped() {
        Settings settings = Settings.at(home.resolve("settings.properties"));
        settings.putList(Hosts.KEY, List.of("ok@box", "not a host", "-bad"));
        assertEquals(List.of("ok@box"), new Hosts(settings).added().stream().map(Host::authority).toList());
    }
}
