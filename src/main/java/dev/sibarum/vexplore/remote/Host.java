package dev.sibarum.vexplore.remote;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Somewhere {@code ssh} can reach: a host as {@code ssh} would be given it, and optionally a user and a port.
 *
 * <p>The host is passed to {@code ssh} as written, so an alias from {@code ~/.ssh/config} is a host, and its
 * {@code HostName}, {@code User}, {@code IdentityFile} and {@code ProxyJump} all apply without Vexplore reading them.
 * A user or port given here wins over the config, as it would on a command line.
 *
 * @param user null to let {@code ssh} decide
 * @param host an alias or an address; never empty, never starting with {@code -}
 * @param port 0 to let {@code ssh} decide
 */
public record Host(String user, String host, int port) {

    public Host {
        if (host == null || host.isBlank() || host.startsWith("-") || host.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("not a host: " + host);
        }
        if (user != null && (user.isBlank() || user.startsWith("-")
                || user.chars().anyMatch(Character::isWhitespace))) {
            throw new IllegalArgumentException("not a user: " + user);
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("not a port: " + port);
        }
    }

    public static Host of(String host) {
        return new Host(null, host, 0);
    }

    /** {@code [user@]host[:port]}: what an address says before its path, and what a file system is keyed by. */
    public String authority() {
        return (user == null ? "" : user + "@") + host + (port == 0 ? "" : ":" + port);
    }

    /** What the tree and the rail call it: the alias, or the address, with the user when one was given. */
    public String label() {
        return (user == null ? "" : user + "@") + host;
    }

    /**
     * The command that leaves an SFTP server on the far end of its standard streams.
     *
     * <p>{@code vexplore.sftp.server}, when set, is run instead of {@code ssh} for every host: a local
     * {@code sftp-server} speaks the same protocol, so a test or a capture can browse "a remote" with no network and
     * no keys. {@code vexplore.ssh} names the {@code ssh} to run when the one on the path is not the one wanted.
     */
    public List<String> command() {
        String server = System.getProperty("vexplore.sftp.server");
        if (server != null && !server.isBlank()) {
            return List.of(server);
        }
        List<String> cmd = new ArrayList<>(List.of(System.getProperty("vexplore.ssh", "ssh"),
                "-T", "-o", "BatchMode=yes", "-o", "ConnectTimeout=15", "-o", "ServerAliveInterval=20"));
        if (user != null) {
            cmd.add("-l");
            cmd.add(user);
        }
        if (port != 0) {
            cmd.add("-p");
            cmd.add(Integer.toString(port));
        }
        cmd.add("-s");
        cmd.add(host);
        cmd.add("sftp");
        return List.copyOf(cmd);
    }

    /**
     * The hosts named in an ssh config, in the order written: every name on a {@code Host} line that is not a
     * pattern. {@code Include} is followed one level, relative to {@code ~/.ssh} as ssh resolves it. Unreadable or
     * absent is no hosts.
     */
    public static List<Host> configured(Path config) {
        Set<String> names = new LinkedHashSet<>();
        read(config, names, true);
        List<Host> out = new ArrayList<>();
        for (String n : names) {
            try {
                out.add(Host.of(n));
            } catch (IllegalArgumentException ignored) {
                // A name ssh would take and this would not run: leave it out rather than offer a broken row.
            }
        }
        return List.copyOf(out);
    }

    private static void read(Path config, Set<String> names, boolean follow) {
        List<String> lines;
        try {
            lines = Files.readAllLines(config);
        } catch (IOException | RuntimeException e) {
            return;
        }
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] words = line.split("[\\s=]+");
            String key = words[0].toLowerCase(Locale.ROOT);
            if (key.equals("host")) {
                for (int i = 1; i < words.length; i++) {
                    String w = words[i].replace("\"", "");
                    if (!w.isEmpty() && w.chars().noneMatch(c -> c == '*' || c == '?' || c == '!')) {
                        names.add(w);
                    }
                }
            } else if (key.equals("include") && follow) {
                for (int i = 1; i < words.length; i++) {
                    String w = words[i].replace("\"", "");
                    if (w.startsWith("~/")) {
                        w = System.getProperty("user.home") + w.substring(1);
                    }
                    Path p = config.getParent() == null ? Path.of(w) : config.getParent().resolve(w);
                    if (!w.contains("*")) {
                        read(p, names, false);
                    }
                }
            }
        }
    }
}
