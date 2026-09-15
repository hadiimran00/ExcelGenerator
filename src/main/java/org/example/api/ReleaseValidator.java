package org.example.api;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;

/**
 * Release Validator - Microservice Release Version Comparator.
 */
public class ReleaseValidator {

    private static final String PROPERTIES_FILE = "environments.properties";
    private static final int TIMEOUT_SECONDS = 10;
    private static final int THREAD_POOL_SIZE = 10;

    private static final String[] SERVICES = {
            "batchprice-service", "autoroute-service", "changemanagement-service",
            "claimautomation-service", "fileuploader-service", "global-service",
            "integrator-service", "menu-service", "merchandising-service",
            "orderfulfillment-service", "order-service", "payment-service",
            "powerbi-service", "promotion-service", "stock-service",
            "studio-service", "target-service", "validation-service",
            "workflow-service", "financials-service", "transactionalreport-service",
            "sso-service", "kafkaauditlog-service", "scriptexecutor-service",
            "angular-service"
    };

    // ------------------------------------------------------------------
    // Domain model
    // ------------------------------------------------------------------

    static class Environment {
        String name;
        String baseUrl;

        Environment(String name, String baseUrl) {
            this.name = name;
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static class VersionResult {
        String service;
        String env1Version;
        String env2Version;
        ComparisonStatus status;

        VersionResult(String service, String env1Version, String env2Version, ComparisonStatus status) {
            this.service = service;
            this.env1Version = env1Version;
            this.env2Version = env2Version;
            this.status = status;
        }
    }

    enum ComparisonStatus {
        EQUAL("\u2713", "EQUAL"),
        AHEAD("\u2191", "AHEAD"),
        BEHIND("\u2193", "BEHIND"),
        ERROR("!", "ERROR");

        final String icon;
        final String label;

        ComparisonStatus(String icon, String label) {
            this.icon = icon;
            this.label = label;
        }
    }

    static class ComparisonReport {
        final Environment env1;
        final Environment env2;
        final List<VersionResult> results;
        final int equal;
        final int ahead;
        final int behind;
        final int errors;
        long elapsedMillis = -1;

        ComparisonReport(Environment env1, Environment env2, List<VersionResult> results,
                         int equal, int ahead, int behind, int errors) {
            this.env1 = env1;
            this.env2 = env2;
            this.results = results;
            this.equal = equal;
            this.ahead = ahead;
            this.behind = behind;
            this.errors = errors;
        }

        int total() {
            return results.size();
        }

        int mismatches() {
            return ahead + behind + errors;
        }
    }

    // ------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
            new MainFrame().setVisible(true);
        });
    }

    // ==================================================================
    // COMPARISON ENGINE
    // ==================================================================

    private static ComparisonReport compareVersions(Environment env1, Environment env2) {
        ConcurrentHashMap<String, String> env1Versions = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, String> env2Versions = new ConcurrentHashMap<>();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Future<?>> futures = new ArrayList<>();

        for (String service : SERVICES) {
            futures.add(executor.submit(() -> {
                String version = fetchVersion(env1.baseUrl, service);
                env1Versions.put(service, version);
            }));
        }

        for (String service : SERVICES) {
            futures.add(executor.submit(() -> {
                String version = fetchVersion(env2.baseUrl, service);
                env2Versions.put(service, version);
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get(TIMEOUT_SECONDS + 5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }

        executor.shutdown();

        List<VersionResult> results = new ArrayList<>();
        for (String service : SERVICES) {
            String v1 = env1Versions.getOrDefault(service, "ERROR");
            String v2 = env2Versions.getOrDefault(service, "ERROR");
            ComparisonStatus status = compareVersionStrings(v1, v2);
            results.add(new VersionResult(service, v1, v2, status));
        }

        results.sort((a, b) -> {
            if (a.status == ComparisonStatus.ERROR && b.status != ComparisonStatus.ERROR) return 1;
            if (a.status != ComparisonStatus.ERROR && b.status == ComparisonStatus.ERROR) return -1;
            if (a.status != ComparisonStatus.EQUAL && b.status == ComparisonStatus.EQUAL) return 1;
            if (a.status == ComparisonStatus.EQUAL && b.status != ComparisonStatus.EQUAL) return -1;
            return a.service.compareTo(b.service);
        });

        int equal = 0, ahead = 0, behind = 0, errors = 0;
        for (VersionResult r : results) {
            switch (r.status) {
                case EQUAL: equal++; break;
                case AHEAD: ahead++; break;
                case BEHIND: behind++; break;
                case ERROR: errors++; break;
            }
        }

        return new ComparisonReport(env1, env2, results, equal, ahead, behind, errors);
    }

    private static String fetchVersion(String baseUrl, String service) {
        try {
            RestAssured.useRelaxedHTTPSValidation();
            RestAssured.baseURI = baseUrl;

            Response response;

            if ("angular-service".equals(service)) {
                response = RestAssured.given()
                        .relaxedHTTPSValidation()
                        .config(RestAssured.config()
                                .httpClient(io.restassured.config.HttpClientConfig.httpClientConfig()
                                        .setParam("http.connection.timeout", TIMEOUT_SECONDS * 1000)
                                        .setParam("http.socket.timeout", TIMEOUT_SECONDS * 1000)))
                        .when()
                        .get("ngui/main/resources/version.txt");
            } else {
                response = RestAssured.given()
                        .relaxedHTTPSValidation()
                        .header("Accept", "application/json")
                        .config(RestAssured.config()
                                .httpClient(io.restassured.config.HttpClientConfig.httpClientConfig()
                                        .setParam("http.connection.timeout", TIMEOUT_SECONDS * 1000)
                                        .setParam("http.socket.timeout", TIMEOUT_SECONDS * 1000)))
                        .when()
                        .get("/" + service + "/api/v1/releaseversion/getReleaseVersion");
            }

            int statusCode = response.getStatusCode();

            if (statusCode == 200) {
                String body = response.getBody().asString().trim();
                body = body.replaceAll("^\"|\"$", "");
                return body.isEmpty() ? "EMPTY_RESPONSE" : body;
            } else {
                String body = response.getBody().asString().trim();
                body = body.replaceAll("^\"|\"$", "");
                return body.isEmpty()
                        ? "HTTP: " + statusCode + "EMPTY_RESPONSE"
                        : "(HTTP: " + statusCode + ") " + body;
            }

        } catch (Exception e) {
            if (e.getMessage() != null &&
                    (e.getMessage().contains("timeout") || e.getMessage().contains("timed out"))) {
                return "TIMEOUT";
            }
            return "ERROR";
        }
    }

    private static ComparisonStatus compareVersionStrings(String v1, String v2) {
        if (v1.startsWith("ERROR") || v1.startsWith("HTTP") || v1.contains("(HTTP:") ||
                v1.startsWith("TIMEOUT") || v2.startsWith("ERROR") || v2.startsWith("HTTP") ||
                v2.contains("(HTTP:") || v2.startsWith("TIMEOUT") ||
                v1.equals("EMPTY_RESPONSE") || v2.equals("EMPTY_RESPONSE")) {
            return ComparisonStatus.ERROR;
        }

        try {
            int comparison = compareVersions(v1, v2);
            if (comparison == 0) {
                return ComparisonStatus.EQUAL;
            } else if (comparison < 0) {
                return ComparisonStatus.AHEAD;
            } else {
                return ComparisonStatus.BEHIND;
            }
        } catch (Exception e) {
            return ComparisonStatus.ERROR;
        }
    }

    private static int compareVersions(String version1, String version2) {
        String[] v1Parts = version1.split("\\.");
        String[] v2Parts = version2.split("\\.");

        int maxLength = Math.max(v1Parts.length, v2Parts.length);

        for (int i = 0; i < maxLength; i++) {
            String v1Part = i < v1Parts.length ? v1Parts[i] : "0";
            String v2Part = i < v2Parts.length ? v2Parts[i] : "0";

            try {
                int v1Num = Integer.parseInt(v1Part);
                int v2Num = Integer.parseInt(v2Part);

                if (v1Num < v2Num) {
                    return -1;
                } else if (v1Num > v2Num) {
                    return 1;
                }
            } catch (NumberFormatException e) {
                int compareResult = compareVersionPart(v1Part, v2Part);
                if (compareResult != 0) {
                    return compareResult;
                }
            }
        }

        return 0;
    }

    private static int compareVersionPart(String part1, String part2) {
        boolean p1IsNum = isInteger(part1);
        boolean p2IsNum = isInteger(part2);

        if (p1IsNum && p2IsNum) {
            int v1 = Integer.parseInt(part1);
            int v2 = Integer.parseInt(part2);
            return Integer.compare(v1, v2);
        } else if (p1IsNum) {
            return -1;
        } else if (p2IsNum) {
            return 1;
        } else {
            return compareAlphanumeric(part1, part2);
        }
    }

    private static boolean isInteger(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static int compareAlphanumeric(String s1, String s2) {
        String[] parts1 = splitAlphanumeric(s1);
        String[] parts2 = splitAlphanumeric(s2);

        int prefixCompare = parts1[0].compareTo(parts2[0]);
        if (prefixCompare != 0) {
            return prefixCompare;
        }

        if (!parts1[1].isEmpty() && !parts2[1].isEmpty()) {
            try {
                int num1 = Integer.parseInt(parts1[1]);
                int num2 = Integer.parseInt(parts2[1]);
                return Integer.compare(num1, num2);
            } catch (NumberFormatException e) {
                return parts1[1].compareTo(parts2[1]);
            }
        }

        return s1.compareTo(s2);
    }

    private static String[] splitAlphanumeric(String s) {
        int i = s.length() - 1;
        while (i >= 0 && Character.isDigit(s.charAt(i))) {
            i--;
        }
        if (i < s.length() - 1) {
            return new String[]{s.substring(0, i + 1), s.substring(i + 1)};
        } else {
            return new String[]{s, ""};
        }
    }

    private static List<Environment> loadEnvironmentsFromProperties() {
        List<Environment> environments = new ArrayList<>();
        Properties properties = new Properties();

        File propertiesFile = findPropertiesFile();

        if (propertiesFile == null || !propertiesFile.exists()) {
            System.err.println("Properties file not found: " + PROPERTIES_FILE);
            return environments;
        }

        try (FileInputStream fis = new FileInputStream(propertiesFile)) {
            properties.load(fis);
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                if (value != null && !value.trim().isEmpty()) {
                    environments.add(new Environment(key, value.trim()));
                }
            }
            environments.sort(Comparator.comparing(e -> e.name));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return environments;
    }

    private static File findPropertiesFile() {
        File currentDir = new File(PROPERTIES_FILE);
        if (currentDir.exists()) return currentDir;

        try {
            String jarPath = ReleaseValidator.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath();
            File jarDir = new File(jarPath).getParentFile();
            File jarDirFile = new File(jarDir, PROPERTIES_FILE);
            if (jarDirFile.exists()) return jarDirFile;
        } catch (Exception ignored) {
        }

        File homeDir = new File(System.getProperty("user.home"), PROPERTIES_FILE);
        if (homeDir.exists()) return homeDir;

        return null;
    }

    // ==================================================================
    // DESIGN SYSTEM & HELPERS
    // ==================================================================

    static final class Palette {
        static final Color WINDOW_BG = new Color(0xF5, 0xF5, 0xF7);
        static final Color CARD_BG = Color.WHITE;
        static final Color DIVIDER = new Color(0xE3, 0xE3, 0xE6);
        static final Color TEXT_PRIMARY = new Color(0x1D, 0x1D, 0x1F);
        static final Color TEXT_SECONDARY = new Color(0x6E, 0x6E, 0x73);
        static final Color TEXT_TERTIARY = new Color(0xA0, 0xA0, 0xA5);

        static final Color ACCENT = new Color(0x00, 0x7A, 0xFF);
        static final Color ACCENT_HOVER = new Color(0x0A, 0x69, 0xD6);
        static final Color GREEN = new Color(0x30, 0xB0, 0x5C);
        static final Color ORANGE = new Color(0xE8, 0x8A, 0x00);
        static final Color RED = new Color(0xE0, 0x39, 0x2E);
        static final Color GRAY = new Color(0x8E, 0x8E, 0x93);

        static final Color GREEN_BG = new Color(0xE6, 0xF6, 0xEB);
        static final Color ORANGE_BG = new Color(0xFD, 0xF1, 0xDD);
        static final Color RED_BG = new Color(0xFB, 0xE7, 0xE5);
        static final Color GRAY_BG = new Color(0xEF, 0xEF, 0xF1);
        static final Color ACCENT_BG = new Color(0xE3, 0xF0, 0xFF);

        static Color forStatus(ComparisonStatus status) {
            switch (status) {
                case EQUAL: return GREEN;
                case AHEAD: return ORANGE;
                case BEHIND: return ORANGE;
                case ERROR: return RED;
                default: return GRAY;
            }
        }

        private Palette() {}
    }

    static final class UIFonts {
        static final String FAMILY = Font.SANS_SERIF;
        static final Font TITLE = new Font(FAMILY, Font.BOLD, 22);
        static final Font SUBTITLE = new Font(FAMILY, Font.PLAIN, 13);
        static final Font SECTION = new Font(FAMILY, Font.BOLD, 15);
        static final Font BODY = new Font(FAMILY, Font.PLAIN, 13);
        static final Font BODY_BOLD = new Font(FAMILY, Font.BOLD, 13);
        static final Font SMALL = new Font(FAMILY, Font.PLAIN, 11);
        static final Font SMALL_BOLD = new Font(FAMILY, Font.BOLD, 11);
        static final Font METRIC_NUMBER = new Font(FAMILY, Font.BOLD, 26);
        static final Font METRIC_LABEL = new Font(FAMILY, Font.PLAIN, 12);
        static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 13);
        static final Font BUTTON = new Font(FAMILY, Font.BOLD, 13);

        private UIFonts() {}
    }

    /**
     * Panel implementation enforcing full horizontal stretch inside JScrollPane viewports.
     */
    static class ScrollablePanel extends JPanel implements Scrollable {
        ScrollablePanel(LayoutManager layout) {
            super(layout);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 64; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    static class RoundedButton extends JButton {
        private final boolean primary;
        private boolean hover = false;

        RoundedButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setFont(UIFonts.BUTTON);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setForeground(primary ? Color.WHITE : Palette.TEXT_PRIMARY);
            setBorder(BorderFactory.createEmptyBorder(9, 18, 9, 18));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = 10;
            Color bg;
            if (!isEnabled()) {
                bg = primary ? new Color(0xB9, 0xD8, 0xFF) : Palette.GRAY_BG;
            } else if (primary) {
                bg = hover ? Palette.ACCENT_HOVER : Palette.ACCENT;
            } else {
                bg = hover ? new Color(0xE9, 0xE9, 0xEC) : Palette.GRAY_BG;
            }
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
            g2.dispose();
            setForeground(isEnabled() ? (primary ? Color.WHITE : Palette.TEXT_PRIMARY) : Palette.TEXT_TERTIARY);
            super.paintComponent(g);
        }
    }

    static class IconButton extends JButton {
        IconButton(String glyph, String tooltip) {
            super(glyph);
            setToolTipText(tooltip);
            getAccessibleContext().setAccessibleName(tooltip);
            setFont(new Font(UIFonts.FAMILY, Font.BOLD, 15));
            setForeground(Palette.TEXT_SECONDARY);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setPreferredSize(new Dimension(34, 34));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    setBackground(Palette.GRAY_BG);
                    setContentAreaFilled(true);
                    setOpaque(true);
                    setBorderPainted(false);
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setContentAreaFilled(false);
                    setOpaque(false);
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (isOpaque()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fill(new Ellipse2D.Float(0, 0, getWidth(), getHeight()));
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    static class PlaceholderField extends JTextField {
        private final String placeholder;

        PlaceholderField(String placeholder) {
            this.placeholder = placeholder;
            setFont(UIFonts.BODY);
            setBorder(new CompoundBorder(
                    new RoundedLineBorder(Palette.DIVIDER, 8),
                    BorderFactory.createEmptyBorder(6, 10, 6, 10)));
            setBackground(Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty() && !placeholder.isEmpty()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Palette.TEXT_TERTIARY);
                g2.setFont(getFont());
                Insets insets = getInsets();
                FontMetrics fm = g2.getFontMetrics();
                int y = insets.top + (getHeight() - insets.top - insets.bottom - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(placeholder, insets.left, y);
                g2.dispose();
            }
        }
    }

    static class RoundedLineBorder extends AbstractBorder {
        private final Color color;
        private final int arc;

        RoundedLineBorder(Color color, int arc) {
            this.color = color;
            this.arc = arc;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.draw(new RoundRectangle2D.Float(x, y, width - 1, height - 1, arc, arc));
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(4, 4, 4, 4);
        }
    }

    static class EnvironmentRenderer extends JPanel implements ListCellRenderer<Environment> {
        private final JLabel nameLabel = new JLabel();
        private final JLabel urlLabel = new JLabel();

        EnvironmentRenderer() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            nameLabel.setFont(UIFonts.BODY_BOLD);
            urlLabel.setFont(UIFonts.SMALL);
            urlLabel.setForeground(Palette.TEXT_SECONDARY);
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            add(nameLabel);
            add(urlLabel);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Environment> list, Environment value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            if (value != null) {
                nameLabel.setText(value.name);
                urlLabel.setText(value.baseUrl);
            }
            setBackground(isSelected ? Palette.ACCENT_BG : Color.WHITE);
            nameLabel.setForeground(Palette.TEXT_PRIMARY);
            setOpaque(true);
            return this;
        }
    }

    static class MetricCard extends JPanel {
        private final JLabel numberLabel = new JLabel("0");
        private final JLabel captionLabel = new JLabel();
        private boolean selected = false;

        MetricCard(String caption, Color accent) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            numberLabel.setFont(UIFonts.METRIC_NUMBER);
            numberLabel.setForeground(accent != null ? accent : Palette.TEXT_PRIMARY);
            numberLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            captionLabel.setFont(UIFonts.METRIC_LABEL);
            captionLabel.setForeground(Palette.TEXT_SECONDARY);
            captionLabel.setText(caption.toUpperCase());
            captionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            add(numberLabel);
            add(Box.createVerticalStrut(4));
            add(captionLabel);

            getAccessibleContext().setAccessibleName(caption);
        }

        void setValue(int value) {
            numberLabel.setText(String.valueOf(value));
            revalidate();
            repaint();
        }

        void setSelected(boolean selected) {
            this.selected = selected;
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (selected) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Palette.GRAY_BG);
                // Fill within panel dimensions cleanly
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
    static class StatusDot extends JComponent {
        private Color color = Palette.GRAY;

        StatusDot() {
            setPreferredSize(new Dimension(9, 9));
        }

        void setColor(Color color) {
            this.color = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fill(new Ellipse2D.Float(0, 0, getWidth(), getHeight()));
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------
    // Table Components
    // ------------------------------------------------------------------

    static class ServiceTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Service", "Source", "Target", "Status"};
        private List<VersionResult> rows = new ArrayList<>();
        private String sourceName = "Source";
        private String targetName = "Target";

        void setData(List<VersionResult> rows, String sourceName, String targetName) {
            this.rows = rows;
            this.sourceName = sourceName;
            this.targetName = targetName;
            fireTableStructureChanged();
        }

        VersionResult rowAt(int index) {
            return rows.get(index);
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }

        @Override
        public String getColumnName(int column) {
            switch (column) {
                case 1: return sourceName;
                case 2: return targetName;
                default: return COLUMNS[column];
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 3 ? ComparisonStatus.class : String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            VersionResult r = rows.get(rowIndex);
            switch (columnIndex) {
                case 0: return r.service;
                case 1: return r.env1Version;
                case 2: return r.env2Version;
                case 3: return r.status;
                default: return null;
            }
        }

        @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return false; }
    }

    static class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof ComparisonStatus) {
                ComparisonStatus status = (ComparisonStatus) value;
                label.setText(status.icon + "  " + status.label);
                label.setForeground(Palette.forStatus(status));
                label.setFont(UIFonts.SMALL_BOLD.deriveFont(12f));
            }
            label.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            if (!isSelected) {
                label.setBackground(row % 2 == 0 ? Color.WHITE : new Color(0xFA, 0xFA, 0xFB));
            }
            return label;
        }
    }

    static class ServiceNameRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            label.setFont(UIFonts.BODY_BOLD);
            label.setForeground(Palette.TEXT_PRIMARY);
            label.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 10));
            if (!isSelected) {
                label.setBackground(row % 2 == 0 ? Color.WHITE : new Color(0xFA, 0xFA, 0xFB));
            }
            return label;
        }
    }

    static class VersionCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            label.setFont(UIFonts.MONO);
            label.setForeground(Palette.TEXT_PRIMARY);
            label.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            if (!isSelected) {
                label.setBackground(row % 2 == 0 ? Color.WHITE : new Color(0xFA, 0xFA, 0xFB));
            }
            return label;
        }
    }

    static class ServiceDetailsDialog extends JDialog {
        ServiceDetailsDialog(Frame owner, VersionResult result, String sourceName, String targetName) {
            super(owner, "Service Details", true);
            setSize(420, 360);
            setLocationRelativeTo(owner);
            setResizable(false);

            JPanel content = new JPanel();
            content.setBackground(Color.WHITE);
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));

            JLabel title = new JLabel(result.service);
            title.setFont(UIFonts.SECTION.deriveFont(18f));
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(title);
            content.add(Box.createVerticalStrut(18));

            content.add(buildRow(sourceName.toUpperCase(), result.env1Version));
            content.add(Box.createVerticalStrut(12));
            content.add(buildRow(targetName.toUpperCase(), result.env2Version));
            content.add(Box.createVerticalStrut(18));

            JSeparator sep = new JSeparator();
            sep.setForeground(Palette.DIVIDER);
            sep.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(sep);
            content.add(Box.createVerticalStrut(18));

            JLabel statusCaption = new JLabel("STATUS");
            statusCaption.setFont(UIFonts.SMALL_BOLD);
            statusCaption.setForeground(Palette.TEXT_SECONDARY);
            statusCaption.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(statusCaption);
            content.add(Box.createVerticalStrut(6));

            JLabel statusLabel = new JLabel(result.status.icon + "  " + result.status.label);
            statusLabel.setFont(UIFonts.SECTION);
            statusLabel.setForeground(Palette.forStatus(result.status));
            statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(statusLabel);
            content.add(Box.createVerticalStrut(14));

            JLabel explanation = new JLabel("<html>" + explanationFor(result.status, sourceName, targetName) + "</html>");
            explanation.setFont(UIFonts.BODY);
            explanation.setForeground(Palette.TEXT_SECONDARY);
            explanation.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(explanation);

            content.add(Box.createVerticalGlue());

            JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            buttonRow.setOpaque(false);
            buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            RoundedButton close = new RoundedButton("Close", true);
            close.addActionListener(e -> dispose());
            buttonRow.add(close);
            content.add(Box.createVerticalStrut(18));
            content.add(buttonRow);

            setContentPane(content);

            getRootPane().registerKeyboardAction(e -> dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    JComponent.WHEN_IN_FOCUSED_WINDOW);
        }

        private JPanel buildRow(String caption, String value) {
            JPanel panel = new JPanel();
            panel.setOpaque(false);
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel captionLabel = new JLabel(caption);
            captionLabel.setFont(UIFonts.SMALL_BOLD);
            captionLabel.setForeground(Palette.TEXT_SECONDARY);
            captionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel valueLabel = new JLabel(value);
            valueLabel.setFont(UIFonts.MONO.deriveFont(15f));
            valueLabel.setForeground(Palette.TEXT_PRIMARY);
            valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            panel.add(captionLabel);
            panel.add(Box.createVerticalStrut(4));
            panel.add(valueLabel);
            return panel;
        }

        private String explanationFor(ComparisonStatus status, String sourceName, String targetName) {
            switch (status) {
                case EQUAL:
                    return "Both environments are running the same release version.";
                case AHEAD:
                    return targetName + " is running a newer release than " + sourceName + ".";
                case BEHIND:
                    return targetName + " is running an older release than " + sourceName + ".";
                case ERROR:
                default:
                    return "The release version could not be retrieved or compared for this service. "
                            + "Check connectivity, authentication, and the service endpoint.";
            }
        }
    }

    // ==================================================================
    // MAIN WINDOW
    // ==================================================================

    static class MainFrame extends JFrame {

        private enum Filter { ALL, EQUAL, AHEAD, BEHIND, ERROR, MISMATCH }

        private final List<Environment> environments;

        // Header
        private final StatusDot statusDot = new StatusDot();
        private final JLabel statusLabel = new JLabel("Ready");

        // Environment selectors
        private final JComboBox<Environment> sourceCombo = new JComboBox<>();
        private final JComboBox<Environment> targetCombo = new JComboBox<>();
        private final RoundedButton compareButton = new RoundedButton("Compare Versions", true);

        // Content cards
        private final CardLayout rootCards = new CardLayout();
        private final JPanel rootPanel = new JPanel(rootCards);
        private final CardLayout contentCards = new CardLayout();
        private final JPanel contentPanel = new JPanel(contentCards);

        // Summary + table
        private final MetricCard totalCard = new MetricCard("Services", null);
        private final MetricCard equalCard = new MetricCard("Equal", Palette.GREEN);
        private final MetricCard aheadCard = new MetricCard("Ahead", Palette.ORANGE);
        private final MetricCard behindCard = new MetricCard("Behind", Palette.ORANGE);
        private final MetricCard errorCard = new MetricCard("Errors", Palette.RED);
        private final JLabel mismatchLabel = new JLabel("0 total mismatches");

        private final PlaceholderField searchField = new PlaceholderField("Search services...");
        private final JComboBox<String> filterCombo = new JComboBox<>(
                new String[]{"All", "Equal", "Ahead", "Behind", "Error"});

        private final ServiceTableModel tableModel = new ServiceTableModel();
        private final JTable table = new JTable(tableModel);
        private TableRowSorter<ServiceTableModel> sorter;

        private final JLabel completedCaption = new JLabel(" ");

        private Filter activeFilter = Filter.ALL;
        private ComparisonReport currentReport = null;

        MainFrame() {
            super("Release Validator");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(1100, 760);
            setMinimumSize(new Dimension(900, 600));
            getContentPane().setBackground(Palette.WINDOW_BG);
            setLayout(new BorderLayout());

            environments = loadEnvironmentsFromProperties();

            add(buildHeader(), BorderLayout.NORTH);

            rootPanel.setOpaque(false);
            rootPanel.add(buildEmptyStatePanel(), "noEnv");
            rootPanel.add(buildAppPanel(), "app");
            add(rootPanel, BorderLayout.CENTER);

            if (environments.isEmpty()) {
                rootCards.show(rootPanel, "noEnv");
            } else {
                rootCards.show(rootPanel, "app");
                populateEnvironmentCombos();
                contentCards.show(contentPanel, "ready");
            }

            setLocationRelativeTo(null);
        }

        private JComponent buildHeader() {
            JPanel header = new JPanel(new BorderLayout());
            header.setBackground(Palette.CARD_BG);
            header.setBorder(new CompoundBorder(
                    new MatteBorder(0, 0, 1, 0, Palette.DIVIDER),
                    BorderFactory.createEmptyBorder(16, 24, 16, 24)));

            JPanel titles = new JPanel();
            titles.setOpaque(false);
            titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
            JLabel title = new JLabel("Release Validator");
            title.setFont(UIFonts.TITLE);
            title.setForeground(Palette.TEXT_PRIMARY);
            JLabel subtitle = new JLabel("Microservice Release Version Comparator");
            subtitle.setFont(UIFonts.SUBTITLE);
            subtitle.setForeground(Palette.TEXT_SECONDARY);
            titles.add(title);
            titles.add(Box.createVerticalStrut(2));
            titles.add(subtitle);

            JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            statusPanel.setOpaque(false);
            statusDot.setColor(Palette.GRAY);
            statusLabel.setFont(UIFonts.BODY);
            statusLabel.setForeground(Palette.TEXT_SECONDARY);
            statusPanel.add(statusDot);
            statusPanel.add(statusLabel);

            header.add(titles, BorderLayout.WEST);
            header.add(statusPanel, BorderLayout.EAST);
            return header;
        }

        private JComponent buildEmptyStatePanel() {
            JPanel wrap = new JPanel(new GridBagLayout());
            wrap.setBackground(Palette.WINDOW_BG);

            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBackground(Palette.CARD_BG);
            card.setBorder(new CompoundBorder(new RoundedLineBorder(Palette.DIVIDER, 14),
                    BorderFactory.createEmptyBorder(32, 40, 32, 40)));
            card.setMaximumSize(new Dimension(460, 320));

            JLabel title = new JLabel("No environments found");
            title.setFont(UIFonts.SECTION.deriveFont(17f));
            title.setForeground(Palette.TEXT_PRIMARY);
            title.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel body = new JLabel("<html><div style='text-align:center;width:320px'>"
                    + "Release Validator couldn't find <b>" + PROPERTIES_FILE + "</b>."
                    + "</div></html>");
            body.setFont(UIFonts.BODY);
            body.setForeground(Palette.TEXT_SECONDARY);
            body.setAlignmentX(Component.CENTER_ALIGNMENT);
            body.setBorder(BorderFactory.createEmptyBorder(10, 0, 4, 0));

            JLabel location = new JLabel("<html><div style='text-align:center'>Expected location:<br>Application directory, current directory, or user home</div></html>");
            location.setFont(UIFonts.SMALL);
            location.setForeground(Palette.TEXT_TERTIARY);
            location.setAlignmentX(Component.CENTER_ALIGNMENT);
            location.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
            buttons.setOpaque(false);
            RoundedButton openLocation = new RoundedButton("Open Configuration Location", false);
            openLocation.addActionListener(e -> openConfigLocation());
            RoundedButton reload = new RoundedButton("Reload", true);
            reload.addActionListener(e -> reloadEnvironments());
            buttons.add(openLocation);
            buttons.add(reload);
            buttons.setAlignmentX(Component.CENTER_ALIGNMENT);

            card.add(title);
            card.add(body);
            card.add(location);
            card.add(buttons);

            wrap.add(card);
            return wrap;
        }

        private void openConfigLocation() {
            try {
                File dir = new File(System.getProperty("user.dir"));
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(dir);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Couldn't open the configuration folder automatically.\nExpected file: " + PROPERTIES_FILE,
                        "Open Configuration Location", JOptionPane.INFORMATION_MESSAGE);
            }
        }

        private void reloadEnvironments() {
            List<Environment> reloaded = loadEnvironmentsFromProperties();
            if (reloaded.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        PROPERTIES_FILE + " still couldn't be found or contains no entries.",
                        "No environments found", JOptionPane.WARNING_MESSAGE);
                return;
            }
            environments.clear();
            environments.addAll(reloaded);
            populateEnvironmentCombos();
            rootCards.show(rootPanel, "app");
            contentCards.show(contentPanel, "ready");
        }

        private JComponent buildAppPanel() {
            // Main container that makes the entire window vertically scrollable
            ScrollablePanel scrollContent = new ScrollablePanel(new BorderLayout(0, 20));
            scrollContent.setOpaque(false);
            scrollContent.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

            scrollContent.add(buildSelectorSection(), BorderLayout.NORTH);

            contentPanel.setOpaque(false);
            contentPanel.add(buildReadyState(), "ready");
            contentPanel.add(buildLoadingState(), "loading");
            contentPanel.add(buildResultsState(), "results");
            contentPanel.add(buildErrorState(), "error");
            scrollContent.add(contentPanel, BorderLayout.CENTER);

            JScrollPane globalScrollPane = new JScrollPane(scrollContent,
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            globalScrollPane.setBorder(BorderFactory.createEmptyBorder());
            globalScrollPane.setOpaque(false);
            globalScrollPane.getViewport().setOpaque(false);
            globalScrollPane.getVerticalScrollBar().setUnitIncrement(16);

            return globalScrollPane;
        }

        private JComponent buildSelectorSection() {
            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBackground(Palette.CARD_BG);
            card.setBorder(new CompoundBorder(new RoundedLineBorder(Palette.DIVIDER, 14),
                    BorderFactory.createEmptyBorder(18, 22, 18, 22)));

            JLabel sectionTitle = new JLabel("COMPARE RELEASES");
            sectionTitle.setFont(UIFonts.SMALL_BOLD);
            sectionTitle.setForeground(Palette.TEXT_SECONDARY);
            sectionTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(sectionTitle);
            card.add(Box.createVerticalStrut(14));

            JPanel row = new JPanel(new GridBagLayout());
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridy = 0;
            gc.insets = new Insets(0, 0, 0, 0);

            JPanel sourceBlock = buildEnvironmentBlock("SOURCE", sourceCombo);
            gc.gridx = 0;
            gc.weightx = 1;
            gc.fill = GridBagConstraints.HORIZONTAL;
            row.add(sourceBlock, gc);

            IconButton swap = new IconButton("\u21C4", "Swap environments");
            swap.addActionListener(e -> {
                Object a = sourceCombo.getSelectedItem();
                Object b = targetCombo.getSelectedItem();
                sourceCombo.setSelectedItem(b);
                targetCombo.setSelectedItem(a);
            });
            JPanel swapWrap = new JPanel(new GridBagLayout());
            swapWrap.setOpaque(false);
            swapWrap.add(swap);
            gc.gridx = 1;
            gc.weightx = 0;
            gc.fill = GridBagConstraints.NONE;
            gc.insets = new Insets(18, 14, 0, 14);
            row.add(swapWrap, gc);

            JPanel targetBlock = buildEnvironmentBlock("TARGET", targetCombo);
            gc.gridx = 2;
            gc.weightx = 1;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.insets = new Insets(0, 0, 0, 0);
            row.add(targetBlock, gc);

            card.add(row);
            card.add(Box.createVerticalStrut(16));

            JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            buttonRow.setOpaque(false);
            buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            compareButton.getAccessibleContext().setAccessibleName("Compare Versions");
            compareButton.addActionListener(e -> runComparison());
            buttonRow.add(compareButton);
            card.add(buttonRow);

            return card;
        }

        private JPanel buildEnvironmentBlock(String caption, JComboBox<Environment> combo) {
            JPanel block = new JPanel();
            block.setOpaque(false);
            block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));

            JLabel label = new JLabel(caption);
            label.setFont(UIFonts.SMALL_BOLD);
            label.setForeground(Palette.TEXT_TERTIARY);
            label.setAlignmentX(Component.LEFT_ALIGNMENT);

            combo.setRenderer(new EnvironmentRenderer());
            combo.setFont(UIFonts.BODY_BOLD);
            combo.setBackground(Color.WHITE);
            combo.setBorder(new RoundedLineBorder(Palette.DIVIDER, 8));
            // Height increased to 56 to display full two-line renderer values
            combo.setPreferredSize(new Dimension(260, 56));
            combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
            combo.setAlignmentX(Component.LEFT_ALIGNMENT);
            combo.getAccessibleContext().setAccessibleName(caption.equals("SOURCE") ? "Source Environment" : "Target Environment");

            block.add(label);
            block.add(Box.createVerticalStrut(6));
            block.add(combo);
            return block;
        }

        private void populateEnvironmentCombos() {
            sourceCombo.removeAllItems();
            targetCombo.removeAllItems();
            for (Environment env : environments) {
                sourceCombo.addItem(env);
                targetCombo.addItem(env);
            }
            if (environments.size() > 1) {
                targetCombo.setSelectedIndex(1);
            }
        }

        private JComponent buildReadyState() {
            JPanel wrap = new JPanel(new GridBagLayout());
            wrap.setOpaque(false);

            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setOpaque(false);

            JLabel title = new JLabel("Ready to compare");
            title.setFont(UIFonts.SECTION.deriveFont(17f));
            title.setForeground(Palette.TEXT_PRIMARY);
            title.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel body = new JLabel("<html><div style='text-align:center'>Select a source and target environment<br>"
                    + "to compare microservice releases.</div></html>");
            body.setFont(UIFonts.BODY);
            body.setForeground(Palette.TEXT_SECONDARY);
            body.setAlignmentX(Component.CENTER_ALIGNMENT);
            body.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));

            JLabel count = new JLabel(SERVICES.length + " services will be checked.");
            count.setFont(UIFonts.SMALL);
            count.setForeground(Palette.TEXT_TERTIARY);
            count.setAlignmentX(Component.CENTER_ALIGNMENT);

            card.add(title);
            card.add(body);
            card.add(count);
            wrap.add(card);
            return wrap;
        }

        private JProgressBar progressBar;
        private JLabel loadingCaption;

        private JComponent buildLoadingState() {
            JPanel wrap = new JPanel(new GridBagLayout());
            wrap.setOpaque(false);

            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setOpaque(false);
            card.setPreferredSize(new Dimension(360, 120));

            loadingCaption = new JLabel("Comparing releases...");
            loadingCaption.setFont(UIFonts.SECTION.deriveFont(16f));
            loadingCaption.setForeground(Palette.TEXT_PRIMARY);
            loadingCaption.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel sub = new JLabel("Fetching release versions in parallel");
            sub.setFont(UIFonts.BODY);
            sub.setForeground(Palette.TEXT_SECONDARY);
            sub.setAlignmentX(Component.CENTER_ALIGNMENT);
            sub.setBorder(BorderFactory.createEmptyBorder(6, 0, 16, 0));

            progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);
            progressBar.setMaximumSize(new Dimension(280, 6));
            progressBar.setPreferredSize(new Dimension(280, 6));
            progressBar.setForeground(Palette.ACCENT);
            progressBar.setBorderPainted(false);

            card.add(loadingCaption);
            card.add(sub);
            card.add(progressBar);
            wrap.add(card);
            return wrap;
        }

        private JLabel errorDetailLabel;

        private JComponent buildErrorState() {
            JPanel wrap = new JPanel(new GridBagLayout());
            wrap.setOpaque(false);

            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setOpaque(false);

            JLabel title = new JLabel("Comparison couldn't be completed");
            title.setFont(UIFonts.SECTION.deriveFont(17f));
            title.setForeground(Palette.RED);
            title.setAlignmentX(Component.CENTER_ALIGNMENT);

            errorDetailLabel = new JLabel("Something went wrong while comparing the selected environments.");
            errorDetailLabel.setFont(UIFonts.BODY);
            errorDetailLabel.setForeground(Palette.TEXT_SECONDARY);
            errorDetailLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            errorDetailLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 18, 0));

            JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            buttonRow.setOpaque(false);
            RoundedButton retry = new RoundedButton("Try Again", true);
            retry.addActionListener(e -> runComparison());
            buttonRow.add(retry);
            buttonRow.setAlignmentX(Component.CENTER_ALIGNMENT);

            card.add(title);
            card.add(errorDetailLabel);
            card.add(buttonRow);
            wrap.add(card);
            return wrap;
        }

        private JPanel emptyResultsCard;
        private JLabel emptyResultsMessage;
        private JPanel tableBodyPanel;

        private JComponent buildResultsState() {
            JPanel results = new JPanel(new BorderLayout(0, 16));
            results.setOpaque(false);

            results.add(buildSummaryPanel(), BorderLayout.NORTH);
            results.add(buildTablePanel(), BorderLayout.CENTER);
            return results;
        }
        private JComponent buildSummaryPanel() {
            JPanel outer = new JPanel(new BorderLayout());
            outer.setOpaque(false);

            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBackground(Palette.CARD_BG);
            card.setBorder(new CompoundBorder(
                    new RoundedLineBorder(Palette.DIVIDER, 14),
                    BorderFactory.createEmptyBorder(6, 6, 12, 6)
            ));

            JLabel caption = new JLabel("OVERVIEW");
            caption.setFont(UIFonts.SMALL_BOLD);
            caption.setForeground(Palette.TEXT_SECONDARY);
            caption.setBorder(BorderFactory.createEmptyBorder(10, 16, 6, 0));
            caption.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(caption);

            // Single initialization with proper dimensions for BoxLayout
            JPanel metricRow = new JPanel(new GridLayout(1, 5, 8, 0));
            metricRow.setOpaque(false);
            metricRow.setPreferredSize(new Dimension(700, 75));
            metricRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 75)); // Prevents BoxLayout clipping
            metricRow.setAlignmentX(Component.LEFT_ALIGNMENT);

            wireMetric(totalCard, Filter.ALL);
            wireMetric(equalCard, Filter.EQUAL);
            wireMetric(aheadCard, Filter.AHEAD);
            wireMetric(behindCard, Filter.BEHIND);
            wireMetric(errorCard, Filter.ERROR);

            metricRow.add(totalCard);
            metricRow.add(equalCard);
            metricRow.add(aheadCard);
            metricRow.add(behindCard);
            metricRow.add(errorCard);
            card.add(metricRow);

            mismatchLabel.setFont(UIFonts.SMALL);
            mismatchLabel.setForeground(Palette.TEXT_TERTIARY);
            mismatchLabel.setBorder(BorderFactory.createEmptyBorder(6, 16, 0, 0));
            mismatchLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            mismatchLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            mismatchLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    setActiveFilter(Filter.MISMATCH);
                }
            });
            card.add(mismatchLabel);

            outer.add(card, BorderLayout.CENTER);
            return outer;
        }
        private void wireMetric(MetricCard card, Filter filter) {
            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    setActiveFilter(filter);
                }
            });
        }

        private JComponent buildTablePanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setOpaque(false);

            JPanel card = new JPanel(new BorderLayout());
            card.setBackground(Palette.CARD_BG);
            card.setBorder(new CompoundBorder(new RoundedLineBorder(Palette.DIVIDER, 14),
                    BorderFactory.createEmptyBorder(16, 18, 16, 18)));

            JPanel headerBlock = new JPanel();
            headerBlock.setOpaque(false);
            headerBlock.setLayout(new BoxLayout(headerBlock, BoxLayout.Y_AXIS));

            JPanel titleRow = new JPanel(new BorderLayout());
            titleRow.setOpaque(false);
            titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel sectionTitle = new JLabel("SERVICE COMPARISON");
            sectionTitle.setFont(UIFonts.SMALL_BOLD);
            sectionTitle.setForeground(Palette.TEXT_SECONDARY);
            titleRow.add(sectionTitle, BorderLayout.WEST);
            completedCaption.setFont(UIFonts.SMALL);
            completedCaption.setForeground(Palette.TEXT_TERTIARY);
            titleRow.add(completedCaption, BorderLayout.EAST);
            headerBlock.add(titleRow);
            headerBlock.add(Box.createVerticalStrut(12));

            JPanel controls = new JPanel(new BorderLayout(10, 0));
            controls.setOpaque(false);
            controls.setAlignmentX(Component.LEFT_ALIGNMENT);
            searchField.getAccessibleContext().setAccessibleName("Service Search");
            searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { applyRowFilter(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { applyRowFilter(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { applyRowFilter(); }
            });
            controls.add(searchField, BorderLayout.CENTER);

            filterCombo.getAccessibleContext().setAccessibleName("Status Filter");
            filterCombo.setPreferredSize(new Dimension(120, 32));
            filterCombo.addActionListener(e -> {
                Filter[] map = {Filter.ALL, Filter.EQUAL, Filter.AHEAD, Filter.BEHIND, Filter.ERROR};
                int idx = filterCombo.getSelectedIndex();
                if (idx >= 0 && idx < map.length) {
                    activeFilter = map[idx];
                    highlightSelectedMetric();
                    applyRowFilter();
                }
            });
            controls.add(filterCombo, BorderLayout.EAST);
            headerBlock.add(controls);
            headerBlock.add(Box.createVerticalStrut(12));

            card.add(headerBlock, BorderLayout.NORTH);

            table.setFont(UIFonts.BODY);
            table.setRowHeight(34);
            table.setShowGrid(false);
            table.setIntercellSpacing(new Dimension(0, 0));
            table.setSelectionBackground(Palette.ACCENT_BG);
            table.setSelectionForeground(Palette.TEXT_PRIMARY);
            table.setFillsViewportHeight(true);
            table.getTableHeader().setFont(UIFonts.SMALL_BOLD);
            table.getTableHeader().setForeground(Palette.TEXT_SECONDARY);
            table.getTableHeader().setBackground(Palette.CARD_BG);
            table.getTableHeader().setBorder(new MatteBorder(0, 0, 1, 0, Palette.DIVIDER));
            table.getTableHeader().setReorderingAllowed(false);

            table.getColumnModel().getColumn(0).setCellRenderer(new ServiceNameRenderer());
            table.getColumnModel().getColumn(1).setCellRenderer(new VersionCellRenderer());
            table.getColumnModel().getColumn(2).setCellRenderer(new VersionCellRenderer());
            table.getColumnModel().getColumn(3).setCellRenderer(new StatusCellRenderer());
            table.getColumnModel().getColumn(0).setPreferredWidth(240);
            table.getColumnModel().getColumn(3).setPreferredWidth(140);

            sorter = new TableRowSorter<>(tableModel);
            sorter.setComparator(3, Comparator.comparing(o -> ((ComparisonStatus) o).ordinal()));
            table.setRowSorter(sorter);

            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        int viewRow = table.getSelectedRow();
                        if (viewRow >= 0) {
                            int modelRow = table.convertRowIndexToModel(viewRow);
                            VersionResult r = tableModel.rowAt(modelRow);
                            String srcName = currentReport != null ? currentReport.env1.name : "Source";
                            String tgtName = currentReport != null ? currentReport.env2.name : "Target";
                            new ServiceDetailsDialog(MainFrame.this, r, srcName, tgtName).setVisible(true);
                        }
                    }
                }
            });
            table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openDetails");
            table.getActionMap().put("openDetails", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int viewRow = table.getSelectedRow();
                    if (viewRow >= 0) {
                        int modelRow = table.convertRowIndexToModel(viewRow);
                        VersionResult r = tableModel.rowAt(modelRow);
                        String srcName = currentReport != null ? currentReport.env1.name : "Source";
                        String tgtName = currentReport != null ? currentReport.env2.name : "Target";
                        new ServiceDetailsDialog(MainFrame.this, r, srcName, tgtName).setVisible(true);
                    }
                }
            });

            // Embedded without separate nested viewport constraints so it expands cleanly
            JPanel tableHeaderAndBody = new JPanel(new BorderLayout());
            tableHeaderAndBody.setOpaque(false);
            tableHeaderAndBody.add(table.getTableHeader(), BorderLayout.NORTH);
            tableHeaderAndBody.add(table, BorderLayout.CENTER);

            emptyResultsMessage = new JLabel();
            emptyResultsMessage.setHorizontalAlignment(SwingConstants.CENTER);
            emptyResultsMessage.setFont(UIFonts.BODY);
            emptyResultsMessage.setForeground(Palette.TEXT_SECONDARY);

            emptyResultsCard = new JPanel(new BorderLayout());
            emptyResultsCard.setOpaque(false);
            emptyResultsCard.add(emptyResultsMessage, BorderLayout.CENTER);

            JPanel clearWrap = new JPanel(new FlowLayout(FlowLayout.CENTER));
            clearWrap.setOpaque(false);
            RoundedButton clearSearch = new RoundedButton("Clear Search", false);
            clearSearch.addActionListener(e -> {
                searchField.setText("");
                filterCombo.setSelectedIndex(0);
            });
            clearWrap.add(clearSearch);
            emptyResultsCard.add(clearWrap, BorderLayout.SOUTH);

            tableBodyPanel = new JPanel(new CardLayout());
            tableBodyPanel.setOpaque(false);
            tableBodyPanel.add(tableHeaderAndBody, "table");
            tableBodyPanel.add(emptyResultsCard, "empty");

            card.add(tableBodyPanel, BorderLayout.CENTER);
            panel.add(card, BorderLayout.CENTER);
            return panel;
        }

        private void applyRowFilter() {
            if (sorter == null) return;
            String search = searchField.getText().trim().toLowerCase();

            RowFilter<ServiceTableModel, Integer> rowFilter = new RowFilter<ServiceTableModel, Integer>() {
                @Override
                public boolean include(Entry<? extends ServiceTableModel, ? extends Integer> entry) {
                    ServiceTableModel model = entry.getModel();
                    VersionResult r = model.rowAt(entry.getIdentifier());

                    if (!search.isEmpty() && !r.service.toLowerCase().contains(search)) {
                        return false;
                    }

                    switch (activeFilter) {
                        case ALL: return true;
                        case EQUAL: return r.status == ComparisonStatus.EQUAL;
                        case AHEAD: return r.status == ComparisonStatus.AHEAD;
                        case BEHIND: return r.status == ComparisonStatus.BEHIND;
                        case ERROR: return r.status == ComparisonStatus.ERROR;
                        case MISMATCH: return r.status != ComparisonStatus.EQUAL;
                        default: return true;
                    }
                }
            };
            sorter.setRowFilter(rowFilter);

            boolean noRows = table.getRowCount() == 0 && tableModel.getRowCount() > 0;
            if (tableBodyPanel != null) {
                CardLayout cl = (CardLayout) tableBodyPanel.getLayout();
                cl.show(tableBodyPanel, noRows ? "empty" : "table");
            }
            if (noRows) {
                emptyResultsMessage.setText(search.isEmpty()
                        ? "No services match the selected filter."
                        : "No services match \"" + searchField.getText().trim() + "\".");
            }
        }

        private void setActiveFilter(Filter filter) {
            activeFilter = filter;
            int idx;
            switch (filter) {
                case EQUAL: idx = 1; break;
                case AHEAD: idx = 2; break;
                case BEHIND: idx = 3; break;
                case ERROR: idx = 4; break;
                default: idx = 0;
            }
            filterCombo.setSelectedIndex(idx);
            highlightSelectedMetric();
            applyRowFilter();
        }

        private void highlightSelectedMetric() {
            totalCard.setSelected(activeFilter == Filter.ALL);
            equalCard.setSelected(activeFilter == Filter.EQUAL);
            aheadCard.setSelected(activeFilter == Filter.AHEAD);
            behindCard.setSelected(activeFilter == Filter.BEHIND);
            errorCard.setSelected(activeFilter == Filter.ERROR);
        }

        private void runComparison() {
            Environment env1 = (Environment) sourceCombo.getSelectedItem();
            Environment env2 = (Environment) targetCombo.getSelectedItem();

            if (env1 == null || env2 == null) {
                JOptionPane.showMessageDialog(this, "Please select both environments.",
                        "Selection required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (env1.name.equals(env2.name)) {
                JOptionPane.showMessageDialog(this, "Please select two different environments.",
                        "Selection required", JOptionPane.WARNING_MESSAGE);
                return;
            }

            setBusy(true, env1, env2);

            new SwingWorker<ComparisonReport, Void>() {
                private long start;
                private Exception failure;

                @Override
                protected ComparisonReport doInBackground() {
                    start = System.nanoTime();
                    try {
                        return ReleaseValidator.compareVersions(env1, env2);
                    } catch (Exception ex) {
                        failure = ex;
                        return null;
                    }
                }

                @Override
                protected void done() {
                    setBusy(false, env1, env2);
                    try {
                        ComparisonReport report = failure == null ? get() : null;
                        if (report == null) {
                            if (failure != null) failure.printStackTrace();
                            showErrorState();
                            return;
                        }
                        report.elapsedMillis = (System.nanoTime() - start) / 1_000_000;
                        showResults(report);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        showErrorState();
                    }
                }
            }.execute();
        }

        private void setBusy(boolean busy, Environment env1, Environment env2) {
            sourceCombo.setEnabled(!busy);
            targetCombo.setEnabled(!busy);
            compareButton.setEnabled(!busy);
            compareButton.setText(busy ? "Comparing..." : "Compare Versions");
            if (busy) {
                loadingCaption.setText("Comparing " + env1.name + " \u2192 " + env2.name);
                statusDot.setColor(Palette.ACCENT);
                statusLabel.setText("Comparing " + SERVICES.length + " services...");
                contentCards.show(contentPanel, "loading");
            }
        }

        private void showErrorState() {
            statusDot.setColor(Palette.RED);
            statusLabel.setText("Comparison failed");
            contentCards.show(contentPanel, "error");
        }

        private void showResults(ComparisonReport report) {
            currentReport = report;
            statusDot.setColor(Palette.GREEN);
            statusLabel.setText("Comparison complete");

            tableModel.setData(report.results, report.env1.name, report.env2.name);
            table.getColumnModel().getColumn(0).setCellRenderer(new ServiceNameRenderer());
            table.getColumnModel().getColumn(1).setCellRenderer(new VersionCellRenderer());
            table.getColumnModel().getColumn(2).setCellRenderer(new VersionCellRenderer());
            table.getColumnModel().getColumn(3).setCellRenderer(new StatusCellRenderer());
            table.getColumnModel().getColumn(0).setPreferredWidth(240);
            table.getColumnModel().getColumn(3).setPreferredWidth(140);
            if (sorter != null) {
                sorter.setComparator(3, Comparator.comparing(o -> ((ComparisonStatus) o).ordinal()));
            }

            totalCard.setValue(report.total());
            equalCard.setValue(report.equal);
            aheadCard.setValue(report.ahead);
            behindCard.setValue(report.behind);
            errorCard.setValue(report.errors);
            mismatchLabel.setText(report.mismatches() + " total mismatches");

            activeFilter = Filter.ALL;
            filterCombo.setSelectedIndex(0);
            highlightSelectedMetric();
            applyRowFilter();

            if (report.elapsedMillis >= 0) {
                completedCaption.setText(report.total() + " services checked \u00b7 completed in "
                        + String.format(Locale.US, "%.1f", report.elapsedMillis / 1000.0) + "s");
            } else {
                completedCaption.setText(report.total() + " services checked");
            }

            contentCards.show(contentPanel, "results");
            contentPanel.revalidate();
            contentPanel.repaint();
        }
    }
}