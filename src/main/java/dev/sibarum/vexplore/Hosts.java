package dev.sibarum.vexplore;

import dev.sibarum.vexplore.remote.Host;
import dev.sibarum.vexplore.remote.Remotes;
import dev.vexelray.gui.core.app.Settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The hosts Vexplore offers: the ones named in {@code ~/.ssh/config}, then the ones added here with New host.
 *
 * <p><b>Vexplore never writes the ssh config.</b> A host added here is kept in Vexplore's own settings, under
 * {@link #KEY}, as the authority it was typed as ({@code me@box:2222}), and only once it has connected, so a typo is
 * not remembered. The config is the person's and stays as they wrote it; a host it already names is not stored
 * twice.
 */
final class Hosts {

    /** The settings key the added hosts are listed under. */
    static final String KEY = "remote.hosts";

    private final Settings settings;

    Hosts(Settings settings) {
        this.settings = settings;
    }

    /** The config's hosts in the order written, then the added ones in the order added, each once. */
    List<Host> all() {
        Map<String, Host> out = new LinkedHashMap<>();
        for (Host h : Remotes.configured()) {
            out.put(h.authority(), h);
        }
        for (Host h : added()) {
            out.putIfAbsent(h.authority(), h);
        }
        return List.copyOf(out.values());
    }

    /** The hosts added with New host, oldest first. A value in the file that is not a host is skipped. */
    List<Host> added() {
        List<Host> out = new ArrayList<>();
        for (String s : settings.getList(KEY)) {
            Host h = Host.ofAuthority(s);
            if (h != null) {
                out.add(h);
            }
        }
        return out;
    }

    /** Remember {@code host}, unless it is already offered. Writes the settings file. */
    synchronized void add(Host host) {
        if (all().stream().anyMatch(h -> h.authority().equals(host.authority()))) {
            return;
        }
        List<String> now = new ArrayList<>(settings.getList(KEY));
        now.add(host.authority());
        settings.putList(KEY, now);
        settings.save();
    }
}
